/* ServiceReferenceTest.java */
package uy.com.r2.core.api.test;

import org.junit.Test;
import uy.com.r2.core.api.ServiceReference;
import static org.junit.Assert.*;

/**
 * @author gustavo
 */
public class ServiceReferenceTest {

    /**
     * Test of setDescription method, of class ServiceReference.
     */
    @Test
    public void testSetDescription() {
        System.out.println( "setDescription" );
        ServiceReference sr = new ServiceReference( "svc");
        sr.setDescription( "A desc");
        assertEquals( "Should get decription", "A desc", sr.getDescription());
    }

    /**
     * Test of addParameter method, of class ServiceReference.
     */
    @Test
    public void testAddRequestField() {
        System.out.println( "addRequestField" );
        ServiceReference sr = new ServiceReference( "svc");
        sr.addRequestField( "Field", "A field desc", ServiceReference.STRING);
        assertEquals( "Should get field decription", "A field desc", sr.getRequestFieldDescription( "Field"));
    }

    /**
     * Test of addParameter method, of class ServiceReference.
     */
    @Test
    public void testAddResponseField() {
        System.out.println( "addResponseField" );
        ServiceReference sr = new ServiceReference( "svc");
        sr.addResponseField( "Field", "A field desc", ServiceReference.STRING);
        assertEquals( "Should get field decription", "A field desc", sr.getResponseFieldDescription( "Field"));
    }

    /**
     * Test of addResponse method, of class ServiceReference.
     */
    @Test
    public void testAddResultCode() {
        System.out.println( "addResultCode" );
        ServiceReference sr = new ServiceReference( "svc");
        sr.addResultCode( 10, "An error");
        assertTrue( "Should have 0 as normal resultCode", sr.getResultCodes().contains( 0));
        assertTrue( "Should have this new resultCode", sr.getResultCodes().contains( 10));
        assertEquals( "Should have this new resultCode description", "An error", sr.getResultCodeDescriptoon( 10 ));
    }
    
}
