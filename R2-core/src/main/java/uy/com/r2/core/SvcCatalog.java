/* SvcCatalog.java */
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
public class SvcCatalog implements CoreModule {
    public static final String CATALOG_NAME = SvcCatalog.class.getSimpleName();
    public static final String DISPATCHER_NAME = Dispatcher.class.getSimpleName();
    
    private static final Logger LOG = Logger.getLogger(SvcCatalog.class);
    private static final Object LOCK = new Object();
    private static SvcCatalog catalog = null;
    private static Dispatcher dispatcher = new SimpleDispatcher();

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
        if( CATALOG_NAME.equals( moduleName) || DISPATCHER_NAME.equals( moduleName)) {
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
        LOG.trace( "updateConfiguration " + moduleName + " " + cfg.toString());
        // setConfiguration
        ModuleInfo mi = modules.get( moduleName);
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
        return modules.keySet();
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
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Dispatcher", ConfigItemDescriptor.STRING,
                "Dispatcher class name", null));
        return l;        
    }

    /** Startup.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    @Override
    public void startup( Configuration cfg) throws Exception {
        if( cfg.isChanged()) {
            try {
                dispatcher = (Dispatcher)Class.forName( cfg.getString( "Dispatcher")).newInstance();
                modules.put( DISPATCHER_NAME, new ModuleInfo( DISPATCHER_NAME, dispatcher));
            } catch( Exception x) {
                LOG.warn( "can't instance Dispatcher", x);
            }    
        }
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
