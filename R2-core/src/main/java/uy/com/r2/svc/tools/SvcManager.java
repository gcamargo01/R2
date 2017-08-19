/* SvcManager.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.net.InetAddress;
import java.net.URL;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import uy.com.r2.core.CoreModule;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.MBeanConfigurator;
import uy.com.r2.core.ModuleInfo;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
//import uy.com.r2.svc.FilePathSynchronizer;
import uy.com.r2.svc.conn.HttpClient;
import uy.com.r2.svc.conn.JdbcService;


/** Command interpreter service that manage servers of the domain.
 * WORK IN PROGRESS !!!!
 * @author G.Camargo
 */
public class SvcManager implements AsyncService, CoreModule {
    public static final String SVC_ADDSERVER       = "AddServer";
    public static final String SVC_GETMASTER       = "GetMasterServer";
    public static final String SVC_GETSERVERSLIST  = "GetServersList";
    public static final String SVC_GETSERVICESLIST = "GetServicesList";
    public static final String SVC_KEEPALIVE       = "KeepAlive";
    public static final String SVC_PERSISTCONFIG   = "PersistConfig";
    public static final String SVC_REMOVESERVER    = "RemoveServer";
    public static final String SVC_REMOVEMDOULE    = "RemoveModule";
    public static final String SVC_SETMASTER       = "SetMasterServer";
    public static final String SVC_SHUTDOWN        = "Shutdown";
    public static final String SVC_UPDATEMDOULE    = "UpdateModule";
    private static final String[] SERVICES = {
        SVC_ADDSERVER, 
        SVC_GETMASTER, 
        SVC_GETSERVERSLIST,
        SVC_GETSERVICESLIST,
        SVC_KEEPALIVE, 
        SVC_PERSISTCONFIG,
        SVC_REMOVESERVER, 
        SVC_REMOVEMDOULE, 
        SVC_SETMASTER, 
        SVC_SHUTDOWN,
        SVC_UPDATEMDOULE 
    };
    private static final Logger LOG = Logger.getLogger(SvcManager.class);
    private static SvcManager svcMgr;
    private static boolean stop = false;
    private final SvcCatalog catalog = SvcCatalog.getCatalog();
    private Configuration cfg = null; 
    private URL remoteUrl = null;
    private int localPort = 0;
    private String localNodeName = "localhost";
    private String masterNodeName = null;
    private Map<String,String> knownServers = new TreeMap();
    private int keepAliveTimeOut = 10000;
    private int keepAliveWait = 5000;
    private int receivedCommands = 0;
    private int errorsOnCommands = 0;
    private int tx = 0;
    private boolean firstTime = true;
    private long masterTimeStamp = System.currentTimeMillis();
    
