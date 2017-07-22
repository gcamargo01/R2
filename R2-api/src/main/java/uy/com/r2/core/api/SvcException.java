/* SvcException.java */
package uy.com.r2.core.api;

/** R2 service exception.
 * An Exception with message and errorCode. 
 * @author G.Camargo
 */
public class SvcException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int errorCode;
    
    /** Pack information about the error.
     * @param reasonOrAction Reason of the failure or current action
     * @param errorCode Numeric code, may be lower than 0 for fatal errors
     */
    public SvcException( String reasonOrAction, int errorCode) {
        super( reasonOrAction);
        this.errorCode = errorCode;
    }

    /** Pack information about the exception.
     * @param reasonOrAction Reason of the failure or current action
     * @param errorCode Numeric code, may be lower than 0 for fatal errors
     * @param th Previous Exception 
     */
    public SvcException( String reasonOrAction, int errorCode, Throwable th) {
        super( reasonOrAction, th);
        this.errorCode = errorCode;
    }

    /** Get the error code.
     * @return Integer
     */
    public int getErrorCode( ) {
        return errorCode;        
    }

}

