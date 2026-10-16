package online.mythoscraft.core.database;

import online.mythoscraft.core.player.Home;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.player.PlayerRepository;
import online.mythoscraft.core.player.Stat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real SQLite end-to-end test of schema, player rows, homes, leaderboards and transactions. */
class DatabaseIntegrationTest {

    @TempDir
    Path dir;

    private DatabaseManager db;
    private PlayerRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseManager(Logger.getLogger("test"));
        db.start(new SqliteConnectionProvider(dir.resolve("test.db").toFile())).get(10, TimeUnit.SECONDS);
        repo = new PlayerRepository(SqlDialect.SQLITE);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private <T> T run(SqlFunction<T> f) throws Exception {
        return db.query("test", f).get(10, TimeUnit.SECONDS);
    }

    @Test
    void schemaMigrationIsIdempotent() throws Exception {
        run(c -> {
            Schema.migrate(c, SqlDialect.SQLITE, Logger.getLogger("test"));
            Schema.migrate(c, SqlDialect.SQLITE, Logger.getLogger("test"));
            return null;
        });
        assertTrue(db.isReady());
    }

    @Test
    void playerRoundTripAndUpsert() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerData data = new PlayerData(id, "Luan");
        data.coins(1_234_567);
        data.gems(42);
        data.addKill();
        data.addKill();
        data.addDeath();
        data.rank("VIP", 0);
        data.daily("2026-10-02", 3);
        run(c -> {
            repo.save(c, data.snapshot());
            return null;
        });
        data.coins(10);
        run(c -> {
            repo.save(c, data.snapshot());
            return null;
        });
        PlayerData.Snapshot loaded = run(c -> repo.load(c, id));
        assertNotNull(loaded);
        assertEquals(10, loaded.coins());
        assertEquals(42, loaded.gems());
        assertEquals(2, loaded.kills());
        assertEquals(1, loaded.deaths());
        assertEquals("VIP", loaded.rankId());
        assertEquals("2026-10-02", loaded.lastDaily());
        assertEquals(3, loaded.dailyStreak());
        assertEquals(id, run(c -> repo.findByName(c, "luan")).uuid());
        assertNull(run(c -> repo.load(c, UUID.randomUUID())));
    }

    @Test
    void homesAreStoredPerPlayer() throws Exception {
        UUID id = UUID.randomUUID();
        Home home = new Home("Basis", "world", 1.5, 64, -3.25, 90f, 10f, 1L);
        run(c -> {
            repo.saveHome(c, id, home);
            repo.saveHome(c, id, new Home("mine", "world_nether", 0, 32, 0, 0, 0, 2L));
            return null;
        });
        Map<String, Home> homes = run(c -> repo.loadHomes(c, id));
        assertEquals(2, homes.size());
        assertEquals(-3.25, homes.get("basis").z());
        run(c -> {
            repo.deleteHome(c, id, "BASIS");
            return null;
        });
        assertEquals(1, run(c -> repo.loadHomes(c, id)).size());
    }

    @Test
    void leaderboardOrdersByStat() throws Exception {
        for (int i = 0; i < 15; i++) {
            PlayerData p = new PlayerData(UUID.randomUUID(), "P" + i);
            p.coins(i * 100L);
            run(c -> {
                repo.save(c, p.snapshot());
                return null;
            });
        }
        List<PlayerData.Snapshot> top = run(c -> repo.top(c, Stat.COINS, 10));
        assertEquals(10, top.size());
        assertEquals("P14", top.get(0).name());
        assertEquals(1400, top.get(0).coins());
        assertEquals(15, run(repo::countPlayers));
    }

    @Test
    void failedTransactionRollsBack() throws Exception {
        UUID a = UUID.randomUUID();
        PlayerData p = new PlayerData(a, "A");
        p.coins(500);
        run(c -> {
            repo.save(c, p.snapshot());
            return null;
        });
        p.coins(0);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> db.transaction("broken transfer", c -> {
            repo.save(c, p.snapshot());
            throw new SQLException("simulated failure after the first write");
        }).get(10, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof SQLException);
        assertEquals(500, run(c -> repo.load(c, a)).coins(), "the first write must have been rolled back");
    }
}
