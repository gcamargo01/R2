/* KeyValidation.java */
package uy.com.r2.svc.tools;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

/** Sign with encrypted key.
 * !!!! To do: It needs testing
 * @author G.Camargo
 */
public class KeyValidation implements AsyncService {
    private static Logger LOG = Logger.getLogger( KeyValidation.class);
    private static String SECRET_TAG = "Secret";
    private boolean serverMode = true;
    private Cipher encryptCipher = null;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<>();
        l.add( new ConfigItemDescriptor( "ServerMode", ConfigItemDescriptor.BOOLEAN,
                "Server mode: validate req.,sign resp. Client mode: sign req., " + ""
                        + "validate resp.", "true"));
        l.add( new ConfigItemDescriptor( "Algorithm", ConfigItemDescriptor.STRING,
                "Algorithm used to encrypt", "AES"));
        l.add( new ConfigItemDescriptor( "Transformation", ConfigItemDescriptor.STRING,
                "Algorithm / Blocking / Padding used", "AES/CBC/PKCS5Padding"));
        l.add( new ConfigItemDescriptor( "Password", ConfigItemDescriptor.STRING,
                "Password used to encript/desencript key", null,
                ConfigItemDescriptor.SECURITY));
        l.add( new ConfigItemDescriptor( "InitVector", ConfigItemDescriptor.STRING,
                "InitVector to use to encript key; as 16 bytes Hex", null,
                ConfigItemDescriptor.SECURITY));
        return l;
    }

    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isUpdated()) {
            return;
        }
        serverMode = cfg.getBoolean( "ServerMode");
        String password = cfg.getString( "Password");
        encryptCipher = Cipher.getInstance( cfg.getString( "Transformation"));
        SecretKeySpec secKey = new SecretKeySpec( password.getBytes("UTF-8"), 
                cfg.getString( "Algorithm"));
        byte[] iv = cfg.getString( "InitVector").getBytes( "UTF-8");
        IvParameterSpec ivPar = new IvParameterSpec( iv);        
        encryptCipher.init( Cipher.ENCRYPT_MODE, secKey, ivPar);
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
        String h = sortAndSerialize( req.getPayload());
        LOG.debug( "hash=" + h);
        if( serverMode) {
            if( !validate(h, "" + req.get(SECRET_TAG))) {
                throw new Exception( "Invalid secret key");
            }    
        } else {
            req.put( SECRET_TAG, generate( h)); 
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
        String h = sortAndSerialize( res.getPayload());
        LOG.debug( "hash=" + h);
        if( serverMode) {
            res.put( SECRET_TAG, generate( h)); 
        } else {
            if( !validate( h, "" + res.get( SECRET_TAG))) {
                throw new Exception( "Invalid secret key");
            }    
        }
        return res;
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

    private String sortAndSerialize( Map<String, List<Object>> m) {
        Map<String, List<Object>> sm = new TreeMap();
        sm.putAll( m);
        StringBuilder sb = new StringBuilder();
        for( String k: sm.keySet())  {
            if( k.equals( SECRET_TAG)) {
                continue;
            }
            sb.append( k);
            sb.append( '=');
            for( Object o: sm.get(  k)) {
                sb.append( "" + o);
                sb.append( ',');
            }
            sb.append( '|');
        }
        return sb.toString();
    }

    private boolean validate( String str, String sec) throws Exception {
        return generate( str).equals( sec);
    }
    
    private String generate( String str) throws Exception {
        // Hash
        byte[] b = str.getBytes( "UTF-8");
        MessageDigest md = MessageDigest.getInstance( "MD5");
        //LOG.trace( "inp=" + new String( b));
        byte[] d = md.digest( b);
        //LOG.trace( "dig=" + new String( d));
        // Encript
        byte[] ed = encryptCipher.doFinal( d);
        //LOG.trace( "enc=" + new String( ed));
        BigInteger bigInt = new BigInteger( 1, ed);
        return bigInt.toString( 16);
    }

    /** Basic tests.
    public static void main( String args[]) {
        org.apache.log4j.BasicConfigurator.configure();
        Configuration c = new Configuration();
        KeyValidation s = new KeyValidation();
        try {
            uy.com.r2.core.ModuleInfo.setDefaultValues( s, c);
            c.put( "Password", "0123456789ABCDEF"); 
            c.put( "InitVector", "0123456789ABCDEF");  
            System.out.println( "cfg= " + c);
            s.setConfiguration( c);
            String prb = "Esto es una prueba";
            String sign = s.generate( prb);
            System.out.println( "prb= " + prb);
            System.out.println( "sign= " + sign);
            System.out.println( "" + s.validate( prb, sign));
            SvcRequest rq = new SvcRequest( null, 0, 0, "Svc", null, 0);
            rq.put( "Key", "Value");
            System.out.println( "rq= " + rq);
            c.put( "ServerMode", "false");
            SvcMessage rq2 = s.onRequest( rq, c);
            System.out.println( "rq2= " + rq2);
            c.put( "ServerMode", "true");
            SvcMessage rq3 = s.onRequest( (SvcRequest)rq2, c);
            System.out.println( "rq3= " + rq3);
        } catch( Exception x) {
            x.printStackTrace( System.err);
        }    
    }
    */

}


