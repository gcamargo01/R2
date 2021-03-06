/* SvcAvailServers.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.ModuleInfo;
import uy.com.r2.core.SvcDeployer;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.ServiceReference;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.svc.conn.HttpClient;
import uy.com.r2.core.api.StartableModule;


/** Keep alive based available servers and master selection module.
 * This core service module keep track of all the registered servers, and always 
 * select on to be the Master. <br>
 * <p> Master - Slave rules: <br>
 * - The  master is the only one, and it sends KEEPALIVE all the time (KeepAliveDelay mS) to all other servers<br>
 * - Each node are master until receives a KEEPALIVE, if the name is higher then local is turned to slave <br>
 * - If a slave detects a time-out (KeepAliveTimeout mS), choose a Master, and notify all servers<br>
 * - When a master releases its role, notify the real master the unknown servers. This is because while he was the 
 *   master, he could detect some servers, unknown yet to the real master because he was out of contact. <br>
 * - The Master is selected by its lower name <br>
 * <p> Known servers rules: <br>
 * - The master send in the KEEPALIVE message with its name and a list of know servers <br>
 * - When a new node starts, it only knows his name, and a remote Url to UNDEFINED name server, so he must <br>
 *   (1) ask who is the Master an then <br>
 *   (2) send to de Master an ADDSERVER command to add himself <br>
 * - Then with the first KEEPALIVE, he can replace the UNDEFINED name <br>
 * <p>
 * @author G.Camargo
 */
public class SvcAvailServers implements AsyncService, StartableModule, Runnable {
    public static final String SVC_ADDSERVER       = "AddServer";
    public static final String SVC_GETMASTER       = "GetMasterServer";
    public static final String SVC_GETSERVERSLIST  = "GetServersList";
    public static final String SVC_KEEPALIVE       = "KeepAlive";
    public static final String SVC_REMOVESERVER    = "RemoveServer";
    public static final String SVC_SETMASTER       = "SetMasterServer";
    public static final String SVC_SHUTDOWN        = "Shutdown";
    private static final String[] SERVICES = {
        SVC_ADDSERVER, 
        SVC_GETMASTER, 
        SVC_GETSERVERSLIST,
        SVC_KEEPALIVE, 
        SVC_REMOVESERVER, 
        SVC_SETMASTER, 
        SVC_SHUTDOWN, 
        Dispatcher.SVC_GETSVCREFERENCE
    };
    private static final List<String> COMMANDS = Arrays.asList( SERVICES);
    private static final String SVC_SYNC_LIBS = "Synclibs";
    private static final String UNDEFINED = "_Undefined_";
    private static final Logger LOG = Logger.getLogger(SvcAvailServers.class);
    private final SvcCatalog catalog = SvcCatalog.getCatalog();
    private long masterTimeStamp = System.currentTimeMillis();
    private int nodeTxNr = 0;
    private boolean stop = false;
    private String localName = new SvcRequest( null, 0, 0, null, null, 0).getClientNode();
    // Cfg
    private URL localUrl = null;
    private URL remoteUrl = null;
    private String masterName = null;
    private Map<String,String> knownServers = new TreeMap();
    private int keepAliveTimeout = 10000;
    private int keepAliveDelay = 5000;
    private String outPipeline = "ClntJson,HttpClient_";
    
