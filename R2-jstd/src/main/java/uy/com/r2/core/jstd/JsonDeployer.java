/* JsonDeployer.java */
package uy.com.r2.core.jstd;

import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import com.google.gson.Gson;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.SvcCatalog;

/** Detects, install and un-install R2 service modules from JSON files.
 * Reference implementation JSON files deployer.
 * @author G.Camargo
 */
public class JsonDeployer implements Runnable {
    private static final Logger LOG = Logger.getLogger(JsonDeployer.class);
    private static enum FileStatus { FSNew, FSReload, FSIdle};

    private boolean stopping = false;
    private final SvcCatalog catalog = SvcCatalog.getCatalog();
    private File folder = new File( ".");
    private int wait = 5000; 
    private TreeMap<String,FileInfo> filesMap = new TreeMap();
    
    /** File status record */
    private static class FileInfo {
        long dateTime;
        FileStatus status = FileStatus.FSNew;
        boolean isThere = true;
    }
    
    /** Set the deploy path.
     * @param path Path where scan files to deploy
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

    /** Running deploy method. */
    @Override
    public void run( ) {
        try {
            do {
                // Detect files changes
                for( String fn : filesMap.keySet()) {
                    filesMap.get( fn).isThere = false;
                }
                if( folder.listFiles() != null) {
                    for( File fileEntry : folder.listFiles()) {
                        if( !fileEntry.isDirectory() && 
                                fileEntry.getName().toLowerCase().endsWith( ".json")) {
                            FileInfo fi = filesMap.get( fileEntry.getName()); 
                            if( fi == null) {  // New file
                                fi = new FileInfo();
                                fi.dateTime = fileEntry.lastModified();
                                filesMap.put( fileEntry.getName(), fi);
                            } else {  // already there, changed?
                                fi.isThere = true;
                                if( fi.dateTime != fileEntry.lastModified()) {
                                    fi.status = FileStatus.FSReload;
                                    fi.dateTime = fileEntry.lastModified();
                                }
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
                    filesMap.remove( fn);
                    undeploy( fn);
                }
                // Update changed files
                for( String fn : filesMap.descendingKeySet()) {
                    if( filesMap.get( fn).status == FileStatus.FSReload) {
                        filesMap.get( fn).status = FileStatus.FSIdle;
                        redeploy( fn);
                    }
                }
                // Deploy new files 
                for( String fn : new TreeSet<String>( filesMap.keySet())) {
                    if( filesMap.get( fn).status == FileStatus.FSNew) {
                        filesMap.get( fn).status = FileStatus.FSIdle;
                        deploy( fn);
                    }
                }
                // Wait a second
                Thread.sleep( wait);
                if( catalog.isStopping()) {  
                    stopping = true;
                }
            } while( !stopping);
        } catch( Exception x) {
            LOG.error( "Fatal error stop Deployer" , x);
        }
        // Undeploy in reverse order
        LOG.info( "Undeploy all...");
        for( String fn : filesMap.descendingKeySet()) {
            undeploy( fn);
        }
        filesMap = new TreeMap();
        LOG.debug( "Stop.");
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
                filesMap.remove( fileName);
                return;
            }
            catalog.installModule( moduleName, cfg);
        } catch ( Throwable ex) {
            LOG.warn( "Deploy error on '" + fileName + "', " + ex, ex );
        }
    }

    /** Undeploy service module.
     * @param fileName File name with extension
     */
    private void undeploy( String fileName) {
        try {
            LOG.debug( "undeploy " + fileName);
            String moduleName = file2ModuleName( fileName);
            catalog.uninstallModule( moduleName);
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
            catalog.updateConfiguration( moduleName, cfg);
        } catch ( Throwable ex ) {
            LOG.warn( "Redeploy error on '" + fileName + "', " + ex, ex );
        }
    }
    
    private static String file2ModuleName( String n) {
        // Remove ".JSON", if there is 
        if( n.toUpperCase().endsWith( ".JSON")) {
            n = n.substring( 0, n.length() - 5);
        }
        // Remove path, if there is one
        if( n.contains( File.separator )) {
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
        String path = folder.getAbsolutePath();
        if( !path.endsWith( File.separator)) {
            path += File.separator;
        }
        FileReader fr = new FileReader( path + fileName);
        Gson gson = new Gson();
        BufferedReader reader = new BufferedReader( fr);
        @SuppressWarnings( "unchecked")
        Configuration cfg = new Configuration( gson.fromJson( reader, Map.class));
        fr.close();
        LOG.trace( cfg.toString());
        return cfg;        
    }
    
    /** Stop deployer, undeploy all deployed modules.
     * @throws Exception Unexpected error
     */
    public void stop() throws Exception {
        LOG.info( "Stop requested...");
        stopping = true;
    }
    
    /** Entry point to Deployer.
     * @param args Standard arguments
     */
    public static void main( String args[]) {
        JsonDeployer d = new JsonDeployer();
        d.setPath( ( args.length > 0) ? args[ 0]: ".");
        if( args.length > 1) {
            d.setWaitTime( Integer.parseInt( args[ 1]));
        }
        d.run();
    }

}

