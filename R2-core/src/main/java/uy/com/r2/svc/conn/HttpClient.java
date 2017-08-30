/* HTTPClient.java */
package uy.com.r2.svc.conn;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;


/** HTTP client connector.
 * It invokes a remote service with getPayLoad() parameters nor get( "serializedJson")
 * and stores with put( "SerializedJson") the response.
 * This is a reference implementation !!!!.
 * This module should return NULL to put to wait the response, and use a 
 * second Thread to call onMessage when has the response.
 * @author G.Camargo
 */
public class HttpClient implements AsyncService {
    private static final Logger log = Logger.getLogger(HttpClient.class);
    private Map<String,String> svcUrl = new HashMap();
    private String url = null;
    private int sleepTimeWait = 10;
    
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Service.*", ConfigItemDescriptor.URL,
                "Service names and URL to call", null));
        l.add( new ConfigItemDescriptor( "Url", ConfigItemDescriptor.URL,
                "Default URL to call", null));
        l.add( new ConfigItemDescriptor( "SleepWaitingTime", ConfigItemDescriptor.INTEGER,
                "Sleep time waiting for a response, in mS", "10"));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        svcUrl = cfg.getStringMap( "Service.*");
        url = cfg.getString( "Url");
        sleepTimeWait = cfg.getInt( "SleepTimeWait");
    }

    /** Service call.
     * @param req Invocation message
     * @return SvcResponse message
     * @throws Exception Unexpected error, the responseCode will be lower than 0
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        int to = req.getTimeOut();
        long at = req.getAbsoluteTime();
        if( to > 0) {
            at += to;
        } else {
            at = Long.MAX_VALUE;
        }
        String r = invoke( req, at);
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

    private String invoke( SvcRequest params, long endTime) 
            throws Exception {
        log.debug( "invoke " + params); 
        String service = params.getServiceName();
        String strUrl = svcUrl.get( service);
        if( strUrl == null) {
            strUrl = url;
        }
        if( strUrl == null) {
            log.warn( "No URL defined, " + service);
            throw new Exception( "No URL defined, " + service); 
        }
        log.trace( "strUrl=" + strUrl + " svc=" + service); 
        try {
            // Find $(KEY) vars to replace un URL
            HashMap<String,String> vv = new HashMap();
            int p = -1;
            for( ; ; ) {
                String q = strUrl.substring( p + 1);
                //log.trace( "q=" + q);
                int np = q.indexOf( "$(");
                if( np < 0) {
                    break;
                }
                p += np + 1; 
                //log.trace( "p=" + p + " " + strUrl.substring( 0, p));
                int ep = strUrl.substring( p).indexOf( ')');
                if( ep > 0) {
                    ep += p;
                    //log.trace( "pp=" + ep + " " + strUrl.substring( 0, ep));
                    String var = strUrl.substring( p + 2, ep);
                    //log.trace( "var=" + var);
                    String value = "" + params.get( var);
                    if( value == null) {
                        value = "";
                    }
                    vv.put( var, value);
                    log.debug( "vv(" + var + ")=" + value); 
                }
            }
            for( String k: vv.keySet()) {
                strUrl = strUrl.replace( "$(" + k + ")", vv.get( k));
            }
            // Add other parameters
            if( !strUrl.contains( "?")) {
                if( !strUrl.endsWith( "/")) {
                    strUrl += "/" + params.getServiceName();
                }
                strUrl += "?";
            }
            for( String k: params.getPayload().keySet()) {
                if( vv.containsKey( k)) {
                    continue;  // already present on URL
                }
                strUrl += "&" + k + "=" + params.get( k);
            }
            log.debug( "***** strUrl2=" + strUrl); 
            URL url = new URL( strUrl);
            HttpURLConnection conn = ( HttpURLConnection)url.openConnection();
            conn.setRequestMethod( "GET");
            conn.setRequestProperty( "User-Agent", "");
            conn.setRequestProperty( "Accept", "application/json");
            //log.trace( "conn.getResponseMessage=" + conn.getResponseMessage());
            if( conn.getResponseCode() != 200) {
                throw new RuntimeException( "Failed call remote service '" + service + "' " 
                        + strUrl + ", HTTP error code: " 
                        + conn.getResponseCode());
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
            log.warn( x + " invoking " + service + " " + strUrl, x);
            throw new Exception( x + " invoking " + service + " " + strUrl, x);
        }
    }

    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        throw new UnsupportedOperationException( "Not used."); 
    }
    
}


