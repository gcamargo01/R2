/* StartUpRequired.java */
package uy.com.r2.core;

import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Module;

/** Started up module.
 * A common interface to internal modules, that needs the event to start or
 * needs some Configuration.
 * @author G.Camargo
 */
public interface StartUpRequired extends Module {
    
    /** Start up.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    public void startUp( Configuration cfg) throws Exception;

}
