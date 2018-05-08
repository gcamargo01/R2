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
     *
    @Test
    public void testAddRequestField() {
        System.out.println( "addRequestField" );
        ServiceReference sr = new ServiceReference( "svc");
        sr.addRequestField( "Field", "A field desc", null);
        assertEquals( "Should get field decription", "A field desc", sr.getDescription());
    }

    /**
     * Test of addResponse method, of class ServiceReference.
     *
    @Test
    public void testAddResultCode() {
        System.out.println( "addResultCode" );
        ServiceReference sr = new ServiceReference( "svc");
        sr.addResultCode( 10, "An error");
        assertEquals( "Should get same result", "An error", sr.getDescription());
    }
    */
    
}
