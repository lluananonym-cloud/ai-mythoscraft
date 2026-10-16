package online.mythoscraft.core.teleport;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random teleport into the wild. Chunks are loaded asynchronously, a spot is accepted only with solid ground, two
 * free blocks above, no liquids or dangerous blocks around, inside the world border and outside protected regions.
 */
public final class RtpService {

    /** Per-world search area. */
    public record Area(String world, int minRadius, int maxRadius, boolean aroundSpawn) {
    }

    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.WATER, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.BEDROCK, Material.SCAFFOLDING,
            Material.COBWEB);

    private final MythosCraft plugin;
    private final Cooldowns cooldowns;
    private final Map<String, Area> areas = new LinkedHashMap<>();
    private final Map<String, Integer> cooldownByRank = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();
    private boolean enabled = true;
    private String defaultWorld = "world";
    private int countdown = 3;
    private int cooldownSeconds = 300;
    private long cost;
    private int attempts = 25;
    private boolean avoidRegions = true;

    public RtpService(MythosCraft plugin, Cooldowns cooldowns) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("rtp");
        enabled = cfg.bool("enabled", true);
        countdown = cfg.integer("countdown-seconds", 3, 0, 60);
        cooldownSeconds = cfg.integer("cooldown-seconds", 300, 0, 86_400);
        cost = cfg.longValue("cost-coins", 0, 0, 1_000_000_000L);
        attempts = cfg.integer("attempts", 25, 1, 100);
        avoidRegions = cfg.bool("avoid-regions", true);
        cooldownByRank.clear();
        ConfigReader ranks = cfg.sub("cooldown-by-rank");
        for (String rank : ranks.keys()) {
            cooldownByRank.put(rank.toUpperCase(Locale.ROOT), ranks.integer(rank, cooldownSeconds, 0, 86_400));
        }
        areas.clear();
        ConfigReader worlds = cfg.sub("worlds");
        for (String name : worlds.keys()) {
            ConfigReader w = worlds.sub(name);
            int min = w.integer("min-radius", 300, 0, 29_000_000);
            int max = w.integer("max-radius", 5000, min + 1, 29_000_000);
            areas.put(name.toLowerCase(Locale.ROOT), new Area(name, min, max, w.string("center", "spawn").equalsIgnoreCase("spawn")));
        }
        if (areas.isEmpty()) {
            World first = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (first != null) {
                areas.put(first.getName().toLowerCase(Locale.ROOT), new Area(first.getName(), 300, 5000, true));
            }
        }
        defaultWorld = cfg.string("default-world", Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName());
    }

    public boolean enabled() {
        return enabled;
    }

    public List<String> worldNames() {
        List<String> out = new ArrayList<>();
        for (Area a : areas.values()) {
            if (Bukkit.getWorld(a.world()) != null) {
                out.add(a.world());
            }
        }
        return out;
    }

    public int cooldownFor(Player player) {
        if (player.hasPermission("mythos.rtp.bypass-cooldown")) {
            return 0;
        }
        Integer byRank = cooldownByRank.get(plugin.ranks().rankOf(player).id().toUpperCase(Locale.ROOT));
        return byRank == null ? cooldownSeconds : Math.min(cooldownSeconds, byRank);
    }

    /**
     * Starts a random teleport.
     *
     * @param worldName null = the player's world if configured, else the default world
     * @param free      ignore cooldown and cost (RTP scroll)
     * @param onSuccess runs after the player arrived
     */
    public boolean start(Player player, String worldName, boolean free, Runnable onSuccess) {
        if (!enabled) {
            plugin.messages().send(player, "rtp.disabled");
            return false;
        }
        if (plugin.arenas().isInArena(player) || plugin.tournaments().isParticipant(player)) {
            plugin.messages().send(player, "arena.leave-first");
            return false;
        }
        if (searching.contains(player.getUniqueId())) {
            plugin.messages().send(player, "rtp.already-searching");
            return false;
        }
        Area area = area(player, worldName);
        World world = area == null ? null : Bukkit.getWorld(area.world());
        if (world == null) {
            plugin.messages().send(player, "rtp.unknown-world", "worlds", String.join(", ", worldNames()));
            return false;
        }
        if (!free) {
            long left = cooldowns.remainingSeconds("rtp", player.getUniqueId());
            if (left > 0) {
                plugin.messages().send(player, "rtp.cooldown", "seconds", left);
                return false;
            }
            PlayerData data = plugin.playerData().get(player);
            if (cost > 0 && (data == null || data.coins() < cost)) {
                plugin.messages().send(player, "rtp.not-enough", "cost", cost);
                return false;
            }
        }
        searching.add(player.getUniqueId());
        plugin.messages().actionBar(player, "rtp.searching");
        Location center = area.aroundSpawn() && plugin.spawn().spawn() != null && world.equals(plugin.spawn().spawn().getWorld())
                ? plugin.spawn().spawn() : world.getSpawnLocation();
        attempt(player, world, area, center, attempts, free, onSuccess);
        return true;
    }

    private Area area(Player player, String worldName) {
        if (worldName != null) {
            Area a = areas.get(worldName.toLowerCase(Locale.ROOT));
            if (a != null) {
                return a;
            }
            for (Area candidate : areas.values()) {
                String w = candidate.world().toLowerCase(Locale.ROOT);
                String q = worldName.toLowerCase(Locale.ROOT);
                if ((q.startsWith("nether") && w.endsWith("nether")) || (q.startsWith("end") && w.endsWith("end"))
                        || (q.startsWith("over") && !w.contains("nether") && !w.contains("end"))) {
                    return candidate;
                }
            }
            return null;
        }
        Area own = areas.get(player.getWorld().getName().toLowerCase(Locale.ROOT));
        return own != null ? own : areas.get(defaultWorld.toLowerCase(Locale.ROOT));
    }

    private void attempt(Player player, World world, Area area, Location center, int left, boolean free, Runnable onSuccess) {
        if (!player.isOnline()) {
            searching.remove(player.getUniqueId());
            return;
        }
        if (left <= 0) {
            searching.remove(player.getUniqueId());
            plugin.messages().send(player, "rtp.failed");
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble(Math.PI * 2);
        double dist = Math.sqrt(r.nextDouble(
                (double) area.minRadius() * area.minRadius(), (double) area.maxRadius() * area.maxRadius()));
        int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, error) -> plugin.tasks().sync(() -> {
            Location spot = error == null ? safeSpot(world, x, z) : null;
            if (spot == null || !world.getWorldBorder().isInside(spot) || (avoidRegions && !plugin.regions().at(spot).isEmpty())) {
                attempt(player, world, area, center, left - 1, free, onSuccess);
                return;
            }
            searching.remove(player.getUniqueId());
            spot.setYaw(player.getLocation().getYaw());
            plugin.teleports().teleport(player, spot, countdown, true, plugin.messages().get("rtp.name"), () -> {
                if (!free) {
                    cooldowns.set("rtp", player.getUniqueId(), cooldownFor(player) * 1000L);
                    PlayerData data = plugin.playerData().get(player);
                    if (cost > 0 && data != null && plugin.economy().withdraw(data, Currency.COINS, cost) == EconomyManager.Result.SUCCESS) {
                        plugin.messages().send(player, "rtp.paid", "cost", cost);
                    }
                }
                plugin.messages().send(player, "rtp.arrived", "x", spot.getBlockX(), "z", spot.getBlockZ(), "world", world.getName());
                if (plugin.bases() != null) {
                    plugin.bases().afterRtp(player, spot);
                }
                if (onSuccess != null) {
                    onSuccess.run();
                }
            });
        }));
    }

    /** Standing position above solid ground, or null. */
    static Location safeSpot(World world, int x, int z) {
        Block floor;
        if (world.getEnvironment() == World.Environment.NETHER) {
            floor = null;
            for (int y = 32; y < 118; y++) {
                Block b = world.getBlockAt(x, y, z);
                if (standable(b)) {
                    floor = b;
                    break;
                }
            }
        } else {
            floor = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!standable(floor) || floor.getY() <= world.getMinHeight() + 1) {
                return null;
            }
        }
        if (floor == null) {
            return null;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material m = floor.getRelative(dx, 1, dz).getType();
                if (m == Material.LAVA || m == Material.FIRE || m == Material.WATER) {
                    return null;
                }
            }
        }
        return floor.getLocation().add(0.5, 1, 0.5);
    }

    private static boolean standable(Block floor) {
        Material ground = floor.getType();
        Block feet = floor.getRelative(0, 1, 0);
        Block head = floor.getRelative(0, 2, 0);
        return ground.isSolid() && !UNSAFE.contains(ground) && !floor.isLiquid() && feet.isPassable() && !feet.isLiquid()
                && !UNSAFE.contains(feet.getType()) && head.isPassable() && !head.isLiquid();
    }

    public void forget(UUID player) {
        searching.remove(player);
    }
}
