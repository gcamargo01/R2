/* UdpCliend.ava */
package uy.com.r2.svc.conn;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.svc.tools.Json;

/** UDP client and server connector.
 * @author G.Camargo
 */
public class UdpClient implements AsyncService {
    private final static Logger LOG = Logger.getLogger(UdpClient.class);
    private int port = 0;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Port", ConfigItemDescriptor.INTEGER, 
                "UDP Port", "8015"));
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
        port = cfg.getInt( "Port");
        // Get serialized request
        Object s = req.get( Json.SERIALIZED_JSON);
        if( s == null) {
            s = req.get( "Serialized");
        }
        byte buff[] = ( "" + s).getBytes();
        LOG.trace( "Content to send: '" + s + "' to port=" + port);
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket();
            socket.setBroadcast( true);
            for( InetAddress ia: getAdaptersAddress()) {
                LOG.trace( "   sending to: " + ia);
                DatagramPacket dp = new DatagramPacket( buff, buff.length, ia, port);
                socket.send( dp);
            }
        } catch( Exception x) {
            LOG.warn( "Failed to send UDP " + x, x);
        } finally {
            socket.close();
        }
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
        StringBuilder sb = new StringBuilder();
        for( InetAddress a: getAdaptersAddress()) {
            sb.append( a);
            sb.append( " ");
        }
        map.put( "BrodcastAddrss", sb.toString());
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    private List<InetAddress> getAdaptersAddress() {
        List<NetworkInterface> al = new LinkedList();
        List<InetAddress> il = new LinkedList();
        NetworkInterface loop = null;
        try {
            for( NetworkInterface nif: Collections.list( NetworkInterface.getNetworkInterfaces())) {
                //LOG.trace( " " + nif.getDisplayName());
                if( nif.isUp()) {
                    if( !nif.isLoopback()) {
                        al.add( nif);
                    } else {
                        loop = nif;
                    }
                }
            }
            if( al.isEmpty() && loop != null) {  
                al.add( loop);  // Add loopback if it was empty
            }
            InetAddress anyIA = null;
            for( NetworkInterface ni: al) {
                for( InterfaceAddress a: ni.getInterfaceAddresses()) {
                    //LOG.trace( "  " + ni.getDisplayName() + " " + a);
                    if( a.getBroadcast() != null) {
                        il.add( a.getBroadcast());
                        //LOG.trace( "  added " + a.getBroadcast());
                    } else {
                        anyIA = a.getAddress();
                    }
                }
            }
            if( il.isEmpty() && anyIA != null) {
                il.add( anyIA);
            }
        } catch( Exception x) {
            LOG.warn( "Falied to get network adapters brodcast address " + x, x);
        }
        LOG.trace( "getAdaptersAddress: " + il);
        return il;
    }

    /*
    public static void main( String args[]) {
        org.apache.log4j.BasicConfigurator.configure();
        UdpClient u = new UdpClient();
        SvcRequest r = new SvcRequest( "Test", 0, 0, "TestService", null, 5000);
        Configuration cfg = new Configuration();
        cfg.put( "Port", 8015);
        try {
            u.onRequest( r, cfg);
        } catch( Exception ex) {
            ex.printStackTrace();
        }
    }
    */
}


