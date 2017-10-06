/* SvcDeployer.java */
package uy.com.r2.svc.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import uy.com.r2.core.SimpleDispatcher;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.svc.conn.HttpClient;
import uy.com.r2.svc.conn.JdbcService;

/** Command interpreter service that deploy and un-deploy modules.
 * This is a in-memory Deployer, that allows system remote control,
 * and it startup a basic or initial service pipe. <p>
 * Its: functions include: <br>
 * - Try to load last configuration from R2.properties file <br>
 * - Startup a minimal configuration <br>
 * - Configure basic items: LocalName, LocalPort, RemoteUrl <br>
 * - And execute commands as a in-memory deployer <br>
 * @author G.Camargo
 */
public class SvcDeployer implements AsyncService {
    public static final String SVC_DEPLOYMODULE    = "DeployModule";
    public static final String SVC_GETMODULECONFIG = "GetModuleConfig";
    public static final String SVC_GETMODULELIST   = "GetModulesList";
    public static final String SVC_GETMODULESTATUS = "GetModuleStatus";
    public static final String SVC_GETSERVICESLIST = "GetServicesList";
    public static final String SVC_SETMODULECONFIG = "SetModuleConfig";
    public static final String SVC_PERSISTCONFIG   = "PersistConfig";
    public static final String SVC_RESTARTMODULE   = "RetartModule";
    public static final String SVC_UNDEPLOYMODULE  = "UndeployModule";
    public static final String TAG_ACTUALCONFIG = "_Configuration";
    private static final String DEPLOYER_NAME = SvcDeployer.class.getSimpleName();
    private static final String[] SERVICES = {
            SVC_DEPLOYMODULE,    SVC_GETMODULECONFIG, SVC_GETMODULELIST,   
            SVC_GETMODULESTATUS, SVC_SETMODULECONFIG, SVC_PERSISTCONFIG,   
            SVC_RESTARTMODULE,   SVC_UNDEPLOYMODULE
    };
    private static final List<String> COMMANDS = Arrays.asList( SERVICES);
    private static final Logger LOG = Logger.getLogger( SvcDeployer.class);

    private final SvcCatalog catalog = SvcCatalog.getCatalog();
    private int receivedCommands = 0;
    private int errorsOnCommands = 0;
    
