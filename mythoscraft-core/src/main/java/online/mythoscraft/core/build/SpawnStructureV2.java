package online.mythoscraft.core.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Legacy spawn (version 2, radius 72) – only used to remove it before a rebuild. The MythosCraft SMP spawn (version 2): a large floating island (radius 72). Centre: fountain, 22 block obelisk with a
 * beacon, crate pedestals for 17 crates and the command buttons. Around it four attraction plazas – statue hall (top
 * players), wheel of fortune, parkour spiral and the elytra tower with a ring course around the island – plus the
 * wishing well (north), the market with merchants (east) and the season square (west) at the ends of the paths.
 * Lamps light up at night, decoration spots get pumpkins or presents during the seasons. Origin (0,0,0) is the centre
 * of the floor.
 */
public final class SpawnStructureV2 {

    public static final int RADIUS = 72;
    /** Version of the layout (data/builds.yml) – used to remove the right old spawn on a rebuild. */
    public static final int VERSION = 2;
    /** Number of crate pedestals (all crates of the plugin fit). */
    public static final int PEDESTALS = 17;
    /** Number of rings of the elytra course. */
    public static final int RINGS = 11;

    static final int PK_X = -38;
    static final int PK_Z = -38;
    static final int PK_R = 5;
    static final int PK_STEPS = 24;

    static final int[] STATUES = {36, 36};
    static final int[] WHEEL = {-36, 36};
    static final int[] TOWER = {38, -38};
    static final int TOWER_TOP = 40;
    static final int RING_RADIUS = 64;
    static final int[] WELL = {0, -52};
    static final int[] MARKET = {52, 0};
    static final int[] SEASON = {-52, 0};

    private SpawnStructureV2() {
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
        garden(p, rnd);
        statueHall(p);
        wheelPlaza(p);
        parkour(p);
        tower(p);
        rings(p);
        well(p);
        market(p);
        seasonSquare(p);
        lamps(p);
        rim(p);
        p.marker("spawn", 0.5, 1, 23.5, 180f);
        p.marker("hologram", -9.5, 4.2, 19.5, 0f);
        p.marker("hologram", 10.5, 4.2, 19.5, 0f);
        p.marker("hologram", 0.5, 4.6, 27.5, 0f);
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(i * 36 + 18);
            p.marker("button", Math.cos(a) * 12.5 + 0.5, 2.3, Math.sin(a) * 12.5 + 0.5, 0f);
        }
        p.marker("button", PK_X + PK_R + 2.5, 2.6, PK_Z + 0.5, 0f);
        p.marker("button", TOWER[0] - 5.5, 2.6, TOWER[1] + 5.5, 0f);
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
                int depth = 4 + (int) ((1 - t * t) * 32) + rnd.nextInt(3);
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
        boolean diagonal = d >= 31 && d <= 44 && Math.abs(Math.abs(x) - Math.abs(z)) <= 2;
        return axis || diagonal;
    }

    private static void floor(BlockPlan p, Random rnd) {
        int[][] bigPlazas = {STATUES, WHEEL, {PK_X, PK_Z}, TOWER};
        int[][] smallPlazas = {WELL, MARKET, SEASON};
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
                            block = Math.abs(x) == 2 || Math.abs(z) == 2 ? "smooth_quartz" : "polished_andesite";
                        } else {
                            block = rnd.nextInt(10) == 0 ? "cracked_deepslate_bricks" : "deepslate_bricks";
                        }
                    } else if (d < 32) {
                        block = "polished_blackstone_bricks";
                    } else if (d < RADIUS - 1) {
                        block = path ? (rnd.nextInt(6) == 0 ? "andesite" : "polished_andesite") : "grass_block";
                    } else {
                        block = "stone_bricks";
                    }
                }
                p.set(x, 0, z, block);
                if (block.equals("grass_block") && d < RADIUS - 2) {
                    int roll = rnd.nextInt(100);
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
                } else if (Math.abs(s) == 2) {
                    p.set(x, 9, z, "lantern[hanging=true]");
                }
            }
        }
    }

    /** Crate pedestals spread over the four arcs between the paths (4 + 4 + 4 + 5). */
    private static void pedestals(BlockPlan p) {
        int[] perArc = {4, 4, 4, 5};
        List<Double> angles = new ArrayList<>();
        for (int arc = 0; arc < 4; arc++) {
            int n = perArc[arc];
            double start = 270 + arc * 90 + 12;
            double span = 66;
            for (int i = 0; i < n; i++) {
                angles.add((start + span * (i + 0.5) / n) % 360);
            }
        }
        for (double a : angles) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(rad) * 24);
            int z = (int) Math.round(Math.sin(rad) * 24);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(x + dx, 1, z + dz, dx == 0 && dz == 0 ? "gold_block" : "polished_blackstone");
                }
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-x, z)) + 180f;
            p.marker("crate", x, 2, z, yaw);
        }
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
        for (int side = -1; side <= 1; side += 2) {
            int px = cx + (int) Math.round(sx * 6 * side);
            int pz = cz + (int) Math.round(sz * 6 * side);
            for (int y = 1; y <= 8; y++) {
                p.set(px, y, pz, y == 8 ? "gold_block" : "polished_blackstone_bricks");
            }
            p.set(px, 9, pz, "lantern");
        }
        for (int s = -6; s <= 6; s++) {
            p.set(cx + (int) Math.round(sx * s), 1, cz + (int) Math.round(sz * s), "polished_blackstone_brick_slab[type=bottom]");
        }
        p.set(cx, 1, cz, "gold_block");
        p.marker("wheel", cx + 0.5, 6.5, cz + 0.5, yaw(fx, fz));
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
            double cy = TOWER_TOP - 3 * k;
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

    private static void garden(BlockPlan p, Random rnd) {
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int x = (int) Math.round(Math.cos(a) * 42);
            int z = (int) Math.round(Math.sin(a) * 42);
            int height = 5 + rnd.nextInt(2);
            for (int y = 1; y <= height; y++) {
                p.set(x, y, z, "cherry_log");
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        double d = Math.sqrt(dx * dx + dy * dy * 2.2 + dz * dz);
                        if (d <= 3.2 && !(dx == 0 && dz == 0 && dy <= 0)) {
                            p.setIfEmpty(x + dx, height + 1 + dy, z + dz, "cherry_leaves[persistent=true]");
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
            lampPost(p, (int) Math.round(Math.cos(a) * 19), (int) Math.round(Math.sin(a) * 19));
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            for (int r = 36; r <= 64; r += 14) {
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
            for (int r = 34; r <= 68; r += 5) {
                for (int side = -1; side <= 1; side += 2) {
                    spots.add(new int[]{d[0] * r + (d[0] == 0 ? side * 3 : 0), d[1] * r + (d[1] == 0 ? side * 3 : 0)});
                }
            }
        }
        int[][] plazas = {STATUES, WHEEL, {PK_X, PK_Z}, TOWER, WELL, MARKET, SEASON};
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
