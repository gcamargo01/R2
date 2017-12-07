/* SvcRequest.java */
package uy.com.r2.core.api;

import java.util.List;
import java.util.Map;
import java.io.Serializable;
import java.util.HashMap;

/** Request for a R2 service module.
 * @author G.Camargo
 */
public class SvcRequest extends SvcMessage implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    private static String defaultClientNode = null;
    
    private final String clientNode;
    private final int nodeRqNr;
    private final int sessionNr;
    private final int timeOut;
    private final String service;
    private final long requestTime;
    private final double amount;
    private final String currency;
    
    /** Build a request with amount and currency.
     * It should be created right on the event to make a service call, the time 
     * starts running.
     * @param clientNode Client node name or null
     * @param nodeRqNr Node transaction number, it must change every request
     * @param sessionNr Session ticket, only for auditory proposes
     * @param service Requested service name
     * @param payload Message data multimap
     * @param amount Total amount of the service requested
     * @param currency Currency code of the amount
     * @param timeOut Maximum response time in mS
     */
    public SvcRequest( String clientNode, int nodeRqNr, int sessionNr, String service, 
            Map<String,List<Object>> payload, double amount, String currency, int timeOut) {
        super( setReqId( clientNode, service, sessionNr, nodeRqNr), payload);
        if( clientNode == null) {
            clientNode = defaultClientNode;
        }
        this.clientNode = clientNode;
        this.nodeRqNr = nodeRqNr;
        this.sessionNr = sessionNr;
        this.service = service;
        this.timeOut = timeOut;
        this.amount = amount;
        this.currency = currency;
        this.requestTime = System.currentTimeMillis();
    }
    
    /** Build a request without amount and currency.
     * It should be created right on the event to make a service call, the time 
     * starts running.
     * @param clientNode Client node name or null
     * @param nodeRqNr Node request number, it must change every request
     * @param sessionNr Session ticket, only for auditory proposes
     * @param service Requested service name
     * @param payload Message data multimap
     * @param timeOut Maximum response time in mS
     */
    public SvcRequest( String clientNode, int nodeRqNr, int sessionNr, String service, 
            Map<String,List<Object>> payload, int timeOut) {
        super( setReqId( clientNode, service, sessionNr, nodeRqNr), payload);
        if( clientNode == null) {
            clientNode = defaultClientNode;
        }
        this.clientNode = clientNode;
        this.nodeRqNr = nodeRqNr;
        this.sessionNr = sessionNr;
        this.service = service;
        this.timeOut = timeOut;
        this.amount = 0d;
        this.currency = null;
        this.requestTime = System.currentTimeMillis();
    }

    private static String setReqId( String clientNode, String service, int sessionNr, int nodeRqNr) { 
        if( clientNode == null) {
            clientNode = defaultClientNode;
        } else if( defaultClientNode == null) {
            defaultClientNode = clientNode;
        }
        StringBuilder sb = new StringBuilder();
        sb.append( clientNode);
        sb.append( '@');
        sb.append( service);
        sb.append( '.');
        sb.append( Integer.toString( sessionNr));
        sb.append( '#');
        sb.append( Integer.toString( nodeRqNr));
        return sb.toString();
    }
    
    /** Clone itself. To isolate multiple messages.
     * @return SvcRequest
     */
    @Override
    public SvcRequest clone() {
        Map<String,List<Object>> m = new HashMap<>();
        m.putAll( getPayload());
        return new SvcRequest( clientNode, nodeRqNr, sessionNr, service, 
            m, amount, currency, timeOut);
    }
    
    /** Clone with new data. To make a new message.
     * @param payload New data
     * @return SvcRequest
     */
    public SvcRequest clone( Map<String,List<Object>> payload) {
        return new SvcRequest( clientNode, nodeRqNr, sessionNr, service, 
            payload, amount, currency, timeOut);
    }
    
    /** Get the service name of the request.
     * @return String
     */
    public String getServiceName() {
        return service;
    }
    
    /** Get the node transaction number.
     * @return Integer
     */
    public int getNodeRqNr() {
        return nodeRqNr;
    }
    
    /** Get the session number.
     * @return Integer
     */
    public int getSessionNr() {
        return sessionNr;
    }
    
    /** Get the maximum time to get a response.
     * @return Interval in mS
     */
    public int getTimeOut( ) {
        return timeOut;
    }
    
    /** Get the client node name who calls the service.
     * @return String
     */
    public String getClientNode( ) {
        return clientNode;
    }

    /** Get the amount from the request.
     * @return Double
     */
    public double getAmount( ) {
        return amount;
    }

    /** Get the currency code from the request.
     * @return String
     */
    public String getCurrency( ) {
        return currency;
    }

    /** Get the absolute time of the request.
     * @return Absolute time (currentTimeMillis)
     */
    public long getAbsoluteTime( ) {
        return requestTime;
    }
    
    /** Get a human readable message.
     * @return String
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append( "REQUEST ");
        sb.append( getRequestId());
        sb.append( " ");
        if( getAmount() != 0) {
            sb.append( getCurrency());
            sb.append( ":");
            sb.append( getAmount());
            sb.append(  " ");
        }
        sb.append( " ");
        sb.append( getPayload());
        return sb.toString();
    }

}
