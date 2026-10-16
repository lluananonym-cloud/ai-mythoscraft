package online.mythoscraft.core.build;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Arena;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.crate.CrateType;
import online.mythoscraft.core.crate.PlacedCrate;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.protection.Region;
import online.mythoscraft.core.stats.ButtonManager;
import online.mythoscraft.core.tournament.TournamentTier;
import online.mythoscraft.core.util.Cuboid;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /spawn place and /mythos arena place &lt;tier&gt;: builds the bundled structures at the admin's position (after a
 * confirmation) and wires them up – spawn point, protection region, crates and holograms for the spawn; arena,
 * spawn ring, spectator point, snapshot and tournament assignment for arenas.
 */
public final class BuildService {

    private record Pending(String what, TournamentTier tier, String name, long expires) {
    }

    private final MythosCraft plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    /** Where the spawn and every arena were built (data/builds.yml) – needed to remove the old build on a rebuild. */
    private final online.mythoscraft.core.config.YamlStore records;
    private int blocksPerTick = 12_000;
    private List<String> spawnCrates = List.of("common", "vip", "elite", "mythos", "ancient", "halloween", "frostfest", "celestial", "inferno");
    private boolean building;

    public BuildService(MythosCraft plugin) {
        this.plugin = plugin;
        this.records = new online.mythoscraft.core.config.YamlStore(plugin, "data/builds.yml");
    }

    private void record(String path, Location origin, String tier) {
        org.bukkit.configuration.file.YamlConfiguration yaml = records.yaml();
        yaml.set(path + ".origin", LocationCodec.toString(origin));
        yaml.set(path + ".tier", tier);
        records.replace(yaml);
        records.saveAsync(plugin.tasks());
    }

    /** Old spawn origin: from the record, or derived from a spawn region that has exactly the size of the built spawn. */
    private Location oldSpawnOrigin(BlockPlan plan) {
        Location rec = LocationCodec.fromString(records.yaml().getString("spawn.origin"));
        if (rec != null) {
            return rec;
        }
        Region region = plugin.regions().get(plugin.spawn().regionName());
        if (region == null || Bukkit.getWorld(region.world()) == null) {
            return null;
        }
        Cuboid b = region.bounds();
        if (b.maxX() - b.minX() != plan.maxX() - plan.minX() || b.maxZ() - b.minZ() != plan.maxZ() - plan.minZ()) {
            return null;
        }
        return new Location(Bukkit.getWorld(region.world()), b.minX() - plan.minX(), b.minY() - plan.minY(), b.minZ() - plan.minZ());
    }

    /** Old arena origin: from the record, or derived from an arena with the same name and exactly the built size. */
    private Location oldArenaOrigin(String name, TournamentTier tier) {
        Location rec = LocationCodec.fromString(records.yaml().getString("arenas." + name + ".origin"));
        if (rec != null) {
            return rec;
        }
        Arena arena = plugin.arenas().get(name);
        if (arena == null || arena.bounds() == null || Bukkit.getWorld(arena.world()) == null) {
            return null;
        }
        Cuboid b = arena.bounds();
        ArenaStructure.Size size = ArenaStructure.size(tier);
        if (b.maxX() - b.minX() != size.outer() * 2) {
            return null;
        }
        return new Location(Bukkit.getWorld(arena.world()), b.minX() + size.outer(), b.minY() + 4, b.minZ() + size.outer());
    }

    /** Removes every block of {@code plan} at {@code origin} (air), then runs {@code then}. */
    private void erase(Player player, BlockPlan plan, Location origin, Runnable then) {
        if (origin == null || origin.getWorld() == null) {
            then.run();
            return;
        }
        BlockPlan air = new BlockPlan();
        for (int[] e : plan.entries()) {
            air.set(e[0], e[1], e[2], "air");
        }
        plugin.messages().send(player, "build.removing-old", "location", LocationCodec.describe(origin));
        BuildJob.run(plugin, air, origin.getWorld(), origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(), blocksPerTick,
                status -> message(player, status), then);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("build");
        blocksPerTick = cfg.integer("blocks-per-tick", 12_000, 500, 200_000);
        records.load(false);
        List<String> crates = cfg.strings("spawn-crates");
        if (!crates.isEmpty()) {
            spawnCrates = crates;
        }
    }

