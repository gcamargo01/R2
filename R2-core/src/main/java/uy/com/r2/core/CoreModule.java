/* CoreModule.java */
package uy.com.r2.core;

import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Module;

/** R2 core module interface.
 * A common interface to internal modules, that needs the event to start or
 * needs some Configuration.
 * @author G.Camargo
 */
public interface CoreModule extends Module {
    
    /** Startup.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    public void startup( Configuration cfg) throws Exception;

}
