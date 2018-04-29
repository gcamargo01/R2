/* SvcMessageTest.java */
package uy.com.r2.core.api.test;

import org.junit.Test;
import static org.junit.Assert.*;
import uy.com.r2.core.api.SvcMessage;

/**
 *
 * @author gustavo
 */
public class SvcMessageTest {
    
    public SvcMessageTest() {
    }
    
    /**
     * Test of get method, of class SvcMessage.
     */
    @Test
    public void testGet() {
        System.out.println( "get" );
        SvcMessage instance = new SvcMessageImpl();
        assertNull( "Get a null field shoud retirn null", instance.get( null));
        assertNull( "Get a empty field shoud retirn null", instance.get( ""));
        assertNull( "Get a non esistant field shoud retirn null", instance.get( "NonExistanField"));
    }

    /**
     * Test of add method, of class SvcMessage.
     */
    @Test
    public void testAdd() {
        System.out.println( "add" );
        SvcMessage instance = new SvcMessageImpl();
        instance.add( "Field", "Value1");
        instance.add( "Field", "Value2");
        assertEquals( "Get a null field shoud retirn Value1", "Value1", instance.get( null));
        assertEquals( "Get a Field shoud retirn Value1", "Value1", instance.get( "Field"));
    }

    /**
     * Test of put method, of class SvcMessage.
     */
    @Test
    public void testPut() {
        System.out.println( "put" );
        SvcMessage instance = new SvcMessageImpl();
        instance.put( "Field", "Value1");
        instance.put( "Field", "Value2");
        assertNotNull( "Get a null field shoud retirn a value", instance.get( null));
        assertEquals( "Get a Field shoud retirn Value2", "Value2", instance.get( "Field"));
    }

    /**
     * Test of addToMap method, of class SvcMessage.
     *
    @Test
    public void testAddToMap() {
        System.out.println( "addToMap" );
        Map<String, List<Object>> payload = null;
        String field = "";
        Object obj = null;
        Map<String, List<Object>> expResult = null;
        Map<String, List<Object>> result = SvcMessage.addToMap( payload, field, obj );
        assertEquals( expResult, result );
        // TODO review the generated test code and remove the default call to fail.
        fail( "The test case is a prototype." );
    }

    /**
     * Test of getRequestId method, of class SvcMessage.
     */
    @Test
    public void testGetRequestId() {
        System.out.println( "getRequestId" );
        SvcMessage instance = new SvcMessageImpl();
        assertNotNull( "Get RequestId shoud not be null", instance.getRequestId());
    }
    
    public class SvcMessageImpl extends SvcMessage {

        public SvcMessageImpl() {
            super( "", null);
        }

    }
    
}
