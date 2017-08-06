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
public class ModuleInfo implements Module {
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
    private int count = 0;
    private int errorCount = 0;
    
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
        } else {  // CoreModule
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
    
    /** Test if is the same implementation class.
     * @return Same implementation
     */
    boolean isTheSameClass( Configuration cfg2) {
        if( cfg2 == null) {
            return false;
        }
        return cfg.getString( "class").equals( cfg2.getString( "class"));
    }

    /** Get the actual module configuration.
     * @return Properties
     */
    public Configuration getConfiguration() {
        return cfg;
    }

    /** Set the new module configuration.
     * @param cfg New configuration
     * @throws Exception Unexpected error
     */
    public void setConfiguration( Configuration cfg) throws Exception {
        // Add generic config descriptors
        List<ConfigItemDescriptor> cdl = getImplementation().getConfigDescriptors();
        if( cdl == null) {
            cdl = new LinkedList();
        }
        cdl.add( new ConfigItemDescriptor( "class", ConfigItemDescriptor.STRING,
               "Specify lon class name or URL of the service", null));
        cdl.add( new ConfigItemDescriptor( "LimitActiveThreads", ConfigItemDescriptor.MODULE,
               "Keep track and limit the concurrent threads ons this module", null));
        cdl.add( new ConfigItemDescriptor( "Monitor", ConfigItemDescriptor.BOOLEAN,
               "Wrap module with SvcMonitor to get statistics and acitvity", "false"));
        cdl.add( new ConfigItemDescriptor( "TimeOut", ConfigItemDescriptor.BOOLEAN,
               "Time out of this module", null));
        // Set default values 
        for( ConfigItemDescriptor cd: cdl) {
            if( cd.getDefaultValue() != null &&           // has a default value
                    ( !cd.getKey().contains( "*")) &&     // Simple cfg. 
                    ( !cfg.containsKey( cd.getKey()))) {  // w/o given value in cfg
                //LOG.trace( moduleName + " set default " + cd.getKey() + "=" + cd.getDefaultValue());
                cfg.put( cd.getKey(), cd.getDefaultValue());
            }
        }    
        // Log config status
        if( LOG.isTraceEnabled()) {
            Map<String,String> cfgItems = cfg.getStringMap( "*");
            LOG.trace( "Config: " + moduleName + " class=" + moduleImpl.getClass().getName());
            cfgItems.remove( "class");
            for( ConfigItemDescriptor cd: cdl) {
                if( !cd.getKey().contains( "*")) {    // Simple cfg.
                    if( cfg.containsKey( cd.getKey())) {
                        LOG.trace( "Config:   // " + cd.getDescription());
                        LOG.trace( "Config:   " + cd.getKey() + "=" + cfg.getString( cd.getKey()));
                        cfgItems.remove( cd.getKey());
                    }
                } else {
                    for( String k: cfg.getStringMap( cd.getKey()).keySet()) {
                        LOG.trace( "Config:   // " + cd.getDescription());
                        String kk = cd.getKey().replace( "*", k);
                        LOG.trace( "Config:   " + kk + "=" + cfg.getString( kk));
                        cfgItems.remove( kk);
                    }
                }
            }
            for( String k: cfgItems.keySet()) {
               LOG.trace( "Config: Undefined configuration!: " + k);
               LOG.trace( "Config:   " + k + "=" + cfg.getString( k));
            }
        }
        // Reset status
        activeCount = 0;
        topActiveCount = 0;
        // Apply generic configuration
        if( cfg.containsKey( "LimitActiveThreads")) {
            limitActiveCount = cfg.getInt( "LimitActiveThreads");
            concCtrl = true;
        }
        if( cfg.getBoolean( "Monitor") && asyncImpl != null) {
            LOG.trace( "Monitor instanced on " + moduleName);
            monitorImpl = new SvcMonitor( asyncImpl, moduleName);
        } else {
            monitorImpl = null;
        }
        // Update config
        this.cfg = cfg.clone(); 
        if( moduleImpl instanceof CoreModule) {  
            ( (CoreModule)moduleImpl).startup( this.cfg);
        }
    }
    
    /** Running instances accounting, if can add one more. */
    boolean takeOne() {
        if( concCtrl) {
            synchronized( lockConcCtrl) {  
                if( activeCount >= limitActiveCount) {
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

    /*
    int getTimeOut() {
        try {
            return cfg.getInt( "TimeOut", DEFAULT_TIME_OUT);
        } catch( Exception x) {
            return DEFAULT_TIME_OUT;
        }
    }
    */

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
        SvcRequest req = ( msg instanceof SvcRequest) ? 
                (SvcRequest)msg:
                ((SvcResponse)msg).getRequest();
        SvcResponse resp = null;
        if( !takeOne()) {  // Too many running instances, cancel!
            ++count;
            ++errorCount;
            LOG.warn( "Too many concurrent active " + moduleName + " " + topActiveCount);
            return new SvcResponse( SvcResponse.MSG_TOPPED + moduleName, 
                SvcResponse.RES_CODE_TOPPED, null, req);
        }
        try {
            AsyncService as = ( monitorImpl != null)? monitorImpl: asyncImpl;
            if( msg instanceof SvcRequest) {
                ++count;
                msg = as.onRequest( req, cfg);
                /* 
                if( LOG.isDebugEnabled() && msg instanceof SvcResponse) {
                    LOG.debug( "processMessage <<| " + moduleName + " " + msg);
                } 
                */
            } else if( msg instanceof SvcResponse) {
                msg = resp = as.onResponse( (SvcResponse)msg, cfg);
            }        
        } catch( SvcException ex) {
            String s = ex.getMessage() + " on module '" + moduleName + "'";
            LOG.warn( s, ex);
            msg = resp = new SvcResponse( s, ex.getErrorCode(), ex, req);
        } catch( Exception ex) {
            String s = "Unexpected error on module '" + moduleName + "' " + ex;
            LOG.warn( s, ex);
            msg = resp = new SvcResponse( s, SvcResponse.RES_CODE_EXCEPTION, ex, req);
        } finally {
            releaseOne();
            if( resp != null && resp.getResultCode() < 0) {
                ++errorCount;
            }
        }
        return msg;
    }

    /** Report module status plus active count.
     * @return Status vars Map 
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> m = new TreeMap();
        m.put( "Count", count);
        m.put( "ErrorCount", errorCount);
        m.put( "ServiceLevel", 1 - errorCount / ( count + 0.000001));
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
    
    /** Get configuration descriptors. */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        List<ConfigItemDescriptor> l = getImplementation().getConfigDescriptors();
        if( l == null) {
            l = new LinkedList();
        }
        return l;
    }

    /** Stop service. */
    @Override
    public void shutdown() {
        getImplementation().shutdown();
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

