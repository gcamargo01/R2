/* ToHtml.java */
package uy.com.r2.svc.tools;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;


/** HTML response formatter to make human readable and help send commands.
 * This is a minimal HTML generator. 
 * @author G.Camargo
 */
public class ToHtml implements AsyncService {
    private static final Logger LOG = Logger.getLogger( ToHtml.class);
    private static final String STYLES = 
            ".divTable{ display: table; }\n" +
            ".divRow{ display: table-row; }\n" +
            ".divHead{ border: 1px solid #999999; display: table-cell; padding: 3px 10px; background-color: #EEE; }\n" +
            ".divCell{ border: 1px solid #999999; display: table-cell; padding: 3px 10px; }\n" +
            ".divCell0{ border: 0px solid #999999; display: table-cell; padding: 3px 10px; }\n";
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        //l.add( new ConfigItemDescriptor( "", ConfigItemDescriptor.STRING,
        //        "", null));
        return l;
    }
    
    /** Invocation dispatch phase.
     * It may: <br>
     * (1) create and return a SvcResponse itself, <br>
     * (2) return a SvcRequest to dispatch to the next service module, or <br>
     * (3) return NULL when there aren't next module to call, or <br>
     * (4) throw a Exception to explicit set the module that originates <br>
     * the failure.
     * @param req Invocation message from caller
     * @param cfg Module configuration
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        if( req.getServiceName().equals( SvcDeployer.SVC_SETMODULECONFIG)) {
            if( req.get( SvcDeployer.TAG_ACTUALCONFIG) != null) {
                String c = "" + req.get( SvcDeployer.TAG_ACTUALCONFIG);
                // Parse in key = value \n
                BufferedReader reader = new BufferedReader( new StringReader( c));
                String l;
                while ( ( l = reader.readLine()) != null) {
                    if( l.contains( "=")) {
                        String s[] = l.split( "=");
                        req.put( s[ 0], s[ 1]);
                    }
                }
                // Remove parsed input tag
                req.getPayload().remove( SvcDeployer.TAG_ACTUALCONFIG);
            }
        }
        return req;
    }

    /** Process a response phase.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcResponse message from next module, or null (no next)
     * @param cfg Module configuration
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        StringBuilder sb = new StringBuilder();        
        sb.append( "<html>\n");
        // Title
        sb.append( "<head><title>"); 
        sb.append( resp.getRequestId()); 
        sb.append( " : "); 
        sb.append( resp.getResultCode()); 
        sb.append( "</title><style>");
        sb.append( STYLES);
        sb.append( "</style></head><body>\n");
        // Upper area
        SvcRequest rq = resp.getRequest();
        sb.append( "<h4>Request</h4><h2>");
        sb.append( rq.getServiceName());
        sb.append( "</h2>");
        sb.append( "From client Node: ");
        sb.append( rq.getClientNode());
        sb.append( " &nbsp; Ses.nr.: ");
        sb.append( rq.getSessionNr());
        sb.append( " &nbsp; Rq.nr.: ");
        sb.append( rq.getNodeRqNr());
        sb.append( " &nbsp; Time Out: ");
        sb.append( rq.getTimeOut());
        sb.append( "<br/>");
        // 
        if( !rq.getPayload().isEmpty()) {
            sb.append( "<div class=\"divTable\">\n"); 
            for( String k: new TreeSet<String>( rq.getPayload().keySet())) {
                sb.append( " <div class=\"divRow\">\n");
                sb.append( "  <div class=\"divHead\">\n");
                sb.append( k);
                sb.append( "  </div>\n"); 
                sb.append( "  <div class=\"divCell\">\n");
                List l = rq.getPayload().get( k);
                for( Object o: l) {
                    sb.append( "" + o);
                    sb.append( "  <br/>\n"); 
                }
                sb.append( "  </div>\n");  
                sb.append( " </div>\n");  // end Row
            }
            sb.append( "</div>\n");  // End table
        }
        // 
        sb.append( "<p/>&nbsp;<p/><h4>Response</h4>Result code: "); 
        sb.append( resp.getResultCode()); 
        sb.append( " &nbsp; Time: ");
        sb.append( resp.getResponseTime());
        sb.append( "<br/>");
        // Content
        sb.append( "<div class=\"divTable\">\n"); 
        for( String k: new TreeSet<String>( resp.getPayload().keySet())) {
            sb.append( " <div class=\"divRow\">\n");
            sb.append( "  <div class=\"divHead\">\n");
            sb.append( k);
            sb.append( "  </div>\n");
            sb.append( "  <div class=\"divCell\">\n");
            toHtml( resp.getPayload().get( k), sb);
            sb.append( "  </div>\n");
            sb.append( " </div>\n"); 
        }
        sb.append( "</div>\n"); 
        // Foot - Gloabl Commands
        sb.append( "<p/>&nbsp;<p/>\n"); 
        sb.append( "<form action=\"/GetServersList\"  style=\"display: inline;\"><input type=\"submit\" value=\"GetServersList\"></form>\n"); 
        sb.append( "&nbsp;"); 
        sb.append( "<form action=\"/GetModulesList\"  style=\"display: inline;\"><input type=\"submit\" value=\"GetModulesList\"></form>\n"); 
        sb.append( "&nbsp;"); 
        sb.append( "<form action=\"/GetServicesList\" style=\"display: inline;\"><input type=\"submit\" value=\"GetServicesList\"></form>\n"); 
        sb.append( "&nbsp;"); 
        sb.append( "<form action=\"/GetMasterServer\" style=\"display: inline;\"><input type=\"submit\" value=\"GetMasterServer\"></form>\n"); 
        sb.append( "&nbsp;"); 
        sb.append( "<form action=\"/Shutdown\"        style=\"display: inline;\"><input type=\"submit\" value=\"Shutdown\"></form>\n");
        // Modules actions
        if( resp.getResultCode() == 0) {
            if( rq.getServiceName().equals( SvcDeployer.SVC_GETMODULELIST)) {
                sb.append( "<p/>\n"); 
                sb.append( "<div class=\"divTable\">\n"); 
                for( String k: new TreeSet<String>( resp.getPayload().keySet())) {
                    if( k.equals( "SerializedJson")) {
                        continue;
                    }
                    List l = resp.getPayload().get( k);
                    for( Object o: l) {
                        String m = "" + o;
                        sb.append( " <div class=\"divRow\">\n");
                        sb.append( "  <div class=\"divCell0\">\n");
                        sb.append( m);
                        sb.append( " :  </div>\n"); 
                        sb.append( "  <div class=\"divCell0\">\n");
                        sb.append( "<form action=\"/GetModuleStatus\" style=\"display: inline;\"><input type=\"hidden\" name=\"Module\" value=\""
                                + m + "\"><input type=\"submit\" value=\"GetModuleStatus\"></form>\n"); 
                        sb.append( "  </div>\n"); 
                        sb.append( "  <div class=\"divCell0\">\n");
                        sb.append( "<form action=\"/GetModuleConfig\" style=\"display: inline;\"><input type=\"hidden\" name=\"Module\" value=\"" 
                                + m + "\"><input type=\"submit\" value=\"GetModuleConfig\"></form>\n"); 
                        sb.append( "  </div>\n"); 
                        sb.append( " </div>\n");   // End row
                    }
                }
                sb.append( "</div>\n");  // End table

            } else if( rq.getServiceName().equals( SvcDeployer.SVC_GETMODULECONFIG)) {

                String m = "" + rq.get( "Module");
                String v = "";
                Map<String,String> cm = ( Map)resp.get( SvcDeployer.TAG_ACTUALCONFIG);
                for( String ck: cm.keySet()) {
                    v += ck + "=" + cm.get(  ck) + "\n";
                }
                sb.append( "<p/>\n"); 
                sb.append( m);
                sb.append( " configuration : <br/>\n"); 
                sb.append( "<form action=\"/SetModuleConfig\">");
                sb.append( "<input type=\"hidden\" name=\"Module\" value=\"");
                sb.append( m);
                sb.append( "\"><textarea name=\"" + SvcDeployer.TAG_ACTUALCONFIG + "\" rows=\"16\" cols=\"80\" >");
                sb.append( v);
                sb.append( "</textarea><br/><br/>"); 
                sb.append( "<input type=\"submit\" value=\"SetModuleConfig\">\n"); 
                sb.append( "</form><p>\n");  
                sb.append( "<form action=\"/PersistConfig\"><input type=\"submit\" value=\"PersistConfig\"></form>\n");  

            } else if( rq.getServiceName().equals( SvcManager.SVC_GETSERVERSLIST)) {

                sb.append( "<p/>\n"); 
                sb.append( "<div class=\"divTable\">\n"); 
                for( String s: new TreeSet<String>( resp.getPayload().keySet())) {
                    if( s.equals( "SerializedJson")) {
                        continue;
                    }
                    sb.append( " <div class=\"divRow\">\n");
                    sb.append( "  <div class=\"divCell0\">\n");
                    sb.append( s);  
                    sb.append( " :  </div>\n");  
                    sb.append( "  <div class=\"divCell0\">\n");
                    sb.append( "<form action=\"/SetMasterServer\">");
                    sb.append( "<input type=\"hidden\" name=\"Server\" value=\"");
                    sb.append( s);
                    sb.append( "\"><input type=\"submit\" value=\"SetMasterServer\">\n"); 
                    sb.append( "</form><br/>\n");  
                    sb.append( "  </div>\n");  
                    sb.append( " </div>\n");  // Row end
                }
                sb.append( "</div>\n");  // Table end

            }
        }
        sb.append( "</body></html>");
        resp.put( "SerializedHtml", sb.toString());
        return resp;
    }
    
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String, Object> map = new HashMap();
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
    
    private void toHtml( Object o, StringBuilder sb) {
        //LOG.debug( "toHTML" + o);
        boolean firstOne = true;
        if( o == null) {
            sb.append( "(NULL)");
        } else if( o instanceof List) {
            for( Object e:( List)o) {
                if( firstOne) {
                    firstOne = false;
                } else {
                    sb.append( "<br/>");
                }
                toHtml( e, sb);
            }        
        } else if( o instanceof Set) {
            for( Object e: new TreeSet( ( Set)o)) {
                if( firstOne) {
                    firstOne = false;
                } else {
                    sb.append( "<br/>");
                }
                toHtml( e, sb);
            }
        } else if( o instanceof Map) {
            Map m = (Map)o;
            sb.append( "<div class=\"divTable\">\n"); 
            for( Object k: new TreeSet( m.keySet())) {
                sb.append( " <div class=\"divRow\">\n");
                sb.append( "  <div class=\"divCell0\">\n");
                sb.append( "" + k);
                sb.append( "  </div>"); // EndCell
                sb.append( "  <div class=\"divCell0\">\n");
                toHtml( m.get( k), sb);
                sb.append( "  </div>"); // EndCell
                sb.append( " </div>"); // EndRow
            }
            sb.append( "</div>");
        } else {
            //LOG.debug( "toHTML class=" + o.getClass());
            sb.append( "" + o);
        }
    }
    
}


