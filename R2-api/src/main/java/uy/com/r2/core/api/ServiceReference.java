/* ServiceReference.java */
package uy.com.r2.core.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Service API descriptor.
 * @author G.Camargo
 */
public class ServiceReference {

    public static final Type STRING = new Type( "string");
    public static final Type INTEGER = new Type( "integer");
    public static final Type NUMBER = new Type( "number");
    public static final Type DATE = new Type( "date");
    public static final Type ENUM = new Type( "enum");
    
    private final String serviceName;
    private final Map<String,Field> requestFields = new HashMap();
    private final Map<String,Field> responseFields = new HashMap();
    private final Map<Integer,String> resultCodes = new HashMap();
    private String description = "";
    
    /** Minimal descriptor constructor.
     * @param name Service name.
     */
    public ServiceReference( String name) {
        this.serviceName = name;
        this.resultCodes.put( 0, "Normal response");
        //this.resultCodes.put( SvcResponse.RES_CODE_NOT_FOUND, SvcResponse.MSG_NOT_FOUND);
    }

    /** Setter.
     * @param description Service description
     */
    public void setDescription( String description) {
        if( description != null) {
            this.description = description;
        }
    }
    
    /** Setter.
     * @param name field name
     * @param description Service description
     * @param type field type
     */
    public void addRequestField( String name, String description, Type type) {
        requestFields.put( name, new Field( description, type));
    }
    
    /** Setter.
     * @param name field name
     * @param description Service description
     * @param type field type
     */
    public void addResponseField( String name, String description, Type type) {
        responseFields.put( name, new Field( description, type));
    }

    /** Setter.
     * @param resultCode Result code 
     * @param description Service description
     */
    public void addResultCode( int resultCode, String description) {
        this.resultCodes.put( resultCode, description);
    }

    /** Getter.
     * @return Service description
     */
    public String getDescription( ) {
        return description;
    }
    
    /** Getter.
     * @param field Field name
     * @return Service description
     */
    public String getRequestFieldDescription( String field) {
        try {
            return requestFields.get( field).description;
        } catch( Exception x) {
            return null;
        }
    }
    
    /** Getter.
     * @param field Field name
     * @return Service description
     */
    public String getResponseFieldDescription( String field) {
        try {
            return responseFields.get( field).description;
        } catch( Exception x) {
            return null;
        }
    }
    
    public Set<Integer> getResultCodes( ) {
        return resultCodes.keySet();
    }
    
    public String getResultCodeDescriptoon( int rc) {
        return resultCodes.get( rc);
    }
    
    private class Field {
        String description;
        Type type;
        
        Field( String description, Type type) {
            this.description = description;
            this.type = type;
        }
    }

    /** Generate a descriptor as a Response.
     * @param rq Request is needed
     * @return Response with complete reference
     */
    public SvcResponse getResponse( SvcRequest rq) {
        SvcResponse resp = new SvcResponse( 0, rq);
        resp.put( "Service", serviceName);
        resp.put( "Description", description);
        Map<String,Map> rqf = new TreeMap<>();
        for( String n :requestFields.keySet()) {
            Map<String,String> m = new TreeMap<>();
            m.put( "Description", requestFields.get( n).description);
            m.put( "Type", "" + responseFields.get( n).type);
            rqf.put( n, m);
        }
        resp.put( "Request", rqf);
        Map<String,Map> rpf = new TreeMap<>();
        for( String n :responseFields.keySet()) {
            Map<String,String> m = new TreeMap<>();
            m.put( "Description", responseFields.get( n).description);
            m.put( "Type", "" + responseFields.get( n).type);
            rpf.put( n, m);
        }
        resp.put( "Response", rpf);
        return resp;
    }
    
    /** Generate a full reference as YAML string.
     * @return YAML
     */
    @Override
    public String toString( ) {
        StringBuilder sb = new StringBuilder();
        sb.append( "/" ).append( serviceName).append( '\n');
        if( !description.isEmpty()) {
            sb.append( "  description: ").append( description).append( '\n');
        }
        // parameters
        sb.append( "  parameters:" ).append( '\n');
        for( String n :requestFields.keySet()) {
            sb.append( "    '" ).append(requestFields.get( n).description).append( "':\n");
            sb.append( "      type: " ).append(requestFields.get( n).type).append( '\n');
        }
        // resultCodes
        sb.append( "  responses:" ).append( '\n');
        // responseFields
        for( String n :responseFields.keySet()) {
            sb.append( "    '" ).append( responseFields.get( n).description).append( "':\n");
            sb.append( "      type: " ).append( responseFields.get( n).type).append( '\n');
        }
        sb.append( "  result codes:" ).append( '\n');
        for( int rc :resultCodes.keySet()) {
            sb.append( "    " ).append( rc).append( ":\n");
            sb.append( "       description: " ).append( resultCodes.get( rc)).append( "':\n");
        }
        return sb.toString();
    }
    
    public static class Type {
        final String name;
        Type( String name) {
            this.name = name;
        }
        @Override
        public String toString() {
            return name;
        } 
    }
    
} 

