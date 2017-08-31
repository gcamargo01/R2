/* EjbProxy.java */
package uy.com.r2.svc.ejb3;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SimpleService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** EJB3 session bean service proxy.
 * @author G.Camargo
 */
public class EjbProxy implements SimpleService {
    private static final Logger LOG = Logger.getLogger(EjbProxy.class);
    
    private SimpleService service = null;
    private Context context = null;
    
    void setConfiguration( Configuration cfg) throws Exception {
        String url = "";
        try {
            if( ( !cfg.isChanged()) && service != null) {
                return;
            }
            Properties jndiProperties = new Properties();
            jndiProperties.put( Context.INITIAL_CONTEXT_FACTORY, 
                    "org.jboss.naming.remote.client.InitialContextFactory");
            jndiProperties.put( Context.PROVIDER_URL, cfg.getString( "ProviderUrl", 
                    "http-remoting://localhost:8080"));
            context = new InitialContext( jndiProperties);
            final String appName = cfg.getString( "AppName", "");
            final String moduleName = cfg.getString( "EjbRemoteServer");
            final String beanName = cfg.getString( "ServiceImpl");
            url = "" + appName + "/" + moduleName + "/" + beanName + "!" + SimpleService.class.getName();
            service = (SimpleService)context.lookup( url);
            cfg.resetChanged();
        } catch( Exception x) {
            Exception xx = new Exception( "Error biding " + url, x);
            LOG.warn( xx.getMessage(), x);
            throw xx;
        }         
    }
    
   /** Service call.
     * The Configuration is a parameter to allow a complete state-less 
     * implementation, such a EJB3 stateless session bean. The container may 
     * restart, create few instances and distribute the module on many JVM / 
     * servers as needed to scale. <br>
     * Each service implementation may throw a Exception to clearly set 
     * what originates the failure. <br>
     * @param req Invocation message
     * @return SvcResponse message
     * @param cfg Module configuration
     * @throws Exception Unexpected error that must be warned
     */
    public SvcResponse call( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        return service.call( req, cfg);
    }

    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        throw new UnsupportedOperationException( "Not supported yet." ); //To change body of generated methods, choose Tools | Templates.
    }

    /** Get the status report of the module.
     * It may occurs at any time to get the current status of the module.
     * The variables may include: Version, ServiceLevel, LastErrors, ...
     * @return Variable and value map
     */
    @Override
    public Map<String, Object> getStatusVars() {
        if( service == null) {
            return null;
        }
        return service.getStatusVars();
    }

    /** Stop execution and release all the allocated resources. */
    @Override
    public void shutdown() {
        if( service != null) {
            service.shutdown();
        }
        try {
            context.close();
        } catch( Exception ex) { }
    }

}

