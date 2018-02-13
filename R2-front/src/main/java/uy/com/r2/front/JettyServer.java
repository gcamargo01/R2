package uy.com.r2.front;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import uy.com.r2.core.SvcCatalog;
import uy.com.r2.core.api.StartableModule;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

 
public class JettyServer extends AbstractHandler implements StartableModule {
    private static final Logger LOG = Logger.getLogger(JettyServer.class);
    private static final String SEC_PORT_LABEL = "HttpsPort";
    private static final String CLEAR_PORT_LABEL = "HttpPort";
    private static final String KEYSTORE_PATH_LABEL = "KeystorePath";
    private static final String KEYSTORE_PWD_LABEL = "KeyPass";
    private static final String NEED_CLIENT_CERT_LABEL = "NeedClientCert";
    private static final String PIPELINE_LABEL = "NeedClientCert";
    
    private static int txNr = 0;
    private Server server = null;
    private String pipe = null;

    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * @return ConfigItemDescriptor List
     */
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( SEC_PORT_LABEL, ConfigItemDescriptor.INTEGER, 
                "HTTPS Port to listen", "8443", ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( CLEAR_PORT_LABEL, ConfigItemDescriptor.INTEGER, 
                "HTTP Port to listen", "8080", ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( KEYSTORE_PATH_LABEL, ConfigItemDescriptor.STRING, 
                "Keystore path", "./src/main/resources/keystore", ConfigItemDescriptor.DEPLOYER));
        l.add( new ConfigItemDescriptor( KEYSTORE_PWD_LABEL, ConfigItemDescriptor.STRING, 
                "Keystore password", "password", ConfigItemDescriptor.SECURITY));
        l.add( new ConfigItemDescriptor( NEED_CLIENT_CERT_LABEL, ConfigItemDescriptor.BOOLEAN, 
                "The clieny is required to present a client certificate", null, ConfigItemDescriptor.SECURITY));
        l.add( new ConfigItemDescriptor( PIPELINE_LABEL, ConfigItemDescriptor.STRING, 
                "Runninf pipeline name to dispatch", null, ConfigItemDescriptor.DEPLOYER));
        return l;
    }

