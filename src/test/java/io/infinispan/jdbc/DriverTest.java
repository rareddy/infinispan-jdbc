/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infinispan.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.Test;

public class DriverTest {

    @Test
    public void testURL() throws Exception {
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

    //@Test
    public void testDDL() throws SQLException {
        // HotRodTestServer server = new HotRodTestServer(11222);
        Driver d = new Driver();
        Connection c = d.connect("jdbc:infinispan://127.0.0.1:11222/default;schema=src/test/resources/tables.ddl",
                new Properties());
        Statement statement = c.createStatement();
        statement.execute("DELETE FROM G1");
        ResultSet resultSet = statement.executeQuery("SELECT * FROM G1");
        assertFalse(resultSet.next());

        statement = c.createStatement();
        int count = statement.executeUpdate("insert into G1 (e1, e2, e3) values (1, '1', 1.11)");
        assertEquals(1, count);

        statement = c.createStatement();
        resultSet = statement.executeQuery("SELECT e1, e2, e3 FROM G1");
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getObject(1));
        assertEquals("1", resultSet.getObject(2));
        assertEquals(new Float(1.11), resultSet.getObject(3));
        // server.stop();
    }

    //@Test
    public void testProto() throws SQLException {
        // HotRodTestServer server = new HotRodTestServer(11222);
        Driver d = new Driver();
        Connection c = d.connect("jdbc:infinispan://127.0.0.1:11222/default;schema=src/test/resources/ispn.proto",
                new Properties());
        Statement statement = c.createStatement();
        statement.execute("DELETE FROM G1");
        ResultSet resultSet = statement.executeQuery("SELECT * FROM G1");
        assertFalse(resultSet.next());

        statement = c.createStatement();
        int count = statement.executeUpdate("insert into G1 (e1, e2, e3) values (1, '1', 1.11)");
        assertEquals(1, count);

        statement = c.createStatement();
        resultSet = statement.executeQuery("SELECT e1, e2, e3 FROM G1");
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getObject(1));
        assertEquals("1", resultSet.getObject(2));
        assertEquals(new Float(1.11), resultSet.getObject(3));
        // server.stop();
    }
}
