/* Dispatcher.java */
package uy.com.r2.core.api;

/** Interface to call and manage execution of some service modules intances.
 * @author G.Camargo
 */
public interface Dispatcher extends Module {
    
    /** Dispatch the execution of a specified service.
     * This method is used by modules that explicit set the next Service to run.
     * @param moduleName Service module name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    public SvcResponse callService( String moduleName, SvcRequest req);   
    
    /** Dispatch the execution of the next service.
     * Call next service module. Its is only known by the Dispatcher.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     * @throws Exception Cant find Next module
     */
    public SvcResponse callNext( SvcRequest req)
            throws Exception;

    /** Process a message from an asynchronous module.
     * @param msg Request or Response from the module
     */
    public void onMessage( SvcMessage msg);
    
} 

