/* NullService.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SimpleService;
import uy.com.r2.core.api.SvcException;


/** Empty synchronous service, prototype to create one.
 * @author G.Camargo
 */
public class NullService implements SimpleService {
    private static final Logger LOG = Logger.getLogger( NullService.class);
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        //l.add( new ConfigItemDescriptor( "", "", null));
        return l;
    }
    
    /** Service call.
     * @param req Invocation message
     * @param cfg Module configuration 
     * @return SvcResponse message
     * @throws Exception Unexpected error, the responseCode will be lower than 0
     */
    @Override
    public SvcResponse call( SvcRequest req, Configuration cfg) throws Exception {
        /*
        // Test error
        if( !req.getServiceName().equals( "Nothing")) {
            throw new SvcException( SvcResponse.MSG_INVALID_SERVICE
                    + req.getServiceName(), SvcResponse.RES_CODE_INVALID_SERVICE);
        } 
        */
        // Call next service in the pipeline
        SvcResponse resp = SvcCatalog.getDispatcher().callNext( req);
       
        return resp;
    }

    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> m = new HashMap<String,Object>();
        m.put( "Version", "$Revision: 1.1 $");
        // ...
        return m;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

}

