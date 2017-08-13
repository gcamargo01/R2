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

    /** Map Constructor.
     * @param map Configuration items map
     */
    public Configuration( Map<String,Object> map){
        cfg = map;
    }

    /** Create a clone.
     * @return A new configuration
     */
    @Override
    public Configuration clone( ){
        return new Configuration( this.cfg);
    }

    /** Determine if the configuration has changed.
     * To avoid unnecessary processing
     * @return Boolean
     */
    public boolean isChanged( ){
        return changed;
    }

    /** String getter.
     * @param key Configuration key
     * @param defValue Default value
     * @return Not null value as string
     */
    public String getString( String key, String defValue) {
        changed = false;
        Object v = cfg.get( key);
        if( v == null) {
            return defValue;
        }
        return v.toString();
    }

    /** String getter.
     * @param key Configuration key
     * @return Not null value as string
     */
    public String getString( String key) {
        return getString( key, "");
    }
    
    /** Multivalued String getter.
     * The key has values like "Sever.*.Name", while de configuration
     * has "Server.zeus.Name", "Z01"), must build a Map with ( "zeus", "Z01")
     * @param key Configuration key
     * @return Ordered Map of elements
     * @throws Exception Parsing error
     */
    public Map<String,String> getStringMap( String key) throws Exception {
        changed = false;
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
     * @param defValue Default value
     * @return Not null value as string
     * @throws Exception Parsing error
     */
    public int getInt( String key, int defValue) throws Exception {
        String s = getString( key, Integer.toString( defValue ));
        return (int)Double.parseDouble( s);
    }

    /** Integer getter.
     * @param key Configuration key
     * @return Not null value as string
     * @throws Exception Parsing error
     */
    public int getInt( String key) throws Exception {
        return getInt( key, 0);
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
     * @param defValue Default value
     * @return Not null value as long
     * @throws Exception Parsing error
     */
    public long getLong( String key, long defValue) throws Exception {
        if( !cfg.containsKey( key)) {
            return defValue;
        }
        return (long)Double.parseDouble( getString( key));
    }

    /** Long getter.
     * @param key Configuration key
     * @return Not null value as long
     * @throws Exception Parsing error
     */
    public long getLong( String key) throws Exception {
        return getLong( key, 0L);
    }

    /** Double getter.
     * @param key Configuration key
     * @param defValue Default value
     * @return Not null value as double
     * @throws Exception Parsing error
     */
    public double getDouble( String key, double defValue) throws Exception {
        if( !cfg.containsKey( key)) {
            return defValue;
        }
        return Double.parseDouble( getString( key));
    }

    /** Long getter.
     * @param key Configuration key
     * @return Not null value as string
     * @throws Exception Parsing error
     */
    public double getDouble( String key) throws Exception {
        return getDouble( key, 0L);
    }

    /** URL getter.
     * @param key Configuration key
     * @param defValue Default value
     * @return Not null value as URL
     * @throws Exception Parsing error
     */
    public URL getURL( String key, URL defValue) throws Exception {
        if( !cfg.containsKey( key)) {
            return defValue;
        }
        return new URL( getString( key));
    }

    /** Long getter.
     * @param key Configuration key
     * @return Not null value as string
     * @throws Exception Parsing error
     */
    public URL getURL( String key) throws Exception {
        return getURL( key, new URL( "http://UNDEFINED_HOST"));
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
        cfg.put( key, value);
    }
    
    /** Serialize to String.
     * @return String
     */
    @Override
    public String toString() {
        return cfg.toString();
    }
    
    /* Test getMap *
    void test( ) {
        Map<String,Object> m = new HashMap<>();
        m.put( "Command.1.A", "some1");
        m.put( "Command.3.A", "some3");
        m.put( "Command.2.A", "some2");
        Map<String,Object> m = new HashMap<>();
        m.put( "Service.Prb.Kind", "funny");
        m.put( "Command.1.A", "some1");
        System.out.println( "has cmd=" + c.containsKey( "Command.*.A"));
        System.out.println( "cmd=" + c.getStringMap( "Command.*.A"));
        m.put( "Command.2.A", "some2");
        Configuration c = new Configuration( m);
        System.out.println( "c=" + c);
        System.out.println( "map=" + c.getStringMap( "Service.*.Kind"));
        System.out.println( "has cmd=" + c.containsKey( "Command.*.A"));
        System.out.println( "cmd=" + c.getStringMap( "Command.*.A"));
    }
    **/

}
