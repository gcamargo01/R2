/* NullAsyncService.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;

/** Empty asynchronous service, prototype to create one.
 * A service that doesn't do any thing.
 * @author G.Camargo
 */
public class NullAsyncService implements AsyncService {
    private final static Logger LOG = Logger.getLogger(NullAsyncService.class);
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "TimeRq", ConfigItemDescriptor.INTEGER, 
                "Request time delay in mS", "0"));
        l.add( new ConfigItemDescriptor( "TimeRp", ConfigItemDescriptor.INTEGER, 
                "Response time delay in mS", "0"));
        return l;
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
        // Service implementation test
        if( req.getServiceName().equals( "GiveAnError")) {
            throw new SvcException( SvcResponse.MSG_INVALID_SERVICE
                    + req.getServiceName(), SvcResponse.RES_CODE_INVALID_SERVICE);
        }    
        // Do some thing with data
        int count = 0;
        try {
            count = ( int)req.get( "CountRq");
        } catch( Exception x) { }
        req.put( "CountRq", count + 1);
        // Espera
        if( cfg.getInt( "TimeRq") > 0) {
            Thread.sleep( cfg.getInt( "TimeRq"));
        }
        /**/
        return req;
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
        /*
        // Do some thing with data
        int count = 0;
        try {
            count = ( int)resp.get( "CountRp");
        } catch( Exception x) { }
        resp.put( "CountRp", count + 1);
        // Espera
        if( cfg.getInt( "TimeRp") > 0) {
            Thread.sleep( cfg.getInt( "TimeRp"));
        }
        /**/
        return resp;
    }
    
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> map = new HashMap<String,Object>();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        } 
        // ...
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        // ...
    }

}


