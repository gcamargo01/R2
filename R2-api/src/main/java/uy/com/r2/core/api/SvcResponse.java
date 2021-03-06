/* SvcResponse.java */
package uy.com.r2.core.api;

import java.util.List;
import java.util.Map;
import java.io.Serializable;
import org.apache.log4j.Logger;

/** Response from a R2 service module.
 * @author G.Camargo
 */
public class SvcResponse extends SvcMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger( SvcResponse.class);

    /** Time-out error. */
    public static final int RES_CODE_TIMEOUT = -10000;
    /** Too many active threads error. */
    public static final int RES_CODE_TOPPED = -10001;
    /** Service name not implemented in the module. */
    public static final int RES_CODE_INVALID_SERVICE = -10002;
    /** Service module not found. */
    public static final int RES_CODE_INVALID_MODULE = -10003;
    /** Unexpected Exception error. */
    public static final int RES_CODE_EXCEPTION = -10004;
    /** Not found. */
    public static final int RES_CODE_NOT_FOUND = 100;

    public static final String MSG_TIMEOUT = "Timeout";
    public static final String MSG_TOPPED = "Too many concurrent ";
    public static final String MSG_INVALID_SERVICE = "Does not implement service ";
    public static final String MSG_INVALID_MODULE = "Not registered module ";
    public static final String MSG_EXCEPTION = "Not registered module ";
    public static final String MSG_NOT_FOUND = "Not found";

    private final int resultCode;
    private final SvcRequest request;
    private transient int responseTime;
    
    /** Build a simple response without any data.
     * Should be instanced right in the event of the response.
     * @param resultCode Error code, 0 is ok, 
     * @param request Invocation request
     */
    public SvcResponse( int resultCode, SvcRequest request) {
        super( request.getRequestId(), null);
        this.responseTime = ( int)( System.currentTimeMillis() - 
                request.getAbsoluteTime());
        this.resultCode = resultCode;
        this.request = request;
    }
    
    /** Successful response constructor with data.
     * Should be instanced right in the event of the response.
     * @param data Message data or null
     * @param resultCode Error code, 0 is ok, 
     * @param request Invocation request
     */
    public SvcResponse( Map<String,List<Object>> data, int resultCode, SvcRequest request) {
        super( request.getRequestId(), data);
        if( resultCode < 0) {
            log.warn( "The resultCode (" + resultCode +
                    ") should not be negative on a normal SvcResponse " +
                    "(Not a SvcErrorResponse) " +
                    request.getServiceName() + " from " +
                    request.getClientNode());
        }
        this.responseTime = ( int)( System.currentTimeMillis() - 
                request.getAbsoluteTime());
        this.resultCode = resultCode;
        this.request = request;
    }
    
    /** Build a failure response from a Exception.
     * Should be instanced right in the event of the response. <br>
     * @param reasonOrAction Cause of error and/or failed action in progress
     * @param errorResultCode Result code, in this method must be lower than 0 
     * @param exception Exception if there was one, optional
     * @param request Invocation request
     */
    public SvcResponse( String reasonOrAction, int errorResultCode, Throwable exception, 
            SvcRequest request) {
        super( request.getRequestId(), null);
        if( errorResultCode >= 0) {
            log.warn( "The resultCode " + errorResultCode + " of error '" + reasonOrAction  
                    + "' should be negative or generic exception " + RES_CODE_EXCEPTION 
                    + " on SvcErrorResponses from " + request);
            errorResultCode = RES_CODE_EXCEPTION;
        }
        this.responseTime = ( int)( System.currentTimeMillis() - 
                request.getAbsoluteTime());
        this.resultCode = errorResultCode;
        this.request = request;
        addToMap( super.getPayload(), "Exception", exception);
        addToMap( super.getPayload(), "ReasonOrAction", reasonOrAction);
    }

    /** Build a failure response from a message.
     * Should be instanced right in the event of the response. <br>
     * @param reasonOrAction Cause of error and/or failed action in progress
     * @param errorResultCode Result code, in this method must be lower than 0 
     * @param request Invocation request
     */
    public SvcResponse( String reasonOrAction, int errorResultCode, SvcRequest request) {
        super( request.getRequestId(), null);
        if( errorResultCode >= 0) {
            log.warn( "The resultCode " + errorResultCode + " of error '" + reasonOrAction  
                    + "' should be negative on SvcErrorResponses from " + request);
        }
        this.responseTime = ( int)( System.currentTimeMillis() - 
                request.getAbsoluteTime());
        this.resultCode = errorResultCode;
        this.request = request;
        addToMap( super.getPayload(), "ReasonOrAction", reasonOrAction);
    }

    /** Clone with new data. To make a new message.
     * @param data New data
     * @return SvcRequest
     */
    public SvcResponse clone( Map<String,List<Object>> data) {
        return new SvcResponse( data, resultCode, request);
    }
    
    /** Get the original request.
     * @return SvcRequest
     */
    public SvcRequest getRequest( ) {
        return request;
    }
    
    /** Get the result code.
     * @return Result code, lower than 0 means it is fatal/unexpected
     */
    public int getResultCode( ) {
        return resultCode;
    }
    
    /** Get the response time.
     * @return SvcResponse time in mS
     */
    public int getResponseTime( ) {
        return responseTime;
    }
    
    /** Re-calculate the response time.
     */
    public void updateResponseTime( ) {
        this.responseTime = ( int)( System.currentTimeMillis() - 
                request.getAbsoluteTime());
    }
    
    /** Get a human readable message.
     * @return String
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();  
        sb.append( ( resultCode >= 0)? "RESPONSE ": "ERROR-RESPONSE ");
        sb.append( getRequestId());
        sb.append( ' ');
        sb.append( responseTime);
        sb.append( "mS ");
        sb.append( resultCode);
        sb.append( ' ');
        sb.append( getPayload());
        return sb.toString();
    }

}



