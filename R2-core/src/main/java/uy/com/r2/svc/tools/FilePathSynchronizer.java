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
    private static int txNr = 0;
    private boolean stop = false;
    private int interval = 1000;
    private Worker wrk = null;
    private Map<String,String> pathMap = null;
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
        Map<String,Object> m = new HashMap();
        m.put( "Version", "$Revision: 1.1 $");
        if( wrk != null) {
            m.putAll( wrk.getStatusVars());
        } else {
            m.put( "Running", false);
        }    
        return m;
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
                        // List Local directory reQuest/resPonse
                        SvcRequest llq = new SvcRequest( 
                                FilePathSynchronizer.class.getSimpleName(), 
                                ++txNr, 0, "FileList", null, TIME_OUT);
                        llq.add( "Path", path);
                        SvcResponse llp = SvcCatalog.getDispatcher().callNext( llq);
                        // Remote List directory reQuest/resPonse
                        SvcRequest rlq = new SvcRequest( 
                                FilePathSynchronizer.class.getSimpleName(), 
                                ++txNr, 0, remote + "/FileList", null, TIME_OUT);
                        rlq.add( "Path", path);
                        SvcResponse lrp = SvcCatalog.getDispatcher().callNext( rlq);
                        // Compare 
                        log.trace( "to compare l " + llq + " r " + rlq);
                        for( String fn: lrp.getPayload().keySet()) {
                            // Take one name from remote
                            if( !( lrp.get( fn) instanceof Map)) {
                                log.trace( "** Discard data response, not a map: " + fn);
                                continue;
                            }
                            @SuppressWarnings("unchecked")
                            Map<String,Object> m = ( Map)lrp.get( fn);
                            log.trace( "** Data response Map: " + m);
                            Long lastMod = (long)Double.parseDouble( "" + m.get( "LastModified"));
                            // Take local (if exists)
                            Long localLastMod = null;
                            if( llp.get( fn) != null) {
                                Map<String,Object> lm = ( Map)llp.get( fn);
                                localLastMod = (long)Double.parseDouble( "" + m.get( "LastModified"));
                            } 
                            // If not equal, add to copy
                            //if( !lastMod.equals( localLastMod)) {
                                namesAndLen.put( fn, ( long)Double.parseDouble( "" + m.get( "Length")));
                            //}
                        }                   
                        // Copy each one
                        log.trace( "to copy " + namesAndLen);
                        for( String name: namesAndLen.keySet()) {
                            // Read Remote reQest/resPponse
                            SvcRequest rrq = new SvcRequest( 
                                    FilePathSynchronizer.class.getSimpleName(), 
                                    ++txNr, 0, remote + "/FileRead", null, TIME_OUT);
                            rrq.put( "Path", path);
                            rrq.put( "Name", name);
                            rrq.put( "Length", "" + namesAndLen.get(name));
                            SvcResponse rdp = SvcCatalog.getDispatcher().callNext( rrq);
                            // Write Local reQest/resPponse
                            SvcRequest wlq = new SvcRequest( 
                                    FilePathSynchronizer.class.getSimpleName(), 
                                    ++txNr, 0, "FileWrite", null, TIME_OUT);
                            wlq.put( "Path", path);
                            wlq.put( "Name", name);
                            wlq.put( "Block", rdp.get( "Block"));
                            SvcResponse rwp = SvcCatalog.getDispatcher().callNext( wlq);
                            log.trace("copied " + name + " " + rwp.getResultCode());
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


