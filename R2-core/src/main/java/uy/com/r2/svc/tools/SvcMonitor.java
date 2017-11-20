/* SvcMonitor.java */
package uy.com.r2.svc.tools;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Module;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SimpleService;

/** Internal service to monitor any other service.
 * It keeps track of service usage and store statistics information.
 * !!!! It should not modify module behavior, eg: in Exceptions, just trace
 * @author G.Camargo
 */
public class SvcMonitor implements AsyncService, SimpleService {
    private final static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final Logger LOG = Logger.getLogger(SvcMonitor.class);
    
    private final AsyncService svc;
    private final SimpleService sSvc;
    private final String name;
    private int keepLast = 0;
    private SvcRequest lastReqs[];
    private SvcResponse lastResp[];
    private int lastReqIndex = 0;
    private int lastRespIndex = 0;
    // Statistics
    private int invocationsCount = 0;
    private int errorsCount = 0;
    private int timeoutCount = 0;
    private int responseOnReqCount = 0;
    private int avgResponseTime = 0;
    private int lastResponseTime = 0;
    private int maxResponseTime = 0;
    private int moduleProcessingTime = 0;
    private long deployTime = 0;
    private long setupTime = 0;
     
    /** Synchronous service constructor.
     * @param svc service implementation to be monitored
     * @param name service name
     */
    public SvcMonitor( Module svc, String name) {
        if( svc instanceof AsyncService) {
            this.svc = (AsyncService)svc;
            this.sSvc = null;
        } else {   // instanceof Service
            this.sSvc = (SimpleService)svc;
            this.svc = null;
        } 
        this.name = name;
        this.deployTime = System.currentTimeMillis();
        this.setupTime = this.deployTime;
        LOG.info( name + " instanced ");
    }
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        List<ConfigItemDescriptor> l = svc.getConfigDescriptors();
        if( l == null) {
            l = new LinkedList();
        }
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        // Resest statistics
        if( cfg.isUpdated()) {
            LOG.info( name + " New Configuration " + cfg.hashCode() + " " + cfg);
            setupTime = System.currentTimeMillis();
            invocationsCount = 0;
            errorsCount = 0;
            timeoutCount = 0;
            responseOnReqCount = 0;
            avgResponseTime = 0;
            lastResponseTime = 0;
            maxResponseTime = 0;
            moduleProcessingTime = 0;
            // setConfiguration
            if( cfg.getInt( "MonitorLastNr") > 0) {
                keepLast = cfg.getInt( "MonitorLastNr");
            }
            if( keepLast > 0) {
                lastReqs = new SvcRequest[ keepLast];
                lastResp = new SvcResponse[ keepLast];
            } 
        } else {
            LOG.info( name + " same Configuration " + cfg.hashCode());
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
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        LOG.info( name + ".onRequest called  " + req);
        setConfiguration( cfg);
        ++invocationsCount;
        putReq( req);
        SvcMessage r;
        long t0 = System.currentTimeMillis();
        try {
            r = svc.onRequest( req, cfg);
        } catch( Exception x) {
            LOG.warn( "onRequest failed: " + x);
            r = new SvcResponse( "Failed to process request to module " + name, 
                    SvcResponse.RES_CODE_EXCEPTION, x, req);
        }
        moduleProcessingTime += System.currentTimeMillis() - t0;
        if( moduleProcessingTime >= cfg.getInt( "TimeOut")) {
            ++timeoutCount;
        }
        if( r instanceof SvcResponse) {
            ++responseOnReqCount;
            SvcResponse rr = (SvcResponse)r;
            if( rr.getResultCode() < 0) {
                ++errorsCount;
            }
            putResp( rr);
        }
        LOG.info( name + ".onRequest returns " + r);
        return r;
    }

