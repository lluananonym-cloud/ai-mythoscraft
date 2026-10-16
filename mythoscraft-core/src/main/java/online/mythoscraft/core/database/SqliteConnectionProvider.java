package online.mythoscraft.core.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteConnectionProvider implements ConnectionProvider {

    private final File file;
    private Connection connection;

    public SqliteConnectionProvider(File file) {
        this.file = file;
    }

    @Override
    public Connection connection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found. Paper normally ships it – is this a Paper/Spigot server?", e);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create directory " + parent);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    @Override
    public SqlDialect dialect() {
        return SqlDialect.SQLITE;
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
        return "SQLite (" + file.getName() + ")";
    }
}
