/* SvcCatalog.java */
package uy.com.r2.core;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Module;
import uy.com.r2.svc.tools.SvcManager;

/** Service modules catalog.
 * This module is almost the core of the entire system. It keeps the list
 * of the installed modules, and a special one module: the Dispatcher.
 * By a convention it must be called "SvcDispatcher" or it si auto installed.
 * @author G.Camargo
 */
public class SvcCatalog implements CoreModule {
    public static final String CATALOG_NAME = SvcCatalog.class.getSimpleName();
    public static final String DISPATCHER_NAME = "SvcDispatcher";
    
    private static final Logger LOG = Logger.getLogger(SvcCatalog.class);
    private static final Object LOCK = new Object();
    private static SvcCatalog catalog = null;
    private static Dispatcher dispatcher = null;

    private boolean stopping = false;
    private final HashMap<String,ModuleInfo> modules;
    
    private SvcCatalog() {  // Private constructor.
        modules = new HashMap();
        modules.put( CATALOG_NAME, new ModuleInfo( CATALOG_NAME, this));
    }  
    
    /** Get the Catalog instance.
     * This class is a singleton.
     * @return Kernel 
     */
    public static SvcCatalog getCatalog() {
        if( catalog != null) {
            return catalog;
        }
        synchronized( LOCK) {
            if( catalog == null) {
                catalog = new SvcCatalog();
            }
        }
        return catalog;
    }

    /** Get a Dispatcher instance.
     * @return Dispatcher instance
     */
    public static Dispatcher getDispatcher() {
        if( dispatcher == null) {
            dispatcher = ( Dispatcher)getCatalog().getModuleInfo( DISPATCHER_NAME).getImplementation();
            if( dispatcher != null) {
                return dispatcher;  // Just loaded
            }
            try {
                dispatcher = new SimpleDispatcher();
                LOG.info( "Auto-install dispatcher");
                catalog.installModule( DISPATCHER_NAME, dispatcher, null);
            } catch( Exception x) {
                LOG.error( "Failed to auto-install dispatcher", x);
            }
        }
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
        // Sepecial names Catalog & Deployer
        if( CATALOG_NAME.equals( moduleName)) {
            return;
        }
        // Load from the net
        ClassLoader loader = getClass().getClassLoader();
        String loaderDesc = "";
        if( cfg.containsKey( "classUrl")) {
            URL u = cfg.getURL( "classUrl");
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
        if( cfg == null) {
            cfg = new Configuration();
        }
        LOG.info( "installModule " + moduleName + " " + cfg.toString());
        // setup modules map
        if( modules.containsKey( moduleName)) {
            throw new Exception( "Module '" + moduleName + "' is already installed.");
        }
        ModuleInfo mi = new ModuleInfo( moduleName, moduleImpl);
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
        LOG.info( "updateConfiguration " + moduleName + " " + cfg.toString());
        // setConfiguration
        ModuleInfo mi = modules.get( moduleName);
        if( mi == null) {  // New module
            installModule( moduleName, cfg);
        } else if( !mi.isTheSameClass( cfg)                    // If diff class
                && mi.getConfiguration().containsKey( "class") // && has cfg 
                && !CATALOG_NAME.equals( moduleName)) {        // && not this
            uninstallModule( moduleName);     
            mi.setConfiguration( cfg);        // Do not re-enter
            installModule( moduleName, cfg);
        } else {
             mi.setConfiguration( cfg);
             SvcManager.onModuleUpdate( moduleName);
        }
    }
     
    /** Free the service module.
     * @param moduleName Module name
     * @throws Exception Not found 
     */
    public void uninstallModule( String moduleName) throws Exception {
        LOG.info( "uninstallModule " + moduleName);
        if( CATALOG_NAME.equals( moduleName)) {
            LOG.info( "Module " + moduleName + " can't be uninstalled, ignored");
            return;
        }
        ModuleInfo mi = modules.get( moduleName);
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
    public ModuleInfo getModuleInfo( String moduleName) {
        return modules.get( moduleName);
    } 
    
    /** Get all the module names.
     * @return Set of Strings
     */
    public Set<String> getModuleNames( ) {
        // return ordered clone
        return new TreeSet( modules.keySet());
    } 
    
    /** Test if system is shutting down.
     * @return Boolean: Stopping all the system
     */
    public boolean isStopping() {
        return stopping;
    }

    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        return null;
    }

    /** Startup.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    @Override
    public void startup( Configuration cfg) throws Exception {
    }
    
    /** Get the status report.
     * @return Map of status variables
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> map = new TreeMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        } 
        map.put( "ModuleNames", getModuleNames());
        Set<String> mns = getModuleNames();
        mns.remove( SvcCatalog.CATALOG_NAME);  // Avoid Loop
        for( String n: mns) {
            Map<String,Object> v = getModuleInfo( n).getStatusVars();
            map.put( "Module_" + n, v);
        }
        return map;
    }
    
    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        LOG.info( "Catalog shutdown ...");
        stopping = true;
        try {
            Thread.sleep( 500);  // Let deployers shutdown itself
        } catch (InterruptedException ex) { }
        Set<String> nml = new HashSet( SvcCatalog.getCatalog().getModuleNames());
        nml.remove( CATALOG_NAME);  // avoid loop
        for( String n: nml) {
            try {
                uninstallModule( n);
            } catch ( Exception ex) { }
        }
        dispatcher.shutdown();
    }    

}
