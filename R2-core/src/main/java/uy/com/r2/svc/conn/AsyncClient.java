/* AsyncCliend.ava */
package uy.com.r2.svc.conn;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import java.net.Socket;
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
 * WORK IN PROGRESS!!!!
 * @author G.Camargo
 */
public class AsyncClient implements AsyncService {
    private final static Logger LOG = Logger.getLogger(AsyncClient.class);
    private int port = 0;
    private Socket socket = null;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Port", ConfigItemDescriptor.INTEGER, 
                "TCP Port to connect", "8888", ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( "Host", ConfigItemDescriptor.INTEGER, 
                "Remote Host name to connect", null, ConfigItemDescriptor.DEPLOYER));
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
        // Get serialized request
        Object s = req.get( Json.SERIALIZED_JSON);
        if( s == null) {
            s = req.get( "Serialized");
        }
        // check the socket
        if( socket == null) {
            startup( cfg);
        }
        // Send
        byte buff[] = ( "" + s).getBytes();
        LOG.trace( "Content to send: '" + s + "' to port=" + port);
        socket.sendUrgentData( port );
        return new SvcResponse( 0, req);
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
        // PENDING IMPLEMENT !!!!
        return null;
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
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        if( socket != null) {
            try {
                socket.close();
                socket = null;
            } catch( Exception x) { }
        }
    }

    private synchronized void startup( Configuration cfg) 
            throws Exception {
        if( socket != null) {
            return;
        } 
        socket = new Socket( cfg.getString( "Host"), cfg.getInt( "Port"));
        new Thread( new SockerListener( socket, this));
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

    private static class SockerListener implements Runnable {

        public SockerListener( Socket socket, AsyncClient ac) {
        }

        @Override
        public void run() {
            for( ; ;) {
                SvcResponse r = null; ///new SvcResponse();
                try {
                    SvcCatalog.getDispatcher().onMessage( r);
                } catch( Exception x) {
                    
                }
            }            
        }
    }
    
}


