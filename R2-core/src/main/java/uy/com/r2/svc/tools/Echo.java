/* Echo.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SimpleService;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;


/** Test module.
 * @author G.Camargo
 */
public class Echo implements SimpleService {
    private static final Logger LOG = Logger.getLogger( Echo.class);
    private int time = 1000;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<>();
        l.add( new ConfigItemDescriptor( "Time", ConfigItemDescriptor.INTEGER,
                "Max value of average time to wait, in mS", null));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        time = cfg.getInt( "Time");
    }

    /** Service call.
     * @param req Invocation message
     * @return SvcResponse message
     * @throws Exception Unexpected error, the responseCode will be lower than 0
     */
    @Override
    public SvcResponse call( SvcRequest req, Configuration cfg) throws Exception {
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


