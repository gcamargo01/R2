/* FilePathSynchronizer.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.StartableModule;

/** Synchronize a Path in local file system from remote file system.
 * @author G.Camargo
 */
public class FilePathSynchronizer implements StartableModule {
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
    
    /** Configure. */
    @Override
    public void start( Configuration cfg) throws Exception {
        interval = cfg.getInt( "Interval");
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
                try {
                    sleep( interval);
                } catch( InterruptedException ex) { }
                for( String k: pathMap.keySet()) {
                    syncPathRecursive( pathMap.get( k));
                }    
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
        
        private void syncPathRecursive( String path) {
            log.trace( "re-stary sync now ++++++++++ ");
            syncPath( path);
            SvcResponse rlp = null;
            try {
                // Remote List directory reQuest/resPonse
                SvcRequest rlq = new SvcRequest( null, ++txNr, 0, "ListDirs", null, TIME_OUT);
                rlq.add( "Path", path);
                rlp = SvcCatalog.getDispatcher().callPipeline( RMT, rlq);
                for( String d: rlp.getPayload().keySet()) {
                    syncPath( ( (Map<String,String>)rlp.get( d)).get( "Path") );
                }
            } catch( Exception ex) {
                ++errorCount;
                log.warn( "Failed to synchronize path " + rlp, ex);
            }
            log.trace( "end by now --------------");
        }
        
        private void syncPath( String path) {
            try {
                // Remote List directory reQuest/resPonse
                SvcRequest rlq = new SvcRequest( null, ++txNr, 0, "ListFiles", null, TIME_OUT);
                rlq.add( "Path", path);
                SvcResponse rlp = SvcCatalog.getDispatcher().callPipeline( RMT, rlq);
                // List Local directory reQuest/resPonse
                SvcRequest llq = new SvcRequest( null, ++txNr, 0, "ListFiles", null, TIME_OUT);
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
                    if( lmq.getResultCode() != 0 || !lmq.get( "ChkSum").equals( rmq.get(  "ChkSum"))) {
                        namesAndLen.put( fn, ( long)Double.parseDouble( "" + m.get( "Length")));
                    }
                }                   
                // Copy each one
                log.trace( "to copy " + namesAndLen);
                for( String name: namesAndLen.keySet()) {
                    for( int pos = 0; ; pos += bufferSize) {  // Block by block
                        // Read Remote reQest/resPonse
                        SvcRequest rrq = new SvcRequest( null, ++txNr, 0, "ReadFile", null, TIME_OUT);
                        rrq.put( "Path", path);
                        rrq.put( "Name", name);
                        rrq.put( "Offset", pos);
                        rrq.put( "Lentgh", bufferSize);
                        rrq.put( "Length", "" + namesAndLen.get(name));
                        SvcResponse rdp = SvcCatalog.getDispatcher().callPipeline( RMT, rrq);
                        if( rdp.getResultCode() != 0) {
                            log.warn( "Read failed " + path + " " + name + " " + rdp);
                            break;
                        }
                        long len = ( "" + rdp.get( "Block")).length() / 2;
                        if( len == 0) {
                            break;
                        }
                        // Local Write reQest/resPonse
                        SvcRequest lwq = new SvcRequest( null, ++txNr, 0, "WriteFile", null, TIME_OUT);
                        lwq.put( "Path", path);
                        lwq.put( "Name", name + "_OUT");
                        lwq.put( "Offset", pos);
                        lwq.put( "Block", rdp.get( "Block"));
                        SvcResponse lwp = SvcCatalog.getDispatcher().call(lwq);
                        if( lwp.getResultCode() != 0 ) {
                            log.warn( "Write failed " + path + " " + name + " " + lwp);
                            break;
                        }
                        log.trace( "copied " + name + " pos = " + pos + " rc=" + lwp.getResultCode());
                    }
                }
                namesAndLen.clear();
                path = null;
            } catch( Exception ex) {
                ++errorCount;
                log.warn( "Failed to synchronize", ex);
            }
        }


    }

}


