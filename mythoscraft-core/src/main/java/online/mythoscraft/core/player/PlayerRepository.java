package online.mythoscraft.core.player;

import online.mythoscraft.core.database.SqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL for player rows and homes. Called on the database thread only. */
public final class PlayerRepository {

    private static final List<String> COLUMNS = List.of("uuid", "name", "coins", "gems", "kills", "deaths", "mob_kills",
            "playtime", "event_points", "events_won", "boss_damage", "relics_found", "crates_opened", "best_streak",
            "rank_id", "rank_expires", "daily_streak", "last_daily", "first_join", "last_seen");
    private static final String SELECT = "SELECT " + String.join(", ", COLUMNS) + " FROM mc_players";

    private final String upsertPlayer;
    private final String upsertHome;

    public PlayerRepository(SqlDialect dialect) {
        this.upsertPlayer = dialect.upsert("mc_players", List.of("uuid"), COLUMNS);
        this.upsertHome = dialect.upsert("mc_homes", List.of("uuid", "name"),
                List.of("uuid", "name", "world", "x", "y", "z", "yaw", "pitch", "created"));
    }

    public PlayerData.Snapshot load(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    public void save(Connection c, PlayerData.Snapshot s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(upsertPlayer)) {
            bind(ps, s);
            ps.executeUpdate();
        }
    }

    public void saveAll(Connection c, List<PlayerData.Snapshot> snapshots) throws SQLException {
        if (snapshots.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(upsertPlayer)) {
            for (PlayerData.Snapshot s : snapshots) {
                bind(ps, s);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Case-insensitive lookup of the most recently seen player with that name. */
    public PlayerData.Snapshot findByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE LOWER(name) = LOWER(?) ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    public List<PlayerData.Snapshot> top(Connection c, Stat stat, int limit) throws SQLException {
        List<PlayerData.Snapshot> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + " ORDER BY " + stat.column() + " DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs));
                }
            }
        }
        return out;
    }

    public int countPlayers(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM mc_players"); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---- homes --------------------------------------------------------------------------------------------

    public Map<String, Home> loadHomes(Connection c, UUID uuid) throws SQLException {
        Map<String, Home> homes = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, world, x, y, z, yaw, pitch, created FROM mc_homes WHERE uuid = ? ORDER BY created")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Home h = new Home(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                            rs.getFloat(6), rs.getFloat(7), rs.getLong(8));
                    homes.put(h.name().toLowerCase(), h);
                }
            }
        }
        return homes;
    }

    public void saveHome(Connection c, UUID uuid, Home h) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(upsertHome)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, h.name());
            ps.setString(3, h.world());
            ps.setDouble(4, h.x());
            ps.setDouble(5, h.y());
            ps.setDouble(6, h.z());
            ps.setFloat(7, h.yaw());
            ps.setFloat(8, h.pitch());
            ps.setLong(9, h.created());
            ps.executeUpdate();
        }
    }

    public void deleteHome(Connection c, UUID uuid, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM mc_homes WHERE uuid = ? AND LOWER(name) = LOWER(?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    // ---- mapping ------------------------------------------------------------------------------------------

    private static void bind(PreparedStatement ps, PlayerData.Snapshot s) throws SQLException {
        int i = 1;
        ps.setString(i++, s.uuid().toString());
        ps.setString(i++, s.name());
        ps.setLong(i++, s.coins());
        ps.setLong(i++, s.gems());
        ps.setInt(i++, s.kills());
        ps.setInt(i++, s.deaths());
        ps.setInt(i++, s.mobKills());
        ps.setLong(i++, s.playtime());
        ps.setLong(i++, s.eventPoints());
        ps.setInt(i++, s.eventsWon());
        ps.setLong(i++, s.bossDamage());
        ps.setInt(i++, s.relicsFound());
        ps.setInt(i++, s.cratesOpened());
        ps.setInt(i++, s.bestStreak());
        ps.setString(i++, s.rankId());
        ps.setLong(i++, s.rankExpires());
        ps.setInt(i++, s.dailyStreak());
        ps.setString(i++, s.lastDaily());
        ps.setLong(i++, s.firstJoin());
        ps.setLong(i, s.lastSeen());
    }

    private static PlayerData.Snapshot read(ResultSet rs) throws SQLException {
        return new PlayerData.Snapshot(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("coins"),
                rs.getLong("gems"), rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("mob_kills"), rs.getLong("playtime"),
                rs.getLong("event_points"), rs.getInt("events_won"), rs.getLong("boss_damage"), rs.getInt("relics_found"),
                rs.getInt("crates_opened"), rs.getInt("best_streak"), rs.getString("rank_id"), rs.getLong("rank_expires"),
                rs.getInt("daily_streak"), rs.getString("last_daily"), rs.getLong("first_join"), rs.getLong("last_seen"));
    }
}
