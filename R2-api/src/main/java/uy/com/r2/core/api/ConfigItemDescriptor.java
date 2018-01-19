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
    
    /** Particular role that is responsible for setting this item. */
    public enum Role {
        Security, 
        Deployer 
    }
    
    /** Safeguard and environment dependant item. */
    public static final Role SECURITY = Role.Security;  
    
    /** Environment dependant item, should change on deploy. */
    public static final Role DEPLOYER = Role.Deployer;  
    
    private final String key;
    private final Class klass;
    private final String description;
    private final String defaultValue;
    private final Role role;
    
    /** Constructor, full detailed item.
     * @param key Configuration key
     * @param klass Value type
     * @param description Item description
     * @param defaultValue Default value as String, in development environment
     * @param role Particular role 
     */
    public ConfigItemDescriptor( String key, Class klass, String description, 
            String defaultValue, Role role) {
        this.key = key;
        this.klass = klass;
        this.description = description;
        this.defaultValue = defaultValue;
        this.role = role;
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
        this.role = null;
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
        this.role = null;
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
    public Role getRole() {
        return role;
    }
        
}
