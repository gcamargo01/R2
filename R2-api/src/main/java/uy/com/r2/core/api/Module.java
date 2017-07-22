/* Module.java */
package uy.com.r2.core.api;

import java.util.List;
import java.util.Map;

/** Base interface of all R2 service modules.
 * Interface that with methods to configure (getConfigDescriptors, 
 * setConfiguration), to get status (getStatusVars) and to release  
 * resources been used (shutdown). 
 * @author G.Camargo
 */
public interface Module {
    
    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * @return ConfigItemDescriptor List
     */
    public List<ConfigItemDescriptor> getConfigDescriptors();

    /** Get the status report of the module.
     * It may occurs at any time to get the current status of the module.
     * Tha variables may include: Version, ServiceLevel, LastErrors, ...
     * @return Variable and value map
     */
    public Map<String,Object> getStatusVars();

    /** Stop execution ns release all the allocated resources. */
    public void shutdown();

}


