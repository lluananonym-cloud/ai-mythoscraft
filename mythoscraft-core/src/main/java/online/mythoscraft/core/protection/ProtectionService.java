package online.mythoscraft.core.protection;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Answers "is X allowed here?" from regions, then per-world and global wilderness defaults. */
public final class ProtectionService {

    private static final long DENY_MESSAGE_INTERVAL = 1500;

    private final MythosCraft plugin;
    private final RegionManager regions;
    private final EnumMap<Flag, Boolean> wilderness = new EnumMap<>(Flag.class);
    private final Map<String, EnumMap<Flag, Boolean>> worldDefaults = new HashMap<>();
    private final Set<UUID> bypassDisabled = new HashSet<>();
    private final Map<UUID, Long> lastDeny = new HashMap<>();
    private boolean pvpBypass;

    public ProtectionService(MythosCraft plugin, RegionManager regions) {
        this.plugin = plugin;
        this.regions = regions;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("protection");
        wilderness.clear();
        ConfigReader w = cfg.sub("wilderness");
        for (Flag f : Flag.values()) {
            wilderness.put(f, w.bool(f.key(), true));
        }
        worldDefaults.clear();
        ConfigReader worlds = cfg.sub("worlds");
        for (String world : worlds.keys()) {
            ConfigReader ws = worlds.sub(world);
            EnumMap<Flag, Boolean> map = new EnumMap<>(Flag.class);
            for (String key : ws.keys()) {
                Flag f = Flag.byKey(key);
                if (f == null) {
                    ws.warn(key, "unbekanntes Flag");
                } else {
                    map.put(f, ws.bool(key, true));
                }
            }
            worldDefaults.put(world, map);
        }
        pvpBypass = cfg.bool("bypass-includes-pvp", false);
    }

    public boolean query(World world, int x, int y, int z, Flag flag) {
        Boolean v = regions.index().flag(world.getName(), x, y, z, flag);
        if (v != null) {
            return v;
        }
        EnumMap<Flag, Boolean> wd = worldDefaults.get(world.getName());
        if (wd != null) {
            Boolean d = wd.get(flag);
            if (d != null) {
                return d;
            }
        }
        return wilderness.getOrDefault(flag, true);
    }

    public boolean query(Location loc, Flag flag) {
        return query(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), flag);
    }

    public boolean query(Block block, Flag flag) {
        return query(block.getWorld(), block.getX(), block.getY(), block.getZ(), flag);
    }

    public boolean canBypass(Player player) {
        return player.hasPermission(Perms.BYPASS) && !bypassDisabled.contains(player.getUniqueId());
    }

    /** Toggles bypass for a permitted admin. Returns the new state. */
    public boolean toggleBypass(Player player) {
        if (!bypassDisabled.remove(player.getUniqueId())) {
            bypassDisabled.add(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean allows(Player player, Block block, Flag flag) {
        return external(player, block.getLocation()) && (canBypass(player) || query(block, flag));
    }

    public boolean allows(Player player, Location loc, Flag flag) {
        return external(player, loc) && (canBypass(player) || query(loc, flag));
    }

    /**
     * Another plugin's protection on this server (MythosPlots registers its plot rules as a BiPredicate service):
     * custom item abilities must not change other people's plots or the roads either.
     */
    @SuppressWarnings("unchecked")
    private static boolean external(Player player, Location loc) {
        var reg = org.bukkit.Bukkit.getServicesManager().getRegistration(java.util.function.BiPredicate.class);
        if (reg == null || player == null || loc == null) {
            return true;
        }
        try {
            return ((java.util.function.BiPredicate<Player, Location>) reg.getProvider()).test(player, loc);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Like {@link #allows} but tells the player (rate limited) when it is denied. */
    public boolean check(Player player, Block block, Flag flag) {
        if (allows(player, block, flag)) {
            return true;
        }
        deny(player, flag);
        return false;
    }

    public boolean check(Player player, Location loc, Flag flag) {
        if (allows(player, loc, flag)) {
            return true;
        }
        deny(player, flag);
        return false;
    }

    public void deny(Player player, Flag flag) {
        long now = System.currentTimeMillis();
        Long last = lastDeny.get(player.getUniqueId());
        if (last != null && now - last < DENY_MESSAGE_INTERVAL) {
            return;
        }
        lastDeny.put(player.getUniqueId(), now);
        plugin.messages().actionBar(player, "protection.denied", "flag", plugin.messages().get("protection.flags." + flag.key()));
    }

    /** PvP needs to be allowed where the victim stands and where the attacker stands. */
    public boolean pvpAllowed(Player attacker, Player victim) {
        if (pvpBypass && canBypass(attacker)) {
            return true;
        }
        return query(victim.getLocation(), Flag.PVP) && query(attacker.getLocation(), Flag.PVP);
    }

    /**
     * True if a change that originates at {@code from} must not affect {@code to}: the target is in a different
     * (top) region than the source and that location denies the flag. Used for liquids, pistons, dispensers, …
     */
    public boolean blocksCrossing(Block from, Block to, Flag flag) {
        RegionIndex index = regions.index();
        String world = to.getWorld().getName();
        if (index.isEmpty(world)) {
            return false;
        }
        Region target = index.top(world, to.getX(), to.getY(), to.getZ());
        Region source = index.top(from.getWorld().getName(), from.getX(), from.getY(), from.getZ());
        return target != source && !query(to, flag);
    }

    public void forget(UUID player) {
        lastDeny.remove(player);
    }
}
