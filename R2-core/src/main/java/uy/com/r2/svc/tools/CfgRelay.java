/* CfgRelay.java */
package uy.com.r2.svc.tools;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.SvcDeployer;
import uy.com.r2.core.api.AsyncService;

/** System configuration rely module. 
 * This module copy all the local configuration to a remote server. <br/>
 * Parameter: RmtPipe
 * @author G.Camargo
 */
public class CfgRelay implements AsyncService {
    public static final String SVC_SYNC_CFG = "SyncConfig";
    private static final Logger LOG = Logger.getLogger(CfgRelay.class);
    private int txNr = 0;
    private long lastSyncTime = 0;
    private String lastDest = null;
        
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        return null;
    }
    
    /** Invocation dispatch phase.
     * It may: <br>
     * (1) create and return a SvcResponse itself, <br>
     * (2) return a SvcRequest to dispatch to the next service module, or <br>
     * (3) return NULL when there aren't next module to call, or <br>
     * (4) throw a Exception to explicit set the module that originates <br>
     * the failure.
     * @param req Invocation message from caller
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        String cmd = req.getServiceName();
        // Not a KEEPAPLIVE a command?
        if( !SVC_SYNC_CFG.equals( req.getServiceName())) {
            return req;  // nothing to do
        }
        lastDest = "" + req.get( "RmtPipe");
        LOG.trace( "to copy cfg. to " + lastDest);
        SvcCatalog catalog = SvcCatalog.getCatalog();
        // Get rmt. module list
        SvcRequest rq = new SvcRequest( null, 0, txNr++, SvcDeployer.SVC_GETMODULELIST, null, 1000);
        SvcResponse rp = SvcCatalog.getDispatcher().callPipeline( lastDest, rq);
        if( rp == null || rp.getResultCode() != 0) {
            throw new Exception( "Failed to get modules list from " + lastDest);
        }
        List<Object> rmtModList = rp.getPayload().get( "Modules");
        // Install new modules
        for( String m: catalog.getModuleNames()) {
            if( !rmtModList.contains( m)) {
                rmtCommand( lastDest, SvcDeployer.SVC_DEPLOYMODULE, m, catalog.getModuleInfo( m).getConfiguration());
            }
        }
        // Update all other confurastion
        for( String m: catalog.getModuleNames()) {
            if( rmtModList.contains( m)) {
                rmtCommand( lastDest, SvcDeployer.SVC_SETMODULECONFIG, m, catalog.getModuleInfo( m).getConfiguration());
            }
        }
        // Remove unused modules
        for( Object m: rmtModList) {
            if( !catalog.getModuleNames().contains( "" + m)) {
                rmtCommand( lastDest, SvcDeployer.SVC_UNDEPLOYMODULE, "" + m, null);
            }
        }
        // return ok
        lastSyncTime = System.currentTimeMillis();
        return new SvcResponse( 0, req);
    }

    /** Process a response phase.
     * The core calls this method to process a response. The module implementation
     * may call Dispatcher.processResponse( SvcResponse) to push the processed 
     * response.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcRequest from next module, or synthetic one  
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        return resp;  // nothing to do
    }

    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String,Object> getStatusVars() {
        Map<String,Object> map = new TreeMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        }
        map.put( "LastSource", lastDest);
        DateFormat df = new SimpleDateFormat( "yyyy/MM/dd HH:mm:ss");
        map.put( "LastSyncTime", df.format( new Date( lastSyncTime)));
        return map;
    }
    
    /** Stop and release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    private void rmtCommand( String dest, String cmd, String mod, Configuration cfg) 
            throws Exception {
        LOG.trace( cmd + " (" + mod + "," + cfg);
        SvcRequest rq;
        rq = new SvcRequest( null, 0, txNr++, cmd, null, 1000);
        rq.put( "Module", mod);
        Map<String,String> cm = cfg.getStringMap( "*"); 
        for( String k: cm.keySet()) {
            rq.put( k, cm.get( k));
        }
        SvcResponse rp = SvcCatalog.getDispatcher().callPipeline( "" + dest, rq);
        if( rp == null || rp.getResultCode() != 0) {
            throw new Exception( "Failed to " + cmd + " " + mod + " with dest: " + dest + " " + rp);
        }
    }
  
}


