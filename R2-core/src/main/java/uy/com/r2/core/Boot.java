/* Boot.java */
package uy.com.r2.core;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Module;
import uy.com.r2.svc.conn.HttpClient;
import uy.com.r2.svc.conn.JdbcService;
import uy.com.r2.svc.conn.MicroHttpServer;
import uy.com.r2.svc.tools.FilePathSynchronizer;
import uy.com.r2.svc.tools.FileServices;
import uy.com.r2.svc.tools.Json;
import uy.com.r2.svc.tools.SvcAvailServers;
import uy.com.r2.svc.tools.ToHtml;

/** Startup implementation.
 * Its: functions include: <br>
 * - Try to load last configuration <br>
 * - Else startup a minimal configuration <br>
 * - Configure basic items: LocalName, LocalPort, RemoteUrl <br>
 * - Support for special boot; Module Arg=Value <br>
 * @author G.Camargo
 */
public class Boot {
    private static final Logger LOG = Logger.getLogger(Boot.class);

    /** Entry point as java application.
     * @param args Standard arguments: Local_Port Remote_Url
     */
    public static void main( String args[]) {
        // Default values
        String hostName = getHostName();
        String rmtUrl = "http://" + hostName + ":8016";  // Asume there is another in this machine
        int localPort = 8015;
        // Parse args
        switch( args.length) {
        case 2:
           rmtUrl = args[ 1];
        case 1:
           localPort = Integer.parseInt( args[ 0]);
        }
        //Start
        start( hostName, localPort, rmtUrl);
        try {
            // Wait till stop
            while( !(Boolean)SvcCatalog.getDispatcher().getStatusVars().get( "Stopped")) {
                Thread.sleep( 1000);
            }
        } catch ( Exception ex ) {
            LOG.warn( "" + ex, ex);
            System.err.println( "Error " + ex);
            ex.printStackTrace( System.err);
        }
        LOG.info( "Stopped");
    }
    
    /** Start all modules.
     * @param localHost local host name in the network
     * @param localPort local listening port
     * @param rmtUrl remote url to contact (or null)
     */
    public static void start( String localHost, int localPort, String rmtUrl) {
        try {
            LOG.trace( "start " + localHost + " " + localPort + " " + rmtUrl);
            // Read previous configuration o basic config
            Properties pr = readConfig( localHost, localPort, rmtUrl);
            // Deploy initial pipe
            for( int i = 0; pr.getProperty( "Module." + i) != null; ++i) {
                String mod = pr.getProperty( "Module." + i);
                String ki = "" + i + ".";
                Configuration c = new Configuration();
                for( String k: pr.stringPropertyNames()) {
                    if( k.startsWith( ki)) {
                        c.put( k.substring( ki.length()), pr.get( k));
                    }    
                }
                LOG.trace( "Deploy " + mod + " " + c);
                SvcCatalog.getCatalog().installModule( mod, c);
            }
        } catch ( Exception ex ) {
            LOG.warn( "" + ex, ex);
            System.err.println( "Error " + ex);
            ex.printStackTrace( System.err);
        }
    }

    private void persistConfig() throws Exception {
        Properties pr = new Properties();
        int n = 0;
        TreeSet<String> ts = new TreeSet( SvcCatalog.getCatalog().getModuleNames());
        ts.remove( SvcDeployer.DEPLOYER_NAME);  // Auto-started itself
        for( String m: ts) {
            pr.put( "Module." + n, m);
            Configuration c = SvcCatalog.getCatalog().getModuleInfo( m).getConfiguration();
            for( String k: c.getStringMap( "*").keySet()) {
                pr.put( "" + n + "." + k, c.getStringMap( "*").get(  k));                        
            }
            ++n;
        }
        String r2Path = System.getProperty( "R2_PATH", "");
        if( r2Path.length() > 0 && !r2Path.endsWith( File.separator)) {
            r2Path += File.separator;
        }
        FileOutputStream fos = new FileOutputStream( r2Path + "R2.properties");
        pr.store( fos, null);
        fos.close();
    }
    
    private static Properties readConfig( String hostName, int localPort, String rmtUrl) throws Exception {
        String r2Path = System.getProperty( "R2_PATH", "");
        if( r2Path.length() > 0 && !r2Path.endsWith( File.separator)) {
            r2Path += File.separator;
        }
        Properties pr = new Properties();
        /* !!!! load its last confuration
        try {
            FileInputStream fi = new FileInputStream( r2Path + "R2.properties");
            pr.load( fi);
            fi.close();
        } catch( Exception x) {
            LOG.info( "Can't load R2.properties: " + x);
        }    
        */
        if( pr.isEmpty()) {
            // calculate default server name
            String localUrl = "http://" + hostName + ":" + localPort;
            String localName = hostName + localPort;
            // Set default cliendNode
            new SvcRequest( localName, 0, 0, null, null, 0).getClientNode();
            // configure
            pr.putAll( DEFAULT_PIPE);
            pr.put( "1.Port", "" + localPort);
            pr.put( "2.LocalUrl", localUrl);
            if( rmtUrl != null && !rmtUrl.isEmpty()) {
                pr.put( "2.RemoteUrl", rmtUrl);
                pr.put( "7.Url", rmtUrl);
            }
        }
        LOG.trace( "Startup cfg " + pr);
        return pr;
    }

