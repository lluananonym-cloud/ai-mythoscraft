package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Positions of the spawn attractions (statues, wheel, well, merchants, elytra rings, lamps, season decoration …),
 * written by {@code /spawn place} from the markers of the built spawn (data/attractions.yml).
 */
public final class AttractionStore {

    private final MythosCraft plugin;
    private final YamlStore store;

    public AttractionStore(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/attractions.yml");
        store.load(false);
    }

    private final Map<String, List<Location>> cache = new java.util.HashMap<>();
    private Location centerCache;

    public List<Location> all(String key) {
        List<Location> cached = cache.get(key);
        if (cached != null && (cached.isEmpty() || cached.get(0).isWorldLoaded())) {
            return cached;
        }
        List<Location> out = new ArrayList<>();
        List<String> raw = store.yaml().getStringList("points." + key);
        for (String s : raw) {
            Location l = LocationCodec.fromString(s);
            if (l != null) {
                out.add(l);
            }
        }
        out = List.copyOf(out);
        if (out.size() == raw.size()) {
            cache.put(key, out);
        }
        return out;
    }

    public Location first(String key) {
        List<Location> l = all(key);
        return l.isEmpty() ? null : l.get(0).clone();
    }

    /** Center of the built spawn (floor level) or null. */
    public Location center() {
        if (centerCache == null || !centerCache.isWorldLoaded()) {
            centerCache = LocationCodec.fromString(store.yaml().getString("center"));
        }
        return centerCache == null ? null : centerCache.clone();
    }

    public int radius() {
        return store.yaml().getInt("radius", 0);
    }

    /** Replaces every stored point (a new spawn was built). */
    public void define(Location center, int radius, Map<String, List<Location>> points) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("center", LocationCodec.toString(center));
        yaml.set("radius", radius);
        points.forEach((key, list) -> yaml.set("points." + key, list.stream().map(LocationCodec::toString).toList()));
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
        cache.clear();
        centerCache = null;
    }

    /** Replaces one list of points (e.g. the decoration spots of the arenas), keeps everything else. */
    public void put(String key, List<Location> list) {
        YamlConfiguration yaml = store.yaml();
        yaml.set("points." + key, list.stream().map(LocationCodec::toString).toList());
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
        cache.remove(key);
    }

    /** Is the location inside the built spawn (cylinder around the center)? */
    public boolean inside(Location loc, double extra) {
        Location c = center();
        if (c == null || loc.getWorld() == null || !loc.getWorld().equals(c.getWorld())) {
            return false;
        }
        double dx = loc.getX() - c.getX();
        double dz = loc.getZ() - c.getZ();
        double r = radius() + extra;
        return dx * dx + dz * dz <= r * r && loc.getY() > c.getY() - 40 && loc.getY() < c.getY() + 90;
    }
}
