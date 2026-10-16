package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The meadows of the spawn follow the season: no grass carpet all year, but snow in winter, sand and dune grass in
 * summer, podzol and leaf litter in autumn, soul soil and mycelium for Halloween, petals in spring … Only meadow
 * blocks are repainted (paths, plazas and buildings never), a few thousand columns per tick.
 */
public final class SeasonGroundService {

    /** Every block a meadow can consist of – only these are ever repainted. */
    static final Set<Material> MEADOW = EnumSet.of(Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.ROOTED_DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.MYCELIUM, Material.SOUL_SOIL, Material.SNOW_BLOCK, Material.SAND, Material.PACKED_ICE,
            Material.DIRT_PATH, Material.MUD, Material.PALE_MOSS_BLOCK, Material.CALCITE, Material.DIRT);
    /** Plants we own on top of the meadows (placed by the spawn plan or by us). */
    static final Set<Material> PLANTS = EnumSet.of(Material.SHORT_GRASS, Material.POPPY, Material.CORNFLOWER, Material.ALLIUM,
            Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.LILY_OF_THE_VALLEY, Material.PINK_TULIP, Material.FLOWERING_AZALEA_LEAVES,
            Material.SNOW, Material.DEAD_BUSH, Material.FERN, Material.PINK_PETALS, Material.LEAF_LITTER, Material.SHORT_DRY_GRASS,
            Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.WILDFLOWERS, Material.PALE_MOSS_CARPET, Material.MOSS_CARPET,
            Material.FIREFLY_BUSH, Material.TALL_DRY_GRASS);

    /** Ground blocks with weights and plants with a chance (per mille). */
    record Palette(Material[] ground, int[] weights, Material[] plants, int plantPerMille) {
    }

    private static Palette p(Material[] g, int[] w, Material[] plants, int perMille) {
        return new Palette(g, w, plants, perMille);
    }

    private static final Map<SeasonDecoService.Theme, Palette> PALETTES = Map.of(
            SeasonDecoService.Theme.NONE, p(new Material[]{Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.ROOTED_DIRT, Material.COARSE_DIRT},
                    new int[]{45, 30, 15, 10}, new Material[]{Material.SHORT_GRASS, Material.MOSS_CARPET, Material.OXEYE_DAISY, Material.FERN}, 60),
            SeasonDecoService.Theme.SPRING, p(new Material[]{Material.GRASS_BLOCK, Material.MOSS_BLOCK},
                    new int[]{75, 25}, new Material[]{Material.PINK_PETALS, Material.WILDFLOWERS, Material.PINK_TULIP, Material.AZURE_BLUET, Material.SHORT_GRASS}, 220),
            SeasonDecoService.Theme.SUMMER, p(new Material[]{Material.SAND, Material.GRASS_BLOCK, Material.COARSE_DIRT},
                    new int[]{55, 35, 10}, new Material[]{Material.SHORT_DRY_GRASS, Material.DEAD_BUSH, Material.SHORT_GRASS}, 50),
            SeasonDecoService.Theme.AUTUMN, p(new Material[]{Material.PODZOL, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK},
                    new int[]{35, 25, 20, 20}, new Material[]{Material.LEAF_LITTER, Material.LEAF_LITTER, Material.FERN, Material.BROWN_MUSHROOM}, 260),
            SeasonDecoService.Theme.HALLOWEEN, p(new Material[]{Material.PODZOL, Material.SOUL_SOIL, Material.MYCELIUM, Material.COARSE_DIRT},
                    new int[]{35, 25, 25, 15}, new Material[]{Material.DEAD_BUSH, Material.RED_MUSHROOM, Material.FERN, Material.LEAF_LITTER}, 90),
            SeasonDecoService.Theme.WINTER, p(new Material[]{Material.SNOW_BLOCK, Material.PACKED_ICE, Material.CALCITE},
                    new int[]{85, 5, 10}, new Material[]{Material.SNOW}, 450),
            SeasonDecoService.Theme.NEWYEAR, p(new Material[]{Material.SNOW_BLOCK, Material.CALCITE},
                    new int[]{85, 15}, new Material[]{Material.SNOW}, 300),
            SeasonDecoService.Theme.VALENTINE, p(new Material[]{Material.GRASS_BLOCK, Material.MOSS_BLOCK},
                    new int[]{70, 30}, new Material[]{Material.PINK_PETALS, Material.POPPY, Material.PINK_TULIP}, 250),
            SeasonDecoService.Theme.STARS, p(new Material[]{Material.MOSS_BLOCK, Material.PALE_MOSS_BLOCK, Material.GRASS_BLOCK},
                    new int[]{40, 30, 30}, new Material[]{Material.FIREFLY_BUSH, Material.PALE_MOSS_CARPET, Material.SHORT_GRASS}, 70));

