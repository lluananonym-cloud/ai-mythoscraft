package online.mythoscraft.core.protection;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Owns user regions (regions.yml) plus regions contributed by other systems (arenas) and the lookup index. */
public final class RegionManager {

    public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Map<String, Collection<Region>> external = new HashMap<>();
    private final EnumMap<Flag, Boolean> newRegionDefaults = new EnumMap<>(Flag.class);
    private volatile RegionIndex index = RegionIndex.EMPTY;

    public RegionManager(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "regions.yml");
    }

    public void reload(ConfigReader root) {
        newRegionDefaults.clear();
        ConfigReader defaults = root.sub("protection.new-region-defaults");
        for (String key : defaults.keys()) {
            Flag f = Flag.byKey(key);
            if (f == null) {
                defaults.warn(key, "unbekanntes Flag");
                continue;
            }
            newRegionDefaults.put(f, defaults.bool(key, false));
        }
        load();
    }

    private void load() {
        YamlConfiguration yaml = store.load(false);
        regions.clear();
        ConfigurationSection sec = yaml.getConfigurationSection("regions");
        int failed = 0;
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                try {
                    regions.put(name.toLowerCase(Locale.ROOT), read(name.toLowerCase(Locale.ROOT), sec.getConfigurationSection(name)));
                } catch (IllegalArgumentException e) {
                    failed++;
                    plugin.getLogger().severe("Failed to load region '" + name + "': " + e.getMessage());
                }
            }
        }
        rebuild();
        plugin.getLogger().info("Loaded " + regions.size() + " regions" + (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    private static Region read(String name, ConfigurationSection s) {
        if (s == null) {
            throw new IllegalArgumentException("not a section");
        }
        String world = s.getString("world");
        List<Integer> min = s.getIntegerList("min");
        List<Integer> max = s.getIntegerList("max");
        if (world == null || min.size() != 3 || max.size() != 3) {
            throw new IllegalArgumentException("world/min/max missing or invalid");
        }
        Region r = new Region(name, Region.Type.REGION, world,
                Cuboid.of(min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2)), s.getInt("priority", 0));
        ConfigurationSection flags = s.getConfigurationSection("flags");
        if (flags != null) {
            for (String key : flags.getKeys(false)) {
                Flag f = Flag.byKey(key);
                if (f != null && flags.isBoolean(key)) {
                    r.flag(f, flags.getBoolean(key));
                }
            }
        }
        return r;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("MythosCraft Regionen – am besten per /mythos region bearbeiten."));
        for (Region r : regions.values()) {
            String p = "regions." + r.name() + ".";
            Cuboid b = r.bounds();
            yaml.set(p + "world", r.world());
            yaml.set(p + "min", List.of(b.minX(), b.minY(), b.minZ()));
            yaml.set(p + "max", List.of(b.maxX(), b.maxY(), b.maxZ()));
            yaml.set(p + "priority", r.priority());
            r.flags().forEach((f, v) -> yaml.set(p + "flags." + f.key(), v));
        }
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
    }

    /** Re-creates the lookup index. Call after adding/removing regions or changing bounds/priority. */
    public void rebuild() {
        List<Region> all = new ArrayList<>(regions.values());
        external.values().forEach(all::addAll);
        index = RegionIndex.build(all);
    }

    public void setExternal(String source, Collection<Region> contributed) {
        external.put(source, List.copyOf(contributed));
        rebuild();
    }

    public RegionIndex index() {
        return index;
    }

    public Region get(String name) {
        return regions.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Region> regions() {
        return Collections.unmodifiableCollection(regions.values());
    }

    public int externalCount() {
        return external.values().stream().mapToInt(Collection::size).sum();
    }

    public Region create(String name, String world, Cuboid bounds, int priority) {
        Region r = new Region(name.toLowerCase(Locale.ROOT), Region.Type.REGION, world, bounds, priority);
        newRegionDefaults.forEach(r::flag);
        regions.put(r.name(), r);
        rebuild();
        save();
        return r;
    }

    public boolean delete(String name) {
        if (regions.remove(name.toLowerCase(Locale.ROOT)) == null) {
            return false;
        }
        rebuild();
        save();
        return true;
    }

    public Region top(Location loc) {
        return index.top(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public List<Region> at(Location loc) {
        return index.at(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
