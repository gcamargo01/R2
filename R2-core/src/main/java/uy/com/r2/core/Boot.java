/* Boot.java */
package uy.com.r2.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.svc.conn.HttpClient;
import uy.com.r2.svc.tools.FileServices;
import uy.com.r2.svc.tools.Json;

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
            Map<String,String> pr = readConfig( localHost, localPort, rmtUrl);
            // Search modules key cm: Key -> ModuleName
            TreeMap<String,String> km = new TreeMap();
            for( String p: pr.keySet()) {
                if( !p.contains( ".")) {
                    km.put( p, pr.get( p));
                }
            }
            // Instance configurations: ModuleName -> Configuration
            TreeMap<String,Configuration> cm = new TreeMap();
            for( String k: km.keySet()) {
                cm.put( km.get( k), new Configuration());
            }
            // Search for config items k + "."
            for( String p: pr.keySet()) {
                if( p.contains( ".")) {
                    String k = p.substring( 0, p.indexOf( '.'));
                    if( km.keySet().contains( k)) {  
                        cm.get( km.get( k)).put( p.substring( k.length() + 1), pr.get( p));
                    }
                }
            }
            // Deploy modules , ordered by k
            for( String k: km.keySet()) {
                //c.put( "Monitor", true);
                LOG.trace( "Deploy " + km.get( k) + " " + cm.get( km.get( k)));
                SvcCatalog.getCatalog().installModule( km.get( k), cm.get( km.get( k)));
            }
        } catch ( Exception ex ) {
            LOG.warn( "" + ex, ex);
            System.err.println( "Error " + ex);
            ex.printStackTrace( System.err);
        }
    }

    private void persistConfig() throws Exception {
        Properties pr = new Properties();
        TreeSet<String> ts = new TreeSet( SvcCatalog.getCatalog().getModuleNames());
        ts.remove( SvcDeployer.DEPLOYER_NAME);  // Auto-started itself
        for( String m: ts) {
            pr.put( m, m);
            Configuration c = SvcCatalog.getCatalog().getModuleInfo( m).getConfiguration();
            for( String k: c.getStringMap( "*").keySet()) {
                pr.put( m + "." + k, c.getStringMap( "*").get(  k));                        
            }
        }
        String r2Path = System.getProperty( "R2_PATH", "");
        if( r2Path.length() > 0 && !r2Path.endsWith( File.separator)) {
            r2Path += File.separator;
        }
        FileOutputStream fos = new FileOutputStream( r2Path + "R2.properties");
        pr.store( fos, null);
        fos.close();
    }
    
    private static Map<String,String> readConfig( String hostName, int localPort, String rmtUrl) throws Exception {
        String r2Path = System.getProperty( "R2_PATH", "");
        if( r2Path.length() > 0 && !r2Path.endsWith( File.separator)) {
            r2Path += File.separator;
        }
        Properties pr = new Properties();
        // load its last confuration
        try {
            FileInputStream fi = new FileInputStream( r2Path + "R2.properties");
            pr.load( fi);
            fi.close();
        } catch( Exception x) {
            LOG.info( "Can't load R2.properties: " + x);
        }    
        if( pr.isEmpty()) {
            // calculate default server name
            String localUrl = "http://" + hostName + ":" + localPort;
            String localName = hostName + localPort;
            // Set default cliendNode
            new SvcRequest( localName, 0, 0, null, null, 0).getClientNode();
            // configure
            pr.putAll(DEFAULT_CFG);
            pr.put( "01.Port", "" + localPort);
            pr.put( "02.LocalUrl", localUrl);
            if( rmtUrl != null && !rmtUrl.isEmpty()) {
                pr.put( "02.RemoteUrl", rmtUrl);
                pr.put( "07.Url", rmtUrl);
            }
        }
        LOG.trace( "Startup cfg " + pr);
        Map<String,String>m = new TreeMap();
        for( Object p: pr.keySet()) {
            m.put( "" + p, "" + pr.get( p));
        }
        return m;
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
    

    static final Map<String,String> DEFAULT_CFG = new HashMap();
    static {
        DEFAULT_CFG.put( "00", SvcCatalog.DISPATCHER_NAME);
        DEFAULT_CFG.put( "00.class", SimpleDispatcher.class.getName());
        DEFAULT_CFG.put( "00.DefaultServicePipeline", "SrvHtml,SrvJson,JdbcService,FileServices,SvcDeployer,CfgRelay,SvcAvailServers");
        DEFAULT_CFG.put( "00.Pipeline._Undefined_", "ClntJson,HttpClient");
        DEFAULT_CFG.put( "00.Pipeline.Udp", "ClntJson,UdpClient");
        //DEFAULT_CFG.put( "00.Pipeline.Test", "JavaScript,JavaScript,JavaScript,JavaScript,JavaScript,JavaScript,JavaScript,JavaScript,JavaScript,JavaScript,Echo");
        DEFAULT_CFG.put( "01", uy.com.r2.svc.conn.MicroHttpServer.class.getSimpleName());
        DEFAULT_CFG.put( "01.class", uy.com.r2.svc.conn.MicroHttpServer.class.getName());
        DEFAULT_CFG.put( "01.Port", "8015");
        DEFAULT_CFG.put( "02", uy.com.r2.svc.tools.SvcAvailServers.class.getSimpleName());
        DEFAULT_CFG.put( "02.class", uy.com.r2.svc.tools.SvcAvailServers.class.getName());
        // Default add 02.LocalName
        // Default add 02.LocalUrl
        // Default add 02.RemotelUrl
        DEFAULT_CFG.put( "03", "SrvHtml");
        DEFAULT_CFG.put( "03.class", uy.com.r2.svc.tools.ToHtml.class.getName());
        DEFAULT_CFG.put( "04", "SrvJson");
        DEFAULT_CFG.put( "04.class", uy.com.r2.svc.tools.Json.class.getName());
        DEFAULT_CFG.put( "04.ToSerial", "false");
        DEFAULT_CFG.put( "04.ProcessRequest", "true");
        DEFAULT_CFG.put( "04.ProcessResponse", "true");
        DEFAULT_CFG.put( "05", uy.com.r2.svc.conn.JdbcService.class.getSimpleName());
        DEFAULT_CFG.put( "05.class", uy.com.r2.svc.conn.JdbcService.class.getName());
        DEFAULT_CFG.put( "05.Driver", "org.apache.derby.jdbc.ClientDriver");
        DEFAULT_CFG.put( "05.URL", "jdbc:derby://localhost:1527/Test");
        DEFAULT_CFG.put( "05.User", "root");
        DEFAULT_CFG.put( "05.Password", "XXXX");
        DEFAULT_CFG.put( "05.Service.ListClients.SQL", "SELECT * FROM clients");
        DEFAULT_CFG.put( "05.Service.AddClient.SQL", "INSERT INTO clients(id,name) VALUES (?,?)");
        DEFAULT_CFG.put( "05.Service.AddClient.Params", "Id,Name");
        DEFAULT_CFG.put( "06", "ClntJson");
        DEFAULT_CFG.put( "06.class", Json.class.getName());
        DEFAULT_CFG.put( "06.ToSerial", "true");
        DEFAULT_CFG.put( "06.ProcessRequest", "true");
        DEFAULT_CFG.put( "06.ProcessResponse", "true");
        //DEFAULT_CFG.put( "06.Monitor", "true");
        DEFAULT_CFG.put( "07", HttpClient.class.getSimpleName());
        DEFAULT_CFG.put( "07.class", HttpClient.class.getName());
        // Default add 07.Url = rmtUrl
        DEFAULT_CFG.put( "08", FileServices.class.getSimpleName());
        DEFAULT_CFG.put( "08.class", FileServices.class.getName());
        DEFAULT_CFG.put( "09", SvcDeployer.class.getSimpleName());
        DEFAULT_CFG.put( "09.class", SvcDeployer.class.getName());       
        DEFAULT_CFG.put( "10", "UdpServer");
        DEFAULT_CFG.put( "10.class", uy.com.r2.svc.conn.UdpServer.class.getName());
        DEFAULT_CFG.put( "11", "UdpClient");
        DEFAULT_CFG.put( "11.class", uy.com.r2.svc.conn.UdpClient.class.getName());
        DEFAULT_CFG.put( "12", "CfgRelay");
        DEFAULT_CFG.put( "12.class", uy.com.r2.svc.tools.CfgRelay.class.getName());
        //DEFAULT_CFG.put( "13", uy.com.r2.svc.tools.FilePathSynchronizer.class.getSimpleName());
        //DEFAULT_CFG.put( "13.class", uy.com.r2.svc.tools.FilePathSynchronizer.class.getName());
        //DEFAULT_CFG.put( "13.Path.lib", "lib");
        //DEFAULT_CFG.put( "13.RemoteServer", "_Undefined_");
        DEFAULT_CFG.put( "97", uy.com.r2.svc.tools.JavaScript.class.getSimpleName());
        DEFAULT_CFG.put( "97.class", uy.com.r2.svc.tools.JavaScript.class.getName());
        DEFAULT_CFG.put( "97.Script", "function requestService( rq) { rq.put( \"Field2\", \"ValueTwo\" + rq.get( \"Field\")); }" + 
                "function responseService( rp) { }");
        DEFAULT_CFG.put( "98", uy.com.r2.svc.tools.Echo.class.getSimpleName());
        DEFAULT_CFG.put( "98.class", uy.com.r2.svc.tools.Echo.class.getName());
        //DEFAULT_CFG.put( "99", uy.com.r2.svc.tools.SvcTestingCaller.class.getSimpleName());
        //DEFAULT_CFG.put( "99.class", uy.com.r2.svc.tools.SvcTestingCaller.class.getName());
        //DEFAULT_CFG.put( "99.Pipeline", "Test");
    }

}


