package online.mythoscraft.core.database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlDialectTest {

    @Test
    void upsertSyntaxPerDialect() {
        assertEquals("INSERT INTO t (id, a, b) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET a = excluded.a, b = excluded.b",
                SqlDialect.SQLITE.upsert("t", List.of("id"), List.of("id", "a", "b")));
        assertEquals("INSERT INTO t (id, a, b) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE a = VALUES(a), b = VALUES(b)",
                SqlDialect.MYSQL.upsert("t", List.of("id"), List.of("id", "a", "b")));
    }
}
