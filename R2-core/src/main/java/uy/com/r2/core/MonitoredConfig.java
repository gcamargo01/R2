/* MmoitoredConfig.java */
package uy.com.r2.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;

/** Monitored version of the Configuration class.
 * This implementation warn when some undefined configuration is used.
 * @author G.Camargo
 */
public class MonitoredConfig extends Configuration {
    private static final Logger LOG = Logger.getLogger( MonitoredConfig.class);
    private String moduleName;
    private final Map<String, ConfigItemDescriptor> cfgDesc;

    public MonitoredConfig( String name, Configuration cfg) {
        this.moduleName = name;
        this.cfgDesc = new HashMap();
        ModuleInfo mi = SvcCatalog.getCatalog().getModuleInfo( name);
        // Get config descriptors with generic config def
        List<ConfigItemDescriptor> cdl = mi.getConfigDescriptors();
        // Log config status
        try {
            Map<String,String> unknowCfg = cfg.getStringMap( "*");
            for( ConfigItemDescriptor cd: cdl) {
                if( !cd.getKey().contains( "*")) {    // Simple cfg.
                    if( cfg.containsKey( cd.getKey())) {
                        unknowCfg.remove( cd.getKey());
                    }
                } else {
                    for( String k: cfg.getStringMap( cd.getKey()).keySet()) {
                        String kk = cd.getKey().replace( "*", k);
                        unknowCfg.remove( kk);
                    }
                }
            }
            for( String k: unknowCfg.keySet()) {
                String s = "Undefined configuration: " + k + "=" + unknowCfg.get( k)
                        + " on module " + name;
                LOG.warn( "Ignored exception", new Exception( s));
            }
        } catch( Exception x) {
             LOG.warn( "Unable to check cfg definition, " + x, x);
        }
        for( ConfigItemDescriptor cid: mi.getConfigDescriptors()) {
            cfgDesc.put( cid.getKey(), cid);
        };
        Map<String,String> m = null;
        try {
            m = cfg.getStringMap(  "*");
        } catch( Exception ex ) { }
        for( String k: m.keySet()) {
            super.put( k, m.get(  k));
        }
    }

    /** String getter.
     * @param key Configuration key
     * @return Not null value as string
     */
    @Override
    public String getString( String key) {
        if( !cfgDesc.containsKey( key)) {
            String s = moduleName + " uses undeclared config " + key;
            LOG.warn( "Ignored exception", new Exception( s));
        }
        String type = "?";
        try {
            type = cfgDesc.get( key).getKlass().getName();
        } catch( Exception x) { }
        String v = super.getString( key);
        LOG.info( moduleName + " get( " + key +  "):" + type + " = " + v);
        return v;
    }

    /** Multivalued String getter.
     * The key has values like "Sever.*.Name", while de configuration
     * has "Server.zeus.Name", "Z01"), must build a Map with ( "zeus", "Z01")
     * @param key Configuration key
     * @return Ordered Map of elements
     * @throws Exception Parsing error
     */
    @Override
    public Map<String,String> getStringMap( String key) throws Exception {
        Map<String,String> m = super.getStringMap( key);
        if( !cfgDesc.containsKey( key)) {
            String s = moduleName + " uses undeclared config " + key; 
            LOG.warn( "Ignored exception", new Exception( s));
        }
        LOG.info( moduleName + " getMap( " + key +  "):" + " = " + m);
        return m;
    }

}


