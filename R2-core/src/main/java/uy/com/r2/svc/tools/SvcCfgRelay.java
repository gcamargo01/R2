/* SvcCfgRelay.java */
package uy.com.r2.svc.tools;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.CoreModule;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.SvcMessage;

/** System configuration rely module. 
 * This core service module manage the configuration replication all over the servers.
 * The rules to do that are: <br>
 * - To make changes, must be open a changes session with STARTCHANGES <br>
 * - Then all changes are stored and done locally (only) <br>
 * - When ROLLBACKCHANGES, undo all the changes done <br>
 * - When COMMITCHANGES, relay all configuration changes to others servers <br>
 * - If a new server is detected, or changes take in place, then: <br>
 * (1) Update all the jars to /lib path <br>
 * (2) Send all the new configuration <br>
 * (3) Update the configuration (avoiding partial upgrades that can bring unexpected results)<br>
 * 
 * This module need to control SvcDeploy, so the Pipeline should be: <br>
 * ... - SvcCfgRelay - SvcDeploy - SvcAvailServers
 * !!! To do: WORK IN PROGRESS 
 <p>
 * @author G.Camargo
 */
public class SvcCfgRelay implements AsyncService, CoreModule {
    public static final String SVC_STARTCHANGES    = "StartChanges";
    public static final String SVC_ROLLBACKCHANGES = "RollbackChanges";
    public static final String SVC_COMMITCHANGES   = "CommitChanges";
    public static final String SVC_CFGRELAY        = "CfgRelay";
    private static final String[] SERVICES = {
        SVC_STARTCHANGES,     // User command to start changing configuration
        SVC_ROLLBACKCHANGES,  // Undo all changes done
        SVC_COMMITCHANGES,    // Relay changes all over the system
        SVC_CFGRELAY,         // Remote server send cfg. changes
    };
    private static final List<String> COMMANDS = Arrays.asList( SERVICES);
    private static final int TIMEOUT = 5000;
    private static final Logger LOG = Logger.getLogger(SvcCfgRelay.class);
    private List<Change> changes = null;
    private int txNr = 0;
        
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        return l;
    }
    
    /** Startup.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    @Override
    public void startup( Configuration cfg) throws Exception {
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
        // Is there a command?
        if( COMMANDS.contains( req.getServiceName())) {
            String mod = null;
            String url = null;
            Map<String,List<Object>> params = null;
            try {
                params = req.getPayload();
            } catch( Exception x) {
                LOG.info( "Parameter cast error on command " + cmd, x);
            }
            Map<String,List<Object>> m = command( cmd, params);
            int cr = ( m.get( "Error") != null)? 100: 0;
            SvcResponse resp = new SvcResponse( m, cr, req);
            LOG.trace( "Command response: " + resp);
            return resp;
        }
        // Is there a SvcDepoyer command?
        Configuration c = null;
        switch( cmd) {
            case SvcDeployer.SVC_DEPLOYMODULE:
            case SvcDeployer.SVC_SETMODULECONFIG:
                c = new Configuration();  // distint configuration
                for( String k: req.getPayload().keySet()) {
                    if( !k.equals( "Module")) {
                        c.put( k, req.get( k));  // Put single value
                    }
                }
            case SvcDeployer.SVC_UNDEPLOYMODULE:
                String mod = ( String)req.get( "Module");
                if( changes == null) {
                   return new SvcResponse( "Not in a changes session, do a " + SVC_STARTCHANGES, -1, req);
                }
                addAChange( cmd, mod, c);
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
        return resp;  // nothing to do
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
        return map;
    }
    
    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    /** Execute command.
     * @param cmd Command verb
     * @param params Request parameters
     * @return Response Map 
     * @throws Exception Error on command execution
     */
    private Map<String,List<Object>> command( String cmd, Map<String,List<Object>> params) 
            throws Exception {
        LOG.trace("Command: " + cmd + " " + params);
        Map<String,List<Object>> resp = new TreeMap();
        try {
            switch( cmd) {
            case SVC_STARTCHANGES:  // User starting changes session
                if( changes != null) {
                    throw new Exception( "Changes session already open, ignored start");
                }
                backupCfg();
                changes = new LinkedList();
                break;
            case SVC_ROLLBACKCHANGES:  // Roll-back changes and close session
                if( changes == null) {
                    throw new Exception( "Changes session not open, ignored rollback");
                }
                rollbackCfg();
                changes = null;
                break;
            case SVC_COMMITCHANGES:  // Relay changes all over 
                if( changes == null) {
                    throw new Exception( "Changes session not open, ignored commit");
                } 
                sendCfgToAll();
                changes = null;
                break;
            case SVC_CFGRELAY:  // Changes been roled over
                // A set of changes is received !!!!
                break;
            default:
                SvcMessage.addToMap( resp, "Error", "Invalid command: " + cmd);
            }
        } catch( Exception x) {
            SvcMessage.addToMap( resp, "Error", "" + x + " on command " + cmd);
        }
        LOG.trace( " resp = " + resp);
        return resp;
    }    
   
    private void addAChange( String cmd, String mod, Configuration cfg) {
        Change c = new Change();
        c.module = mod;
        c.cmd = cmd;
        c.cfg = cfg;
        changes.add( c);
    }
    
    private final SvcCatalog catalog = SvcCatalog.getCatalog();
    private LinkedHashMap<String,Configuration> bkp = new LinkedHashMap();
    

    private void sendCfgToAll( ) {
        try {
            // Get the Local Name
            String localName = new SvcRequest( null, 0, 0, null, null, 0).getClientNode();
            // Get Servers list
            SvcResponse r = SvcCatalog.getDispatcher().call(  
                    new SvcRequest( null, txNr++, 0, SvcAvailServers.SVC_GETSERVERSLIST, null, TIMEOUT));
            if( r == null || r.getResultCode() != 0) {
                throw new Exception( "Can't get servers list");
            }
            Set<String> s = r.getPayload().keySet();
            // Remove this one
            s.remove( localName);
            if( s.isEmpty()) {
                return;  // No one to send
            }
            // Send to each one
            for( String sn: s) {
                try {
                    for( Change c: changes) {
                        SvcRequest rq = new SvcRequest( localName, txNr++, 0, c.cmd, null, 0);
                        for( String k: c.cfg.getStringMap( "*").keySet()) {
                            rq.put( k, c.cfg.getString( k));
                        }
                        r = SvcCatalog.getDispatcher().callPipeline( sn, rq);
                        if( r == null || r.getResultCode() != 0) {
                            throw new Exception( "Failed to update " + c.module);
                        }
                    }
                } catch( Exception x) {
                    LOG.warn( "Can't send cfg to server " + s, x);
                }
            }
        } catch( Exception x) {
            LOG.warn( "Failed to send cfg", x);
        }
    }

    private void backupCfg() {
        // Bakcup current cfg.
        bkp = new LinkedHashMap();
        for( String mn: catalog.getModuleNames()) {
            Configuration c = catalog.getModuleInfo( mn).getConfiguration();
            bkp.put( mn, c);
        }
    }
    
    private void rollbackCfg() {
        // Restore cfg.
        for( String mn: bkp.keySet()) {
            try {
                catalog.updateConfiguration( mn, bkp.get( mn));
            } catch( Exception x) {
                LOG.warn( "Failed to restore module '" + mn + "' cfg", x);
            }
        }
        // Remove new modules 
        for( String mn: catalog.getModuleNames()) {
            if( !bkp.containsKey( mn)) {
                try {
                    catalog.uninstallModule( mn );
                } catch( Exception x) {
                    LOG.warn( "Failed to remove module '" + mn + "'", x);
                }
            }
        }
    }
    
    private class Change {
        String cmd;
        String module;
        Configuration cfg;
    }
    
}


