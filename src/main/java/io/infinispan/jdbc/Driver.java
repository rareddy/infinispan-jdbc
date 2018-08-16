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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.teiid.adminapi.AdminException;
import org.teiid.adminapi.VDB;
import org.teiid.cache.Cache;
import org.teiid.cache.CacheFactory;
import org.teiid.core.util.ApplicationInfo;
import org.teiid.core.util.PropertiesUtils;
import org.teiid.deployers.VirtualDatabaseException;
import org.teiid.dqp.internal.datamgr.ConnectorManagerRepository.ConnectorManagerException;
import org.teiid.jdbc.ConnectionImpl;
import org.teiid.runtime.EmbeddedConfiguration;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.infinispan.hotrod.InfinispanExecutionFactory;

import io.infinispan.data.InfinispanConnectionFactory;
import io.infinispan.jdbc.TeiidServer.LocalCache;


/**
 * JDBC Driver class for Infinispan Remote cache cluster. The JDBC URL format is
 * <pre>
 *    jdbc:infinispan://&lt;host&gt;[:&lt;port&gt]/&lt;cache-name&gt;;protobuf=&lt;protobuf-name&gt;[...]    
 * </pre>
 * Sample code looks like
 * <pre>
        Class.forName("io.infinispan.jdbc.Driver");
        Connection conn = DriverManager.getConnection("jdbc:infinispan://localhost:11222/addressbook_indexed;protobuf=/quickstart/addressbook.proto");
        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("select id, name, email from Person");
        writeResultSet(resultSet);           
 * </pre>
 * 
 * The following are allowed properties on the URL
 * <pre>
 *    protobuf => Name of the protobuf file name in the cache (required). Based on the name, the file is read directly from cache.
 *    username => if cache is secured, defines the login user name
 *    password => if cache is secured, defines the login password
 *    saslMechanism => authentication mechanism. Allowed values are "CRAM-MD5", "DIGEST-MD5", "PLAIN".
 *    authenticationRealm => if cache is secured with external server
 *    authenticationServerName => if cache is secured with external server
 * </pre>
 */

public class Driver implements java.sql.Driver {
    private static final String UTF_8 = "UTF-8"; //$NON-NLS-1$

    static Logger logger = Logger.getLogger("org.infinispan.jdbc"); //$NON-NLS-1$
    static final String DRIVER_NAME = "Infinispan JDBC Driver"; //$NON-NLS-1$
    static final String JDBC_PROTOCOL = "jdbc:infinispan:"; //$NON-NLS-1$
    static final String URL_PATTERN = JDBC_PROTOCOL + "(?://([^;]*))?(;.*)?"; //$NON-NLS-1$
    
    static Pattern urlPattern = Pattern.compile(URL_PATTERN);    
    
    private static Driver INSTANCE = new Driver();
    private static TeiidServer TEIID;
    
    static {
        try {
            DriverManager.registerDriver(INSTANCE);
        } catch(SQLException e) {
            logger.log(Level.SEVERE, "Error registering the Infinispan JDBC Driver");
        }
    }
    
    public static Driver getInstance() {
        return INSTANCE;
    }
    
    public Driver() {        
    }
    
    public ConnectionImpl connect(String url, Properties info) throws SQLException {
        Matcher m = urlPattern.matcher(url);
        if (!m.matches()) {
            return null;
        }
        if(info == null) {
            // create a properties obj if it is null
            info = new Properties();
        } else {
            //don't modify the original
            info = PropertiesUtils.clone(info);
        }
        Properties p = parseURL(url, info);
        String vdbName = initTeiid(p);
        
        ConnectionImpl myConnection = TEIID.getDriver()
                .connect("jdbc:teiid:" + vdbName 
                        + ";useCallingThread=true;autoFailover=true;waitForLoad=5000;", info);
        return myConnection;
    }

