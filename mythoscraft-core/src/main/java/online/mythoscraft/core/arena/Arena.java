package online.mythoscraft.core.arena;

import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.protection.Region;
import online.mythoscraft.core.util.Cuboid;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** A PvP/event area with its own protection rules, spawn points and join rules. */
public final class Arena {

    public enum InventoryMode {
        /** Players keep their own inventory inside the arena. */
        KEEP,
        /** Inventory is stored on join (crash-safe in the database), cleared, and restored on leave. */
        SEPARATE
    }

    private final String name;
    private String world;
    private Cuboid bounds;
    private final List<String> spawns = new ArrayList<>();
    private String spectator;
    private ArenaState state = ArenaState.DISABLED;

    private boolean pvp = true;
    private boolean build;
    private boolean breakBlocks;
    private boolean explosions;
    private boolean mobSpawning;
    private boolean itemDrop = true;
    private boolean keepInventory = true;
    private InventoryMode inventoryMode = InventoryMode.SEPARATE;
    private String kit = "";
    private int maxPlayers = 20;
    private boolean respawnInArena = true;
    private boolean resetOnEmpty;
    private boolean blockCommands = true;
    private int priority = 100;

    private Region region;
    private boolean tournamentMode;

    public Arena(String name, String world, Cuboid bounds) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.world = world;
        this.bounds = bounds;
        rebuildRegion();
    }

    /** The region that the protection system uses for this arena (higher priority than normal regions). */
    public Region region() {
        return region;
    }

    public void rebuildRegion() {
        Region r = new Region("arena:" + name, Region.Type.ARENA, world, bounds, priority);
        if (tournamentMode) {
            // tournaments: everything allowed – the tournament tracker keeps the arena itself intact
            for (Flag f : Flag.values()) {
                r.flag(f, true);
            }
            r.flag(Flag.CONTAINER, false);
            r.flag(Flag.MOB_SPAWNING, false);
            this.region = r;
            return;
        }
        r.flag(Flag.PVP, pvp);
        r.flag(Flag.BUILD, build);
        r.flag(Flag.BREAK, breakBlocks);
        r.flag(Flag.EXPLOSIONS, explosions);
        r.flag(Flag.FIRE, build);
        r.flag(Flag.MOB_SPAWNING, mobSpawning);
        r.flag(Flag.ITEM_DROP, itemDrop);
        r.flag(Flag.DAMAGE, true);
        r.flag(Flag.MOB_DAMAGE, true);
        r.flag(Flag.HUNGER, true);
        r.flag(Flag.INTERACT, false);
        r.flag(Flag.CONTAINER, false);
        r.flag(Flag.USE, true);
        r.flag(Flag.ENTITY_DAMAGE, false);
        this.region = r;
    }

    public boolean tournamentMode() {
        return tournamentMode;
    }

    public void tournamentMode(boolean enabled) {
        this.tournamentMode = enabled;
        rebuildRegion();
    }

    public boolean contains(Location loc) {
        return loc.getWorld() != null && loc.getWorld().getName().equals(world)
                && bounds.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Location randomSpawn() {
        List<Location> valid = spawnLocations();
        if (valid.isEmpty()) {
            return null;
        }
        return valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
    }

    public List<Location> spawnLocations() {
        List<Location> out = new ArrayList<>();
        for (String s : spawns) {
            Location l = LocationCodec.fromString(s);
            if (l != null) {
                out.add(l);
            }
        }
        return out;
    }

    public Location spectatorLocation() {
        Location l = LocationCodec.fromString(spectator);
        return l != null ? l : randomSpawn();
    }

    /** Applies a "/mythos arena set" option. Returns false for unknown options or invalid values. */
    public boolean set(String key, String value) {
        String k = key.toLowerCase(Locale.ROOT);
        String v = value.toLowerCase(Locale.ROOT);
        Boolean b = v.equals("true") || v.equals("on") || v.equals("ja") ? Boolean.TRUE
                : v.equals("false") || v.equals("off") || v.equals("nein") ? Boolean.FALSE : null;
        switch (k) {
            case "pvp" -> pvp = require(b);
            case "build" -> build = require(b);
            case "break" -> breakBlocks = require(b);
            case "explosions" -> explosions = require(b);
            case "mob-spawning" -> mobSpawning = require(b);
            case "item-drop" -> itemDrop = require(b);
            case "keep-inventory" -> keepInventory = require(b);
            case "respawn-in-arena" -> respawnInArena = require(b);
            case "reset-on-empty" -> resetOnEmpty = require(b);
            case "block-commands" -> blockCommands = require(b);
            case "inventory" -> inventoryMode = InventoryMode.valueOf(v.toUpperCase(Locale.ROOT));
            case "kit" -> kit = v.equals("none") || v.equals("-") ? "" : v;
            case "max-players" -> maxPlayers = Math.max(1, Math.min(500, Integer.parseInt(v)));
            case "priority" -> priority = Integer.parseInt(v);
            default -> {
                return false;
            }
        }
        rebuildRegion();
        return true;
    }

    private static boolean require(Boolean b) {
        if (b == null) {
            throw new IllegalArgumentException("expected true/false");
        }
        return b;
    }

    public static List<String> settingKeys() {
        return List.of("pvp", "build", "break", "explosions", "mob-spawning", "item-drop", "keep-inventory", "inventory",
                "kit", "max-players", "respawn-in-arena", "reset-on-empty", "block-commands", "priority");
    }

    public String setting(String key) {
        return switch (key) {
            case "pvp" -> String.valueOf(pvp);
            case "build" -> String.valueOf(build);
            case "break" -> String.valueOf(breakBlocks);
            case "explosions" -> String.valueOf(explosions);
            case "mob-spawning" -> String.valueOf(mobSpawning);
            case "item-drop" -> String.valueOf(itemDrop);
            case "keep-inventory" -> String.valueOf(keepInventory);
            case "inventory" -> inventoryMode.name().toLowerCase(Locale.ROOT);
            case "kit" -> kit.isEmpty() ? "none" : kit;
            case "max-players" -> String.valueOf(maxPlayers);
            case "respawn-in-arena" -> String.valueOf(respawnInArena);
            case "reset-on-empty" -> String.valueOf(resetOnEmpty);
            case "block-commands" -> String.valueOf(blockCommands);
            case "priority" -> String.valueOf(priority);
            default -> "?";
        };
    }

    public String name() {
        return name;
    }

    public String world() {
        return world;
    }

    public Cuboid bounds() {
        return bounds;
    }

    public void redefine(String world, Cuboid bounds) {
        this.world = world;
        this.bounds = bounds;
        rebuildRegion();
    }

    public List<String> spawns() {
        return spawns;
    }

    public String spectator() {
        return spectator;
    }

    public void spectator(String encoded) {
        this.spectator = encoded;
    }

    public ArenaState state() {
        return state;
    }

    public void state(ArenaState state) {
        this.state = state;
    }

    public boolean keepInventory() {
        return keepInventory;
    }

    public InventoryMode inventoryMode() {
        return inventoryMode;
    }

    public String kit() {
        return kit;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public boolean respawnInArena() {
        return respawnInArena;
    }

    public boolean resetOnEmpty() {
        return resetOnEmpty;
    }

    public boolean blockCommands() {
        return blockCommands;
    }
}
