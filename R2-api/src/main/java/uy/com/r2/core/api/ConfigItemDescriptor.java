/* ConfigItemDescriptor.java */
package uy.com.r2.core.api;

import java.net.URL;

/** Configuration item descriptor.
 * Each configurable item must have a descriptor, to document 
 * its type, function, usage and default value.
 * @author G.Camargo
 */
public class ConfigItemDescriptor {
    public static final Class STRING = String.class;
    public static final Class INTEGER = Integer.class;
    public static final Class BOOLEAN = Boolean.class;
    public static final Class URL = URL.class;
    public static final Class MODULE = Module.class;
    
    private final String key;
    private final Class klass;
    private final String description;
    private final String defaultValue;
    
    /** Constructor, simple item.
     * @param key Configuration key
     * @param klass Value type
     * @param description Item description
     * @param defaultValue Default value as String
     */
    public ConfigItemDescriptor( String key, Class klass, String description, 
            String defaultValue) {
        this.key = key;
        this.klass = klass;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    /** Getter.
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /** Getter.
     * @return the type
     */
    public Class getKlass() {
        return klass;
    }

    /** Getter.
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /** Getter.
     * @return the value
     */
    public String getDefaultValue() {
        return defaultValue;
    }
        
}