    public SvcManager() {
        LOG.trace( "new");
        svcMgr = this;
    }
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "RemoteUrl", ConfigItemDescriptor.URL,
                "URL of a known server", null));
        l.add( new ConfigItemDescriptor( "Port", ConfigItemDescriptor.INTEGER,
                "Local server port to listen", null));
        l.add( new ConfigItemDescriptor( "ServerName", ConfigItemDescriptor.STRING,
                "Local node name", null));
        l.add( new ConfigItemDescriptor( "MasterName", ConfigItemDescriptor.STRING,
                "The primary node name", null));
        l.add( new ConfigItemDescriptor( "Server.*", ConfigItemDescriptor.STRING,
                "Known servers and URLs", null));
        l.add( new ConfigItemDescriptor( "Commands.*.Cmd", ConfigItemDescriptor.STRING,
                "Commands to execute in order", null));
        l.add( new ConfigItemDescriptor( "Commands.*.Server", ConfigItemDescriptor.STRING,
                "Server parameter of commands to execute (optional)", null));
        l.add( new ConfigItemDescriptor( "Commands.*.Server", ConfigItemDescriptor.STRING,
                "Url parameter of commands to execute (optional)", null));
        return l;
    }
    
    @Override
    public void startup( Configuration cfg) throws Exception {
        this.cfg = cfg;
        receivedCommands = 0;
        errorsOnCommands = 0;
        if( cfg.containsKey( "Commands.*")) {
            Map<String,String> m = cfg.getStringMap( "Commands.*.Cmd");
            for( String k: m.keySet()) {
                String cmd = m.get( k);
                command( cmd, cfg.getString( "Commands." + k + ".Server"),
                        cfg.getString( "Commands." + k + ".Url")); 
            }
        }
        // calculate default server name
        String defaultName = InetAddress.getLocalHost().getHostName();
        String localUrl = "http://" + defaultName;
        // update cfg
        remoteUrl = cfg.getURL( "RemoteUrl");
        localPort = cfg.getInt( "Port");
        if( localPort > 0) {
            localUrl += ":" + localPort;
            defaultName += localPort;
        }
        localNodeName = cfg.getString( "ServerName", defaultName);
        masterNodeName = cfg.getString( "MasterServer", localNodeName);
        knownServers = cfg.getStringMap( "Server.*");
        knownServers.put( localNodeName, localUrl);
        LOG.debug( "ServerName: " + localNodeName);
        LOG.debug( "knownServers: " + knownServers);
        if( firstTime) {
            basicPipe();
            firstTime = false;
        }
    }

    /** Invocation dispatch phase.
     * It may: <br>
     * (1) create and return a SvcResponse itself, <br>
     * (2) return a SvcRequest to dispatch to the next service module, or <br>
     * (3) return NULL when there aren't next module to call, or <br>
     * (4) throw a Exception to explicit set the module that originates <br>
     * the failure.
     * @param req Invocation message from caller
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        String cmd = req.getServiceName();
        Map<String,List<Object>> m = command( cmd, req.get( "Name"), req.get( "Url"));
        int cr = ( m.get( "Error") != null)? 100: 0;
        SvcResponse resp = new SvcResponse( m, cr, req);
        LOG.trace( "Command response: " + resp);
        return resp;
    }

    /** Process a response phase.
     * The core calls this method to process a response. The module implementation
     * may call Dispatcher.processResponse( SvcResponse) to push the processed 
     * response.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcRequest from next module, or synthetic one  
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
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
        Map<String,Object> m = new TreeMap();
        m.put( "Commands", receivedCommands);
        m.put( "CommandErrors", errorsOnCommands);
        m.put( "KnownServers", knownServers);
        return m;
    }

    /** Execute command.
     * @param cmd Command verb
     * @param sn Server Name
     * @param param URL o Module name
     * @return Response Map with REMOVE sub-key (Multimap)
     * @throws Exception Error on command execution
     */
    private Map<String,List<Object>>  command( String cmd, Object sn, Object param) 
            throws Exception {
        LOG.trace( "Command: " + cmd + " " + sn + " " + param);
        ++receivedCommands;
        Map<String,List<Object>> resp = new TreeMap();
        try {
            switch( cmd) {
            case SVC_GETSERVERSLIST:
                for( String k: knownServers.keySet()) {
                    SvcMessage.addToPayload( resp, k, knownServers.get(  k));
                }
                break;
            case SVC_ADDSERVER:
                knownServers.put( "" + sn, "" + param);
                updateDestinations();
                break;
            case SVC_REMOVESERVER:
                knownServers.remove( "" + sn);
                updateDestinations();
                break;
            case SVC_GETMASTER: 
                SvcMessage.addToPayload( resp, "Name", masterNodeName);
                SvcMessage.addToPayload( resp, "Url", knownServers.get( masterNodeName));
                break;
            case SVC_SETMASTER: 
                masterNodeName = "" + sn;
                break;
            case SVC_UPDATEMDOULE:
                Configuration cfM = null;
                String mod = "" + param;
                // Get config from master
                SvcRequest r;
                if( masterNodeName != null) {
                    r = new SvcRequest( localNodeName, 0, tx++, 
                            knownServers.get( masterNodeName) + "/" + "GetModuleConfig", null, 1000);
                } else {
                    r = new SvcRequest( localNodeName, 0, tx++, 
                            remoteUrl + "/" + "GetModuleConfig", null, 1000);
                }    
                r.put( "Module", mod);
                SvcResponse rp = SvcCatalog.getDispatcher().callNext( r);
                if( rp.getResultCode() > 0) {
                    cfM = new Configuration();
                    for( String k: rp.getPayload().keySet()) {
                        cfM.put( k, rp.get( k));
                    }
                }
                if( cfM == null) {
                    throw new Exception( "Module " + mod + " not found");
                }
                // Wait JAR copy
                r = new SvcRequest( localNodeName, 0, tx++, "WaitCopyEnd", null, 1000);
                SvcCatalog.getDispatcher().callNext( r);
                // Update module config
                catalog.updateConfiguration( mod, cfM);
                break;
            case SVC_REMOVEMDOULE:
                catalog.uninstallModule( "" + param);
                break;
            case SVC_KEEPALIVE:
                masterTimeStamp = System.currentTimeMillis();
                if( isMaster()) {  // Conflict! The master is the one that call KeepAlive
                    masterNodeName = "" + sn;
                }
                break;
            case SVC_SHUTDOWN:
                stop = true;
                catalog.shutdown();
                break;
            case SVC_PERSISTCONFIG:
                for( String s:knownServers.keySet()) {
                    //for( )
                    
                }
                break;
            case SVC_GETSERVICESLIST:
                Set<String> s = new TreeSet();
                int i = 0;
                for( String k: SERVICES) {
                    SvcMessage.addToPayload( resp, "Services", k);
                }
                break;
            default:
                ++errorsOnCommands;
                 SvcMessage.addToPayload( resp, "Error", "Invalid command: " + cmd);
            }
        } catch( Exception x) {
            LOG.info( "Command failed: " + cmd + " " + sn + " " + param, x);
            ++errorsOnCommands;
            throw x;
        }
        LOG.trace( " resp = " + resp);
        return resp;
    }    

    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
        if( isMaster()) {  // Choose another Master
            String newMaster = "" + knownServers.keySet().toArray()[ 0];
            LOG.trace( "newMaster=" + newMaster);
            notifyAllServers( SVC_SETMASTER, newMaster);
        }
        notifyAllServers( SVC_REMOVESERVER, localNodeName);
        stop = true;
    }

    /** Local method used by catalog to notify local changes.
     * @param module Module name 
     */
    public static void onModuleUpdate( String module) {
        MBeanConfigurator.moduleUpdate( module);
        try {
            if( svcMgr == null) {
                return;
            }
            if( svcMgr.isMaster()) {
                svcMgr.notifyAllServers( SVC_UPDATEMDOULE, module);
            } 
        } catch( Exception ex) {
            LOG.warn( "Error on notify moduleUpdate '" + module + "'", ex);
        }
    }

    /** Local method used by catalog to notify local changes.
     * @param module Module name 
     */
    public static void onModuleUndeploy( String module) {
        MBeanConfigurator.moduleUndeploy( module);
        try {
            if( svcMgr != null && svcMgr.isMaster()) {
                svcMgr.notifyAllServers( SVC_REMOVEMDOULE, module);
            } 
        } catch( Exception ex) {
            LOG.warn( "Error on notify moduleDeploy '" + module + "'", ex);
        }
    }

    /** Local method used by SvcDeployer to check status.
     * @return Boolean is alive
     */
    static boolean isAlive( ) {
        try {
            if( svcMgr.isMaster()) {
                svcMgr.notifyAllServers( SVC_KEEPALIVE, svcMgr.localNodeName);
            } else {  // The manager is alive?
                if( System.currentTimeMillis() > svcMgr.masterTimeStamp + svcMgr.keepAliveTimeOut) {
                    // Contact lost w/Master
                    // !!!!
                }
            }
            Thread.sleep( svcMgr.keepAliveWait);
        } catch( Exception ex) {
            LOG.warn( "Error on keepAlive", ex);
        }
        return !stop;
    }
    
    /* Search for a module by its class name */
    private ModuleInfo getModInfoEndsWith( String className) {
        LOG.trace( "getModInfoEndsWith " + className);
        ModuleInfo mi = null;
        for( String n: catalog.getModuleNames()) {
            mi = catalog.getModuleInfo( n);
            Configuration c = mi.getConfiguration();
            if( c.getString( "class").endsWith( className)) {
                LOG.trace( "                   --> " + n);
                return mi;
            }
        }
        LOG.trace( "                     --> null");
        return null;
    }
    
    /** This method in executed on every change of the known servers */
    private void updateDestinations() throws Exception {
        // Prepare String
        StringBuilder sb = new StringBuilder();
        for( String s: knownServers.keySet()) {
            sb.append( ',');
            sb.append( s);
        }
        String ss = sb.toString();
        if( !ss.isEmpty()) {
            ss = ss.substring( 1);
        }
        LOG.trace( "ss=" + ss);
        // Search for a Balancer
        ModuleInfo mi = getModInfoEndsWith( "Balancer");
        if( mi != null) {
            Configuration c = mi.getConfiguration();
            c.put( "Modules", ss);
            mi.setConfiguration( c);
        }    
    }
    
    private boolean isMaster() {
        return ( masterNodeName != null) &&
                masterNodeName.equals( localNodeName) &&
                !knownServers.isEmpty();
    }
    
    private void notifyAllServers( String command, String name) {
        for( String sn: knownServers.keySet()) {
            try {
                if( sn.equals( localNodeName)) {  // Do not notify it self!
                    continue;
                }
                SvcRequest r = new SvcRequest( localNodeName, 0, tx++, knownServers.get(sn) 
                        + "/" + command, null, 1000);
                r.put( "Name", name);
                SvcCatalog.getDispatcher().callNext( r);
            } catch( Exception ex) {
                LOG.debug( "Failed to notify " + command + " " + name, ex);
            }
        }
    }

    private void basicPipe() {
        try {
            LOG.debug( "basicPipe " + remoteUrl + " " + localPort);
            Configuration c;
            
            c = new Configuration();
            c.put( "DefaultServicePipeline", "HTML,JDBC,SvcDeployer,SvcManager");
            c.put( "Pipeline.SvcManager", "FileServices,Serializer,HttpClient");
            deploy( SvcCatalog.DISPATCHER_NAME, c);

            c = new Configuration();
            c.put( "class", MiniHttpServer.class.getName());
            if( localPort > 0) {
                c.put( "Port", localPort);
            }
            deploy( MiniHttpServer.class.getSimpleName(), c);

            c = new Configuration();
            c.put( "class", ToHtml.class.getName());
            deploy( "HTML", c);
/*            
            c = new Configuration();
            c.put( "class", Json.class.getName());
            c.put( "ToSerial", false);
            c.put( "ProcessRequest", false);
            deploy( "DeSerializer", c);

            c = new Configuration();
            c.put( "class", FilePathSynchronizer.class.getName());
            c.put( "Path.lib", "lib");
            c.put( "RemoteServer", remoteUrl);
            deploy( "Replicator", c);
*/
            c = new Configuration();
            c.put( "class", FileServices.class.getName());
            deploy( "FileServices", c);

            c = new Configuration();
            c.put( "class", Json.class.getName());
            c.put( "ProcessRequest", false);
            deploy( "Serializer", c);

            c = new Configuration();
            c.put( "class", JdbcService.class.getName());
            c.put( "Driver", "org.apache.derby.jdbc.ClientDriver");
            c.put( "URL", "jdbc:derby://localhost:1527/Test");
            c.put( "User", "root");
            c.put( "Password", "XXXX");
            c.put( "Service.ListClients.SQL", "SELECT * FROM clients");
            c.put( "Service.AddClient.SQL", "INSERT INTO clients(id,name) VALUES (?,?)");
            c.put( "Service.AddClient.Params", "Id,Name");
            deploy( "JDBC", c);

            c = new Configuration();
            c.put( "class", HttpClient.class.getName());
            deploy( "HttpClient", c);

        } catch( Exception ex) {
            LOG.warn( "Failed to start basicPipe", ex);
        }
    }
    
    private void deploy( String module, Configuration cfg) throws Exception {
        LOG.trace( "deploy " + module + " " + cfg);
        SvcRequest rq = new SvcRequest( localNodeName, tx++, 0, "DeployModule", null, 1000);
        rq.put( "Module", module);
        for( String n: cfg.getStringMap( "*").keySet()) {
            rq.put( n, cfg.getString( n));            
        }
        catalog.getDispatcher().callService( "SvcDeployer", rq);
    }

    /* Mimimal tests *
    public static void main( String args[]) throws Exception {
        SvcManager m = new SvcManager();
        SvcCatalog.getCatalog().installModule( "SvcManager", m, null);
        Configuration c = new Configuration();
        c.put( "Commands.2.Cmd", "AddServer");
        c.put( "Commands.2.Server", "SRV002");
        c.put( "Commands.1.Cmd", "AddServer");
        c.put( "Commands.1.Server", "SRV001");
        m.setConfiguration( c);
    }
    /**/

}