    private final MythosCraft plugin;
    private final File file;
    private boolean enabled = true;
    private int columnsPerTick = 2500;
    private String painted = "";
    private SeasonDecoService.Theme target;
    private int cursor;

    public SeasonGroundService(MythosCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/ground-theme.yml");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.season-ground");
        enabled = cfg.bool("enabled", true);
        columnsPerTick = cfg.integer("columns-per-tick", 2500, 100, 50_000);
        painted = YamlConfiguration.loadConfiguration(file).getString("theme", "");
        target = null;
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 200, 1, "season ground", this::tick);
    }

    /** Repaint everything again (after a spawn build). */
    public void reset() {
        painted = "";
        target = null;
        save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("theme", painted);
        try {
            file.getParentFile().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("ground-theme.yml: " + e.getMessage());
        }
    }

    private void tick() {
        if (!enabled) {
            return;
        }
        Location c = plugin.attractionStore().center();
        if (c == null || !c.isChunkLoaded()) {
            return;
        }
        if (target == null) {
            SeasonDecoService.Theme now = plugin.attractions().deco().theme();
            if (now.name().equals(painted)) {
                return;
            }
            target = now;
            cursor = 0;
        }
        int r = plugin.attractionStore().radius();
        int side = r * 2 + 1;
        int total = side * side;
        World w = c.getWorld();
        int y = c.getBlockY();
        Palette pal = PALETTES.getOrDefault(target, PALETTES.get(SeasonDecoService.Theme.NONE));
        int done = 0;
        while (cursor < total && done < columnsPerTick) {
            int dx = cursor % side - r;
            int dz = cursor / side - r;
            cursor++;
            if (dx * dx + dz * dz > (r - 2) * (r - 2) || dx * dx + dz * dz < 33 * 33) {
                continue;
            }
            int x = c.getBlockX() + dx;
            int z = c.getBlockZ() + dz;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            done++;
            Block ground = w.getBlockAt(x, y, z);
            if (!MEADOW.contains(ground.getType())) {
                continue;
            }
            // stable per column, so a repaint always looks the same
            Random rnd = new Random(x * 341_873_128_712L + z * 132_897_987_541L + target.ordinal());
            ground.setType(pick(pal, rnd), false);
            Block above = ground.getRelative(0, 1, 0);
            if (above.getType().isAir() || PLANTS.contains(above.getType())) {
                Material plant = rnd.nextInt(1000) < pal.plantPerMille() ? pal.plants()[rnd.nextInt(pal.plants().length)] : Material.AIR;
                if (plant == Material.SHORT_DRY_GRASS || plant == Material.DEAD_BUSH) {
                    if (ground.getType() != Material.SAND && ground.getType() != Material.COARSE_DIRT && ground.getType() != Material.PODZOL) {
                        plant = Material.SHORT_GRASS;
                    }
                }
                if (plant == Material.SNOW && ground.getType() == Material.PACKED_ICE) {
                    plant = Material.AIR;
                }
                if (above.getType() != plant) {
                    above.setType(plant, false);
                }
            }
        }
        if (cursor >= total) {
            painted = target.name();
            target = null;
            save();
            plugin.getLogger().info("Spawn meadows repainted for the season theme " + painted);
        }
    }

    private static Material pick(Palette pal, Random rnd) {
        int sum = 0;
        for (int w : pal.weights()) {
            sum += w;
        }
        int roll = rnd.nextInt(sum);
        for (int i = 0; i < pal.ground().length; i++) {
            roll -= pal.weights()[i];
            if (roll < 0) {
                return pal.ground()[i];
            }
        }
        return pal.ground()[0];
    }
}
