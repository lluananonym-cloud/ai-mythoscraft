package online.mythoscraft.core.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** MySQL/MariaDB through the driver bundled with Paper. One validated connection on the database thread. */
public final class MysqlConnectionProvider implements ConnectionProvider {

    private static final long VALIDATE_AFTER_MILLIS = 30_000;

    private final String url;
    private final String host;
    private final String database;
    private final Properties properties = new Properties();
    private Connection connection;
    private long lastUse;

    public MysqlConnectionProvider(String host, int port, String database, String user, String password, boolean ssl) {
        this.host = host + ":" + port;
        this.database = database;
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("useSSL", String.valueOf(ssl));
        properties.setProperty("allowPublicKeyRetrieval", "true");
        properties.setProperty("characterEncoding", "utf8");
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "30000");
        properties.setProperty("tcpKeepAlive", "true");
    }

    @Override
    public Connection connection() throws SQLException {
        long now = System.currentTimeMillis();
        if (connection != null && !connection.isClosed()) {
            if (now - lastUse < VALIDATE_AFTER_MILLIS || connection.isValid(3)) {
                lastUse = now;
                return connection;
            }
            close();
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.mariadb.jdbc.Driver");
            } catch (ClassNotFoundException e2) {
                throw new SQLException("No MySQL/MariaDB driver available on this server", e);
            }
        }
        connection = DriverManager.getConnection(url, properties);
        lastUse = now;
        return connection;
    }

    @Override
    public SqlDialect dialect() {
        return SqlDialect.MYSQL;
    }

    @Override
    public void reset() {
        close();
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing anyway
            }
            connection = null;
        }
    }

    @Override
    public String describe() {
        return "MySQL (" + host + "/" + database + ")";
    }
}
