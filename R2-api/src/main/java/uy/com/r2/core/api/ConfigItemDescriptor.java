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
    
    public enum Attribute {
        Secured, 
        Environment 
    }
    
    /** Safeguard and environment dependant item. */
    public static final Attribute SECURED = Attribute.Secured;  
    
    /** Environment dependant item, should change on deploy. */
    public static final Attribute ENVIRONMENT = Attribute.Environment;  
    
    private final String key;
    private final Class klass;
    private final String description;
    private final String defaultValue;
    private final Attribute attribute;
    
    /** Constructor, full detailed item.
     * @param key Configuration key
     * @param klass Value type
     * @param description Item description
     * @param defaultValue Default value as String, in development environment
     * @param attribute Special attribute modifier
     */
    public ConfigItemDescriptor( String key, Class klass, String description, 
            String defaultValue, Attribute attribute) {
        this.key = key;
        this.klass = klass;
        this.description = description;
        this.defaultValue = defaultValue;
        this.attribute = attribute;
    }
    
    /** Constructor, simple item with default value.
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
        this.attribute = null;
    }
        
    /** Constructor, simple item.
     * @param key Configuration key
     * @param klass Value type
     * @param description Item description
     */
    public ConfigItemDescriptor( String key, Class klass, String description) { 
        this.key = key;
        this.klass = klass;
        this.description = description;
        this.defaultValue = null;
        this.attribute = null;
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
        
    /** Getter.
     * @return the attribute
     */
    public Attribute getAttribute() {
        return attribute;
    }
        
}
