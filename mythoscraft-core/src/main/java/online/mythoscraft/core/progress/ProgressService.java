package online.mythoscraft.core.progress;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.database.SqlDialect;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small key/value progress store per player (jobs, achievements, milestones, parkour). Online players are cached;
 * every change is written to the database right away on the database thread.
 */
public final class ProgressService {

    private final MythosCraft plugin;
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();
    private String upsert;

    public ProgressService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    private String upsert() {
        if (upsert == null) {
            SqlDialect d = plugin.database().dialect();
            upsert = d.upsert("mc_progress", List.of("uuid", "pkey"), List.of("uuid", "pkey", "value"));
        }
        return upsert;
    }

    /** Loads the player's progress; {@code ready} runs on the main thread afterwards. */
    public void load(Player player, Runnable ready) {
        UUID id = player.getUniqueId();
        plugin.database().query("load progress " + player.getName(), c -> {
            Map<String, Long> values = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT pkey, value FROM mc_progress WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        values.put(rs.getString(1), rs.getLong(2));
                    }
                }
            }
            return values;
        }).whenComplete((values, error) -> plugin.tasks().sync(() -> {
            if (error == null && player.isOnline()) {
                cache.put(id, new ConcurrentHashMap<>(values));
                if (ready != null) {
                    ready.run();
                }
            }
        }));
    }

    public boolean loaded(Player player) {
        return cache.containsKey(player.getUniqueId());
    }

    public long get(Player player, String key) {
        Map<String, Long> m = cache.get(player.getUniqueId());
        return m == null ? 0 : m.getOrDefault(key, 0L);
    }

    public boolean has(Player player, String key) {
        Map<String, Long> m = cache.get(player.getUniqueId());
        return m != null && m.containsKey(key);
    }

    public void set(Player player, String key, long value) {
        Map<String, Long> m = cache.get(player.getUniqueId());
        if (m == null) {
            return;
        }
        m.put(key, value);
        String id = player.getUniqueId().toString();
        plugin.database().query("save progress " + key, c -> {
            write(c, id, key, value);
            return null;
        });
    }

    public long add(Player player, String key, long delta) {
        long v = get(player, key) + delta;
        set(player, key, v);
        return v;
    }

    private void write(Connection c, String uuid, String key, long value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(upsert())) {
            ps.setString(1, uuid);
            ps.setString(2, key);
            ps.setLong(3, value);
            ps.executeUpdate();
        }
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    /** Top values of one key over all players (for parkour records). */
    public java.util.concurrent.CompletableFuture<List<Map.Entry<String, Long>>> lowest(String key, int limit) {
        return plugin.database().query("progress top " + key, c -> {
            List<Map.Entry<String, Long>> out = new java.util.ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT p.name, g.value FROM mc_progress g JOIN mc_players p ON p.uuid = g.uuid "
                    + "WHERE g.pkey = ? AND g.value > 0 ORDER BY g.value ASC LIMIT ?")) {
                ps.setString(1, key);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(Map.entry(rs.getString(1), rs.getLong(2)));
                    }
                }
            }
            return out;
        });
    }
}