    /** Process a response phase.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param r SvcResponse message from next module, or null (no next)
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse r, Configuration cfg) throws Exception {
        LOG.info( name + ".onResponse called " + r);
        setConfiguration( cfg);
        long t0 = System.currentTimeMillis();
        try {
            r = svc.onResponse( r, cfg);
        } catch( Exception x) {
            LOG.warn( "onResponse failed: " + x);
            r = new SvcResponse( "Failed to process reposne on module " + name, 
                    SvcResponse.RES_CODE_EXCEPTION, x, r.getRequest());
        }    
        moduleProcessingTime += System.currentTimeMillis() - t0;
        int rt = r.getResponseTime();
        lastResponseTime = rt;
        avgResponseTime = ( int)( 9L * avgResponseTime + rt) / 10;
        if( rt > maxResponseTime) {
            maxResponseTime = rt;
        }
        if( r.getResultCode() < 0) {
            ++errorsCount;
        }
        putResp( r);
        LOG.trace( name + " keepLast=" + keepLast + " lastRespIndex=" + lastRespIndex + " " + lastResp);
        LOG.info( name + ".onResponse returns " + r);
        return r;
    }

    /** Service call.
     * @param req Invocation message
     * @return SvcResponse message
     * @throws Exception Unexpected error, the responseCode will be lower than 0
     */
    @Override
    public SvcResponse call( SvcRequest req, Configuration cfg) throws Exception {
        LOG.info( name + ".call called " + req);
        ++invocationsCount;
        putReq( req);
        SvcResponse r;
        long t0 = System.currentTimeMillis();
        try {
            r = sSvc.call( req, cfg);
        } catch( Exception x) {
            LOG.warn( "call failed: " + x);
            r = new SvcResponse( "Failed to process call to module " + name, 
                    SvcResponse.RES_CODE_EXCEPTION, x, req);
        }
        moduleProcessingTime += System.currentTimeMillis() - t0;
        if( r.getResultCode() < 0) {
            ++errorsCount;
        }
        LOG.info( name + ".call returns " + r);
        putResp( r);
        return r;
    }

    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> m = new TreeMap();
        m.put( "Count", invocationsCount);
        m.put( "ErrorCount", errorsCount);
        m.put( "TimeOuts", timeoutCount);
        m.put( "ResponseOnReqCount", responseOnReqCount);
        m.put( "LastResponseTime", lastResponseTime);
        m.put( "AvgResponseTime", avgResponseTime);
        m.put( "MaxResponseTime", maxResponseTime);
        m.put( "ModuleProcessingTime", moduleProcessingTime);
        SimpleDateFormat df = new SimpleDateFormat( DATE_FORMAT);
        m.put( "DeployTime", df.format( new Date( deployTime)));
        m.put( "SetupTime", df.format( new Date( setupTime)));
        if( keepLast > 0) {
            for( int i = 0; i < keepLast; ++i) {
                int iq = ( lastReqIndex + keepLast - i - 1) % keepLast;
                if( lastReqs[ iq] == null) {
                    break;
                }
                m.put( "Request_" + i, lastReqs[ iq]);
                int ir = ( lastRespIndex + keepLast - i - 1) % keepLast;
                m.put( "Response_" + i, lastResp[ ir]);
            }
        }
        Map<String,Object> mm = new HashMap();
        try {
            mm = svc.getStatusVars();
        } catch( Exception x) {
            LOG.warn( "getStatusVars failed: " + x, x);
        }
        if( mm != null) {
            for( String k: mm.keySet()) {
                m.put( k, mm.get( k));
            }
        }
        LOG.trace( name + " getStatusVars " + m);
        return m;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        LOG.info( name + " shutdown");
        try {
            svc.shutdown();
        } catch( Exception x) {
            LOG.warn( "shutdown failed: " + x, x);
        }
        LOG.trace( name + " shutdown ended");
    }

    private synchronized void putReq( SvcRequest req) {
        if( keepLast > 0) {
            lastReqs[ lastReqIndex++] = req;
            lastReqIndex %= keepLast;
        }
    }

    private synchronized void putResp( SvcResponse res) {
        res.updateResponseTime();
        if( keepLast > 0) {
            lastResp[ lastRespIndex++] = res;
            lastRespIndex %= keepLast;
        }
    }

}


