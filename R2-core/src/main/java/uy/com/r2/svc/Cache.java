/* Cache.java */
package uy.com.r2.svc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;

/**
 * Trivial cache service. 
 * This module stores responses by it service name and payload.
 * This is a reference implementation !!!!.
 *
 * @author G.Camargo
 */
public class Cache implements AsyncService {

    private static final Logger log = Logger.getLogger( Cache.class );
    private Map<String, CacheEntry> cache = new HashMap();
    private HashSet<String> cacheableServices = new HashSet();
    private int ttd = 1000 * 60 * 10;  // 10 minutes

    /**
     * Get the configuration descriptors of this module.
     *
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "CacheablesServices", ConfigItemDescriptor.STRING,
                "Cacheable services names, comma separated", null));
        l.add( new ConfigItemDescriptor( "TimeToDiscard", ConfigItemDescriptor.INTEGER,
                "Time in mS to discard a cache entry", "60000"));
        l.add( new ConfigItemDescriptor( "ResetCache", ConfigItemDescriptor.BOOLEAN,
                "Discad all cached entrys", "false"));
        return l;
    }

    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isChanged()) {
            return;
        }
        ttd = cfg.getInt( "TimeToDiscard", ttd);
        if( cfg.getBoolean( "Reset")) {
            String sa[] = cfg.getString( "CacheablesServices").split( ",");
            cacheableServices = new HashSet( Arrays.asList( sa));
            cache = new HashMap();
        }
    }

    /**
     * Invocation dispatch phase. It may (1) prepare a SvcResponse itself, (2)
     * return a SvcRequest to dispatch to the next service module, or (3) throw
     * a Exception to clearly set what module originates the failure.
     *
     * @param req Invocation message from caller
     * @return SvcRequest to dispatch to the next module or SvcResponse to
     * caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        SvcMessage rr;
        CacheEntry ce = cache.get( req.getServiceName() + req.getPayload().toString());
        if( ce != null && ce.expTime > System.currentTimeMillis()) {
            // Catched response
            ce.hits++;
            rr = new SvcResponse( ce.res, ce.rc, req );
            log.trace( "Served from cache" );
        } else {
            rr = req;  // Execute this one
            log.trace( "Go on to the next, cache size=" + cache.size() );
        }
        return rr;
    }

    /**
     * Process a response. If something goes wrong it should throw a Exception
     * to clearly set what module originates the failure.
     *
     * @param resp SvcResponse message from next module
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        SvcRequest req = resp.getRequest();
        if( cacheableServices.contains( req.getServiceName())) {  // Is cacheable
            CacheEntry ce = new CacheEntry();
            ce.req = req.getPayload();
            ce.res = resp.getPayload();
            ce.expTime = System.currentTimeMillis() + ttd;
            ce.rc = resp.getResultCode();
            cache.put( req.getServiceName() + req.getPayload().toString(), ce);
            log.trace( "Saved to cache, cache size=" + cache.size());
        }
        return resp;
    }

    /**
     * Get the status report.
     *
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put( "Version", "$Revision: 1.1 $" );
        m.put( "Size", cache.size() );
        for( String s : cache.keySet() ) {
            m.put( "HitsOnService_" + s, "" + cache.get( s).hits );
        }
        return m;
    }

    /**
     * Release all the allocated resources.
     */
    @Override
    public void shutdown() {
        cache = new HashMap<String, CacheEntry>();
    }

    private static class CacheEntry {
        Map<String, List<Object>> req;
        Map<String, List<Object>> res;
        int rc;
        long expTime;
        int hits = 0;
    }

}