    /** Start up module execution.
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    public void start( Configuration cfg) throws Exception {
        LOG.info( "(Re)Starting " + cfg + " " + System.getProperty( "user.dir"));
        if( server == null) {
            shutdown();
        }
        server = new Server();
        pipe = cfg.getString( PIPELINE_LABEL);
        
        // === HTTP Configuration ===
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme( "https");
        httpConfig.setSecurePort( cfg.getInt( SEC_PORT_LABEL));
        httpConfig.setOutputBufferSize( 32768);
        httpConfig.setRequestHeaderSize( 8192);
        httpConfig.setResponseHeaderSize( 8192);
        httpConfig.setSendServerVersion( false);
        httpConfig.setSendDateHeader( false);

        // Add HTTP Connector
        ServerConnector http = new ServerConnector( server,
            new HttpConnectionFactory( httpConfig));
        http.setPort( cfg.getInt( CLEAR_PORT_LABEL));
        http.setIdleTimeout( 30000);
        server.addConnector( http);

        // Configure SSL KeyStore, TrustStore, and Ciphers
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath( cfg.getString( KEYSTORE_PATH_LABEL));
        sslContextFactory.setKeyStorePassword( cfg.getString( KEYSTORE_PWD_LABEL));
        sslContextFactory.setKeyManagerPassword( cfg.getString( KEYSTORE_PWD_LABEL));
        sslContextFactory.setTrustStorePath( cfg.getString( KEYSTORE_PATH_LABEL));
        sslContextFactory.setTrustStorePassword( cfg.getString( KEYSTORE_PWD_LABEL));
        sslContextFactory.setNeedClientAuth( cfg.getBoolean( NEED_CLIENT_CERT_LABEL));
        sslContextFactory.setExcludeCipherSuites(
            "SSL_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

        // SSL HTTP Configuration
        HttpConfiguration httpsConfig = new HttpConfiguration( httpConfig);
        httpsConfig.addCustomizer( new SecureRequestCustomizer()); 

        // Add SSL Connector
        ServerConnector sslConnector = new ServerConnector( server,
            new SslConnectionFactory( sslContextFactory, "http/1.1"),
            new HttpConnectionFactory( httpsConfig));
        sslConnector.setPort( cfg.getInt( SEC_PORT_LABEL));
        server.addConnector( sslConnector);
        
        // Start        
        server.setHandler( this);
        server.start();
    }
    
    /** HTTP Request handler. */
    public void handle( String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
        LOG.info( "" + request.getMethod() + " target: " + target + " auth: " + request.getAuthType() + " path:" + request.getRequestURI());
        //for( String h: Collections.list( request.getHeaderNames())) {
        //    log.info( h + "=" + request.getHeader( h) + "|");
        //}
        LOG.info( "qs: " + request.getQueryString());

        // Process de HTTP reqResp
        String svc = "none";
        try {
            svc = target.substring( 1);
        } catch( Exception xx) { }
        if( svc.equals( "favicon.ico")) {
            response.sendError( 404);
            return;
        }
        String node = request.getRemoteAddr();
        if( request.getHeader( "Node") != null) {
            node = request.getHeader( "Node");
        }
        String userAgent = request.getHeader( "User-Agent");
        // Convert Map of arrays to Map of Lists
        Map<String, List<Object>> params = new HashMap();
        for( String p: request.getParameterMap().keySet()) {
           for( String value: request.getParameterValues( p)) {
              SvcMessage.addToMap( params, p, value);
           }
        }
        // Invoke service
        SvcRequest req = new SvcRequest( node, ++txNr, 0, svc, params, 0);
        SvcResponse resp = new SvcResponse( 1, req);
        try {
            // Dispatch invocation
            if( !pipe.isEmpty()) {
                resp = SvcCatalog.getDispatcher().callPipeline( pipe, req);
            } else {
                resp = SvcCatalog.getDispatcher().call( req);
            }
        } catch( Exception ex) {
            LOG.warn( "Dispatch error " + ex, ex);
        }     
        //LOG.trace( thr + " *** to send " + resp.toString().substring( 40) + "...");
        // Prepare and send HTTP sr
        StringBuilder sr = new StringBuilder();
        if( userAgent != null && resp.get( "SerializedHtml") != null) {
            LOG.trace( "**** HTML response");
            response.setHeader( "Content-Type", "text/html");
            sr.append( "" + resp.get( "SerializedHtml"));
        } else if( resp.get( "SerializedJson") != null) {
            LOG.trace( "**** JSON response");
            response.setHeader( "Content-Type", "application/json");
            sr.append( "" + resp.get( "SerializedJson"));
        } else {
            LOG.trace( "**** TXT response");
            sr.append( "" + resp.getPayload());
        }
        sr.append( "\n");
        response.setHeader( "ResultCode", "" + resp.getResultCode());
        OutputStream os = response.getOutputStream();
        os.write( sr.toString().getBytes());
        os.flush();
        os.close();
        LOG.trace( "*** end response lrg=" + sr.length());
        /*
        response.setContentType( "text/html;charset=utf-8");
        response.setStatus( HttpServletResponse.SC_OK);
        baseRequest.setHandled( true);
        response.getWriter().println( "<h1>Hello</h1>");
        */
    }
 
    /** Get the status report of the module.
     * It may occurs at any time to get the current status of the module.
     * The variables may include: Version, ServiceLevel, LastErrors, ...
     * @return Variable and value map
     */
    public Map<String,Object> getStatusVars() {
        Map<String,Object> map = new TreeMap();
        Package pak = getClass().getPackage();
        if( pak != null) {
            map.put( "Version", "" + pak.getImplementationVersion());
        }
        map.put( "Status", server.getState());
        return map;
    }

    /** Stop execution and release all the allocated resources. */
    public void shutdown() {
        try {
            server.stop();
            server = null;
        } catch( Exception ex ) {
            LOG.warn( "Exception while stopping HTTP Server", ex );
        }
    }
    
    /** Test entry point. */
    public static void main( String[] args) throws Exception {
        org.apache.log4j.BasicConfigurator.configure();
        JettyServer ms = new JettyServer();
        Configuration c = new Configuration( ms.getConfigDescriptors());
        c.put( "class", JettyServer.class.getName());
        //ms.start( c);
        uy.com.r2.core.SvcCatalog.getCatalog().installModule( "JettyServer", c);
        uy.com.r2.core.Boot.main( args);
        //ms.server.join();  // Avoid stop while running
    }

}
