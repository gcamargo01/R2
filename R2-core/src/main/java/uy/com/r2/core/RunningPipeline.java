/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
    private final String reqAndModules;
    private String moduleNames[];
    private int index;
    private SvcMessage msg;
    
    /** Create a running catalog.
     * Its a sequence of service to call one by one.
     * @param modules Modules names separated by comma (,)
     * @param req Request to process
     */
    RunningPipeline( String modules[], SvcRequest req) {
        //this.msgId = req.getRequestId();
        this.moduleNames = modules;
        this.req0 = req;
        this.index = 0;
        this.msg = req;
        StringBuilder sb = new StringBuilder();
        sb.append( req0.getRequestId());
        sb.append( ": ");
        for( String m: moduleNames) {
            sb.append( m);
            sb.append( ',');
        }
        sb.append( '[');
        this.reqAndModules = sb.toString();
        //LOG.debug( "new RunningPipeline( " + req.getRequestId() + " ) " + toString());
    }
    
    /** Running */
    void run () {
        if( LOG.isDebugEnabled()) {
            LOG.debug("run --- " + toString());
        }
        try {
            while( index >= 0) {
                if( index >= moduleNames.length) {
                    index = moduleNames.length - 1;
                    throw new Exception( "No next module on " + toString());
                }
                String moduleName = moduleNames[ index];
                ModuleInfo mi = core.getModuleInfo( moduleName);
                if( mi == null) {
                    throw new Exception( "Module '" + moduleName + "' not installed on " 
                            + toString());
                }
                msg = mi.processMessage( msg);
                if( msg == null) { 
                    return;
                } else if( msg instanceof SvcRequest) {   // Its a request
                    ++index;
                } else if( msg instanceof SvcResponse) {  // Its a response
                    --index;
                } else {   // Invalid message
                    throw new Exception( "Invalid response " + msg + " on " 
                            + toString());
                }
            }
        } catch( Exception x) {
            LOG.warn( "run failed", x);
            SvcMessage r = ( msg instanceof SvcRequest)? msg: req0;
            msg = new SvcResponse( "" + x,
                    SvcResponse.RES_CODE_EXCEPTION, x, (SvcRequest)r);
        }
    }
    
    /** Process a message from an asynchronous module.
     * @param msg Request or Response from the module
     */
    void onMessage( SvcMessage msg) {
        this.msg = msg;
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
        if( index >= moduleName.length()) {  // Expand the array
           String nmn[] = new String[ index + 10];
           System.arraycopy( moduleNames, 0, nmn, 0, moduleNames.length);
           moduleNames = nmn;
        }
        moduleNames[ ++index] = moduleName;
    }
    
    /** Blocking method to get the final response.
     * @return SvcResponse
     */
    SvcResponse getFinalResponse() {
        while( index >= 0) {
            run();
            if( index >= 0) {
                try {
                    Thread.sleep( 10);
                } catch ( InterruptedException ex ) { }
            }
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
        sb.append( reqAndModules);
        sb.append( index);
        sb.append( "] ");
        sb.append( msg.toString());
        return sb.toString();
    }

}