    public static String getHostName() {
        String ln = "localhost";
        String some = null;
        try {
            ln = InetAddress.getLocalHost().getHostName();
            for( NetworkInterface nif: Collections.list( NetworkInterface.getNetworkInterfaces())) {
                LOG.trace( " nif= " + nif.getDisplayName() + " ln=" + ln);
                if( nif.isUp() && !nif.isLoopback()) {
                    for( InterfaceAddress a: nif.getInterfaceAddresses()) {
                        some = a.getAddress().getHostName();
                        LOG.trace( " a= " + a);
                        if( !some.equals( a.getAddress().getHostAddress())) {  // Isn't address
                            ln = a.getAddress().getHostName();   
                        }
                    }
                }
            }
        } catch( Exception x) {
            LOG.warn( "Falied to get local adapter address " + x, x);
        }
        ln = ( ln.equals( "localhost") && some != null)? some: ln;  // In the worst case, take de IP
        LOG.trace( "getHostName= " + ln);        
        return ln;
    }
    

    static final Map<String,String> DEFAULT_PIPE = new HashMap();
    static {
        DEFAULT_PIPE.put( "Module.0", SvcCatalog.DISPATCHER_NAME);
        DEFAULT_PIPE.put( "0.class", SimpleDispatcher.class.getName());
        DEFAULT_PIPE.put( "0.DefaultServicePipeline", "SrvHtml,SrvJson,JdbcService,FileServices,SvcDeployer,SvcAvailServers");
        DEFAULT_PIPE.put( "0.Pipeline._Undefined_", "ClntJson,HttpClient");
        DEFAULT_PIPE.put( "0.Pipeline.Udp", "ClntJson,UdpClient");
        DEFAULT_PIPE.put( "Module.1", MicroHttpServer.class.getSimpleName());
        DEFAULT_PIPE.put( "1.class", MicroHttpServer.class.getName());
        DEFAULT_PIPE.put( "1.Port", "8015");
        DEFAULT_PIPE.put( "Module.2", SvcAvailServers.class.getSimpleName());
        DEFAULT_PIPE.put( "2.class", SvcAvailServers.class.getName());
        // Default add 2.LocalName
        // Default add 2.LocalUrl
        // Default add 2.RemotelUrl
        DEFAULT_PIPE.put( "Module.3", "SrvHtml");
        DEFAULT_PIPE.put( "3.class", ToHtml.class.getName());
        DEFAULT_PIPE.put( "Module.4", "SrvJson");
        DEFAULT_PIPE.put( "4.class", Json.class.getName());
        DEFAULT_PIPE.put( "4.ToSerial", "false");
        DEFAULT_PIPE.put( "4.ProcessRequest", "true");
        DEFAULT_PIPE.put( "4.ProcessResponse", "true");
        DEFAULT_PIPE.put( "Module.5", JdbcService.class.getSimpleName());
        DEFAULT_PIPE.put( "5.class", JdbcService.class.getName());
        DEFAULT_PIPE.put( "5.Driver", "org.apache.derby.jdbc.ClientDriver");
        DEFAULT_PIPE.put( "5.URL", "jdbc:derby://localhost:1527/Test");
        DEFAULT_PIPE.put( "5.User", "root");
        DEFAULT_PIPE.put( "5.Password", "XXXX");
        DEFAULT_PIPE.put( "5.Service.ListClients.SQL", "SELECT * FROM clients");
        DEFAULT_PIPE.put( "5.Service.AddClient.SQL", "INSERT INTO clients(id,name) VALUES (?,?)");
        DEFAULT_PIPE.put( "5.Service.AddClient.Params", "Id,Name");
        DEFAULT_PIPE.put( "Module.6", "ClntJson");
        DEFAULT_PIPE.put( "6.class", Json.class.getName());
        DEFAULT_PIPE.put( "6.ToSerial", "true");
        DEFAULT_PIPE.put( "6.ProcessRequest", "true");
        DEFAULT_PIPE.put( "6.ProcessResponse", "true");
        DEFAULT_PIPE.put( "Module.7", HttpClient.class.getSimpleName());
        DEFAULT_PIPE.put( "7.class", HttpClient.class.getName());
        DEFAULT_PIPE.put( "Module.8", FileServices.class.getSimpleName());
        DEFAULT_PIPE.put( "8.class", FileServices.class.getName());
        DEFAULT_PIPE.put( "Module.9", SvcDeployer.class.getSimpleName());
        DEFAULT_PIPE.put( "9.class", SvcDeployer.class.getName());
        
        DEFAULT_PIPE.put( "Module.10", "UdpServer");
        DEFAULT_PIPE.put( "10.class", uy.com.r2.svc.conn.UdpServer.class.getName());
        DEFAULT_PIPE.put( "Module.11", "UdpClient");
        DEFAULT_PIPE.put( "11.class", uy.com.r2.svc.conn.UdpClient.class.getName());
        //DEFAULT_PIPE.put( "Module.10", FilePathSynchronizer.class.getSimpleName());
        //DEFAULT_PIPE.put( "10.class", FilePathSynchronizer.class.getName());
        //DEFAULT_PIPE.put( "10.Path.lib", "lib");
        //DEFAULT_PIPE.put( "10.RemoteServer", "_Undefined_");
    }

}


