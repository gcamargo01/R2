/* FilePathSynchronizer.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.CoreModule;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** Synchronize a Path in local file system from remote file system.
 * @author G.Camargo
 */
public class FilePathSynchronizer implements CoreModule {
    private final static int TIME_OUT = 10000;
    private final static Logger log = Logger.getLogger( FilePathSynchronizer.class);
    private final static String RMT = "_Undefined_";
    private static int txNr = 0;
    private boolean stop = false;
    private int interval = 1000;
    private Worker wrk = null;
    private Map<String,String> pathMap = null;
    private int bufferSize = 10240;
    private String remote = null;
    private int errorCount = 0;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Path.*", ConfigItemDescriptor.STRING, 
                "Path to synchronize, it may have few", null));
        l.add( new ConfigItemDescriptor( "Interval", ConfigItemDescriptor.INTEGER, 
                "Interval to synchronize", "5000"));
        l.add( new ConfigItemDescriptor( "RemoteServer", ConfigItemDescriptor.STRING, 
                "Remote server name to synchronize from", null));
        return l;
    }
    
    @Override
    public void startup( Configuration cfg) throws Exception {
        interval = cfg.getInt( "Interval", 5000);
        remote = cfg.getString( "RemoteServer");
        pathMap = cfg.getStringMap( "Path.*");
        // resest statistics
        stop = false;
        wrk = new Worker();
        wrk.start();
    }
    
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> map = new HashMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        } 
        if( wrk != null) {
            map.putAll( wrk.getStatusVars());
        } else {
            map.put( "Running", false);
        }    
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        stop = true;
        if( wrk != null) {
            wrk.interrupt();
        }
        wrk = null;
    }
    
    class Worker extends Thread {
        
        private HashMap<String,Long> namesAndLen = new HashMap();
        private String path = null;

        @Override
        public void run() {
            while( !stop) {
                for( String k: pathMap.keySet()) {
                    try {
                        path = pathMap.get( k);
                        // Remote List directory reQuest/resPonse
                        SvcRequest rlq = new SvcRequest( null, ++txNr, 0, "FileList", null, TIME_OUT);
                        rlq.add( "Path", path);
                        SvcResponse rlp = SvcCatalog.getDispatcher().callPipeline( RMT, rlq);
                        // List Local directory reQuest/resPonse
                        SvcRequest llq = new SvcRequest( null, ++txNr, 0, "FileList", null, TIME_OUT);
                        llq.add( "Path", path);
                        SvcResponse llp = SvcCatalog.getDispatcher().call( llq);
                        // Compare 
                        log.trace( "to compare l " + llq + " r " + rlq);
                        for( String fn: rlp.getPayload().keySet()) {
                            // Get Remote MD5
                            SvcRequest rmr = new SvcRequest( null, ++txNr, 0, "GetChkSum", null, TIME_OUT);
                            rmr.add( "Path", path);
                            rmr.put( "Name", fn);
                            SvcResponse rmq = SvcCatalog.getDispatcher().call( rmr);
                            if( rmq.getResultCode() != 0) {
                                break;  // cant read file
                            }
                            // Get Local MD5
                            SvcRequest lmr = new SvcRequest( null, ++txNr, 0, "GetChkSum", null, TIME_OUT);
                            lmr.add( "Path", path);
                            lmr.put( "Name", fn);
                            SvcResponse lmq = SvcCatalog.getDispatcher().call( lmr);
                            // If cant read dest, or not equal, add to copy
                            Map<String,Object> m = ( Map)rlp.get( fn);
                            //if( lmq.getResultCode() != 0 || !lmq.get( "ChkSum").equals( rmq.get(  "ChkSum"))) {
                                namesAndLen.put( fn, ( long)Double.parseDouble( "" + m.get( "Length")));
                            //}
                        }                   
                        // Copy each one
                        log.trace( "to copy " + namesAndLen);
                        for( String name: namesAndLen.keySet()) {
                            for( int pos = 0; ; pos += bufferSize) {
                                // Read Remote reQest/resPponse
                                SvcRequest rrq = new SvcRequest( null, ++txNr, 0, "FileRead", null, TIME_OUT);
                                rrq.put( "Path", path);
                                rrq.put( "Name", name);
                                rrq.put( "Offset", pos);
                                rrq.put( "Lentgh", bufferSize);
                                rrq.put( "Length", "" + namesAndLen.get(name));
                                SvcResponse rdp = SvcCatalog.getDispatcher().callPipeline( RMT, rrq);
                                long len = ( "" + rdp.get( "Block")).length() / 2;
                                if( len == 0) {
                                    break;
                                }
                                // Write Local reQest/resPponse
                                SvcRequest wlq = new SvcRequest( null, ++txNr, 0, "FileWrite", null, TIME_OUT);
                                wlq.put( "Path", path);
                                wlq.put( "Name", name + "_OUT");
                                rrq.put( "Offset", pos);
                                wlq.put( "Block", rdp.get( "Block"));
                                SvcResponse rwp = SvcCatalog.getDispatcher().call( wlq);
                                log.trace( "copied " + name + " pos = " + pos + " rc=" + rwp.getResultCode());
                            }
                        }
                        namesAndLen.clear();
                        path = null;
                        log.trace( "end by now");
                    } catch( Exception ex) {
                        ++errorCount;
                        log.warn( "Failed to synchronize", ex);
                    }
                }    
                try {
                    sleep( interval);
                } catch( InterruptedException ex) { }
            }
        }

        private Map<String, Object> getStatusVars() {
            Map<String,Object> m = new HashMap();
            m.put( "Running", !namesAndLen.isEmpty());
            m.put( "RunningPath", path);
            m.put( "FilesToCopy", namesAndLen.size());
            m.put( "ErrorCount", errorCount);
            return m;
        }
        
    }

}


