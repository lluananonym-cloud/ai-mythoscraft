package online.mythoscraft.core.teleport;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.protection.Region;
import online.mythoscraft.core.util.Cuboid;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * The central spawn. Its location and its protection flags live in config.yml (section "spawn"); the protected
 * area itself is the region named in spawn.protection.region, created automatically around the spawn.
 */
public final class SpawnManager implements Listener {

    private final MythosCraft plugin;
    private boolean enabled = true;
    private int delay = 3;
    private boolean cancelOnMove = true;
    private boolean respawnAtSpawn = true;
    private boolean firstJoinTeleport = true;
    private boolean joinTeleport;
    private String regionName = "spawn";
    private boolean autoRegion = true;
    private int radius = 64;
    private final Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);

    public SpawnManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("spawn");
        enabled = cfg.bool("enabled", true);
        delay = cfg.integer("teleport-delay", 3, 0, 120);
        cancelOnMove = cfg.bool("cancel-on-move", true);
        respawnAtSpawn = cfg.bool("respawn-at-spawn", true);
        firstJoinTeleport = cfg.bool("teleport-on-first-join", true);
        joinTeleport = cfg.bool("teleport-on-join", false);
        regionName = cfg.string("protection.region", "spawn").toLowerCase();
        autoRegion = cfg.bool("protection.auto-create", true);
        radius = cfg.integer("protection.radius", 64, 1, 10_000);
        flags.clear();
        for (Flag f : Flag.values()) {
            if (cfg.has(f.key())) {
                flags.put(f, cfg.bool(f.key(), false));
            }
        }
        // no monsters at spawn unless the admin explicitly allows it (spawn.mob-spawning: true)
        flags.putIfAbsent(Flag.MOB_SPAWNING, false);
        ensureRegion();
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 100, 100, "spawn monster cleanup", this::clearMonsters);
    }

    private boolean monstersAllowed() {
        return Boolean.TRUE.equals(flags.get(Flag.MOB_SPAWNING));
    }

    private boolean nearSpawn(Location loc) {
        Location spawn = spawn();
        if (spawn == null || loc.getWorld() == null || !loc.getWorld().equals(spawn.getWorld())) {
            return false;
        }
        Region region = plugin.regions().get(regionName);
        if (region != null && region.contains(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
            return true;
        }
        double dx = loc.getX() - spawn.getX();
        double dz = loc.getZ() - spawn.getZ();
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /** Blocks every natural monster spawn at spawn (also when the region was not created yet). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMonsterSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (!enabled || monstersAllowed() || !(event.getEntity() instanceof org.bukkit.entity.Enemy)) {
            return;
        }
        switch (event.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG, DEFAULT -> {
                return;
            }
            default -> {
            }
        }
        if (nearSpawn(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Removes monsters that walked into the spawn (never named or plugin/event mobs). */
    private void clearMonsters() {
        Location spawn = spawn();
        if (!enabled || monstersAllowed() || spawn == null) {
            return;
        }
        for (org.bukkit.entity.LivingEntity e : spawn.getWorld().getLivingEntities()) {
            if (e instanceof org.bukkit.entity.Enemy && e.customName() == null && e.getPersistentDataContainer().getKeys().isEmpty()
                    && nearSpawn(e.getLocation())) {
                e.getWorld().spawnParticle(org.bukkit.Particle.POOF, e.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
                e.remove();
            }
        }
    }

    /** Creates the spawn region if missing and applies the spawn flags from config.yml to it. */
    private void ensureRegion() {
        if (!enabled) {
            return;
        }
        Region region = plugin.regions().get(regionName);
        if (region == null && autoRegion) {
            Location spawn = spawn();
            if (spawn == null) {
                return;
            }
            World w = spawn.getWorld();
            region = plugin.regions().create(regionName, w.getName(), Cuboid.of(spawn.getBlockX() - radius, w.getMinHeight(),
                    spawn.getBlockZ() - radius, spawn.getBlockX() + radius, w.getMaxHeight() - 1, spawn.getBlockZ() + radius), 10);
            plugin.getLogger().info("Created spawn protection region '" + regionName + "' (radius " + radius + ")");
        }
        if (region != null) {
            flags.forEach(region::flag);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String regionName() {
        return regionName;
    }

    /** Configured spawn or the main world's spawn if none is set / its world is not loaded. */
    public Location spawn() {
        Location loc = LocationCodec.read(plugin.getConfig().getConfigurationSection("spawn.location"));
        if (loc != null) {
            return loc;
        }
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return main == null ? null : main.getSpawnLocation().add(0.5, 0, 0.5);
    }

    public void setSpawn(Location loc) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("spawn.location");
        if (sec == null) {
            sec = plugin.getConfig().createSection("spawn.location");
        }
        LocationCodec.write(sec, loc);
        plugin.saveConfig();
        loc.getWorld().setSpawnLocation(loc);
        ensureRegion();
    }

    /** Keeps config.yml in sync when an admin edits a flag of the spawn region per command. */
    public void syncFlag(Flag flag, Boolean value) {
        if (value == null) {
            plugin.getConfig().set("spawn." + flag.key(), null);
            flags.remove(flag);
        } else {
            plugin.getConfig().set("spawn." + flag.key(), value);
            flags.put(flag, value);
        }
        plugin.saveConfig();
    }

    public void teleport(Player player) {
        Location spawn = spawn();
        if (!enabled || spawn == null) {
            plugin.messages().send(player, "spawn.not-set");
            return;
        }
        if (plugin.arenas().isInArena(player)) {
            plugin.messages().send(player, "arena.leave-first");
            return;
        }
        plugin.teleports().teleport(player, spawn, delay, cancelOnMove, plugin.messages().get("spawn.name"), null);
    }

    /** Called after the player's data is ready. */
    public void handleJoin(Player player, boolean firstJoin) {
        Location spawn = spawn();
        if (!enabled || spawn == null) {
            return;
        }
        if ((firstJoin && firstJoinTeleport) || joinTeleport) {
            online.mythoscraft.core.cosmetic.CompanionService.unmount(player);
            player.teleportAsync(spawn);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!enabled || !respawnAtSpawn || event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location spawn = spawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
