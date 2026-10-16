package online.mythoscraft.core.cosmetic;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Farming companions: sneak + right-click the ground with a companion item → it settles there as a little farm
 * station and walks around its spot while it works. Right-click it: a menu to set how long (5 min … 4 h), how far and
 * up to how many stacks it farms, start / stop, gem upgrades (speed, power, range, luck), collect the loot and pick it
 * up again (upgrades stay on the item). Every action has a tiny chance to find an OP item. Every companion has its own job and shows the tool it uses:
 * bee → crops (harvest + replant), penguin → fishing, snowman → trees (fell + replant), ghost → sugar cane, cactus,
 * bamboo, melons and pumpkins, dragon → ores (replaced by stone), shadow cube → hostile mobs.
 * Stations are saved in data/companion_farms.yml and only work while their chunk is loaded.
 */
public final class CompanionFarmService implements Listener {

    enum Job {
        CROPS("companion_bee", Material.IRON_HOE),
        FISH("companion_penguin", Material.FISHING_ROD),
        TREES("companion_snowman", Material.IRON_AXE),
        PLANTS("companion_ghost", Material.SHEARS),
        ORES("companion_dragon", Material.IRON_PICKAXE),
        MOBS("companion_shadow", Material.IRON_SWORD);

        final String item;
        final Material tool;

        Job(String item, Material tool) {
            this.item = item;
            this.tool = tool;
        }

        static Job of(String item) {
            for (Job j : values()) {
                if (j.item.equals(item)) {
                    return j;
                }
            }
            return null;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static final class Station {
        final UUID id;
        final UUID owner;
        final String ownerName;
        final Job job;
        final Location at;
        int radius = 8;
        int minutes = 30;
        /** stop farming at this many loot stacks */
        int limit = MAX_STACKS;
        /** upgrade levels, index = Upgrade.ordinal() */
        final int[] levels = new int[Upgrade.values().length];
        /** where the companion currently stands / walks to (home = at) */
        Location pos;
        Location goal;
        int idleUntil;
        boolean moving;
        /** last time the station worked (ms) – the gap is made up as offline progress */
        long lastSeen;
        int offlineGot;
        /** end of the running work time, 0 = stopped */
        long until;
        final List<ItemStack> loot = new ArrayList<>();
        int xp;
        ItemDisplay body;
        ItemDisplay tool;
        TextDisplay label;
        Interaction hitbox;
        int tick;

        Station(UUID id, UUID owner, String ownerName, Job job, Location at) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.job = job;
            this.at = at;
        }

        boolean running() {
            return until > System.currentTimeMillis();
        }

        int level(Upgrade u) {
            return levels[u.ordinal()];
        }

        /** a copy of where the companion stands right now */
        Location pos() {
            return (pos == null ? at : pos).clone();
        }

        int stacks() {
            return loot.size();
        }
    }

    enum Upgrade {
        SPEED(Material.SUGAR), POWER(Material.BLAZE_POWDER), RANGE(Material.ENDER_EYE), LUCK(Material.RABBIT_FOOT);

        final Material icon;

        Upgrade(Material icon) {
            this.icon = icon;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    record OpFind(String item, double weight) {
    }

    private static final int MAX_STACKS = 54;
    private static final int MIN_MINUTES = 5;
    private static final int MAX_MINUTES = 720;      // up to 12 h – also overnight while offline
    private static final int MIN_RADIUS = 2;
    /** walking speed in blocks per movement step (every 2 ticks) */
    private static final double STEP = 0.2;

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Map<UUID, Station> stations = new LinkedHashMap<>();
    private int maxPerPlayer = 2;
    private boolean enabled = true;
    private int baseRadius = 8;
    private int radiusPerLevel = 3;
    private int maxLevel = 5;
    private final Map<Upgrade, Integer> upgradeCost = new java.util.EnumMap<>(Upgrade.class);
    private double opChance = 0.00005;
    private double opChancePerLevel = 0.00004;
    private final List<OpFind> opFinds = new ArrayList<>();
    private boolean dirty;
    /** stations in worlds that are not loaded (yet) – kept unchanged in the file */
    private final Map<String, Map<String, Object>> orphans = new LinkedHashMap<>();

    public CompanionFarmService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/companion_farms.yml");
        load();
        plugin.tasks().repeat(this, 40, 10, "companion farms", this::tick);
        plugin.tasks().repeat(this, 41, 2, "companion farms walk", this::walk);
        plugin.tasks().repeat(this, 20 * 60, 20 * 60, "companion farms save", () -> {
            if (dirty) {
                save();
                store.saveAsync(plugin.tasks());
            }
        });
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("companion-farm");
        enabled = cfg.bool("enabled", true);
        maxPerPlayer = cfg.integer("max-per-player", 2, 0, 20);
        baseRadius = cfg.integer("base-radius", 8, 2, 32);
        radiusPerLevel = cfg.integer("radius-per-level", 3, 0, 16);
        maxLevel = cfg.integer("max-upgrade-level", 5, 0, 20);
        ConfigReader cost = cfg.sub("upgrade-cost");
        upgradeCost.put(Upgrade.SPEED, cost.integer("speed", 15, 0, 1_000_000));
        upgradeCost.put(Upgrade.POWER, cost.integer("power", 15, 0, 1_000_000));
        upgradeCost.put(Upgrade.RANGE, cost.integer("range", 10, 0, 1_000_000));
        upgradeCost.put(Upgrade.LUCK, cost.integer("luck", 25, 0, 1_000_000));
        opChance = cfg.decimal("op-find.chance", 0.00005, 0, 1);
        opChancePerLevel = cfg.decimal("op-find.chance-per-luck-level", 0.00004, 0, 1);
        opFinds.clear();
        for (String line : cfg.raw().getStringList("op-find.items")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0 || CustomItem.byId(parts[0]) == null) {
                cfg.warn("op-find.items", "unbekanntes Item: " + line);
                continue;
            }
            double w = 1;
            try {
                w = parts.length > 1 ? Double.parseDouble(parts[1]) : 1;
            } catch (NumberFormatException ignored) {
                cfg.warn("op-find.items", "ungültiges Gewicht: " + line);
            }
            opFinds.add(new OpFind(parts[0], Math.max(0, w)));
        }
        for (Station s : stations.values()) {
            s.radius = Math.min(s.radius, maxRadius(s));
        }
    }

