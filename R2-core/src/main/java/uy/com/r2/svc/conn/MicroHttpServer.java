/* MicroHttpServer.java */
package uy.com.r2.svc.conn;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.List;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import org.apache.log4j.Logger;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import static uy.com.r2.svc.conn.MicroHttpServer.encoding;
import uy.com.r2.core.StartUpRequired;

/**
 * Micro HTTP server, to process remote commands.
 *
 * @author G.Camargo
 */
public class MicroHttpServer implements StartUpRequired {

    public static String encoding = System.getProperty( "file.encoding");
    private static final Logger LOG = Logger.getLogger( MicroHttpServer.class);
    private int txNr = 0;
    private ListenerThread server = null;
    private String pipe = "";
    private int maxWorkersLimit = 5;

    /** Get the configuration descriptors of this module.
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
                "System Pipeline name to route requests", ""));
        l.add( new ConfigItemDescriptor( "MaxWorkersLimit", ConfigItemDescriptor.INTEGER,
                "Maximum number of Threads used to serve current requests", "5"));
        return l;
    }

    /** Configure and start the server.
     * @throws java.lang.Exception 
     */
    @Override
    public void startUp( Configuration cfg) throws Exception {
        LOG.trace( "startup " + cfg + " " + cfg.isChanged());
        if( !cfg.isChanged()) {
            return;
        }
        int port = cfg.getInt( "Port");
        encoding = cfg.getString( "Encoding");
        pipe = cfg.getString( "Pipeline");
        maxWorkersLimit = cfg.getInt( "MaxWorkersLimit");
        // Shutdown if it was up
        if( server != null) {
            server.shutdown();
        }
        // Start the server to this port
        server = new ListenerThread( port, maxWorkersLimit, this);
        server.start();
        cfg.resetChanged();
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
        if( server != null) {
            map.putAll(server.getStatusVars());
        }
        return map;
    }

    /** Release all the allocated resources.
     */
    @Override
    public void shutdown() {
        LOG.debug( "shutdown");
        if( server != null) {
            server.shutdown();
        }
    }

    private void handle( WorkerThread reqResp) throws Exception {
        try {
            String thr = Thread.currentThread().getName();
            LOG.trace(thr + " *** handler " + reqResp.getRequestURI());
            // Process de HTTP reqResp
            String svc = "none";
            try {
                svc = reqResp.getRequestURI().getPath().substring( 1);
            } catch( Exception xx) { }
            if( svc.equals( "favicon.ico")) {
                reqResp.sendResponseHeaders( 404, 0);
                return;
            }
            String node = reqResp.getRemoteAddress().getHostName();
            Map<String,String> rqh = reqResp.getRequestHeaders();
            if( rqh.containsKey( "Node")) {
                node = rqh.get( "Node");
            }
            String userAgent = rqh.get( "User-Agent");
            if( userAgent != null && userAgent.isEmpty()) {
                userAgent = null;
            }
            // Parse HTTP parameters
            String query = reqResp.getRequestURI().getRawQuery();
            Map<String, List<Object>> params = null;
            try {
                params = parseQueryString( query);
            } catch( Exception ex) {
                LOG.warn( thr + " error parsing query " + query + ", ignored", ex);
            }
            if( LOG.isTraceEnabled()) {
                LOG.trace( thr + " *** svc=" + svc + " node=" + node + " ua=" + userAgent);
                LOG.trace( thr + " *** params=" + params);
            }
            // Invoke service
            SvcRequest req = new SvcRequest( node, ++txNr, 0, svc, params, 5000);
            SvcResponse resp = new SvcResponse( 1, req);
            try {
                // Dispatch invocation
                if( !pipe.isEmpty()) {
                    resp = SvcCatalog.getDispatcher().callPipeline( pipe, req);
                } else {
                    resp = SvcCatalog.getDispatcher().call( req);
                }
            } catch( Exception ex) {
                LOG.warn( thr + " dispatch error " + ex, ex);
            }     
            //LOG.trace( thr + " *** to send " + resp.toString().substring( 40) + "...");
            // Prepare and send HTTP sr
            StringBuilder sr = new StringBuilder();
            if( userAgent != null && resp.get( "SerializedHtml") != null) {
                LOG.trace( "**** HTML response");
                reqResp.getResponseHeaders().put( "Content-Type", "text/html");
                sr.append( "" + resp.get( "SerializedHtml"));
            } else if( resp.get( "SerializedJson") != null) {
                LOG.trace( "**** JSON response");
                reqResp.getResponseHeaders().put( "Content-Typee", "application/json");
                sr.append( "" + resp.get( "SerializedJson"));
            } else {
                LOG.trace( "**** TXT response");
                sr.append( "" + resp.getPayload());
            }
            sr.append( "\n");
            reqResp.getResponseHeaders().put( "ResultCode", "" + resp.getResultCode());
            reqResp.sendResponseHeaders( 200, sr.length());
            LOG.trace( thr + " *** sr = " + sr);
            OutputStream os = reqResp.getResponseBody();
            os.write( sr.toString().getBytes());
            os.flush();
            os.close();
            LOG.trace( thr + " *** end response lrg=" + sr.length());
        } catch( Exception x) {
            LOG.info( "" + x, x);
            throw new IOException( x);
        }    
    }

