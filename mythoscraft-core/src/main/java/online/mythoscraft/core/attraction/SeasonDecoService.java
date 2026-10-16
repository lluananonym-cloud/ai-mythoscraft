package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.item.SeasonService;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Season decoration of the spawn. Halloween: pumpkins, jack o'lanterns, cobwebs and candles on the decoration spots,
 * a giant pumpkin on the season square, ground fog and bats. Winter: presents, snowmen and little firs, a big
 * christmas tree, snow falling from the sky that slowly covers the spawn with a thin layer – walking through it
 * leaves footprints that snow over again. Everything is removed again when the season ends.
 */
public final class SeasonDecoService implements Listener {

    public enum Theme { NONE, HALLOWEEN, WINTER, SPRING, SUMMER, AUTUMN, NEWYEAR, VALENTINE, STARS }

    private final MythosCraft plugin;
    private boolean websConverted;
    private final YamlStore store;
    /** "world;x;y;z" → original block data of every block we placed (deco, tree, pumpkin). */
    private final Map<String, String> placed = new LinkedHashMap<>();
    private final Set<Integer> doneMarkers = new HashSet<>();
    private final Set<String> snow = new LinkedHashSet<>();
    private boolean centerpieceDone;
    private Theme applied = Theme.NONE;
    private Map<String, Theme> themes = Map.of("halloween", Theme.HALLOWEEN, "frostfest", Theme.WINTER, "fruehling", Theme.SPRING,
            "sommerfest", Theme.SUMMER, "erntefest", Theme.AUTUMN, "neujahr", Theme.NEWYEAR, "valentin", Theme.VALENTINE,
            "sternenregen", Theme.STARS);
    private int snowPerTick = 30;
    private int snowMax = 9000;
    private int soundMinutes = 4;
    private boolean dirty;
    private long nextSound;

    public SeasonDecoService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/season-deco.yml");
        YamlConfiguration yaml = store.load(false);
        try {
            applied = Theme.valueOf(yaml.getString("theme", "NONE"));
        } catch (IllegalArgumentException e) {
            applied = Theme.NONE;
        }
        for (String s : yaml.getStringList("placed")) {
            int bar = s.indexOf('|');
            if (bar > 0) {
                placed.put(s.substring(0, bar), s.substring(bar + 1));
            }
        }
        yaml.getIntegerList("markers").forEach(doneMarkers::add);
        snow.addAll(yaml.getStringList("snow"));
        centerpieceDone = yaml.getBoolean("centerpiece", false);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.season-deco");
        Map<String, Theme> map = new LinkedHashMap<>();
        ConfigReader t = cfg.sub("themes");
        for (String id : t.keys()) {
            try {
                map.put(id.toLowerCase(java.util.Locale.ROOT), Theme.valueOf(t.string(id, "NONE").toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                t.warn(id, "halloween, winter oder none");
            }
        }
        if (!map.isEmpty()) {
            themes = map;
        }
        snowPerTick = cfg.integer("snow-per-second", 30, 0, 500);
        snowMax = cfg.integer("snow-max-blocks", 9000, 0, 100_000);
        soundMinutes = cfg.integer("sound-every-minutes", 4, 1, 120);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 80, 20, "season deco", this::tick);
        plugin.tasks().repeat(this, 60, 5, "season particles", this::particles);
        plugin.tasks().repeat(this, 70, 4, "season spawn glow", this::spawnGlow);
        plugin.tasks().repeat(this, 1200, 1200, "season deco save", this::saveIfDirty);
    }

    public Theme theme() {
        SeasonService.Season s = plugin.seasons().active();
        return s == null ? Theme.NONE : themes.getOrDefault(s.id().toLowerCase(java.util.Locale.ROOT), Theme.NONE);
    }

