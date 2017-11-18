/* RunningPipeline.java */
package uy.com.r2.core;

import org.apache.log4j.Logger;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** A structure to keep a sequence of modules running.
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
    private boolean stop = false;
    
    /** Create a running catalog.
     * It receives the list of service names to call one by one.
     * @param modules Modules name array
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
        /*
        if( LOG.isTraceEnabled()) {
            LOG.trace( "new RunningPipeline( " + req.getRequestId() + " ) " + toString() + " " + modules[ 0]);
        }
        */
    }
    
    /** Run one module a time */
    private void runStep () {
        String moduleName = null;
        try {
            if( index >= moduleNames.length || moduleNames[ index] == null) {
                String s = req0.getServiceName();
                if( s.equals( Dispatcher.SVC_GETSERVICESLIST)) {
                    msg = new SvcResponse( "", 0, req0);
                   --index;
                   return;
                }
                throw new Exception( "Service '" + s + "' not implemented in pipeline");
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
    
    /** Get the next module name to runStep.
     * @return Name of the module to be executed next
     */
    String next() {
        return moduleNames[ ++index];
    }

    /** Add a new module to runStep.
     * @param moduleName The module to runStep now
     * @deprecated Its only needed by the deprecated method callService()
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
        while( index >= actualIndex && !stop) {
            runStep();
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
        while( index >= 0 && !stop) {
            runStep();
        }
        if( !( msg instanceof SvcResponse )) {
            Exception x = new Exception( "Cast error running " + toString());
            LOG.warn( "run failed: " + toString(), x);
            return new SvcResponse( "Ended running pipe w/o SvcResponse " + toString()
                    , SvcResponse.RES_CODE_EXCEPTION, new Exception( ""), req0);
        }
        return (SvcResponse)msg;
    }
    
    void stop() {
        stop = true;
        LOG.debug( "stopping " + toString());
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
