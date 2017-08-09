/* SimpleService.java */
package uy.com.r2.core.api;

/** R2 simple and direct call service module interface.
 * A SimpleService can call {@link uy.com.r2.core.api.Dispatcher}.callNext() 
 * to call the next module in the service module pipeline.
 * @author G.Camargo
 */
public interface SimpleService extends Module {
    
    /** Service call.
     * The Configuration is a parameter on service call method to allow 
     * a stateless implementation, such a EJB3 Stateless Session Bean. 
     * The container may restart, create few instances and distribute the 
     * module on many JVM / servers as needed to scale.
     * Each service implementation may throw a Exception to clearly set 
     * what originates the failure. <br>
     * @param req Invocation message
     * @param cfg Module configuration
     * @return SvcResponse message
     * @throws Exception Unexpected error that must be warned
     */
    public SvcResponse call( SvcRequest req, Configuration cfg) throws Exception;

}
