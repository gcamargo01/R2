/* JavaScriptTest.java */
package uy.com.r2.svc.tools.test;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import uy.com.r2.core.api.Configuration;
import uy.com.r2.core.api.SvcMessage;
import uy.com.r2.core.api.SvcRequest;
import uy.com.r2.core.api.SvcResponse;
import uy.com.r2.svc.tools.JavaScript;

/** JavaScript service test.
 * @author Gustavo Camargo
 */
public class JavaScriptTest {
    JavaScript svc;
    Configuration cfg;
    
    public JavaScriptTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        org.apache.log4j.BasicConfigurator.configure();
        svc = new JavaScript();
        cfg = new Configuration( svc.getConfigDescriptors());
        cfg.put( "Script", 
                "function onRequest_Test( rq) { \n" + 
                "  rq.put( \"Field2\", \"ValueTwo\" + rq.get( \"Field\")); \n" + 
                "}" + 
                "function onResponse_Test( rq) { \n" + 
                "  rq.put( \"Field3\", \"ValueThree\"); \n" + 
                "}"
        );
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of onRequest method, of class JavaScript.
     */
    @Test
    public void testOnRequest() throws Exception {
        System.out.println( "onRequest" );
        SvcRequest req = new SvcRequest( "TEST", 0, 0, "Test", null, 1000);
        req.put( "Field", "1");
        SvcMessage result = svc.onRequest( req, cfg);
        assertEquals( "ValueTwo1", result.get( "Field2"));
    }

    /**
     * Test of onResponse method, of class JavaScript.
     */
    @Test
    public void testOnResponse() throws Exception {
        System.out.println( "onResponse" );
        SvcRequest req = new SvcRequest( "TEST", 0, 0, "Test", null, 1000);
        SvcResponse res = new SvcResponse( 0, req);
        SvcMessage result = svc.onResponse( res, cfg);
        assertEquals( "ValueThree", result.get( "Field3"));
    }

}
