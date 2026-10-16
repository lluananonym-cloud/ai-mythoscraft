package online.mythoscraft.core.arena;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.protection.Region;
import online.mythoscraft.core.util.Cuboid;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaManager {

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, ArenaSession> sessions = new HashMap<>();
    private final Map<String, Kit> kits = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final ArenaSnapshotService snapshots;
    private final ArenaInventoryStore inventories;
    private List<String> allowedCommands = List.of("arena", "msg", "r", "helpop");

    public ArenaManager(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "arenas.yml");
        this.snapshots = new ArenaSnapshotService(plugin);
        this.inventories = new ArenaInventoryStore(plugin.database());
    }

    public void reload(ConfigReader root) {
        snapshots.reload(root);
        allowedCommands = root.strings("arena.allowed-commands").stream().map(s -> s.toLowerCase(Locale.ROOT).replace("/", "")).toList();
        if (allowedCommands.isEmpty()) {
            allowedCommands = List.of("arena", "msg", "r", "helpop");
        }
        kits.clear();
        ConfigReader kitSection = root.sub("kits");
        for (String id : kitSection.keys()) {
            kits.put(id.toLowerCase(Locale.ROOT), Kit.parse(id, kitSection.sub(id)));
        }
        load();
    }

    private void load() {
        Map<String, ArenaState> previousStates = new HashMap<>();
        arenas.values().forEach(a -> previousStates.put(a.name(), a.state()));
        YamlConfiguration yaml = store.load(false);
        arenas.clear();
        ConfigurationSection sec = yaml.getConfigurationSection("arenas");
        int failed = 0;
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                try {
                    Arena arena = read(name, sec.getConfigurationSection(name));
                    ArenaState prev = previousStates.get(arena.name());
                    if (prev == ArenaState.RUNNING || prev == ArenaState.RESETTING) {
                        arena.state(prev);
                    }
                    arenas.put(arena.name(), arena);
                } catch (RuntimeException e) {
                    failed++;
                    plugin.getLogger().severe("Failed to load arena '" + name + "': " + e.getMessage());
                }
            }
        }
        for (Map.Entry<UUID, ArenaSession> e : new ArrayList<>(sessions.entrySet())) {
            if (!arenas.containsKey(e.getValue().arena())) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    leave(p, true, true);
                }
            }
        }
        publishRegions();
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas" + (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    private static Arena read(String name, ConfigurationSection s) {
        List<Integer> min = s.getIntegerList("min");
        List<Integer> max = s.getIntegerList("max");
        String world = s.getString("world");
        if (world == null || min.size() != 3 || max.size() != 3) {
            throw new IllegalArgumentException("world/min/max missing");
        }
        Arena a = new Arena(name, world, Cuboid.of(min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2)));
        a.spawns().addAll(s.getStringList("spawns"));
        a.spectator(s.getString("spectator"));
        ConfigurationSection settings = s.getConfigurationSection("settings");
        if (settings != null) {
            for (String key : settings.getKeys(false)) {
                try {
                    a.set(key, String.valueOf(settings.get(key)));
                } catch (IllegalArgumentException ignored) {
                    // keep default for broken values
                }
            }
        }
        // saved as ENABLED / DISABLED (older files may contain a runtime state like WAITING)
        a.state("DISABLED".equalsIgnoreCase(s.getString("state", "ENABLED")) ? ArenaState.DISABLED : ArenaState.WAITING);
        return a;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("MythosCraft Arenen – am besten per /mythos arena bearbeiten."));
        for (Arena a : arenas.values()) {
            String p = "arenas." + a.name() + ".";
            Cuboid b = a.bounds();
            yaml.set(p + "world", a.world());
            yaml.set(p + "min", List.of(b.minX(), b.minY(), b.minZ()));
            yaml.set(p + "max", List.of(b.maxX(), b.maxY(), b.maxZ()));
            yaml.set(p + "state", a.state() == ArenaState.DISABLED ? "DISABLED" : "ENABLED");
            yaml.set(p + "spawns", a.spawns());
            yaml.set(p + "spectator", a.spectator());
            for (String key : Arena.settingKeys()) {
                yaml.set(p + "settings." + key, a.setting(key));
            }
        }
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
    }

    public void publishRegions() {
        List<Region> list = new ArrayList<>();
        arenas.values().forEach(a -> list.add(a.region()));
        plugin.regions().setExternal("arenas", list);
    }

    // ---- queries ------------------------------------------------------------------------------------------

    public Collection<Arena> arenas() {
        return arenas.values();
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public Arena arenaAt(Location loc) {
        for (Arena a : arenas.values()) {
            if (a.contains(loc)) {
                return a;
            }
        }
        return null;
    }

    public boolean isInArena(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public ArenaSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public int players(Arena arena) {
        int n = 0;
        for (ArenaSession s : sessions.values()) {
            if (s.arena().equals(arena.name()) && !s.spectator()) {
                n++;
            }
        }
        return n;
    }

    public Kit kit(String id) {
        return kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> kitIds() {
        return kits.keySet();
    }

    public ArenaInventoryStore inventories() {
        return inventories;
    }

    public ArenaSnapshotService snapshots() {
        return snapshots;
    }

    public boolean isInternalTeleport(Player player) {
        return internalTeleports.contains(player.getUniqueId());
    }

    public boolean commandAllowed(String label) {
        return allowedCommands.contains(label.toLowerCase(Locale.ROOT));
    }

    // ---- admin --------------------------------------------------------------------------------------------

    public Arena create(String name, String world, Cuboid bounds) {
        Arena arena = new Arena(name, world, bounds);
        arenas.put(arena.name(), arena);
        publishRegions();
        save();
        return arena;
    }

    public void delete(Arena arena) {
        kickAll(arena);
        arenas.remove(arena.name());
        publishRegions();
        save();
    }

    public void enable(Arena arena) {
        if (arena.state() == ArenaState.DISABLED) {
            arena.state(ArenaState.WAITING);
            save();
        }
    }

    public void disable(Arena arena) {
        kickAll(arena);
        arena.state(ArenaState.DISABLED);
        save();
    }

    private void kickAll(Arena arena) {
        for (Map.Entry<UUID, ArenaSession> e : new ArrayList<>(sessions.entrySet())) {
            if (e.getValue().arena().equals(arena.name())) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    leave(p, true, false);
                    plugin.messages().send(p, "arena.closed", "arena", arena.name());
                }
            }
        }
    }

    public void reset(Arena arena, CommandSender report) {
        if (!snapshots.exists(arena)) {
            if (report != null) {
                plugin.messages().send(report, "arena.no-snapshot", "arena", arena.name());
            }
            return;
        }
        kickAll(arena);
        ArenaState after = arena.state() == ArenaState.DISABLED ? ArenaState.DISABLED : ArenaState.WAITING;
        arena.state(ArenaState.RESETTING);
        long start = System.currentTimeMillis();
        snapshots.restore(arena, error -> {
            arena.state(after);
            if (error != null) {
                plugin.getLogger().warning("Arena reset of " + arena.name() + " failed: " + error);
            } else {
                plugin.getLogger().info("Arena " + arena.name() + " reset in " + (System.currentTimeMillis() - start) + " ms");
            }
            if (report != null && (!(report instanceof Player p) || p.isOnline())) {
                plugin.messages().send(report, error == null ? "arena.reset-done" : "arena.reset-failed",
                        "arena", arena.name(), "error", error == null ? "" : error);
            }
        });
    }

    // ---- players ------------------------------------------------------------------------------------------

    public void join(Player player, Arena arena, boolean spectate) {
        if (sessions.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "arena.already-in");
            return;
        }
        if (arena.state() == ArenaState.DISABLED) {
            plugin.messages().send(player, "arena.disabled", "arena", arena.name());
            return;
        }
        if (arena.state() == ArenaState.RESETTING) {
            plugin.messages().send(player, "arena.resetting", "arena", arena.name());
            return;
        }
        if (arena.tournamentMode() || plugin.tournaments().isParticipant(player)) {
            plugin.messages().send(player, "tournament.arena-busy");
            return;
        }
        long combat = plugin.combat().remainingSeconds(player);
        if (combat > 0) {
            plugin.messages().send(player, "teleport.in-combat", "seconds", combat);
            return;
        }
        Location target = spectate ? arena.spectatorLocation() : arena.randomSpawn();
        if (target == null) {
            plugin.messages().send(player, "arena.no-spawns", "arena", arena.name());
            return;
        }
        if (!spectate && players(arena) >= arena.maxPlayers()) {
            plugin.messages().send(player, "arena.full", "arena", arena.name());
            return;
        }
        plugin.teleports().cancel(player, null);
        Location back = player.getLocation();
        byte[] saved = null;
        if (spectate || arena.inventoryMode() == Arena.InventoryMode.SEPARATE) {
            saved = PlayerStateCodec.encode(player);
            inventories.store(player.getUniqueId(), arena.name(), saved, LocationCodec.toString(back));
            player.getInventory().clear();
        }
        sessions.put(player.getUniqueId(), new ArenaSession(arena.name(), spectate, back, player.getGameMode(), saved));
        teleportInternal(player, target);
        if (spectate) {
            player.setGameMode(GameMode.SPECTATOR);
            plugin.messages().send(player, "arena.spectating", "arena", arena.name());
            return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        prepare(player, arena);
        if (arena.state() == ArenaState.WAITING) {
            arena.state(ArenaState.RUNNING);
        }
        plugin.messages().send(player, "arena.joined", "arena", arena.name(), "players", players(arena));
        broadcastToArena(arena, "arena.player-joined", player);
    }

    /** Heal, clear effects and hand out the kit. */
    public void prepare(Player player, Arena arena) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(10);
        player.setFireTicks(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (!arena.kit().isEmpty()) {
            Kit kit = kit(arena.kit());
            if (kit != null) {
                if (arena.inventoryMode() == Arena.InventoryMode.SEPARATE) {
                    player.getInventory().clear();
                }
                kit.give(player);
            } else {
                plugin.getLogger().warning("Arena " + arena.name() + " uses unknown kit '" + arena.kit() + "'");
            }
        }
    }

    /**
     * Removes the player from the arena and restores their inventory.
     *
     * @param teleportBack teleport to where they joined from
     */
    public void leave(Player player, boolean teleportBack, boolean message) {
        ArenaSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            if (message) {
                plugin.messages().send(player, "arena.not-in");
            }
            return;
        }
        Arena arena = get(session.arena());
        if (session.savedState() != null) {
            try {
                PlayerStateCodec.restore(player, session.savedState());
                inventories.delete(player.getUniqueId());
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("Could not restore inventory of " + player.getName() + " (kept in database for recovery): " + e.getMessage());
                plugin.messages().send(player, "arena.restore-failed");
            }
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        if (player.getGameMode() != session.previousGameMode()) {
            player.setGameMode(session.previousGameMode());
        }
        if (teleportBack && session.returnLocation() != null && session.returnLocation().getWorld() != null) {
            teleportInternal(player, session.returnLocation());
        }
        if (message) {
            plugin.messages().send(player, "arena.left", "arena", session.arena());
        }
        if (arena != null && !session.spectator()) {
            broadcastToArena(arena, "arena.player-left", player);
            if (players(arena) == 0 && arena.state() == ArenaState.RUNNING) {
                arena.state(ArenaState.WAITING);
                if (arena.resetOnEmpty() && snapshots.exists(arena)) {
                    reset(arena, null);
                }
            }
        }
    }

    public void teleportInternal(Player player, Location target) {
        internalTeleports.add(player.getUniqueId());
        try {
            online.mythoscraft.core.cosmetic.CompanionService.unmount(player);
            player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            internalTeleports.remove(player.getUniqueId());
        }
    }

    private void broadcastToArena(Arena arena, String key, Player subject) {
        for (Map.Entry<UUID, ArenaSession> e : sessions.entrySet()) {
            if (e.getValue().arena().equals(arena.name()) && !e.getKey().equals(subject.getUniqueId())) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    plugin.messages().send(p, key, "player", subject.getName(), "players", players(arena));
                }
            }
        }
    }

    /** After a crash the inventory copy is still in the database – give it back on the next join. */
    public void recover(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            return;
        }
        inventories.load(player.getUniqueId()).whenComplete((stored, ex) -> plugin.tasks().sync(() -> {
            if (ex != null || stored == null || !player.isOnline() || sessions.containsKey(player.getUniqueId())) {
                return;
            }
            try {
                PlayerStateCodec.restore(player, stored.data());
                inventories.delete(player.getUniqueId());
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                Location back = LocationCodec.fromString(stored.location());
                if (back != null) {
                    teleportInternal(player, back);
                }
                plugin.messages().send(player, "arena.recovered", "arena", stored.arena());
                plugin.getLogger().info("Restored arena inventory of " + player.getName() + " after an unclean shutdown.");
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("Arena inventory recovery failed for " + player.getName() + ": " + e.getMessage());
            }
        }));
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                leave(p, true, false);
            }
        }
        sessions.clear();
    }
}
