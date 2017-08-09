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


/** Direct and simple dispatcher.
 * It uses only one thread to run the service modules. The thread given 
 * by it's caller is used to run the service modules pipeline.
 * Note thar a single thread server may need another implementation with a 
 * thread pool to dispatch multiple request concurrently, in that way he 
 * Catalog has a Configuration item to set the Dispatcher class.
 * @author G.Camargo
 */
public class SimpleDispatcher implements Dispatcher, CoreModule {
    private static final Logger LOG = Logger.getLogger( SimpleDispatcher.class);
    
    // Current running pipeline
    private final Map<String,RunningPipeline> runningPipelines = new ConcurrentHashMap();
    private Map<String,String[]> defPipes = new ConcurrentHashMap();
    private Map<String,String> nodePipes = new ConcurrentHashMap();
    private String defaultServicePipeline[] = new String[ 0];
      
    SimpleDispatcher( ) { }
    
    /** Start running a service call.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    @Override
    public SvcResponse call( SvcRequest req) {
        // Get the defined pipe to use
        String modsToRun[] = defaultServicePipeline;
        String rpn = nodePipes.get( req.getClientNode());
        if( rpn != null) {   // Defined RunningPipe by name
            String[] mtr = defPipes.get(  rpn);
            if( mtr == null) {
                return newExceptionResponse( "RunningPipeline name '" + rpn + "' undefined", req);
            } else {
                modsToRun = mtr;
            }
        }
        // Build the Running pipe
        RunningPipeline rp = new RunningPipeline( modsToRun, req);
        runningPipelines.put( req.getRequestId(), rp);
        // Run to the end
        SvcResponse resp = rp.getFinalResponse();
        runningPipelines.remove( req.getRequestId());
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
        RunningPipeline rp = new RunningPipeline( rpns, req);
        SvcResponse resp = rp.getResponse();
        /**/
        if( LOG.isDebugEnabled()) {
            LOG.debug("callPipeline( " + pipe + ") resp. =  " + resp);
        }
        /**/
        return resp;
    }
    
    /** Dispatch the execution of a specified service.
     * This method is used by modules that explicit set the next Service to run.
     * @param serviceName Service module name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    @Override
    public SvcResponse callService( String serviceName, SvcRequest req) {
        /**/
        if( LOG.isDebugEnabled()) {
            LOG.debug( "callService( " + serviceName + " req. " + req + " )");
        }
        /**/
        // Verify RunningPipeline
        RunningPipeline rp = runningPipelines.get( req.getRequestId());
        if( rp == null) {
            LOG.debug( "Pipeline not found, create one for " + serviceName);
            rp = new RunningPipeline( new String[ 0], req);
            //return newExceptionResponse("Failed callService( " + serviceName 
            //        + "), RunningPipeline not found from " + req, req);
        }
        rp.add( serviceName);
        // Run 
        SvcResponse resp = rp.getResponse();
        /**/
        if( LOG.isDebugEnabled()) {
            LOG.debug("callService( " + serviceName + ") resp. =  " + resp);
        }
        /**/
        return resp;
    }
    
    /** Get the status report.
     * @return Map of status variables
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> map = new TreeMap<String,Object>();
        map.put( "RunningPipelinesCount", runningPipelines.size());
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
    
    /** Startup.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    @Override
    public void startup( Configuration cfg ) throws Exception {
        defaultServicePipeline = cfg.getString( "DefaultServicePipeline").split( ",");
        defPipes = new ConcurrentHashMap();
        Map<String,String> rps = cfg.getStringMap( "Pipeline.*");
        for( String n: rps.keySet()) {
            defPipes.put(  n, rps.get( n).split( ","));
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
                "Default services to dispatch separated by comma (,)", null));
        l.add( new ConfigItemDescriptor( "Pipeline.*", ConfigItemDescriptor.STRING,
                "Defined services to dispatch separated by comma (,) by Pipe-Name, ", null));
        l.add( new ConfigItemDescriptor( "Node.*", ConfigItemDescriptor.STRING,
                "Pipe-Name to use, by Client Node", null));
        return l;        
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    private SvcResponse newExceptionResponse( String msg, SvcRequest req) {
        Exception x = new Exception( msg);
        SvcResponse resp = new SvcResponse( msg, SvcResponse.RES_CODE_EXCEPTION, req);
        LOG.warn( msg, x);
        return resp;
    } 
    
}

