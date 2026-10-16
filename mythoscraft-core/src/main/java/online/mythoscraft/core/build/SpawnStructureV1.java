package online.mythoscraft.core.build;

import java.util.Random;

/**
 * Legacy spawn (version 1, radius 46) – only used to remove an old spawn before a rebuild. The old SMP spawn: a floating island (radius 46) with a radial plaza, a 22 block obelisk with a purple
 * beacon beam above a sea-lantern fountain, floating amethyst crystals, four grand arches, a lamp ring, crate
 * pedestals for all nine crates, a cherry garden and a carved stone rim. Origin (0,0,0) is the centre of the floor.
 */
public final class SpawnStructureV1 {

    public static final int RADIUS = 46;

    private SpawnStructureV1() {
    }

    public static BlockPlan generate() {
        BlockPlan p = new BlockPlan();
        Random rnd = new Random(641);
        island(p, rnd);
        floor(p, rnd);
        fountain(p);
        obelisk(p);
        arches(p);
        lampRing(p);
        pedestals(p);
        garden(p, rnd);
        rim(p);
        p.marker("spawn", 0.5, 1, 23.5, 180f);
        p.marker("hologram", -9.5, 4.2, 19.5, 0f);
        p.marker("hologram", 10.5, 4.2, 19.5, 0f);
        p.marker("hologram", 0.5, 4.6, 27.5, 0f);
        parkour(p);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            p.marker("button", Math.cos(a) * 12.5 + 0.5, 2.3, Math.sin(a) * 12.5 + 0.5, 0f);
        }
        p.marker("button", PK_X + PK_R + 2.5, 2.6, PK_Z + 0.5, 0f);
        p.clear(RADIUS + 1, 1, 48);
        return p;
    }

    private static double dist(int x, int z) {
        return Math.sqrt(x * x + z * z);
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
                int depth = 4 + (int) ((1 - t * t) * 26) + rnd.nextInt(3);
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

    private static void floor(BlockPlan p, Random rnd) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = dist(x, z);
                if (d > RADIUS) {
                    continue;
                }
                boolean path = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                double angle = Math.toDegrees(Math.atan2(z, x));
                boolean spoke = Math.abs(((angle % 45) + 45) % 45 - 22.5) > 19;
                String block;
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
                } else if (d < 30) {
                    if (path) {
                        block = Math.abs(x) == 2 || Math.abs(z) == 2 ? "smooth_quartz" : "polished_andesite";
                    } else {
                        block = rnd.nextInt(10) == 0 ? "cracked_deepslate_bricks" : "deepslate_bricks";
                    }
                } else if (d < 31) {
                    block = "polished_blackstone_bricks";
                } else if (d < RADIUS - 1) {
                    block = path ? "polished_andesite" : "grass_block";
                } else {
                    block = "stone_bricks";
                }
                p.set(x, 0, z, block);
                if (block.equals("grass_block") && d < RADIUS - 2) {
                    int roll = rnd.nextInt(100);
                    String[] flowers = {"poppy", "cornflower", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "pink_tulip"};
                    if (roll < 10) {
                        p.set(x, 1, z, flowers[rnd.nextInt(flowers.length)]);
                    } else if (roll < 13) {
                        p.set(x, 1, z, "flowering_azalea_leaves[persistent=true]");
                    } else if (roll < 22) {
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
        p.set(-1, 22, -1, "end_rod[facing=up]");
        p.set(1, 22, -1, "end_rod[facing=up]");
        p.set(-1, 22, 1, "end_rod[facing=up]");
        p.set(1, 22, 1, "end_rod[facing=up]");
        // floating crystal ring
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
                int bx = d[0] * 30 + (d[0] == 0 ? side : 0);
                int bz = d[1] * 30 + (d[1] == 0 ? side : 0);
                for (int y = 1; y <= 9; y++) {
                    p.set(bx, y, bz, y == 5 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
                }
                p.set(bx, 0, bz, "polished_blackstone_bricks");
            }
            for (int s = -4; s <= 4; s++) {
                int x = d[0] * 30 + (d[0] == 0 ? s : 0);
                int z = d[1] * 30 + (d[1] == 0 ? s : 0);
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

    private static void lampRing(BlockPlan p) {
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int x = (int) Math.round(Math.cos(a) * 20);
            int z = (int) Math.round(Math.sin(a) * 20);
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, z, "deepslate_brick_wall");
            }
            p.set(x, 4, z, "lantern");
        }
    }

    /** Number of crate pedestals (all crates of the plugin fit). */
    public static final int PEDESTALS = 17;

    /** Crate pedestals spread over the four arcs between the paths (4 + 4 + 4 + 5). */
    private static void pedestals(BlockPlan p) {
        int[] perArc = {4, 4, 4, 5};
        java.util.List<Double> angles = new java.util.ArrayList<>();
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

    static final int PK_X = -27;
    static final int PK_Z = -27;
    static final int PK_R = 5;
    static final int PK_STEPS = 24;

    /**
     * Parkour spiral in the south-west of the island: 24 single-block jumps around a glowing pillar, checkpoints after
     * 8 and 16 jumps, finish platform with a heavy pressure plate on top.
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
            int y = i;
            boolean checkpoint = i == 8 || i == 16;
            if (checkpoint) {
                p.set(x, y, z, "emerald_block");
                p.set(x, y + 1, z, "light_weighted_pressure_plate");
                p.marker("parkour_cp", x, y + 1, z, 0f);
            } else {
                p.set(x, y, z, colors[i % colors.length] + "_concrete");
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

    private static void garden(BlockPlan p, Random rnd) {
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int x = (int) Math.round(Math.cos(a) * 38);
            int z = (int) Math.round(Math.sin(a) * 38);
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

    private static void rim(BlockPlan p) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = dist(x, z);
                if (d >= RADIUS - 1 && d <= RADIUS && !(Math.abs(x) <= 2 || Math.abs(z) <= 2)) {
                    p.set(x, 1, z, (x * 7 + z * 3) % 5 == 0 ? "mossy_stone_bricks" : "stone_bricks");
                    double angle = Math.toDegrees(Math.atan2(z, x));
                    if (Math.abs(((angle % 10) + 10) % 10 - 5) > 4.6) {
                        p.set(x, 2, z, "lantern");
                    }
                }
            }
        }
    }
}
