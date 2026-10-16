package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spawn glide like on CraftAttack: on the spawn, press jump twice (double jump) and you glide down once – no elytra
 * needed and no fall damage. Landing ends the glide; outside the spawn nothing changes.
 */
public final class SpawnGlideService implements Listener {

    private final MythosCraft plugin;
    /** Players we gave allowFlight to (so we never touch flight given by ranks or admins). */
    private final Set<UUID> armed = new HashSet<>();
    private final Set<UUID> gliding = new HashSet<>();
    private final Map<UUID, Long> safeUntil = new HashMap<>();
    private boolean enabled = true;
    private double boost = 1.1;

    public SpawnGlideService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.spawn-glide");
        enabled = cfg.bool("enabled", true);
        boost = cfg.decimal("boost", 1.1, 0, 5);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 4, "spawn glide", this::tick);
    }

    private static boolean creativeLike(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean eligible(Player p) {
        if (plugin.rankPerks() != null && plugin.rankPerks().fliesAtSpawn(p)) {
            return false; // MYTHOS flies at the spawn instead
        }
        if (!enabled || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)
                || !plugin.attractionStore().inside(p.getLocation(), 0)) {
            return false;
        }
        // no glide during the spawn games (ring course, dropper, parkour) or with an elytra on
        org.bukkit.inventory.ItemStack chest = p.getInventory().getChestplate();
        if (chest != null && chest.getType() == org.bukkit.Material.ELYTRA) {
            return false;
        }
        return !plugin.attractions().rings().inRun(p) && !plugin.attractions().games().busy(p) && !plugin.parkour().inRun(p);
    }

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean onGround = ((org.bukkit.entity.Entity) p).isOnGround();
            if (gliding.contains(id) && (onGround || p.isInWater())) {
                gliding.remove(id);
                p.setGliding(false);
                safeUntil.put(id, System.currentTimeMillis() + 1500);
            }
            boolean want = eligible(p) && !gliding.contains(id);
            if (want && onGround && !p.getAllowFlight()) {
                p.setAllowFlight(true);
                armed.add(id);
            } else if (!want && armed.remove(id) && !gliding.contains(id) && !creativeLike(p)) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (creativeLike(p)) {
            armed.remove(p.getUniqueId());
            return; // creative / spectator fly normally
        }
        if (!event.isFlying() || !armed.contains(p.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        armed.remove(p.getUniqueId());
        p.setFlying(false);
        p.setAllowFlight(false);
        if (!eligible(p)) {
            return;
        }
        gliding.add(p.getUniqueId());
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 0.01) {
            dir.normalize();
        }
        p.setVelocity(dir.multiply(boost).setY(0.55));
        plugin.tasks().later(4, "start glide", () -> {
            if (p.isOnline() && gliding.contains(p.getUniqueId())) {
                p.setGliding(true);
            }
        });
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.1, 0.3, 0.05);
        SoundSpec.of("entity.phantom.flap", 0.8f, 1.3f).play(p);
        plugin.messages().actionBar(p, "attractions.glide");
    }

    /** Keeps the glide going without an elytra (the server would stop it every tick). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player p && !event.isGliding() && gliding.contains(p.getUniqueId())
                && !((org.bukkit.entity.Entity) p).isOnGround() && !p.isInWater()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL && event.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            return;
        }
        UUID id = p.getUniqueId();
        if (gliding.contains(id) || safeUntil.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            event.setCancelled(true);
            gliding.remove(id);
            safeUntil.put(id, System.currentTimeMillis() + 1500);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if ((armed.remove(id) | gliding.remove(id)) && !creativeLike(event.getPlayer())) {
            event.getPlayer().setAllowFlight(false);
        }
        safeUntil.remove(id);
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        for (UUID id : armed) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && !creativeLike(p)) {
                p.setAllowFlight(false);
            }
        }
        armed.clear();
        gliding.clear();
    }
}
