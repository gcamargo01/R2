/* Dispatcher.java */
package uy.com.r2.core.api;

/** Interface to call and manage execution of some service modules instances.
 * @author G.Camargo
 */
public interface Dispatcher extends Module {
    
    /** Start the execution of a request.
     * This method is used by end-point implementation, for example a remote client.
     * The pipeline of services to be executed depends on the Node configuration 
     * in the Dispatcher (Node.XXXX=Pipe1) or the DefaultServicePipeline.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    public SvcResponse call( SvcRequest req);   
 
    /** Dispatch the execution of the next service.
     * Call next service module. Its is only known by the Dispatcher.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     * @throws Exception Cant find Next module
     */
    public SvcResponse callNext( SvcRequest req) throws Exception;

    /** Dispatch the execution of a service pipeline by its name.
     * @param pipe Service pipeline name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    public SvcResponse callPipeline( String pipe, SvcRequest req) throws Exception;

    /** Dispatch the execution of a specified service.
     * This method is one that explicit set the next Service to run.
     * @param serviceName Service module name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     * @deprecated Use callPipeline
     */
    public SvcResponse callService( String serviceName, SvcRequest req);   
   
    /** Process a message from an asynchronous service implementation.
     * The AsyncService returned NULL when was called, and now this is the event
     * to notify that there are a message to process.
     * @param msg Request or Response from the module
     * @throws Exception Cant find module to dispatch
     */
    public void onMessage( SvcMessage msg) throws Exception;

} 

