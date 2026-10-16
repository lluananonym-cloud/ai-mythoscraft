package online.mythoscraft.core.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Supplies the single connection used by the database thread. Implementations are only ever touched from that
 * thread, so they need no synchronisation.
 */
public interface ConnectionProvider {

    Connection connection() throws SQLException;

    SqlDialect dialect();

    /** Drops the current connection so the next call reconnects. */
    void reset();

    void close();

    /** Human readable target for logs and /mythos status – never contains credentials. */
    String describe();
}
