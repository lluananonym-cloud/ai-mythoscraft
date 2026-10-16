package online.mythoscraft.core.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/** Versioned schema. New versions are appended as further migration steps, never by editing old ones. */
final class Schema {

    static final int VERSION = 3;

    private Schema() {
    }

    static void migrate(Connection c, SqlDialect d, Logger logger) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_schema (version INT NOT NULL)");
        }
        int current = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(version) FROM mc_schema")) {
            if (rs.next()) {
                current = rs.getInt(1);
            }
        }
        if (current < 1) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_players ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(16) NOT NULL,"
                        + "coins BIGINT NOT NULL DEFAULT 0,"
                        + "gems BIGINT NOT NULL DEFAULT 0,"
                        + "kills INT NOT NULL DEFAULT 0,"
                        + "deaths INT NOT NULL DEFAULT 0,"
                        + "mob_kills INT NOT NULL DEFAULT 0,"
                        + "playtime BIGINT NOT NULL DEFAULT 0,"
                        + "event_points BIGINT NOT NULL DEFAULT 0,"
                        + "events_won INT NOT NULL DEFAULT 0,"
                        + "boss_damage BIGINT NOT NULL DEFAULT 0,"
                        + "relics_found INT NOT NULL DEFAULT 0,"
                        + "crates_opened INT NOT NULL DEFAULT 0,"
                        + "best_streak INT NOT NULL DEFAULT 0,"
                        + "rank_id VARCHAR(32) NULL,"
                        + "rank_expires BIGINT NOT NULL DEFAULT 0,"
                        + "daily_streak INT NOT NULL DEFAULT 0,"
                        + "last_daily VARCHAR(10) NULL,"
                        + "first_join BIGINT NOT NULL DEFAULT 0,"
                        + "last_seen BIGINT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_homes ("
                        + "uuid CHAR(36) NOT NULL,"
                        + "name VARCHAR(32) NOT NULL,"
                        + "world VARCHAR(64) NOT NULL,"
                        + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,"
                        + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL,"
                        + "created BIGINT NOT NULL,"
                        + "PRIMARY KEY (uuid, name))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_transactions ("
                        + "id " + d.autoIncrementPrimaryKey() + ","
                        + "created BIGINT NOT NULL,"
                        + "sender CHAR(36) NULL,"
                        + "receiver CHAR(36) NULL,"
                        + "currency VARCHAR(8) NOT NULL,"
                        + "amount BIGINT NOT NULL,"
                        + "reason VARCHAR(64) NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_arena_inventories ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                        + "arena VARCHAR(64) NOT NULL,"
                        + "data " + d.blob() + " NOT NULL,"
                        + "location VARCHAR(255) NULL,"
                        + "created BIGINT NOT NULL)");
                createIndex(st, d, "idx_mc_players_name", "mc_players (name)");
                createIndex(st, d, "idx_mc_tx_created", "mc_transactions (created)");
                st.executeUpdate("INSERT INTO mc_schema (version) VALUES (1)");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            logger.info("Database schema created (version 1)");
        }
        if (current < 2) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_auctions ("
                        + "id CHAR(36) NOT NULL PRIMARY KEY,"
                        + "seller CHAR(36) NOT NULL,"
                        + "seller_name VARCHAR(16) NOT NULL,"
                        + "item " + d.blob() + " NOT NULL,"
                        + "price BIGINT NOT NULL,"
                        + "currency VARCHAR(8) NOT NULL,"
                        + "created BIGINT NOT NULL,"
                        + "expires BIGINT NOT NULL,"
                        + "state VARCHAR(12) NOT NULL,"
                        + "buyer CHAR(36) NULL,"
                        + "finished BIGINT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_orders ("
                        + "id CHAR(36) NOT NULL PRIMARY KEY,"
                        + "owner CHAR(36) NOT NULL,"
                        + "owner_name VARCHAR(16) NOT NULL,"
                        + "material VARCHAR(64) NOT NULL,"
                        + "amount INT NOT NULL,"
                        + "delivered INT NOT NULL DEFAULT 0,"
                        + "collected INT NOT NULL DEFAULT 0,"
                        + "price_each BIGINT NOT NULL,"
                        + "created BIGINT NOT NULL,"
                        + "expires BIGINT NOT NULL,"
                        + "state VARCHAR(12) NOT NULL)");
                createIndex(st, d, "idx_mc_auctions_state", "mc_auctions (state)");
                createIndex(st, d, "idx_mc_orders_state", "mc_orders (state)");
                st.executeUpdate("INSERT INTO mc_schema (version) VALUES (2)");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            logger.info("Database schema updated to version 2 (auction house, farm orders)");
        }
        if (current < 3) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS mc_progress ("
                        + "uuid CHAR(36) NOT NULL,"
                        + "pkey VARCHAR(64) NOT NULL,"
                        + "value BIGINT NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (uuid, pkey))");
                st.executeUpdate("INSERT INTO mc_schema (version) VALUES (3)");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            logger.info("Database schema updated to version 3 (jobs, achievements, milestones, parkour)");
        }
    }

    /** MySQL DDL commits implicitly, so a half-finished earlier run may already have created the index. */
    private static void createIndex(Statement st, SqlDialect d, String name, String target) throws SQLException {
        if (d == SqlDialect.SQLITE) {
            st.executeUpdate("CREATE INDEX IF NOT EXISTS " + name + " ON " + target);
            return;
        }
        try {
            st.executeUpdate("CREATE INDEX " + name + " ON " + target);
        } catch (SQLException e) {
            if (e.getErrorCode() != 1061) {
                throw e;
            }
        }
    }
}