    private static Map<String, List<Object>> parseQueryString( String queryString)throws Exception {
        Map<String, List<Object>> parameters = new HashMap();
        if( queryString != null && !queryString.isEmpty()) {
            String pairs[] = queryString.split( "[&]");
            for( String pair : pairs) {
                String param[] = pair.split( "[=]");
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
    
    private class WorkerThread extends Thread {
        private final Socket soc;
        private final ListenerThread listener;
        private final MicroHttpServer handler;
        private InetAddress addrs;
        private URI uri;
        private Map<String, String> reqHeaders = new HashMap();
        private OutputStream outStream = null;
        private Map<String, String> respHeaders = new HashMap();
        
        private WorkerThread( int port, Socket soc, ListenerThread listener, MicroHttpServer handler) {
            this.soc = soc;
            this.listener = listener;
            this.handler = handler;
            setName( "HttpWorker_" + port + "_" + getId());
            LOG.trace( "worker " + getName() +" starting");
        }
        
        @Override
        public void run() {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader( new InputStreamReader( soc.getInputStream()));
                outStream = soc.getOutputStream();
                
                // Read HTTP headers and parse out the route.
                String route = "";
                String line;
                while( ( line = reader.readLine()) != null && !line.isEmpty()) {
                    //LOG.trace( "parsing line " + line);
                    if( line.startsWith( "GET /")) {
                        int start = line.indexOf( '/');
                        int end = line.indexOf( ' ', start);
                        route = line.substring( start, end);
                    } else if( line.contains( ": ")) {
                        int sov = line.indexOf( ": ") ;
                        reqHeaders.put( line.substring( 0, sov), line.substring( sov + 2));
                    }
                }
                LOG.trace( "route " + route + " headers " + reqHeaders);
 
                // Process request.
                uri = URI.create(  "http://localhost" + route); 

                // Send out the content.
                addrs = soc.getInetAddress();
                handler.handle( this);
                outStream.flush();
                listener.releaseInfo( this, false);
            } catch( Exception x) {
                try {
                    outStream.write( "HTTP/1.0 500 Internal Server Error".getBytes());
                    outStream.flush();
                } catch( Exception xx) { }    
                listener.releaseInfo( this, true);
            } finally {
                try {
                    outStream.close();
                    reader.close();
                } catch( Exception x ) { }
            }                
        }
        
        private URI getRequestURI() {
            return uri;
        }

        private Map<String, String> getRequestHeaders() {
            return reqHeaders;
        }

        private InetAddress getRemoteAddress() {
            return addrs;
        }

        private Map<String, String> getResponseHeaders() {
            return respHeaders;
        }

        private OutputStream getResponseBody() {
            return outStream;
        }

        private void sendResponseHeaders( int httpRetCode, int len) throws Exception {
            if( !respHeaders.containsKey( "Content-Length")) {
                respHeaders.put( "Content-Length", "" + len);
            }
            String line;
            line = "HTTP/1.0 " + httpRetCode + " " + ( ( httpRetCode == 200)? "OK": "Error") + "\r\n";
            outStream.write( line.getBytes( encoding));
            for( String h: respHeaders.keySet()) {
                String v = respHeaders.get( h);
                line = h + ": " + v + "\r\n";
                outStream.write( line.getBytes( encoding));
            }
            outStream.write( '\r');
            outStream.write( '\n');
        }

    }

    class ListenerThread extends Thread {
        private final Object sync = new Object(); 
        private final int port;
        private final int maxWorkersLimit;
        private final MicroHttpServer handler;
        private ServerSocket serSoc;
        private int workers = 0;
        private int maxWorkers = 0;
        private int requests = 0;
        private int errors = 0;

        private ListenerThread( int port, int maxWorkersLimit, MicroHttpServer handler) throws Exception {
            this.port = port;
            this.serSoc = new ServerSocket( port);
            this.maxWorkersLimit = maxWorkersLimit;
            this.handler = handler;
            setName( "HttpListener_" + port);
        }

        private void releaseInfo( WorkerThread wrkr, boolean error) {
            synchronized( sync) {
                --workers;
                if( error) {
                    ++errors;
                }
            }
            LOG.trace( "relese " + wrkr.getName() + " error=" + error);
        }
        
        private Map<String, Object> getStatusVars() {
            Map<String, Object> map = new HashMap();
            map.put( "Workers", "" + workers);
            map.put( "MaxWorkers", "" + maxWorkers);
            map.put( "Requests", "" + requests);
            map.put( "RequestErrors", "" + errors);
            return map; 
        }
        
        private void shutdown() {
            try {
                serSoc.close();
                serSoc = null;
            } catch( IOException ex ) { }
        }

        @Override
        public void run() {
            while( serSoc != null) {
                try {
                    Socket s = serSoc.accept();
                    boolean overloaded;
                    synchronized( sync) {
                        overloaded = ( workers >= maxWorkersLimit);
                        ++requests;
                        if( !overloaded) {
                            ++workers;
                            maxWorkers = ( workers > maxWorkers)? workers: maxWorkers; 
                        }
                    }
                    if( overloaded) {
                        s.close();
                        ++errors;
                        throw new Exception( "Request overload " + maxWorkersLimit);
                    } else {
                        new WorkerThread( port, s, this, handler).start();
                    }
                } catch( Exception ex) {
                    if( serSoc != null) {
                        LOG.warn( "Error on Listener: " + ex, ex);
                    }
                }
            }
        }
    }
    
}


