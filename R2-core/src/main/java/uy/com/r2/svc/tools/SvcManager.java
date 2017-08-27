/* SvcManager.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
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


/** System manager module. 
 * Its functions are: <br>
 * - Startup a minimal configuration  <br>
 * - Keeps track of registered servers <br>
 * - Notify changes to master <br>
 * - If its a master notify changes to servers <br>
 * Basic rules: <br>
 * - The  master is the only one, and it sends KEEP_ALIVE all the time <br>
 * - Each node are master until receives a KEEP_ALIVE, then is a slave <br>
 * - If a node detects a time-out, set it self the Master, and inform that <br>
 * WORK IN PROGRESS !!!!
 * @author G.Camargo
 */
public class SvcManager implements AsyncService, CoreModule {
    public static final String SVC_ADDSERVER       = "AddServer";
    public static final String SVC_GETMASTER       = "GetMasterServer";
    public static final String SVC_GETSERVERSLIST  = "GetServersList";
    public static final String SVC_GETSERVICESLIST = "GetServicesList";
    public static final String SVC_KEEPALIVE       = "KeepAlive";
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
        SVC_REMOVESERVER, 
        SVC_REMOVEMDOULE, 
        SVC_SETMASTER, 
        SVC_SHUTDOWN,
        SVC_UPDATEMDOULE 
    };
    private static final Logger LOG = Logger.getLogger(SvcManager.class);
    private static SvcManager svcMgr;
    private final SvcCatalog catalog = SvcCatalog.getCatalog();
    private long masterTimeStamp = System.currentTimeMillis();
    private int nodeTxNr = 0;
    private static boolean stop = false;
    // Cfg
    private String localName = "localhost";
    private URL localUrl = null;
    private URL remoteUrl = null;
    private String masterName = null;
    private Map<String,String> knownServers = new TreeMap();
    private int keepAliveTimeout = 10000;
    private int keepAliveTime = 5000;
    
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
        l.add( new ConfigItemDescriptor( "LocalName", ConfigItemDescriptor.STRING,
                "Local node name", localName));
        l.add( new ConfigItemDescriptor( "LocalUrl", ConfigItemDescriptor.URL,
                "local URL of this  server", null));
        l.add( new ConfigItemDescriptor( "RemoteUrl", ConfigItemDescriptor.URL,
                "local URL of this  server", null));
        l.add( new ConfigItemDescriptor( "MasterName", ConfigItemDescriptor.STRING,
                "The primary node name", null));
        l.add( new ConfigItemDescriptor( "Server.*", ConfigItemDescriptor.STRING,
                "Known servers and URLs", null));
        l.add( new ConfigItemDescriptor( "KeepAliveTimeout", ConfigItemDescriptor.INTEGER,
                "Max.time to wait a KEEP_ALIVE, in mS", "7000"));
        l.add( new ConfigItemDescriptor( "KeepAliveTime", ConfigItemDescriptor.INTEGER,
                "Delay time to sleep waiting", "5000"));
        return l;
    }
    
    @Override
    public void startup( Configuration cfg) throws Exception {
        if( cfg.containsKey( "Commands.*")) {
            Map<String,String> m = cfg.getStringMap( "Commands.*.Cmd");
            for( String k: m.keySet()) {
                String cmd = m.get( k);
                command( cmd, cfg.getString( "Commands." + k + ".Server"),
                        cfg.getString( "Commands." + k + ".Url")); 
            }
        }
        localName = cfg.getString( "LocalName");
        localUrl = cfg.getUrl( "LocalUrl");
        remoteUrl = cfg.getUrl( "RemoteUrl");
        masterName = cfg.getString( "MasterServer");
        knownServers = cfg.getStringMap( "Server.*");
        keepAliveTimeout = cfg.getInt( "KeepAliveTimeout");
        keepAliveTime = cfg.getInt( "KeepAliveTime");
        if( remoteUrl != null && !knownServers.values().contains( "" + remoteUrl)) {
            knownServers.put( "<Unknown>", "" + remoteUrl);
        }
        LOG.debug( "Starting ............... " + localName);
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
        Map<String,Object> map = new TreeMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        } 
        map.put( "TimeStamp", "" + ( System.currentTimeMillis() -  masterTimeStamp));
        map.put( "KnownServers", knownServers);
        return map;
    }

    /** Execute command.
     * @param cmd Command verb
     * @param sn Server Name
     * @param url URL o Module name
     * @return Response Map with REMOVE sub-key (Multimap)
     * @throws Exception Error on command execution
     */
    private Map<String,List<Object>>  command( String cmd, Object sn, Object url) 
            throws Exception {
        LOG.trace("Command: " + cmd + " " + sn + " " + url);
        Map<String,List<Object>> resp = new TreeMap();
        try {
            switch( cmd) {
            case SVC_GETSERVERSLIST:
                for( String k: knownServers.keySet()) {
                    SvcMessage.addToPayload( resp, k, knownServers.get(  k));
                }
                break;
            case SVC_ADDSERVER:
                if( ( "" + url).equals( "" + remoteUrl)) {
                    knownServers.remove( "<Unknown>");
                }
                knownServers.put( "" + sn, "" + url);
                updateDestinations();
                break;
            case SVC_REMOVESERVER:
                knownServers.remove( "" + sn);
                updateDestinations();
                break;
            case SVC_GETMASTER: 
                SvcMessage.addToPayload( resp, "Name", masterName);
                if( masterName != null) {
                    SvcMessage.addToPayload( resp, "Url", knownServers.get( masterName));
                }
                break;
            case SVC_SETMASTER: 
                masterName = "" + sn;
                knownServers.put( "" + sn, "" + url);
                break;
            case SVC_UPDATEMDOULE:
                Configuration cfM = null;
                String mod = "" + url;
                // Get config from master
                SvcRequest r;
                if( masterName != null) {
                    r = new SvcRequest( localName, 0, nodeTxNr++, 
                            knownServers.get(masterName) + "/" + "GetModuleConfig", null, 1000);
                } else {
                    r = new SvcRequest( localName, 0, nodeTxNr++, 
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
                r = new SvcRequest( localName, 0, nodeTxNr++, "WaitCopyEnd", null, 1000);
                SvcCatalog.getDispatcher().callNext( r);
                // Update module config
                catalog.updateConfiguration( mod, cfM);
                break;
            case SVC_REMOVEMDOULE:
                catalog.uninstallModule("" + url);
                break;
            case SVC_KEEPALIVE:
                masterTimeStamp = System.currentTimeMillis();
                if( isMaster()) {  // Conflict! The master is the one that call KeepAlive
                    masterName = "" + sn;   // Realliation
                }
                break;
            case SVC_SHUTDOWN:
                stop = true;
                catalog.shutdown();
                break;
            case SVC_GETSERVICESLIST:
                Set<String> s = new TreeSet();
                int i = 0;
                for( String k: SERVICES) {
                    SvcMessage.addToPayload( resp, "Services", k);
                }
                break;
            default:
                 SvcMessage.addToPayload( resp, "Error", "Invalid command: " + cmd);
            }
        } catch( Exception x) {
            LOG.info("Command failed: " + cmd + " " + sn + " " + url, x);
            throw x;
        }
        LOG.trace( " resp = " + resp);
        return resp;
    }    

    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
        if( isMaster() && knownServers.size() > 0) {  // Choose another Master
            String newMaster = "" + knownServers.keySet().toArray()[ 0];
            LOG.trace( "newMaster=" + newMaster);
            notifyAllServers( SVC_SETMASTER, newMaster);
        }
        notifyAllServers( SVC_REMOVESERVER, localName);
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

    /** Method used by SvcDeployer to check status.
     * @return Boolean is alive
     */
    static boolean isAlive( ) {
        try {
            svcMgr.keepAlive();
            Thread.sleep( 1000);  // Avoid closed loop
        } catch( Exception ex) {
            LOG.warn( "Error on keepAlive", ex);
        }
        return !stop;
    }
    
    private void keepAlive() {
        if( isMaster()) {
            if( System.currentTimeMillis() > masterTimeStamp + keepAliveTime) {
                masterTimeStamp = System.currentTimeMillis();
                // Time to send a new KEEP_ALIVE
                notifyAllServers( SVC_KEEPALIVE, localName);
            }
        } else if( masterName != null) {   // There are a master, and is other
            // The master is alive?
            if( System.currentTimeMillis() > masterTimeStamp + keepAliveTimeout) {
                masterTimeStamp = System.currentTimeMillis();
                // This is the new master
                masterName = localName;
                notifyAllServers( SVC_SETMASTER, localName);
            }
        } else if( remoteUrl != null) {   // Unknown master, tyy to find one
            if( System.currentTimeMillis() > masterTimeStamp + keepAliveTime) {
                masterTimeStamp = System.currentTimeMillis();
                notifyAllServers( SVC_ADDSERVER, localName);
            }              
        }
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
        sb.append( localName);
        for( String s: knownServers.keySet()) {
            sb.append( ',');
            sb.append( s);
        }
        // Search for a Balancer
        ModuleInfo mi = getModInfoEndsWith( "Balancer");
        if( mi != null) {
            Configuration c = mi.getConfiguration();
            c.put( "Modules", sb.toString());
            mi.setConfiguration( c);
        }    
    }
    
    private boolean isMaster() {
        return ( masterName != null) &&
                masterName.equals( localName);
    }
    
    private void notifyAllServers( String command, String name) {
        for( String sn: knownServers.keySet()) {
            try {
                LOG.debug( " to notify" + sn + " from " + localName + " " + localUrl);
                if( sn.equals( localName)) {  // Do not notify it self!
                    continue;
                }
                SvcRequest r = new SvcRequest( localName, 0, nodeTxNr++, knownServers.get( sn) 
                        + "/" + command, null, 1000);
                r.put( "Name", name);
                r.put( "Url", localUrl);
                SvcCatalog.getDispatcher().callPipeline( "SvcManager", r);
            } catch( Exception ex) {
                LOG.debug( "Failed to notify " + command + " " + name, ex);
            }
        }
    }
  
}


