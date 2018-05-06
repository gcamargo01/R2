/* JavaScript.java */
package uy.com.r2.svc.tools;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;

/** JavaScript service execution module. 
 * WORK IN PROGRESS!!!!
 * @author G.Camargo
 */
public class JavaScript implements AsyncService {
    private final static Logger LOG = Logger.getLogger( JavaScript.class);
    
    private Invocable inv = null;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "Script", ConfigItemDescriptor.STRING, 
                "JavaScript source code to execute"));
        l.add( new ConfigItemDescriptor( "SourceFile", ConfigItemDescriptor.STRING, 
                "File name of JavaScript file to execute"));
        return l;
    }

    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isUpdated()) {
            return;
        }
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine engine = sem.getEngineByName( "JavaScript");
        // read scripts files
        String s = cfg.getString( "Script");
        if( s != null && !s.isEmpty()) {
            engine.eval( s);
        }
        String f = cfg.getString( "SurceFile");
        if( f != null && !f.isEmpty()) {
            BufferedReader br = Files.newBufferedReader( Paths.get( f));
            engine.eval( br);
            br.close();
        }
        inv = (Invocable)engine;
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
        setConfiguration( cfg);
        inv.invokeFunction( "onRequest_" + req.getServiceName(), req);
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
        setConfiguration( cfg);
        inv.invokeFunction( "onResponse_" + resp.getRequest().getServiceName(), resp);
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

    /*
    public static void main( String args[]) {
        Configuration c = new Configuration();
        JavaScript s = new JavaScript();
        try {
            uy.com.r2.core.ModuleInfo.setDefaultValues( s, c);
            //System.out.println( "cfg= " + c);
            c.put( "Script", 
                    "function requestService( rq) { \n" + 
                    "  print( rq); \n" + 
                    "  rq.put( \"Field2\", \"ValueTwo\" + rq.get( \"Field\")); \n" + 
                    "  print( rq); \n" + 
                    "}");
            s.setConfiguration( c);
            SvcRequest rq = new SvcRequest( "CLNT_TST", 0, 0, "Service", null, 0);
            rq.put( "Field", "Value");
            System.out.println( "rq= " + rq);
            System.out.println( "rs= " + s.onRequest( rq, c));
        } catch( Exception x) {
            x.printStackTrace( System.err);
        }    
    }
    */
    
}


