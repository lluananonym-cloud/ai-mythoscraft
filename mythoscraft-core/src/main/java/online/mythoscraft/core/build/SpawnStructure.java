package online.mythoscraft.core.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The MythosCraft SMP spawn (version 3): a huge floating island (radius 96). Centre: fountain, 22 block obelisk with a
 * beacon, crate pedestals for 17 crates and the command buttons. Around it four attraction plazas – statue hall (top
 * players), wheel of fortune, parkour spiral and the elytra tower with a ring course around the island – plus the
 * wishing well (north), the market with merchants (east) and the season square (west) at the ends of the paths.
 * Lamps light up at night, decoration spots get pumpkins or presents during the seasons. Origin (0,0,0) is the centre
 * of the floor.
 */
public final class SpawnStructure {

    public static final int RADIUS = 128;
    /** Version of the layout (data/builds.yml) – used to remove the right old spawn on a rebuild. */
    public static final int VERSION = 11;
    /** Number of crate pedestals (all crates of the plugin fit). */
    public static final int PEDESTALS = 22;
    /** Number of rings of the elytra course. */
    public static final int RINGS = 11;

    static final int PK_X = -50;
    static final int PK_Z = -50;
    static final int PK_R = 5;
    static final int PK_STEPS = 24;

    static final int[] STATUES = {48, 48};
    static final int[] WHEEL = {-48, 48};
    /** the second (Mythos) wheel stands 17 blocks beside the first one */
    static final int[] WHEEL2 = {-62, 38};
    static final int[] TOWER = {50, -50};
    static final int TOWER_TOP = 56;
    static final int RING_RADIUS = 84;
    static final double RING_DROP = 4.5;
    static final int[] WELL = {0, -70};
    static final int[] MARKET = {70, 0};
    static final int[] SEASON = {-70, 0};
    static final int[] DROPPER = {-28, 62};
    static final int DROPPER_TOP = 46;
    static final int[] MAZE = {-62, -24};
    static final int[] STARTER = {0, 96};
    static final int[] PLAYGROUND = {-92, 62};
    static final int[] PARTY = {92, 62};
    static final int[] SPLEEF = {92, -62};
    static final int[] RANGE = {0, -106};
    static final int GAME_FLOOR = 4;
    /** Ponds on the meadows (x, z, radius) – with a player cannon at the first two. */
    static final int[][] PONDS = {{36, 84, 9}, {-84, -84, 9}, {98, 22, 7}, {-34, -98, 7}, {64, -94, 7}, {-104, 24, 7}, {56, 106, 6}, {-40, 104, 6}};
    static final int MAZE_CELLS = 10;

    private SpawnStructure() {
    }

    public static BlockPlan generate() {
        BlockPlan p = new BlockPlan();
        Random rnd = new Random(641);
        island(p, rnd);
        floor(p, rnd);
        fountain(p);
        obelisk(p);
        arches(p);
        pedestals(p);
        relicPedestal(p);
        garden(p, rnd);
        statueHall(p);
        wheelPlaza(p);
        parkour(p);
        tower(p);
        rings(p);
        well(p);
        market(p);
        seasonSquare(p);
        dropper(p);
        maze(p);
        starterSquare(p);
        playground(p);
        meadowFeatures(p);
        blockParty(p);
        spleef(p);
        targetRange(p);
        jumpPads(p);
        lamps(p);
        rim(p);
        p.marker("spawn", 0.5, 1, 23.5, 180f);
        p.marker("hologram", -16.5, 4.2, 23.5, 0f);
        p.marker("hologram", 17.5, 4.2, 23.5, 0f);
        p.marker("hologram", 0.5, 4.6, 29.5, 0f);
        // buttons spread wide around the centre so their texts never overlap
        for (int i = 0; i < 11; i++) {
            double a = Math.toRadians(i * 360.0 / 11 + 16);
            p.marker("button", Math.cos(a) * 18 + 0.5, 2.3, Math.sin(a) * 18 + 0.5, 0f);
        }
        p.marker("button", PK_X + PK_R + 2.5, 2.6, PK_Z + 0.5, 0f);
        p.marker("button", TOWER[0] - 8.5, 2.6, TOWER[1] + 8.5, 0f);
        p.marker("button", DROPPER[0] + 9.5, 2.6, DROPPER[1] - 9.5, 0f);
        p.marker("button", MAZE[0] + MAZE_CELLS + 3.5, 2.6, MAZE[1] + MAZE_CELLS - 1.5, 0f);
        p.marker("button", PLAYGROUND[0] + 10.5, 2.6, PLAYGROUND[1] - 10.5, 0f);
        p.marker("button", PARTY[0] - 4.5, 2.6, PARTY[1] - 14.5, 0f);
        p.marker("button", SPLEEF[0] - 4.5, 2.6, SPLEEF[1] + 14.5, 0f);
        p.marker("button", RANGE[0] + 6.5, 2.6, RANGE[1] + 11.5, 0f);
        decoSpots(p);
        p.clear(RADIUS + 1, 1, 70);
        return p;
    }