    public SvcAvailServers() {
        LOG.trace( "new");
    }
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "LocalUrl", ConfigItemDescriptor.URL,
                "local URL of this  server"));
        l.add( new ConfigItemDescriptor( "RemoteUrl", ConfigItemDescriptor.URL,
                "local URL of this  server"));
        l.add( new ConfigItemDescriptor( "MasterName", ConfigItemDescriptor.STRING,
                "The primary node name"));
        l.add( new ConfigItemDescriptor( "OuterPipeline", ConfigItemDescriptor.STRING,
                "Pipeline to remote server", outPipeline));
        l.add( new ConfigItemDescriptor( "Server.*", ConfigItemDescriptor.STRING,
                "Known servers and URLs"));
        l.add( new ConfigItemDescriptor( "KeepAliveTimeout", ConfigItemDescriptor.INTEGER,
                "Max.time to wait a KEEP_ALIVE, in mS", "10000"));
        l.add( new ConfigItemDescriptor( "KeepAliveDelay", ConfigItemDescriptor.INTEGER,
                "Delay time to sleep waiting", "5000"));
        return l;
    }
    
    /** Configure and start keep alive thread. */
    @Override
    public void start( Configuration cfg) throws Exception {
        localUrl = cfg.getUrl( "LocalUrl");
        remoteUrl = cfg.getUrl( "RemoteUrl");
        masterName = cfg.getString( "MasterName");
        knownServers.putAll( cfg.getStringMap( "Server.*"));
        outPipeline = cfg.getString( "OuterPipeline");
        keepAliveTimeout = cfg.getInt( "KeepAliveTimeout");
        keepAliveDelay = cfg.getInt( "KeepAliveDelay");
        if( !knownServers.containsKey( localName)) {
            knownServers.put( localName, "" + localUrl);
        }
        if( remoteUrl != null && !knownServers.values().contains( "" + remoteUrl)) {
            knownServers.put( UNDEFINED, "" + remoteUrl);
        }
        if( masterName == null || masterName.isEmpty()) {
            masterName = localName;
        }
        masterTimeStamp = System.currentTimeMillis();
        LOG.debug( "Starting ............... " + localName + " rmt= " + remoteUrl + " " + masterName);
        Thread t = new Thread( this, "KeepAlive");
        t.start();
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
        // Is there a command?
        if( COMMANDS.contains( req.getServiceName())) {
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
        return req;
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
        if( req.getServiceName().equals( Dispatcher.SVC_GETSERVICESLIST)) {
            for( String s: SERVICES) {
                resp.add( "Services", s);
            }
        } 
        return resp; 
    }

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

    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
        stop = true;
    }

    /** Remote command execution.
     * @param cmd Command verb
     * @param name Module Name or Server name
     * @param url URL as String
     * @param map Server map (name, Url)
     * @return Response Map with REMOVE sub-key (Multimap)
     * @throws Exception Error on command execution
     */
    private Map<String,List<Object>> command( String cmd, String name, String url, 
            Map<String,String> map) throws Exception {
        LOG.trace( "Command: " + cmd + " " + name + " " + url + " " + map);
        Map<String,List<Object>> respMap = new TreeMap();
        try {
            switch( cmd) {
            case SVC_ADDSERVER:
                if( url.equals( "" + remoteUrl)) {  // Is  the url defined on start-up?
                    knownServers.remove( UNDEFINED);   // yes: remove _Undefined_
                }
                addSyncAndBalance( name, url);
            case SVC_GETSERVERSLIST:
                for( String k: knownServers.keySet()) {
                    SvcMessage.addToMap( respMap, k, knownServers.get(  k));
                }
                break;
            case SVC_REMOVESERVER:
                knownServers.remove( name);
                updateBalancerList();
                break;
            case SVC_GETMASTER: 
                if( masterName != null && !masterName.isEmpty()) {
                    SvcMessage.addToMap( respMap, "MasterName", masterName);
                    SvcMessage.addToMap( respMap, "MasterUrl", knownServers.get( masterName));
                } else {
                    SvcMessage.addToMap( respMap, "Error", "Undefined Master");
                }
                break;
            case SVC_SETMASTER: 
                if( name == null) {
                    throw new Exception( "Null name on setMaster command");
                }
                masterTimeStamp = System.currentTimeMillis();
                masterName = name;
                if( !knownServers.containsKey( name)) {  // new?
                    addSyncAndBalance( name, map.get( name));
                }
                break;
            case SVC_KEEPALIVE:
                masterTimeStamp = System.currentTimeMillis();
                if( isMaster()) {  // Conflict! The master is the only one that send KeepAlive
                    if( name.compareTo( localName) < 0) {   // the lower name is the master
                        masterName = name;   // Update the Master name
                        LOG.info( "Released as Master by " + name);
                        if( !map.containsKey( localName)) {
                            map.put( localName, "" + localUrl);
                        }
                        // Now check if all the knownServers are known by new master
                        for( String s: knownServers.keySet()) {
                            if( !map.containsKey( s)) {
                                // Tell the new Master the servers unknown
                                SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++, 
                                        SVC_ADDSERVER, null, 0);
                                rq.put( "Name", s);
                                rq.put( "Url", knownServers.get( s));
                                SvcCatalog.getDispatcher().callPipeline( masterName, rq);
                            }
                        }
                        // Update the knownServers map from master
                        knownServers.putAll( map);
                    } else {
                        LOG.info( "Local Master replay a keepAlive from " + name);
                        // Tell it that local im the Master 
                        SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++, 
                                SVC_SETMASTER, null, 0);
                        rq.put( "Name", localName);
                        rq.put( "Url", knownServers.get( localName));
                        SvcCatalog.getDispatcher().callPipeline( name, rq);
                    }
                } else {  // keepAlive from master
                    knownServers = map;
                    updateBalancerList();  // The master knows if it are usable (?)
                }
                // The master may KEEP_ALIIVE w/o know the localname & localurl, tell him
                SvcMessage.addToMap( respMap, "Name", localName);
                SvcMessage.addToMap( respMap, "Url", "" + localUrl);
                break;
            case SVC_SHUTDOWN:
                stop = true;
                notifyAllServers( SVC_REMOVESERVER, localName);
                catalog.shutdown();
                break;
            case Dispatcher.SVC_GETSVCREFERENCE:
                ServiceReference sr = null;
                switch( name) {
                case SVC_ADDSERVER:
                    sr = new ServiceReference( name);
                    sr.setDescription( "Add a server to the known server list");
                    sr.addRequestField( "Name", "Server name", ServiceReference.STRING);
                    sr.addRequestField( "Url", "External URL to contact server", ServiceReference.STRING);
                    break;
                case SVC_GETSERVERSLIST:
                    sr.setDescription( "Get the known server list");
                    sr.addResponseField( "_ANY_", "Master server names and its URLs", ServiceReference.STRING);
                    break;
                case SVC_REMOVESERVER:
                    sr = new ServiceReference( name);
                    sr.setDescription( "Remove a server from the known servers list");
                    sr.addRequestField( "Name", "Srver name to remove", ServiceReference.STRING);
                    break;
                case SVC_GETMASTER:
                    sr = new ServiceReference( name);
                    sr.setDescription( "Get the master server");
                    sr.addResponseField( "MasterName", "Master server name", ServiceReference.STRING);
                    sr.addResponseField( "MasterUrl", "Master server URL", ServiceReference.STRING);
                    break;
                case SVC_SETMASTER:
                    sr = new ServiceReference( name);
                    sr.setDescription( "Set the master servert");
                    sr.addRequestField( "Name", "New master server name", ServiceReference.STRING);
                    break;
                case SVC_KEEPALIVE:
                    sr = new ServiceReference( name);
                    sr.setDescription( "Test if this server is alive");
                    sr.addRequestField( "Name", "Master server name", ServiceReference.STRING);
                    sr.addResponseField( "Name", "Local server name", ServiceReference.STRING);
                    sr.addResponseField( "Url", "Local server URL", ServiceReference.STRING);
                    break;
                case SVC_SHUTDOWN:
                    sr = new ServiceReference( name);
                    sr.setDescription( "Shutdown current server");
                    break;
                }
                if( sr != null) {
                    respMap = sr.getResponse( null).getPayload();
                }
                break;
            default:
                throw new Exception( "Invalid command: " + cmd);
            }
        } catch( Exception x) {
            LOG.info( "Command failed: " + cmd + " " + name + " " + url, x);
            SvcMessage.addToMap( respMap, "Error", "" + x);
        }
        LOG.trace(" resp = " + respMap);
        return respMap;
    }    

    /** Runnable method to check status.
     * Ron every keepAliveDelay interval.
     */
    @Override
    public void run( ) {
        for( ; ;) {
            try {
                Thread.sleep( keepAliveDelay);  
                if( stop) {
                    break;
                }
                if( knownServers.size() <= 1) {
                    continue;  // Keep quiet if doesnt know anyrhing
                }
                if( isMaster()) {  
                    masterTimeStamp = System.currentTimeMillis();
                    notifyAllServers( SVC_KEEPALIVE, localName);
                } else {  // This is not the Master, so control it
                    // The master is alive?
                    if( System.currentTimeMillis() > masterTimeStamp 
                            + keepAliveTimeout) {  // Timeout?
                        chooseANewMaster();
                    }
                }
                notifyXUdp();
            } catch( Exception ex) {
                LOG.warn( "Error on keepAlive", ex);
            }
        }
    }

    private ModuleInfo getModInfoEndsWith( String className) {
        LOG.trace( "getModInfoEndsWith " + className);
        for( String n: catalog.getModuleNames()) {
            ModuleInfo mi = catalog.getModuleInfo( n);
            Configuration c = mi.getConfiguration();
            if( c.getString( "class").endsWith( className)) {
                return mi;
            }
        }
        return null;
    }
    
    private boolean isMaster() {
        return ( masterName != null) &&
                masterName.equals( localName);
    }
    
    private boolean chooseANewMaster() {
        LOG.trace( "chooseANewMaster actual=" + masterName);
        for( String n: knownServers.keySet()) {  // alfabetical ordered
            if( n.equals( UNDEFINED)) {
                continue;
            }
            LOG.trace( "promoted to Master " + n + " from " + masterName);
            masterName = n;
            notifyAllServers( SVC_SETMASTER, n);
            return true;
        }
        return false;
    }
    
    private void notifyAllServers( String command, String name) {
        boolean updateDest = false;
        LOG.trace( "notifyAllServers " + command + " " + name);
        for(  String sn: new ArrayList<>( knownServers.keySet())) {
            if( sn.equals( localName)) {  // Do not notify it self!
                continue;
            }
            try {
                LOG.debug( " to notify " + sn + " from " + localName + " " + localUrl);
                SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++, command, 
                        null, 0);
                rq.put( "Name", name);
                rq.put( "Url", localUrl);
                rq.put( "Servers", knownServers);
                SvcResponse rn = SvcCatalog.getDispatcher().callPipeline( sn, rq);
                // Check KEEP_ALIVE response
                if( command.equals( SVC_KEEPALIVE)) {
                    if( rn.getResultCode() == 0) {
                        if( sn.equals( UNDEFINED)) {
                            knownServers.remove( UNDEFINED);
                            addSyncAndBalance( "" + rn.get( "Name"), "" + rn.get( "Url"));
                        }
                    } else {  // Not ack
                        LOG.warn( "Error code on " + sn + ", to discard it !");
                        knownServers.remove( sn);
                        updateDest = true;
                    }
                } else {
                    if( rn.getResultCode() != 0) {
                        LOG.warn( "Error responnse " + rn.getResultCode() + " to notify " 
                                + command + " " + name + " to " + sn);
                    }
                }
            } catch( Exception ex) {
                LOG.debug( "Failed to notify " + command + " " + name, ex);
            }
        }
        if( updateDest) {
            updateBalancerList();
        }
    }
  
    private void notifyXUdp() {
        LOG.trace( "notifyXUdpMaster " + masterName);
        try {
            SvcRequest rq = new SvcRequest( localName, 0, nodeTxNr++, SVC_SETMASTER, 
                    null, 0);
            rq.put( "Name", masterName);
            rq.put( "Url", knownServers.get( masterName));
            rq.put( "Servers", knownServers);
            SvcCatalog.getDispatcher().callPipeline( "Udp", rq);
        } catch( Exception ex) {
            LOG.debug( "Failed to notifyXUdpMaster " + ex, ex);
        }
    }

    /** Add a known server and its pipeline. */
    private void addDestination( String name, String url) throws Exception {
        LOG.trace( "addDestination " + name + " " + url);
        knownServers.put( name, url);
        boolean changes = false;
        // Check Pipelines
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
    
    /** This method in executed on every change of the known servers */
    private void updateBalancerList() {
        try {
            LOG.trace( "updateBalancer " + knownServers);
            // Prepare String with destinations
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
        } catch( Exception x) {
            LOG.warn( "Failed update balancer", x);
        }
    }
    
    /** Add a server, sychronize and add as balancing destination. */
    private void addSyncAndBalance( String name, String url) throws Exception {
        addDestination( name, url);
        SvcRequest rq;
        SvcResponse rs;
        //rq = new SvcRequest( localName, 0, nodeTxNr++, SVC_SYNC_LIBS, null, 1000);
        //rq.put( "RmtPipe", knownServers.get( name));
        //SvcCatalog.getDispatcher().call( rq);
        rq = new SvcRequest( localName, 0, nodeTxNr++, CfgRelay.SVC_SYNC_CFG, null, 0);
        rq.put( "RmtPipe", name);
        rs = SvcCatalog.getDispatcher().call( rq);
        if( rs.getResultCode() > 0) {
            updateBalancerList();
        }
    }
    
}


