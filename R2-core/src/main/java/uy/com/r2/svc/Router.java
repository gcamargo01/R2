/* Router.java */
package uy.com.r2.svc;

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


/** This module determines which node invoke next.
 * It is based on a service-module map. the default module is the "Next" one.
 * @author G.Camargo
 */
public class Router implements AsyncService {
    private static final Logger log = Logger.getLogger( Router.class);
    private HashMap<String,ServiceInfo> servMods = new HashMap();
    private HashMap<String,String> nextSvcs = null;
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Service.*", ConfigItemDescriptor.MODULE,
                "Service an route to invoke", null));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        if( cfg.isChanged()) {
            return;
        }
        servMods = new HashMap();
        Map<String,String> sm = cfg.getStringMap( "Service.*");
        for( String k: sm.keySet()) {
            String sn = k.substring( 8); 
            log.debug( "Service " + sn + " = " + sm.get( k));
            ServiceInfo dm = new ServiceInfo( sn, sm.get( k));
            servMods.put( sn, dm);
            // Verifica si esta instanciado
            if( SvcCatalog.getCatalog().getModuleInfo( cfg.getString( sn)) == null) {
                log.warn( "Service routed not installed " + sn);
            }
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
        ServiceInfo ds = servMods.get( req.getServiceName());
        SvcResponse resp;
        if( ds == null) {
            resp = SvcCatalog.getDispatcher().callService( nextSvcs.get( ""), req);
        } else {
            ++ds.uses;
            resp = SvcCatalog.getDispatcher().callService( nextSvcs.get( ds.moduleName), req);
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
        for( String s: servMods.keySet()) {
            servMods.get( s).addStatus( map);
        }
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    class ServiceInfo {
        final String service;
        final String moduleName;
        int uses = 0;
        int errors = 0;

        private ServiceInfo( String s, String nm) {
            this.service = s;
            this.moduleName = nm;
        }

        private void addStatus( Map<String,Object> map) {
            map.put( "Service." + service + ".Uses", uses); 
            map.put( "Service." + service + ".Errors", errors);
        }
    }

}

