/* SvcMessage.java */
package uy.com.r2.core.api;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** Abstract base class of invocation {@link uy.com.r2.core.api.SvcRequest}
 * and message {@link uy.com.r2.core.api.SvcResponse}.
 * It hast a "payload" data stored ia a Multi-Map (Map of Lists).
 * @author G.Camargo
 */
public abstract class SvcMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String,List<Object>> payload;
    protected String requestId;
    
    /** Constructor.
     * @param data Message data map
     */
    public SvcMessage( Map<String,List<Object>> data) {
        if( data == null) {
            data = new HashMap();
        }
        this.payload = data;
    }
    
    /** Get the data contents.
     * @return Map of Lists
     */
    public Map<String,List<Object>> getPayload( ) {
        return payload;
    }
    
    /** Extract the first element with this name form data.
     * @param field Data name to extract or null = any one
     * @return Object or null
     */
    public Object get( String field) {
        if( field == null && payload != null && !payload.isEmpty()) {
            field = ( String)payload.keySet().toArray()[ 0];
        }    
        List<Object> lo = payload.get( field);
        if( lo == null || lo.isEmpty()) {
            return null;
        }
        return lo.get( 0);
    }
    
    /** Add a new element to data map.
     * @param field Data name to addToData
     * @param obj Object 
     * @return Data
     */
    public Map<String,List<Object>> add( String field, Object obj) {
        List<Object> l;
        if( !payload.containsKey( field)) {
            l = new LinkedList();
            payload.put( field, l);
        } else {
            l = payload.get( field);
        }
        l.add( obj);
        return payload;
    }

    /** Replace or insert this element to data map.
     * @param field Data name to addToData
     * @param obj Object 
     * @return Data
     */
    public Map<String,List<Object>> put( String field, Object obj) {
        if( payload.containsKey( field)) {
            payload.remove( field);
        }
        List<Object> l = new LinkedList();
        l.add( obj);
        payload.put( field, l);
        return payload;
    }
    
    /** Add a new element to data map.
     * @param payload Previous data or null
     * @param field Data name to addToData
     * @param obj Object 
     * @return Data
     */
    public static Map<String,List<Object>> addToPayload( 
            Map<String,List<Object>> payload, String field, Object obj) {
        List<Object> l;
        if( payload == null) {
            payload = new HashMap();
        }    
        if( ( !payload.containsKey( field)) || payload.get( field).isEmpty()) {
            l = new LinkedList();
            l.add( obj);
            payload.put( field, l);
        } else {
            l = payload.get( field);
            l.add( obj);
        }
        return payload;
    }
    
    /** Get the key information of the request.
     * @return String
     */
    public String getRequestId( ) {
        return requestId;
    };
    
}


