package online.mythoscraft.core.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Floating leaderboards (text displays) that refresh themselves every minute. */
public final class HologramManager {

    public static final class Hologram {
        final String name;
        final Stat stat;
        final Location location;
        final int limit;
        TextDisplay display;

        Hologram(String name, Stat stat, Location location, int limit) {
            this.name = name;
            this.stat = stat;
            this.location = location;
            this.limit = limit;
        }

        public String name() {
            return name;
        }

        public Stat stat() {
            return stat;
        }

        public Location location() {
            return location;
        }
    }

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();

    public HologramManager(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/holograms.yml");
    }

    public void reload() {
        holograms.values().forEach(this::despawn);
        holograms.clear();
        YamlConfiguration yaml = store.load(false);
        ConfigurationSection sec = yaml.getConfigurationSection("holograms");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                Stat stat = Stat.byId(sec.getString(name + ".stat", ""));
                Location loc = LocationCodec.fromString(sec.getString(name + ".location"));
                if (stat == null || loc == null) {
                    plugin.getLogger().warning("Hologram " + name + " skipped (unknown stat or world missing)");
                    continue;
                }
                boolean duplicate = holograms.values().stream().anyMatch(o -> o.location.getWorld().equals(loc.getWorld())
                        && o.location.distanceSquared(loc) < 1.5 * 1.5);
                if (duplicate) {
                    plugin.getLogger().info("Hologram " + name + " removed (another hologram stands on the same spot)");
                    continue;
                }
                holograms.put(name, new Hologram(name, stat, loc, sec.getInt(name + ".limit", 10)));
            }
            if (holograms.size() != sec.getKeys(false).size()) {
                save();
            }
        }
        for (Hologram h : holograms.values()) {
            if (h.location.isChunkLoaded()) {
                spawn(h);
            }
        }
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 20 * 60, "hologram refresh", this::refreshAll);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Hologram h : holograms.values()) {
            yaml.set("holograms." + h.name + ".stat", h.stat.id());
            yaml.set("holograms." + h.name + ".location", LocationCodec.toString(h.location));
            yaml.set("holograms." + h.name + ".limit", h.limit);
        }
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
    }

    public Hologram create(String name, Stat stat, Location location, int limit) {
        Hologram h = new Hologram(name.toLowerCase(Locale.ROOT), stat, location, limit);
        Hologram old = holograms.put(h.name, h);
        if (old != null) {
            despawn(old);
        }
        save();
        spawn(h);
        refresh(h);
        return h;
    }

    /** Removes every hologram (new spawn: the spawn creates its own again). */
    public void removeAll() {
        holograms.values().forEach(this::despawn);
        holograms.clear();
        save();
    }

    public boolean delete(String name) {
        Hologram h = holograms.remove(name.toLowerCase(Locale.ROOT));
        if (h == null) {
            return false;
        }
        despawn(h);
        save();
        return true;
    }

    public Collection<Hologram> all() {
        return holograms.values();
    }

    /** Removes hologram texts that belong to no hologram (or are a second copy of one). */
    public int sweepOrphans(org.bukkit.entity.Entity[] entities) {
        int removed = 0;
        for (org.bukkit.entity.Entity e : entities) {
            String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
            if (tag == null || !tag.startsWith("hologram:")) {
                continue;
            }
            Hologram h = holograms.get(tag.substring(9));
            if (h == null || !e.equals(h.display)) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    public void onChunkLoad(Chunk chunk) {
        sweepOrphans(chunk.getEntities());
        for (Hologram h : holograms.values()) {
            if (h.location.getWorld().equals(chunk.getWorld()) && h.location.getBlockX() >> 4 == chunk.getX()
                    && h.location.getBlockZ() >> 4 == chunk.getZ() && (h.display == null || !h.display.isValid())) {
                spawn(h);
                refresh(h);
            }
        }
    }

    public void onChunkUnload(Chunk chunk) {
        for (Hologram h : holograms.values()) {
            if (h.location.getWorld().equals(chunk.getWorld()) && h.location.getBlockX() >> 4 == chunk.getX()
                    && h.location.getBlockZ() >> 4 == chunk.getZ()) {
                despawn(h);
            }
        }
    }

    private void spawn(Hologram h) {
        despawn(h);
        h.display = h.location.getWorld().spawn(h.location, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setLineWidth(240);
            t.setBackgroundColor(Color.fromARGB(0x66, 0x14, 0x0A, 0x24));
            t.setViewRange(0.6f);
            t.text(plugin.messages().get("holograms.loading"));
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "hologram:" + h.name);
        });
    }

    private void despawn(Hologram h) {
        if (h.display != null && h.display.isValid()) {
            h.display.remove();
        }
        h.display = null;
    }

    private void refreshAll() {
        for (Hologram h : holograms.values()) {
            if (h.display != null && h.display.isValid()) {
                refresh(h);
            }
        }
    }

    private void refresh(Hologram h) {
        plugin.leaderboards().top(h.stat, h.limit).thenAccept(entries -> {
            if (h.display == null || !h.display.isValid()) {
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(plugin.messages().get("holograms.title", "stat", plugin.messages().get("stats.names." + h.stat.id())));
            int place = 0;
            for (LeaderboardService.Entry e : entries) {
                place++;
                lines.add(plugin.messages().get(place <= 3 ? "holograms.line-top" : "holograms.line", "place", place,
                        "player", e.name(), "value", h.stat.format(e.value())));
            }
            if (entries.isEmpty()) {
                lines.add(plugin.messages().get("holograms.empty"));
            }
            h.display.text(Component.join(JoinConfiguration.newlines(), lines));
        });
    }

    public void shutdown() {
        holograms.values().forEach(this::despawn);
    }
}
