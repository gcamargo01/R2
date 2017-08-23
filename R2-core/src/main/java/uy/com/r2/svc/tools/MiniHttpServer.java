/* MiniHTTPServer.java */
package uy.com.r2.svc.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.List;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URLDecoder;
import java.util.ArrayList;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;
import org.apache.log4j.Logger;
import uy.com.r2.core.CoreModule;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/**
 * Mini HTTP server, to process remote commands.
 *
 * @author G.Camargo
 */
public class MiniHttpServer implements CoreModule {

    private static final Logger log = Logger.getLogger(MiniHttpServer.class);
    private int txNr = 0;
    private String encoding = System.getProperty( "file.encoding");
    private ExecutorService pool = null;
    private HttpServer server = null;
    private String pipe = null;
    private int calledTimes = 0;
    private int callingErrors = 0;

    /**
     * Get the configuration descriptors of this module.
     *
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Port", ConfigItemDescriptor.INTEGER,
                "Port nomber where the server is listening", "8012"));
        l.add( new ConfigItemDescriptor( "Encoding", ConfigItemDescriptor.STRING,
                "Encoding", System.getProperty( "file.encoding")));
        l.add( new ConfigItemDescriptor( "Pipeline", ConfigItemDescriptor.STRING,
                "System Pipeline name to route requests", null));
        return l;
    }

    @Override
    public void startup( Configuration cfg) throws Exception {
        log.trace( "starup " + cfg + " " + cfg.isChanged());
        if( !cfg.isChanged()) {
            return;
        }
        int port = cfg.getInt( "Port", 8012);
        encoding = cfg.getString( "Encoding");
        pipe = cfg.getString( "Pipeline");
        log.debug( "port=" + port);
        // Shutdown if it was up
        if( pool != null) {
            if( !pool.isShutdown()) {
                pool.shutdownNow();
                Thread.sleep( 100);
            }
        }
        // Start the server to this port
        log.trace( "staring on port=" + port);
        pool = Executors.newCachedThreadPool();
        server = HttpServer.create( new InetSocketAddress( port), 0);
        server.createContext( "/", new MyHandler());
        server.setExecutor( pool); // creates a default executor
        server.start();
        cfg.resetChanged();
        // reset statistics
        calledTimes = 0;
        callingErrors = 0;
    }

    /**
     * Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put( "CalledTimes", calledTimes);
        m.put( "CallingErrors", callingErrors);
        return m;
    }

    /**
     * Release all the allocated resources.
     */
    @Override
    public void shutdown() {
        log.debug( "shutdown");
        if( pool != null) {
            pool.shutdown();
        }
        if( server != null) {
            server.stop( 1);
        }
    }

    class MyHandler implements HttpHandler {

        @Override
        public void handle( HttpExchange t) throws IOException {
            String thr = Thread.currentThread().getName();
            log.trace( thr + " *** handler " + t.getRequestURI());
            // Process de HTTP request
            String svc = "none";
            try {
                svc = t.getRequestURI().getPath().substring( 1);
            } catch( Exception xx) { }
            if( svc.equals( "favicon.ico")) {
                t.sendResponseHeaders( 404, 0);
                return;
            }
            ++calledTimes;
            String node = t.getRemoteAddress().getHostName();
            Headers rqh = t.getRequestHeaders();
            if( rqh.containsKey( "Node")) {
                node = rqh.getFirst( "Node");
            }
            String userAgent = rqh.getFirst( "User-Agent");
            // Parse HTTP parameters
            String query = t.getRequestURI().getRawQuery();
            Map<String, List<Object>> params = null;
            try {
                params = parseQuery( query);
            } catch( Exception ex) {
                log.warn( thr + " error parsing query " + query + ", ignored", ex);
                ++callingErrors;
            }
            if( log.isTraceEnabled()) {
                log.trace( thr + " *** svc=" + svc + " node=" + node + " ua=" + userAgent);
                log.trace( thr + " *** params=" + params);
            }
            // Invoke service
            SvcRequest req = new SvcRequest( node, ++txNr, 0, svc, params, 5000);
            SvcResponse resp = new SvcResponse( 1, req);
            try {
                // Dispatch invocation
                if( pipe != null) {
                    resp = SvcCatalog.getDispatcher().callPipeline( pipe, req);
                } else {
                    resp = SvcCatalog.getDispatcher().call( req);
                }
            } catch( Exception ex) {
                ++callingErrors;
                log.warn( thr + " dispatch error " + ex, ex);
            }     
            // Prepare and send HTTP response
            String response = "" + resp.get( "Serialized");
            t.getResponseHeaders().add( "ResultCode", "" + resp.getResultCode());
            t.sendResponseHeaders( 200, response.length());
            OutputStream os = t.getResponseBody();
            os.write( response.getBytes());
            os.close();
            log.trace( thr + " *** end response=" + response);
        }
    }

    private Map<String, List<Object>> parseQuery( String query)throws Exception {
        Map<String, List<Object>> parameters = new HashMap<String, List<Object>>();
        if( query != null && !query.isEmpty()) {
            String pairs[] = query.split("[&]");
            for( String pair : pairs) {
                String param[] = pair.split("[=]");
                String key = null;
                String value = null;
                if( param.length > 0) {
                    key = URLDecoder.decode( param[ 0], encoding); 
                }
                if( param.length > 1) {
                    value = URLDecoder.decode( param[ 1], encoding);
                }
                if( parameters.containsKey( key)) {
                    List<Object> lst = parameters.get( key);
                    lst.add( value);
                } else {
                    List<Object> lst = new ArrayList<Object>();
                    lst.add( value);
                    parameters.put( key, lst);
                }
            }
        }
        return parameters;
    }
    
}

