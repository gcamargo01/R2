/* ToHtml.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
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
    private static final Logger LOG = Logger.getLogger(ToHtml.class);
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
            for( String k: rq.getPayload().keySet()) {
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
        boolean modlist = resp.getRequest().getServiceName().equals( "GetModulesList");
        sb.append( "<div class=\"divTable\">\n"); 
        for( String k: resp.getPayload().keySet()) {
            sb.append( " <div class=\"divRow\">\n");
            sb.append( "  <div class=\"divHead\">\n");
            sb.append( k);
            sb.append( "  </div>\n");
            sb.append( "  <div class=\"divCell\">\n");
            List l = resp.getPayload().get( k);
            if( modlist) {
                sb.append( "<div class=\"divTable\">\n"); 
            }
            for( Object o: l) {
                if( !modlist) {
                    toHtml( o, sb);
                    sb.append( "<br>");
                } else {
                    String m = "" + o;
                    sb.append( " <div class=\"divRow\">\n");
                    sb.append( "  <div class=\"divCell0\">\n");
                    sb.append( m);
                    sb.append( "  </div>\n"); 
                    sb.append( "  <div class=\"divCell0\">\n");
                    sb.append( "<form action=\"/GetModuleConfig\" style=\"display: inline;\"><input type=\"hidden\" name=\"Module\" value=\"" 
                            + m + "\"><input type=\"submit\" value=\"GetModuleConfig\"></form>\n"); 
                    sb.append( "  </div>\n"); 
                    sb.append( "  <div class=\"divCell0\">\n");
                    sb.append( "<form action=\"/GetModuleDetailedConfig\" style=\"display: inline;\"><input type=\"hidden\" name=\"Module\" value=\""
                            + m + "\"><input type=\"submit\" value=\"GetModuleDetailedConfig\"></form>\n"); 
                    sb.append( "  </div>\n"); 
                    sb.append( "  <div class=\"divCell0\">\n");
                    sb.append( "<form action=\"/GetModuleStatus\" style=\"display: inline;\"><input type=\"hidden\" name=\"Module\" value=\""
                            + m + "\"><input type=\"submit\" value=\"GetModuleStatus\"></form>\n"); 
                    sb.append( "  </div>\n"); 
                    sb.append( " </div>\n");   // End row
                }
            }
            if( modlist) {
                sb.append( "</div>\n");  // End table
            }
            sb.append( "  </div>\n");
            sb.append( " </div>\n"); 
        }
        sb.append( "</div>\n"); 
        // Commands
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
        // Foot
        sb.append( "</body></html>");
        resp.put( "Serialized", sb.toString());
        return resp;
    }
    
    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        return null;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }
    
    private void toHtml( Object o, StringBuilder sb) {
        boolean firstOne = true;
        if( o == null) {
            sb.append( "(NULL)");
        } else if( o instanceof List) {
            for( Object e: ( List)o) {
                if( firstOne) {
                    firstOne = false;
                } else {
                    sb.append( "<br/>");
                }
                toHtml( e, sb);
            }        
        } else if( o instanceof Set) {
            for( Object e: ( Set)o) {
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
            for( Object k: m.keySet()) {
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
            sb.append( "" + o);
        }
    }
    

}


