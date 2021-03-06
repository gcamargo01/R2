/* SvcTestingCaller.java */
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
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.StartableModule;


/** Module to simulate client invocations, like automated stress-test.
 * This module is not a real service. It implements StartUpRequired to be loaded.
 * @author G.Camargo
 */
public class SvcTestingCaller implements StartableModule {
    private static final Logger LOG = Logger.getLogger(SvcTestingCaller.class);
    // Cfg
    private int testTime = 0; 
    private int testThreads = 1;
    private int testIterations = Integer.MAX_VALUE;
    private int sleepTime = 0;
    private String msgs[] = { ""};
    private String pipe = null;
    private int invocationTimeout = 1000;
    private String service;
    private String node;
    // Vars
    private final Object lock = new Object();
    private boolean stopTest = false;
    private int activeWorkers = 0;
    private int msgIndex = 0;
    private long totalTime = 0;
    private long time = 0;
    private int errors = 0;
    private int iterations = 0;
    private int responseTimeSum = 0;
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<ConfigItemDescriptor>();
        l.add( new ConfigItemDescriptor( "TestTime", ConfigItemDescriptor.INTEGER,
                "Test time in mS"));
        l.add( new ConfigItemDescriptor( "TestThreads", ConfigItemDescriptor.INTEGER,
                "Test threads", "1"));
        l.add( new ConfigItemDescriptor( "TestIterations", ConfigItemDescriptor.INTEGER,
                "Test loop iterations", "1"));
        l.add( new ConfigItemDescriptor( "SleepTime", ConfigItemDescriptor.INTEGER,
                "Sleep time after each test loop in mS", "0"));
        l.add( new ConfigItemDescriptor( "Messages", ConfigItemDescriptor.STRING,
                "List of request messages, separated by comma (,)"));
        l.add( new ConfigItemDescriptor( "Service", ConfigItemDescriptor.STRING,
                "Service name called in the test", "Service"));
        l.add( new ConfigItemDescriptor( "Node", ConfigItemDescriptor.STRING,
                "Client node name used in the test", "TESTER"));
        l.add( new ConfigItemDescriptor( "InvocationTimeout", ConfigItemDescriptor.INTEGER,
                "Response time-out in mS", "1000"));
        l.add( new ConfigItemDescriptor( "Pipeline", ConfigItemDescriptor.STRING,
                "Service pipeline name to dispatch"));
        l.add( new ConfigItemDescriptor( "DumpStatus", ConfigItemDescriptor.BOOLEAN,
                "Dump all the status at the end ofthe test", "true"));
        return l;
    }

    /** Configure an perform tests */
    @Override
    public void start( Configuration cfg) throws Exception { 
        testTime = cfg.getInt( "TestTime");
        testThreads = cfg.getInt( "TestThreads");
        testIterations = cfg.getInt( "TestIterations");
        sleepTime = cfg.getInt( "SleepTime");
        if( cfg.containsKey( "Messages")) {
            msgs = cfg.getString( "Messages").split( ",");
            for( String m: msgs) {
                LOG.trace( "msgs='" + m + "'");
            }
        }
        pipe = cfg.getString( "Pipeline");
        service = cfg.getString( "Service");
        node = cfg.getString( "Node");
        invocationTimeout = cfg.getInt( "InvocationTimeout");
        // reset vars
        stopTest = false;
        activeWorkers = 0;
        msgIndex = 0;
        totalTime = 0;
        time = 0;
        errors = 0;
        iterations = 0;
        responseTimeSum = 0;
        long t0 = System.currentTimeMillis();
        // Start the threads
        LOG.warn( "**** Starting Tests .... " + testIterations + " on " + testThreads);
        for( int i = 0; i < testThreads; ++i) {
            new Worker( "ClientTest" + i);
        }
        // Wait and stop
        if( testTime > 0) {
            Thread.sleep( time = testTime);
        } else {
            while( activeWorkers > 0) {
                Thread.sleep( 250);
                LOG.warn( "activeWorkers=" + activeWorkers + " " + iterations + " / " + testIterations);
            }
        } 
        totalTime = ( int)(System.currentTimeMillis() - t0);
        LOG.warn( "**** Ended Tests .... ");
        stopTest = true;
        time = responseTimeSum / testThreads;
        Thread.sleep( 1);  // Let all finalize
        if( cfg.getBoolean( "DumpStatus")) {
            Map<String,Object> m = SvcCatalog.getCatalog().getStatusVars();
            for( String k: m.keySet()) {
                LOG.warn( "Status " + k + "=" + m.get( k));
            }
        }
        LOG.warn( "Iterations          = " + iterations);
        LOG.warn( "Errors              = " + errors);
        LOG.warn( "Threads             = " + testThreads);
        LOG.warn( "Execution Time      = " + time);
        LOG.warn( "Real Time           = " + totalTime);
        LOG.warn( "Response Time Sum.  = " + responseTimeSum);
        LOG.warn( "Response Time Avg.  = " + ( double)responseTimeSum 
                   / iterations + " mS");
        if( time > 0) {
           LOG.warn( "Total calls / sec.  = " + ( 1000.0 * iterations) 
                   / time);
        }
    }

    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> m = new HashMap<>();
        Package pak = getClass().getPackage();
        if( pak != null) {
            m.put( "Version", "" + pak.getImplementationVersion());
        } 
        m.put( "RunningTests", !stopTest);
        m.put( "TotalIterations", iterations);
        m.put( "TotalResponseTime", responseTimeSum);
        m.put( "Threads", testThreads);
        m.put( "ExecutionTime", time);
        m.put( "RealTime", totalTime);
        if( iterations > 0) {
            m.put( "ResponseTimeAvg", ( double)responseTimeSum / iterations);
        }
        if( time > 0) {
            m.put( "CallsPerSecond", ( 1000.0 * iterations) / time);
        }
        return m;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        stopTest = true;
    }

    private String getMessage() throws Exception {
        int i = msgIndex + 1;
        i %= msgs.length;
        return msgs[ msgIndex = i];
    }
    
    class Worker extends Thread {
       
        Worker( String name) {
            super( name);
            LOG.trace( "Start " + name);
            synchronized( lock) {
                ++activeWorkers;
            }
            start();
        }

        @Override
        public void run() {
            while( !stopTest) {
                int it0;
                int range = 1000; 
                synchronized( lock) {
                    if( iterations >= testIterations) {
                        break;
                    }
                    if( range > ( testIterations - iterations)) {
                        range = testIterations - iterations;
                    }
                    it0 = iterations;
                    iterations += range;
                }
                long t = 0;
                for( int i = it0; i < it0 + range; ++i) {
                    try {
                        if( LOG.isTraceEnabled()) {
                            LOG.debug(">>> " + pipe + " " + getName() + ":" + i + " " + getName());
                        }
                        String m = getMessage();
                        Map<String,List<Object>> data = SvcMessage.addToMap( null, "Data", m);
                        SvcMessage.addToMap( data, "ItCount", i);
                        SvcMessage.addToMap( data, "ThName", getName());
                        SvcRequest rq = new SvcRequest( node, i, 0, service, data, invocationTimeout);
                        //long t0 = System.currentTimeMillis();
                        long t0 = System.nanoTime();
                        SvcResponse rp;
                        if( pipe == null || pipe.isEmpty()) {
                            rp = SvcCatalog.getDispatcher().call( rq);
                        } else {
                            rp = SvcCatalog.getDispatcher().callPipeline( pipe, rq);
                        }
                        //t += (int)( System.currentTimeMillis() - t0);
                        t += ( System.nanoTime() - t0);
                        if( LOG.isTraceEnabled()) {
                            LOG.debug("<<< " + pipe + " t=" + t + " " + rp);
                        }    
                        /*
                        if( testIterations > 100 && ( i % (testIterations / 10)) == 0) {
                            LOG.warn( "" + (int)( i * 100 / testIterations) + "% response= " + rp);
                        }
                        */
                        if( rp.getResultCode() < 0) {
                            LOG.warn( "Iteration " + i + " failed " + rp);
                            ++errors;
                            if( errors > 100) {
                                LOG.error( getName() + " Stopped by >100 errors" + errors);
                                stopTest = true;
                                break;
                            }
                        }
                        if( sleepTime > 0) {
                            Thread.sleep( sleepTime);
                        }
                    } catch( Exception ex) {
                        synchronized( this) {
                            ++errors;
                        }
                        LOG.warn( ex, ex);
                    }
                }
                synchronized( lock) {
                    responseTimeSum += (int)( t / 1000000);
                }
            }    
            synchronized( lock) {
                --activeWorkers;
            }
        }
        
    }
    
}


