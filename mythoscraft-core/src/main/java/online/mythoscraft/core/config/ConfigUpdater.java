package online.mythoscraft.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Brings files from older plugin versions up to date: every setting that exists in the bundled default but not
 * in the server's file is added (with its comment), nothing the admin changed is touched. Runs only when the
 * bundled {@code config-version} is newer than the file's, so deliberately removed entries stay removed until
 * the next version bump.
 */
public final class ConfigUpdater {

    public static final String VERSION_KEY = "config-version";
    /** Plain lists that get new default values appended on an update (e.g. new crates on the spawn). */
    private static final Set<String> GROWING_LISTS = Set.of("build.spawn-crates", "music.playlists.default",
            "music.playlists.halloween", "music.playlists.frostfest");

    private ConfigUpdater() {
    }

    /**
     * Adds missing entries from {@code defaults} to {@code target}.
     *
     * @return number of added entries, or -1 if the target is already up to date
     */
    public static int merge(YamlConfiguration target, YamlConfiguration defaults) {
        int fileVersion = target.getInt(VERSION_KEY, 0);
        int defaultVersion = defaults.getInt(VERSION_KEY, 0);
        if (fileVersion >= defaultVersion) {
            return -1;
        }
        int added = 0;
        for (String path : defaults.getKeys(true)) {
            if (path.equals(VERSION_KEY) || parentBlocked(target, path)) {
                continue;
            }
            if (target.contains(path, true)) {
                added += mergeRewardList(target, defaults, path);
                continue;
            }
            if (defaults.isConfigurationSection(path)) {
                target.createSection(path);
                copyComments(target, defaults, path);
                ConfigurationSection section = defaults.getConfigurationSection(path);
                if (section != null && section.getKeys(false).isEmpty()) {
                    added++;
                }
                continue;
            }
            target.set(path, defaults.get(path));
            copyComments(target, defaults, path);
            added++;
        }
        target.set(VERSION_KEY, defaultVersion);
        return added;
    }

    /**
     * Lists of {reward: …} entries (crate rewards, shop offers) get the new default entries appended – matched by
     * their reward text, so changed weights or prices of existing entries are kept.
     */
    private static int mergeRewardList(YamlConfiguration target, YamlConfiguration defaults, String path) {
        List<?> def = defaults.getList(path);
        List<?> cur = target.getList(path);
        if (def != null && cur != null && GROWING_LISTS.contains(path)) {
            List<Object> merged = new ArrayList<>(cur);
            int added = 0;
            for (Object o : def) {
                if (!merged.contains(o)) {
                    merged.add(o);
                    added++;
                }
            }
            if (added > 0) {
                target.set(path, merged);
            }
            return added;
        }
        if (def == null || cur == null || def.isEmpty() || !(def.get(0) instanceof Map<?, ?> first) || !first.containsKey("reward")) {
            return 0;
        }
        if (path.equals("shop.entries")) {
            cur = paginate(cur, def);
            target.set(path, cur);
        }
        Set<String> present = new HashSet<>();
        for (Object o : cur) {
            if (o instanceof Map<?, ?> m && m.get("reward") != null) {
                present.add(String.valueOf(m.get("reward")).trim().toLowerCase(Locale.ROOT));
            }
        }
        List<Object> merged = new ArrayList<>(cur);
        int added = 0;
        for (Object o : def) {
            if (o instanceof Map<?, ?> m && m.get("reward") != null
                    && present.add(String.valueOf(m.get("reward")).trim().toLowerCase(Locale.ROOT))) {
                merged.add(o);
                added++;
            }
        }
        if (added > 0) {
            target.set(path, merged);
        }
        return added;
    }

    /**
     * Shop pages: offers from older versions have no "page" – they get page and slot of the same offer in the new
     * default layout (so the shop is split into pages without losing changed prices).
     */
    private static List<Object> paginate(List<?> cur, List<?> def) {
        Map<String, Map<?, ?>> byReward = new java.util.HashMap<>();
        for (Object o : def) {
            if (o instanceof Map<?, ?> m && m.get("reward") != null) {
                byReward.put(String.valueOf(m.get("reward")).trim().toLowerCase(Locale.ROOT), m);
            }
        }
        List<Object> out = new ArrayList<>();
        for (Object o : cur) {
            // the Silvester key is only won on the wheel – never sold in the shop
            if (o instanceof Map<?, ?> m && "key silvester 1".equalsIgnoreCase(String.valueOf(m.get("reward")).trim())) {
                continue;
            }
            if (o instanceof Map<?, ?> m && !m.containsKey("page") && m.get("reward") != null) {
                Map<?, ?> d = byReward.get(String.valueOf(m.get("reward")).trim().toLowerCase(Locale.ROOT));
                if (d != null && d.get("page") != null) {
                    Map<Object, Object> copy = new java.util.LinkedHashMap<>(m);
                    copy.put("page", d.get("page"));
                    if (d.get("slot") != null) {
                        copy.put("slot", d.get("slot"));
                    }
                    out.add(copy);
                    continue;
                }
            }
            out.add(o);
        }
        return out;
    }

    /** A value (not a section) in the target where the default has a section: never replace admin values. */
    private static boolean parentBlocked(YamlConfiguration target, String path) {
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            String parent = path.substring(0, dot);
            if (target.isSet(parent) && !target.isConfigurationSection(parent)) {
                return true;
            }
            dot = parent.lastIndexOf('.');
        }
        return false;
    }

    private static void copyComments(YamlConfiguration target, YamlConfiguration defaults, String path) {
        try {
            if (!defaults.getComments(path).isEmpty()) {
                target.setComments(path, defaults.getComments(path));
            }
        } catch (RuntimeException ignored) {
            // comments are cosmetic
        }
    }

    /**
     * Updates the file in place (a backup of the old file is kept as {@code <name>.v<old>.bak}).
     *
     * @return number of added entries, or -1 if nothing had to be done
     */
    public static int update(File file, InputStream bundled) throws IOException, InvalidConfigurationException {
        if (!file.exists()) {
            return -1;
        }
        YamlConfiguration target = new YamlConfiguration();
        target.load(file);
        YamlConfiguration defaults = new YamlConfiguration();
        try (Reader r = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
            defaults.load(r);
        }
        int oldVersion = target.getInt(VERSION_KEY, 0);
        int added = merge(target, defaults);
        if (added < 0) {
            return -1;
        }
        File backup = new File(file.getParentFile(), file.getName() + ".v" + oldVersion + ".bak");
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        target.save(file);
        return added;
    }
}
