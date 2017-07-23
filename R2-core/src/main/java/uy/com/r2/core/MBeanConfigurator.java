/* MBeanConfigurator.java */
package uy.com.r2.core;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import javax.management.*;
import org.apache.log4j.Logger;
import uy.com.r2.core.api.ConfigItemDescriptor;
import uy.com.r2.core.api.Configuration;

/** Dynamic MBean to configure.
 */
public class MBeanConfigurator implements DynamicMBean {
    
    private static final Logger log = Logger.getLogger( MBeanConfigurator.class);
    private static boolean firstTime = true;

    private final String moduleName;
    private final String description;
    private final HashMap<String,ConfigItemDescriptor> cfgDescMap;
    private final HashMap<String,String> cfgChgdValues = new HashMap<String,String>();
    
    static void moduleUpdate( String moduleName) {
        if( firstTime) {
            firstTime = false;
            moduleUpdate( SvcCatalog.class.getSimpleName());
        }
        ModuleInfo mi = SvcCatalog.getCatalog().getModuleInfo( moduleName);
        List<ConfigItemDescriptor> cfgDescs = mi.getConfigDescriptors();
        if( cfgDescs == null) {
            cfgDescs = new LinkedList<ConfigItemDescriptor>();
        }
        cfgDescs.add( new ConfigItemDescriptor( "class", 
                ConfigItemDescriptor.STRING, 
                "Module class", null));
        // JMX
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            Object cfg2 = new MBeanConfigurator( moduleName, "R2 Module " + moduleName, 
                    cfgDescs);
            ObjectName objName = new ObjectName( "uy.com.r2.svc:type=" + moduleName);
            if( !mbs.isRegistered( objName)) {
                mbs.registerMBean( cfg2, objName);
            }    
            log.trace( "(re)registered " + moduleName);
        } catch( Exception x) {
            log.info( "moduleDeploy " + moduleName + " failure", x);
        }
    }
    
    static void moduleUndeploy( String moduleName) {
        // JMX
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objName = new ObjectName( "uy.com.r2.svc:type=" + moduleName);
            mbs.unregisterMBean( objName);
            log.trace( "unregistered " + moduleName);
        } catch( Exception x) {
            log.info( "moduleUndeploy '" + moduleName + "' failure", x);
        }
    }
    
    MBeanConfigurator( String moduleName, String description, List<ConfigItemDescriptor> cfgList
            ) throws Exception {
        this.moduleName = moduleName;
        this.description = description;
        cfgDescMap = new HashMap<String,ConfigItemDescriptor>();
        cfgDescMap.put( "class", new ConfigItemDescriptor( "class", ConfigItemDescriptor.STRING, 
                "R2 Module implementation class", "null"));
        for( ConfigItemDescriptor cd: cfgList) {
            cfgDescMap.put( cd.getKey(), cd);
        }
    }

    @Override
    public synchronized String getAttribute( String name)
            throws AttributeNotFoundException {
        // Status vars has prority
        Map<String,Object> statVars = SvcCatalog.getCatalog().getModuleInfo( moduleName).getStatusVars();
        if( statVars.containsKey( name)) {
            log.trace( moduleName + ".get(" + name + ") statVar:" + statVars.get( name));
            return "" + statVars.get( name);
        }
        // Check description list
        if( !cfgDescMap.containsKey( name)) {
            throw new AttributeNotFoundException( "No such property: " + name );
        }
        // Configuraton changed values
        if( cfgChgdValues.containsKey( name)) {
            log.trace( moduleName + ".get(" + name + ") chgd:" + cfgChgdValues.get( name));
            return cfgChgdValues.get( name);
        }
        // then go for Configuration values
        ModuleInfo mi = SvcCatalog.getCatalog().getModuleInfo( moduleName);
        String value = "" + mi.getConfiguration().getString( name);
        log.trace( moduleName + ".get(" + name + "):" + value);
        return value;
    }

    @Override
    public synchronized void setAttribute( Attribute attribute)
            throws InvalidAttributeValueException, MBeanException, AttributeNotFoundException {
        String name = attribute.getName();
        if( !cfgDescMap.containsKey( name) ) {
            throw new AttributeNotFoundException( name );
        }
        Object value = attribute.getValue();
        if( !( value instanceof String ) ) {
            value = value.toString();
        }
        log.info( moduleName + ".set(" + name + "," + value + ")");
        cfgChgdValues.put( name, "" + value);
    }

    @Override
    public synchronized AttributeList getAttributes( String[] names ) {
        AttributeList list = new AttributeList();
        for( String name : names ) {
            String value = "";
            try {
                value = getAttribute( name);
            } catch( Exception xx) { }
            if( value != null ) {
                list.add( new Attribute( name, value));
            }
        }
        log.info( "getAttributes " + moduleName + " = " + list);
        return list;
    }

    @Override
    public synchronized AttributeList setAttributes( AttributeList list) {
        Attribute[] attrs = list.toArray( new Attribute[ 0 ] );
        AttributeList retlist = new AttributeList();
        for( Attribute attr : attrs ) {
            String name = attr.getName();
            Object value = attr.getValue();
            cfgChgdValues.put( name, "" + value);
        }
        return retlist;
    }

    @Override
    public Object invoke( String methodName, Object[] args, String[] sig)
            throws MBeanException, ReflectionException {
        log.debug( "invoke " + moduleName + "." + methodName);
        Object result = "";
        try {
            ModuleInfo mi = SvcCatalog.getCatalog().getModuleInfo( moduleName);
            if( methodName.equals( "Apply and Save" )) {
                log.info( moduleName + ".Apply");
                Configuration cfg = mi.getConfiguration();
                for( String n: cfgChgdValues.keySet()) {
                    cfg.put( n, cfgChgdValues.get (n));
                }
                SvcCatalog.getCatalog().updateConfiguration( moduleName, cfg);
                // TO DO:Save!
            } else if( methodName.equals( "Reset stats" )) {
                log.info( moduleName + ".Reset stats");
                mi.setConfiguration( mi.getConfiguration());
            } else if( methodName.equals( "Restore" )) {
                log.info( moduleName + ".Restore");
                // TO DO: restore!         
            } else if( methodName.equals( "Stop" )) {
                log.info( moduleName + ".Stop");
                mi.getImplementation().shutdown();
            } else { 
                throw new ReflectionException( new NoSuchMethodException( methodName));
            }
        } catch( Exception xx) {
            log.warn( "invoke failed ", xx);
            throw new MBeanException( xx);
        }    
        return result;
    }

    @Override
    public synchronized MBeanInfo getMBeanInfo() {
        // Update status
        Map<String,Object> statVars = SvcCatalog.getCatalog().getModuleInfo( moduleName).getStatusVars();
        // Display
        SortedSet<String> names = new TreeSet<String>();
        for( String name : cfgDescMap.keySet()) {
            names.add( name);
        }
        for( String name : statVars.keySet()) {
            names.add( name);
        }
        MBeanAttributeInfo[] attrs = new MBeanAttributeInfo[ names.size()];
        Iterator<String> it = names.iterator();
        for( int i = 0; i < attrs.length; i++ ) {
            String name = it.next();
            if( cfgDescMap.containsKey( name)) {  // config item
                attrs[i] = new MBeanAttributeInfo(
                        name,
                        cfgDescMap.get( name).getKlass().getName(),
                        cfgDescMap.get( name).getDescription(),
                        true, // isReadable
                        true, // isWritable
                        false); // isIs
                Object defaultValue = cfgDescMap.get( name).getDefaultValue();
                if( defaultValue == null) {
                    defaultValue = "";
                }
            } else {  // status var
                attrs[i] = new MBeanAttributeInfo(
                        name,
                        "java.lang.String",
                        "",
                        true, // isReadable
                        false, // isWritable
                        false); // isIs
            }
        }
        MBeanOperationInfo[] opers = {
            new MBeanOperationInfo(
                "Apply and Save",
                "Apply changes, save actual values and re-start if it was stopped",
                null, // no parameters
                "void",
                MBeanOperationInfo.ACTION ),
            new MBeanOperationInfo(
                "Reset stats",
                "Clear statistics values",
                null, // no parameters
                "void",
                MBeanOperationInfo.ACTION ),
            new MBeanOperationInfo(
                "Stop",
                "Stop module and release all its resources",
                null, // no parameters
                "void",
                MBeanOperationInfo.ACTION ),
            new MBeanOperationInfo(
               "Restore",
               "Restore previous applied values",
                null, // no parameters
               "void",
                MBeanOperationInfo.ACTION )
        };
        return new MBeanInfo(
            this.moduleName,
            description,
            attrs,
            null, // constructors
            opers,
            null ); // notifications
    }

}