    private String initTeiid(Properties p) throws SQLException {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            if (TEIID == null) {
                TEIID = teiidServer();
            }
            if (!TEIID.hasConnectorManagerRepository(p.getProperty("cache"))) {
                TEIID.addConnectionFactory(p.getProperty("cache"), buildConnectionFactory(p));
            }            
            return buildAndDeployTeiidVDB(p, TEIID);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private InfinispanConnectionFactory buildConnectionFactory(Properties p) {
        InfinispanConnectionFactory factory = new InfinispanConnectionFactory();
        factory.setCacheName(p.getProperty("cache"));
        String server = p.getProperty("host");
        if (p.getProperty("port") != null) {
            server = server + ":" + p.getProperty("port");
        }
        factory.setRemoteServerList(server);
        if (p.getProperty("username") != null) {
            factory.setUserName(p.getProperty("username"));    
        }
        if (p.getProperty("password") != null) {
            factory.setPassword(p.getProperty("password"));    
        }        
        if (p.getProperty("saslMechanism") != null) {
            factory.setSaslMechanism(p.getProperty("saslMechanism"));    
        }        
        if (p.getProperty("authenticationRealm") != null) {
            factory.setAuthenticationRealm(p.getProperty("authenticationRealm"));    
        }
        if (p.getProperty("authenticationServerName") != null) {
            factory.setAuthenticationServerName(p.getProperty("authenticationServerName"));    
        }
        return factory;
    }
    
    /**
     * Returns true if the driver thinks that it can open a connection to the given URL.
     * Expected URL format is
     * jdbc:infinispan://server:port/CACHE;protobuf=xyz.proto;user=username;password=password
     * 
     * @param The URL used to establish a connection.
     * @return A boolean value indicating whether the driver understands the subprotocol.
     * @throws SQLException, should never occur
     */
    public boolean acceptsURL(String url) throws SQLException {
        return urlPattern.matcher(url).matches();
    }

    public int getMajorVersion() {
        return ApplicationInfo.getInstance().getMajorReleaseVersion();
    }

    public int getMinorVersion() {
        return ApplicationInfo.getInstance().getMinorReleaseVersion();
    }

    public String getDriverName() {
        return DRIVER_NAME;
    }
    
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        if(info == null) {
            info = new Properties();
        } else {
            info = PropertiesUtils.clone(info);
        }

        info = parseURL(url, info);

        // construct list of driverPropertyInfo objects
        List<DriverPropertyInfo> driverProps = new LinkedList<DriverPropertyInfo>();

        DriverPropertyInfo protobuf = new DriverPropertyInfo("protobuf", info.getProperty("protobuf"));
        protobuf.description = "Name of the protobuf file name in the cache (required). Based on the name, the file is read directly from cache.";
        protobuf.required = false;
        
        DriverPropertyInfo username = new DriverPropertyInfo("username", info.getProperty("username"));
        username.description = "if cache is secured, defines the login user name";
        username.required = false;

        DriverPropertyInfo password = new DriverPropertyInfo("password", info.getProperty("password"));
        password.description = "if cache is secured, defines the login password";
        password.required = false;
        
        DriverPropertyInfo saslMechanism = new DriverPropertyInfo("saslMechanism", info.getProperty("saslMechanism"));
        saslMechanism.description = "Authentication mechanism";
        saslMechanism.required = false;
        saslMechanism.choices = new String[] {"CRAM-MD5", "DIGEST-MD5", "PLAIN"};

        DriverPropertyInfo authenticationRealm = new DriverPropertyInfo("authenticationRealm", info.getProperty("authenticationRealm"));
        authenticationRealm.description = "if cache is secured with external server";
        authenticationRealm.required = false;
        
        DriverPropertyInfo authenticationServerName = new DriverPropertyInfo("authenticationServerName", info.getProperty("authenticationServerName"));
        authenticationServerName.description = "if cache is secured with external server";
        authenticationServerName.required = false;

        driverProps.add(protobuf);
        driverProps.add(username);
        driverProps.add(password);
        driverProps.add(saslMechanism);
        driverProps.add(authenticationServerName);
        driverProps.add(authenticationRealm);
        
        // create an array of DriverPropertyInfo objects
        DriverPropertyInfo [] propInfo = new DriverPropertyInfo[driverProps.size()];

        // copy the elements from the list to the array
        return driverProps.toArray(propInfo);
    }

    protected Properties parseURL(String jdbcURL) {
        return parseURL(jdbcURL, new Properties());
    }
    
