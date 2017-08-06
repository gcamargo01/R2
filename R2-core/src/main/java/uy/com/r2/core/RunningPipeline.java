/* RunningPipeline.java */
package uy.com.r2.core;

import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** A structure to keep the sequence of modules been running.
 * @author Gustavo Camargo
 */
public class RunningPipeline {
    private static final Logger LOG = Logger.getLogger( RunningPipeline.class);
    private final SvcCatalog core = SvcCatalog.getCatalog();
    private final SvcRequest req0;
    private final String toStrPrefix;
    private final Object lock = new Object();
    private String moduleNames[];
    private int index;
    private SvcMessage msg;
    
    /** Create a running catalog.
     * Its a sequence of service to call one by one.
     * @param modules Modules names separated by comma (,)
     * @param req Request to process
     */
    RunningPipeline( String modules[], SvcRequest req) {
        this.moduleNames = modules;
        this.req0 = req;
        this.index = 0;
        this.msg = req;
        StringBuilder sb = new StringBuilder();
        sb.append( req0.getRequestId());
        sb.append( ": ");
        this.toStrPrefix = sb.toString();
        //LOG.trace( "new RunningPipeline( " + req.getRequestId() + " ) " + toString());
    }
    
    /** Running step, process one module a time */
    void run () {
        String moduleName = null;
        try {
            if( index >= moduleNames.length || moduleNames[ index] == null) {
                throw new Exception( "Ended service pipeline " + moduleNames);
            }
            moduleName = moduleNames[ index];
            LOG.trace( "run index=" + index + " " + moduleName + " " + msg);
            ModuleInfo mi = core.getModuleInfo( moduleName);
            if( mi == null) {
                throw new Exception( "Module '" + moduleName + "' not installed"); 
            }
            msg = mi.processMessage( msg);
            if( msg == null) {  // Nothing to do here, wait some msg
                synchronized( lock) {
                    lock.wait();
                }
                return;
            } else if( msg instanceof SvcRequest) {   // Its a request
                ++index;
            } else if( msg instanceof SvcResponse) {  // Its a response
                --index;
            } else {   // Invalid message
                throw new Exception( "Invalid response: " + msg);
            }
        } catch( Exception x) {
            x = new Exception( "" + x + " on " + toString(), x);
            LOG.warn( "" + x, x);
            SvcMessage r = ( msg instanceof SvcRequest)? msg: req0;
            msg = new SvcResponse( "" + x, SvcResponse.RES_CODE_EXCEPTION, x, 
                    (SvcRequest)r);
            --index;
        }
    }
    
    /** Process a message from an asynchronous module.
     * @param msg Request or Response from the module
     */
    void onMessage( SvcMessage msg) {
        synchronized( lock) {
            this.msg = msg;
            lock.notifyAll();
        }
    }
    
    /** Get the next module name to run.
     * @return Module name
     */
    String next() {
        return moduleNames[ ++index];
    }

    /** Put a new one to run.
     * @param moduleName The module to run now
     */
    void add( String moduleName) {
        if( index >= moduleNames.length) {  // Expand the array
           String nmn[] = new String[ index + 10];
           System.arraycopy( moduleNames, 0, nmn, 0, moduleNames.length);
           moduleNames = nmn;
        }
        moduleNames[ index] = moduleName;
    }
    
    /** Blocking method to get the final response.
     * @return SvcResponse
     */
    /** Blocking method to get the response from the current service.
     * @return SvcResponse
     */
    SvcResponse getResponse() {
        int actualIndex = index;
        while( index >= actualIndex) {
            run();
        }
        if( !( msg instanceof SvcResponse )) {
            Exception x = new Exception( "Cast error running " + toString());
            LOG.warn( "run failed: " + toString(), x);
            return new SvcResponse( "Ended running pipe w/o SvcResponse " + toString()
                    , SvcResponse.RES_CODE_EXCEPTION, new Exception( ""), req0);
        }
        return (SvcResponse)msg;
    }
    
    SvcResponse getFinalResponse() {
        while( index >= 0) {
            run();
        }
        if( !( msg instanceof SvcResponse )) {
            Exception x = new Exception( "Cast error running " + toString());
            LOG.warn( "run failed: " + toString(), x);
            return new SvcResponse( "Ended running pipe w/o SvcResponse " + toString()
                    , SvcResponse.RES_CODE_EXCEPTION, new Exception( ""), req0);
        }
        return (SvcResponse)msg;
    }
    
    /** Dump status.
     * @return String
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append( toStrPrefix);
        for( String m: moduleNames) {
            sb.append( m);
            sb.append( ',');
        }
        sb.append( '[');
        sb.append( index);
        sb.append( "] ");
        sb.append( msg.toString());
        return sb.toString();
    }

}
