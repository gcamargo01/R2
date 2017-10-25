/* Echo.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;


/** Test module.
 * @author G.Camargo
 */
public class Echo implements AsyncService {
    private static final Logger LOG = Logger.getLogger( Echo.class);
    private int time = 1000;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<ConfigItemDescriptor>();
        l.add( new ConfigItemDescriptor( "Time", ConfigItemDescriptor.INTEGER,
                "Max value of average time to wait, in mS", null));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        time = cfg.getInt( "Time");
    }

    /** Invocation dispatch phase.
     * It may: <br>
     * (1) create and return a SvcResponse itself, <br>
     * (2) return a SvcRequest to dispatch to the next service module, or <br>
     * (3) return NULL when there aren't next module to call, or <br>
     * (4) throw a Exception to explicit set the module that originates <br>
     * the failure.
     * @param req Invocation message from caller
     * @param cfg Module configuration
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        /*
        if( LOG.isTraceEnabled()) {
            LOG.trace( req.toString());
        }
        /**/
        setConfiguration( cfg);
        if( time > 0) {
            int t = new Random().nextInt( time);
            Thread.sleep( t);
            req.add( "t", t);
        } 
        /**/
        SvcResponse resp = new SvcResponse( req.getPayload(), 0, req);
        /*
        if( LOG.isTraceEnabled()) {
            LOG.trace( resp.toString());
        }
        /**/
        return resp;
    }

    /** Process a response phase.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcResponse message from next module, or null (no next)
     * @param cfg Module configuration
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        return resp;
    }
    
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        return null;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

}