    private static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private static Block block(String key) {
        String[] p = key.split(";");
        World w = Bukkit.getWorld(p[0]);
        return w == null ? null : w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    private void tick() {
        Theme want = theme();
        if (want != applied) {
            removeAll();
            applied = want;
            dirty = true;
            nextSound = System.currentTimeMillis() + 30_000;
        }
        if (!websConverted && plugin.attractionStore().center() != null && plugin.attractionStore().center().isChunkLoaded()) {
            websConverted = true;
            convertRealWebs();
        }
        if (applied == Theme.NONE || plugin.attractionStore().center() == null) {
            return;
        }
        List<Location> spots = plugin.attractionStore().all("deco");
        for (int i = 0; i < spots.size(); i++) {
            Location l = spots.get(i);
            if (!doneMarkers.contains(i) && l.isChunkLoaded()) {
                decorate(i, l);
                doneMarkers.add(i);
                dirty = true;
            }
        }
        // meadow plants, obelisk accents and arch crowns
        seasonSpots("ground", 30_000, this::groundPlant);
        seasonSpots("obelisk_accent", 40_000, l -> accent());
        seasonSpots("arch_top", 41_000, l -> crown());
        banner();
        // the tournament arenas get decorated as well
        int arenaIndex = 20_000;
        for (String tier : new String[]{"daily", "weekly", "monthly"}) {
            List<Location> arenaSpots = plugin.attractionStore().all("arena_deco_" + tier);
            for (int i = 0; i < arenaSpots.size(); i++) {
                Location l = arenaSpots.get(i);
                int id = arenaIndex + i;
                // arenas are reset from a snapshot after each tournament – decorate again when the spot is empty
                if ((!doneMarkers.contains(id) || l.getBlock().getType().isAir()) && l.isChunkLoaded()) {
                    decorate(i, l);
                    doneMarkers.add(id);
                    dirty = true;
                }
            }
            arenaIndex += 1000;
        }
        // every lamp post gets a season topper
        List<Location> lamps = plugin.attractionStore().all("lamp");
        for (int i = 0; i < lamps.size(); i++) {
            Location l = lamps.get(i).clone().add(0, 2, 0);
            if (!doneMarkers.contains(10_000 + i) && l.isChunkLoaded()) {
                if (l.getBlock().getType().isAir()) {
                    String top = lampTopper();
                    if (top != null) {
                        set(l, top);
                    }
                }
                doneMarkers.add(10_000 + i);
                dirty = true;
            }
        }
        Location tree = plugin.attractionStore().first("season_tree");
        if (!centerpieceDone && tree != null && tree.isChunkLoaded()) {
            if (applied == Theme.WINTER) {
                christmasTree(tree);
            } else if (applied == Theme.HALLOWEEN) {
                giantPumpkin(tree);
            } else {
                centerpiece(tree);
            }
            centerpieceDone = true;
            dirty = true;
        }
        if (applied == Theme.WINTER) {
            snowfall();
        }
        sounds();
    }

    /** Cobwebs are only shown (block display) – players must never get stuck in the spawn decoration. */
    private void web(Location at) {
        if (!at.isChunkLoaded()) {
            return;
        }
        Location l = at.getBlock().getLocation();
        for (org.bukkit.entity.Entity e : l.getWorld().getNearbyEntities(l.clone().add(0.5, 0.5, 0.5), 0.4, 0.4, 0.4)) {
            if ("deco_web".equals(e.getPersistentDataContainer().get(online.mythoscraft.core.util.Keys.ENTITY,
                    org.bukkit.persistence.PersistentDataType.STRING))) {
                return;
            }
        }
        l.getWorld().spawn(l, org.bukkit.entity.BlockDisplay.class, d -> {
            d.setBlock(Material.COBWEB.createBlockData());
            d.setPersistent(true);
            d.getPersistentDataContainer().set(online.mythoscraft.core.util.Keys.ENTITY, org.bukkit.persistence.PersistentDataType.STRING, "deco_web");
        });
    }

    private void removeWebs() {
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.BlockDisplay d : w.getEntitiesByClass(org.bukkit.entity.BlockDisplay.class)) {
                if ("deco_web".equals(d.getPersistentDataContainer().get(online.mythoscraft.core.util.Keys.ENTITY,
                        org.bukkit.persistence.PersistentDataType.STRING))) {
                    d.remove();
                }
            }
        }
    }

    /** Old servers: real cobwebs placed by earlier versions become display-only webs. */
    private void convertRealWebs() {
        for (Map.Entry<String, String> e : new ArrayList<>(placed.entrySet())) {
            Block b = block(e.getKey());
            if (b != null && b.getType() == Material.COBWEB && b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
                try {
                    b.setBlockData(Bukkit.createBlockData(e.getValue()), false);
                } catch (IllegalArgumentException ex) {
                    b.setType(Material.AIR, false);
                }
                placed.remove(e.getKey());
                web(b.getLocation());
                dirty = true;
            }
        }
    }

    private void set(Location at, String data) {
        if (data.startsWith("minecraft:cobweb")) {
            web(at);
            return;
        }
        BlockData bd;
        try {
            bd = Bukkit.createBlockData(data);
        } catch (IllegalArgumentException e) {
            return; // block not known in this version – skip the spot
        }
        Block b = at.getBlock();
        String k = key(at);
        placed.putIfAbsent(k, b.getBlockData().getAsString());
        b.setBlockData(bd, false);
    }

    private void decorate(int index, Location l) {
        Block b = l.getBlock();
        if (!b.getType().isAir() && b.getType() != Material.SHORT_GRASS) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String[] faces = {"north", "east", "south", "west"};
        String face = faces[Math.floorMod(index * 7, 4)];
        int roll = Math.floorMod(index * 37 + 11, 100);
        if (applied == Theme.HALLOWEEN) {
            if (roll < 40) {
                set(l, "minecraft:jack_o_lantern[facing=" + face + "]");
            } else if (roll < 65) {
                set(l, "minecraft:carved_pumpkin[facing=" + face + "]");
            } else if (roll < 80) {
                set(l, "minecraft:pumpkin");
                set(l.clone().add(0, 1, 0), "minecraft:jack_o_lantern[facing=" + face + "]");
            } else if (roll < 90) {
                set(l, "minecraft:cobweb");
            } else {
                set(l, "minecraft:orange_candle[candles=" + (1 + rnd.nextInt(4)) + ",lit=true]");
            }
        } else if (applied == Theme.WINTER) {
            String[] colors = {"red", "lime", "white", "light_blue", "yellow"};
            String color = colors[Math.floorMod(index * 3, colors.length)];
            if (roll < 45) {
                set(l, "minecraft:" + color + "_wool");
                set(l.clone().add(0, 1, 0), "minecraft:" + (color.equals("red") ? "lime" : "red") + "_candle[candles=1,lit=true]");
            } else if (roll < 70) {
                set(l, "minecraft:snow_block");
                set(l.clone().add(0, 1, 0), "minecraft:carved_pumpkin[facing=" + face + "]");
            } else {
                set(l, "minecraft:spruce_leaves[persistent=true]");
                set(l.clone().add(0, 1, 0), "minecraft:spruce_leaves[persistent=true]");
                set(l.clone().add(0, 2, 0), "minecraft:lantern");
            }
        } else {
            otherDeco(l, roll, index);
        }
    }

    private void seasonSpots(String key, int offset, java.util.function.Function<Location, String> block) {
        List<Location> list = plugin.attractionStore().all(key);
        for (int i = 0; i < list.size(); i++) {
            Location l = list.get(i);
            int id = offset + i;
            if (!doneMarkers.contains(id) && l.isChunkLoaded()) {
                String b = block.apply(l);
                if (b != null) {
                    if (key.equals("ground") && !l.getBlock().getType().isAir() && l.getBlock().getType() != Material.SHORT_GRASS) {
                        doneMarkers.add(id);
                        continue;
                    }
                    set(l, b);
                }
                doneMarkers.add(id);
                dirty = true;
            }
        }
    }

    private String groundPlant(Location l) {
        int roll = Math.floorMod(l.getBlockX() * 31 + l.getBlockZ() * 17, 100);
        return switch (applied) {
            case HALLOWEEN -> roll < 20 ? "minecraft:carved_pumpkin[facing=south]" : roll < 60 ? "minecraft:dead_bush" : "minecraft:cobweb";
            case SPRING -> roll < 70 ? "minecraft:pink_petals[flower_amount=4,facing=north]" : "minecraft:allium";
            case SUMMER -> roll < 50 ? "minecraft:dandelion" : roll < 80 ? "minecraft:sunflower[half=lower]" : "minecraft:melon";
            case AUTUMN -> roll < 75 ? "minecraft:leaf_litter[segment_amount=4,facing=north]" : "minecraft:pumpkin";
            case VALENTINE -> roll < 50 ? "minecraft:poppy" : "minecraft:pink_tulip";
            case NEWYEAR -> roll < 50 ? "minecraft:yellow_candle[candles=3,lit=true]" : "minecraft:purple_candle[candles=2,lit=true]";
            case STARS -> roll < 40 ? "minecraft:small_amethyst_bud[facing=up]" : null;
            default -> null; // winter: the snow layer does the job
        };
    }

    private String accent() {
        return switch (applied) {
            case HALLOWEEN -> "minecraft:jack_o_lantern";
            case WINTER -> "minecraft:blue_ice";
            case SPRING -> "minecraft:pink_glazed_terracotta";
            case SUMMER -> "minecraft:shroomlight";
            case AUTUMN -> "minecraft:orange_glazed_terracotta";
            case NEWYEAR -> "minecraft:gold_block";
            case VALENTINE -> "minecraft:red_glazed_terracotta";
            case STARS -> "minecraft:amethyst_block";
            default -> null;
        };
    }

    private String crown() {
        return switch (applied) {
            case HALLOWEEN -> "minecraft:jack_o_lantern[facing=south]";
            case WINTER -> "minecraft:snow_block";
            case SPRING -> "minecraft:flowering_azalea";
            case SUMMER -> "minecraft:sunflower[half=lower]";
            case AUTUMN -> "minecraft:pumpkin";
            case NEWYEAR -> "minecraft:end_rod[facing=up]";
            case VALENTINE -> "minecraft:rose_bush[half=lower]";
            case STARS -> "minecraft:amethyst_cluster[facing=up]";
            default -> null;
        };
    }

    private org.bukkit.entity.TextDisplay bannerEntity;

    /** Big season banner floating above the obelisk. */
    private void banner() {
        if (bannerEntity != null && bannerEntity.isValid()) {
            return;
        }
        Location c = plugin.attractionStore().center();
        SeasonService.Season s = plugin.seasons().active();
        if (c == null || s == null || !c.isChunkLoaded()) {
            return;
        }
        bannerEntity = c.getWorld().spawn(c.clone().add(0, 27, 0), org.bukkit.entity.TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setLineWidth(400);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(0x60, 0x10, 0x08, 0x20));
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(4f, 4f, 4f), new org.joml.AxisAngle4f()));
            t.text(plugin.messages().get("seasons.banner", "season", online.mythoscraft.core.util.Text.parse(s.name())));
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "season_banner");
        });
    }

    private void removeBanner() {
        if (bannerEntity != null && bannerEntity.isValid()) {
            bannerEntity.remove();
        }
        bannerEntity = null;
    }

    private String lampTopper() {
        return switch (applied) {
            case HALLOWEEN -> "minecraft:jack_o_lantern[facing=south]";
            case WINTER -> "minecraft:snow_block";
            case SPRING -> "minecraft:flowering_azalea_leaves[persistent=true]";
            case SUMMER -> "minecraft:shroomlight";
            case AUTUMN -> "minecraft:pumpkin";
            case NEWYEAR -> "minecraft:gold_block";
            case VALENTINE -> "minecraft:pink_wool";
            case STARS -> "minecraft:amethyst_cluster[facing=up]";
            default -> null;
        };
    }

    /** Colours of the season (glow around lamps, obelisk aura). */
    private int[] colors() {
        return switch (applied) {
            case HALLOWEEN -> new int[]{0xFF7A1A, 0x7C3AED, 0x22C55E};
            case WINTER -> new int[]{0xE0F2FE, 0x38BDF8, 0xEF4444};
            case SPRING -> new int[]{0xF9A8D4, 0x86EFAC, 0xFDE047};
            case SUMMER -> new int[]{0xFDE047, 0x38BDF8, 0xFB923C};
            case AUTUMN -> new int[]{0xE07A1F, 0xB45309, 0xFACC15};
            case NEWYEAR -> new int[]{0xFDE047, 0xA855F7, 0x38BDF8};
            case VALENTINE -> new int[]{0xF472B6, 0xDC2626, 0xFFFFFF};
            case STARS -> new int[]{0xC4B5FD, 0x22D3EE, 0xFEF3C7};
            default -> new int[]{0xFFFFFF};
        };
    }

    private double spin;

    /** Spawn glow: coloured motes around every lamp, a spiral around the obelisk, sparkles at the centrepiece. */
    private void spawnGlow() {
        if (applied == Theme.NONE) {
            return;
        }
        Location c = plugin.attractionStore().center();
        if (c == null || !c.isChunkLoaded()) {
            return;
        }
        boolean anyone = false;
        for (Player p : c.getWorld().getPlayers()) {
            anyone |= plugin.attractionStore().inside(p.getLocation(), 20);
        }
        if (!anyone) {
            return;
        }
        int[] col = colors();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World w = c.getWorld();
        for (Location lamp : plugin.attractionStore().all("lamp")) {
            if (lamp.isChunkLoaded() && rnd.nextInt(3) == 0) {
                w.spawnParticle(Particle.DUST, lamp.clone().add(0.5, 1.6, 0.5), 3, 0.5, 0.4, 0.5, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(col[rnd.nextInt(col.length)]), 1.3f));
            }
        }
        spin += 0.35;
        for (int i = 0; i < 3; i++) {
            double a = spin + i * Math.PI * 2 / 3;
            double y = 2 + ((spin * 2 + i * 7) % 20);
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 2.6, y, Math.sin(a) * 2.6), 2, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(col[i % col.length]), 1.6f));
        }
        Location tree = plugin.attractionStore().first("season_tree");
        if (tree != null && tree.isChunkLoaded()) {
            w.spawnParticle(applied == Theme.HALLOWEEN ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD, tree.clone().add(0.5, 6, 0.5), 4, 3, 4, 3, 0.01);
        }
    }

    /** Decoration spots of the other seasons. */
    private void otherDeco(Location l, int roll, int index) {
        Location up = l.clone().add(0, 1, 0);
        switch (applied) {
            case SPRING -> {
                String[] flowers = {"pink_tulip", "allium", "oxeye_daisy", "cornflower", "lily_of_the_valley"};
                String[] wool = {"pink", "light_blue", "yellow", "lime", "magenta"};
                if (roll < 55) {
                    set(l, "minecraft:" + flowers[index % flowers.length]);
                } else if (roll < 80) {
                    set(l, "minecraft:" + wool[index % wool.length] + "_wool");
                    set(up, "minecraft:" + wool[(index + 2) % wool.length] + "_carpet");
                } else {
                    set(l, "minecraft:flowering_azalea");
                }
            }
            case SUMMER -> {
                if (roll < 40) {
                    set(l, "minecraft:sunflower[half=lower]");
                    set(up, "minecraft:sunflower[half=upper]");
                } else if (roll < 70) {
                    set(l, "minecraft:sand");
                    set(up, "minecraft:" + (roll % 2 == 0 ? "red" : "yellow") + "_banner[rotation=" + (index % 16) + "]");
                } else {
                    set(l, "minecraft:melon");
                }
            }
            case AUTUMN -> {
                if (roll < 40) {
                    set(l, "minecraft:hay_block");
                    set(up, "minecraft:pumpkin");
                } else if (roll < 70) {
                    set(l, "minecraft:pumpkin");
                } else {
                    set(l, "minecraft:orange_wool");
                    set(up, "minecraft:dark_oak_leaves[persistent=true]");
                }
            }
            case NEWYEAR -> {
                String[] c = {"red", "yellow", "light_blue", "magenta", "lime"};
                if (roll < 50) {
                    set(l, "minecraft:gold_block");
                    set(up, "minecraft:" + (roll % 2 == 0 ? "end_rod[facing=up]" : "lantern"));
                } else {
                    set(l, "minecraft:" + c[index % c.length] + "_concrete");
                    set(up, "minecraft:" + c[(index + 1) % c.length] + "_candle[candles=3,lit=true]");
                }
            }
            case VALENTINE -> {
                if (roll < 50) {
                    set(l, "minecraft:rose_bush[half=lower]");
                    set(up, "minecraft:rose_bush[half=upper]");
                } else if (roll < 80) {
                    set(l, "minecraft:pink_wool");
                    set(up, "minecraft:red_candle[candles=2,lit=true]");
                } else {
                    set(l, "minecraft:pink_tulip");
                }
            }
            case STARS -> {
                if (roll < 50) {
                    set(l, "minecraft:amethyst_block");
                    set(up, "minecraft:amethyst_cluster[facing=up]");
                } else {
                    set(l, "minecraft:end_rod[facing=up]");
                }
            }
            default -> {
            }
        }
    }

    /** Centrepiece of the other seasons on the season square. */
    private void centerpiece(Location base) {
        switch (applied) {
            case SPRING -> {
                String[] stripes = {"pink", "light_blue", "yellow", "lime", "magenta", "white"};
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = 0; dy <= 8; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            double ry = dy < 4 ? 4.0 : 4.8;
                            if (Math.pow(dx / 3.2, 2) + Math.pow((dy - 4) / ry, 2) + Math.pow(dz / 3.2, 2) <= 1) {
                                set(base.clone().add(dx, dy, dz), "minecraft:" + stripes[dy % stripes.length] + "_concrete");
                            }
                        }
                    }
                }
            }
            case SUMMER -> {
                for (int y = 0; y < 9; y++) {
                    set(base.clone().add(y > 5 ? 1 : 0, y, 0), "minecraft:jungle_log");
                }
                int[][] fronds = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
                for (int[] f : fronds) {
                    for (int i = 1; i <= 4; i++) {
                        set(base.clone().add(1 + f[0] * i, 9 - (i > 2 ? 1 : 0), f[1] * i), "minecraft:jungle_leaves[persistent=true]");
                    }
                }
                set(base.clone().add(1, 9, 0), "minecraft:jungle_leaves[persistent=true]");
            }
            case AUTUMN -> {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int h = 3 - Math.max(Math.abs(dx), Math.abs(dz));
                        for (int y = 0; y <= h; y++) {
                            set(base.clone().add(dx, y, dz), "minecraft:hay_block");
                        }
                    }
                }
                set(base.clone().add(0, 4, 0), "minecraft:carved_pumpkin[facing=east]");
                set(base.clone().add(0, 5, 0), "minecraft:lantern");
            }
            case NEWYEAR -> {
                for (int y = 0; y < 10; y++) {
                    int r = Math.max(0, 3 - y / 3);
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            set(base.clone().add(dx, y, dz), "minecraft:" + (y % 3 == 2 ? "sea_lantern" : "gold_block"));
                        }
                    }
                }
                set(base.clone().add(0, 10, 0), "minecraft:end_rod[facing=up]");
            }
            case VALENTINE -> {
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = 0; dy <= 9; dy++) {
                        double x = dx / 5.0;
                        double y = (dy - 3.5) / 5.0;
                        double v = Math.pow(x * x + y * y - 1, 3) - x * x * y * y * y;
                        if (v <= 0) {
                            set(base.clone().add(dx, dy, 0), "minecraft:" + ((Math.abs(dx) + dy) % 3 == 0 ? "pink_wool" : "red_wool"));
                        }
                    }
                }
            }
            case STARS -> {
                for (int y = 0; y < 12; y++) {
                    set(base.clone().add(0, y, 0), "minecraft:" + (y % 3 == 0 ? "sea_lantern" : "amethyst_block"));
                }
                set(base.clone().add(0, 12, 0), "minecraft:end_rod[facing=up]");
            }
            default -> {
            }
        }
    }

    private void christmasTree(Location base) {
        int height = 14;
        for (int y = 0; y < height; y++) {
            set(base.clone().add(0, y, 0), "minecraft:spruce_log");
        }
        String[] lights = {"ochre_froglight", "pearlescent_froglight", "verdant_froglight", "glowstone", "sea_lantern", "shroomlight"};
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int y = 2; y < height; y++) {
            double r = (height - y) * 0.42 + (y % 3 == 0 ? 0.6 : 0);
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if ((dx != 0 || dz != 0) && d <= r) {
                        boolean light = d > r - 1 && rnd.nextInt(7) == 0;
                        set(base.clone().add(dx, y, dz), "minecraft:" + (light ? lights[rnd.nextInt(lights.length)] : "spruce_leaves[persistent=true]"));
                    }
                }
            }
        }
        set(base.clone().add(0, height, 0), "minecraft:gold_block");
        set(base.clone().add(0, height + 1, 0), "minecraft:end_rod[facing=up]");
        String[] gifts = {"red_wool", "blue_wool", "lime_wool", "yellow_wool", "magenta_wool", "white_wool"};
        int[][] spots = {{3, 0}, {-3, 1}, {1, 3}, {-2, -3}, {3, -2}, {-1, 3}};
        for (int i = 0; i < spots.length; i++) {
            Location g = base.clone().add(spots[i][0], 0, spots[i][1]);
            if (g.getBlock().getType().isAir()) {
                set(g, "minecraft:" + gifts[i]);
            }
        }
    }

    private void giantPumpkin(Location base) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.pow(dx / 3.4, 2) + Math.pow((dy - 2.4) / 2.8, 2) + Math.pow(dz / 3.4, 2);
                    if (d <= 1) {
                        boolean face = dz == -3 || (dz == -2 && Math.abs(dx) == 3);
                        boolean eye = face && dy == 3 && Math.abs(dx) == 1;
                        boolean mouth = face && dy == 1 && Math.abs(dx) <= 2;
                        set(base.clone().add(dx, dy, dz), eye || mouth ? "minecraft:jack_o_lantern[facing=north]" : "minecraft:pumpkin");
                    }
                }
            }
        }
        set(base.clone().add(0, 6, 0), "minecraft:oak_log");
        set(base.clone().add(0, 7, 0), "minecraft:oak_leaves[persistent=true]");
    }

    // ---- snow ------------------------------------------------------------------------------------------------

    private void snowfall() {
        Location c = plugin.attractionStore().center();
        if (c == null || snowPerTick <= 0 || snow.size() >= snowMax) {
            return;
        }
        World w = c.getWorld();
        int radius = plugin.attractionStore().radius();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < snowPerTick; i++) {
            double a = rnd.nextDouble(Math.PI * 2);
            double r = Math.sqrt(rnd.nextDouble()) * radius;
            int x = c.getBlockX() + (int) Math.round(Math.cos(a) * r);
            int z = c.getBlockZ() + (int) Math.round(Math.sin(a) * r);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            if (top.getY() < c.getBlockY() - 2 || top.getY() > c.getBlockY() + 30) {
                continue;
            }
            Block above = top.getRelative(0, 1, 0);
            Material m = top.getType();
            boolean base = (m.isOccluding() || Tag.LEAVES.isTagged(m)) && m != Material.ICE && m != Material.PACKED_ICE
                    && !Tag.SLABS.isTagged(m);
            if (!base || !above.getType().isAir()) {
                continue;
            }
            above.setBlockData(Bukkit.createBlockData(Material.SNOW), false);
            snow.add(key(above.getLocation()));
            dirty = true;
        }
    }

    /** Footprints: walking through the snow layer removes it (it slowly snows over again). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (applied != Theme.WINTER || !event.hasChangedBlock()) {
            return;
        }
        Block feet = event.getTo().getBlock();
        if (feet.getType() == Material.SNOW) {
            String k = key(feet.getLocation());
            if (snow.remove(k)) {
                BlockData data = feet.getBlockData();
                if (data instanceof Snow s && s.getLayers() <= 1) {
                    feet.setType(Material.AIR, false);
                    feet.getWorld().spawnParticle(Particle.SNOWFLAKE, feet.getLocation().add(0.5, 0.1, 0.5), 3, 0.2, 0, 0.2, 0.01);
                    dirty = true;
                }
            }
        }
    }

    private void particles() {
        if (applied == Theme.NONE) {
            return;
        }
        Location c = plugin.attractionStore().center();
        if (c == null) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Player p : c.getWorld().getPlayers()) {
            if (!plugin.attractionStore().inside(p.getLocation(), 10)) {
                continue;
            }
            if (applied == Theme.WINTER) {
                p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 9, 0), 30, 14, 2, 14, 0.02);
            } else if (applied == Theme.SPRING) {
                p.spawnParticle(Particle.CHERRY_LEAVES, p.getLocation().add(0, 6, 0), 6, 10, 2, 10, 0);
            } else if (applied == Theme.SUMMER) {
                p.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, p.getLocation().add(0, 6, 0), 3, 10, 2, 10, 0);
            } else if (applied == Theme.AUTUMN) {
                p.spawnParticle(Particle.TINTED_LEAVES, p.getLocation().add(0, 7, 0), 8, 10, 2, 10, 0, org.bukkit.Color.fromRGB(0xE07A1F));
            } else if (applied == Theme.VALENTINE) {
                if (rnd.nextInt(3) == 0) {
                    p.spawnParticle(Particle.HEART, p.getLocation().add(rnd.nextDouble(-8, 8), 2.5, rnd.nextDouble(-8, 8)), 1, 0.2, 0.2, 0.2, 0);
                }
            } else if (applied == Theme.STARS || applied == Theme.NEWYEAR) {
                p.spawnParticle(Particle.END_ROD, p.getLocation().add(rnd.nextDouble(-14, 14), 14, rnd.nextDouble(-14, 14)), 1, 0, 0, 0, 0.05);
            } else if (applied == Theme.HALLOWEEN) {
                for (int i = 0; i < 3; i++) {
                    Location fog = p.getLocation().add(rnd.nextDouble(-10, 10), 0.2, rnd.nextDouble(-10, 10));
                    p.spawnParticle(Particle.WHITE_SMOKE, fog, 2, 0.8, 0.05, 0.8, 0.002);
                }
                if (rnd.nextInt(8) == 0) {
                    p.spawnParticle(Particle.SOUL, p.getLocation().add(rnd.nextDouble(-6, 6), 1.5, rnd.nextDouble(-6, 6)), 1, 0.2, 0.3, 0.2, 0.01);
                }
            }
        }
    }

    private void sounds() {
        if (System.currentTimeMillis() < nextSound) {
            return;
        }
        nextSound = System.currentTimeMillis() + soundMinutes * 60_000L + ThreadLocalRandom.current().nextLong(60_000);
        Location c = plugin.attractionStore().center();
        if (c == null) {
            return;
        }
        for (Player p : c.getWorld().getPlayers()) {
            if (!plugin.attractionStore().inside(p.getLocation(), 0)) {
                continue;
            }
            if (applied == Theme.WINTER) {
                MusicService.sleighBells(plugin, p);
                plugin.tasks().later(30, "hohoho", () -> {
                    if (p.isOnline()) {
                        p.playSound(p.getLocation(), "mythoscraft:season.hohoho", org.bukkit.SoundCategory.AMBIENT, 0.9f, 1f);
                    }
                });
            } else if (applied == Theme.HALLOWEEN) {
                SoundSpec.of("entity.witch.celebrate", 0.6f, 0.8f).play(p);
                bats(p.getLocation());
            } else if (applied == Theme.NEWYEAR || applied == Theme.STARS) {
                fireworks(p.getLocation());
            }
        }
    }

    /** A few fireworks high above the player (they explode far up, no damage). */
    private void fireworks(Location near) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            plugin.tasks().later(1 + i * 15, "season firework", () -> {
                Location at = near.clone().add(rnd.nextDouble(-12, 12), 4, rnd.nextDouble(-12, 12));
                near.getWorld().spawn(at, org.bukkit.entity.Firework.class, fw -> {
                    org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
                    org.bukkit.FireworkEffect.Type[] types = org.bukkit.FireworkEffect.Type.values();
                    meta.addEffect(org.bukkit.FireworkEffect.builder().with(types[rnd.nextInt(types.length)])
                            .withColor(org.bukkit.Color.fromRGB(rnd.nextInt(0xFFFFFF))).withFade(org.bukkit.Color.WHITE).flicker(true).build());
                    meta.setPower(2);
                    fw.setFireworkMeta(meta);
                    fw.setPersistent(false);
                    fw.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "season_firework");
                });
            });
        }
    }

    private void bats(Location near) {
        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Location at = near.clone().add(ThreadLocalRandom.current().nextDouble(-4, 4), 3, ThreadLocalRandom.current().nextDouble(-4, 4));
            bats.add(near.getWorld().spawn(at, Bat.class, b -> {
                b.setPersistent(false);
                b.setInvulnerable(true);
                b.setAwake(true);
                b.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "season_bat");
            }));
        }
        plugin.tasks().later(20 * 12, "remove bats", () -> bats.forEach(b -> {
            if (b.isValid()) {
                b.getWorld().spawnParticle(Particle.SMOKE, b.getLocation(), 5, 0.1, 0.1, 0.1, 0.01);
                b.remove();
            }
        }));
    }

    // ---- removal / saving ------------------------------------------------------------------------------------

    /** Restores every decoration block and removes all snow. */
    public void removeAll() {
        removeBanner();
        for (String k : snow) {
            Block b = block(k);
            if (b != null && b.getType() == Material.SNOW) {
                b.setType(Material.AIR, false);
            }
        }
        snow.clear();
        List<Map.Entry<String, String>> list = new ArrayList<>(placed.entrySet());
        java.util.Collections.reverse(list);
        for (Map.Entry<String, String> e : list) {
            Block b = block(e.getKey());
            if (b != null) {
                try {
                    b.setBlockData(Bukkit.createBlockData(e.getValue()), false);
                } catch (IllegalArgumentException ex) {
                    b.setType(Material.AIR, false);
                }
            }
        }
        placed.clear();
        removeWebs();
        doneMarkers.clear();
        centerpieceDone = false;
        dirty = true;
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("theme", applied.name());
        List<String> list = new ArrayList<>();
        placed.forEach((k, v) -> list.add(k + "|" + v));
        yaml.set("placed", list);
        yaml.set("markers", new ArrayList<>(doneMarkers));
        yaml.set("snow", new ArrayList<>(snow));
        yaml.set("centerpiece", centerpieceDone);
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
    }

    /** A new spawn was built: the old decoration positions are gone. */
    public void reset() {
        placed.clear();
        snow.clear();
        doneMarkers.clear();
        centerpieceDone = false;
        dirty = true;
        saveIfDirty();
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        removeBanner();
        dirty = true;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("theme", applied.name());
        List<String> list = new ArrayList<>();
        placed.forEach((k, v) -> list.add(k + "|" + v));
        yaml.set("placed", list);
        yaml.set("markers", new ArrayList<>(doneMarkers));
        yaml.set("snow", new ArrayList<>(snow));
        yaml.set("centerpiece", centerpieceDone);
        store.replace(yaml);
        store.saveNow();
    }

    static String describe(Location l) {
        return LocationCodec.describe(l);
    }
}
