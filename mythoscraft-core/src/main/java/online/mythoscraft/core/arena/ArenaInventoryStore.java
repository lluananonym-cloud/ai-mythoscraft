package online.mythoscraft.core.arena;

import online.mythoscraft.core.database.DatabaseManager;
import online.mythoscraft.core.database.SqlDialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Crash-recovery copy of inventories taken while a player is in an arena. */
public final class ArenaInventoryStore {

    public record Stored(String arena, byte[] data, String location) {
    }

    private final DatabaseManager db;
    private final String upsert;

    public ArenaInventoryStore(DatabaseManager db) {
        this.db = db;
        SqlDialect d = db.dialect();
        this.upsert = d.upsert("mc_arena_inventories", List.of("uuid"), List.of("uuid", "arena", "data", "location", "created"));
    }

    public CompletableFuture<Void> store(UUID uuid, String arena, byte[] data, String location) {
        return db.query("store arena inventory", c -> {
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, arena);
                ps.setBytes(3, data);
                ps.setString(4, location);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Stored> load(UUID uuid) {
        return db.query("load arena inventory", c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT arena, data, location FROM mc_arena_inventories WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new Stored(rs.getString(1), rs.getBytes(2), rs.getString(3)) : null;
                }
            }
        });
    }

    public CompletableFuture<Void> delete(UUID uuid) {
        return db.query("delete arena inventory", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mc_arena_inventories WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }
}
