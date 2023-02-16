package play.db;

import jregex.Matcher;
import org.apache.commons.lang.StringUtils;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.db.DB.ExtendedDatasource;
import play.exceptions.DatabaseException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.rds.auth.GetIamAuthTokenRequest;
import com.amazonaws.services.rds.auth.RdsIamAuthTokenGenerator;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.io.File;
import java.sql.*;
import java.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class DBPlugin extends PlayPlugin {

    public static String url = "";
    public static DefaultAWSCredentialsProviderChain creds;
    public static String AWS_ACCESS_KEY;
    public static String AWS_SECRET_KEY;

    private static final String SSL_CERTIFICATE = "rds-ca-2019-root.pem";
    private static final String KEY_STORE_TYPE = "JKS";
    private static final String KEY_STORE_PROVIDER = "SUN";
    private static final String KEY_STORE_FILE_PREFIX = "sys-connect-via-ssl-cacerts";
    private static final String KEY_STORE_FILE_SUFFIX = ".jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "Jo1nEdUpByBeeL1n3!@#";
   
    protected DataSourceFactory factory(Configuration dbConfig) {
        String dbFactory = dbConfig.getProperty("db.factory", "play.db.hikaricp.HikariDataSourceFactory");
        try {
            return (DataSourceFactory) Class.forName(dbFactory).newInstance();
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Expected implementation of " + DataSourceFactory.class.getName() + 
                ", but received: " + dbFactory);
        }
    }
    
    @Override
    public void onApplicationStart() {
        if (changed()) {
            String dbName = "";
            try {
                // Destroy all connections
                if (!DB.datasources.isEmpty()) {
                    DB.destroyAll();
                }
                
                // Define common parameter here
                if (play.Logger.usesJuli()) {
                    System.setProperty("com.mchange.v2.log.MLog", "jul");
                } else {
                    System.setProperty("com.mchange.v2.log.MLog", "log4j");
                }
                
                Set<String> dbNames = Configuration.getDbNames();
                Iterator<String> it = dbNames.iterator();
                while (it.hasNext()) {
                    dbName = it.next();
                    Configuration dbConfig = new Configuration(dbName);

                    if (dbConfig.getProperty("db.url") != null
                            && dbConfig.getProperty("db.url").toLowerCase().startsWith("jdbc:iampostgresql")) {
                        Logger.info("initializing ssl and aim auth settings");
                        setSslProperties();
                        creds = new DefaultAWSCredentialsProviderChain();
                        AWS_ACCESS_KEY = creds.getCredentials().getAWSAccessKeyId();
                        AWS_SECRET_KEY = creds.getCredentials().getAWSSecretKey();
                    }
                    
                    boolean isJndiDatasource = false;
                    String datasourceName = dbConfig.getProperty("db", "");

                    // Identify datasource JNDI lookup name by 'jndi:' or 'java:' prefix 
                    if (datasourceName.startsWith("jndi:")) {
                        datasourceName = datasourceName.substring("jndi:".length());
                        isJndiDatasource = true;
                    }

                    if (isJndiDatasource || datasourceName.startsWith("java:")) {
                        Context ctx = new InitialContext();
                        DataSource ds =  (DataSource) ctx.lookup(datasourceName);
                        DB.datasource = ds;
                        DB.destroyMethod = "";
                        DB.ExtendedDatasource extDs = new DB.ExtendedDatasource(ds, "");
                        DB.datasources.put(dbName, extDs);  
                    } else {

                        // Try the driver
                        String driver = dbConfig.getProperty("db.driver");
                        try {
                            Driver d = (Driver) Class.forName(driver, true, Play.classloader).newInstance();
                            DriverManager.registerDriver(new ProxyDriver(d));
                        } catch (Exception e) {
                            throw new Exception("Database [" + dbName + "] Driver not found (" + driver + ")", e);
                        }

                        // Try the connection
                        Connection fake = null;
                        try {
                            if (dbConfig.getProperty("db.user") == null) {
                                fake = DriverManager.getConnection(dbConfig.getProperty("db.url"));
                            } else if (dbConfig.getProperty("db.url") != null
                                    && dbConfig.getProperty("db.url").toLowerCase().startsWith("jdbc:iampostgresql")) {
                                java.util.Properties info = new java.util.Properties();
                                info.put("user", dbConfig.getProperty("db.user"));
                                info.put("password", generateAuthToken(dbConfig));
                                info.put("delegateJdbcDriverSchemeName", "postgresql");
                                info.put("delegateJdbcDriverClass", dbConfig.getProperty("db.driver"));
                                info.put("verifyServerCertificate", "true");
                                info.put("useSSL", "true");
                                info.put("sslmode", "verify-full");
                                info.put("sslmode", "require");
                                info.put("awsRegion", dbConfig.getProperty("aws.region", "eu-west-2"));
                                fake = DriverManager.getConnection(dbConfig.getProperty("db.url"), info);
                            } else {
                                fake = DriverManager.getConnection(dbConfig.getProperty("db.url"), dbConfig.getProperty("db.user"), dbConfig.getProperty("db.pass"));
                            }
                        } finally {
                            if (fake != null) {
                                fake.close();
                            }
                        }

                        DataSource ds = factory(dbConfig).createDataSource(dbConfig);
                       
                        // Current datasource. This is actually deprecated. 
                        String destroyMethod = dbConfig.getProperty("db.destroyMethod", "");
                        DB.datasource = ds;
                        DB.destroyMethod = destroyMethod;

                        DB.ExtendedDatasource extDs = new DB.ExtendedDatasource(ds, destroyMethod);

                        url = testDataSource(ds);
                        Logger.info("Connected to %s for %s", url, dbName);
                        DB.datasources.put(dbName, extDs);
                    }
                }
                
            } catch (Exception e) {
                DB.datasource = null;
                Logger.error(e, "Database [%s] Cannot connected to the database : %s", dbName, e.getMessage());
                if (e.getCause() instanceof InterruptedException) {
                    throw new DatabaseException("Cannot connected to the database["+ dbName + "]. Check the configuration.", e);
                }
                throw new DatabaseException("Cannot connected to the database["+ dbName + "], " + e.getMessage(), e);
            }
        }
    }

    public static String generateAuthToken(Configuration dbConfig) {

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY);

        RdsIamAuthTokenGenerator generator = RdsIamAuthTokenGenerator.builder()
                .credentials(new AWSStaticCredentialsProvider(awsCredentials)).region(dbConfig.getProperty("aws.region", "eu-west-2")).build();

        Map<String, Integer> map = new HashMap<String, Integer>();
        int slashing = dbConfig.getProperty("db.url").indexOf("//") + 2;
        String sub = dbConfig.getProperty("db.url").substring(slashing, dbConfig.getProperty("db.url").indexOf("/", slashing));
        String[] splitted = sub.split(":");

        String authToken = generator.getAuthToken(
                GetIamAuthTokenRequest.builder()
                        .hostname(splitted[0])
                        .port(Integer.valueOf(dbConfig.getProperty("db.port", "5432")))
                        .userName(dbConfig.getProperty("db.user"))
                        .build());

        return authToken;
    }

    /**
     * This method sets the SSL properties which specify the key store file, its type and password:
     * @throws Exception
     */
    private static void setSslProperties() throws Exception {
        System.setProperty("javax.net.ssl.trustStore", createKeyStoreFile());
        System.setProperty("javax.net.ssl.trustStoreType", KEY_STORE_TYPE);
        System.setProperty("javax.net.ssl.trustStorePassword", DEFAULT_KEY_STORE_PASSWORD);
    }

    /**
     * This method returns the path of the Key Store File needed for the SSL verification during the IAM Database Authentication to
     * the db instance.
     * @return
     * @throws Exception
     */
    private static String createKeyStoreFile() throws Exception {
        return createKeyStoreFile(createCertificate()).getPath();
    }

    /**
     *  This method generates the SSL certificate
     * @return
     * @throws Exception
     */
    private static X509Certificate createCertificate() throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        URL url = new File(SSL_CERTIFICATE).toURI().toURL();
        if (url == null) {
            throw new Exception();
        }
        try (InputStream certInputStream = url.openStream()) {
            return (X509Certificate) certFactory.generateCertificate(certInputStream);
        }
    }

    /**
     * This method creates the Key Store File
     * @param rootX509Certificate - the SSL certificate to be stored in the KeyStore
     * @return
     * @throws Exception
     */
    private static File createKeyStoreFile(final X509Certificate rootX509Certificate) throws Exception {
        File keyStoreFile = File.createTempFile(KEY_STORE_FILE_PREFIX, KEY_STORE_FILE_SUFFIX);
        try (FileOutputStream fos = new FileOutputStream(keyStoreFile.getPath())) {
            KeyStore ks = KeyStore.getInstance(KEY_STORE_TYPE, KEY_STORE_PROVIDER);
            ks.load(null);
            ks.setCertificateEntry("rootCaCertificate", rootX509Certificate);
            ks.store(fos, DEFAULT_KEY_STORE_PASSWORD.toCharArray());
        }
        return keyStoreFile;
    }

    /**
     * This method clears the SSL properties.
     * @throws Exception
     */
    private static void clearSslProperties() throws Exception {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStoreType");
        System.clearProperty("javax.net.ssl.trustStorePassword");
    }

    protected String testDataSource(DataSource ds) throws SQLException {
        try (Connection connection = ds.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }
    
    @Override
    public void onApplicationStop() {
        if (Play.mode.isProd()) {
            DB.destroyAll();
        }
    }
    
    @Override
    public void invocationFinally() {
        DB.closeAll();
    }

    private static void check(Configuration config, String mode, String property) {
        if (!StringUtils.isEmpty(config.getProperty(property))) {
            Logger.warn("Ignoring " + property + " because running the in " + mode + " db.");
        }
    }

    private boolean changed() {
        Set<String> dbNames = Configuration.getDbNames();
        
        for (String dbName : dbNames) {
            Configuration dbConfig = new Configuration(dbName);
            
            if ("mem".equals(dbConfig.getProperty("db")) && dbConfig.getProperty("db.url") == null) {
                dbConfig.put("db.driver", "org.h2.Driver");
                dbConfig.put("db.url", "jdbc:h2:mem:play;MODE=MYSQL");
                dbConfig.put("db.user", "sa");
                dbConfig.put("db.pass", "");
            }

            if ("fs".equals(dbConfig.getProperty("db")) && dbConfig.getProperty("db.url") == null) {
                dbConfig.put("db.driver", "org.h2.Driver");
                dbConfig.put("db.url", "jdbc:h2:" + (new File(Play.applicationPath, "db/h2/play").getAbsolutePath()) + ";MODE=MYSQL");
                dbConfig.put("db.user", "sa");
                dbConfig.put("db.pass", "");
            }
            String datasourceName = dbConfig.getProperty("db", "");
            DataSource ds = DB.getDataSource(dbName);
                     
            if ((datasourceName.startsWith("java:") || datasourceName.startsWith("jndi:")) && dbConfig.getProperty("db.url") == null) {
                if (ds == null) {
                    return true;
                }
            } else {
                // Internal pool is c3p0, we should call the close() method to destroy it.
                check(dbConfig, "internal pool", "db.destroyMethod");

                dbConfig.put("db.destroyMethod", "close");
            }

            Matcher m = new jregex.Pattern("^mysql:(//)?(({user}[a-zA-Z0-9_]+)(:({pwd}[^@]+))?@)?(({host}[^/]+)/)?({name}[a-zA-Z0-9_]+)(\\?)?({parameters}[^\\s]+)?$").matcher(dbConfig.getProperty("db", ""));
            if (m.matches()) {
                String user = m.group("user");
                String password = m.group("pwd");
                String name = m.group("name");
                String host = m.group("host");
                String parameters = m.group("parameters");
                
                dbConfig.put("db.driver", "com.mysql.jdbc.Driver");
                dbConfig.put("db.url", "jdbc:mysql://" + (host == null ? "localhost" : host) + "/" + name + "?" + parameters);
                if (user != null) {
                    dbConfig.put("db.user", user);
                }
                if (password != null) {
                    dbConfig.put("db.pass", password);
                }
            }
            
            m = new jregex.Pattern("^postgres:(//)?(({user}[a-zA-Z0-9_]+)(:({pwd}[^@]+))?@)?(({host}[^/]+)/)?({name}[^\\s]+)$").matcher(dbConfig.getProperty("db", ""));
            if (m.matches()) {
                String user = m.group("user");
                String password = m.group("pwd");
                String name = m.group("name");
                String host = m.group("host");
                dbConfig.put("db.driver", "org.postgresql.Driver");
                dbConfig.put("db.url", "jdbc:postgresql://" + (host == null ? "localhost" : host) + "/" + name);
                if (user != null) {
                    dbConfig.put("db.user", user);
                }
                if (password != null) {
                    dbConfig.put("db.pass", password);
                }
            }

            if(dbConfig.getProperty("db.url") != null && dbConfig.getProperty("db.url").startsWith("jdbc:h2:mem:")) {
                dbConfig.put("db.driver", "org.h2.Driver");
                dbConfig.put("db.user", "sa");
                dbConfig.put("db.pass", "");
            }

            if ((dbConfig.getProperty("db.driver") == null) || (dbConfig.getProperty("db.url") == null)) {
                return false;
            }
            
            if (ds == null) {
                return true;
            } else {
                DataSourceFactory factory = factory(dbConfig);
                
                if (!dbConfig.getProperty("db.driver").equals(factory.getDriverClass(ds))) {
                    return true;
                }
                if (!dbConfig.getProperty("db.url").equals(factory.getJdbcUrl(ds))) {
                    return true;
                }
                if (!dbConfig.getProperty("db.user", "").equals(factory.getUser(ds))) {
                    return true;
                }
            }

            ExtendedDatasource extDataSource = DB.datasources.get(dbName);

            if (extDataSource != null && !dbConfig.getProperty("db.destroyMethod", "").equals(extDataSource.getDestroyMethod())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Needed because DriverManager will not load a driver ouside of the system classloader
     */
    public static class ProxyDriver implements Driver {

        private Driver driver;

        ProxyDriver(Driver d) {
            this.driver = d;
        }

        @Override
        public boolean acceptsURL(String u) throws SQLException {
            return this.driver.acceptsURL(u);
        }

        @Override
        public Connection connect(String u, Properties p) throws SQLException {
            return this.driver.connect(u, p);
        }

        @Override
        public int getMajorVersion() {
            return this.driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return this.driver.getMinorVersion();
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
            return this.driver.getPropertyInfo(u, p);
        }

        @Override
        public boolean jdbcCompliant() {
            return this.driver.jdbcCompliant();
        }
      
        // Method not annotated with @Override since getParentLogger() is a new method
        // in the CommonDataSource interface starting with JDK7 and this annotation
        // would cause compilation errors with JDK6.
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            try {
                return (java.util.logging.Logger) Driver.class.getDeclaredMethod("getParentLogger").invoke(this.driver);
            } catch (Throwable e) {
                return null;
            }
        }
    }
}
