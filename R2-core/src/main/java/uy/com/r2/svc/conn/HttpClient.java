/* HTTPClient.java */
package uy.com.r2.svc.conn;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.SimpleService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import static uy.com.r2.core.api.ConfigItemDescriptor.*;
import uy.com.r2.core.api.Configuration;


/** HTTP client connector.
 * It invokes a remote service with getPayLoad() parameters nor get( "serializedJson")
 * and stores with put( "SerializedJson") the response.
 * This is a reference implementation !!!!.
 * This module should return NULL to put to wait the response, and use a 
 * second Thread to call onMessage when has the response.
 * @author G.Camargo
 */
public class HttpClient implements SimpleService {
    private static final Logger log = Logger.getLogger(HttpClient.class);
    private Map<String,String> svcUrl = new HashMap();
    private String defaultUrl = null;
    private int sleepTimeWait = 10;
    private String userAgentHeader = ""; 
    private String acceptHeader = ""; 
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Url.*", URL,
                "Service names and URL with $(var) to replace", null, ENVIRONMENT));
        l.add( new ConfigItemDescriptor( "Url", URL,
                "Default URL with $(var) to replace", null, ENVIRONMENT));
        l.add( new ConfigItemDescriptor( "DoPost", URL,
                "Use ethod POST (or GET) to call", "true"));
        l.add( new ConfigItemDescriptor( "SleepTimeWait", INTEGER,
                "Sleep time waiting for a response, in mS", "10"));
        l.add( new ConfigItemDescriptor( "UserAgentHeader", INTEGER,
                "User-agetn header on requests", ""));
        l.add( new ConfigItemDescriptor( "AccpetHeader", INTEGER,
                "User-agetn header on requests", "application/json"));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        svcUrl = cfg.getStringMap( "Url.*");
        defaultUrl = cfg.getString( "Url");
        sleepTimeWait = cfg.getInt( "SleepTimeWait");
        userAgentHeader = cfg.getString( "UserAgentHeader");
        acceptHeader = cfg.getString( "AccpetHeader");
    }

    /** Service call.
     * @param req Invocation message
     * @return SvcResponse message
     * @throws Exception Unexpected error, the responseCode will be lower than 0
     */
    @Override
    public SvcResponse call( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        int to = req.getTimeOut();
        long at = req.getAbsoluteTime();
        if( to > 0) {
            at += to;
        } else {
            at = Long.MAX_VALUE;
        }
        String r = invoke( cfg.getBoolean( "DoPost"), req, at);
        SvcResponse resp = new SvcResponse( 0, req);
        resp.put( "SerializedJson", r);
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
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    private String invoke( boolean doPost, SvcRequest req, long endTime) 
            throws Exception {
        log.debug("invoke " + (doPost? "POST": "GET") + req); 
        String strUrl = prepareUrl( doPost, req);
        try {
            URL url = new URL( strUrl);
            HttpURLConnection conn = ( HttpURLConnection)url.openConnection();
            conn.setRequestProperty( "User-Agent", userAgentHeader);
            conn.setRequestProperty( "Accept", acceptHeader);
            if( doPost) {
                // get serialized req
                String serializedParams = "" + req.get( "SerializedJson");
                if( serializedParams.isEmpty()) {
                    serializedParams = "" + req.get( "Serialized");
                }
                // process POST
                conn.setDoOutput( true);
                conn.setRequestMethod( "POST");
                DataOutputStream wr = new DataOutputStream( conn.getOutputStream());
                wr.writeBytes( serializedParams);
                wr.flush();
                wr.close();
            } else {
                conn.setRequestMethod( "GET");
            }
            if( conn.getResponseCode() != 200) {
                throw new Exception( "HTTP error code: " + conn.getResponseCode()); 
            }    
            InputStream is = conn.getInputStream();
            BufferedReader br = new BufferedReader( new InputStreamReader( is));
            StringBuilder sb = new StringBuilder();
            for( ; ;) {
                if( is.available() == 0) {
                    if( System.currentTimeMillis() > endTime) {
                        break;
                    }
                    Thread.sleep( sleepTimeWait);
                }
                String line = br.readLine();
                if( line == null) {
                    break;
                }    
                sb.append( line);
                sb.append( '\n');
            }
            conn.disconnect();
            log.trace( "resp: " + sb.toString());
            return sb.toString();
        } catch( Exception x) {
            throw new Exception( x + " invoking " + req.getServiceName() + " " + strUrl, x);
        }
    }

    private String prepareUrl( boolean doPost, SvcRequest req) throws Exception {
        String service = req.getServiceName();
        String strUrl = svcUrl.get( service);
        if( strUrl == null && defaultUrl.isEmpty()) {
            throw new Exception( "No URL defined on service " + service); 
        }
        if( strUrl == null) {   // If default Url used, add service name
            strUrl = defaultUrl;
        }
        // Find $(VAR) vars to replace un URL
        HashMap<String,String> vv = new HashMap();
        vv.put( "_Service", service);
        vv.put( "_ClientNode", req.getClientNode());
        for( int p = -1; strUrl.substring( p + 1).contains( "$("); ) {
            String q = strUrl.substring( p + 1);
            //log.trace( "q=" + q);
            int np = q.indexOf( "$(");
            p += np + 1; 
            //log.trace( "p=" + p + " " + strUrl.substring( 0, p));
            int ep = strUrl.substring( p).indexOf( ')');
            if( ep > 0) {
                ep += p;
                //log.trace( "pp=" + ep + " " + strUrl.substring( 0, ep));
                String var = strUrl.substring( p + 2, ep);
                //log.trace( "var=" + var);
                Object value = ( String)req.get( var);
                if( value != null) {
                    vv.put( var, "" + value);
                    log.debug( "vv(" + var + ")=" + value); 
                }
            }
        }
        // Replace all $(VAR)
        for( String k: vv.keySet()) {
            strUrl = strUrl.replace( "$(" + k + ")", vv.get( k));
        }
        if( !doPost) {  // GET mode: add other vars to URL
            StringBuilder urlParams = new StringBuilder();
            for( String k: req.getPayload().keySet()) {
                if( vv.containsKey( k)) {
                    continue;  // already present in URL
                }
                urlParams.append( ( urlParams.length() == 0? "": "&"));
                urlParams.append( k);  
                urlParams.append( "="); 
                urlParams.append( escapeAsUrl( req.get( k)));
            }
            strUrl += ( strUrl.contains( "?")? "&": "?") + urlParams.toString();
        }
        log.trace( "strUrl=" + strUrl); 
        return strUrl;
    }

    
    private String escapeAsUrl( Object obj) {
        StringBuilder s = new StringBuilder();
        for( char ch : ( "" + obj).toCharArray()) {
            if( isUnsafe( ch)) {
                s.append( '%');
                s.append( String.format( "%02X", ( int)ch));
            } else {
                s.append(ch);
            }
        }
        return s.toString();
    }        
    
    private static boolean isUnsafe( char ch) {
        if( ch > 128 || ch < 0) {
            return true;
        }
        return " %$&+,/:;=?@<>#%{}[]\"".indexOf( ch) >= 0;
    }
        
    /**/
    public static void main( String args[]) {
        Configuration c = new Configuration();
        HttpClient s = new HttpClient();
        uy.com.r2.core.api.AsyncService sj = new uy.com.r2.svc.tools.Json();
        try {
            uy.com.r2.core.ModuleInfo.setDefaultValues( s, c);
            c.put( "DoPost", "false"); 
            c.put( "Url", "http://localhost:8015/$(_Service)"); 
            System.out.println( "cfg= " + c);
            SvcRequest rq = new SvcRequest( "TEST", 0, 0, "GetModulesList", null, 0);
            rq.put( "Field1", "Value0001");
            rq.put( "Field2", "Value0002a");
            rq.add( "Field2", "Value0002b");
            rq = ( SvcRequest)sj.onRequest( rq, c);
            SvcResponse rq2 = s.call( rq, c);
            System.out.println( "rq  = |" + rq + "|");
            System.out.println( "rq2 = |" + rq2 + "|");
        } catch( Exception x) {
            x.printStackTrace( System.err);
        }    
    }
    /**/    
    
}