    private static double dist(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /** Yaw (Minecraft) of an entity looking along (dx, dz). */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static boolean inPlaza(int x, int z, int[] c, int r) {
        return dist(x - c[0], z - c[1]) <= r;
    }

    private static void island(BlockPlan p, Random rnd) {
        String[] deep = {"stone", "stone", "andesite", "deepslate", "tuff", "cobbled_deepslate"};
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = dist(x, z);
                if (d > RADIUS) {
                    continue;
                }
                double t = d / RADIUS;
                int depth = 4 + (int) ((1 - t * t) * 38) + rnd.nextInt(3);
                for (int y = -depth; y <= -1; y++) {
                    String block;
                    if (y >= -3) {
                        block = "dirt";
                    } else if (rnd.nextInt(140) == 0) {
                        block = "amethyst_block";
                    } else if (rnd.nextInt(160) == 0) {
                        block = "glowstone";
                    } else {
                        block = deep[rnd.nextInt(deep.length)];
                    }
                    p.set(x, y, z, block);
                }
                if (rnd.nextInt(30) == 0) {
                    p.set(x, -depth - 1, z, "pointed_dripstone[vertical_direction=down,thickness=tip]");
                }
            }
        }
    }

    private static boolean path(int x, int z) {
        double d = dist(x, z);
        boolean axis = Math.abs(x) <= 2 || Math.abs(z) <= 2;
        boolean diagonal = d >= 31 && d <= 60 && Math.abs(Math.abs(x) - Math.abs(z)) <= 2;
        return axis || diagonal;
    }

    private static void floor(BlockPlan p, Random rnd) {
        int[][] bigPlazas = {STATUES, WHEEL, WHEEL2, {PK_X, PK_Z}, TOWER};
        int[][] smallPlazas = {WELL, MARKET, SEASON, STARTER};
        int[][] bigExtra = {PLAYGROUND, PARTY, SPLEEF, RANGE};
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = dist(x, z);
                if (d > RADIUS) {
                    continue;
                }
                boolean path = path(x, z);
                double angle = Math.toDegrees(Math.atan2(z, x));
                boolean spoke = Math.abs(((angle % 45) + 45) % 45 - 22.5) > 19;
                String block = null;
                for (int[] c : bigPlazas) {
                    double pd = dist(x - c[0], z - c[1]);
                    if (pd <= 12) {
                        block = pd > 11 ? "polished_blackstone_bricks" : pd > 9.5 ? "chiseled_deepslate"
                                : (x + z) % 2 == 0 ? "polished_deepslate" : "deepslate_tiles";
                    }
                }
                for (int[] c : bigExtra) {
                    double pd = dist(x - c[0], z - c[1]);
                    if (pd <= 12) {
                        block = pd > 11 ? "polished_blackstone_bricks" : (x + z) % 2 == 0 ? "smooth_quartz" : "polished_diorite";
                    }
                }
                for (int[] c : smallPlazas) {
                    double pd = dist(x - c[0], z - c[1]);
                    if (pd <= 9) {
                        block = pd > 8 ? "polished_blackstone_bricks" : (x + z) % 2 == 0 ? "smooth_quartz" : "polished_diorite";
                    }
                }
                if (block == null) {
                    if (d < 5) {
                        p.set(x, -1, z, "sea_lantern");
                        block = "water";
                    } else if (d < 6.5) {
                        block = "polished_blackstone_bricks";
                    } else if (d < 8) {
                        block = "gilded_blackstone";
                    } else if (d < 15) {
                        block = d >= 11 && d < 12 ? "amethyst_block" : spoke ? "deepslate_tiles" : "polished_deepslate";
                    } else if (d < 16) {
                        block = "chiseled_deepslate";
                    } else if (d < 31) {
                        if (path) {
                            block = Math.abs(x) == 2 || Math.abs(z) == 2 ? "polished_tuff" : "tuff_bricks";
                        } else {
                            block = rnd.nextInt(10) == 0 ? "cracked_deepslate_bricks" : "deepslate_bricks";
                        }
                    } else if (d < 32) {
                        block = "polished_blackstone_bricks";
                    } else if (d < RADIUS - 1) {
                        // meadows: no plain grass carpet – a calm mix; the season repaints them at runtime
                        int m = Math.floorMod((int) (Math.sin(x * 0.21) * 3 + Math.cos(z * 0.17) * 3) + rnd.nextInt(3), 7);
                        block = path ? (Math.abs(x) == 2 || Math.abs(z) == 2 || Math.abs(Math.abs(x) - Math.abs(z)) == 2 ? "polished_tuff"
                                : rnd.nextInt(7) == 0 ? "chiseled_tuff_bricks" : "tuff_bricks")
                                : m < 3 ? "grass_block" : m < 5 ? "moss_block" : m < 6 ? "rooted_dirt" : "coarse_dirt";
                    } else {
                        block = "stone_bricks";
                    }
                }
                p.set(x, 0, z, block);
                if ((block.equals("grass_block") || block.equals("moss_block")) && d < RADIUS - 2) {
                    int roll = rnd.nextInt(100) + 12;
                    String[] flowers = {"poppy", "cornflower", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "pink_tulip"};
                    if (roll < 8) {
                        p.set(x, 1, z, flowers[rnd.nextInt(flowers.length)]);
                    } else if (roll < 10) {
                        p.set(x, 1, z, "flowering_azalea_leaves[persistent=true]");
                    } else if (roll < 18) {
                        p.set(x, 1, z, "short_grass");
                    }
                }
            }
        }
    }

    private static void fountain(BlockPlan p) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = dist(x, z);
                if (d >= 5 && d < 6.5) {
                    double angle = Math.toDegrees(Math.atan2(z, x));
                    boolean post = Math.abs(((angle % 45) + 45) % 45 - 22.5) > 17;
                    p.set(x, 1, z, post ? "chiseled_polished_blackstone" : "polished_blackstone_brick_slab[type=bottom]");
                    if (post) {
                        p.set(x, 2, z, "soul_lantern");
                    }
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                p.set(x, 0, z, "polished_blackstone");
            }
        }
    }

    private static void obelisk(BlockPlan p) {
        p.fill(-1, 1, -1, 1, 2, 1, "polished_blackstone_bricks");
        for (int y = 3; y <= 19; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
                    p.set(x, y, z, corner && y % 3 == 0 ? "crying_obsidian" : "obsidian");
                    if (corner && y % 3 == 0) {
                        p.marker("obelisk_accent", x, y, z, 0f);
                    }
                }
            }
        }
        for (int y = 5; y <= 17; y += 6) {
            p.set(0, y, -2, "end_rod[facing=north]");
            p.set(0, y, 2, "end_rod[facing=south]");
            p.set(-2, y, 0, "end_rod[facing=west]");
            p.set(2, y, 0, "end_rod[facing=east]");
        }
        p.fill(-1, 20, -1, 1, 20, 1, "gold_block");
        p.set(0, 21, 0, "beacon");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    p.set(x, 21, z, Math.abs(x) == 1 && Math.abs(z) == 1 ? "amethyst_block" : "sea_lantern");
                }
            }
        }
        p.set(0, 22, 0, "purple_stained_glass");
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int x = (int) Math.round(Math.cos(a) * 5);
            int z = (int) Math.round(Math.sin(a) * 5);
            int y = 12 + (i % 2) * 2;
            p.set(x, y, z, "amethyst_block");
            p.set(x, y + 1, z, "amethyst_cluster[facing=up]");
            p.set(x, y - 1, z, "end_rod[facing=down]");
        }
    }

    private static void arches(BlockPlan p) {
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : dirs) {
            for (int side = -4; side <= 4; side += 8) {
                int bx = d[0] * 31 + (d[0] == 0 ? side : 0);
                int bz = d[1] * 31 + (d[1] == 0 ? side : 0);
                for (int y = 1; y <= 9; y++) {
                    p.set(bx, y, bz, y == 5 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
                }
            }
            for (int s = -4; s <= 4; s++) {
                int x = d[0] * 31 + (d[0] == 0 ? s : 0);
                int z = d[1] * 31 + (d[1] == 0 ? s : 0);
                p.set(x, 10, z, Math.abs(s) == 4 ? "polished_blackstone_bricks" : "polished_blackstone");
                if (s == 0) {
                    p.set(x, 11, z, "gilded_blackstone");
                    p.set(x, 12, z, "amethyst_cluster[facing=up]");
                    p.marker("arch_top", x, 12, z, 0f);
                } else if (Math.abs(s) == 2) {
                    p.set(x, 9, z, "lantern[hanging=true]");
                }
            }
        }
    }

    /**
     * Crate carousel: 10 pedestals over the four arcs between the paths (3 + 2 + 3 + 2). The first is the event
     * pedestal (Halloween, Frostfest, Silvester crate – only during the event), then 4 fixed and 5 rotating crates.
     */
    private static void pedestals(BlockPlan p) {
        int[] perArc = {3, 2, 3, 2};
        List<Double> angles = new ArrayList<>();
        for (int arc = 0; arc < 4; arc++) {
            int n = perArc[arc];
            double start = 270 + arc * 90 + 12;
            double span = 66;
            for (int i = 0; i < n; i++) {
                angles.add((start + span * (i + 0.5) / n) % 360);
            }
        }
        boolean first = true;
        for (double a : angles) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(rad) * 24);
            int z = (int) Math.round(Math.sin(rad) * 24);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String b = dx == 0 && dz == 0 ? (first ? "crying_obsidian" : "gold_block") : first ? "purpur_block" : "polished_blackstone";
                    p.set(x + dx, 1, z + dz, b);
                }
            }
            // a glowing ring under every carousel pedestal
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        p.set(x + dx, 0, z + dz, first ? "amethyst_block" : "sea_lantern");
                    }
                }
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-x, z)) + 180f;
            p.marker(first ? "crate_event" : "crate", x, 2, z, yaw);
            first = false;
        }
    }

    /**
     * Relic pedestal right in front of the spawn point (between spawn and fountain): a raised gold / crying obsidian
     * platform with four lantern pillars. Relic crates V1–V4 appear here only rarely (at a crate swap).
     */
    private static void relicPedestal(BlockPlan p) {
        int cx = 0;
        int cz = 12;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                p.set(cx + dx, 0, cz + dz, rim ? ((dx + dz) % 2 == 0 ? "amethyst_block" : "gilded_blackstone") : "polished_blackstone");
                p.set(cx + dx, 1, cz + dz, "air");
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.set(cx + dx, 1, cz + dz, dx == 0 && dz == 0 ? "crying_obsidian" : Math.abs(dx) == 1 && Math.abs(dz) == 1 ? "gold_block"
                        : "polished_blackstone_bricks");
            }
        }
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            p.set(cx + c[0], 1, cz + c[1], "chiseled_polished_blackstone");
            p.set(cx + c[0], 2, cz + c[1], "polished_blackstone_wall");
            p.set(cx + c[0], 3, cz + c[1], "soul_lantern");
        }
        p.marker("relic_crate", cx, 2, cz, 0f);
    }

    /** Statue hall: three podiums (1st in the middle and highest) with a colonnade behind. */
    private static void statueHall(BlockPlan p) {
        double fx = -STATUES[0];
        double fz = -STATUES[1];
        double len = dist(fx, fz);
        fx /= len;
        fz /= len;
        double sx = -fz;
        double sz = fx;
        int[] order = {0, 1, -1};
        int[] heights = {4, 3, 2};
        String[] tops = {"gold_block", "iron_block", "copper_block"};
        for (int i = 0; i < 3; i++) {
            int cx = STATUES[0] + (int) Math.round(sx * order[i] * 5);
            int cz = STATUES[1] + (int) Math.round(sz * order[i] * 5);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 1; y < heights[i]; y++) {
                        p.set(cx + dx, y, cz + dz, y == 1 ? "polished_blackstone_bricks" : "quartz_bricks");
                    }
                    p.set(cx + dx, heights[i], cz + dz, dx == 0 && dz == 0 ? tops[i] : "smooth_quartz_slab[type=bottom]");
                }
            }
            p.set(cx + (int) Math.round(fx * 2), 1, cz + (int) Math.round(fz * 2), "lantern");
            p.marker("statue", cx + 0.5, heights[i] + 1, cz + 0.5, yaw(fx, fz));
        }
        // colonnade behind the podiums
        for (int s = -9; s <= 9; s += 3) {
            int cx = STATUES[0] + (int) Math.round(sx * s - fx * 6);
            int cz = STATUES[1] + (int) Math.round(sz * s - fz * 6);
            for (int y = 1; y <= 7; y++) {
                p.set(cx, y, cz, y == 7 ? "chiseled_quartz_block" : "quartz_pillar");
            }
            p.set(cx, 8, cz, "end_rod[facing=up]");
        }
    }

    /** Wheel of fortune: frame and base; the wheel itself is made of item displays. */
    /**
     * Twin wheel plaza: the coin wheel (daily free spin) and the bigger Mythos wheel (gems) side by side, framed by
     * three tall pillars, on a dark brick floor with a gold inlay under each wheel.
     */
    private static void wheelPlaza(BlockPlan p) {
        double fx = -WHEEL[0];
        double fz = -WHEEL[1];
        double len = dist(fx, fz);
        fx /= len;
        fz /= len;
        double sx = -fz;
        double sz = fx;
        int cx = WHEEL[0] - (int) Math.round(fx * 3);
        int cz = WHEEL[1] - (int) Math.round(fz * 3);
        // floor strip under both wheels (side offsets -26 … +10, 7 deep)
        for (int s = -28; s <= 11; s++) {
            for (int d = -4; d <= 3; d++) {
                int x = cx + (int) Math.round(sx * s + fx * d);
                int z = cz + (int) Math.round(sz * s + fz * d);
                boolean edge = s == -28 || s == 11 || d == -4 || d == 3;
                p.set(x, 0, z, edge ? "polished_blackstone_bricks" : (s + d) % 2 == 0 ? "polished_deepslate" : "deepslate_tiles");
                p.set(x, 1, z, "air");
            }
        }
        // on the diagonal every offset is rounded – these keep 8.5 blocks to each wheel centre
        for (int side : new int[]{9, -8, -26}) {
            int px = cx + (int) Math.round(sx * side);
            int pz = cz + (int) Math.round(sz * side);
            for (int y = 1; y <= 15; y++) {
                p.set(px, y, pz, y == 15 ? "gold_block" : y % 4 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
            }
            p.set(px, 16, pz, "lantern");
        }
        for (int[] w : new int[][]{{0, 0}, {-17, 1}}) {
            int wx = cx + (int) Math.round(sx * w[0]);
            int wz = cz + (int) Math.round(sz * w[0]);
            for (int s = -6; s <= 6; s++) {
                p.set(wx + (int) Math.round(sx * s), 1, wz + (int) Math.round(sz * s), "polished_blackstone_brick_slab[type=bottom]");
            }
            p.set(wx, 1, wz, w[1] == 0 ? "gold_block" : "amethyst_block");
            p.marker(w[1] == 0 ? "wheel" : "wheel2", wx + 0.5, 8.5, wz + 0.5, yaw(fx, fz));
        }
    }

    /**
     * Parkour spiral: 24 single-block jumps around a glowing pillar, checkpoints after 8 and 16 jumps, finish platform
     * with a heavy pressure plate on top.
     */
    private static void parkour(BlockPlan p) {
        String[] colors = {"red", "orange", "yellow", "lime", "light_blue", "magenta", "purple", "pink"};
        for (int y = 0; y <= PK_STEPS + 2; y++) {
            p.set(PK_X, y, PK_Z, y % 4 == 0 ? "sea_lantern" : "amethyst_block");
        }
        p.set(PK_X, PK_STEPS + 3, PK_Z, "end_rod[facing=up]");
        int sx = PK_X + PK_R;
        p.set(sx, 1, PK_Z, "light_weighted_pressure_plate");
        p.set(sx, 0, PK_Z, "gold_block");
        p.marker("parkour_start", sx, 1, PK_Z, 0f);
        for (int i = 1; i <= PK_STEPS; i++) {
            double a = Math.toRadians(i * 30);
            int x = PK_X + (int) Math.round(Math.cos(a) * PK_R);
            int z = PK_Z + (int) Math.round(Math.sin(a) * PK_R);
            boolean checkpoint = i == 8 || i == 16;
            if (checkpoint) {
                p.set(x, i, z, "emerald_block");
                p.set(x, i + 1, z, "light_weighted_pressure_plate");
                p.marker("parkour_cp", x, i + 1, z, 0f);
            } else {
                p.set(x, i, z, colors[i % colors.length] + "_concrete");
            }
        }
        double a = Math.toRadians((PK_STEPS + 1) * 30);
        int fx = PK_X + (int) Math.round(Math.cos(a) * PK_R);
        int fz = PK_Z + (int) Math.round(Math.sin(a) * PK_R);
        int fy = PK_STEPS + 1;
        p.fill(fx - 1, fy, fz - 1, fx + 1, fy, fz + 1, "gold_block");
        p.set(fx, fy + 1, fz, "heavy_weighted_pressure_plate");
        p.set(fx - 1, fy + 1, fz - 1, "lantern");
        p.set(fx + 1, fy + 1, fz + 1, "lantern");
        p.marker("parkour_finish", fx, fy + 1, fz, 0f);
    }

    /** Elytra tower: round tower with a launch platform; a pressure plate at the foot brings you up. */
    private static void tower(BlockPlan p) {
        int cx = TOWER[0];
        int cz = TOWER[1];
        for (int y = 1; y <= TOWER_TOP; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = dist(dx, dz);
                    if (d <= 3.4 && d > 2.2) {
                        boolean window = y % 6 == 3 && (dx == 0 || dz == 0);
                        p.set(cx + dx, y, cz + dz, window ? "purple_stained_glass" : y % 8 == 0 ? "chiseled_deepslate" : "deepslate_bricks");
                    } else if (d <= 2.2 && y % 10 == 0) {
                        p.set(cx + dx, y, cz + dz, "glowstone");
                    }
                }
            }
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = dist(dx, dz);
                if (d <= 5.4) {
                    p.set(cx + dx, TOWER_TOP + 1, cz + dz, d > 4.4 ? "polished_blackstone_bricks" : "smooth_quartz");
                    if (d > 4.4) {
                        double ang = Math.toDegrees(Math.atan2(dz, dx));
                        boolean open = Math.abs(ang - 45) < 40;
                        if (!open) {
                            p.set(cx + dx, TOWER_TOP + 2, cz + dz, "polished_blackstone_wall");
                        }
                    }
                }
            }
        }
        p.set(cx, TOWER_TOP + 2, cz, "beacon");
        p.set(cx, TOWER_TOP + 1, cz, "iron_block");
        // flight direction: tangent of the ring circle at the tower (counter-clockwise in angle)
        double a = Math.atan2(cz, cx);
        p.marker("elytra_start", cx + 0.5 + Math.cos(Math.toRadians(45)) * 3, TOWER_TOP + 2, cz + 0.5 + Math.sin(Math.toRadians(45)) * 3,
                yaw(-Math.sin(a), Math.cos(a)));
        int lx = cx - 5;
        int lz = cz + 5;
        p.set(lx, 0, lz, "gold_block");
        p.set(lx, 1, lz, "light_weighted_pressure_plate");
        p.set(lx - 1, 1, lz - 1, "lantern");
        p.set(lx + 1, 1, lz + 1, "lantern");
        p.marker("elytra_lift", lx, 1, lz, 0f);
    }

    /** Ring course around the island (descending), each ring a vertical circle across the flight direction. */
    private static void rings(BlockPlan p) {
        double start = Math.atan2(TOWER[1], TOWER[0]);
        for (int k = 1; k <= RINGS; k++) {
            double a = start + Math.toRadians(30 * k);
            double cx = Math.cos(a) * RING_RADIUS;
            double cz = Math.sin(a) * RING_RADIUS;
            double cy = TOWER_TOP - RING_DROP * k;
            double rx = Math.cos(a);
            double rz = Math.sin(a);
            for (int deg = 0; deg < 360; deg += 8) {
                double t = Math.toRadians(deg);
                double ox = Math.cos(t) * 3.2;
                double oy = Math.sin(t) * 3.2;
                int x = (int) Math.round(cx + rx * ox);
                int y = (int) Math.round(cy + oy);
                int z = (int) Math.round(cz + rz * ox);
                p.set(x, y, z, deg % 48 == 0 ? "sea_lantern" : k == RINGS ? "gold_block" : "lime_stained_glass");
            }
            p.marker("ring", cx + 0.5, cy + 0.5, cz + 0.5, yaw(-Math.sin(a), Math.cos(a)));
        }
    }

    /** Wishing well: round basin with gold glittering on the ground and a small roof. */
    private static void well(BlockPlan p) {
        int cx = WELL[0];
        int cz = WELL[1];
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = dist(dx, dz);
                if (d <= 2.6) {
                    p.set(cx + dx, 0, cz + dz, "water");
                    p.set(cx + dx, -1, cz + dz, "water");
                    p.set(cx + dx, -2, cz + dz, (dx + dz) % 2 == 0 ? "gold_block" : "raw_gold_block");
                } else if (d <= 3.6) {
                    p.set(cx + dx, 1, cz + dz, "stone_bricks");
                    p.set(cx + dx, 2, cz + dz, "stone_brick_slab[type=bottom]");
                }
            }
        }
        int[][] posts = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] q : posts) {
            for (int y = 1; y <= 4; y++) {
                p.set(cx + q[0], y, cz + q[1], "dark_oak_fence");
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int edge = Math.max(Math.abs(dx), Math.abs(dz));
                p.set(cx + dx, 5 + (4 - edge) / 2, cz + dz, edge >= 3 ? "dark_oak_slab[type=bottom]" : "dark_oak_planks");
            }
        }
        p.set(cx, 4, cz, "lantern[hanging=true]");
        p.marker("well", cx + 0.5, 1.2, cz + 0.5, 0f);
    }

    /** Market: three stalls with coloured canopies around the plaza, the wandering merchant stands in the middle. */
    private static void market(BlockPlan p) {
        String[] canopy = {"red", "yellow", "light_blue"};
        double[] angles = {90, 180, 270};
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(angles[i]);
            int cx = MARKET[0] + (int) Math.round(Math.cos(a) * 6);
            int cz = MARKET[1] + (int) Math.round(Math.sin(a) * 6);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(cx + dx, 4, cz + dz, canopy[i] + "_wool");
                }
            }
            int[][] posts = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
            for (int[] q : posts) {
                for (int y = 1; y <= 3; y++) {
                    p.set(cx + q[0], y, cz + q[1], "spruce_fence");
                }
            }
            p.set(cx, 5, cz, "lantern");
            p.marker("merchant", cx + 0.5, 1, cz + 0.5, yaw(MARKET[0] - cx, MARKET[1] - cz));
        }
        p.marker("merchant_wander", MARKET[0] + 0.5, 1, MARKET[1] + 0.5, yaw(-1, 0));
        p.set(MARKET[0] - 2, 1, MARKET[1] - 2, "barrel[facing=up]");
        p.set(MARKET[0] - 2, 1, MARKET[1] + 2, "barrel[facing=up]");
    }

    /** Season square: the place of the season centrepiece (christmas tree, giant pumpkin …). */
    private static void seasonSquare(BlockPlan p) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = dist(dx, dz);
                if (d <= 3.6) {
                    p.set(SEASON[0] + dx, 0, SEASON[1] + dz, "grass_block");
                } else if (d <= 4.6) {
                    p.set(SEASON[0] + dx, 1, SEASON[1] + dz, "mossy_stone_brick_wall");
                }
            }
        }
        p.marker("season_tree", SEASON[0] + 0.5, 1, SEASON[1] + 0.5, 0f);
    }

    /**
     * Dropper tower: a glass tube with a pool at the bottom. The plate at the foot brings you to the top, then you drop
     * down and steer around the platforms into the water.
     */
    private static void dropper(BlockPlan p) {
        int cx = DROPPER[0];
        int cz = DROPPER[1];
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = dist(dx, dz);
                if (d <= 4.4) {
                    for (int y = -4; y <= 0; y++) {
                        p.set(cx + dx, y, cz + dz, y == -4 ? "sea_lantern" : "water");
                    }
                } else if (d <= 5.6) {
                    for (int y = -4; y <= DROPPER_TOP; y++) {
                        boolean frame = y % 8 == 0 || (dx == 0 || dz == 0) && y % 4 == 0;
                        p.set(cx + dx, y, cz + dz, y <= 1 ? "polished_blackstone_bricks"
                                : frame ? "light_blue_concrete" : "light_blue_stained_glass");
                    }
                } else if (d <= 6.6) {
                    p.set(cx + dx, 0, cz + dz, "polished_blackstone_bricks");
                }
            }
        }
        // empty tube first (a rebuild also removes obstacles of older layouts)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dist(dx, dz) <= 4.4) {
                    for (int y = 1; y <= DROPPER_TOP; y++) {
                        p.set(cx + dx, y, cz + dz, "air");
                    }
                }
            }
        }
        // obstacles: 150° discs, every 8 blocks turned by 120° – the gap under the entrance (south) is always free
        String[] colors = {"white", "pink", "yellow", "lime", "cyan", "orange"};
        int level = 0;
        for (int y = DROPPER_TOP - 8; y >= 8; y -= 8) {
            double turn = 180 + level * 120;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double d = dist(dx, dz);
                    double ang = Math.toDegrees(Math.atan2(dz, dx));
                    double rel = ((ang - turn) % 360 + 360) % 360;
                    if (d <= 4.4 && rel < 150) {
                        p.set(cx + dx, y, cz + dz, colors[level % colors.length] + "_concrete");
                    }
                }
            }
            level++;
        }
        // top platform outside the tube
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 6; dz <= 9; dz++) {
                p.set(cx + dx, DROPPER_TOP, cz + dz, "smooth_quartz");
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            p.set(cx + dx, DROPPER_TOP, cz + 5, "smooth_quartz");
            for (int y = DROPPER_TOP + 1; y <= DROPPER_TOP + 2; y++) {
                p.set(cx + dx, y, cz + 5, "air");
            }
        }
        p.marker("dropper_start", cx + 0.5, DROPPER_TOP + 1, cz + 8.5, 180f);
        p.marker("dropper_pool", cx + 0.5, 0, cz + 0.5, 0f);
        int lx = cx + 7;
        int lz = cz - 7;
        p.set(lx, 0, lz, "gold_block");
        p.set(lx, 1, lz, "light_weighted_pressure_plate");
        p.marker("dropper_lift", lx, 1, lz, 0f);
    }

    /** Hedge maze with a golden goal plate in the middle. */
    private static void maze(BlockPlan p) {
        int n = MAZE_CELLS;
        int size = n * 2 + 1;
        boolean[][] open = new boolean[size][size];
        boolean[][] seen = new boolean[n][n];
        Random rnd = new Random(2027);
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{0, 0});
        seen[0][0] = true;
        open[1][1] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            List<int[]> next = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0];
                int nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !seen[nx][nz]) {
                    next.add(new int[]{nx, nz});
                }
            }
            if (next.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] nb = next.get(rnd.nextInt(next.size()));
            seen[nb[0]][nb[1]] = true;
            open[nb[0] * 2 + 1][nb[1] * 2 + 1] = true;
            open[c[0] + nb[0] + 1][c[1] + nb[1] + 1] = true;
            stack.push(nb);
        }
        // entrance at the corner facing the spawn centre (+x side), goal in the middle
        open[size - 1][size - 2] = true;
        int ox = MAZE[0] - n;
        int oz = MAZE[1] - n;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                p.set(ox + x, 0, oz + z, open[x][z] ? "moss_block" : "polished_deepslate");
                if (!open[x][z]) {
                    for (int y = 1; y <= 3; y++) {
                        p.set(ox + x, y, oz + z, "azalea_leaves[persistent=true]");
                    }
                } else {
                    p.set(ox + x, 1, oz + z, "air");
                }
            }
        }
        int gx = ox + n + (n % 2 == 0 ? 1 : 0);
        int gz = oz + n + (n % 2 == 0 ? 1 : 0);
        p.set(gx, 0, gz, "gold_block");
        p.set(gx, 1, gz, "heavy_weighted_pressure_plate");
        p.set(gx, 4, gz, "end_rod[facing=up]");
        p.marker("maze_goal", gx, 1, gz, 0f);
    }

    /** Starter square for new players: the mentor (quests + starter help) and a notice board. */
    private static void starterSquare(BlockPlan p) {
        int cx = STARTER[0];
        int cz = STARTER[1];
        p.set(cx, 1, cz - 3, "lectern[facing=north]");
        for (int dx = -2; dx <= 2; dx++) {
            p.set(cx + dx, 1, cz + 4, "oak_planks");
            p.set(cx + dx, 2, cz + 4, "oak_planks");
            p.set(cx + dx, 3, cz + 4, "spruce_slab[type=bottom]");
        }
        p.set(cx - 3, 1, cz + 4, "oak_fence");
        p.set(cx + 3, 1, cz + 4, "oak_fence");
        p.set(cx - 3, 2, cz + 4, "lantern");
        p.set(cx + 3, 2, cz + 4, "lantern");
        String[] flowers = {"poppy", "dandelion", "cornflower", "allium", "oxeye_daisy"};
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5);
            p.set(cx + (int) Math.round(Math.cos(a) * 7), 1, cz + (int) Math.round(Math.sin(a) * 7), flowers[i % flowers.length]);
        }
        p.marker("mentor", cx + 0.5, 1, cz + 0.5, yaw(0, -1));
        p.marker("starter_board", cx + 0.5, 4.3, cz + 4.5, 0f);
        rtpPortal(p, cx, cz + 12);
    }

    /**
     * Wilderness portal behind the starter square: a tall purpur/crying-obsidian arch, walking over one of the
     * pressure plates inside starts a random teleport (last goal of the newcomer guide).
     */
    private static void rtpPortal(BlockPlan p, int cx, int cz) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean rim = Math.abs(dx) == 4 || Math.abs(dz) == 3;
                p.set(cx + dx, 0, cz + dz, rim ? "polished_blackstone_bricks" : (dx + dz) % 2 == 0 ? "purpur_block" : "amethyst_block");
                p.set(cx + dx, 1, cz + dz, "air");
            }
        }
        for (int y = 1; y <= 8; y++) {
            for (int dx : new int[]{-3, 3}) {
                p.set(cx + dx, y, cz, y % 3 == 0 ? "crying_obsidian" : "purpur_pillar");
                p.set(cx + dx, y, cz - 1, y == 8 ? "purpur_stairs[facing=south]" : "air");
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            p.set(cx + dx, 9, cz, Math.abs(dx) == 3 ? "crying_obsidian" : dx == 0 ? "beacon" : "purpur_block");
            p.set(cx + dx, 10, cz, Math.abs(dx) <= 1 ? "purpur_slab[type=bottom]" : "air");
        }
        p.set(cx, 8, cz, "end_rod[facing=down]");
        for (int dx = -2; dx <= 2; dx++) {
            p.set(cx + dx, 1, cz, "heavy_weighted_pressure_plate");
            p.marker("rtp_portal", cx + dx, 1, cz, 180f);
        }
        p.set(cx - 4, 1, cz - 2, "lantern");
        p.set(cx + 4, 1, cz - 2, "lantern");
    }

    private static boolean nearAnything(int x, int z, double extra) {
        for (int[] c : new int[][]{STATUES, WHEEL, WHEEL2, {PK_X, PK_Z}, TOWER, WELL, MARKET, SEASON, DROPPER, MAZE, STARTER, PLAYGROUND, PARTY, SPLEEF, RANGE}) {
            if (dist(x - c[0], z - c[1]) < 14 + extra) {
                return true;
            }
        }
        return path(x, z);
    }

    /**
     * Less plain meadow: a promenade ring with lanterns, ponds with lily pads and a player cannon, rock gardens,
     * framed flower beds and bouncy giant mushrooms.
     */
    private static void meadowFeatures(BlockPlan p) {
        Random rnd = new Random(1010);
        // promenade ring (r 64–67) with benches and lanterns
        for (int x = -70; x <= 70; x++) {
            for (int z = -70; z <= 70; z++) {
                double d = dist(x, z);
                if (d >= 64 && d < 67.5 && !nearAnything(x, z, -4)) {
                    boolean edge = d < 64.8 || d > 66.7;
                    p.set(x, 0, z, edge ? "polished_tuff" : (x + z) % 3 == 0 ? "tuff_bricks" : "polished_deepslate");
                    if (p.has(x, 1, z)) {
                        p.set(x, 1, z, "air");
                    }
                }
            }
        }
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a + 7.5);
            int x = (int) Math.round(Math.cos(rad) * 68.5);
            int z = (int) Math.round(Math.sin(rad) * 68.5);
            if (nearAnything(x, z, -4)) {
                continue;
            }
            p.set(x, 1, z, "polished_tuff_wall");
            p.set(x, 2, z, "polished_tuff_wall");
            p.set(x, 3, z, a % 30 == 0 ? "lantern[hanging=false]" : "soul_lantern[hanging=false]");
        }
        // ponds
        for (int i = 0; i < PONDS.length; i++) {
            int cx = PONDS[i][0];
            int cz = PONDS[i][1];
            int r = PONDS[i][2];
            for (int dx = -r - 2; dx <= r + 2; dx++) {
                for (int dz = -r - 2; dz <= r + 2; dz++) {
                    double d = dist(dx, dz) + Math.sin(dx * 0.9 + dz * 0.6) * 0.6;
                    int x = cx + dx;
                    int z = cz + dz;
                    if (d <= r) {
                        p.set(x, -2, z, "clay");
                        p.set(x, -1, z, d < r - 2 ? "water" : "sand");
                        p.set(x, 0, z, "water");
                        p.set(x, 1, z, rnd.nextInt(9) == 0 && d < r - 1 ? "lily_pad" : "air");
                    } else if (d <= r + 1.6) {
                        p.set(x, 0, z, rnd.nextInt(3) == 0 ? "mud" : rnd.nextBoolean() ? "sand" : "gravel");
                        p.set(x, 1, z, rnd.nextInt(5) == 0 ? "sugar_cane" : "air");
                        if (rnd.nextInt(5) == 0) {
                            p.set(x, 2, z, "air");
                        }
                    }
                }
            }
            p.set(cx, -1, cz, "sea_lantern");
            if (i < 2) {
                cannon(p, cx, cz, r);
            }
        }
        // rock gardens and flower beds on the free meadow
        int rocks = 0;
        int beds = 0;
        int shrooms = 0;
        for (int tries = 0; tries < 3000 && (rocks < 30 || beds < 24 || shrooms < 10); tries++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = 38 + rnd.nextDouble() * 82;
            int x = (int) Math.round(Math.cos(a) * d);
            int z = (int) Math.round(Math.sin(a) * d);
            if (nearAnything(x, z, 2) || Math.abs(d - 66) < 5) {
                continue;
            }
            boolean pond = false;
            for (int[] c : PONDS) {
                pond |= dist(x - c[0], z - c[1]) < c[2] + 5;
            }
            if (pond || p.has(x, 2, z)) {
                continue;
            }
            int kind = rocks < 30 ? 0 : beds < 24 ? 1 : 2;
            if (kind == 0) {
                rocks++;
                String[] stone = {"mossy_cobblestone", "andesite", "cobblestone", "tuff", "mossy_stone_bricks"};
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        double rr = dist(dx, dz);
                        if (rr <= 2.2) {
                            p.set(x + dx, 0, z + dz, rnd.nextBoolean() ? "coarse_dirt" : "gravel");
                            if (rr < 1.5 || rnd.nextInt(3) == 0) {
                                p.set(x + dx, 1, z + dz, stone[rnd.nextInt(stone.length)]);
                            }
                            if (rr < 0.8) {
                                p.set(x + dx, 2, z + dz, stone[rnd.nextInt(stone.length)]);
                            }
                        }
                    }
                }
                p.set(x, 3, z, "moss_carpet");
            } else if (kind == 1) {
                beds++;
                String[] flowers = {"poppy", "cornflower", "allium", "oxeye_daisy", "orange_tulip", "pink_tulip", "lily_of_the_valley"};
                String f = flowers[rnd.nextInt(flowers.length)];
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean frame = Math.abs(dx) == 2 || Math.abs(dz) == 1;
                        p.set(x + dx, 0, z + dz, frame ? "stripped_spruce_wood" : "rooted_dirt");
                        p.set(x + dx, 1, z + dz, frame ? "air" : f);
                    }
                }
            } else {
                shrooms++;
                // bouncy giant mushroom: a slime stem you can jump on
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z, "mushroom_stem");
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dist(dx, dz) <= 2.3) {
                            p.set(x + dx, 4, z + dz, dist(dx, dz) < 1 ? "slime_block" : (dx + dz) % 2 == 0 ? "red_mushroom_block" : "slime_block");
                        }
                    }
                }
                p.set(x, 5, z, "shroomlight");
            }
        }
    }

    /** Player cannon at a pond: step on the plate and fly into the water. */
    private static void cannon(BlockPlan p, int cx, int cz, int r) {
        // the cannon stands on the side towards the spawn centre and shoots across the pond
        double len = dist(cx, cz);
        double ux = -cx / len;
        double uz = -cz / len;
        int bx = cx + (int) Math.round(ux * (r + 14));
        int bz = cz + (int) Math.round(uz * (r + 14));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.set(bx + dx, 0, bz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "polished_blackstone_bricks" : "iron_block");
            }
        }
        p.set(bx, 1, bz, "heavy_weighted_pressure_plate");
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            p.set(bx + c[0], 1, bz + c[1], "black_concrete");
            p.set(bx + c[0], 2, bz + c[1], "polished_blackstone_button[face=floor]");
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(cx - bx), cz - bz));
        p.marker("cannon", bx, 1, bz, yaw);
        p.marker("cannon_target", cx + 0.5, 1, cz + 0.5, 0f);
    }

    /** Raised game floor over a water pool with stairs up – shared by Block-Party and Spleef. */
    private static void raisedFloor(BlockPlan p, int cx, int cz, int r, boolean round, String exitSide) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = dist(dx, dz);
                boolean inside = round ? d <= r + 0.5 : Math.abs(dx) <= r && Math.abs(dz) <= r;
                boolean rim = !inside && (round ? d <= r + 1.5 : Math.abs(dx) <= r + 1 && Math.abs(dz) <= r + 1);
                if (inside) {
                    p.set(cx + dx, 0, cz + dz, "sea_lantern");
                    p.set(cx + dx, 1, cz + dz, "water");
                } else if (rim) {
                    p.set(cx + dx, 1, cz + dz, "polished_blackstone_bricks");
                    p.set(cx + dx, 2, cz + dz, "polished_blackstone_brick_wall");
                }
            }
        }
        // pillars carrying the floor
        for (int[] c : new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
            if (round && dist(c[0], c[1]) > r + 0.5) {
                c[0] = (int) Math.round(c[0] * 0.7);
                c[1] = (int) Math.round(c[1] * 0.7);
            }
            for (int y = 1; y < GAME_FLOOR; y++) {
                p.set(cx + c[0], y, cz + c[1], "quartz_pillar");
            }
        }
        // stairs up (towards the spawn centre)
        int dir = exitSide.equals("north") ? -1 : 1;
        for (int i = 0; i <= GAME_FLOOR; i++) {
            int z = cz + dir * (r + 2 + GAME_FLOOR - i);
            for (int dx = -1; dx <= 1; dx++) {
                p.set(cx + dx, i, z, i == 0 ? "polished_blackstone_bricks" : dir < 0 ? "quartz_stairs[facing=south]" : "quartz_stairs[facing=north]");
                for (int y = 0; y < i; y++) {
                    p.set(cx + dx, y, z, "quartz_block");
                }
            }
        }
        // bridge from the top step onto the floor
        for (int dx = -1; dx <= 1; dx++) {
            p.set(cx + dx, GAME_FLOOR, cz + dir * (r + 1), "quartz_block");
            p.set(cx + dx, 2, cz + dir * (r + 1), "quartz_block");
        }
    }

    /** Block-Party: a 17×17 colourful floor – stand on the right colour before the others disappear. */
    private static void blockParty(BlockPlan p) {
        int cx = PARTY[0];
        int cz = PARTY[1];
        int r = 8;
        raisedFloor(p, cx, cz, r, false, "north");
        String[] colors = {"red", "orange", "yellow", "lime", "light_blue", "blue", "purple", "pink"};
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int tile = Math.floorMod((dx + 8) / 2 * 3 + (dz + 8) / 2 * 5, colors.length);
                p.set(cx + dx, GAME_FLOOR, cz + dz, colors[tile] + "_concrete");
            }
        }
        // speaker towers in the corners
        for (int[] c : new int[][]{{-r - 3, -r - 3}, {r + 3, -r - 3}, {-r - 3, r + 3}, {r + 3, r + 3}}) {
            for (int y = 1; y <= 6; y++) {
                p.set(cx + c[0], y, cz + c[1], y == 6 ? "jukebox" : y % 2 == 0 ? "note_block" : "black_concrete");
            }
            p.set(cx + c[0], 7, cz + c[1], "end_rod");
        }
        p.marker("party_center", cx + 0.5, GAME_FLOOR + 1, cz + 0.5, 0f);
        p.marker("party_exit", cx + 0.5, 1, cz - r - 9.5, 180f);
    }

    /** Spleef: a round snow floor – dig the snow away under the others, the last one standing wins. */
    private static void spleef(BlockPlan p) {
        int cx = SPLEEF[0];
        int cz = SPLEEF[1];
        int r = 9;
        raisedFloor(p, cx, cz, r, true, "south");
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dist(dx, dz) <= r + 0.5) {
                    p.set(cx + dx, GAME_FLOOR, cz + dz, "snow_block");
                }
            }
        }
        for (int a = 0; a < 360; a += 45) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * (r + 3));
            int z = cz + (int) Math.round(Math.sin(rad) * (r + 3));
            for (int y = 1; y <= 4; y++) {
                p.set(x, y, z, y == 4 ? "sea_lantern" : "packed_ice");
            }
        }
        p.marker("spleef_center", cx + 0.5, GAME_FLOOR + 1, cz + 0.5, 0f);
        p.marker("spleef_exit", cx + 0.5, 1, cz + r + 10.5, 0f);
    }

    /** Target range: shooting stand with a plate, a wall of targets – one target lights up at a time. */
    private static void targetRange(BlockPlan p) {
        int cx = RANGE[0];
        int cz = RANGE[1];
        // shooting stand (south side, towards the spawn)
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = 8; dz <= 10; dz++) {
                p.set(cx + dx, 0, cz + dz, "dark_oak_planks");
            }
            p.set(cx + dx, 1, cz + 7, dx % 2 == 0 ? "dark_oak_fence" : "air");
        }
        p.set(cx, 1, cz + 9, "heavy_weighted_pressure_plate");
        p.marker("range_start", cx, 1, cz + 9, 180f);
        p.marker("range_stand", cx + 0.5, 1, cz + 9.5, 180f);
        // the wall with 8 targets
        for (int dx = -9; dx <= 9; dx++) {
            for (int y = 1; y <= 9; y++) {
                p.set(cx + dx, y, cz - 10, Math.abs(dx) == 9 || y == 9 ? "polished_blackstone_bricks" : "black_concrete");
            }
            p.set(cx + dx, 10, cz - 10, dx % 3 == 0 ? "lantern" : "polished_blackstone_brick_wall");
        }
        int[][] targets = {{-6, 3}, {-3, 6}, {0, 4}, {3, 7}, {6, 3}, {-6, 7}, {6, 6}, {0, 8}};
        for (int[] t : targets) {
            p.set(cx + t[0], t[1], cz - 10, "target");
            p.marker("target", cx + t[0], t[1], cz - 10, 0f);
        }
        // lane markings
        for (int dz = -9; dz <= 6; dz++) {
            p.set(cx - 7, 0, cz + dz, "white_concrete");
            p.set(cx + 7, 0, cz + dz, "white_concrete");
        }
    }

    /** Playground: a big trampoline field (slime blocks) and betting tables for friends. */
    private static void playground(BlockPlan p) {
        int cx = PLAYGROUND[0];
        int cz = PLAYGROUND[1];
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                double d = dist(dx, dz);
                if (d <= 12) {
                    p.set(cx + dx, 0, cz + dz, d > 11 ? "polished_blackstone_bricks" : (dx + dz) % 2 == 0 ? "smooth_quartz" : "polished_diorite");
                }
            }
        }
        // trampoline field
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean frame = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                p.set(cx + dx, 0, cz + dz, frame ? "yellow_concrete" : "slime_block");
                if (frame && (dx + dz) % 2 == 0) {
                    p.set(cx + dx, 1, cz + dz, "yellow_carpet");
                }
            }
        }
        // betting tables
        int[][] tables = {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}};
        for (int[] t : tables) {
            int x = cx + t[0];
            int z = cz + t[1];
            p.set(x, 1, z, "dark_oak_fence");
            p.set(x, 2, z, "green_carpet");
            p.set(x + 1, 1, z, "spruce_stairs[facing=west]");
            p.set(x - 1, 1, z, "spruce_stairs[facing=east]");
            p.set(x, 3, z, "lantern");
        }
    }

    /** Jump pads on the paths: step on them and you are flung outwards (combine with the spawn glide!). */
    private static void jumpPads(BlockPlan p) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            for (int r : new int[]{40, 56}) {
                int x = d[0] * r;
                int z = d[1] * r;
                p.set(x, 0, z, "slime_block");
                p.set(x, 1, z, "light_weighted_pressure_plate");
                p.marker("jump_pad", x, 1, z, yaw(d[0], d[1]));
            }
        }
    }

    private static void garden(BlockPlan p, Random rnd) {
        for (int i = 0; i < 96; i++) {
            boolean outer = i >= 8;
            boolean far = i >= 24;
            boolean edge = i >= 56;
            double a = Math.toRadians(edge ? (i - 56) * 9 + 4.5 : far ? (i - 24) * 11.25 + 5.6 : outer ? (i - 8) * 22.5 + 11.25 : i * 45 + 22.5);
            int r = edge ? 118 : far ? 102 : outer ? 82 : 42;
            int x = (int) Math.round(Math.cos(a) * r);
            int z = (int) Math.round(Math.sin(a) * r);
            boolean blocked = false;
            for (int[] c : new int[][]{STATUES, WHEEL, WHEEL2, {PK_X, PK_Z}, TOWER, WELL, MARKET, SEASON, DROPPER, MAZE, STARTER, PLAYGROUND, PARTY, SPLEEF, RANGE}) {
                blocked |= dist(x - c[0], z - c[1]) < 18;
            }
            for (int[] c : PONDS) {
                blocked |= dist(x - c[0], z - c[1]) < c[2] + 6;
            }
            blocked |= Math.abs(dist(x, z) - 66) < 4;
            if (blocked || path(x, z) || far && i % 2 == 1) {
                continue;
            }
            boolean azalea = i % 3 == 0;
            int height = 5 + rnd.nextInt(2);
            for (int y = 1; y <= height; y++) {
                p.set(x, y, z, azalea ? "oak_log" : "cherry_log");
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        double d = Math.sqrt(dx * dx + dy * dy * 2.2 + dz * dz);
                        if (d <= 3.2 && !(dx == 0 && dz == 0 && dy <= 0)) {
                            p.setIfEmpty(x + dx, height + 1 + dy, z + dz, azalea
                                    ? (rnd.nextInt(4) == 0 ? "flowering_azalea_leaves[persistent=true]" : "azalea_leaves[persistent=true]")
                                    : "cherry_leaves[persistent=true]");
                        }
                    }
                }
            }
        }
    }

    private static void lampPost(BlockPlan p, int x, int z) {
        for (int y = 1; y <= 3; y++) {
            p.set(x, y, z, "deepslate_brick_wall");
        }
        p.set(x, 4, z, "redstone_lamp");
        p.set(x, 5, z, "polished_blackstone_slab[type=bottom]");
        p.marker("lamp", x, 4, z, 0f);
    }

    /** Lamp posts (redstone lamps: lit at night by the plugin) around the centre and along the paths. */
    private static void lamps(BlockPlan p) {
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            lampPost(p, (int) Math.round(Math.cos(a) * 22), (int) Math.round(Math.sin(a) * 22));
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            for (int r = 36; r <= 120; r += 14) {
                for (int side = -1; side <= 1; side += 2) {
                    int x = d[0] * r + (d[0] == 0 ? side * 4 : 0);
                    int z = d[1] * r + (d[1] == 0 ? side * 4 : 0);
                    if (!p.has(x, 1, z) && !p.has(x, 4, z)) {
                        lampPost(p, x, z);
                    }
                }
            }
        }
    }

    /** Free spots next to the paths and around the plazas for season decoration (pumpkins, presents …). */
    private static void decoSpots(BlockPlan p) {
        List<int[]> spots = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            for (int r = 34; r <= 124; r += 5) {
                for (int side = -1; side <= 1; side += 2) {
                    spots.add(new int[]{d[0] * r + (d[0] == 0 ? side * 3 : 0), d[1] * r + (d[1] == 0 ? side * 3 : 0)});
                }
            }
        }
        int[][] plazas = {STATUES, WHEEL, {PK_X, PK_Z}, TOWER, WELL, MARKET, SEASON, DROPPER, STARTER, PLAYGROUND, PARTY, SPLEEF};
        for (int[] c : plazas) {
            int r = c == WELL || c == MARKET || c == SEASON ? 10 : 13;
            for (int deg = 0; deg < 360; deg += 30) {
                double a = Math.toRadians(deg);
                spots.add(new int[]{c[0] + (int) Math.round(Math.cos(a) * r), c[1] + (int) Math.round(Math.sin(a) * r)});
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 + 11.25);
            spots.add(new int[]{(int) Math.round(Math.cos(a) * 17), (int) Math.round(Math.sin(a) * 17)});
        }
        // nothing may stand in front of or under the second wheel
        spots.removeIf(c -> dist(c[0] - WHEEL2[0], c[1] - WHEEL2[1]) < 11);
        // a grid of ground spots on the meadows (season plants: petals, leaf litter, pumpkins …)
        for (int x = -RADIUS; x <= RADIUS; x += 6) {
            for (int z = -RADIUS; z <= RADIUS; z += 6) {
                int gx = x + Math.floorMod(z * 7, 3);
                double d = dist(gx, z);
                if (d > 34 && d < RADIUS - 3 && java.util.Set.of("minecraft:grass_block", "minecraft:moss_block", "minecraft:rooted_dirt", "minecraft:coarse_dirt").contains(p.get(gx, 0, z)) && !p.has(gx, 1, z)) {
                    p.marker("ground", gx, 1, z, 0f);
                }
            }
        }
        for (int[] s : spots) {
            double d = dist(s[0], s[1]);
            if (d < RADIUS - 2 && !p.has(s[0], 1, s[1]) && p.has(s[0], 0, s[1]) && !"water".equals(p.get(s[0], 0, s[1]))) {
                p.marker("deco", s[0], 1, s[1], (s[0] * 31 + s[1] * 17) % 360);
            }
        }
    }

    private static void rim(BlockPlan p) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = dist(x, z);
                if (d >= RADIUS - 1 && d <= RADIUS && !(Math.abs(x) <= 2 || Math.abs(z) <= 2)) {
                    p.set(x, 1, z, (x * 7 + z * 3) % 5 == 0 ? "mossy_stone_bricks" : "stone_bricks");
                    double angle = Math.toDegrees(Math.atan2(z, x));
                    if (Math.abs(((angle % 8) + 8) % 8 - 4) > 3.7) {
                        p.set(x, 2, z, "lantern");
                    }
                }
            }
        }
    }
}
