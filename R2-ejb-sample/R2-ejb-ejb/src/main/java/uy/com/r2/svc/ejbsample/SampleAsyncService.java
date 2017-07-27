/* SampleAsyncService.java */
package uy.com.r2.svc.ejbsample;

import java.util.Map;
import java.util.List;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** Example of R2 EJB3 AsyncService. */
@Stateless
@Remote
public class SampleAsyncService implements AsyncService {
    public static final Logger LOG = Logger.getLogger( SampleAsyncService.class);
    
    @Override
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        LOG.debug( "arranca invocacion " + req);
        req.put( "DatoInvocacion", "" + this.toString());
        if( cfg.getBoolean( "ExceptionInvocacion")) {
            throw new Exception( "Exception inventado");
        }    
        try {
            Thread.sleep( cfg.getInt( "Tmp"));
        } catch( Exception xx) { }
        if( cfg.getBoolean( "Eco")) {
            req.put( "Dato", 0);
        }    
        LOG.debug( "finaliza invoacion " + req);
        return req;
    } 

    @Override
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        LOG.debug( "arranca respuesta " + resp);
        if( cfg.getBoolean( "ExceptionRespuesta")) {
            throw new Exception( "Exception inventado");
        }    
        try {
            Thread.sleep( cfg.getInt( "Tmp"));
        } catch( Exception xx) { }
        resp.put( "DatpRespuesta", "" + this.toString());
        LOG.debug( "finaliza respuesta " + resp);
        return resp;
    } 

    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        return null;
    }

    @Override
    public Map<String, Object> getStatusVars() {
        return null;
    }

    @Override
    public void shutdown() {
    }

}


