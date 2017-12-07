/* Encryption.java */
package uy.com.r2.svc.tools;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** Encrypt an Decrypt messages.
 * @author G.Camargo
 */
public class Encryption implements AsyncService {
    private static Logger log = Logger.getLogger( Encryption.class);
    private boolean pRequest = true;
    private boolean pResponse = true;
    private boolean serverMode = false;
    private String doNot = "";
    private Cipher encryptCipher = null;
    private Cipher decryptCipher = null;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<>();
        l.add( new ConfigItemDescriptor( "ProcessRequest", ConfigItemDescriptor.BOOLEAN,
                "Encrypt or desencrypt Request", "true"));
        l.add( new ConfigItemDescriptor( "ProcessResponse", ConfigItemDescriptor.BOOLEAN,
                "Encrypt or desencrypt Response", "true"));
        l.add( new ConfigItemDescriptor( "ServerMode", ConfigItemDescriptor.BOOLEAN,
                "Server mode: desenc.req., enc.resp., Client mode: enc.req., desenc.resp", "false"));
        l.add( new ConfigItemDescriptor( "DoNotEncrypt", ConfigItemDescriptor.BOOLEAN, 
                "A comma separated field names to keep in clear", ""));
        l.add( new ConfigItemDescriptor( "Algorithm", ConfigItemDescriptor.STRING,
                "Algorithm used to encrypt", "AES"));
        l.add( new ConfigItemDescriptor( "Transformation", ConfigItemDescriptor.STRING,
                "Algorithm / Blocking / Padding used", "AES/CBC/PKCS5Padding"));
        l.add( new ConfigItemDescriptor( "Password", ConfigItemDescriptor.STRING,
                "Password used to encript/desencript key; in Hexadecimal", null));
        l.add( new ConfigItemDescriptor( "InitVector", ConfigItemDescriptor.STRING,
                "InitVector to use to encript/desencript key; as 16 bytes Hex", null));
        return l;
    }

    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isUpdated()) {
            return;
        }
        pRequest = cfg.getBoolean( "ProcessRequest");
        pResponse = cfg.getBoolean( "ProcessResponse");
        serverMode = cfg.getBoolean( "ServerMode");
        doNot = "," + cfg.getString( "DoNotEncrypt") + ",";
        String password = cfg.getString( "Password");
        SecretKeySpec secKey = new SecretKeySpec( password.getBytes( "UTF-8"), 
                cfg.getString( "Algorithm"));
        byte[] iv = toByte( cfg.getString( "InitVector"));
        IvParameterSpec ivPar = new IvParameterSpec( iv);        
        encryptCipher = Cipher.getInstance( cfg.getString( "Transformation"));
        encryptCipher.init( Cipher.ENCRYPT_MODE, secKey, ivPar);
        decryptCipher = Cipher.getInstance( cfg.getString( "Transformation"));
        decryptCipher.init( Cipher.DECRYPT_MODE, secKey, ivPar);
        cfg.clearUpdated();
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
        if( !pRequest) {
            return req;
        }
        return req.clone( crypt( req.getPayload(), !serverMode));
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
        if( !pResponse) {
            return res;
        }
        return res.clone( crypt( res.getPayload(), serverMode));
    }

    /** Get the status report.
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        Map<String,Object> map = new HashMap<>();
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

    private Map<String,List<Object>> crypt( Map<String,List<Object>> m, boolean enc) throws Exception {
        TreeSet<String> ts = new TreeSet( m.keySet());
        Map<String,List<Object>> nm = new HashMap<>();
        for( String k: ts)  {
            if( doNot.contains( "," + k + ",")) {
                nm.put( k, m.get(  k));
                continue;  
            }
            List l = m.get( k);
            List nl = new LinkedList();
            for( Object o: l) {
                nl.add( crypt( o, enc));
            }
            nm.put( k, nl);
            //System.out.println( "crypt " + k + " " + l + " to " + nl);        
        }
        return nm;
    }
    
    private Object crypt( Object o, boolean enc) throws Exception { 
        byte[] b;
        byte[] bb;
        if( enc) {  // Encript
            b = ( "" + o).getBytes( "UTF-8");
            bb = encryptCipher.doFinal( b);
            return toString( bb);
        } else {   // Decript
            b = toByte( "" + o);
            bb = decryptCipher.doFinal( b);
            return new String( bb);
        }
    }

    private static byte[] toByte( String s) { 
        int len = s.length();
        if( ( len % 1) == 1) {
            len += 1;
            s = "0" + s;
        }
        //System.out.println( "toByte " + s + " " + len);
        byte[] data = new byte[ len / 2];
        for( int i = 0; i < len; i += 2) {
            data[ i / 2] = (byte) ( ( Character.digit( s.charAt( i), 16) << 4)
                             + Character.digit( s.charAt( i + 1), 16));
        }
        return data; 
    }    
    
    private static String toString( byte b[]) { 
        BigInteger bigInt = new BigInteger( 1, b);
        return bigInt.toString( 16);
    }
    
    /**/
    public static void main( String args[]) {
        Configuration c = new Configuration();
        Encryption s = new Encryption();
        try {
            uy.com.r2.core.ModuleInfo.setDefaultValues( s, c);
            System.out.println( "cfg= " + c);
            c.put( "Password", "0123456789ABCDEF"); 
            c.put( "InitVector", "0123456789ABCDEF0123456789ABCDEF");  
            s.setConfiguration( c);
            String prb = "Esto es una prueba";
            //Object enc = s.crypt( prb, true);
            //Object des = s.crypt( enc, false);
            //System.out.println( "prb= |" + prb + "|");
            //System.out.println( "enc= " + enc);
            //System.out.println( "des= |" + des + "|");
            SvcRequest rq = new SvcRequest( "TEST", 0, 0, "Tst", null, 0);
            rq.put( "Field1", "Value0001");
            rq.put( "Field2", "Value0002a");
            rq.add( "Field2", "Value0002b");
            SvcRequest rq2 = (SvcRequest)s.onRequest( rq, c);
            c.put( "ServerMode", "true");
            SvcRequest rq3 = (SvcRequest)s.onRequest( rq2, c);
            System.out.println( "rq  = |" + rq + "|");
            System.out.println( "rq2 = |" + rq2 + "|");
            System.out.println( "rq3 = |" + rq3 + "|");
        } catch( Exception x) {
            x.printStackTrace( System.err);
        }    
    }
    /**/

}


