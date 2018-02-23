/* AsyncCliend.ava */
package uy.com.r2.svc.conn;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.svc.tools.Json;

/** Single connection client.
 * Connector used in ISO-8586 connections or some HSM.
 * Here we have only one "channel" (a TCP connection) and 
 * N "slots" to send messages concurrently. 
 * WORK IN PROGRESS!!!!
 * @author G.Camargo
 */
public class AsyncClient implements AsyncService {
    private final static Logger LOG = Logger.getLogger(AsyncClient.class);
    private ChannelRunnable channelTh = null;
    private String charset = Charset.defaultCharset().name();
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Port", ConfigItemDescriptor.INTEGER, 
                "TCP Port to connect", "8888", ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( "Host", ConfigItemDescriptor.STRING, 
                "Remote Host name to connect", null, ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( "Slots", ConfigItemDescriptor.INTEGER, 
                "Numver od concurrent requests", "10", ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( "Charset", ConfigItemDescriptor.STRING, 
                "Charset used to converto to String messages", Charset.defaultCharset().name(), 
                ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( "SlotNrMgr", ConfigItemDescriptor.STRING, 
                "Pipeline to put an get SlotNr", "SlotNrMgr", ConfigItemDescriptor.DEPLOYER));
        return l;
    }
    
    /** Invocation dispatch phase.
     * It may: <br>
     * (1) create and return a SvcResponse itself, <br>
     * (2) return a SvcRequest to dispatch to the next service module, or <br>
     * (3) return NULL when there aren't next module to call, or <br>
     * (4) throw a Exception to explicit set the module that originates <br>
     * the failure.
     * @param req Invocation message from caller
     * @param cfg Module configuration 
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        // check the socket
        if( channelTh == null) {
            startup( cfg);
        }
        // Send
        return channelTh.send( req);
    }

    /** Process a response phase.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcResponse message from next module, or null (no next)
     * @param cfg Module configuration 
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        throw new UnsupportedOperationException( "AsyncClient.onResponse() is invalid");
    }
    
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> map = new HashMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        } 
        if( channelTh != null) {
            map.put( "ActiveSlots", channelTh.getActiveSlotsNr());
        }
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        if( channelTh != null) {
            try {
                channelTh.close();
            } catch( Exception x) { }
            channelTh = null;
        }
    }

    private synchronized void startup( Configuration cfg) throws Exception {
        if( channelTh != null) {
            return;
        } 
        charset = cfg.getString( "Charset");
        LOG.debug( "Opening socket " + cfg.getString( "Host") + ":" + cfg.getInt( "Port"));
        Socket s = new Socket( cfg.getString( "Host"), cfg.getInt( "Port"));
        channelTh = new ChannelRunnable( s, this, cfg.getInt( "Slots"));
        new Thread( channelTh).start();
    }
    
    private class ChannelRunnable implements Runnable {
        private final Socket socket;
        private final Slot slots[];
        private InputStream inS = null;
        private OutputStream outS = null;

        ChannelRunnable( Socket socket, AsyncClient ac, int slotsNr) {
            this.socket = socket;
            try {
                inS = socket.getInputStream();
                outS = socket.getOutputStream();
            } catch( Exception x) {
                LOG.warn( "Failed to get Streams", x);
            }
            slots = new Slot[ slotsNr];
            LOG.debug( "SocketRunnable started with " + slots.length + " slots");
        }

        SvcResponse send( SvcRequest rq) throws Exception {
            int slotNr = getNewSlotNr( rq);
            if( slotNr < 0) {
                return new SvcResponse( "Busy, too many slots: " + slots.length, 
                        SvcResponse.RES_CODE_TOPPED, rq);   
            }
            // Get serialized request
            Object s = rq.get( Json.SERIALIZED_JSON);
            if( s == null) {
                s = rq.get( "Serialized");
            }
            try { // In this point we have to put the slotNr in the mmesage
                // or link slotNr with something of the request
                SvcRequest rqSetSlot = new SvcRequest( "", 0, 0, "GetSlotNr", null, 0);
                rqSetSlot.add( "Serialized", s);
                SvcResponse rpSlot = SvcCatalog.getDispatcher().call( rqSetSlot);
                s = "" + rpSlot.get( "Serialized");
            } catch( Exception x) { 
                LOG.info( "Failed set SlotNr in msg " + s, x);
            }
            // Send
            byte buff[] = ( "" + s).getBytes();
            LOG.trace( "Content to send: '" + s);
            outS.write( buff);
            return null;
        }
        
        @Override
        public void run() {
            byte buff[] = new byte[ 10240];
            int l = 0;
            String sBuff = "";
            int slotNr = 0;
            for( ; ;) {
                try {
                    l = inS.read( buff, 0, buff.length);
                    sBuff = new String( buff, 0, l, charset);
                } catch( Exception x) { 
                    LOG.info( "Failed read msg " + x, x);
                }
                try { // In this point we have to get the slotNr from the mmesage
                    // To do that a specific pipe si called
                    SvcRequest rqGetSlot = new SvcRequest( "", 0, 0, "GetSlotNr", null, 0);
                    rqGetSlot.add( "Serialized", sBuff);
                    SvcResponse rpSlotNr = SvcCatalog.getDispatcher().call( rqGetSlot);
                    slotNr = Integer.parseInt( "" + rpSlotNr.get( "SlotNr"));
                } catch( Exception x) { 
                    LOG.info( "Failed get SlotNr in msg " + sBuff, x);
                }
                try { // Dispatch response
                    SvcResponse r = new SvcResponse( 0, slots[ slotNr].request);
                    releaseSlot( slotNr);
                    SvcCatalog.getDispatcher().onMessage( r);
                } catch( Exception x) { 
                    LOG.info( "Failed to process message " + sBuff, x);
                }
            }            
        }
        
        void close() throws Exception {
            socket.close();
            inS = null;
        }
        
        synchronized int getNewSlotNr( SvcRequest rq) {
            for( int i = 0; i < slots.length; ++i) {
                if( slots[ i].free) {
                    slots[ i].free = false;
                    return i;
                }
            }
            return -1;
        }
        
        synchronized void releaseSlot( int i) {
            slots[ i].free = true;
        }
        
        int getActiveSlotsNr() {
            int count = 0;
            for( int i = 0; i < slots.length; ++i) {
                if( !slots[ i].free) {
                    ++count;
                }
            }
            return count;
        }
    }
    
    private class Slot {
        SvcRequest request;
        boolean free = true;
    }
    
    /**/
    public static void main( String args[]) {
        org.apache.log4j.BasicConfigurator.configure();
        AsyncClient u = new AsyncClient();
        SvcRequest r = new SvcRequest( "Test", 0, 0, "TestService", null, 5000);
        Configuration cfg = new Configuration();
        cfg.put( "Port", 8015);
        try {
            u.onRequest( r, cfg);
        } catch( Exception ex) {
            ex.printStackTrace();
        }
    }
    /**/

}


