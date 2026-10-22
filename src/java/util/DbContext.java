package util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The only class that knows how to reach SQL Server. Settings are read from
 * db.properties (src/java/db.properties), so another machine only needs that file edited.
 * Works both inside Tomcat and from tool classes started with Run File.
 */
public final class DbContext {

    private static final String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final Properties SETTINGS = loadSettings();

    private DbContext() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQL Server JDBC driver not found in WEB-INF/lib", e);
        }
        return DriverManager.getConnection(SETTINGS.getProperty("db.url"),
                SETTINGS.getProperty("db.user"), SETTINGS.getProperty("db.password"));
    }

    private static Properties loadSettings() {
        Properties props = new Properties();
        // defaults match the workshop sample
        props.setProperty("db.url", "jdbc:sqlserver://localhost:1433;databaseName=PRJ301_GAS;encrypt=false");
        props.setProperty("db.user", "sa");
        props.setProperty("db.password", "12345");
        try (InputStream in = DbContext.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // keep the defaults when the file cannot be read
        }
        return props;
    }
}
