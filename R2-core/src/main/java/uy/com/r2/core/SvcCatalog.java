/* Core.java */
package uy.com.r2.core;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Module;
//import uy.com.r2.svc.tools.SvcManager;

/** Service modules catalog.
 * This module is only to be user locally, except getDispatcher() 
 * which is user by the modules itself.
 * @author G.Camargo
 */
public class SvcCatalog implements Module {
    private static final String CATALOG_NAME = SvcCatalog.class.getSimpleName();
    private static final String DISPATCHER_NAME = "Dispatcher";
    private static final Logger LOG = Logger.getLogger(SvcCatalog.class);
    private static SvcCatalog core = null;
    private static Dispatcher dispatcher = null;

    private boolean stopping = false;
    private final HashMap<String,ServiceInfo> modules;
    private final HashMap<String,Object> running;
    
    private SvcCatalog() {  // Private constructor.
        modules = new HashMap();
        modules.put( CATALOG_NAME, new ServiceInfo( CATALOG_NAME, this));
        running = new HashMap();
    }  
    
    /** Get the Catalog instance.
     * This class is a singleton.
     * @return Kernel 
     */
    public static synchronized SvcCatalog getCatalog() {
        if( core == null) {
            core = new SvcCatalog();
        }
        return core;
    }

    /** Get a Dispatcher instance.
     * @return Dispatcher instance
     */
    public static Dispatcher getDispatcher() {
        if( dispatcher != null) {  
            return dispatcher;
        }
        dispatcher = ( Dispatcher)( ( core.modules.get( DISPATCHER_NAME)).getImplementation());
        return dispatcher;
    }

    /** Instance a module, its class and configure it.
     * @param moduleName Module name may be different to class name
     * @param cfg Configuration
     * @throws Exception Unexpected error, can't install
     */
    public void installModule( String moduleName, Configuration cfg) 
            throws Exception {
        if( cfg == null) {
            cfg = new Configuration();
        }
        String className = "" + cfg.getString( "class");
        LOG.trace( "installModule " + moduleName + " " + className);
        // Kernel name used, set its configuration
        if( CATALOG_NAME.equals( moduleName)) {
            return;
        }
        // Load from the net
        ClassLoader loader = getClass().getClassLoader();
        String loaderDesc = "";
        if( cfg.containsKey( "URL")) {
            URL u = cfg.getURL( "URL");
            loader = new URLClassLoader( new URL[] { u});
            loaderDesc = " from " + u.toString();
        }
        // Get service implementation
        Module moduleImpl;
        try {
            moduleImpl = ( Module)loader.loadClass( className).newInstance();
        } catch ( Exception xx) {
            throw new Exception( "Can't load '" + className + "' " + loaderDesc, xx);
        }
        installModule( moduleName, moduleImpl, cfg);
    } 
          
    /** Instance a module with a given module implementations and configure it.
     * @param moduleName Module name may be different to class name
     * @param moduleImpl Module implementation
     * @param cfg Configuration
     * @throws Exception Unexpected error, can't install
     */
    public void installModule( String moduleName, Module moduleImpl, Configuration cfg) 
            throws Exception {
        // setup modules map
        if( modules.containsKey( moduleName)) {
            throw new Exception( "Module '" + moduleName 
                    + "' was already installed.");
        }
        ServiceInfo mi = new ServiceInfo( moduleName, moduleImpl);
        modules.put( moduleName, mi);
        // setup configuration
        updateConfiguration( moduleName, cfg);
    } 
          
    /** Update module configuration.
     * @param moduleName Module name may be different to class name
     * @param cfg Configuration
     * @throws Exception Unexpected error, can't update configuration
     */
    public void updateConfiguration( String moduleName, Configuration cfg) 
            throws Exception {
        if( cfg == null) {
            cfg = new Configuration();
        }
        LOG.trace( "updateConfiguration " + moduleName + " " + cfg.toString());
        // setConfiguration
        ServiceInfo mi = modules.get( moduleName);
        if( mi == null) {
            installModule( moduleName, cfg);
        } else if( !mi.isTheSameClass( cfg)                    // If diff class
                && mi.getConfiguration().containsKey( "class") // && has cfg 
                && !CATALOG_NAME.equals( moduleName)) {           // && not Kernel
            uninstallModule( moduleName);     
            mi.setConfiguration( cfg);        // Do not re-enter
            installModule( moduleName, cfg);
        } else {
             mi.setConfiguration( cfg);
             //SvcManager.onModuleUpdate( moduleName);
        }
    }
     
    /** Free the service module.
     * @param moduleName Module name
     * @throws Exception Not found 
     */
    public void uninstallModule( String moduleName) throws Exception {
        LOG.trace( "uninstallModule " + moduleName);
        if( CATALOG_NAME.equals( moduleName)) {
            LOG.info( "Invalid Kernel module action, ignored");
            return;
        }
        ServiceInfo mi = modules.get( moduleName);
        if( mi == null) {
            throw new Exception( "Module '" + moduleName + "' not found");
        }
        modules.remove( moduleName);
        mi.getImplementation().shutdown();
        //SvcManager.onModuleUndeploy( moduleName);
    } 
    
    /** Get module information by its name.
     * @param moduleName Module name
     * @return InstalledModule
     */
    public ServiceInfo getModuleInfo( String moduleName) {
        return modules.get( moduleName);
    } 
    
    /** Get all the module names.
     * @return Set of Strings
     */
    public Set<String> getModuleNames( ) {
        return modules.keySet();
    } 
    
    /** Test if system is shutting down.
     * @return Boolean: Stopping all the system
     */
    public boolean isStopping() {
        return stopping;
    }

    /** Get the status report.
     * @return Map of status variables
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> m = new TreeMap();
        m.put( "Version", "$Revision: 1.1 $");
        m.put( "ModuleNames", getModuleNames());
        return m;
    }
    
    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Dispatcher", ConfigItemDescriptor.STRING,
                "Dispatcher class name", null));
        return l;        
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        LOG.info( "Kernel shutdown in progress...");
        stopping = true;
        try {
            Thread.sleep( 500);  // Let deployers shutdown itself
        } catch (InterruptedException ex) { }
        Set<String> nml = new HashSet( SvcCatalog.getCatalog().getModuleNames());
        nml.remove(CATALOG_NAME);  // avoid loop
        for( String n: nml) {
            try {
                uninstallModule( n);
            } catch ( Exception ex) { }
        }
    }    
    
}
