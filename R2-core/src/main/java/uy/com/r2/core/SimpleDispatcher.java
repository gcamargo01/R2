/* SimpleDispatcher.java */
package uy.com.r2.core;

import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.StartableModule;


/** Direct and simple dispatcher.
 * It uses only one thread to run the service modules. The thread given 
 * by it's caller is used to run the service modules pipeline.
 * Note thar a single thread server may need another implementation with a 
 * thread pool to dispatch multiple request concurrently, in that way he 
 * Catalog has a Configuration item to set the Dispatcher class.
 * @author G.Camargo
 */
public class SimpleDispatcher implements Dispatcher, StartableModule {
    private static final Logger LOG = Logger.getLogger( SimpleDispatcher.class);
    
    // Current running pipeline
    private static Exception instanced = null;
    private final Map<String,RunningPipeline> runningPipelines = new ConcurrentHashMap();
    private Map<String,String[]> defPipes = new ConcurrentHashMap();
    private Map<String,String> nodePipes = new ConcurrentHashMap();
    private String defaultServicePipeline[] = new String[ 0];
    private boolean stopped = false;
      
    SimpleDispatcher( ) {
        if( instanced != null) {
            LOG.warn( "Dispatcher re-instaced", new Exception());
            LOG.info( "And first time instaced was", instanced);
        } else {
            instanced = new Exception();
        }
    }
    
    /** Start running a service call, and wait a response.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    @Override
    public SvcResponse call( SvcRequest req) {
        // Get the defined pipe to use
        String modsToRun[] = defaultServicePipeline;
        String rpn = nodePipes.get( req.getClientNode());
        if( rpn != null) {   // Defined RunningPipe by name
            String[] mtr = defPipes.get( rpn);
            if( mtr == null || mtr.length == 0) {
                return newExceptionResponse( "RunningPipeline name '" + rpn + "' undefined", req);
            } else {
                modsToRun = mtr;
            }
        }
        // Build the Running pipe
        RunningPipeline rp = new RunningPipeline( "(Default)", modsToRun, req);
        runningPipelines.put( req.getRequestId(), rp);
        // Run to the end
        //SvcResponse resp = rp.getFinalResponse(); // In the past this method forces to wai to the end
        SvcResponse resp = rp.getResponse();
        runningPipelines.remove( req.getRequestId());
        return resp;
    }
    
    /** Start the execution of a request in asynchronous mode, w/o waiting its response.
     * Some-times it may have a response immediately, otherwise it return null.
     * @param req Request to dispatch
     * @return SvcResponse or null
     */
    @Override
    public SvcResponse process( SvcRequest req) {
        // Get the defined pipe to use
        String modsToRun[] = defaultServicePipeline;
        String rpn = nodePipes.get( req.getClientNode());
        if( rpn != null) {   // Defined RunningPipe by name
            String[] mtr = defPipes.get( rpn);
            if( mtr == null || mtr.length == 0) {
                return newExceptionResponse( "RunningPipeline name '" + rpn + "' undefined", req);
            } else {
                modsToRun = mtr;
            }
        }
        // Build the Running pipe
        RunningPipeline rp = new RunningPipeline( "(Default)", modsToRun, req);
        runningPipelines.put( req.getRequestId(), rp);
        // Try to run 
        SvcResponse resp = rp.getResponse();
        //runningPipelines.remove( req.getRequestId());
        return resp;
    }
    
    /** Dispatch the next module service call.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     * @throws Exception Cant find Next 
     */
    @Override
    public SvcResponse callNext( SvcRequest req) throws Exception {
        if( req == null) {
            throw new Exception( "callNext with Null request", new NullPointerException());
        }
        RunningPipeline rp = runningPipelines.get( req.getRequestId());
        if( rp == null) {
            return newExceptionResponse( "callNext( " + req.getRequestId() + ") can't find RunningPipeline", req);
        }
        String nm = rp.next();
        /**/
        if( LOG.isDebugEnabled()) {
            LOG.debug( "callNext " + nm + "(" +  req + " )");
        }
        /**/
        // Run 
        SvcResponse resp = rp.getResponse();
        /**/
        if( LOG.isDebugEnabled()) {
            LOG.debug( "callNext " + nm + " resp. =  " + resp);
        }
        /**/
        return resp;
    }
    
    /** Dispatch the execution of a service pipeline by its name.
     * @param pipe Service pipeline name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    @Override
    public SvcResponse callPipeline( String pipe, SvcRequest req) {
        /**/
        if( LOG.isDebugEnabled()) {
            LOG.debug( "callPipeline( " + pipe + " req. " + req + " )");
        }
        /**/
        // Search Pipeline
        String rpns[] = defPipes.get( pipe);
        if( rpns == null) {
            return newExceptionResponse( "Can't find pipeline name '" + pipe + "' to request " + req, req);
        }
        // Run 
        RunningPipeline rp = new RunningPipeline( pipe, rpns, req);
        SvcResponse resp = rp.getResponse();
        /**/
        if( LOG.isDebugEnabled()) {
            LOG.debug("callPipeline( " + pipe + ") resp. =  " + resp);
        }
        /**/
        return resp;
    }
    
    /** Get the status report.
     * @return Map of status variables
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> map = new TreeMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        } 
        map.put( "RunningPipelinesCount", runningPipelines.size());
        map.put( "Stopped", stopped);
        return map;
    } 
    
    /** Process a response from an asynchronous module.
     * @param msg Response or Request from the module
     * @throws Exception Can't find RunningPipeline
     */
    @Override
    public void onMessage( SvcMessage msg) throws Exception {
        LOG.debug( "onMessage " + msg);
        RunningPipeline rp = runningPipelines.get( msg.getRequestId());
        if( rp == null) {
            Exception x = new Exception( "Can't find " + msg.getRequestId() 
                    + " to dispatch onMessage " + msg);
            LOG.debug( x.getMessage(), x);
            throw x;
        } else {
            rp.onMessage( msg);
        }
    }
    
    /** Start up.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    @Override
    public void start( Configuration cfg) throws Exception {
        defaultServicePipeline = cfg.getString( "DefaultServicePipeline").split( ",");
        LOG.debug( "DefaultServicePipeline " + cfg.getString( "DefaultServicePipeline"));
        defPipes = new ConcurrentHashMap();
        Map<String,String> rps = cfg.getStringMap( "Pipeline.*");
        for( String n: rps.keySet()) {
            defPipes.put(  n, rps.get( n).split( ","));
            LOG.debug( "Pipeline " + n + "=" + rps.get( n));
        }
        nodePipes = cfg.getStringMap( "Node.*");
    }
    
    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "DefaultServicePipeline", ConfigItemDescriptor.STRING,
                "Default list of services to dispatch separated by comma (,)"));
        l.add( new ConfigItemDescriptor( "Pipeline.*", ConfigItemDescriptor.STRING,
                "List of services to dispatch by Pipeline name; separated by comma (,) "));
        l.add( new ConfigItemDescriptor( "Node.*", ConfigItemDescriptor.STRING,
                "Pipelinee name to use, by Client Node"));
        return l;        
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        LOG.debug( "shutdown");
        stopped = true;
        for( String k: runningPipelines.keySet()) {
            runningPipelines.get(  k).stop();
        }
    }

    private SvcResponse newExceptionResponse( String msg, SvcRequest req) {
        Exception x = new Exception( msg);
        SvcResponse resp = new SvcResponse( msg, SvcResponse.RES_CODE_EXCEPTION, req);
        LOG.warn( msg, x);
        return resp;
    } 
    
}