    protected Properties parseURL(String jdbcURL, Properties p) {
        if (jdbcURL == null) {
            throw new IllegalArgumentException();
        }
        // Trim extra spaces
        jdbcURL = jdbcURL.trim();
        if (jdbcURL.length() == 0) {
            throw new IllegalArgumentException();
        }
        
        Matcher m = urlPattern.matcher(jdbcURL);
        if (!m.matches()) {
            throw new IllegalArgumentException();
        }
        
        String connectionURL = m.group(1);
        if (connectionURL != null) {
            connectionURL = getValidValue(connectionURL.trim());
            int idx = connectionURL.indexOf('/');
            if (idx != -1 ) {
                String cache = connectionURL.substring(idx+1);
                p.setProperty("cache", cache);
                connectionURL = connectionURL.substring(0, idx);
            }
            int portidx = connectionURL.indexOf(':');
            if (portidx != -1) {
                p.setProperty("port", connectionURL.substring(portidx+1).trim());
                p.setProperty("host", connectionURL.substring(0, portidx).trim());
            } else {
                p.setProperty("port", "11222");
                p.setProperty("host", connectionURL.trim());                    
            }            
        }
        
        String props = m.group(2);
        if (props != null) {
            parseConnectionProperties(props, p);
        }
        return p;
    }    
    
    static void parseConnectionProperties(String connectionInfo, Properties p) {
        String[] connectionParts = connectionInfo.split(";"); //$NON-NLS-1$
        if (connectionParts.length != 0) {
            // The rest should be connection params
            for (int i = 0; i < connectionParts.length; i++) {
                parseConnectionProperty(connectionParts[i], p);
            }
        }
    }   
    
    static void parseConnectionProperty(String connectionProperty, Properties p) {
        if (connectionProperty.length() == 0) {
            // Be tolerant of double-semicolons and dangling semicolons
            return;
        } else if(connectionProperty.length() < 3) {
            // key=value must have at least 3 characters
            throw new IllegalArgumentException();
        }
        int firstEquals = connectionProperty.indexOf('=');
        if(firstEquals < 1) {
            throw new IllegalArgumentException();
        } 
        String key = connectionProperty.substring(0, firstEquals).trim();
        String value = connectionProperty.substring(firstEquals+1).trim();        
        if(value.indexOf('=') >= 0) {
            throw new IllegalArgumentException();
        }        
        p.setProperty(getValidValue(key), getValidValue(value));
    }
    
    private static String getValidValue(String value) {
        try {
            // Decode the value of the property if incase they were encoded.
            return URLDecoder.decode(value, UTF_8);
        } catch (UnsupportedEncodingException e) {
            // use the original value
        }            
        return value;
    }  
    
    /**
     * This method returns true if the driver passes jdbc compliance tests.
     * @return true if the driver is jdbc complaint, else false.
     */
    public boolean jdbcCompliant() {
        return false;
    }

    public Logger getParentLogger() {
        return logger;
    }
    
    private TeiidServer teiidServer() {
        logger.info("Starting Teiid Server.");
        
        // turning off PostgreSQL support
        System.setProperty("org.teiid.addPGMetadata", "false");
        
        final TeiidServer server = new TeiidServer();
        
        EmbeddedConfiguration config = new EmbeddedConfiguration();
        config.setUseDisk(false);
        config.setCacheFactory(new CacheFactory() {
            @Override
            public <K, V> Cache<K, V> get(String name) {
                return new LocalCache<>(name, 10);
            }
            @Override
            public void destroy() {
            }
        });
        
        server.start(config);
        server.addTranslator("infinispan-hotrod", new InfinispanExecutionFactory());
        return server;
    }
    
    private String buildAndDeployTeiidVDB(Properties p, TeiidServer ts) throws SQLException {
        String vdb = 
                "<vdb name=\"{cache}\" version=\"1\">\n" + 
                "    <model name=\"ispn\">\n" + 
                "        <property name=\"importer.ProtobufName\" value=\"{protobuf}\"/>\n" + 
                "        <source name=\"{host}\" translator-name=\"infinispan-hotrod\" connection-jndi-name=\"{cache}\"/>\n" + 
                "        <metadata type = \"NATIVE\"/>\n" + 
                "    </model>\n" + 
                "</vdb>";
        vdb = vdb.replace("{cache}", p.getProperty("cache"));
        vdb = vdb.replace("{host}", p.getProperty("host"));
        vdb = vdb.replace("{protobuf}", p.getProperty("protobuf"));
        
        try {
            VDB v = ts.getAdmin().getVDB(p.getProperty("cache"), "1");
            if (v == null) {
                ts.deployVDB(new ByteArrayInputStream(vdb.getBytes()));
            }
            logger.finer(ts.getAdmin().getSchema(p.getProperty("cache"), "1", "ispn", null, null));
        } catch (VirtualDatabaseException | ConnectorManagerException | TranslatorException | IOException
                | AdminException e) {
            throw new SQLException(e.getMessage());
        }
        return p.getProperty("cache");
    }
}


