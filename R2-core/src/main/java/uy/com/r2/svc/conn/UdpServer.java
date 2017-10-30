/* UdpServer.ava */
package uy.com.r2.svc.conn;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.StartUpRequired;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.svc.tools.Json;

/** UDP client and server connector.
 * @author G.Camargo
 */
public class UdpServer implements StartUpRequired {
    private final static Logger LOG = Logger.getLogger(UdpServer.class);
    private int port = 0;
    private DatagramSocket soc = null;
    private boolean stop = false;
    private int receivedCount = 0;
     
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
    
    /** Configure and start the server.
     * @throws java.lang.Exception 
     */
    @Override
    public void startUp( Configuration cfg) throws Exception {
        port = cfg.getInt( "Port");
        new UdpListener().start();
        receivedCount = 0;
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
        for( InetAddress a: getExternalAddressList()) {
            sb.append( a.toString());
            sb.append( " ");
        }
        map.put( "ExternalAddress", sb.toString());
        map.put( "Received", "" + receivedCount);
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        stop = true;
        try {
            soc.close();
        } catch( Exception x) { }
    }

    /** UdpListener */
    class UdpListener extends Thread {
        byte buff[] = new byte[ 4096];
        
        UdpListener() {
            setName( "Udplistener_" + getId()); 
            LOG.trace( "Started " + getName());
        }
        
        @Override
        public void run() {
            while( !stop) {
                try {
                    soc = new DatagramSocket( port);
                    DatagramPacket dp = new DatagramPacket( buff, buff.length);
                    soc.receive( dp);
                    InetAddress a = dp.getAddress();
                    if( !getExternalAddressList().contains( a)) {
                        LOG.trace( "************************************************* " + a);
                        LOG.trace( "Packet(" + receivedCount + "): " + new String( dp.getData(), 0, dp.getLength()));
                        ++receivedCount;
                        SvcRequest rq = new SvcRequest( a.getHostName(), 0, 0, "SetMasterServer", null, 10000);
                        rq.add( Json.SERIALIZED_JSON, new String( dp.getData(), 0, dp.getLength()));
                        SvcCatalog.getDispatcher().call( rq);
                    } else {
                        LOG.trace( "Packet ignored, " + a);
                    }
                } catch( Exception ex) {
                    if( !stop) {
                        LOG.warn( "Error on " + getName() + " " + ex, ex);
                    }
                } finally {
                    try {
                        soc.close();
                    } catch( Exception xx) { }
                }
            }
            LOG.trace( "Stopped " + getName());
        }
        
    }
    
    private List<InetAddress> getExternalAddressList() {
        List<InetAddress> l = new LinkedList();
        try {
            for( NetworkInterface nif: Collections.list( NetworkInterface.getNetworkInterfaces())) {
                if( nif.isUp() && !nif.isLoopback()) {
                    l.addAll( Collections.list( nif.getInetAddresses()));
                }
            }
        } catch( Exception x) { }
        return l;
    }
    
    /**/
    public static void main( String args[]) {
        org.apache.log4j.BasicConfigurator.configure();
        LOG.trace( "Storting....");
        UdpServer u = new UdpServer();
        Configuration cfg = new Configuration();
        cfg.put( "Port", 8015);
        try {
            u.startUp( cfg);
            long t = System.currentTimeMillis() + 10000;
            while( System.currentTimeMillis() < t) {
                Thread.sleep( 1000);
                System.err.println( ".");
            }
            u.shutdown();
        } catch( Exception ex) {
            ex.printStackTrace();
        }
    }
    /**/
}


