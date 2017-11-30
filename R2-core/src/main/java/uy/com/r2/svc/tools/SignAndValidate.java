/* SignAndValidate.java */
package uy.com.r2.svc.tools;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.bind.DatatypeConverter;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** Sign request and validate responses.
 * !!!! To do: Brand new, need basic testing
 * @author G.Camargo
 */
public class SignAndValidate implements AsyncService {
    private static Logger log = Logger.getLogger( SignAndValidate.class);
    private String signatureAlgorithm = "SHA256withRSA";
    private String keystoreType = "pkcs12";
    private boolean validateReq = false;
    private boolean validateResp = false;
    private Certificate cert;
    private PrivateKey prvKey;
     
    /** Get the configuration descriptors of this module.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList<ConfigItemDescriptor>();
        l.add( new ConfigItemDescriptor( "ValidateReq", ConfigItemDescriptor.BOOLEAN,
                "Validate as a server (request)", "true"));
        l.add( new ConfigItemDescriptor( "ValidateResp", ConfigItemDescriptor.BOOLEAN, 
                "Validate as a client (response)", "false"));
        l.add( new ConfigItemDescriptor( "CertificateName", ConfigItemDescriptor.STRING,
                "Name of the certificate to use", "R2"));
        l.add( new ConfigItemDescriptor( "KeystoreType", ConfigItemDescriptor.STRING, 
                "Keystore Type (JKS,PKCS12,...)", System.getProperty( "javax.net.ssl.keyStoreType")));
        l.add( new ConfigItemDescriptor( "KeyStoreFile", ConfigItemDescriptor.STRING, 
                "Path to the Key Store including Path", System.getProperty( "javax.net.ssl.keyStore")));
        l.add( new ConfigItemDescriptor( "PassPhrase", ConfigItemDescriptor.STRING, 
                "Password to open the certificate", System.getProperty( "javax.net.ssl.keyStorePassword")));
        l.add( new ConfigItemDescriptor( "ExcludeFields", ConfigItemDescriptor.STRING, 
                "Data fields to exclude from signature", ""));
        l.add( new ConfigItemDescriptor( "SignatureAlgorithm", ConfigItemDescriptor.STRING, 
                "Signature Algorithm", "SHA256withRSA"));
        return l;
    }

    private void setConfiguration( Configuration cfg) throws Exception {
        if( !cfg.isUpdated()) {
            return;
        }
        signatureAlgorithm = cfg.getString( "SignatureAlgorithm");
        keystoreType = cfg.getString( "KeystoreType");
        validateReq = cfg.getBoolean( "ValidateReq");
        validateResp = cfg.getBoolean( "ValidateResp");
        String certName = cfg.getString( "CertificateName");
        String passPhrase = cfg.getString( "PassPhrase");
        KeyStore ks = KeyStore.getInstance( keystoreType);
        if( !cfg.getString( "KeyStoreFile").isEmpty()) {
            FileInputStream fsi = new FileInputStream( cfg.getString( "KeyStoreFile")); 
            ks.load( fsi, passPhrase.toCharArray());
            fsi.close();
        }    
        cert = ks.getCertificate( certName);
        prvKey = ( PrivateKey)ks.getKey( certName, passPhrase.toCharArray());        
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
        Map<String,List<Object>> m = req.getPayload();
        String h = sortAndSerialize( m);
        log.debug( "hash=" + h);
        if( validateReq) {
            if( !validate( h, "" + req.get( "Signature"))) {
                throw new Exception( "Invalid signature");
            }    
        } else {
            SvcMessage.addToMap( m, "Signature", sign( h)); 
        }
        return req.clone( m);
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
        Map<String,List<Object>> m = res.getPayload();
        String h = sortAndSerialize( m);
        log.debug( "hash=" + h);
        if( validateResp) {
            if( !validate( h, "" + res.get( "Signature"))) {
                throw new Exception( "Invalid signature");
            }    
        } else {
            SvcMessage.addToMap( m, "Signature", sign( h)); 
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
            sb.append( k);
            sb.append( '=');
            for( Object o: sm.get(  k)) {
                sb.append( "" + o);
                sb.append( ',');
            }
            sb.append( '\n');
        }
        return sb.toString();
    }

    private String sign( String hash) throws Exception {
        Signature s = Signature.getInstance( signatureAlgorithm);
        s.initSign( prvKey);
        s.update( hash.getBytes());
        return DatatypeConverter.printBase64Binary( s.sign());
    }

    private boolean validate( String hash, String signature) throws Exception {
        byte sb[] = DatatypeConverter.parseBase64Binary( signature);
        PublicKey puk = cert.getPublicKey();
        Signature s = Signature.getInstance( signatureAlgorithm);
        s.initVerify( puk);
        s.update( hash.getBytes());
        return s.verify( sb);
    }

    /**/   
    public static void main( String args[]) {
        Configuration c = new Configuration();
        SignAndValidate s = new SignAndValidate();
        try {
            uy.com.r2.core.ModuleInfo.setDefaultValues( s, c);
            System.out.println( "cfg= " + c);
            s.setConfiguration( c);
            String prb = "Esto es una prueba";
            String sign = s.sign( prb);
            System.out.println( "prb= " + prb);
            System.out.println( "sign= " + sign);
            System.out.println( "" + s.validate( prb, sign));
        } catch( Exception x) {
            x.printStackTrace( System.err);
        }    
    }
    /**/

}


