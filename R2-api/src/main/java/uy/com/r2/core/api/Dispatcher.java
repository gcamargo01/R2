/* Dispatcher.java */
package uy.com.r2.core.api;

/** Interface to call and manage execution of some service modules intances.
 * @author G.Camargo
 */
public interface Dispatcher extends Module {
    
    /** Start the execution of a request.
     * This method is used by end-point implementation, for example a remote client.
     * Or some module is starting a new execution.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    public SvcResponse call( SvcRequest req);   

    /** Dispatch the execution of a specified service.
     * This method is used by modules that explicit set the next Service to run.
     * @param serviceName Service module name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    public SvcResponse callService( String serviceName, SvcRequest req);   
    
    /** Dispatch the execution of the next service.
     * Call next service module. Its is only known by the Dispatcher.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     * @throws Exception Cant find Next module
     */
    public SvcResponse callNext( SvcRequest req) throws Exception;

    /** Process a message from an asynchronous service implementation.
     * The AsyncService returned NULL when was called, and now this is the event
     * to notify that there are a message to process.
     * @param msg Request or Response from the module
     * @throws Exception Cant find module to dispatch
     */
    public void onMessage( SvcMessage msg) throws Exception;

} 

