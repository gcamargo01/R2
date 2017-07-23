/* PropertyDeployer.java */
package uy.com.r2.core.jstd;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.SvcCatalog;

/** Detects, install and un-install R2 service modules from properties files.
 * Reference implementation properties files deployer.
 * @author G.Camargo
 */
public class PropertiesDeployer implements Runnable {
    private static final Logger LOG = Logger.getLogger( PropertiesDeployer.class);
    private boolean stopping = false;
    private static enum FileStatus { FSNew, FSReload, FSIdle};

    private final SvcCatalog kernel = SvcCatalog.getCatalog();
    private File folder;
    private int wait = 5000;
    private TreeMap<String,FileInfo> filesMap = new TreeMap<String,FileInfo>();

    /** File status record */
    private static class FileInfo {
        private FileStatus status = FileStatus.FSNew;
        private boolean isThere = true;
        private long dateTime;
    }
    
    /** Set the deployer path.
     * @param path Path
     */
    public void setPath( String path) {
        LOG.trace( "set deploy path=" + path);
        folder = new File( path);
        LOG.debug( "deploy absolute path " + folder.getAbsolutePath());
    }

    /** Set the deploy wait in mS.
     * @param wait Wait time in mS in deploy loop
     */
    public void setWaitTime( int wait) {
        this.wait = wait;
    }
    
    /** Runnable method.
     */
    @Override
    public void run( ) {
        try {
            do {
                // Detect files changes
                for( String fn : filesMap.keySet()) {
                    filesMap.get( fn).isThere = false;
                }
                for( File fileEntry : folder.listFiles()) {
                    if( !fileEntry.isDirectory() &&
                            fileEntry.getName().endsWith( ".properties")) {
                        FileInfo fi = filesMap.get( fileEntry.getName()); 
                        if( fi == null) {
                            fi = new FileInfo();
                            fi.dateTime = fileEntry.lastModified();
                            filesMap.put( fileEntry.getName(), fi);
                        } else {
                            fi.isThere = true;
                            if( fi.dateTime != fileEntry.lastModified()) {
                                fi.status = FileStatus.FSReload;
                                fi.dateTime = fileEntry.lastModified();
                            }
                        }
                    }
                }
                // Undeploy deleted files 
                LinkedList<String> deletedFiles = new LinkedList<String>();
                for( String fn : filesMap.descendingKeySet()) {
                    if( !filesMap.get( fn).isThere) {
                        deletedFiles.add( fn);
                    }
                }
                for( String fn : deletedFiles) {
                    undeploy( fn);
                    filesMap.remove( fn);
                }
                // Update changed files
                for( String fn : filesMap.descendingKeySet()) {
                    if( filesMap.get( fn).status == PropertiesDeployer.FileStatus.FSReload) {
                        redeploy( fn);
                        filesMap.get( fn).status = PropertiesDeployer.FileStatus.FSIdle;
                    }
                }
                // Deploy new files 
                for( String fn : filesMap.keySet()) {
                    if( filesMap.get( fn).status == FileStatus.FSNew) {
                        deploy( fn);
                        filesMap.get( fn).status = FileStatus.FSIdle;
                    }
                }
                // Wait a second
                Thread.sleep( wait);
                if( kernel.isStopping()) {  
                    stopping = true;
                }
                //log.trace( ".");
            } while( !stopping);
            LOG.debug( "Stopping...");
        } catch( Exception x) {
            LOG.error( "Fatal error stop Deployer", x);
        }    
        for( String fn : filesMap.descendingKeySet()) {
            undeploy( fn);
        }        
        filesMap = new TreeMap();
    }
    
    /** Deploy service module.
     * @param fileName File name with extension
     */
    private void deploy( String fileName) {
        try {
            LOG.debug( "deploy " + fileName);
            Configuration cfg = loadCfg( fileName);
            String moduleName = file2ModuleName( fileName);
            if( getClass().getName().equals( cfg.getString( "class")) 
                    && cfg.getBoolean( "Shutdown")) {
                stop();
                return;
            }
            kernel.installModule( moduleName, cfg);
        } catch ( Throwable ex) {
            LOG.warn( "Deploy error on " + fileName + ", " + ex, ex );
        }
    }

    /** Undeploy service module.
     * @param fileName File name with extension
     */
    private void undeploy( String fileName) {
        try {
            LOG.debug( "undeploy " + fileName);
            String moduleName = file2ModuleName( fileName);
            kernel.uninstallModule( moduleName);
        } catch ( Throwable ex ) {
            LOG.warn( "Undeploy error on " + fileName + ", " + ex, ex );
        }
    }
    
    /** Redeploy a service module.
     * Attention: the class must remain the same, it's ignored.
     * @param fileName File name with extension
     */
    private void redeploy( String fileName) {
        try {
            LOG.debug( "redeploy " + fileName);
            String moduleName = file2ModuleName( fileName);
            Configuration cfg = loadCfg( fileName);
            kernel.updateConfiguration( moduleName, cfg);
        } catch ( Throwable ex ) {
            LOG.warn( "Redeploy error on " + fileName + ", " + ex, ex );
        }
    }
  
    private static String file2ModuleName( String n) {
        // Remove ".properties", if there is 
        if( n.endsWith( ".properties")) {
            n = n.substring( 0, n.length() - 11);
        }
        // Remove path, if there is one
        if( n.contains( File.separator)) {
            n = n.substring( n.lastIndexOf( File.separator) + 1);
        }
        // Remove numeric prefix ^[0-9]+[_-]  (used to order deploy)
        Pattern p = Pattern.compile( "^\\d+[_-]");
        Matcher m = p.matcher( n);
        if( m.find()) {
            n = n.substring( m.end());
        }
        return n;
    }

    private Configuration loadCfg( String fileName) throws Exception {
        Map<String,Object> cfg = new HashMap();
        String path = folder.getAbsolutePath();
        if( !path.endsWith( File.separator)) {
            path += File.separator;
        }
        InputStream is = new FileInputStream( path + fileName);
        Properties p = new Properties();
        p.load( is);
        is.close();
        for( String k: p.stringPropertyNames()) {
            cfg.put( k, p.getProperty( k));
        }
        LOG.trace( cfg.toString());
        return new Configuration( cfg);        
    }
    
    /** Stop system, undeploy all modules.
     * @throws Exception Unexpected error
     */
    public void stop() throws Exception {
        stopping = true;
        LOG.trace( "Stop");
    }
    
    /** Entry point to Deployer.
     * @param args Standard arguments
     */
    public static void main( String args[]) {
         PropertiesDeployer d = new PropertiesDeployer();
        d.setPath( ( args.length > 0) ? args[ 0]: ".");
        if( args.length > 1) {
            d.setWaitTime( Integer.parseInt( args[ 1]));
        }
        d.run();
    }

}
