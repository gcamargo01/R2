/* SvcDeployer.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;

/** Command interpreter service that deploy and un-deploy modules.
 * This is a in-memory Deployer, that allows system remote control.
 * @author G.Camargo
 */
public class SvcDeployer implements AsyncService {
    private static final String DEPLOYER_NAME = SvcDeployer.class.getSimpleName();
    private static final String SVC_DEPLOYMODULE    = "DeployModule";
    private static final String SVC_GETSERVICESLIST = "GetServicesList";
    private static final String SVC_GETMODULECONFIG = "GetModuleConfig";
    private static final String SVC_GETMODULELIST   = "GetModulesList";
    private static final String SVC_GETMODULESTATUS = "GetModuleStatus";
    private static final String SVC_SETMODULECONFIG = "SetModuleConfig";
    private static final String SVC_GETMODULEDETCFG = "GetModuleDetailedConfig";
    private static final String SVC_STARTMODULE     = "StartModule";
    private static final String SVC_STOPMODULE      = "StopModule";
    private static final String SVC_UNDEPLOYMODULE  = "UndeployModule";
    private static final String[] SERVICES = {
            SVC_DEPLOYMODULE,    SVC_GETMODULECONFIG, SVC_GETMODULEDETCFG, 
            SVC_GETMODULELIST,   SVC_GETMODULESTATUS, SVC_SETMODULECONFIG, 
            SVC_STARTMODULE,     SVC_STOPMODULE,      SVC_UNDEPLOYMODULE 
    };
    private static final Logger LOG = Logger.getLogger( SvcDeployer.class);
    private static String commands = "";
    private final SvcCatalog catalog = SvcCatalog.getCatalog();
    private int receivedCommands = 0;
    private int errorsOnCommands = 0;

    static {
        StringBuilder sb = new StringBuilder( "|");
        for( String n: SERVICES) {
            sb.append(  n);
            sb.append(  "|");
        }
        commands = sb.toString();
    }
    
    /** Constructor.
     * @throws Exception Unexpected failure
     */
    SvcDeployer() throws Exception {
        // Register itself in the system catalog
        catalog.installModule( DEPLOYER_NAME, this, null);
    }
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Commands.*.Cmd", ConfigItemDescriptor.STRING,
                "Commands to execute in order", null));
        l.add( new ConfigItemDescriptor( "Commands.*.Module", ConfigItemDescriptor.STRING,
                "Module parameter of commands to execute (optional)", null));
        return l;
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
        if( commands.contains( "|" + req.getServiceName() + "|" )) {
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
        Map<String,Object> m = new TreeMap();
        m.put( "Commands", receivedCommands);
        m.put( "CommandErrors", errorsOnCommands);
        return m;
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
        Map<String,List<Object>> resp = new TreeMap();
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
                    SvcMessage.addToPayload( resp, "Modules", s);                    
                }
                break;
            case SVC_SETMODULECONFIG:    
                catalog.updateConfiguration( mn, cfg);
                break;
            case SVC_GETMODULEDETCFG:
                Map<String,Map<String,Object>> mm = catalog.getModuleInfo( mn).getDetailedConfiguration();
                for( String k: mm.keySet()) {
                    SvcMessage.addToPayload( resp, k, mm.get( k)); 
                } 
                break;
            case SVC_GETMODULECONFIG:
                Map<String,String> cm = catalog.getModuleInfo( mn).getConfiguration().getStringMap( "*");
                for( String k: cm.keySet()) {
                    SvcMessage.addToPayload( resp, k, cm.get( k)); 
                } 
                break;
            case SVC_GETMODULESTATUS:
                Map<String,Object> mo = catalog.getModuleInfo( mn).getStatusVars();
                for( String k: mo.keySet()) {
                    SvcMessage.addToPayload( resp, k, mo.get( k));
                }
                break;
            case SVC_STOPMODULE:
                catalog.getModuleInfo( mn).shutdown();
                break;
            case SVC_STARTMODULE:
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
        return resp;
    }    

    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    /** Entry point as a Deployer.
     * @param args Standard arguments
     */
    public static void main( String args[]) {
        try {
            LOG.debug( "start " + args);
            SvcDeployer m = new SvcDeployer();
            String rmtUrl = "http://localhost:8012";
            int localPort = 8015;
            for( int i = 0; i < args.length; ++i) {
                if( args[ i].startsWith( "-u")) {
                    if( args[ i].length() == 2) {
                        rmtUrl = args[ ++i];
                    } else {
                        rmtUrl = args[ i].substring( 2);
                    }
                } else if( args[ i].startsWith( "-p")) {
                    if( args[ i].length() == 2) {
                        localPort = Integer.parseInt( args[ ++i]);
                    } else {
                        localPort = Integer.parseInt( args[ i].substring( 2));
                    }
                }
            }
            Configuration c = new Configuration();
            c.put( "class", SvcManager.class.getName());
            if( localPort > 0) {
                c.put( "Port", localPort);
            }
            c.put( "RemoteUrl", rmtUrl);
            m.command( SVC_DEPLOYMODULE, SvcManager.class.getSimpleName(), c);
            while( SvcManager.isAlive()) { }
            LOG.info( "Stopped");
        } catch ( Exception ex ) {
            System.err.println( "Error " + ex);
            ex.printStackTrace( System.err);
        }
    }

}


