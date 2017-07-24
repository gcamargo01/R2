/* ModuleInfo.java */
package uy.com.r2.core;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Module;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SimpleService;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.SvcException;
import uy.com.r2.svc.tools.SvcMonitor;

/** Module Information structure and methods.
 * It has: Module name, Module implementation, Configuration & Status Vars.
 * To be used only in this package. 
 * @author Gustavo Camargo
 */
public class ModuleInfo {
    private final static Logger LOG = Logger.getLogger(ModuleInfo.class);
    private final static int DEFAULT_TIME_OUT = 10000;
    private final String moduleName;
    private final Module moduleImpl;
    private final AsyncService asyncImpl;  // Module Wrapped as AsyncService
    private final Object lockConcCtrl = new Object();
    private boolean concCtrl = false;
    private AsyncService monitorImpl = null;
    private Configuration cfg = new Configuration();

    private int limitActiveCount = Integer.MAX_VALUE;
    private int activeCount = 0;
    private int topActiveCount = 0;
    
    /** Constructor
     * @param name Module name
     * @param impl Implementation
     */
    ModuleInfo( String name, Module impl) {
        this.moduleName = name;
        this.moduleImpl = impl;
        if( impl instanceof AsyncService) {
            this.asyncImpl = (AsyncService)impl;
        } else if( impl instanceof SimpleService){
            this.asyncImpl = new WrapAsAsyncService( (SimpleService)impl); 
        } else {
            this.asyncImpl = null;
        }
    }    

    /** Get module name.
     * @return Module short name
     */
    public String getName() {
        return moduleName;
    }

    /** Get module implementation.
     * @return Module object implementation
     */
    Module getImplementation() {
        return ( monitorImpl != null)? monitorImpl: moduleImpl;
    }
    
    /** Get the actual module configuration.
     * @return Properties
     */
    Configuration getConfiguration() {
        return cfg;
    }

    /** Test if is the same implementation class.
     * @return Same implementation
     */
    boolean isTheSameClass( Configuration cfg2) {
        if( cfg2 == null) {
            return false;
        }
        return cfg.getString( "class").equals( cfg2.getString( "class"));
    }

    /** Set the new module configuration.
     * @param cfg New configuration
     * @throws Exception Unexpected error
     */
    void setConfiguration( Configuration cfg) throws Exception {
        // Add generic config descriptors
        List<ConfigItemDescriptor> cdl = getImplementation().getConfigDescriptors();
        if( cdl == null) {
            cdl = new LinkedList();
        }
        cdl.add( new ConfigItemDescriptor( "URL", ConfigItemDescriptor.URL,
               "Specify a URL from jar to class loading", null));
        cdl.add( new ConfigItemDescriptor( "Next", ConfigItemDescriptor.MODULE,
               "Next module to be called after this one", null));
        cdl.add( new ConfigItemDescriptor( "LimitActiveThreads", ConfigItemDescriptor.MODULE,
               "Keep track and limit the concurrent threads ons this module", null));
        cdl.add( new ConfigItemDescriptor( "Monitor", ConfigItemDescriptor.BOOLEAN,
               "Wrap module with SvcMonitor to get statistics and acitvity", "false"));
        cdl.add( new ConfigItemDescriptor( "TimeOut", ConfigItemDescriptor.BOOLEAN,
               "Time out of this module", "" + DEFAULT_TIME_OUT));
        // Set default values 
        for( ConfigItemDescriptor cd: cdl) {
            if( cd.getDefaultValue() != null &&           // has a default value
                    ( !cd.getKey().contains( "*")) &&     // Simple cfg. 
                    ( !cfg.containsKey( cd.getKey()))) {  // w/o given value in cfg
                LOG.trace( moduleName + " set default " + cd.getKey() + "=" + cd.getDefaultValue());
                cfg.put( cd.getKey(), cd.getDefaultValue());
            }
        }    
        // Log config status
        if( LOG.isTraceEnabled()) {
            Map<String,String> cfgItems = cfg.getStringMap( "*");
            LOG.trace( "Config: #### " + moduleName);
            LOG.trace( "Config: ## Module implementation class");
            LOG.trace( "Config: class=" + moduleImpl.getClass().getName());
            cfgItems.remove( "class");
            for( ConfigItemDescriptor cd: cdl) {
                if( !cd.getKey().contains( "*")) {    // Simple cfg.
                    if( cfg.containsKey( cd.getKey())) {
                        LOG.trace( "Config: ## " + cd.getDescription());
                        LOG.trace( "Config: " + cd.getKey() + "=" + cfg.getString( cd.getKey()));
                        cfgItems.remove( cd.getKey());
                    }
                } else {
                    for( String k: cfg.getStringMap( cd.getKey()).keySet()) {
                        LOG.trace( "Config: ## " + cd.getDescription());
                        String kk = cd.getKey().replace( "*", k);
                        LOG.trace( "Config: " + kk + "=" + cfg.getString( kk));
                        cfgItems.remove( kk);
                    }
                }
            }
            for( String k: cfgItems.keySet()) {
               LOG.trace( "Config: ## Not recognized configuration:");
               LOG.trace( "Config: " + k + "=" + cfg.getString( k));
            }
        }
        // Reset status
        activeCount = 0;
        topActiveCount = 0;
        // Apply generic configuration
        if( cfg.containsKey( "LimitActiveThreads")) {
            limitActiveCount = cfg.getInt( "limitActiveThreads");
            concCtrl = true;
        }
        if( cfg.getBoolean( "Monitor")) {
            LOG.trace( "Monitor instanced on " + moduleName);
            monitorImpl = new SvcMonitor( asyncImpl, moduleName);
        } else {
            monitorImpl = null;
        }
        // Update config
        this.cfg = cfg.clone();
        if( moduleImpl instanceof CoreModule) {  
           ( (CoreModule)moduleImpl).startup( cfg);
        }
    }
    
