/* Json.java */
package uy.com.r2.svc.tools;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** Serialize to JSON and De-serialize from JSON.
 * @author G.Camargo
 */
public class Json implements AsyncService {
    public static final String SERIALIZED_JSON = "SerializedJson";
    public static final String RESULT_CODE = "ResultCode";
    private static final Logger log = Logger.getLogger( Json.class);
    private Gson mapper = new Gson();
    private boolean toSerial = true;
    private boolean procRequest = true;
    private boolean procResponse = true;
    private int parsedCount = 0;
    private int generatedCount = 0;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<ConfigItemDescriptor>();
        l.add( new ConfigItemDescriptor( "ToSerial", ConfigItemDescriptor.BOOLEAN,
                "Going to a serialized service, or not", "true"));
        l.add( new ConfigItemDescriptor( "ProcessRequest", ConfigItemDescriptor.BOOLEAN,
                "Process the Requests (default true)", "true"));
        l.add( new ConfigItemDescriptor( "ProcessResponse", ConfigItemDescriptor.BOOLEAN,
                "Process the Responses (default true)", "true"));
        return l;
    }

    /** Inject configuration to this module, (re)start, and reset statistics.
     * @param cfg Variable and value map
     * @throws Exception Unexpected error
     */
    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isUpdated()) {
            return;
        }
        toSerial = cfg.getBoolean( "ToSerial");
        procRequest = cfg.getBoolean( "ProcessRequest");
        procResponse = cfg.getBoolean( "ProcessResponse");
        // reset statistics
        if( cfg.getBoolean( "Reset")) {
            parsedCount = 0;
            generatedCount = 0;
        }
        cfg.clearUpdated();
    }

    /** Process a service call.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param cfg Module configuration
     * @param req Invocation message from caller
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        if( !procRequest) {
            return req;
        }
        if( toSerial) {
            // Add or replace a "Data" field with JSON
            req.put( SERIALIZED_JSON, toJSON( req.getPayload()));
        } else {
            // Take one field Data an serialize it
            Map<String, List<Object>> r;
            r = fromJSON( "" + req.get( SERIALIZED_JSON));
            // Add parsed params is better
            for( String k: r.keySet()) {
                req.getPayload().put( k, r.get( k));  // Already is a list
            }
        }
        return req;
    }

    /** Process a response.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param res SvcResponse message from next module
     * @param cfg Module configuration
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse res, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        if( !procResponse) {
            return res;
        }
        if( toSerial) {
            // Take one field Data an serialize it
            Map<String, List<Object>> r = fromJSON("" + res.get( SERIALIZED_JSON));
            // Remove SerializedJson and try to parse ResultCode
            r.remove( SERIALIZED_JSON);
            int rc = res.getResultCode();
            if( r.containsKey( RESULT_CODE) && rc == 0) {
                rc = Integer.parseInt( "" + r.get( RESULT_CODE).toArray()[ 0]);
                r.remove( RESULT_CODE);
            }
            res = new SvcResponse( rc, res.getRequest());
            res.getPayload().putAll( r);
        } else {
            // Add some fields
            Map<String,List<Object>> m = new HashMap( res.getPayload());
            List<Object> l = new ArrayList();
            l.add( "" + res.getResultCode());
            m.put( RESULT_CODE, l);
            // Add or replace a "SerialisexJson" field with JSON
            res.put( SERIALIZED_JSON, toJSON( m));
        }
        return res;
    }

    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> m = new HashMap();
        m.put( "ParsedChars", parsedCount);
        m.put( "GeneratedChars", generatedCount);
        return m;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    private String toJSON( Map<String, List<Object>> data) throws Exception {
        log.trace( "process toJSON");
        String js = "";
        if( data == null) {
            return js;
        }
        try {
            StringWriter sw = new StringWriter();
            mapper.toJson( data, sw);
            js = sw.toString();
            generatedCount += js.length();
        } catch( Exception x) {
            throw new Exception( "Error generate JSON <<<<" + data + ">>>> " + x, x);
        }
        return js;
    }

    private Map<String, List<Object>> fromJSON( String data) throws Exception {
        log.trace( "process fromJSON");
        Map<String,List<Object>> r = new HashMap<String,List<Object>>();
        try {
            StringReader sr = new StringReader( data);
            Map<String,List<Object>> t; 
            t = (Map<String,List<Object>>)mapper.fromJson( sr, r.getClass());
            if( t != null) {   // Avoid return null
                r = t;
            }
            parsedCount += data.length();
        } catch( Exception x) {
            throw new Exception( "Error parsing JSON <<<<" + data + ">>>> " + x, x);
        }
        return r;
    }

    /** Minimal Test *
    public static void main( String args[]) {
        try {
            Json jj = new Json();
            SvcMessage m = new SvcRequest( "",  0, 0, "", null, 0);
            m.add( "data1", "one");
            m.add( "data2", "two");
            m.add( "data2", "two-2");
            String j = jj.toJSON( m.getPayload());
            System.out.println( "toJson:" + j);
            System.out.println( "fromJson: " + jj.fromJSON( j));
        } catch( Exception ex) { 
            ex.printStackTrace();
        }
    }
    **/
    
}