    int maxRadius(Station s) {
        return baseRadius + radiusPerLevel * s.level(Upgrade.RANGE);
    }

    long upgradeCost(Station s, Upgrade u) {
        return (long) upgradeCost.getOrDefault(u, 15) * (s.level(u) + 1);
    }

    // ------------------------------------------------------------------------------------------- placing
    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.getPlayer().isSneaking()
                || event.getClickedBlock() == null) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        CustomItem type = plugin.items().identify(hand);
        Job job = type == null ? null : Job.of(type.id());
        if (job == null) {
            return;
        }
        event.setCancelled(true);      // also stops the shoulder toggle (CompanionService runs later)
        if (!enabled) {
            return;
        }
        Block ground = event.getClickedBlock();
        Block spot = event.getBlockFace() == BlockFace.UP ? ground.getRelative(BlockFace.UP) : null;
        if (spot == null || !spot.isPassable() || !ground.getType().isSolid()) {
            plugin.messages().actionBar(p, "companion-farm.place-on-ground");
            return;
        }
        if (plugin.attractionStore().inside(spot.getLocation(), 4) || !plugin.protection().allows(p, spot.getLocation(), Flag.BUILD)) {
            plugin.messages().actionBar(p, "companion-farm.not-here");
            return;
        }
        long own = stations.values().stream().filter(s -> s.owner.equals(p.getUniqueId())).count();
        if (own >= maxPerPlayer) {
            plugin.messages().send(p, "companion-farm.limit", "max", maxPerPlayer);
            return;
        }
        if (type.id().equals(plugin.companions().active(p))) {
            plugin.companions().set(p, null);
        }
        String saved = hand.getItemMeta() == null ? null
                : hand.getItemMeta().getPersistentDataContainer().get(UPGRADES, PersistentDataType.STRING);
        hand.setAmount(hand.getAmount() - 1);
        Location at = spot.getLocation().add(0.5, 0, 0.5);
        at.setYaw(p.getLocation().getYaw() + 180);
        Station s = new Station(UUID.randomUUID(), p.getUniqueId(), p.getName(), job, at);
        readLevels(s, saved);
        s.radius = Math.min(s.radius, maxRadius(s));
        stations.put(s.id, s);
        spawn(s);
        dirty = true;
        SoundSpec.of("entity.allay.item_given", 1f, 1.2f).playAt(at);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.6, 0), 20, 0.4, 0.4, 0.4, 0);
        plugin.messages().send(p, "companion-farm.placed", "job", plugin.messages().get("companion-farm.jobs." + job.key()));
        plugin.tasks().later(2, "companion farm menu", () -> new FarmMenu(s, p).open(p));
    }

    private static final org.bukkit.NamespacedKey UPGRADES = Keys.key("farm_upgrades");

    private static void readLevels(Station s, String saved) {
        if (saved == null) {
            return;
        }
        String[] parts = saved.split(",");
        for (int i = 0; i < Math.min(parts.length, s.levels.length); i++) {
            try {
                s.levels[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
            } catch (NumberFormatException ignored) {
                // keep 0
            }
        }
    }

    private static String writeLevels(Station s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.levels.length; i++) {
            b.append(i == 0 ? "" : ",").append(s.levels[i]);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------- visuals
    private void spawn(Station s) {
        despawn(s);
        World w = s.at.getWorld();
        if (w == null || !s.at.isChunkLoaded()) {
            return;
        }
        s.pos = s.at.clone();
        s.goal = null;
        String tag = "companion_farm:" + s.id;
        s.body = w.spawn(s.at.clone(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new ItemBuilder(Material.PAPER).model(s.job.item).build());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0, 0.42f, 0), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
            d.setTeleportDuration(3);
            d.setRotation(s.at.getYaw(), 0);
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        s.tool = w.spawn(s.at.clone().add(0, 0.5, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new ItemStack(s.job.tool));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND);
            d.setTransformation(new Transformation(new Vector3f(0.42f, 0.0f, 0), new AxisAngle4f(), new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
            d.setInterpolationDuration(4);
            d.setTeleportDuration(2);
            d.setRotation(s.at.getYaw(), 0);
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        s.label = w.spawn(s.at.clone().add(0, 1.35, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(110, 20, 10, 30));
            d.setTeleportDuration(2);
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        s.hitbox = w.spawn(s.at.clone(), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(0.9f);
            i.setInteractionHeight(1.0f);
            i.setResponsive(true);
            i.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        label(s);
    }

    private static void despawn(Station s) {
        for (Entity e : new Entity[]{s.body, s.tool, s.label, s.hitbox}) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        s.body = null;
        s.tool = null;
        s.label = null;
        s.hitbox = null;
    }

    private void label(Station s) {
        if (s.label == null || !s.label.isValid()) {
            return;
        }
        Component job = plugin.messages().get("companion-farm.jobs." + s.job.key());
        if (s.running()) {
            long left = (s.until - System.currentTimeMillis()) / 1000;
            s.label.text(plugin.messages().get("companion-farm.label-running", "owner", s.ownerName, "job", job,
                    "time", String.format("%d:%02d", left / 60, left % 60), "stacks", s.stacks(), "max", s.limit, "radius", s.radius));
        } else {
            s.label.text(plugin.messages().get("companion-farm.label-idle", "owner", s.ownerName, "job", job, "stacks", s.stacks(),
                    "max", s.limit));
        }
    }

    // ------------------------------------------------------------------------------------------- working
    private void tick() {
        for (Station s : List.copyOf(stations.values())) {
            if (s.at.getWorld() == null || !s.at.isChunkLoaded()) {
                s.body = null;                      // entities went with the chunk
                continue;
            }
            if (s.body == null || !s.body.isValid()) {
                spawn(s);
            }
            s.tick++;
            if (s.tick % 2 == 0) {
                label(s);
            }
            catchUp(s, false);
            boolean running = s.running();
            animate(s, running);
            if (!running) {
                if (s.until != 0) {
                    s.until = 0;
                    dirty = true;
                    Player owner = Bukkit.getPlayer(s.owner);
                    if (owner != null) {
                        plugin.messages().send(owner, "companion-farm.finished", "job", plugin.messages().get("companion-farm.jobs." + s.job.key()),
                                "stacks", s.stacks());
                    }
                }
                continue;
            }
            if (s.stacks() >= s.limit) {
                s.goal = s.at.clone();
                if (s.tick % 20 == 0) {
                    s.at.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, s.pos().add(0, 1, 0), 1, 0.2, 0.2, 0.2, 0);
                }
                continue;
            }
            int base = baseInterval(s.job);
            int every = Math.max(2, (int) Math.round(base * (1 - 0.13 * Math.min(5, s.level(Upgrade.SPEED)))));
            if (s.tick % every == 0) {
                work(s, base);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- offline progress
    /** items per hour while nobody is near (chunk unloaded, server off) – before upgrades */
    private static int offlinePerHour(Job job) {
        return switch (job) {
            case CROPS -> 220;
            case FISH -> 50;
            case TREES -> 70;
            case PLANTS -> 180;
            case ORES -> 24;
            case MOBS -> 60;
        };
    }

    private static List<ItemStack> offlineDrop(Job job, ThreadLocalRandom r) {
        return switch (job) {
            case CROPS -> List.of(new ItemStack(switch (r.nextInt(4)) {
                case 0 -> Material.WHEAT;
                case 1 -> Material.CARROT;
                case 2 -> Material.POTATO;
                default -> Material.BEETROOT;
            }));
            case FISH -> List.of(new ItemStack(r.nextInt(10) < 6 ? Material.COD : r.nextInt(10) < 8 ? Material.SALMON : Material.PUFFERFISH));
            case TREES -> r.nextInt(12) == 0 ? List.of(new ItemStack(Material.OAK_SAPLING)) : r.nextInt(20) == 0
                    ? List.of(new ItemStack(Material.APPLE)) : List.of(new ItemStack(Material.OAK_LOG));
            case PLANTS -> List.of(new ItemStack(switch (r.nextInt(5)) {
                case 0 -> Material.SUGAR_CANE;
                case 1 -> Material.CACTUS;
                case 2 -> Material.BAMBOO;
                case 3 -> Material.MELON_SLICE;
                default -> Material.PUMPKIN;
            }));
            case ORES -> List.of(new ItemStack(switch (r.nextInt(20)) {
                case 0, 1, 2, 3, 4, 5, 6, 7 -> Material.COAL;
                case 8, 9, 10, 11, 12 -> Material.RAW_IRON;
                case 13, 14, 15 -> Material.RAW_COPPER;
                case 16, 17 -> Material.RAW_GOLD;
                case 18 -> Material.REDSTONE;
                default -> Material.DIAMOND;
            }));
            case MOBS -> List.of(new ItemStack(switch (r.nextInt(5)) {
                case 0 -> Material.ROTTEN_FLESH;
                case 1 -> Material.BONE;
                case 2 -> Material.STRING;
                case 3 -> Material.GUNPOWDER;
                default -> Material.ARROW;
            }));
        };
    }

    /**
     * Makes up for the time the station could not work (chunk unloaded or server off) while it was switched on:
     * a calmer, simulated production with the same upgrades. Called while ticking and when the owner joins.
     */
    private void catchUp(Station s, boolean notify) {
        long now = System.currentTimeMillis();
        long last = s.lastSeen;
        s.lastSeen = now;
        if (last <= 0 || s.until <= last) {
            return;
        }
        long gap = Math.min(now, s.until) - last;
        if (gap < 15_000) {
            return;
        }
        double perHour = offlinePerHour(s.job) * (1 + 0.15 * s.level(Upgrade.SPEED)) * (1 + 0.1 * s.level(Upgrade.POWER));
        int items = (int) Math.min(64 * MAX_STACKS, perHour * gap / 3_600_000.0);
        if (items <= 0) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int before = totalItems(s);
        for (int i = 0; i < items && s.stacks() <= s.limit; i++) {
            List<ItemStack> drop = offlineDrop(s.job, r);
            if (s.stacks() >= s.limit && s.loot.stream().noneMatch(h -> h.isSimilar(drop.get(0)) && h.getAmount() < h.getMaxStackSize())) {
                break;
            }
            addLoot(s, drop);
        }
        int got = totalItems(s) - before;
        if (s.job == Job.FISH || s.job == Job.MOBS || s.job == Job.ORES) {
            s.xp += got / 2;
        }
        // a few OP-find chances for the simulated work, too
        if (!opFinds.isEmpty() && got > 0) {
            double chance = (opChance + opChancePerLevel * s.level(Upgrade.LUCK)) * Math.min(400, got / 2.0);
            if (r.nextDouble() < chance) {
                opFind(s);
            }
        }
        if (got > 0) {
            dirty = true;
            s.offlineGot += got;
            Player owner = Bukkit.getPlayer(s.owner);
            if (owner != null && (notify || owner.getWorld().equals(s.at.getWorld()))) {
                plugin.messages().send(owner, "companion-farm.offline", "job", plugin.messages().get("companion-farm.jobs." + s.job.key()),
                        "items", s.offlineGot, "time", online.mythoscraft.core.util.Durations.format(java.time.Duration.ofMillis(gap)));
                s.offlineGot = 0;
            }
        }
    }

    /** Owner joins: report what the companions did while he was away. */
    @EventHandler
    public void onOwnerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(120, "companion offline", () -> {
            if (!p.isOnline()) {
                return;
            }
            for (Station s : List.copyOf(stations.values())) {
                if (s.owner.equals(p.getUniqueId())) {
                    catchUp(s, true);
                    if (s.offlineGot > 0) {
                        plugin.messages().send(p, "companion-farm.offline", "job", plugin.messages().get("companion-farm.jobs." + s.job.key()),
                                "items", s.offlineGot, "time", "-");
                        s.offlineGot = 0;
                    }
                }
            }
        });
    }

    private static int baseInterval(Job job) {
        return switch (job) {
            case FISH -> 24;          // a catch every ~12 s
            case TREES, ORES -> 8;    // one tree / ore every ~4 s
            default -> 4;             // crops, plants and mobs every 2 s
        };
    }

    // ------------------------------------------------------------------------------------------- walking
    /** Every 2 ticks: the companion walks (hops) towards its goal – the spot it works on, a wander point or home. */
    private void walk() {
        for (Station s : stations.values()) {
            if (s.body == null || !s.body.isValid() || s.pos == null) {
                continue;
            }
            if (s.goal == null) {
                if (!s.running()) {
                    if (s.pos.distanceSquared(s.at) > 0.04) {
                        s.goal = s.at.clone();
                    }
                } else if (s.tick >= s.idleUntil) {
                    // nothing to do right now: stroll around the home spot
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    int range = Math.max(2, Math.min(s.radius, 5));
                    s.goal = s.at.clone().add(r.nextInt(-range, range + 1), 0, r.nextInt(-range, range + 1));
                    s.idleUntil = s.tick + 6 + r.nextInt(8);
                }
            }
            if (s.goal == null) {
                s.moving = false;
                continue;
            }
            double dx = s.goal.getX() - s.pos.getX();
            double dz = s.goal.getZ() - s.pos.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.9 || (s.goal.equals(s.at) && dist < 0.2)) {
                if (s.goal.getX() == s.at.getX() && s.goal.getZ() == s.at.getZ() && dist > 0.01) {
                    s.pos = s.at.clone();
                    place(s, s.at.getYaw(), 0);
                }
                s.goal = null;
                s.moving = false;
                continue;
            }
            double step = STEP * (1 + 0.1 * Math.min(5, s.level(Upgrade.SPEED)));
            double nx = s.pos.getX() + dx / dist * Math.min(step, dist);
            double nz = s.pos.getZ() + dz / dist * Math.min(step, dist);
            Double y = groundY(s.pos.getWorld(), nx, s.pos.getY(), nz);
            if (y == null) {
                s.goal = null;              // wall, cliff or unloaded chunk: pick something else
                s.idleUntil = s.tick + 2;
                s.moving = false;
                continue;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            s.pos = new Location(s.pos.getWorld(), nx, y, nz, yaw, 0);
            s.moving = true;
            int walked = (int) (System.currentTimeMillis() / 100);
            double hop = Math.abs(Math.sin(walked * 0.9)) * 0.18;
            place(s, yaw, hop);
        }
    }

    /** Height to stand on at x/z: steps up one block or down up to three; null = can't go there. */
    private static Double groundY(World w, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
            return null;
        }
        int feet = (int) Math.floor(y + 0.01);
        for (int fy = feet + 1; fy >= feet - 3; fy--) {
            Block below = w.getBlockAt(bx, fy - 1, bz);
            Block at = w.getBlockAt(bx, fy, bz);
            Block head = w.getBlockAt(bx, fy + 1, bz);
            boolean standable = below.getType().isSolid() || below.isLiquid();
            if (standable && at.isPassable() && !at.isLiquid() && head.isPassable()) {
                return (double) fy;
            }
        }
        return null;
    }

    private void place(Station s, float yaw, double hop) {
        Location body = s.pos.clone().add(0, hop, 0);
        body.setYaw(yaw);
        body.setPitch(0);
        s.body.teleport(body);
        if (s.tool != null && s.tool.isValid()) {
            s.tool.teleport(body.clone().add(0, 0.5, 0));
        }
        if (s.label != null && s.label.isValid()) {
            s.label.teleport(s.pos.clone().add(0, 1.35, 0));
        }
        if (s.hitbox != null && s.hitbox.isValid()) {
            s.hitbox.teleport(s.pos.clone());
        }
    }

    private void animate(Station s, boolean running) {
        if (s.body == null || !s.body.isValid()) {
            return;
        }
        float bob = running && !s.moving ? (float) Math.sin(s.tick * 0.6) * 0.06f : 0f;
        s.body.setInterpolationDelay(0);
        s.body.setInterpolationDuration(10);
        Quaternionf turn = new Quaternionf().rotationY(running && !s.moving ? (float) Math.sin(s.tick * 0.15) * 0.5f : 0f);
        s.body.setTransformation(new Transformation(new Vector3f(0, 0.42f + bob, 0), turn, new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
        if (s.tool != null && s.tool.isValid()) {
            float swing = running ? (float) Math.sin(s.tick * 1.3) * 0.7f : 0f;
            s.tool.setInterpolationDelay(0);
            s.tool.setInterpolationDuration(10);
            s.tool.setTransformation(new Transformation(new Vector3f(0.42f, 0.05f + bob, 0), new Quaternionf().rotationX(swing),
                    new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
        }
        if (running && s.tick % 6 == 0) {
            s.at.getWorld().spawnParticle(Particle.WAX_ON, s.pos().add(0, 0.9, 0), 2, 0.25, 0.2, 0.25, 0);
        }
        if (s.moving && s.tick % 2 == 0) {
            s.at.getWorld().spawnParticle(Particle.CLOUD, s.pos().add(0, 0.1, 0), 1, 0.1, 0, 0.1, 0.01);
        }
    }

    private void work(Station s, int baseInterval) {
        int before = totalItems(s);
        switch (s.job) {
            case CROPS -> crops(s);
            case FISH -> fish(s);
            case TREES -> trees(s);
            case PLANTS -> plants(s);
            case ORES -> ores(s);
            case MOBS -> mobs(s);
        }
        dirty = true;
        // found something (mobs: they drop when they die) → tiny chance for an OP item, same odds per minute for every job
        if ((totalItems(s) > before || s.job == Job.MOBS) && !opFinds.isEmpty()) {
            double chance = (opChance + opChancePerLevel * s.level(Upgrade.LUCK)) * baseInterval / 4.0;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                opFind(s);
            }
        }
    }

    private static int totalItems(Station s) {
        int n = 0;
        for (ItemStack it : s.loot) {
            n += it.getAmount();
        }
        return n;
    }

    private void opFind(Station s) {
        double total = opFinds.stream().mapToDouble(OpFind::weight).sum();
        if (total <= 0) {
            return;
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        OpFind pick = opFinds.get(opFinds.size() - 1);
        for (OpFind f : opFinds) {
            r -= f.weight();
            if (r < 0) {
                pick = f;
                break;
            }
        }
        CustomItem type = CustomItem.byId(pick.item());
        if (type == null) {
            return;
        }
        ItemStack item = plugin.items().create(type);
        s.loot.add(item);                 // always kept, even when the storage is full
        Location l = s.pos().add(0, 1, 0);
        l.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l, 60, 0.4, 0.6, 0.4, 0.4);
        l.getWorld().strikeLightningEffect(l);
        SoundSpec.of("ui.toast.challenge_complete", 1f, 1.2f).playAt(l);
        Player owner = Bukkit.getPlayer(s.owner);
        if (owner != null) {
            plugin.messages().send(owner, "companion-farm.op-find", "job", plugin.messages().get("companion-farm.jobs." + s.job.key()),
                    "item", item.effectiveName());
        }
        plugin.messages().broadcast("companion-farm.op-find-broadcast", "player", s.ownerName, "item", item.effectiveName());
    }

    private boolean allowed(Block b) {
        return plugin.protection().query(b, Flag.BREAK) && !plugin.attractionStore().inside(b.getLocation(), 2);
    }

    private void addLoot(Station s, java.util.Collection<ItemStack> drops) {
        double bonus = 0.08 * s.level(Upgrade.POWER);
        for (ItemStack d : drops) {
            if (d == null || d.isEmpty()) {
                continue;
            }
            ItemStack rest = d.clone();
            if (bonus > 0 && ThreadLocalRandom.current().nextDouble() < bonus) {
                rest.setAmount(Math.min(rest.getMaxStackSize(), rest.getAmount() * 2));     // power upgrade: double drops
            }
            for (ItemStack have : s.loot) {
                if (rest.getAmount() <= 0) {
                    break;
                }
                if (have.isSimilar(rest) && have.getAmount() < have.getMaxStackSize()) {
                    int take = Math.min(rest.getAmount(), have.getMaxStackSize() - have.getAmount());
                    have.setAmount(have.getAmount() + take);
                    rest.setAmount(rest.getAmount() - take);
                }
            }
            if (rest.getAmount() > 0 && s.loot.size() < MAX_STACKS) {
                s.loot.add(rest);
            }
        }
    }

    /** Line of light from the companion to the block it works on – and the companion runs over there. */
    private static void beam(Station s, Location to) {
        s.goal = to.clone();
        s.idleUntil = s.tick + 6;
        Location from = s.pos().add(0, 0.7, 0);
        org.bukkit.util.Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.3) {
            return;
        }
        d.multiply(1 / len);
        for (double t = 0.4; t < len; t += 0.5) {
            from.getWorld().spawnParticle(Particle.END_ROD, from.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0);
        }
    }

    private List<Block> around(Station s, int dy, java.util.function.Predicate<Block> test, int max) {
        List<Block> out = new ArrayList<>();
        Block c = s.at.getBlock();
        int r = s.radius;
        for (int x = -r; x <= r && out.size() < max; x++) {
            for (int z = -r; z <= r && out.size() < max; z++) {
                for (int y = -dy; y <= dy && out.size() < max; y++) {
                    Block b = c.getRelative(x, y, z);
                    if (test.test(b)) {
                        out.add(b);
                    }
                }
            }
        }
        return out;
    }

    private void crops(Station s) {
        ItemStack hoe = new ItemStack(Material.IRON_HOE);
        for (Block b : around(s, 3, b -> b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()
                && !b.getType().name().endsWith("_STEM") && (Tag.CROPS.isTagged(b.getType()) || b.getType() == Material.NETHER_WART || b.getType() == Material.COCOA
                || b.getType() == Material.SWEET_BERRY_BUSH), 6 + 3 * s.level(Upgrade.POWER))) {
            if (!allowed(b)) {
                continue;
            }
            List<ItemStack> drops = new ArrayList<>(b.getDrops(hoe));
            if (b.getType() == Material.SWEET_BERRY_BUSH) {
                drops = List.of(new ItemStack(Material.SWEET_BERRIES, 2 + ThreadLocalRandom.current().nextInt(2)));
            } else {
                // keep one seed back for replanting
                for (ItemStack d : drops) {
                    if (d.getType() == seedOf(b.getType()) && d.getAmount() > 0) {
                        d.setAmount(d.getAmount() - 1);
                        break;
                    }
                }
            }
            Ageable age = (Ageable) b.getBlockData();
            age.setAge(b.getType() == Material.SWEET_BERRY_BUSH ? 1 : 0);
            b.setBlockData(age);
            addLoot(s, drops);
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, 0);
            beam(s, b.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    private static Material seedOf(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER_SEEDS;
            case PITCHER_CROP -> Material.PITCHER_POD;
            default -> Material.AIR;
        };
    }

    private void fish(Station s) {
        List<Block> water = around(s, 2, b -> b.getType() == Material.WATER, 1);
        if (water.isEmpty()) {
            if (s.tick % 40 == 0) {
                Player owner = Bukkit.getPlayer(s.owner);
                if (owner != null && owner.getWorld().equals(s.at.getWorld()) && owner.getLocation().distanceSquared(s.at) < 900) {
                    plugin.messages().actionBar(owner, "companion-farm.no-water");
                }
            }
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(1000);
        ItemStack catchItem;
        if (roll < 580) {
            catchItem = new ItemStack(Material.COD);
        } else if (roll < 830) {
            catchItem = new ItemStack(Material.SALMON);
        } else if (roll < 850) {
            catchItem = new ItemStack(Material.TROPICAL_FISH);
        } else if (roll < 960) {
            catchItem = new ItemStack(Material.PUFFERFISH);
        } else if (roll < 975) {
            catchItem = new ItemStack(Material.NAUTILUS_SHELL);
        } else if (roll < 985) {
            catchItem = new ItemStack(Material.NAME_TAG);
        } else if (roll < 993) {
            catchItem = new ItemStack(Material.SADDLE);
        } else {
            catchItem = new ItemStack(Material.LILY_PAD, 2);
        }
        addLoot(s, List.of(catchItem));
        if (r.nextDouble() < 0.15 * s.level(Upgrade.POWER)) {
            addLoot(s, List.of(new ItemStack(r.nextBoolean() ? Material.COD : Material.SALMON)));
        }
        s.xp += 1 + r.nextInt(3);
        Location w = water.get(0).getLocation().add(0.5, 1, 0.5);
        w.getWorld().spawnParticle(Particle.SPLASH, w, 15, 0.3, 0.1, 0.3, 0.1);
        SoundSpec.of("entity.fishing_bobber.splash", 0.5f, 1.2f).playAt(w);
        beam(s, w);
    }

    private void trees(Station s) {
        List<Block> bases = around(s, 2, b -> Tag.LOGS.isTagged(b.getType()) && !b.getType().name().startsWith("STRIPPED_")
                && isSoil(b.getRelative(BlockFace.DOWN).getType()), 1);
        if (bases.isEmpty()) {
            return;
        }
        Block base = bases.get(0);
        if (!allowed(base)) {
            return;
        }
        Material sapling = sapling(base.getType());
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ArrayDeque<Block> todo = new ArrayDeque<>(List.of(base));
        Set<Block> seen = new HashSet<>(todo);
        int n = 0;
        while (!todo.isEmpty() && n < 160) {
            Block b = todo.poll();
            boolean log = Tag.LOGS.isTagged(b.getType());
            boolean leaf = Tag.LEAVES.isTagged(b.getType()) && !(b.getBlockData() instanceof org.bukkit.block.data.type.Leaves l && l.isPersistent());
            if (!log && !leaf) {
                continue;
            }
            if (!allowed(b)) {
                continue;
            }
            addLoot(s, b.getDrops(axe));
            b.setType(Material.AIR, false);
            n++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block nb = b.getRelative(dx, dy, dz);
                        if (nb.getY() >= base.getY() && seen.add(nb)) {
                            todo.add(nb);
                        }
                    }
                }
            }
        }
        if (sapling != null) {
            base.setType(sapling);
        }
        base.getWorld().spawnParticle(Particle.BLOCK, base.getLocation().add(0.5, 1, 0.5), 30, 0.5, 1.5, 0.5, 0, Material.OAK_LOG.createBlockData());
        SoundSpec.of("block.wood.break", 1f, 0.8f).playAt(base.getLocation());
        beam(s, base.getLocation().add(0.5, 0.5, 0.5));
    }

    private static boolean isSoil(Material m) {
        return m == Material.DIRT || m == Material.GRASS_BLOCK || m == Material.PODZOL || m == Material.COARSE_DIRT
                || m == Material.ROOTED_DIRT || m == Material.MUD || m == Material.MOSS_BLOCK;
    }

    private static Material sapling(Material log) {
        String n = log.name();
        for (String wood : new String[]{"PALE_OAK", "DARK_OAK", "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "CHERRY"}) {
            if (n.startsWith(wood + "_")) {
                return Material.matchMaterial(wood + "_SAPLING");
            }
        }
        return n.startsWith("MANGROVE") ? Material.MANGROVE_PROPAGULE : null;
    }

    private void plants(Station s) {
        ItemStack shears = new ItemStack(Material.SHEARS);
        for (Block b : around(s, 3, b -> {
            Material m = b.getType();
            if (m == Material.MELON || m == Material.PUMPKIN) {
                return true;
            }
            return (m == Material.SUGAR_CANE || m == Material.CACTUS || m == Material.BAMBOO || m == Material.KELP_PLANT || m == Material.KELP)
                    && sameBelow(b);
        }, 8 + 3 * s.level(Upgrade.POWER))) {
            if (!allowed(b)) {
                continue;
            }
            // tall plants: everything from this block upwards, the bottom one stays and grows again
            Block top = b;
            while (top.getRelative(BlockFace.UP).getType() == b.getType()
                    || (b.getType() == Material.KELP_PLANT && top.getRelative(BlockFace.UP).getType() == Material.KELP)) {
                top = top.getRelative(BlockFace.UP);
            }
            for (Block x = top; x.getY() >= b.getY(); x = x.getRelative(BlockFace.DOWN)) {
                addLoot(s, x.getDrops(shears));
                x.setType(x.getType() == Material.KELP_PLANT || x.getType() == Material.KELP ? Material.WATER : Material.AIR, false);
            }
            b.getWorld().spawnParticle(Particle.COMPOSTER, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.4, 0.3, 0);
            beam(s, b.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    /** a tall plant block that stands on the same plant (the lowest one is left to regrow) */
    private static boolean sameBelow(Block b) {
        Material below = b.getRelative(BlockFace.DOWN).getType();
        return below == b.getType() || (b.getType() == Material.KELP && below == Material.KELP_PLANT)
                || (b.getType() == Material.KELP_PLANT && below == Material.KELP_PLANT);
    }

    private void ores(Station s) {
        List<Block> ores = around(s, Math.min(s.radius, 8), b -> {
            String n = b.getType().name();
            return n.endsWith("_ORE") || b.getType() == Material.ANCIENT_DEBRIS;
        }, 1);
        if (ores.isEmpty()) {
            return;
        }
        Block b = ores.get(0);
        if (!allowed(b)) {
            return;
        }
        addLoot(s, b.getDrops(new ItemStack(Material.DIAMOND_PICKAXE)));
        s.xp += 2;
        String n = b.getType().name();
        b.setType(n.startsWith("DEEPSLATE_") ? Material.DEEPSLATE : n.startsWith("NETHER_") || b.getType() == Material.ANCIENT_DEBRIS
                ? Material.NETHERRACK : Material.STONE);
        b.getWorld().spawnParticle(Particle.FLAME, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.02);
        SoundSpec.of("entity.ender_dragon.flap", 0.4f, 1.6f).playAt(s.at);
        beam(s, b.getLocation().add(0.5, 0.5, 0.5));
    }

    private void mobs(Station s) {
        int hit = 0;
        int power = s.level(Upgrade.POWER);
        for (Entity e : s.at.getWorld().getNearbyEntities(s.at, s.radius, Math.min(s.radius, 6), s.radius)) {
            if (hit >= 3 + power || !(e instanceof Monster m) || m.isDead() || m.customName() != null
                    || online.mythoscraft.core.protection.DamageSources.isPluginEntity(m)
                    || !plugin.protection().query(m.getLocation(), Flag.MOB_DAMAGE)) {
                continue;
            }
            m.getPersistentDataContainer().set(Keys.key("farm_kill"), PersistentDataType.STRING, s.id.toString());
            m.damage(7 + 3 * power);
            m.getWorld().spawnParticle(Particle.SWEEP_ATTACK, m.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
            beam(s, m.getLocation().add(0, 1, 0));
            hit++;
        }
        if (hit > 0) {
            SoundSpec.of("entity.player.attack.sweep", 0.5f, 1.3f).playAt(s.at);
        }
    }

    /** Mobs killed by a shadow companion drop into its loot (and their XP into its XP store). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        String id = event.getEntity().getPersistentDataContainer().get(Keys.key("farm_kill"), PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        Station s;
        try {
            s = stations.get(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (s == null || event.getEntity().getKiller() != null) {
            return;
        }
        addLoot(s, event.getDrops());
        event.getDrops().clear();
        s.xp += event.getDroppedExp();
        event.setDroppedExp(0);
        dirty = true;
    }

    // ------------------------------------------------------------------------------------------- clicks
    private Station stationOf(Entity e) {
        String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
        if (tag == null || !tag.startsWith("companion_farm:")) {
            return null;
        }
        try {
            return stations.get(UUID.fromString(tag.substring("companion_farm:".length())));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEntityEvent event) {
        Station s = stationOf(event.getRightClicked());
        if (s == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (!p.getUniqueId().equals(s.owner) && !p.hasPermission("mythos.admin")) {
            plugin.messages().actionBar(p, "companion-farm.not-yours", "owner", s.ownerName);
            return;
        }
        new FarmMenu(s, p).open(p);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHit(EntityDamageByEntityEvent event) {
        if (stationOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Takes the loot (and the stored XP) into the player's inventory. */
    private void collect(Station s, Player p) {
        for (ItemStack it : s.loot) {
            CustomItemManager.give(p, it);
        }
        int n = s.loot.size();
        s.loot.clear();
        if (s.xp > 0) {
            p.giveExp(s.xp);
            s.xp = 0;
        }
        dirty = true;
        SoundSpec.of("entity.item.pickup", 1f, 0.8f).play(p);
        plugin.messages().actionBar(p, "companion-farm.collected", "stacks", n);
    }

    private void pickUp(Station s, Player p) {
        collect(s, p);
        stations.remove(s.id);
        despawn(s);
        CustomItem item = CustomItem.byId(s.job.item);
        if (item != null) {
            ItemStack back = plugin.items().create(item);
            if (java.util.Arrays.stream(s.levels).anyMatch(l -> l > 0)) {
                back.editMeta(meta -> {
                    meta.getPersistentDataContainer().set(UPGRADES, PersistentDataType.STRING, writeLevels(s));
                    List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    lore.addAll(plugin.messages().in(p, () -> plugin.messages().getList("companion-farm.item-upgrades",
                            "speed", s.level(Upgrade.SPEED), "power", s.level(Upgrade.POWER), "range", s.level(Upgrade.RANGE),
                            "luck", s.level(Upgrade.LUCK))));
                    meta.lore(lore);
                });
            }
            CustomItemManager.give(p, back);
        }
        save();
        store.saveAsync(plugin.tasks());
        SoundSpec.of("entity.allay.item_taken", 1f, 1f).play(p);
        plugin.messages().send(p, "companion-farm.picked-up");
    }

    final class FarmMenu extends Menu {
        private static final int ICON = 4;
        private static final int DURATION = 10;
        private static final int RADIUS = 12;
        private static final int LIMIT = 14;
        private static final int START = 16;
        private static final int[] UPGRADE_SLOTS = {19, 21, 23, 25};
        private static final int COLLECT = 31;
        private static final int PICKUP = 49;
        private final Station s;

        FarmMenu(Station s, Player viewer) {
            super(6, plugin.messages().in(viewer, () -> plugin.messages().get("companion-farm.menu-title",
                    "job", plugin.messages().get("companion-farm.jobs." + s.job.key()))));
            this.s = s;
            plugin.messages().in(viewer, this::draw);
        }

        private void draw() {
            var m = plugin.messages();
            for (int i = 0; i < 54; i++) {
                getInventory().setItem(i, MenuItems.filler());
            }
            CustomItem type = CustomItem.byId(s.job.item);
            ItemStack icon = type == null ? new ItemStack(Material.PAPER) : plugin.items().create(type);
            getInventory().setItem(ICON, MenuItems.withLore(icon, m.getList("companion-farm.how." + s.job.key())));
            getInventory().setItem(DURATION, MenuItems.button(Material.CLOCK, m.get("companion-farm.set-duration", "minutes", s.minutes),
                    m.getList("companion-farm.adjust-lore", "min", MIN_MINUTES, "max", MAX_MINUTES, "step", 5, "big", 30)));
            getInventory().setItem(RADIUS, MenuItems.button(Material.SPYGLASS, m.get("companion-farm.set-radius", "radius", s.radius),
                    m.getList("companion-farm.adjust-lore", "min", MIN_RADIUS, "max", maxRadius(s), "step", 1, "big", 4)));
            getInventory().setItem(LIMIT, MenuItems.button(Material.HOPPER, m.get("companion-farm.set-limit", "limit", s.limit),
                    m.getList("companion-farm.adjust-lore", "min", 1, "max", MAX_STACKS, "step", 1, "big", 9)));
            boolean running = s.running();
            long left = running ? Math.max(0, (s.until - System.currentTimeMillis()) / 60_000) : 0;
            getInventory().setItem(START, MenuItems.button(running ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                    m.get(running ? "companion-farm.stop" : "companion-farm.start"),
                    running ? m.getList("companion-farm.stop-lore", "minutes", left) : m.getList("companion-farm.start-lore",
                            "minutes", s.minutes, "radius", s.radius, "limit", s.limit)));
            Upgrade[] ups = Upgrade.values();
            for (int i = 0; i < ups.length; i++) {
                Upgrade u = ups[i];
                int lvl = s.level(u);
                boolean max = lvl >= maxLevel;
                List<Component> lore = new ArrayList<>(m.getList("companion-farm.upgrade." + u.key() + ".lore", "level", lvl, "max", maxLevel,
                        "next", lvl + 1));
                lore.addAll(max ? m.getList("companion-farm.upgrade-maxed")
                        : m.getList("companion-farm.upgrade-cost", "cost", upgradeCost(s, u)));
                ItemStack b = MenuItems.button(u.icon, m.get("companion-farm.upgrade." + u.key() + ".name", "level", lvl, "max", maxLevel), lore);
                b.setAmount(Math.max(1, lvl));
                getInventory().setItem(UPGRADE_SLOTS[i], b);
            }
            getInventory().setItem(COLLECT, MenuItems.button(Material.CHEST, m.get("companion-farm.collect"),
                    m.getList("companion-farm.collect-lore", "stacks", s.stacks(), "max", s.limit, "xp", s.xp)));
            getInventory().setItem(PICKUP, MenuItems.button(Material.BARRIER, m.get("companion-farm.pickup"), m.getList("companion-farm.pickup-lore")));
            // preview of the loot
            for (int i = 0; i < Math.min(9, s.loot.size()); i++) {
                getInventory().setItem(36 + i, s.loot.get(i).clone());
            }
        }

        /** left click +, right click −, with shift the big step */
        private int adjust(InventoryClickEvent event, int value, int step, int big, int min, int max) {
            int d = event.isShiftClick() ? big : step;
            int v = event.isRightClick() ? value - d : value + d;
            return Math.max(min, Math.min(max, v));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            int slot = event.getRawSlot();
            if (!stations.containsKey(s.id)) {
                player.closeInventory();
                return;
            }
            int upgrade = -1;
            for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
                if (UPGRADE_SLOTS[i] == slot) {
                    upgrade = i;
                }
            }
            if (slot == DURATION) {
                int before = s.minutes;
                s.minutes = adjust(event, s.minutes, 5, 30, MIN_MINUTES, MAX_MINUTES);
                if (s.running()) {
                    s.until += (s.minutes - before) * 60_000L;
                }
            } else if (slot == RADIUS) {
                s.radius = adjust(event, s.radius, 1, 4, MIN_RADIUS, maxRadius(s));
            } else if (slot == LIMIT) {
                s.limit = adjust(event, s.limit, 1, 9, 1, MAX_STACKS);
            } else if (upgrade >= 0) {
                Upgrade u = Upgrade.values()[upgrade];
                if (s.level(u) >= maxLevel) {
                    plugin.messages().actionBar(player, "companion-farm.upgrade-max");
                    return;
                }
                long cost = upgradeCost(s, u);
                if (!plugin.attractions().charge(player, online.mythoscraft.core.economy.Currency.GEMS, cost)) {
                    return;
                }
                s.levels[u.ordinal()]++;
                SoundSpec.of("block.anvil.use", 0.6f, 1.5f).play(player);
                s.at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, s.pos().add(0, 0.8, 0), 25, 0.4, 0.4, 0.4, 0);
                plugin.messages().send(player, "companion-farm.upgraded", "upgrade",
                        plugin.messages().get("companion-farm.upgrade." + u.key() + ".name", "level", s.level(u), "max", maxLevel),
                        "cost", cost);
            } else if (slot == START) {
                if (s.running()) {
                    s.until = 0;
                    plugin.messages().actionBar(player, "companion-farm.stopped");
                } else {
                    s.until = System.currentTimeMillis() + s.minutes * 60_000L;
                    s.lastSeen = System.currentTimeMillis();
                    SoundSpec.of("block.note_block.chime", 1f, 1.4f).play(player);
                    plugin.messages().actionBar(player, "companion-farm.started", "minutes", s.minutes, "radius", s.radius);
                }
            } else if (slot == COLLECT) {
                collect(s, player);
            } else if (slot == PICKUP) {
                player.closeInventory();
                pickUp(s, player);
                return;
            } else {
                return;
            }
            dirty = true;
            label(s);
            SoundSpec.of("ui.button.click", 0.6f, 1.2f).play(player);
            plugin.messages().in(player, this::draw);
        }
    }

    // ------------------------------------------------------------------------------------------- storage
    private void load() {
        YamlConfiguration y = store.load(false);
        ConfigurationSection root = y.getConfigurationSection("stations");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(key);
            try {
                Job job = Job.valueOf(c.getString("job", "CROPS"));
                World w = Bukkit.getWorld(c.getString("world", ""));
                if (w == null) {
                    orphans.put(key, c.getValues(true));
                    continue;
                }
                Location at = new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), (float) c.getDouble("yaw"), 0);
                Station s = new Station(UUID.fromString(key), UUID.fromString(c.getString("owner")), c.getString("owner-name", "?"), job, at);
                s.radius = c.getInt("radius", 8);
                s.minutes = Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, c.getInt("minutes", 30)));
                s.limit = Math.max(1, Math.min(MAX_STACKS, c.getInt("limit", MAX_STACKS)));
                readLevels(s, c.getString("upgrades"));
                s.until = c.getLong("until", 0);
                s.lastSeen = c.getLong("last-seen", 0);
                s.xp = c.getInt("xp", 0);
                List<?> loot = c.getList("loot");
                if (loot != null) {
                    for (Object o : loot) {
                        if (o instanceof ItemStack it) {
                            s.loot.add(it);
                        }
                    }
                }
                stations.put(s.id, s);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("companion_farms.yml: station " + key + " skipped – " + e.getMessage());
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        orphans.forEach((key, values) -> values.forEach((k, v) -> {
            if (!(v instanceof ConfigurationSection)) {
                y.set("stations." + key + "." + k, v);
            }
        }));
        for (Station s : stations.values()) {
            String p = "stations." + s.id;
            y.set(p + ".job", s.job.name());
            y.set(p + ".owner", s.owner.toString());
            y.set(p + ".owner-name", s.ownerName);
            y.set(p + ".world", s.at.getWorld() == null ? "" : s.at.getWorld().getName());
            y.set(p + ".x", s.at.getX());
            y.set(p + ".y", s.at.getY());
            y.set(p + ".z", s.at.getZ());
            y.set(p + ".yaw", s.at.getYaw());
            y.set(p + ".radius", s.radius);
            y.set(p + ".minutes", s.minutes);
            y.set(p + ".limit", s.limit);
            y.set(p + ".upgrades", writeLevels(s));
            y.set(p + ".until", s.until);
            y.set(p + ".last-seen", s.lastSeen);
            y.set(p + ".xp", s.xp);
            y.set(p + ".loot", new ArrayList<>(s.loot));
        }
        store.replace(y);
        dirty = false;
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        save();
        store.saveNow();
        stations.values().forEach(CompanionFarmService::despawn);
    }
}
