/* StartableModule.java */
package uy.com.r2.core.api;

/** Started up module.
 * Modules that need to be started or needs some Configuration.
 * @author G.Camargo
 */
public interface StartableModule extends Module {
    
    /** Start up module execution.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    public void start( Configuration cfg) throws Exception;

}
