/* AsyncService.java */
package uy.com.r2.core.api;


/** R2 stateless service module interface.
 * This interface implements a service by dividing it into two phases:
 * onRequest and onResponse.
 * The module implementation may call 
 * {@link uy.com.r2.core.api.Dispatcher}.onEvent( 
 * {@link uy.com.r2.core.api.SvcMessage} )
 * to signal the event of a new message to be processed (asynchronous). <br>
 * (*) The Configuration is a parameter of each method.
 * That way makes each module implementation may be state-less, 
 * such a EJB3 Stateless Session Bean. The container may restart, create 
 * few instances and distribute the module on many JVM / servers as needed
 * to scale.
* @author G.Camargo
 */
public interface AsyncService extends Module {
    
    /** Invocation dispatch phase.
     * The module implementation may return: <br>
     * (1) A SvcRequest to dispatch to the next service module, or <br>
     * (2) A SvcResponse created (example: Error condition), <br>
     * (3) NULL when there aren't next module to call (yet) <br>
     * (4) throw a Exception to explicit set the module that originates 
     * the failure.
     * @param req Service request message 
     * @param cfg Module configuration (*)
     * @return SvcRequest, SvrResponse or NULL
     * @throws Exception Unexpected error
     */
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception;

    /** Process a response phase.
     * The container calls this method to process a response. 
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcRequest from next module  
     * @param cfg Module configuration (*)
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception;

}

