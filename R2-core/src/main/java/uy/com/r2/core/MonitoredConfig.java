/* MmoitoredConfig.java */
package uy.com.r2.core;

import java.util.HashMap;
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
    private String name;
    private final Map<String, ConfigItemDescriptor> cfgDesc;

    public MonitoredConfig( String name, Configuration cfg) {
        this.name = name;
        this.cfgDesc = new HashMap();
        ModuleInfo mi = SvcCatalog.getCatalog().getModuleInfo( name);
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
            LOG.warn( name + " uses undeclared config " + key, new Exception( "Stacktrace"));
        }
        String type = "?";
        try {
            type = cfgDesc.get( key).getKlass().getName();
        } catch( Exception x) { }
        String v = super.getString( key);
        LOG.info( "get( " + key +  "):" + type + " = " + v);
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
            LOG.warn( name + " uses undeclared config " + key, new Exception( "Stacktrace"));
        }
        LOG.info( "getMap( " + key +  "):" + " = " + m);
        return m;
    }

}


