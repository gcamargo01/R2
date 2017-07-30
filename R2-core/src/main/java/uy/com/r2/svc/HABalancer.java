/* HABalancer.java */
package uy.com.r2.svc;

import java.util.List;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.LinkedList;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;


/** High availability round-robin load balancer module.
 * It performs a round robin between equal state modules, being discarded 
 * bad response times (&gt;RespTimeThresold) and modules with fatal errors (rc&lt;0).
 * Modules in failure status are skipped many times (ErrorFactor), but 
 * then once to try to detect if it works.
 * The module selection is done minimizing the "weight" formula: <br>
 * &nbsp; timesUsed * UsesFactor + errors * ErrorFactor + SvcResponseTime * RespTimeFactor
 * @author G.Camargo
 */
public class HABalancer implements AsyncService {
    private static final Logger log = Logger.getLogger( HABalancer.class);
    private final Object lock = new Object(); 
    private ModuleStats[] modules = new ModuleStats[ 0];
    private HashMap<String,ModuleStats> modsInUse = new HashMap<String,ModuleStats>();
    private String testMsg = null;
    // Statistics
    private float usesFactor = 1F;
    private float errorFactor = 10F;
    private float respTimeFactor = 0.01F;
    private int respTimeThresold = 5000;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<ConfigItemDescriptor>();
        l.add( new ConfigItemDescriptor( "Modules", ConfigItemDescriptor.MODULE, 
                "Modules names to balance, comma separated", ""));
        l.add( new ConfigItemDescriptor( "UsesFactor", ConfigItemDescriptor.INTEGER,
                "Usage weighting factor", "1"));
        l.add( new ConfigItemDescriptor( "ErrorFactor", ConfigItemDescriptor.INTEGER,
                "Error weighting factor", "10"));
        l.add( new ConfigItemDescriptor( "RespTimeFactor", ConfigItemDescriptor.INTEGER,
                "Response time weighting factor", "0.01"));
        l.add( new ConfigItemDescriptor( "RespTimeThresold", ConfigItemDescriptor.INTEGER,
                "Respone time in mS where is trated as an error", "5000"));
        l.add( new ConfigItemDescriptor( "TestMessage", ConfigItemDescriptor.STRING,
                "Invocation to verify status", null));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isChanged()) {
            return;
        }
        if( !cfg.containsKey( "Modules")) {
            log.warn( "No modules to balance in cfg.");
            return;
        }
        String mn[] = cfg.getString( "Modules").split( ",");
        modules = new ModuleStats[ mn.length];
        for( int i = 0; i < mn.length; ++i) {
            modules[ i] = new ModuleStats( mn[ i]);
        }
        usesFactor = (float)cfg.getDouble( "UsesFactor");
        errorFactor = (float)cfg.getDouble( "ErrorFactor");
        respTimeFactor = (float)cfg.getDouble( "RespTimeFactor");
        respTimeThresold = cfg.getInt( "RespTimeThresold");
        testMsg = cfg.getString( "TestMessage");
    }

    /** Process a service call.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param req Invocation message from caller
     * @param cfg Module configuration
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        if( modules.length == 0){
            SvcResponse r = new SvcResponse( "No modules to Balance", -1, null, req);
            log.warn( r.toString());
            return r;
        }
        // Get the better module to be used: the lower weight
        ModuleStats ms = modules[ 0];
        synchronized( lock) {
            for( int i = 1; i < modules.length; ++i) {  // order-N search
                ModuleStats mi = modules[ i];
                if( mi.compareTo( ms) < 0) {  // revert (buble search)
                    modules[ i] = ms;
                    modules[ 0] = mi;
                    ms = mi;
                }
            }
            ++ms.timesUsed;
            modsInUse.put( req.getRequestId(), ms);
        }
        log.trace( "selected weight=" + ms.weight + " " + ms.moduleName);
        // Call this module
        cfg.put( "Next", ms.moduleName);
        return req;
    }

    /** Process a response.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param res SvcResponse message from next module
     * @param cfg Module configuration
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse res, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        String msgId = res.getRequest().getRequestId();
        ModuleStats ms;
        synchronized( lock) {
            ms = modsInUse.get( msgId);
            modsInUse.remove( msgId);
            // Update stats
            ms.responseTime = res.getResponseTime();
            if( res.getResultCode() < 0 || res.getResponseTime() > respTimeThresold) {
                ++ms.errors;
            }
            ms.weight = ms.timesUsed * usesFactor + ms.errors * errorFactor + 
                    ms.responseTime * respTimeFactor;
        }
        log.trace( "result weight=" + ms.weight);
        return res;
    }
    
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> m = new TreeMap();
        m.put( "Version", "$Revision: 1.1 $");
        m.put( "InUseCount", modsInUse.size());
        m.put( "InUse", modsInUse);
        for( int i = 0; i < modules.length; ++i) {
            ModuleStats ms = modules[ i];
            m.put( "Module_" + i + "_Module", ms.moduleName);
            m.put( "Module_" + i + "_TimesUsed", ms.timesUsed);
            m.put( "Module_" + i + "_Errors", ms.errors);
            m.put( "Module_" + i + "_ResponseTime", ms.responseTime);
            m.put( "Module_" + i + "_Weight", ms.weight);
        }
        return m;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    private class ModuleStats implements Comparable<ModuleStats> {
        ModuleStats( String m) { moduleName = m;}
        String moduleName;
        int timesUsed = 0;
        int errors = 0;
        int responseTime = 0;
        float weight = 0;

        @Override
        public int compareTo( ModuleStats o) {
            return Float.compare( weight, o.weight);
        }
        
        @Override
        public String toString() {
            return moduleName;
        }
    }

}