    /** First call shows the warning, the confirm call within 30 s builds. */
    public void requestSpawn(Player player, boolean confirm) {
        if (handleConfirm(player, confirm, "spawn", null, null)) {
            buildSpawn(player);
        }
    }

    public void requestArena(Player player, TournamentTier tier, String name, boolean confirm) {
        if (handleConfirm(player, confirm, "arena", tier, name)) {
            buildArena(player, tier, name);
        }
    }

    private boolean handleConfirm(Player player, boolean confirm, String what, TournamentTier tier, String name) {
        if (building) {
            plugin.messages().send(player, "build.busy");
            return false;
        }
        Pending p = pending.get(player.getUniqueId());
        if (confirm && p != null && p.what.equals(what) && p.expires > System.currentTimeMillis()) {
            pending.remove(player.getUniqueId());
            return true;
        }
        pending.put(player.getUniqueId(), new Pending(what, tier, name, System.currentTimeMillis() + 30_000));
        int radius = what.equals("spawn") ? SpawnStructure.RADIUS + 1 : ArenaStructure.size(tier).outer() + 1;
        plugin.messages().send(player, what.equals("spawn") ? "build.confirm-spawn" : "build.confirm-arena",
                "radius", radius, "tier", tier == null ? "" : tier.id(),
                "command", what.equals("spawn") ? "/spawn place confirm" : "/mythos arena place " + tier.id() + " " + name + " confirm");
        return false;
    }