    /** Running instances accounting, if can add one more. */
    boolean takeOne() {
        if( concCtrl) {
            synchronized( lockConcCtrl) {  
                if( activeCount > limitActiveCount) {
                    return false;
                }
                ++activeCount;
                if( activeCount > topActiveCount) {
                    topActiveCount = activeCount;
                }
            }
        }
        return true;
    }

    /** Decrement running instances accounting. */
    void releaseOne() {
        if( concCtrl) {
            synchronized( lockConcCtrl) {  
                if( activeCount > 0) {
                    --activeCount;
                }
            }
        }
    }
    
    SvcMessage processMessage( SvcMessage msg) {
        /*
        if( LOG.isDebugEnabled() && msg != null) {
            if( msg instanceof SvcRequest) {
                LOG.debug( "processMessage >>> " + moduleName + " " + msg);
            } else if( msg instanceof SvcResponse) {
                LOG.debug( "processMessage <<< " + moduleName + " " + msg);
            }        
        }
        */
        SvcRequest invoc = ( msg instanceof SvcRequest) ? 
                (SvcRequest)msg:
                ((SvcResponse)msg).getRequest();
        if( !takeOne()) {  // Too many running instances, cancel!
            LOG.warn( "Too many concurrent active instances " + moduleName);
            return new SvcResponse( SvcResponse.MSG_TOPPED, 
                SvcResponse.RES_CODE_TOPPED, null, invoc);
        }
        try {
            AsyncService as = ( monitorImpl != null)? monitorImpl: asyncImpl;
            if( msg instanceof SvcRequest) {
                msg = as.onRequest( invoc, cfg);
                /* 
                if( LOG.isDebugEnabled() && msg instanceof SvcResponse) {
                    LOG.debug( "processMessage <<| " + moduleName + " " + msg);
                } 
                */
            } else if( msg instanceof SvcResponse) {
                msg = as.onResponse( (SvcResponse)msg, cfg);
            }        
        } catch( SvcException ex) {
            String s = ex.getMessage() + " on module '" + moduleName + "'";
            LOG.warn( s, ex);
            msg = new SvcResponse( s, ex.getErrorCode(), ex, invoc);
        } catch( Exception ex) {
            String s = "Unexpected error on module '" + moduleName + "' " + ex;
            LOG.warn( s, ex);
            msg = new SvcResponse( s, SvcResponse.RES_CODE_EXCEPTION, ex, invoc);
        } finally {
            releaseOne();
        }
        return msg;
    }

    /** Report module status plus active count */
    Map<String,Object> getStatusVars() {
        Map<String,Object> m = new TreeMap<String,Object>();
        if( concCtrl) {
            m.put( "ActiveCount", activeCount);
            m.put( "TopActiveCount", topActiveCount);
        }
        Map<String,Object> mm = getImplementation().getStatusVars();
        if( mm != null) {
            m.putAll( mm);
        }
        return m;        
    }
    
    int getTimeOut() {
        try {
            return cfg.getInt( "TimeOut", DEFAULT_TIME_OUT);
        } catch( Exception x) {
            return DEFAULT_TIME_OUT;
        }
    }

    List<ConfigItemDescriptor> getConfigDescriptors() {
        List<ConfigItemDescriptor> l = getImplementation().getConfigDescriptors();
        if( l == null) {
            l = new LinkedList();
        }
        return l;
    }
    
    /** SimpleService wrapper as a AsyncService. */
    private class WrapAsAsyncService implements AsyncService {
        private SimpleService s;

        WrapAsAsyncService( SimpleService s) {
            this.s = s;
        }

        @Override
        public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
            return s.call( req, cfg);
        }
        
        @Override
        public SvcResponse onResponse( SvcResponse res, Configuration cfg) throws Exception {
            return res;
        }

        @Override
        public List<ConfigItemDescriptor> getConfigDescriptors() {
            return s.getConfigDescriptors();
        }

        @Override
        public Map<String, Object> getStatusVars() {
            return s.getStatusVars();
        }

        @Override
        public void shutdown() {
            s.shutdown();
        }

    }
    
}

