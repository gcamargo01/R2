/* EjbAsyncProxy.java */
package uy.com.r2.svc.ejb3;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.AsyncService;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;

/** EJB3 session bean service proxy.
 * @author G.Camargo
 */
public class EjbAsyncProxy implements AsyncService {
    private static final Logger LOG = Logger.getLogger(EjbAsyncProxy.class);

    private AsyncService service = null;
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
            url = "" + appName + "/" + moduleName + "/" + beanName + "!" + AsyncService.class.getName();
            service = (AsyncService)context.lookup( url);
            cfg.resetChanged();
        } catch( Exception x) {
            Exception xx = new Exception( "Error biding " + url, x);
            throw xx;
        }         
    }
    
    /** Invocation dispatch phase.
     * The module implementation may return: <br>
     * (1) A SvcRequest to dispatch to the next service module, or <br>
     * (2) A SvcResponse created (example: Error condition), <br>
     * (3) NULL when there aren't next module to call (yet) <br>
     * (4) throw a Exception to explicit set the module that originates 
     * the failure.
     * @param req Service request message 
     * @param cfg Module configuration (*)
     * @return SvcRequest, SvrResponse or NULL
     * @throws Exception Unexpected error
     */
    public SvcMessage onRequest( SvcRequest req, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        return service.onRequest( req, cfg);
    }

    /** Process a response phase.
     * The container calls this method to process a response. 
     * If something goes wrong it should throw a Exception to clearly set 
     * what module originates the failure.
     * @param resp SvcRequest from next module  
     * @param cfg Module configuration (*)
     * @return SvcResponse message to caller
     * @throws Exception Unexpected error
     */
    public SvcResponse onResponse( SvcResponse resp, Configuration cfg) throws Exception {
        setConfiguration( cfg);
        return resp; // !!!!
        //return service.onResponse( resp, cfg);
    }

    /** Get the configuration descriptors of this module.
     * Each module must implement this method to give complete information about 
     * its configurable items.
     * @return ConfigItemDescriptor List
     */
    @Override
    public List<ConfigItemDescriptor> getConfigDescriptors() {
        LinkedList<ConfigItemDescriptor> l = new LinkedList();
        l.add( new ConfigItemDescriptor( "ProviderUrl", ConfigItemDescriptor.STRING, 
                "Initial Context Provider URL like http-remoting://localhost:8080", null));
        l.add( new ConfigItemDescriptor( "AppName", ConfigItemDescriptor.STRING, 
                "The app name is the application name of the deployed EJBs. This is typically the ear name" +
                "without the .ear suffix. However, the application name could be overridden in the " +
                "application.xml of the EJB deployment on the server. ", "")); 
        l.add( new ConfigItemDescriptor( "EjbRemoteServer", ConfigItemDescriptor.STRING, 
                "This is the module name of the deployed EJBs on the server. This is typically the jar name " +
                "of the EJB deployment, without the .jar suffix, but can be overridden via the ejb-jar.xml",
                null));
        l.add( new ConfigItemDescriptor( "DistinctName", ConfigItemDescriptor.STRING, 
                "AS7 allows each deployment to have an (optional) distinct name. We haven't specified a " + 
                "distinct name for our EJB deployment, or an empty string", ""));
        l.add( new ConfigItemDescriptor( "ServiceImpl", ConfigItemDescriptor.STRING, 
                "The EJB name which by default is the simple class name of the bean implementation class",
                ""));
        return l;
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

