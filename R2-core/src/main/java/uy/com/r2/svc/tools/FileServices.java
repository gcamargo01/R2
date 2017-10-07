/* FilelServices.java */
package uy.com.r2.svc.tools;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.security.MessageDigest;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.Dispatcher;
import uy.com.r2.core.api.SvcMessage;

/** File IO services: FileRead, FileWrite, FileList, FileRename.
 * Other parameters: Path, Name, Block, Offset, Length, NewName
 * @author G.Camargo
 */
public class FileServices implements AsyncService {
    private final static Logger log = Logger.getLogger( FileServices.class);
    private final static String SVC_LIST     = "ListFiles";
    private final static String SVC_LISTDIRS = "ListDirs";
    private final static String SVC_SUM      = "GetChkSum";
    private final static String SVC_READ     = "ReadFile";
    private final static String SVC_REN      = "RenameFile";
    private final static String SVC_WRITE    = "WriteFile";
    private final static String[] SERVICES = {
            SVC_LIST, SVC_LISTDIRS, SVC_SUM, SVC_READ, SVC_REN, SVC_WRITE
    };

    private int bufferSize = 10240;
    private String defaultPath = null;

    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<ConfigItemDescriptor>();
        l.add( new ConfigItemDescriptor( "DefaultPath", ConfigItemDescriptor.STRING, 
                "Default path to read/write", null));
        l.add( new ConfigItemDescriptor( "BufferSize", ConfigItemDescriptor.INTEGER, 
                "Default size in bytes to read in a block", "10240"));
        return l;
    }
    
    /** Inject configuration to this module, (re)start, and reset statistics.
     * @param cfg Variable and value map
     * @throws Exception Unexpected error
     */
    private void setConfiguration( Configuration cfg) throws Exception {
        defaultPath = cfg.getString( "DefaultPath", System.getProperty( "user.dir"));
        bufferSize = cfg.getInt( "BufferSize", 10240);
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
        boolean write = false;
        switch( req.getServiceName()) {
            case SVC_WRITE:
                write = true;
            case SVC_READ:
                Object oPath = req.get( "Path");
                String path = ( oPath == null)? defaultPath: "" + oPath;
                String name = "" + req.get( "Name");
                int pos = 0;
                Object oPos = req.get( "Offset");
                if( oPos != null) {
                    pos = Integer.parseInt( "" + oPos);
                }
                int len = bufferSize;
                Object oLen = req.get( "Length");
                if( oLen != null) {
                    len = Integer.parseInt( "" + oLen);
                }
                byte buff[];
                SvcResponse resp = new SvcResponse( 0, req);
                try {
                    if( !write) {  // read
                        buff = read( path, name, pos, len);
                        resp.put( "Block", toStr( buff));
                    } else {  // write
                        buff = toBin( "" + req.get( "Block"));
                        len = buff.length;
                        write( path, name, buff, pos, len); 
                    }
                } catch( Exception xx) {
                    return new SvcResponse( "Failed " + req.getServiceName(), 1, xx, req);
                }
                return resp;
            case SVC_SUM:
                oPath = req.get( "Path");
                path = ( oPath == null)? defaultPath: "" + oPath;
                name = "" + req.get( "Name");
                pos = 0;
                len = bufferSize;
                resp = new SvcResponse( 0, req);
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.reset();
                try {
                    for( ; ;) {
                       buff = read( path, name, pos, len);
                       if( buff.length == 0) {
                           break;
                       }
                       md.update( buff);
                       pos += bufferSize;
                    };
                    resp.put( "ChkSum", new String( Hex.encodeHex( md.digest())));
                } catch( Exception xx) {
                    log.debug( "" + xx, xx);
                    return new SvcResponse( "Failed " + req.getServiceName(), 1, xx, req);
                }
                return resp;
            case SVC_REN:
                oPath = req.get( "Path");
                path = ( oPath == null)? defaultPath: "" + oPath;
                name = "" + req.get( "Name");
                String newName = "" + req.get( "NewName");
                try {
                    rename( path, name, newName);                    
                } catch( Exception xx) {
                    return new SvcResponse( "Failed " + req.getServiceName(), 1, xx, req);
                }
                return new SvcResponse( 0, req);
            case SVC_LIST:
                oPath = req.get( "Path");
                path = ( oPath == null)? defaultPath: "" + oPath;
                Map<String,Map<String,Object>> dir = listFiles( path);
                try {
                    SvcResponse r = new SvcResponse( 0, req);
                    for( String f: dir.keySet()) {
                       r.add( f, dir.get( f));
                    }    
                    return r;
                } catch( Exception xx) {
                    return new SvcResponse( "Failed " + req.getServiceName(), -1, xx, req);
                }  
            case SVC_LISTDIRS:
                oPath = req.get( "Path");
                path = ( oPath == null)? defaultPath: "" + oPath;
                dir = listDirs( path);
                try {
                    SvcResponse r = new SvcResponse( 0, req);
                    for( String f: dir.keySet()) {
                       r.add( f, dir.get( f));
                    }    
                    return r;
                } catch( Exception xx) {
                    return new SvcResponse( "Failed " + req.getServiceName(), -1, xx, req);
                }  
            default:    
                return req;
        }
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
        SvcRequest req = resp.getRequest();
        if( req.getServiceName().equals( Dispatcher.SVC_GETSERVICESLIST)) {
            for( String s: SERVICES) {
                resp.add( "Services", s);
            }
        } 
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
        map.put( "DefaultPath", defaultPath);
        return map;
    }

    /** Release all the allocated resources. */
    @Override
    public void shutdown() {
    }

    private byte[] read( String path, String name, long pos, int len) 
            throws Exception {
        log.trace( "read " + path + "," + name + "," + pos + "," + len);
        File dir = new File( path);
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile( new File( dir, name), "r");
            if( pos > 0) {
                f.seek( pos);
            }
            byte buff[] = new byte[ len];
            len = f.read( buff, 0, len);
            if( len == -1) {
                len = 0;
            }
            return Arrays.copyOf( buff, len);
        } catch( Exception ex) {
            log.info( "read failed " + ex);
            throw ex;
        } finally {
             try {
                 if( f != null) {
                     f.close();
                 }
            } catch( Exception x) { }
        }
    }

    private void write( String path, String name, byte[] buff, int pos, int len) 
            throws Exception {
        File dir = new File( path);
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile( new File( dir, name), "rw");
            if( pos > 0) {
                f.seek( pos);
            }
            f.write( buff, 0, len);
        } catch( Exception ex) {
            log.info( "write failed " + ex);
            throw ex;
        } finally {
             try {
                 if( f != null) {
                     f.close();
                 }
            } catch( Exception x) { }
        }
    }

    private Map<String,Map<String, Object>> listFiles( String path) {
        log.trace( "list " + path + " defaultPath=" + defaultPath);
        TreeMap<String,Map<String, Object>> mm = new TreeMap<>();
        File dir = new File( path);
        if( !dir.isAbsolute()) {
            dir = new File( defaultPath, path);
            log.trace( "list relative " + dir);
        }
        File[] filesList = dir.listFiles();
        if( filesList == null) {
            return mm;
        }
        for( File f : filesList) {
            if( !f.isDirectory()) {
                Map<String,Object> m = new TreeMap<>();
                m.put( "Length", f.length());
                m.put( "LastModified", f.lastModified());
                m.put( "CanRead", f.canRead());
                m.put( "CanWrite", f.canWrite());
                mm.put( f.getName(), m);
            }
        }
        return mm;
    }
    
    private Map<String,Map<String, Object>> listDirs( String path) {
        log.trace( "listDirs " + path + " defaultPath=" + defaultPath);
        TreeMap<String,Map<String, Object>> mm = new TreeMap<>();
        File dir = new File( path);
        if( !dir.isAbsolute()) {
            dir = new File( defaultPath, path);
            log.trace( "list relative " + dir);
        }
        File[] filesList = dir.listFiles();
        if( filesList == null) {
            return mm;
        }
        for( File f : filesList) {
            if( f.isDirectory()) {
                Map<String,Object> m = new TreeMap<>();
                m.put( "CanRead", f.canRead());
                m.put( "CanWrite", f.canWrite());
                mm.put( f.getName(), m);
            }
        }
        return mm;
    }
    
    private void rename( String path, String name, String newName) throws Exception {
        File dir = new File( path);
        File f = new File( dir, name);
        f.renameTo( new File( dir, newName));
    }    

    private String toStr( byte[] buff) {
        StringBuilder sb = new StringBuilder();
        for( int i = 0; i < buff.length; ++i) {
            sb.append( String.format("%02X", buff[ i])); 
        }
        return sb.toString();
    }

    private byte[] toBin( String str) {
        byte[] b = new byte[ str.length() / 2];
        for( int i = 0; i < b.length; ++i) {
            b[ i] = ( byte)Integer.parseInt( str.substring( i * 2, ( i + 1) * 2), 16);
        }
        return b;
    }
            
    /**
    public static void main( String args[]) throws Exception {
        org.apache.log4j.BasicConfigurator.configure();
        FileServices fs = new FileServices();
        //fs.setConfiguration( new HashMap<String,Object>());
        //System.out.println( "list=" + fs.list( fs.defaultPath));
        //byte buff[] = fs.read( "/home/gustavo", "vacio.txt", 0, 0);
        //System.out.println( "l=" + buff.length + "[SOF]" + new String( buff) + "[EOF]");
        //fs.write( "/home/gustavo", "prueba.txt", "Prueba".getBytes(), 0, 5);
        //fs.rename( "/home/gustavo", "prueba.txt", "prb2.txt");
    }
    /**/

}


