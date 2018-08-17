package io.infinispan.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Test;

public class DriverTest {

    @org.junit.Test
    public void testURL() throws Exception{
        Driver d = new Driver();
        
        assertTrue(d.acceptsURL("jdbc:infinispan://localhost:1234/mycache"));
        assertFalse(d.acceptsURL("jdbc:infinispan:@//localhost:1234/mycache"));
        
        
        assertEquals("localhost", d.parseURL("jdbc:infinispan://localhost:1234/mycache").getProperty("host"));
        assertEquals("1234", d.parseURL("jdbc:infinispan://localhost:1234/mycache").getProperty("port"));
        assertEquals("mycache", d.parseURL("jdbc:infinispan://localhost:1234/mycache").getProperty("cache"));
        
        assertEquals("localhost", d.parseURL("jdbc:infinispan://localhost/mycache").getProperty("host"));
        assertEquals("11222", d.parseURL("jdbc:infinispan://localhost/mycache").getProperty("port"));
        assertEquals("mycache", d.parseURL("jdbc:infinispan://localhost/mycache").getProperty("cache"));        

        assertEquals("localhost", d.parseURL("jdbc:infinispan://localhost").getProperty("host"));
        assertEquals("11222", d.parseURL("jdbc:infinispan://localhost").getProperty("port"));
        assertEquals(null, d.parseURL("jdbc:infinispan://localhost").getProperty("cache"));        

        assertEquals("value", d.parseURL("jdbc:infinispan://localhost:1234/mycache;prop=value").getProperty("prop"));
        
    }
    
/*    @Test
    public void connection() throws Exception {
        Class.forName("io.infinispan.jdbc.Driver");
        Connection conn = DriverManager.getConnection("jdbc:infinispan://localhost:11222/addressbook_indexed;protobuf=/quickstart/addressbook.proto");
        Statement statement = conn.createStatement();
        statement.execute("insert into PhoneNumber (number, type, Person_id) values ('3333', 2, 3)");
        //ResultSet resultSet = statement.execute("update Person set email = 'tristin@redhat.com' where id = 3");
        //writeResultSet(resultSet);          
    }
    
    private void writeResultSet(ResultSet rs) throws SQLException {
        while (rs.next()) {
            System.out.print(rs.getObject(1));
            System.out.print(",");
            System.out.println(rs.getObject(2));
        }
    } */   
}
