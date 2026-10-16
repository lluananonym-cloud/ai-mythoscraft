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
 * Farming companions: sneak + right-click the ground with a companion item → it sits down there as a little farm
 * station. Right-click it: a menu to pick how long (10 min … 2 h) and how far (4 … 16 blocks) it works, start /
 * stop, collect the loot and pick it up again. Every companion has its own job and shows the tool it uses:
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

        int stacks() {
            return loot.size();
        }
    }

    private static final int MAX_STACKS = 54;
    private static final int[] DURATIONS = {10, 30, 60, 120};
    private static final int[] RADII = {4, 8, 12, 16};

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Map<UUID, Station> stations = new LinkedHashMap<>();
    private int maxPerPlayer = 2;
    private boolean enabled = true;
    private boolean dirty;
    /** stations in worlds that are not loaded (yet) – kept unchanged in the file */
    private final Map<String, Map<String, Object>> orphans = new LinkedHashMap<>();

    public CompanionFarmService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/companion_farms.yml");
        load();
        plugin.tasks().repeat(this, 40, 10, "companion farms", this::tick);
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
        hand.setAmount(hand.getAmount() - 1);
        Location at = spot.getLocation().add(0.5, 0, 0.5);
        at.setYaw(p.getLocation().getYaw() + 180);
        Station s = new Station(UUID.randomUUID(), p.getUniqueId(), p.getName(), job, at);
        stations.put(s.id, s);
        spawn(s);
        dirty = true;
        SoundSpec.of("entity.allay.item_given", 1f, 1.2f).playAt(at);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.6, 0), 20, 0.4, 0.4, 0.4, 0);
        plugin.messages().send(p, "companion-farm.placed", "job", plugin.messages().get("companion-farm.jobs." + job.key()));
        plugin.tasks().later(2, "companion farm menu", () -> new FarmMenu(s, p).open(p));
    }

    // ------------------------------------------------------------------------------------------- visuals
    private void spawn(Station s) {
        despawn(s);
        World w = s.at.getWorld();
        if (w == null || !s.at.isChunkLoaded()) {
            return;
        }
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
            d.setRotation(s.at.getYaw(), 0);
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        s.label = w.spawn(s.at.clone().add(0, 1.35, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(110, 20, 10, 30));
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
                    "time", String.format("%d:%02d", left / 60, left % 60), "stacks", s.stacks(), "max", MAX_STACKS, "radius", s.radius));
        } else {
            s.label.text(plugin.messages().get("companion-farm.label-idle", "owner", s.ownerName, "job", job, "stacks", s.stacks(),
                    "max", MAX_STACKS));
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
            if (s.stacks() >= MAX_STACKS) {
                if (s.tick % 20 == 0) {
                    s.at.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, s.at.clone().add(0, 1, 0), 1, 0.2, 0.2, 0.2, 0);
                }
                continue;
            }
            int every = switch (s.job) {
                case FISH -> 24;          // a catch every ~12 s
                case TREES, ORES -> 8;    // one tree / ore every ~4 s
                default -> 4;             // crops, plants and mobs every 2 s
            };
            if (s.tick % every == 0) {
                work(s);
            }
        }
    }

    private void animate(Station s, boolean running) {
        if (s.body == null || !s.body.isValid()) {
            return;
        }
        float bob = running ? (float) Math.sin(s.tick * 0.6) * 0.06f : 0f;
        s.body.setInterpolationDelay(0);
        s.body.setInterpolationDuration(10);
        Quaternionf turn = new Quaternionf().rotationY(running ? (float) Math.sin(s.tick * 0.15) * 0.5f : 0f);
        s.body.setTransformation(new Transformation(new Vector3f(0, 0.42f + bob, 0), turn, new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
        if (s.tool != null && s.tool.isValid()) {
            float swing = running ? (float) Math.sin(s.tick * 1.3) * 0.7f : 0f;
            s.tool.setInterpolationDelay(0);
            s.tool.setInterpolationDuration(10);
            s.tool.setTransformation(new Transformation(new Vector3f(0.42f, 0.05f + bob, 0), new Quaternionf().rotationX(swing),
                    new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
        }
        if (running && s.tick % 6 == 0) {
            s.at.getWorld().spawnParticle(Particle.WAX_ON, s.at.clone().add(0, 0.9, 0), 2, 0.25, 0.2, 0.25, 0);
        }
    }

    private void work(Station s) {
        switch (s.job) {
            case CROPS -> crops(s);
            case FISH -> fish(s);
            case TREES -> trees(s);
            case PLANTS -> plants(s);
            case ORES -> ores(s);
            case MOBS -> mobs(s);
        }
        dirty = true;
    }

    private boolean allowed(Block b) {
        return plugin.protection().query(b, Flag.BREAK) && !plugin.attractionStore().inside(b.getLocation(), 2);
    }

    private void addLoot(Station s, java.util.Collection<ItemStack> drops) {
        for (ItemStack d : drops) {
            if (d == null || d.isEmpty()) {
                continue;
            }
            ItemStack rest = d.clone();
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

    /** Line of light from the companion to the block it works on. */
    private static void beam(Station s, Location to) {
        Location from = s.at.clone().add(0, 0.7, 0);
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
                || b.getType() == Material.SWEET_BERRY_BUSH), 6)) {
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
        }, 8)) {
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
        for (Entity e : s.at.getWorld().getNearbyEntities(s.at, s.radius, Math.min(s.radius, 6), s.radius)) {
            if (hit >= 3 || !(e instanceof Monster m) || m.isDead() || m.customName() != null
                    || online.mythoscraft.core.protection.DamageSources.isPluginEntity(m)
                    || !plugin.protection().query(m.getLocation(), Flag.MOB_DAMAGE)) {
                continue;
            }
            m.getPersistentDataContainer().set(Keys.key("farm_kill"), PersistentDataType.STRING, s.id.toString());
            m.damage(7);
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
            CustomItemManager.give(p, plugin.items().create(item));
        }
        save();
        store.saveAsync(plugin.tasks());
        SoundSpec.of("entity.allay.item_taken", 1f, 1f).play(p);
        plugin.messages().send(p, "companion-farm.picked-up");
    }

    final class FarmMenu extends Menu {
        private final Station s;

        FarmMenu(Station s, Player viewer) {
            super(5, plugin.messages().in(viewer, () -> plugin.messages().get("companion-farm.menu-title",
                    "job", plugin.messages().get("companion-farm.jobs." + s.job.key()))));
            this.s = s;
            plugin.messages().in(viewer, this::draw);
        }

        private void draw() {
            var m = plugin.messages();
            for (int i = 0; i < 45; i++) {
                getInventory().setItem(i, MenuItems.filler());
            }
            CustomItem type = CustomItem.byId(s.job.item);
            ItemStack icon = type == null ? new ItemStack(Material.PAPER) : plugin.items().create(type);
            getInventory().setItem(4, MenuItems.withLore(icon, m.getList("companion-farm.how." + s.job.key())));
            for (int i = 0; i < DURATIONS.length; i++) {
                boolean sel = s.minutes == DURATIONS[i];
                getInventory().setItem(10 + i, MenuItems.button(sel ? Material.CLOCK : Material.GRAY_DYE,
                        m.get(sel ? "companion-farm.duration-selected" : "companion-farm.duration", "minutes", DURATIONS[i]), List.of()));
            }
            for (int i = 0; i < RADII.length; i++) {
                boolean sel = s.radius == RADII[i];
                getInventory().setItem(19 + i, MenuItems.button(sel ? Material.SPYGLASS : Material.GRAY_DYE,
                        m.get(sel ? "companion-farm.radius-selected" : "companion-farm.radius", "radius", RADII[i]), List.of()));
            }
            boolean running = s.running();
            long left = running ? Math.max(0, (s.until - System.currentTimeMillis()) / 60_000) : 0;
            getInventory().setItem(15, MenuItems.button(running ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                    m.get(running ? "companion-farm.stop" : "companion-farm.start"),
                    running ? m.getList("companion-farm.stop-lore", "minutes", left) : m.getList("companion-farm.start-lore",
                            "minutes", s.minutes, "radius", s.radius)));
            getInventory().setItem(24, MenuItems.button(Material.CHEST, m.get("companion-farm.collect"),
                    m.getList("companion-farm.collect-lore", "stacks", s.stacks(), "max", MAX_STACKS, "xp", s.xp)));
            getInventory().setItem(40, MenuItems.button(Material.BARRIER, m.get("companion-farm.pickup"), m.getList("companion-farm.pickup-lore")));
            // preview of the loot
            for (int i = 0; i < Math.min(7, s.loot.size()); i++) {
                getInventory().setItem(28 + i, s.loot.get(i).clone());
            }
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            int slot = event.getRawSlot();
            if (!stations.containsKey(s.id)) {
                player.closeInventory();
                return;
            }
            if (slot >= 10 && slot < 10 + DURATIONS.length) {
                s.minutes = DURATIONS[slot - 10];
                if (s.running()) {
                    s.until = System.currentTimeMillis() + s.minutes * 60_000L;
                }
            } else if (slot >= 19 && slot < 19 + RADII.length) {
                s.radius = RADII[slot - 19];
            } else if (slot == 15) {
                if (s.running()) {
                    s.until = 0;
                    plugin.messages().actionBar(player, "companion-farm.stopped");
                } else {
                    s.until = System.currentTimeMillis() + s.minutes * 60_000L;
                    SoundSpec.of("block.note_block.chime", 1f, 1.4f).play(player);
                    plugin.messages().actionBar(player, "companion-farm.started", "minutes", s.minutes, "radius", s.radius);
                }
            } else if (slot == 24) {
                collect(s, player);
            } else if (slot == 40) {
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
                s.minutes = c.getInt("minutes", 30);
                s.until = c.getLong("until", 0);
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
            y.set(p + ".until", s.until);
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
