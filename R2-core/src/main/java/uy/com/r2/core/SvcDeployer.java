/* SvcDeployer.java */
package uy.com.r2.core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.SvcMessage;

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
    public static final String SVC_SETMODULECONFIG = "SetModuleConfig";
    public static final String SVC_PERSISTCONFIG   = "PersistConfig";
    public static final String SVC_RESTARTMODULE   = "RetartModule";
    public static final String SVC_UNDEPLOYMODULE  = "UndeployModule";
    public static final String TAG_ACTUALCONFIG = "_Configuration";
    static final String DEPLOYER_NAME = SvcDeployer.class.getSimpleName();
    private static final String[] SERVICES = {
            SVC_DEPLOYMODULE,    SVC_GETMODULECONFIG, SVC_GETMODULELIST,   
            SVC_GETMODULESTATUS, SVC_SETMODULECONFIG, SVC_RESTARTMODULE,   
            SVC_UNDEPLOYMODULE
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
    }
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        return null;
    }
    
    private void updateCfg( Configuration cfg) throws Exception {
        if( !cfg.isUpdated()) {
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
        cfg.clearUpdated();
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
        if( req.getServiceName().equals( Dispatcher.SVC_GETSERVICESLIST)) {
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
        map.put( "OS", System.getProperty( "os.name"));
        map.put( "Java", System.getProperty( "java.version"));
        map.put( "Classpath", System.getProperty( "java.class.path"));
        map.put( "Lib", System.getProperty( "java.library.path"));
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
                Map<String,Map<String,Object>> mm = getDetailedConfiguration( mn);
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

    /** Get the actual module configuration full detailed.
     * @return Map 
     * @throws Exception Error on configuration
     */
    private Map<String,Map<String,Object>> getDetailedConfiguration( String mn) throws Exception {
        Configuration cfg = catalog.getModuleInfo( mn).getConfiguration();
        Map<String, Map<String,Object>> r = new TreeMap();
        Map<String, String> vm = cfg.getStringMap( "*");
        List<ConfigItemDescriptor> cdl = catalog.getModuleInfo( mn).getConfigDescriptors();
        for( ConfigItemDescriptor cd: cdl) {
            TreeMap<String,Object> tm = new TreeMap();
            tm.put( "Key", cd.getKey());
            tm.put( "Type", "" + cd.getKlass());
            tm.put( "Description", cd.getDescription());
            if( cd.getDefaultValue() != null) {
                tm.put( "DefaultValue", cd.getDefaultValue());
            }
            if( !cd.getKey().contains( "*")) {    // Simple cfg.
                if( cfg.containsKey( cd.getKey())) {
                    tm.put( "Value", vm.get( cd.getKey()));
                } else {
                    tm.put( "Unused", "true");
                }
            } else {
                tm.put( "ValuesMap", cfg.getStringMap( cd.getKey()));
            }
            if( cd.getAttribute() == ConfigItemDescriptor.SECURITY) {
                tm.put( "Attribute", "SECURED");
            } else if( cd.getAttribute() == ConfigItemDescriptor.DEPLOYER) {
                tm.put( "Attribute", "ENVIRONMENT");
            }
            r.put( cd.getKey(), tm);
        }
        return r;
    }

    
}