    /** Constructor.
     * @throws Exception Unexpected failure
     */
    SvcDeployer() throws Exception {
        LOG.info( "Deployer started");
        if( catalog.getModuleInfo( DEPLOYER_NAME) == null) {
            // Register itself in the system catalog
            catalog.installModule( DEPLOYER_NAME, this, null);
        }
    }
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        return null;
    }
    
    private void updateCfg( Configuration cfg) throws Exception {
        if( !cfg.isChanged()) {
            return;
        }
        receivedCommands = 0;
        errorsOnCommands = 0;
        if( cfg.containsKey( "Commands.*")) {
            Map<String,String> m = cfg.getStringMap( "Commands.*.Cmd");
            for( String k: m.keySet()) {
                String cmd = m.get( k);
                command( cmd, cfg.getString( "Commands." + k + ".Module"), cfg); 
            }
        }
        cfg.resetChanged();
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
        updateCfg( cfg);
        LOG.trace( "onRequest " + req + " " + cfg);
        // Is there a command?
        if( COMMANDS.contains( req.getServiceName())) {
            String cmd = req.getServiceName();
            String md = "" + req.get( "Module");
            Configuration c = new Configuration();  // distint configuration
            for( String k: req.getPayload().keySet()) {
                if( !k.equals( "Module")) {
                    c.put( k, req.get( k));  // Put single value
                }
            }
            SvcResponse resp = new SvcResponse( 0, req);
            resp.getPayload().putAll( command( cmd, md, c));
            LOG.trace( "Command response: " + resp);
            return resp;
        }
        // Not a command, go on, return the same req.
        return req;
    }

    /** Process a response phase.
     * The core calls this method to process a response. The module implementation
     * may call Dispatcher.processResponse( SvcResponse) to push the processed 
     * response.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcRequest from next module, or synthetic one  
     * @param cfg Module configuration
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        updateCfg( cfg);
        SvcRequest req = resp.getRequest();
        if( req.getServiceName().equals( SVC_GETSERVICESLIST)) {
            for( String s: SERVICES) {
                resp.add( "Services", s);
            }
        } 
        return resp;  // nothing to do
    }

    /** Service call.
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> map = new TreeMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        } 
        map.put( "Commands", receivedCommands);
        map.put( "CommandErrors", errorsOnCommands);
        return map;
    }

    /** Execute command.
     * @param cmd Command verb
     * @param mn Module Name
     * @param cfg Configuration
     * @return Response Map
     * @throws Exception Error on command execution
     */
    private Map<String,List<Object>> command( String cmd, String mn, Configuration cfg) 
            throws Exception {
        LOG.trace( "Command: " + cmd + " " + mn + " " + cfg);
        ++receivedCommands;
        Map<String,List<Object>> mmap = new TreeMap();
        try {
            switch( cmd) {
            case SVC_DEPLOYMODULE:    
                catalog.installModule( mn, cfg);
                break;
            case SVC_UNDEPLOYMODULE:    
                catalog.uninstallModule( mn);
                break;
            case SVC_GETMODULELIST:
                int i = 0;
                for( String s: catalog.getModuleNames()) {
                    SvcMessage.addToMap( mmap, "Modules", s);                    
                }
                break;
            case SVC_SETMODULECONFIG:    
                catalog.updateConfiguration( mn, cfg);
                break;
            case SVC_GETMODULECONFIG:
                Map<String,Map<String,Object>> mm = catalog.getModuleInfo( mn).getDetailedConfiguration();
                for( String k: mm.keySet()) {
                    SvcMessage.addToMap( mmap, k, mm.get( k)); 
                } 
                SvcMessage.addToMap( mmap, TAG_ACTUALCONFIG, 
                        catalog.getModuleInfo( mn).getConfiguration().getStringMap( "*")); 
                break;
            case SVC_GETMODULESTATUS:
                Map<String,Object> mo = catalog.getModuleInfo( mn).getStatusVars();
                for( String k: mo.keySet()) {
                    SvcMessage.addToMap( mmap, k, mo.get( k));
                }
                break;
            case SVC_PERSISTCONFIG:
                persistConfig();
                break;
            case SVC_RESTARTMODULE:
                catalog.getModuleInfo( mn).setConfiguration( cfg);
                break;
            default:
                ++errorsOnCommands;
                throw new Exception( "Invalid command: " + cmd); 
            }
        } catch( Exception x) {
            LOG.info( "Command error: " + x, x);
            ++errorsOnCommands;
            throw x;
        }
        LOG.debug( "command " + cmd + " mmap=" + mmap);
        return mmap;
    }    

    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    /** Entry point as a main Deployer.
     * @param args Standard arguments: Local_Port Remote_Url
     */
    public static void main( String args[]) {
        try {
            SvcDeployer m = new SvcDeployer();
            String rmtUrl = "http://localhost:8016";
            int localPort = 8015;
            switch( args.length) {
            case 2:
               rmtUrl = args[ 1];
            case 1:
               localPort = Integer.parseInt( args[ 0]);
            }
            LOG.trace( "start " + localPort + " " + rmtUrl);
            // Read previous configuration o basic config
            Properties pr = readConfig( localPort, rmtUrl);
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
                m.command( SVC_DEPLOYMODULE, mod, c);
            }
            // Wait till stop
            while( !(Boolean)SvcCatalog.getDispatcher().getStatusVars().get( "Stopped")) {
                Thread.sleep( 1000);
            }
            LOG.info( "Stopped");
        } catch ( Exception ex ) {
            System.err.println( "Error " + ex);
            ex.printStackTrace( System.err);
        }
    }

    private void persistConfig() throws Exception {
        Properties pr = new Properties();
        int n = 0;
        TreeSet<String> ts = new TreeSet( catalog.getModuleNames());
        ts.remove( DEPLOYER_NAME);  // Auto-started itself
        for( String m: ts) {
            pr.put( "Module." + n, m);
            Configuration c = catalog.getModuleInfo( m).getConfiguration();
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
    
    private static Properties readConfig( int localPort, String rmtUrl) throws Exception {
        String r2Path = System.getProperty( "R2_PATH", "");
        if( r2Path.length() > 0 && !r2Path.endsWith( File.separator)) {
            r2Path += File.separator;
        }
        Properties pr = new Properties();
        try {
            FileInputStream fi = new FileInputStream( r2Path + "R2.properties");
            pr.load( fi);
            fi.close();
        } catch( Exception x) {
            LOG.info( "Can't load R2.properties: " + x);
        }    
        if( pr.isEmpty()) {
            // calculate default server name
            String hostName = InetAddress.getLocalHost().getHostName();
            String localUrl = "http://" + hostName + ":" + localPort;
            String localName = hostName + localPort;
            // configure
            pr.putAll( DEFAULT_PIPE);
            pr.put( "0.Port", "" + localPort);
            pr.put( "1.LocalName", localName);
            pr.put( "1.LocalUrl", localUrl);
            if( rmtUrl != null && !rmtUrl.isEmpty()) {
                pr.put( "1.RemoteUrl", rmtUrl);
                pr.put( "7.Url", rmtUrl);
            }
        }
        LOG.trace( "Init Pipe =" + pr);
        return pr;
    }

    static final Map<String,String> DEFAULT_PIPE = new HashMap();
    static {
        DEFAULT_PIPE.put( "Module.0", MiniHttpServer.class.getSimpleName());
        DEFAULT_PIPE.put( "0.class", MiniHttpServer.class.getName());
        DEFAULT_PIPE.put( "0.Port", "8015");
        DEFAULT_PIPE.put( "Module.1", SvcAvailServers.class.getSimpleName());
        DEFAULT_PIPE.put( "1.class", SvcAvailServers.class.getName());
        // Default add 1.LocalName
        // Default add 1.LocalUrl
        // Default add 1.RemotelUrl
        DEFAULT_PIPE.put( "Module.2", SvcCatalog.DISPATCHER_NAME);
        DEFAULT_PIPE.put( "2.class", SimpleDispatcher.class.getName());
        DEFAULT_PIPE.put( "2.DefaultServicePipeline", "ToHtml,ToJson,JdbcService,SvcDeployer,SvcAvailServers");
        DEFAULT_PIPE.put( "2.Pipeline._Undefined_", "FromJson,HttpClient");
        DEFAULT_PIPE.put( "Module.3", ToHtml.class.getSimpleName());
        DEFAULT_PIPE.put( "3.class", ToHtml.class.getName());
        DEFAULT_PIPE.put( "Module.4", "ToJson");
        DEFAULT_PIPE.put( "4.class", Json.class.getName());
        DEFAULT_PIPE.put( "4.ToSerial", "false");
        DEFAULT_PIPE.put( "4.ProcessRequest", "false");
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
        DEFAULT_PIPE.put( "Module.6", "FromJson");
        DEFAULT_PIPE.put( "6.class", Json.class.getName());
        DEFAULT_PIPE.put( "6.ToSerial", "true");
        DEFAULT_PIPE.put( "6.ProcessRequest", "false");
        DEFAULT_PIPE.put( "6.ProcessResponse", "true");
        DEFAULT_PIPE.put( "Module.7", HttpClient.class.getSimpleName());
        DEFAULT_PIPE.put( "7.class", HttpClient.class.getName());
        // Default add 7.lUrl
    }

}


