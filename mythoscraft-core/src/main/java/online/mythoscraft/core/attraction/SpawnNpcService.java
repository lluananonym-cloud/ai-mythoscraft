package online.mythoscraft.core.attraction;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helper NPCs on the starter square instead of text holograms with commands: real player figures (mannequins with
 * the skins of server members) that do the command on right-click – wilderness (/rtp), plot world (/plot), jobs,
 * crates – plus one who just chats. Spawned and removed together with the other spawn attractions.
 */
public final class SpawnNpcService {

    static final String TAG = "spawn_npc";

    /** marker → skin, item in hand, message key */
    private record Npc(String marker, String skin, Material hand, String key) {
    }

    private static final List<Npc> NPCS = List.of(
            new Npc("npc_rtp", "WestDesert68101", Material.COMPASS, "rtp"),
            new Npc("npc_plot", "Jakob283", Material.GRASS_BLOCK, "plot"),
            new Npc("npc_jobs", "Jakob283", Material.IRON_PICKAXE, "jobs"),
            new Npc("npc_crates", "WestDesert68101", Material.TRIAL_KEY, "crates"),
            new Npc("npc_idle", "LeonKal", Material.COOKIE, "idle"));

    private final MythosCraft plugin;
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<UUID, String> roles = new HashMap<>();
    private final Map<String, PlayerProfile> skins = new HashMap<>();
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    SpawnNpcService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    /** @return how many NPCs stand now */
    int spawn() {
        clear();
        int missingMarkers = 0;
        for (Npc n : NPCS) {
            Location at = plugin.attractionStore().first(n.marker());
            if (at == null || at.getWorld() == null) {
                missingMarkers++;
                continue;
            }
            if (!at.isChunkLoaded()) {
                at.getChunk().load();
            }
            try {
                spawnOne(n, at);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Helper NPC " + n.key() + " could not be spawned: " + ex);
            }
        }
        if (missingMarkers == NPCS.size()) {
            plugin.getLogger().warning("No helper NPC spots stored – rebuild the spawn once with /spawn place confirm "
                    + "(or /mythos npcs here to place them around you).");
        } else {
            plugin.getLogger().info("Helper NPCs at spawn: " + spawned.size() + "/" + NPCS.size());
        }
        return spawned.size();
    }

    /** Puts the five NPC spots in a half circle in front of the player (for spawns built before the NPCs existed). */
    void placeAround(Player p) {
        Location c = p.getLocation();
        double yaw = Math.toRadians(c.getYaw());
        for (int i = 0; i < NPCS.size(); i++) {
            double a = yaw + Math.toRadians(-60 + i * 30);
            Location l = c.clone().add(-Math.sin(a) * 4, 0, Math.cos(a) * 4);
            l.setX(l.getBlockX() + 0.5);
            l.setZ(l.getBlockZ() + 0.5);
            l.setY(l.getBlockY());
            l.setYaw((float) Math.toDegrees(Math.atan2(c.getX() - l.getX(), -(c.getZ() - l.getZ()))) * -1);
            l.setDirection(c.toVector().subtract(l.toVector()).setY(0));
            plugin.attractionStore().put(NPCS.get(i).marker(), List.of(l));
        }
    }

    private void spawnOne(Npc n, Location at) {
        {
            Mannequin m = at.getWorld().spawn(at, Mannequin.class, e -> {
                e.setPersistent(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setGravity(false);
                e.setAI(false);
                e.setImmovable(true);
                e.setCollidable(false);
                e.setRotation(at.getYaw(), 0);
                e.customName(plugin.messages().get("npcs." + n.key() + ".name"));
                e.setCustomNameVisible(true);
                e.setDescription(plugin.messages().get("npcs." + n.key() + ".description"));
                e.getEquipment().setItem(EquipmentSlot.HAND, new ItemStack(n.hand()));
                e.setPose(Pose.STANDING);
                e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
            });
            spawned.add(m);
            roles.put(m.getUniqueId(), n.key());
            skin(m, n.skin());
        }
    }

    /** NPC spots exist but some figure is gone (chunk was unloaded …)? */
    boolean missing() {
        int expected = 0;
        for (Npc n : NPCS) {
            Location at = plugin.attractionStore().first(n.marker());
            if (at != null && at.isChunkLoaded()) {
                expected++;
            }
        }
        long alive = spawned.stream().filter(Entity::isValid).count();
        return alive < expected;
    }

    /** Skin of a player name (looked up once, async). */
    private void skin(Mannequin m, String name) {
        PlayerProfile known = skins.get(name);
        if (known != null) {
            m.setProfile(ResolvableProfile.resolvableProfile(known));
            return;
        }
        PlayerProfile profile = Bukkit.createProfile(name);
        plugin.tasks().io("npc skin " + name, () -> {
            boolean ok = profile.complete(true);
            plugin.tasks().sync(() -> {
                if (ok) {
                    skins.put(name, profile);
                }
                if (m.isValid()) {
                    m.setProfile(ResolvableProfile.resolvableProfile(profile));
                }
            });
        });
    }

    long count() {
        return spawned.stream().filter(Entity::isValid).count();
    }

    boolean present() {
        return !spawned.isEmpty() && spawned.stream().allMatch(Entity::isValid);
    }

    boolean any() {
        for (Npc n : NPCS) {
            if (plugin.attractionStore().first(n.marker()) != null) {
                return true;
            }
        }
        return false;
    }

    /** Right-click on one of the NPCs. */
    void click(Player p, Entity e) {
        String role = roles.get(e.getUniqueId());
        if (role == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = clickCooldown.get(p.getUniqueId());
        if (last != null && now - last < 1200) {
            return;
        }
        clickCooldown.put(p.getUniqueId(), now);
        SoundSpec.of("entity.villager.yes", 0.7f, 1.2f).play(p);
        e.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getLocation().add(0, 2.2, 0), 6, 0.3, 0.2, 0.3, 0);
        switch (role) {
            case "rtp" -> {
                plugin.messages().sendRaw(p, "npcs.rtp.talk");
                plugin.rtp().start(p, null, false, null);
            }
            case "plot" -> {
                plugin.messages().sendRaw(p, "npcs.plot.talk");
                p.performCommand("plot");
            }
            case "jobs" -> {
                plugin.messages().sendRaw(p, "npcs.jobs.talk");
                p.performCommand("jobs");
            }
            case "crates" -> {
                plugin.messages().sendRaw(p, "npcs.crates.talk");
                plugin.crateCarousel().info(p);
            }
            default -> {
                List<String> lines = plugin.messages().rawList("npcs.idle.lines");
                if (!lines.isEmpty()) {
                    p.sendMessage(plugin.messages().format(lines.get(ThreadLocalRandom.current().nextInt(lines.size()))));
                }
            }
        }
    }

    void clear() {
        spawned.forEach(e -> {
            if (e.isValid()) {
                e.remove();
            }
        });
        spawned.clear();
        roles.clear();
    }
}
