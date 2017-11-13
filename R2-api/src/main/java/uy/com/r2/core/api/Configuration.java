/* Configuration.java */
package uy.com.r2.core.api;

import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.io.Serializable;

/** Module configuration.
 * A Map of environment settings, each one with its own 
 * {@link uy.com.r2.core.api.ConfigItemDescriptor} and actual value.
 * @author G.Camargo
 */
public class Configuration implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Map<String,Object>cfg = new TreeMap<>();
    private boolean changed = true;
    
    /** Empty constructor.
     */
    public Configuration( ) {
    }

    /** Determine if the configuration has changed.
     * To avoid unnecessary processing. Use with resetChaged();
     * @return Boolean
     */
    public boolean isUpdated( ) {
        return changed;
    }

    /** Clear changed flag.
     * To explicit set this status.
     */
    public void clearUpdated( ) {
        changed = false;
    }

    /** String getter.
     * @param key Configuration key
     * @return Not null value as string
     */
    public String getString( String key) {
        Object v = cfg.get( key);
        if( v == null) {
            return "";
        }
        return v.toString();
    }

    /** Multivalued String getter.
     * The key has values like "Sever.*.Name", while de configuration
     * has "Server.zeus.Name", "Z01"), must build a Map with ( "zeus", "Z01")
     * @param key Configuration key
     * @return Ordered Map of elements
     * @throws Exception Parsing error
     */
    public Map<String,String> getStringMap( String key) throws Exception {
        if( !key.contains( "*")) {
            throw new Exception( "The config item " + key 
                    + " is not multivalued (must have *)");
        }
        String key1 = key.substring( 0, key.indexOf( "*"));
        String key2 = key.substring( key.indexOf( "*") + 1);
        Map<String,String> map = new TreeMap<>();
        for( String k: cfg.keySet()) {
            if( k.startsWith( key1) && k.endsWith( key2)) {
                String nk = k.substring( 0, k.length() - key2.length());
                nk = nk.substring( key1.length());
                map.put( nk, "" + cfg.get( k));
            }
        }
        return map;
    }

    /** Integer getter.
     * @param key Configuration key
     * @return Not null value as string
     * @throws Exception Parsing error
     */
    public int getInt( String key) throws Exception {
        String s = getString( key);
        if( s == null || s.isEmpty()) {
            return 0;
        }
        return (int)Double.parseDouble( s);
    }

    /** Multivalued String getter.
     * The key has values like "Sever.*.Number", while de configuration
     * has "Server.zeus.Number", "101"), must build a Map with ( "zeus", 101)
     * @param key Configuration key
     * @return Ordered Map of elements
     * @throws Exception Parsing error
     */
    public Map<String,Integer> getIntMap( String key) throws Exception {
        Map<String,String> map = getStringMap( key);
        Map<String,Integer> map2 = new TreeMap<>();
        for( String k: map.keySet()) {
             map2.put( k, Integer.parseInt( "" + map.get( k)));
        }
        return map2;
    }

    /** Long getter.
     * @param key Configuration key
      * @return Not null value as long
     * @throws Exception Parsing error
     */
    public long getLong( String key) throws Exception {
        if( !cfg.containsKey( key)) {
            return 0L;
        }
        return (long)Double.parseDouble( getString( key));
    }

    /** Double getter.
     * @param key Configuration key
     * @return Not null value as double
     * @throws Exception Parsing error
     */
    public double getDouble( String key) throws Exception {
        if( !cfg.containsKey( key)) {
            return 0D;
        }
        return Double.parseDouble( getString( key));
    }

    /** URL getter.
     * @param key Configuration key
     * @return URL 
     * @throws Exception Parsing error
     */
    public URL getUrl( String key) throws Exception {
        if( !cfg.containsKey( key)) {
            return null;
        }
        return new URL( getString( key));
    }

    /** Test a configuration boolean value.
     * @param key Configuration key
     * @return Boolean
     */
    public boolean getBoolean( String key) {
        return Boolean.parseBoolean( "" + cfg.get( key));
    }

    /** Test if has some configuration.
     * @param key Configuration key. admits '*' as wildchar
     * @return Boolean
     */
    public boolean containsKey( String key) {
        if( key.contains( "*")) {
            String start = key.substring( 0, key.indexOf( '*'));
            String end = key.substring( key.lastIndexOf( '*') + 1, key.length());
            for( String k: cfg.keySet()) {
                if( k.startsWith( start) && k.endsWith( end)) {
                    return true;
                } 
            }
        }
        return cfg.containsKey( key);
    }

    /** Put a new value.
     * @param key configuration key
     * @param value Configuration value
     */
    public void put( String key, Object value) {
        changed = true;
        if( key == null) {
            return;
        }
        cfg.put( key, value);
    }
    
    /** Serialize to String.
     * @return String
     */
    @Override
    public String toString() {
        return cfg.toString();
    }
    
}
