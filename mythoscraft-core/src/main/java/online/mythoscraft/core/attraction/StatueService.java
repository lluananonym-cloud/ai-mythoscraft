package online.mythoscraft.core.attraction;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.stats.LeaderboardService;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Statues of the best players: a mannequin (full 3D player skin incl. second layer) on each podium of the statue
 * hall, with a floating name plate. Refreshed every few minutes from the leaderboards.
 */
public final class StatueService {

    static final String TAG = "statue";

    private final MythosCraft plugin;
    private final List<Entity> spawned = new ArrayList<>();
    private final List<UUID> shown = new ArrayList<>();
    private List<Stat> stats = List.of(Stat.COINS, Stat.KILLS, Stat.EVENTS_WON);
    private double scale = 1.25;

    public StatueService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.statues");
        List<Stat> list = new ArrayList<>();
        for (String id : cfg.strings("stats")) {
            Stat s = Stat.byId(id.toLowerCase(Locale.ROOT));
            if (s == null) {
                cfg.warn("stats", "unbekannte Statistik '" + id + "'");
            } else {
                list.add(s);
            }
        }
        if (!list.isEmpty()) {
            stats = list;
        }
        scale = cfg.decimal("scale", 1.25, 0.5, 3);
        int minutes = cfg.integer("refresh-minutes", 5, 1, 1440);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 100, minutes * 1200L, "statues", this::refresh);
    }

    /** Loads the top players and rebuilds every statue. */
    public void refresh() {
        List<Location> spots = plugin.attractionStore().all("statue");
        if (spots.isEmpty()) {
            clear();
            return;
        }
        if (stats.size() == 1) {
            plugin.leaderboards().top(stats.get(0), spots.size()).thenAccept(top -> plugin.tasks().sync(() -> {
                clear();
                for (int i = 0; i < spots.size(); i++) {
                    place(spots.get(i), stats.get(0), i < top.size() ? top.get(i) : null, i + 1);
                }
            }));
            return;
        }
        List<LeaderboardService.Entry> best = new ArrayList<>();
        int n = Math.min(spots.size(), stats.size());
        java.util.concurrent.CompletableFuture<?>[] all = new java.util.concurrent.CompletableFuture<?>[n];
        for (int i = 0; i < n; i++) {
            best.add(null);
            int index = i;
            all[i] = plugin.leaderboards().top(stats.get(i), 1).thenAccept(top -> {
                synchronized (best) {
                    best.set(index, top.isEmpty() ? null : top.get(0));
                }
            });
        }
        java.util.concurrent.CompletableFuture.allOf(all).thenRun(() -> plugin.tasks().sync(() -> {
            clear();
            for (int i = 0; i < n; i++) {
                place(spots.get(i), stats.get(i), best.get(i), 1);
            }
        }));
    }

    private void place(Location at, Stat stat, LeaderboardService.Entry entry, int place) {
        if (at.getWorld() == null || !at.isChunkLoaded()) {
            return;
        }
        String title = plugin.messages().raw("attractions.statue.title-" + Math.min(place, 3));
        if (entry == null || entry.value() <= 0) {
            spawned.add(label(at.clone().add(0, 1.3, 0), plugin.messages().get("attractions.statue.empty",
                    "stat", plugin.messages().get("stats.names." + stat.id()), "title", plugin.messages().format(title))));
            return;
        }
        Mannequin m = at.getWorld().spawn(at, Mannequin.class, e -> {
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setGravity(false);
            e.setAI(false);
            e.setImmovable(true);
            e.setCollidable(false);
            e.setRotation(at.getYaw(), 0);
            e.setDescription(null);
            e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
            if (e.getAttribute(Attribute.SCALE) != null) {
                e.getAttribute(Attribute.SCALE).setBaseValue(scale);
            }
            if (place == 1) {
                e.getEquipment().setItem(EquipmentSlot.HAND, new ItemStack(Material.NETHERITE_SWORD));
            }
            e.setPose(Pose.STANDING);
        });
        spawned.add(m);
        shown.add(entry.uuid());
        PlayerProfile profile = Bukkit.createProfile(entry.uuid(), entry.name());
        org.bukkit.entity.Player online = Bukkit.getPlayer(entry.uuid());
        if (online != null) {
            m.setProfile(ResolvableProfile.resolvableProfile(online.getPlayerProfile()));
        } else {
            plugin.tasks().io("statue skin " + entry.name(), () -> {
                profile.complete(true);
                plugin.tasks().sync(() -> {
                    if (m.isValid()) {
                        m.setProfile(ResolvableProfile.resolvableProfile(profile));
                    }
                });
            });
        }
        double height = 2.05 * scale + 0.35;
        spawned.add(label(at.clone().add(0, height, 0), plugin.messages().get("attractions.statue.plate",
                "title", plugin.messages().format(title), "player", entry.name(),
                "stat", plugin.messages().get("stats.names." + stat.id()), "value", Numbers.format(entry.value()))));
    }

    private TextDisplay label(Location at, net.kyori.adventure.text.Component text) {
        return at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setLineWidth(220);
            t.setBackgroundColor(Color.fromARGB(0x70, 0x1b, 0x10, 0x22));
            t.text(text);
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        });
    }

    public boolean present() {
        return !spawned.isEmpty() && spawned.stream().allMatch(Entity::isValid);
    }

    public void clear() {
        spawned.forEach(e -> {
            if (e.isValid()) {
                e.remove();
            }
        });
        spawned.clear();
        shown.clear();
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        clear();
    }
}