    private void buildSpawn(Player player) {
        BlockPlan plan = SpawnStructure.generate();
        Location origin = player.getLocation().getBlock().getLocation().add(0, -1, 0);
        World world = origin.getWorld();
        building = true;
        int oldVersion = records.yaml().getInt("spawn.version", 1);
        BlockPlan oldPlan = oldVersion >= 3 ? plan
                : oldVersion == 2 ? SpawnStructureV2.generate() : SpawnStructureV1.generate();
        Location old = oldSpawnOrigin(oldPlan);
        // a new spawn: ALL crates and holograms go (there are none anywhere else) – the new spawn places them once again
        plugin.crates().removeAll();
        plugin.holograms().removeAll();
        for (World w : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntitiesByClasses(org.bukkit.entity.Display.class, org.bukkit.entity.Interaction.class,
                    org.bukkit.entity.Mannequin.class, org.bukkit.entity.ArmorStand.class)) {
                String tag = e.getPersistentDataContainer().get(online.mythoscraft.core.util.Keys.ENTITY,
                        org.bukkit.persistence.PersistentDataType.STRING);
                if (tag != null && (tag.startsWith("crate:") || tag.startsWith("hologram:"))) {
                    e.remove();
                    continue;
                }
                // every other hologram / text / NPC figure on the old or new spawn goes too (also from older versions or
                // other plugins) – the new spawn places everything it needs again, in English
                if (tag != null && (tag.equals(online.mythoscraft.core.tournament.TrophyService.TAG) || tag.startsWith("companion_farm:"))) {
                    continue;                       // placed trophies / totems and farm companions belong to players
                }
                boolean holo = !(e instanceof org.bukkit.entity.ArmorStand a) || a.isMarker() || a.isInvisible();
                if (holo && (near(e.getLocation(), origin) || (old != null && near(e.getLocation(), old)))) {
                    e.remove();
                }
            }
        }
        plugin.attractions().deco().removeAll();
        erase(player, oldPlan, old, () -> {
            plugin.messages().send(player, "build.started", "blocks", plan.size());
            BuildJob.run(plugin, plan, world, origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(), blocksPerTick,
                    status -> message(player, status), () -> {
                        building = false;
                        record("spawn", origin, "");
                        records.yaml().set("spawn.version", SpawnStructure.VERSION);
                        records.saveAsync(plugin.tasks());
                        finishSpawn(player, plan, origin);
                    });
        });
    }

    private void finishSpawn(Player player, BlockPlan plan, Location origin) {
        World w = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        BlockPlan.Marker sm = plan.markers("spawn").get(0);
        Location spawn = new Location(w, ox + sm.x(), oy + sm.y(), oz + sm.z(), sm.yaw(), 0);
        plugin.spawn().setSpawn(spawn);
        Cuboid bounds = Cuboid.of(ox + plan.minX(), oy + plan.minY(), oz + plan.minZ(), ox + plan.maxX(), oy + 80, oz + plan.maxZ());
        String regionName = plugin.spawn().regionName();
        Region region = plugin.regions().get(regionName);
        if (region == null) {
            plugin.regions().create(regionName, w.getName(), bounds, 10);
        } else {
            region.redefine(w.getName(), bounds);
            plugin.regions().rebuild();
            plugin.regions().save();
        }
        plugin.spawn().reload(plugin.rootConfig());
        List<BlockPlan.Marker> crateSpots = plan.markers("crate");
        List<String> order = new java.util.ArrayList<>(spawnCrates);
        for (CrateType t : plugin.crates().types()) {
            if (!order.contains(t.id())) {
                order.add(t.id());
            }
        }
        int placed = 0;
        for (int i = 0; i < crateSpots.size() && i < order.size(); i++) {
            CrateType type = plugin.crates().type(order.get(i));
            BlockPlan.Marker m = crateSpots.get(i);
            Location loc = new Location(w, ox + m.x(), oy + m.y(), oz + m.z());
            if (type != null && plugin.crates().nearest(loc.clone().add(0.5, 0.5, 0.5), 0.6) == null) {
                plugin.crates().place(type, loc, m.yaw());
                placed++;
            }
        }
        List<BlockPlan.Marker> buttonSpots = plan.markers("button");
        List<String[]> presets = ButtonManager.spawnPresets();
        for (int i = 0; i < buttonSpots.size() && i < presets.size(); i++) {
            BlockPlan.Marker m = buttonSpots.get(i);
            plugin.buttons().create("spawn_" + presets.get(i)[0], new Location(w, ox + m.x(), oy + m.y(), oz + m.z()),
                    presets.get(i)[1], null);
        }
        java.util.function.Function<BlockPlan.Marker, Location> at = m -> new Location(w, ox + Math.floor(m.x()), oy + Math.floor(m.y()),
                oz + Math.floor(m.z()));
        if (!plan.markers("parkour_start").isEmpty()) {
            List<Location> cps = new java.util.ArrayList<>();
            plan.markers("parkour_cp").forEach(m -> cps.add(at.apply(m)));
            plugin.parkour().define(at.apply(plan.markers("parkour_start").get(0)), cps, at.apply(plan.markers("parkour_finish").get(0)));
        }
        java.util.Map<String, List<Location>> points = new java.util.LinkedHashMap<>();
        for (String key : new String[]{"statue", "wheel", "well", "merchant", "merchant_wander", "ring", "elytra_start", "elytra_lift",
                "lamp", "deco", "season_tree", "dropper_start", "dropper_pool", "dropper_lift", "maze_goal", "jump_pad", "mentor", "starter_board", "npc_rtp", "npc_plot", "npc_jobs", "npc_crates", "npc_idle", "ground", "obelisk_accent", "arch_top",
                "crate", "crate_event", "cannon", "cannon_target", "party_center", "party_exit", "spleef_center", "spleef_exit", "range_start", "range_stand", "target",
                "rtp_portal", "wheel2", "relic_crate"}) {
            List<Location> list = new java.util.ArrayList<>();
            for (BlockPlan.Marker m : plan.markers(key)) {
                list.add(new Location(w, ox + m.x(), oy + m.y(), oz + m.z(), m.yaw(), 0));
            }
            points.put(key, list);
        }
        plugin.attractionStore().define(new Location(w, ox + 0.5, oy, oz + 0.5), SpawnStructure.RADIUS, points);
        plugin.attractions().deco().reset();
        plugin.attractions().rebuild();
        plugin.crateCarousel().arrange();
        plugin.ground().reset();
        plugin.creatures().despawnAll();
        Stat[] stats = {Stat.COINS, Stat.KILLS, Stat.EVENTS_WON};
        List<BlockPlan.Marker> holos = plan.markers("hologram");
        for (int i = 0; i < holos.size() && i < stats.length; i++) {
            BlockPlan.Marker m = holos.get(i);
            plugin.holograms().create("spawn_" + stats[i].id(), stats[i], new Location(w, ox + m.x(), oy + m.y(), oz + m.z()), 10);
        }
        if (player.isOnline()) {
            online.mythoscraft.core.cosmetic.CompanionService.unmount(player);
            player.teleport(spawn);
            plugin.messages().send(player, "build.spawn-done", "crates", placed, "location", LocationCodec.describe(spawn));
        }
        if (plugin.rootConfig().bool("build.auto-arenas", true)) {
            plugin.tasks().later(100, "auto arenas", () -> autoArenas(player, origin));
        }
        plugin.getLogger().info("Spawn structure built at " + LocationCodec.describe(origin) + " by " + player.getName());
    }

    /** Inside the spawn area around {@code center} (with a margin)? */
    private static boolean near(Location l, Location center) {
        return center != null && l.getWorld() != null && l.getWorld().equals(center.getWorld())
                && l.distanceSquared(center) <= Math.pow(SpawnStructure.RADIUS + 24, 2);
    }

    private void buildArena(Player player, TournamentTier tier, String requestedName) {
        buildArenaAt(player, tier, requestedName, player.getLocation().getBlock().getLocation().add(0, -1, 0), null);
    }

    /** Minimum distance of the automatically placed tournament arenas from the spawn. */
    public static final int ARENA_DISTANCE = 10_000;

    /**
     * After a spawn build: the three tournament arenas are built far away (at least 10,000 blocks from the spawn)
     * floating in the sky – their highest block sits exactly one block below the build limit.
     */
    private void autoArenas(Player player, Location spawnOrigin) {
        TournamentTier[] tiers = TournamentTier.values();
        java.util.function.IntConsumer[] next = new java.util.function.IntConsumer[1];
        next[0] = i -> {
            if (i >= tiers.length) {
                if (player.isOnline()) {
                    plugin.messages().send(player, "build.arenas-auto-done", "distance", ARENA_DISTANCE);
                }
                return;
            }
            TournamentTier tier = tiers[i];
            if (plugin.arenas().get(tier.id()) != null) {
                // the arena already stands far away – a new spawn does not rebuild it
                if (player.isOnline()) {
                    plugin.messages().send(player, "build.arena-kept", "tier", tier.id());
                }
                next[0].accept(i + 1);
                return;
            }
            World w = spawnOrigin.getWorld();
            int[] o = arenaOrigin(spawnOrigin.getBlockX(), spawnOrigin.getBlockZ(), w.getMaxHeight(), tier, i);
            Location origin = new Location(w, o[0], o[1], o[2]);
            if (player.isOnline()) {
                plugin.messages().send(player, "build.arena-auto", "tier", tier.id(), "location", LocationCodec.describe(origin));
            }
            buildArenaAt(player, tier, tier.id(), origin, () -> next[0].accept(i + 1));
        };
        next[0].accept(0);
    }

    /**
     * Where an automatic arena goes: {@link #ARENA_DISTANCE}+ blocks east of the spawn, so high that its highest block
     * is exactly one below the build limit ({@code maxHeight - 2}). Returns {x, y, z} of the origin.
     */
    static int[] arenaOrigin(int spawnX, int spawnZ, int maxHeight, TournamentTier tier, int index) {
        BlockPlan plan = ArenaStructure.generate(tier);
        return new int[]{spawnX + ARENA_DISTANCE + index * 800, maxHeight - 2 - plan.maxY(), spawnZ};
    }

    private void buildArenaAt(Player player, TournamentTier tier, String requestedName, Location origin, Runnable then) {
        String name = (requestedName == null || requestedName.isBlank() ? tier.id() : requestedName).toLowerCase(Locale.ROOT);
        BlockPlan plan = ArenaStructure.generate(tier);
        ArenaStructure.Size size = ArenaStructure.size(tier);
        World world = origin.getWorld();
        building = true;
        Location old = oldArenaOrigin(name, tier);
        String oldTierId = records.yaml().getString("arenas." + name + ".tier", tier.id());
        TournamentTier oldTier = TournamentTier.byId(oldTierId) == null ? tier : TournamentTier.byId(oldTierId);
        Arena existing = plugin.arenas().get(name);
        if (existing != null) {
            // same name: the old arena (region, spawns, snapshot) is replaced completely by the new one
            plugin.arenas().delete(existing);
        }
        erase(player, ArenaStructure.generate(oldTier), old, () -> {
            plugin.messages().send(player, "build.started", "blocks", plan.size());
            BuildJob.run(plugin, plan, world, origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(), blocksPerTick,
                    status -> message(player, status), () -> {
                        building = false;
                        record("arenas." + name, origin, tier.id());
                        finishArena(player, tier, name, plan, size, origin);
                        if (then != null) {
                            plugin.tasks().later(40, "next arena", then);
                        }
                    });
        });
    }

    private void finishArena(Player player, TournamentTier tier, String name, BlockPlan plan, ArenaStructure.Size size, Location origin) {
        World w = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        Cuboid bounds = Cuboid.of(ox - size.outer(), oy - 4, oz - size.outer(), ox + size.outer(), oy + size.wallHeight() + 26, oz + size.outer());
        Arena arena = plugin.arenas().get(name);
        if (arena == null) {
            arena = plugin.arenas().create(name, w.getName(), bounds);
        } else {
            arena.redefine(w.getName(), bounds);
        }
        arena.spawns().clear();
        for (BlockPlan.Marker m : plan.markers("spawn")) {
            arena.spawns().add(LocationCodec.toString(new Location(w, ox + m.x(), oy + m.y(), oz + m.z(), m.yaw(), 0)));
        }
        BlockPlan.Marker spec = plan.markers("spectator").get(0);
        arena.spectator(LocationCodec.toString(new Location(w, ox + spec.x(), oy + spec.y(), oz + spec.z(), spec.yaw(), 0)));
        arena.set("max-players", String.valueOf(size.slots()));
        arena.set("inventory", "separate");
        arena.set("keep-inventory", "true");
        arena.set("block-commands", "true");
        plugin.arenas().enable(arena);
        // season decoration spots of the arena (stands)
        String decoKey = "arena_deco_" + tier.id();
        List<Location> deco = new java.util.ArrayList<>();
        for (BlockPlan.Marker m : plan.markers("deco")) {
            deco.add(new Location(w, ox + m.x(), oy + m.y(), oz + m.z()));
        }
        plugin.attractionStore().put(decoKey, deco);
        plugin.arenas().publishRegions();
        plugin.arenas().save();
        plugin.tournaments().setArena(tier, arena.name());
        Arena built = arena;
        plugin.messages().send(player, "build.arena-snapshot", "arena", name);
        plugin.arenas().snapshots().capture(built, error -> {
            if (player.isOnline()) {
                plugin.messages().send(player, error == null ? "build.arena-done" : "build.arena-done-no-snapshot",
                        "arena", built.name(), "tier", tier.id(), "spawns", built.spawns().size(), "error", error == null ? "" : error);
            }
        });
        plugin.getLogger().info("Tournament arena " + name + " (" + tier.id() + ") built at " + LocationCodec.describe(origin));
    }

    private void message(Player player, String status) {
        if (player.isOnline()) {
            plugin.messages().actionBar(player, "build.progress", "status", status);
        }
    }
}
