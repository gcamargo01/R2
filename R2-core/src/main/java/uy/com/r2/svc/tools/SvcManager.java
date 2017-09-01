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
import uy.com.r2.svc.conn.HttpClient;


/** System manager module. 
 * Its functions are: <br>
 * - Startup a minimal configuration  <br>
 * - Keeps track of registered servers <br>
 * - Notify changes to master <br>
 * - If its a master notify changes to servers <br>
 * Basic rules: <br>
 * - The  master is the only one, and it sends KEEP_ALIVE all the time (KeepAliveDelay mS)<br>
 * - Each node are master until receives a KEEP_ALIVE, an then is a slave <br>
 * - If a node detects a time-out (KeepAliveTimeout mS), set it self the Master, and inform that <br>
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
    //public static final String SVC_STANDALONE      = "CfgStandalone";
    //public static final String SVC_COMMITCFG       = "CfgCommit";
    //public static final String SVC_ROLLBACKCFG     = "CfgRollback";
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
    private static final String UNDEFINED = "_Undefined_";
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
    private int keepAliveDelay = 5000;
    private String outPipeline = "FromJson,HttpClient_";
    
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
        l.add( new ConfigItemDescriptor( "OuterPipeline", ConfigItemDescriptor.STRING,
                "Pipeline to remote server", outPipeline));
        l.add( new ConfigItemDescriptor( "Server.*", ConfigItemDescriptor.STRING,
                "Known servers and URLs", null));
        l.add( new ConfigItemDescriptor( "KeepAliveTimeout", ConfigItemDescriptor.INTEGER,
                "Max.time to wait a KEEP_ALIVE, in mS", "10000"));
        l.add( new ConfigItemDescriptor( "KeepAliveDelay", ConfigItemDescriptor.INTEGER,
                "Delay time to sleep waiting", "5000"));
        return l;
    }
    
    @Override
    public void startup( Configuration cfg) throws Exception {
        localName = cfg.getString( "LocalName");
        localUrl = cfg.getUrl( "LocalUrl");
        remoteUrl = cfg.getUrl( "RemoteUrl");
        masterName = cfg.getString( "MasterServer", localName);
        knownServers = cfg.getStringMap( "Server.*");
        outPipeline = cfg.getString( "OuterPipeline");
        keepAliveTimeout = cfg.getInt( "KeepAliveTimeout");
        keepAliveDelay = cfg.getInt( "KeepAliveDelay");
        if( !knownServers.containsKey( localName)) {
            knownServers.put( localName, "" + localUrl);
        }
        if( remoteUrl != null && !knownServers.values().contains( "" + remoteUrl)) {
            knownServers.put( UNDEFINED, "" + remoteUrl);
        }
        masterTimeStamp = System.currentTimeMillis();
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
        String mod = null;
        String url = null;
        Map<String,String> map = null;
        try {
            mod = ( String)req.get( "Name");
            url = ( String)req.get( "Url");
            map = ( Map<String,String>)req.get( "Servers");
        } catch( Exception x) {
            LOG.info( "Parameter cast error con command " + cmd, x);
        }
        Map<String,List<Object>> m = command( cmd, mod, url, map);
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
     * @param name Module Name or Server name
     * @param url URL as String
     * @param map Server map (name, Url)
     * @return Response Map with REMOVE sub-key (Multimap)
     * @throws Exception Error on command execution
     */
    private Map<String,List<Object>> command( String cmd, String name, String url, 
            Map<String,String> map) throws Exception {
        LOG.trace("Command: " + cmd + " " + name + " " + url + " " + map);
        Map<String,List<Object>> resp = new TreeMap();
        try {
            switch( cmd) {
            case SVC_ADDSERVER:
                if( url.equals( "" + remoteUrl)) {
                    knownServers.remove( UNDEFINED);
                }
                knownServers.put( name, url);
                updateDestinations();
            case SVC_GETSERVERSLIST:
                for( String k: knownServers.keySet()) {
                    SvcMessage.addToPayload( resp, k, knownServers.get(  k));
                }
                break;
            case SVC_REMOVESERVER:
                knownServers.remove(name);
                updateDestinations();
                break;
            case SVC_GETMASTER: 
                if( masterName != null && !masterName.isEmpty()) {
                    SvcMessage.addToPayload( resp, "Name", masterName);
                    SvcMessage.addToPayload( resp, "Url", knownServers.get( masterName));
                } else {
                    SvcMessage.addToPayload( resp, "Error", "Undefined Master");
                }
                break;
            case SVC_SETMASTER: 
                if( name != null) {
                    masterTimeStamp = System.currentTimeMillis();
                    masterName = name;
                    knownServers.put( name, url);
                    updateDestinations();
                }
                break;
            case SVC_UPDATEMDOULE:
                Configuration cfM = null;
                // Get config from master, the Module cfg.
                if( !isMaster()) {
                    SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++,"GetModuleConfig", null, 1000);
                    rq.put( "Module", name);
                    SvcResponse rp = SvcCatalog.getDispatcher().callPipeline( masterName, rq);
                    if( rp.getResultCode() != 0) {
                        LOG.warn("Failed to update " +  name + ": " + cfM);
                        SvcMessage.addToPayload( resp, "Error", "Failed UpdateModule: " + cmd);
                        break;
                    }
                    cfM = new Configuration();
                    Map<String,String> cm = ( Map<String,String>)rp.get( "_Configuration");
                    if( cm != null || cm.isEmpty()) {
                        LOG.warn( "Empty config. on UpdateModule " + name);
                        break;
                    }
                    for( String k: cm.keySet()) {
                        cfM.put( k, rp.get( k));
                    }
                    // Wait JAR copy
                    //r = new SvcRequest( localName, 0, nodeTxNr++, "WaitCopyEnd", null, 1000);
                    //SvcCatalog.getDispatcher().callNext( rq);
                    // Update module config
                    LOG.trace( "update " +  name + ": " + cfM);
                    catalog.updateConfiguration(name, cfM);
                }
                break;
            case SVC_REMOVEMDOULE:
                catalog.uninstallModule( name);
                break;
            case SVC_KEEPALIVE:
                masterTimeStamp = System.currentTimeMillis();
                if( isMaster()) {  // Conflict! The master is the one that call KeepAlive
                    masterName = name;   // Ok, this is the newMaster
                    LOG.info( "Released as Master by " + name);
                    // Now check if all the knownServers are known by new master
                    for( String s: knownServers.keySet()) {
                        if( !map.containsKey( s)) {
                            SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++, 
                                    SVC_ADDSERVER, null, 1000);
                            rq.put( "Name", localName);
                            rq.put( "Url", localUrl);
                            SvcCatalog.getDispatcher().callPipeline( masterName, rq);
                        }
                    }
                } 
                // Update the knownServers from master
                for( String s: map.keySet()) {
                    if( !knownServers.containsKey( s)) {
                        knownServers.put( "" + s, "" + map.get( s));
                    }
                }
                // The master may KEEP_ALIIVE w/o know the real localname & localurl
                SvcMessage.addToPayload( resp, "Name", localName);
                SvcMessage.addToPayload( resp, "Url", "" + localUrl);
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
            LOG.info( "Command failed: " + cmd + " " + name + " " + url, x);
            throw x;
        }
        LOG.trace( " resp = " + resp);
        return resp;
    }    

    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
        if( isMaster() && knownServers.size() > 1) {  // Choose another Master
            for( String n: knownServers.keySet()) {
                if( n.equals( localName)) {
                    continue;
                }
                LOG.trace( "newMaster=" + n);
                notifyAllServers( SVC_SETMASTER, n);
                break;
            }
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
            if( svcMgr != null && svcMgr.isMaster()) {
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
    public static boolean isAlive( ) {
        try {
            svcMgr.keepAlive();
            Thread.sleep( 1000);  // Avoid closed loop
        } catch( Exception ex) {
            LOG.warn( "Error on keepAlive", ex);
        }
        return !stop;
    }
    
    private void keepAlive() {
        long t = System.currentTimeMillis(); 
        if( isMaster()) {  
            if( t > masterTimeStamp + keepAliveDelay) {  // Time to send?
                masterTimeStamp = t;
                notifyAllServers( SVC_KEEPALIVE, localName);
            }
        } else {  // There is a master there
            // The master is alive?
            if( t > masterTimeStamp + keepAliveTimeout) {  // Timeout?
                masterTimeStamp = t;
                masterName = localName;   // This is the new master
                notifyAllServers( SVC_SETMASTER, localName);
            }
            if( t > masterTimeStamp + keepAliveDelay &&  // Time to contact?
                    remoteUrl != null) {   // And a there is a URL to contact
                try {
                    // Get master Name and Url
                    SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++, 
                            SVC_GETMASTER, null, 1000);
                    SvcResponse rs = SvcCatalog.getDispatcher().callPipeline( UNDEFINED, rq);
                    if( rs.getResultCode() != 0) {
                        LOG.warn( "Failed GetMaster from " + remoteUrl);
                        return;
                    }    
                    masterName = "" + rs.get( "Name");
                    knownServers.put( masterName, "" + rs.get( "Url"));
                    updateDestinations();
                    // Add to local server to Master and get server list
                    rq = new SvcRequest( localName, 0, nodeTxNr++, 
                            SVC_ADDSERVER, null, 1000);
                    rq.put( "Name", localName);
                    rq.put( "Url", localUrl);
                    rs = SvcCatalog.getDispatcher().callPipeline( masterName, rq);
                    if( rs.getResultCode() != 0) {
                        LOG.warn( "Failed AddServer to Master " + masterName);
                        return;
                    }    
                    masterTimeStamp = t;
                    knownServers.remove( UNDEFINED);
                    for( String sn: rs.getPayload().keySet()) {
                        knownServers.put( sn, "" + rs.get( sn));
                    }
                    updateDestinations();
                } catch( Exception x) {
                    LOG.info( "Failed to contact Master on " + remoteUrl, x);
                }
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
                return mi;
            }
        }
        return null;
    }
    
    /** This method in executed on every change of the known servers */
    private void updateDestinations() throws Exception {
        LOG.trace( "updateDestinations " + knownServers);
        // Prepare String
        StringBuilder sb = new StringBuilder();
        for( String s: knownServers.keySet()) {
            sb.append( ',');
            sb.append( s);
        }
        if( sb.length() > 0) {
            sb = sb.replace( 0, 1, "");  // remove first ","
        }
        // Update Balancer list
        ModuleInfo mi = getModInfoEndsWith( "Balancer");
        if( mi != null) {
            Configuration c = mi.getConfiguration();
            c.put( "Modules", sb.toString());
            mi.setConfiguration( c);
        }    
        // Check Pipelines
        boolean changes = false;
        ModuleInfo dmi = catalog.getModuleInfo( "SvcDispatcher");
        if( dmi == null) {
            LOG.warn( "Can't find SvcDispatcher to update destinations", 
                    new Exception( "Ignore changes"));
            return;
        }
        Configuration dc = dmi.getConfiguration();
        Map<String,String> cm = dc.getStringMap( "Pipeline.*");
        for( String sn: knownServers.keySet()) {
            if( !knownServers.get( sn).equals( cm.get( sn ))) {
                LOG.info( "New destination " + sn + " " + knownServers.get( sn));
                // (Re)Define this HttpClient 
                ModuleInfo cmi = catalog.getModuleInfo( "HttpClient_" + sn);
                if( cmi == null) {
                    // (Re)Deploy
                    Configuration c = new Configuration();
                    c.put( "class", HttpClient.class.getName());
                    c.put( "Url", knownServers.get(  sn));
                    catalog.installModule( "HttpClient_" + sn, c);
                }
                // (Re)Define this Pipeline
                dc.put( "Pipeline." + sn, outPipeline + sn);
                changes = true;
            }
        }
        if( changes) {
            LOG.trace( "new Dispatcher cfg=" + dc);
            dmi.setConfiguration( dc);
        }
    }
    
    private boolean isMaster() {
        return ( masterName != null) &&
                masterName.equals( localName);
    }
    
    private void notifyAllServers( String command, String name) {
        for(  String sn: new TreeSet<String>( knownServers.keySet())) {
            if( sn.equals( localName)) {  // Do not notify it self!
                continue;
            }
            try {
                LOG.debug( " to notify " + sn + " from " + localName + " " + localUrl);
                SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++, command, null, 
                        1000);
                rq.put( "Name", name);
                rq.put( "Url", localUrl);
                rq.put( "Servers", knownServers);
                SvcResponse rn = SvcCatalog.getDispatcher().callPipeline(sn, rq);
                // Check KEEP_ALIVE response
                if( command.equals( SVC_KEEPALIVE)) {
                    if( rn.getResultCode() == 0) {
                        if( sn.equals( UNDEFINED)) {
                            knownServers.remove( UNDEFINED);
                            knownServers.put( "" + rn.get( "Name"), "" + rn.get( "Url"));
                            updateDestinations();
                        }
                    } else {  // Not ack
                        LOG.warn( "ERROR ON " + sn + ", to discard it !");
                        knownServers.remove( sn);
                        updateDestinations();
                    }
                } else {
                    if( rn.getResultCode() != 0) {
                        LOG.warn( "ERROR ON " + sn + ", command failed " + command);
                    }
                }
            } catch( Exception ex) {
                LOG.debug( "Failed to notify " + command + " " + name, ex);
            }
        }
    }
  
}


