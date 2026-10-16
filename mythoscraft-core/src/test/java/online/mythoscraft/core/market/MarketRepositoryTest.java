package online.mythoscraft.core.market;

import online.mythoscraft.core.database.DatabaseManager;
import online.mythoscraft.core.database.SqlDialect;
import online.mythoscraft.core.database.SqlFunction;
import online.mythoscraft.core.database.SqliteConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real SQLite test of the auction and farm order tables (schema version 2). */
class MarketRepositoryTest {

    @TempDir
    Path dir;

    private DatabaseManager db;
    private MarketRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseManager(Logger.getLogger("test"));
        db.start(new SqliteConnectionProvider(dir.resolve("market.db").toFile())).get(10, TimeUnit.SECONDS);
        repo = new MarketRepository(SqlDialect.SQLITE);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private <T> T run(SqlFunction<T> f) throws Exception {
        return db.query("test", f).get(10, TimeUnit.SECONDS);
    }

    @Test
    void auctionsRoundTripAndOnlyOpenOnesAreLoaded() throws Exception {
        String seller = UUID.randomUUID().toString();
        byte[] item = {1, 2, 3, 4};
        MarketRepository.AuctionRow active = new MarketRepository.AuctionRow("a1", seller, "Luan", item, 500, "coins", 1, 99, "ACTIVE", null, 0);
        MarketRepository.AuctionRow sold = new MarketRepository.AuctionRow("a2", seller, "Luan", item, 700, "gems", 2, 99, "SOLD",
                UUID.randomUUID().toString(), 50);
        run(c -> {
            repo.saveAuction(c, active);
            repo.saveAuction(c, sold);
            return null;
        });
        List<MarketRepository.AuctionRow> open = run(repo::openAuctions);
        assertEquals(1, open.size());
        assertEquals("a1", open.get(0).id());
        assertArrayEquals(item, open.get(0).item());
        assertEquals(500, open.get(0).price());

        run(c -> {
            repo.saveAuction(c, new MarketRepository.AuctionRow("a1", seller, "Luan", item, 500, "coins", 1, 99, "EXPIRED", null, 100));
            return null;
        });
        open = run(repo::openAuctions);
        assertEquals("EXPIRED", open.get(0).state(), "upsert updates the existing row");
        run(c -> {
            repo.saveAuction(c, new MarketRepository.AuctionRow("a1", seller, "Luan", item, 500, "coins", 1, 99, "RETURNED", null, 200));
            return null;
        });
        assertTrue(run(repo::openAuctions).isEmpty());
    }

    @Test
    void ordersStayLoadedUntilEverythingIsCollected() throws Exception {
        String owner = UUID.randomUUID().toString();
        run(c -> {
            repo.saveOrder(c, new MarketRepository.OrderRow("o1", owner, "Luan", "WHEAT", 640, 100, 0, 2, 1, 99, "OPEN"));
            repo.saveOrder(c, new MarketRepository.OrderRow("o2", owner, "Luan", "CARROT", 64, 64, 30, 3, 1, 99, "COMPLETED"));
            repo.saveOrder(c, new MarketRepository.OrderRow("o3", owner, "Luan", "POTATO", 64, 64, 64, 3, 1, 99, "COMPLETED"));
            return null;
        });
        List<MarketRepository.OrderRow> open = run(repo::openOrders);
        assertEquals(List.of("o1", "o2"), open.stream().map(MarketRepository.OrderRow::id).toList());
        assertEquals(640, open.get(0).amount());
        assertEquals("WHEAT", open.get(0).material());
    }
}
