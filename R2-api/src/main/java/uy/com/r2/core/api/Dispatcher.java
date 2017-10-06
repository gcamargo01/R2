/* Dispatcher.java */
package uy.com.r2.core.api;

/** Interface to core services to call and manage execution of service modules instances.
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
     * This method is used by synchronous module implementations (SimpleService)  
     * to call next one in the service pipeline. Its is only known by the Dispatcher.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     * @throws Exception Cant find Next module
     */
    public SvcResponse callNext( SvcRequest req) throws Exception;

    /** Dispatch the execution of a service pipeline by its name.
     * This method is used by services implementations (AsyncService, SimpleService)  
     * that need to set one explicit set the next services pipeline to be executed.
     * Like a routing o balancer service module.
     * @param pipe Service pipeline name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    public SvcResponse callPipeline( String pipe, SvcRequest req) throws Exception;

    /** Process a message from an asynchronous service implementation.
     * After a service returned NULL because there wasn't a message yet,
     * this method is used to signal the event that now there are a message 
     * to process.
     * This method is usually executed by a different thread, started by 
     * current service module to manage asynchronous events.
     * With the SvcMessage.getRequestId() the Dispatcher previously subscribe 
     * each message id, to get the corresponding service pipeline, and to go on 
     * running.
     * @param msg Request or Response from the module
     * @throws Exception Cant find module to dispatch
     */
    public void onMessage( SvcMessage msg) throws Exception;

} 

