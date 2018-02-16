/* Module.java */
package uy.com.r2.core.api;

import java.util.List;
import java.util.Map;

/** Base interface of all R2 service modules.
 * Interface with methods to configure (getConfigDescriptors, 
 * setConfiguration), to get status (getStatusVars) and to release  
 * resources been used (shutdown). 
 * @author G.Camargo
 */
public interface Module {
    
    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * It is not mandatory to return the complete list of parameters used, but 
     * it is considered a bad practice not to document them, and in each case it is warned.
     * @return ConfigItemDescriptor List
     */
    public List<ConfigItemDescriptor> getConfigDescriptors();

    /** Get the status report of the module.
     * Used to get (and monitoring) current status of the module.
     * The variables may include: Version, ServiceLevel, LastErrors, an so on.
     * The core add: (usage) Count, ErrorCount, ServiceLevel(1=Ok, 0=Total failure), 
     * and others when the service is monitored<br>
     * @return Variable and value map
     */
    public Map<String,Object> getStatusVars();

    /** Stop execution and release all the allocated resources. */
    public void shutdown();

}


