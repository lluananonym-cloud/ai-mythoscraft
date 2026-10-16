package online.mythoscraft.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Display texts written in config.yml / crates.yml (crate and key names, seasons, ranks, shop, NPCs) exist in
 * German. Players whose game is not German see the English version from lang/config_en.yml – matched by the exact
 * text, so edited texts simply stay as they are. Admins can add own pairs in plugins/MythosCraft/lang/config_en.yml.
 */
public final class ConfigText {

    private static volatile Map<String, String> english = Map.of();

    private ConfigText() {
    }

    public static void load(JavaPlugin plugin) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = plugin.getResource("lang/config_en.yml")) {
            if (in != null) {
                read(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), map);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("lang/config_en.yml (built-in): " + e.getMessage());
        }
        File own = new File(plugin.getDataFolder(), "lang/config_en.yml");
        if (own.isFile()) {
            read(YamlConfiguration.loadConfiguration(own), map);
        } else {
            plugin.saveResource("lang/config_en.yml", false);
        }
        english = Map.copyOf(map);
    }

    private static void read(YamlConfiguration y, Map<String, String> map) {
        for (Map<?, ?> m : y.getMapList("texts")) {
            Object de = m.get("de");
            Object en = m.get("en");
            if (de != null && en != null) {
                map.put(de.toString(), en.toString());
            }
        }
    }

    /** The text in the language of the current receiver (German original or English). */
    public static String tr(String text) {
        if (text == null || !"en".equals(Messages.currentLang())) {
            return text;
        }
        return english.getOrDefault(text, text);
    }

    public static List<String> tr(List<String> lines) {
        if (lines == null || !"en".equals(Messages.currentLang())) {
            return lines;
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            out.add(english.getOrDefault(l, l));
        }
        return out;
    }
}
