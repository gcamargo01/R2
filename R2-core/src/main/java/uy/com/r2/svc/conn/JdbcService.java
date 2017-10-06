/* JdbcService.java */
package uy.com.r2.svc.conn;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.SvcMessage;


/** JDBC service module.
 * Its a very interesting idea to make a service just writing its query,
 * but it need some work to be really useful, as it can be.
 * This is a reference implementation !!!!.
 * It should support stored procedures.
 * @author G.Camargo
 */
public class JdbcService implements AsyncService {
    private static final Logger log = Logger.getLogger(JdbcService.class);
    private String driverClass = "";
    private String url = "";
    private String user = "";
    private String password = "";
    private Map<String,ServiceInfo> svcs = new HashMap();
    private Connection conn = null;
    private boolean firstTime = true;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Driver", ConfigItemDescriptor.STRING,
                "JDBC Driver class", null));
        l.add( new ConfigItemDescriptor( "URL", ConfigItemDescriptor.STRING,
                "JDBC URL", null));
        l.add( new ConfigItemDescriptor( "User", ConfigItemDescriptor.STRING,
                "JDBC User", null));
        l.add( new ConfigItemDescriptor( "Password", ConfigItemDescriptor.STRING,
                "JDBC Password", null));
        l.add( new ConfigItemDescriptor( "Service.*.SQL", ConfigItemDescriptor.STRING,
                "Service and SQL sentence", null));
        l.add( new ConfigItemDescriptor( "Service.*.Params", ConfigItemDescriptor.STRING,
                "Service and SQL parameters separated by coma (,)", null));
        l.add( new ConfigItemDescriptor( "Service.*.RowName", ConfigItemDescriptor.STRING,
                "Name of a tuple", "Row"));
        return l;
    }
    
    /** Inject configuration to this module.
     * @param cfg Variable and value map
     * @throws Exception Unexpected error
     */
    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isChanged()) {
            return;
        }
        firstTime = false;
        driverClass = cfg.getString( "Driver");
        url = cfg.getString( "URL");
        user = cfg.getString( "User");
        password = cfg.getString( "Password");
        if( cfg.getBoolean( "SeptUpTest")) {
            getConnection();  // Try!
        }  
        Map<String,String> svcsSQL = cfg.getStringMap( "Service.*.SQL");
        Map<String,String> svcsParams = cfg.getStringMap( "Service.*.Params");
        Map<String,String> svcsRowName = cfg.getStringMap( "Service.*.RowName");
        svcs = new HashMap();
        for( String k: svcsSQL.keySet()) {
            ServiceInfo si = new ServiceInfo();
            si.sqlSentence = svcsSQL.get( k);
            String pns = svcsParams.get( k);
            si.paramNames = ( pns != null)? svcsParams.get( k).split( ","): new String[ 0];
            si.rowName = svcsRowName.get( k);
            if( si.rowName == null) {
                si.rowName = "Row";
            }
            svcs.put( k, si);
            log.debug( "Service " + k + " " + si.sqlSentence);
        }
        cfg.resetChanged();
    }

    /** Service call.
     * @param req Invocation message
     * @param cfg Module configuration
     * @return SvcResponse message
     * @throws Exception Unexpected error, the responseCode will be lower than 0
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        String svcName = req.getServiceName();
        ServiceInfo si = svcs.get( svcName);
        if( si == null) {
            //throw new Exception( SvcResponse.MSG_INVALID_SERVICE + req.getServiceName());
            return req;
        }
        Map<String,List<Object>> r = execute( req.getPayload(), si);
        SvcResponse resp = new SvcResponse( r, 0, req);
        return resp;
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
        for( String s: svcs.keySet()) {
            if( svcs.get( s).uses > 0) {
                map.put( s + ".Uses", "" + svcs.get( s).uses);
                map.put( s + ".Errors", "" + svcs.get( s).errors);
                map.put( s + ".AvgTime", "" + ( svcs.get( s).time / svcs.get( s).uses));
            }    
        }
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        try {
            conn.close();
        } catch( Exception x) { }
        conn = null;
    }

    private Connection getConnection() throws Exception {
        if( conn == null) {
            Class.forName( driverClass);
            conn = DriverManager.getConnection( url, user, password);
        }
        return conn;
    }
    
    private Map<String,List<Object>> execute( Map<String,List<Object>> input, ServiceInfo si) 
            throws Exception {
        ++si.uses;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            long t0 = System.currentTimeMillis();
            log.trace( "sqlSentence=" + si.sqlSentence);
            ps = getConnection().prepareStatement( si.sqlSentence);
            for( int i = 0; i < si.paramNames.length; ++i) {
                Object o = null;
                try {
                    o = input.get( si.paramNames[ i]).get( 0);
                } catch( Exception xx) { }
                ps.setObject( i + 1, o);
                log.trace( "arg" + (i + 1) + "=" + o);
            }
            if( ps.execute()) {
                rs = ps.getResultSet();
            }
            ResultSetMetaData rsmd = rs.getMetaData();
            Map<String,List<Object>> resp = new LinkedHashMap();
            while( rs != null && rs.next()) {
                Map<String,List<Object>> row = new LinkedHashMap();
                for( int i = 0; i < rsmd.getColumnCount(); ++i) {
                    SvcMessage.addToMap( row, rsmd.getColumnName( i + 1), rs.getString( i + 1));
                }
                SvcMessage.addToMap( resp, si.rowName, row);
            }    
            si.time += ( int)(System.currentTimeMillis() - t0);
            return resp;
        } catch( Exception x) {
            ++si.errors;
            throw new Exception( "" + x + " url= " + url + " user=" + user, x);
        } finally {   // Allways release 
            try { rs.close(); } catch( Exception xx) { }
            try { ps.close(); } catch( Exception xx) { }
        }
    }

    
    /** Process a response from another modules
     * @throws Exception Unexpected
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        if( resp.getRequest().getServiceName().equals( Dispatcher.SVC_GETSERVICESLIST)) {
            for( String k: svcs.keySet()) {
                resp.add( "Services", k);
            }
        }
        return resp;
    }
    
    private class ServiceInfo {
        String sqlSentence;
        String paramNames[];
        String rowName;
        int uses = 0;
        int errors = 0;
        int time = 0;
    }
    
}


