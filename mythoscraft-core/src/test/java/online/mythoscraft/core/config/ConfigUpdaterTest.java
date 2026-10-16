package online.mythoscraft.core.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    @TempDir
    Path dir;

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void resetsListedSectionsOnceForOlderFiles() throws Exception {
        YamlConfiguration server = yaml("""
                config-version: 22
                attractions:
                  wheel:
                    price: 2500
                    old-key: true
                  rings:
                    boost: 3
                """);
        YamlConfiguration bundled = yaml("""
                config-version: 23
                config-reset:
                  23: [attractions.wheel]
                attractions:
                  wheel:
                    price: 10000
                    daily-limit: 5
                  rings:
                    boost: 1.25
                """);
        ConfigUpdater.merge(server, bundled);
        assertEquals(10000, server.getInt("attractions.wheel.price"));
        assertEquals(5, server.getInt("attractions.wheel.daily-limit"));
        assertFalse(server.contains("attractions.wheel.old-key"));
        assertEquals(3, server.getInt("attractions.rings.boost"), "other admin values stay");
        assertFalse(server.contains("config-reset"));
        // already on the new version: the admin's later change stays
        server.set("attractions.wheel.price", 1);
        ConfigUpdater.merge(server, bundled);
        assertEquals(1, server.getInt("attractions.wheel.price"));
    }

    @Test
    void addsMissingEntriesButKeepsAdminValues() throws Exception {
        YamlConfiguration server = yaml("""
                resource-pack:
                  url: "https://example.org/pack.zip"
                crates:
                  vip:
                    name: "Mein VIP"
                """);
        YamlConfiguration bundled = yaml("""
                config-version: 2
                resource-pack:
                  url: ""
                  sha1: ""
                crates:
                  vip:
                    name: "VIP"
                    color: "#22C55E"
                  halloween:
                    name: "Schauernacht"
                    rewards:
                      - {reward: "coins 5", weight: 1}
                shop:
                  entries: []
                """);
        int added = ConfigUpdater.merge(server, bundled);
        assertTrue(added >= 4, "added " + added);
        assertEquals("https://example.org/pack.zip", server.getString("resource-pack.url"));
        assertEquals("", server.getString("resource-pack.sha1"));
        assertEquals("Mein VIP", server.getString("crates.vip.name"));
        assertEquals("#22C55E", server.getString("crates.vip.color"));
        assertEquals("Schauernacht", server.getString("crates.halloween.name"));
        assertEquals(1, server.getList("crates.halloween.rewards").size());
        assertEquals(List.of(), server.getList("shop.entries"));
        assertEquals(2, server.getInt(ConfigUpdater.VERSION_KEY));
    }

    @Test
    void runsOnlyOncePerVersion() throws Exception {
        YamlConfiguration bundled = yaml("config-version: 2\na: 1\nb: 2\n");
        YamlConfiguration server = yaml("a: 5\n");
        assertEquals(1, ConfigUpdater.merge(server, bundled));
        server.set("b", null);
        assertEquals(-1, ConfigUpdater.merge(server, bundled), "deliberately removed entries stay removed");
        assertFalse(server.contains("b"));
    }

    @Test
    void neverReplacesAValueWithASection() throws Exception {
        YamlConfiguration server = yaml("spawn: \"world,0,64,0\"\n");
        YamlConfiguration bundled = yaml("config-version: 2\nspawn:\n  radius: 10\n");
        ConfigUpdater.merge(server, bundled);
        assertEquals("world,0,64,0", server.getString("spawn"));
    }

    @Test
    void updatesFileAndKeepsBackup() throws Exception {
        File file = dir.resolve("crates.yml").toFile();
        Files.writeString(file.toPath(), "# Kommentar vom Admin\ncrates:\n  vip:\n    name: \"VIP\"\n", StandardCharsets.UTF_8);
        InputStream bundled = new ByteArrayInputStream("config-version: 2\ncrates:\n  vip:\n    name: \"x\"\n  inferno:\n    name: \"Inferno\"\n"
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(ConfigUpdater.update(file, bundled) > 0);
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(text.contains("Kommentar vom Admin"), text);
        assertTrue(text.contains("inferno"), text);
        assertTrue(new File(dir.toFile(), "crates.yml.v0.bak").exists());
        InputStream again = new ByteArrayInputStream("config-version: 2\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(-1, ConfigUpdater.update(file, again));
    }

    @Test
    void appendsNewRewardsToExistingCrates() throws Exception {
        YamlConfiguration server = yaml("""
                config-version: 2
                crates:
                  vip:
                    rewards:
                      - {reward: "coins 500", weight: 99}
                """);
        YamlConfiguration bundled = yaml("""
                config-version: 3
                crates:
                  vip:
                    rewards:
                      - {reward: "coins 500", weight: 10}
                      - {reward: "tool coin_pouch 2", weight: 5}
                """);
        assertEquals(1, ConfigUpdater.merge(server, bundled));
        List<?> rewards = server.getList("crates.vip.rewards");
        assertEquals(2, rewards.size());
        assertEquals(99, ((java.util.Map<?, ?>) rewards.get(0)).get("weight"), "admin weight kept");
    }
}
