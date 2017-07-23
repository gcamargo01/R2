/* SimpleDispatcher.java */
package uy.com.r2.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;


/** Direct and simple dispatcher.
 * This is a minimal dispatcher, it uses the same thread and blocks it.
 * @author G.Camargo
 */
public class SimpleDispatcher implements Dispatcher, CoreModule {
    private static final Logger LOG = Logger.getLogger( SimpleDispatcher.class);
    
    private static final SvcCatalog kernel = SvcCatalog.getCatalog();
    private final ConcurrentHashMap<String,RunningPipeline> runPipeMap = new ConcurrentHashMap();
    private String defaultServicePipeline[] = "Null9,Null8,Null7,Null6,Null5,Null4,Null3,Null2,Null1,Null0,Loop".split( ","); // !!!!
            //= "";
    private Map<String,String> servicePipelinesMap = new HashMap();
    private int onMessageFailedCount = 0;
      
    SimpleDispatcher( ) { }
    
    /** Start running a service call.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    public SvcResponse call( SvcRequest req) {
        // Build the Running pipe
        String modsToRun[];
        if( servicePipelinesMap.containsKey( req.getClientNode())) {
            modsToRun = servicePipelinesMap.get( req.getClientNode()).split( ",");           
        } else {
            modsToRun = defaultServicePipeline;
        }
        RunningPipeline rp = new RunningPipeline( modsToRun, req);
        runPipeMap.put( req.getRequestId(), rp);
        // Run to the end
        return rp.getFinalResponse();
    }
    
    /** Dispatch the execution of a specified service.
     * This method is used by modules that explicit set the next Service to run.
     * @param moduleName Service module name
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     */
    @Override
    public SvcResponse callService( String moduleName, SvcRequest req) {
        if( LOG.isDebugEnabled()) {
            LOG.debug("callService( " + moduleName + " req. " + req + " )");
        }
        // Verify RunningPipeline
        RunningPipeline rp = runPipeMap.get( req.getRequestId());
        if( rp == null) {  
            LOG.warn( "May be an error, not runningPipeline found " + req);
            return call( req);
        }
        rp.add( moduleName);
        // Run to the end
        return rp.getFinalResponse();
    }
    
    /** Dispatch the next module service call.
     * @param req Request to dispatch
     * @return SvcResponse or error packed as a response 
     * @throws Exception Cant find Next 
     */
    @Override
    public SvcResponse callNext( SvcRequest req) throws Exception {
        if( LOG.isDebugEnabled()) {
            LOG.debug("callNext( req. " + req + " )");
        }
        if( req == null) {
            throw new Exception( "callNext with Null request", new NullPointerException());
        }
        RunningPipeline rp = runPipeMap.get( req.getRequestId());
        if( rp == null) {
            throw new Exception( "callNext( " + req.getRequestId() + ") can't find RunningPipeline");
        }
        rp.next();
        // Run to the end
        return rp.getFinalResponse();
    }
    
    /** Get the status report.
     * @return Map of status variables
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> map = new TreeMap<String,Object>();
        map.put( "Version", "$Revision: 1.1 $");
        map.put( "OnMessageFailedCount", "" + onMessageFailedCount);
        Set<String> s = kernel.getModuleNames();
        s.remove( SvcCatalog.DISPATCHER_NAME); // Avoid Loop, it is the Dispatcher
        for( String m: kernel.getModuleNames()) {
            Map<String,Object> sv = kernel.getModuleInfo( m).getStatusVars();
            for( String ks: sv.keySet()) {
                map.put( m + "." + ks, sv.get( ks));
            }
        }
        return map;
    } 
    
    /** Process a response from an asynchronous module.
     * @param msg Response or Request from the module
     */
    @Override
    public void onMessage( SvcMessage msg) {
        LOG.debug( "onMessage " + msg);
        RunningPipeline rp = runPipeMap.get( msg.getRequestId());
        if( rp == null) {
            LOG.debug( "Can't find " + msg.getRequestId() + " on msg " + msg);
            ++onMessageFailedCount;
        } else {
            rp.onMessage( msg);
        }
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
                "Services to dispatcher separated by comma (,)", null));
        l.add( new ConfigItemDescriptor( "ServicePipeline.*", ConfigItemDescriptor.STRING,
                "Services to dispatcher separated by comma (,)", null));
        return l;        
    }

    /** Startup.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    @Override
    public void startup( Configuration cfg ) throws Exception {
        defaultServicePipeline = cfg.getString( "DefaultServicePipeline").split( ",");
        servicePipelinesMap = cfg.getStringMap( "ServicePipeline.*");
        if( cfg.isChanged()) {
            onMessageFailedCount = 0;
        }
    }
    
    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

}

