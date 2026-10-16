package online.mythoscraft.core.database;

import java.util.List;
import java.util.stream.Collectors;

/** The few places where SQLite and MySQL/MariaDB syntax differ. */
public enum SqlDialect {
    SQLITE,
    MYSQL;

    public String autoIncrementPrimaryKey() {
        return this == SQLITE ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
    }

    public String blob() {
        return this == SQLITE ? "BLOB" : "MEDIUMBLOB";
    }

    public String upsert(String table, List<String> keyColumns, List<String> columns) {
        String cols = String.join(", ", columns);
        String values = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        List<String> updates = columns.stream().filter(c -> !keyColumns.contains(c)).toList();
        if (this == SQLITE) {
            return "INSERT INTO " + table + " (" + cols + ") VALUES (" + values + ") ON CONFLICT("
                    + String.join(", ", keyColumns) + ") DO UPDATE SET "
                    + updates.stream().map(c -> c + " = excluded." + c).collect(Collectors.joining(", "));
        }
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE "
                + updates.stream().map(c -> c + " = VALUES(" + c + ")").collect(Collectors.joining(", "));
    }
}
