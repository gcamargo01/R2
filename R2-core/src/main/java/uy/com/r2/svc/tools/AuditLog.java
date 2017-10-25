/* AuditLog.java */
package uy.com.r2.svc.tools;

import java.util.List;
import java.util.LinkedList;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;


/** Audit module, it records in a file the activity: Request and Responses. 
 * This module use to be the first one in services pipeline.
 * !!!! To do: It needs testing
 * @author G.Camargo
 */
public class AuditLog implements AsyncService {
    private static final Logger log = Logger.getLogger( AuditLog.class);
    private String fileName = "Audit_YYYY-MM-DD.log";
    private String realName = null;
    private String realPath = null;
    private String lastName = null;
    private FileWriter logWr = null;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "FileName", ConfigItemDescriptor.STRING, 
                "Log File with optional path prefix and date mask (YYYY-MM-DD)", 
                "Audit_YYYY-MM-DD.log"));
        return l;
    }
    
    private void setConfiguration( Configuration cfg) throws Exception {
        if( cfg.isChanged()) {
            fileName = cfg.getString( "FileName");
            // restart logging
            try {
                logWr.close();
            } catch( Exception x) { }
            logWr = null;
            cfg.resetChanged();
        }
    }

    /** Process a service call.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param req Invocation message from caller
     * @param cfg Module configuration
     * @return SvcRequest to dispatch to the next module or SvcResponse to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        log( req.toString());
        return req;
    }

    /** Process a response.
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcResponse message from next module
     * @param cfg Module configuration
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        log( resp.toString());
        return resp;
    }

    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> m = new HashMap();
        m.put( "AuditFile", realName);
        m.put( "AuditAbsolutePath", realPath);
        return m;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
        try {
            log( "--- Shutdown ---");
            logWr.flush();
        } catch ( Exception ex ) { }
    }

    private void log( String line) throws Exception {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd hh:mm:ss.SSS: ");
            StringBuilder sb = new StringBuilder( 256);
            sb.append( sdf.format( new Date()));
            sb.append( line.replace( "\n", "\\n"));
            sb.append( "\n");
            // Replace date in fileName
            String date = sb.substring( 0, 10);
            realName = fileName;
            int p = fileName.indexOf( "YYYY-MM-DD");
            if( p >= 0) {
                realName = fileName.replace( "YYYY-MM-DD", date);
            }
            if( !realName.equals( lastName)) {  // the name (or date) changed?
                lastName = realName;
                try {
                    logWr.close();
                } catch( Exception x) { }
                logWr = null;
            }
            log.trace( "log to " + realName + " : " + sb.toString());
            // re open file if its not open or name changes
            if( logWr == null) {
                File f = new File( realName);
                realPath = f.getAbsolutePath();
                logWr = new FileWriter( f, true);
                logWr.write( sdf.format( new Date()));
                logWr.write( "--- Start ---\n");
            }
            logWr.write( sb.toString());
            logWr.flush();
        } catch ( IOException ex) {
            log.warn( "Can't write audit file " + realName + " on " + realPath 
                    + " with: " + line, ex);
            throw new Exception( "Can't write audit file " + realName + " on " + realPath, ex);
        }
    }
    
}


