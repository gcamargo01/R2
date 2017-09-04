/* SvcException.java */
package uy.com.r2.core.api;

/** R2 service exception.
 * An Exception with a message (error or failed action) a errorCode and a SvcRequest. 
 * @author G.Camargo
 */
public class SvcException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int errorCode;
    private final SvcRequest req;
    
    /** Pack information about the error.
     * @param reasonOrAction Reason of the failure or current action
     * @param errorCode Numeric code, may be lower than 0 for fatal errors
     * @param req Request message
     */
    public SvcException( String reasonOrAction, int errorCode, SvcRequest req) {
        super( reasonOrAction);
        this.errorCode = errorCode;
        this.req = req;
    }

    /** Pack information about the exception.
     * @param reasonOrAction Reason of the failure or current action
     * @param errorCode Numeric code, may be lower than 0 for fatal errors
     * @param th Previous Exception 
     * @param req Request message
     */
    public SvcException( String reasonOrAction, int errorCode, Throwable th, SvcRequest req) {
        super( reasonOrAction, th);
        this.errorCode = errorCode;
        this.req = req;
    }

    /** Get the error code.
     * @return Integer
     */
    public int getErrorCode( ) {
        return errorCode;        
    }

    /** Get the original request that origin the failure.
     * @return SvcRequest
     */
    public SvcRequest getRequest( ) {
        return req;        
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append( super.toString());
        s.append( " ErrorCode: ");
        s.append( errorCode);
        s.append( " Req: ");
        s.append( req.toString());
        return s.toString();
    }
    
}

