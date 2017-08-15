/* Router.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;


/** A special service module to control which service is called next.
 * It is based on a service-module map. If no one is found, the default action
 * is run the next defined by the Dispatcher pipeline.
 * @author G.Camargo
 */
public class Router implements AsyncService {
    private static final Logger log = Logger.getLogger( Router.class);
    private HashMap<String,PipeInfo> defRoutes = new HashMap();
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Service.*", ConfigItemDescriptor.MODULE,
                "Service and pipeline name to invoke", null));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        if( cfg.isChanged()) {
            return;
        }
        defRoutes = new HashMap();
        Map<String,String> sm = cfg.getStringMap( "Service.*");
        for( String k: sm.keySet()) {
            String sn = k.substring( 8); 
            String pn = sm.get( k);
            log.debug( "Service " + sn + " = " + pn);
            PipeInfo p = new PipeInfo( sn, pn);
            defRoutes.put( sn, p);
            // Verifica si esta instanciado
            //if( SvcCatalog.getCatalog().getDispatcher().isValidPipe( pn) == null) {
            //    log.warn( "Defined route not found " + pn + " for node " + sn);
            //}
        }
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
        setConfiguration( cfg);
        PipeInfo ds = defRoutes.get( req.getServiceName());
        SvcResponse resp;
        if( ds == null) {
            return req;  // Lets go on
        } else {
            ++ds.uses;
            resp = SvcCatalog.getDispatcher().callPipeline( ds.pipeName, req);
            if( resp.getResultCode() < 0) {
                ++ds.errors;
            }
        }    
        return resp;
    }

    /** Process a response phase.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcRequest from next module, or synthesized one  
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
        HashMap<String, Object> map = new HashMap<String, Object>();
        for( String s: defRoutes.keySet()) {
            defRoutes.get( s).addStatus( map);
        }
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    class PipeInfo {
        final String service;
        final String pipeName;
        int uses = 0;
        int errors = 0;

        private PipeInfo( String s, String nm) {
            this.service = s;
            this.pipeName = nm;
        }

        private void addStatus( Map<String,Object> map) {
            map.put( "Pipe." + service + ".Uses", uses); 
            map.put( "Pipe." + service + ".Errors", errors);
        }
    }

}

