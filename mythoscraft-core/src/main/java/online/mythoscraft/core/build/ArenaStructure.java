package online.mythoscraft.core.build;

import online.mythoscraft.core.tournament.TournamentTier;

import java.util.Random;

/**
 * Tournament colosseum: a natural fighting floor, cover (pillars, mounds, ruins, trees), a battlement wall with an
 * invisible barrier ring above it, tiered spectator stands, a spawn ring with one spawn per tournament slot and a
 * spectator point. Daily → 35 blocks across, weekly → 63, monthly → 123 (plus wall and stands).
 */
public final class ArenaStructure {

    public record Size(int radius, int wallHeight, int stands, int slots) {
        public int outer() {
            return radius + 2 + stands;
        }
    }

    private ArenaStructure() {
    }

    public static Size size(TournamentTier tier) {
        return switch (tier) {
            case DAILY -> new Size(17, 8, 3, tier.defaultSlots());
            case WEEKLY -> new Size(31, 10, 5, tier.defaultSlots());
            case MONTHLY -> new Size(61, 12, 7, tier.defaultSlots());
        };
    }

    public static BlockPlan generate(TournamentTier tier) {
        Size s = size(tier);
        BlockPlan p = new BlockPlan();
        Random rnd = new Random(tier.ordinal() * 31L + 7);
        int r = s.radius();
        String[] ground = {"grass_block", "grass_block", "grass_block", "grass_block", "coarse_dirt", "podzol", "moss_block", "rooted_dirt"};
        for (int x = -s.outer(); x <= s.outer(); x++) {
            for (int z = -s.outer(); z <= s.outer(); z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r + 0.5) {
                    p.set(x, 0, z, ground[rnd.nextInt(ground.length)]);
                    p.fill(x, -3, z, x, -1, z, "dirt");
                    p.set(x, -4, z, "stone");
                    if (rnd.nextInt(18) == 0 && d < r - 1) {
                        p.set(x, 1, z, "short_grass");
                    }
                } else if (d <= r + 1.5) {
                    wall(p, x, z, s, rnd);
                } else if (d <= s.outer() + 0.5) {
                    int ring = (int) Math.floor(d - (r + 1.5));
                    int top = s.wallHeight() + Math.min(ring, s.stands() - 1);
                    for (int y = -4; y < top; y++) {
                        p.set(x, y, z, y < 0 ? "stone" : "stone_bricks");
                    }
                    p.set(x, top, z, "smooth_stone");
                    if (d > s.outer() - 0.5) {
                        p.set(x, top + 1, z, "stone_brick_wall");
                    }
                }
            }
        }
        cover(p, s, rnd, tier);
        int count = s.slots();
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            double x = Math.cos(a) * (r - 3) + 0.5;
            double z = Math.sin(a) * (r - 3) + 0.5;
            // face the centre: yaw = atan2(-dx, dz) with (dx, dz) pointing from the spawn to the centre
            p.marker("spawn", x, 1, z, (float) Math.toDegrees(Math.atan2(x, -z)));
        }
        int standMid = r + 2 + s.stands() / 2;
        p.marker("spectator", 0.5, s.wallHeight() + s.stands() / 2 + 1, standMid + 0.5, 180f);
        glowRing(p, s);
        standLights(p, s);
        island(p, s, rnd);
        palette(p, tier);
        p.clear(s.outer() + 1, 1, s.wallHeight() + 24);
        return p;
    }

    /** Glowing ring around the fighting floor. */
    private static void glowRing(BlockPlan p, Size s) {
        int r = s.radius();
        for (int deg = 0; deg < 360; deg += 2) {
            double a = Math.toRadians(deg);
            int x = (int) Math.round(Math.cos(a) * r);
            int z = (int) Math.round(Math.sin(a) * r);
            if (p.has(x, 0, z) && !p.has(x, 1, z)) {
                p.set(x, 0, z, deg % 10 == 0 ? "sea_lantern" : "polished_blackstone_bricks");
            }
        }
    }

    /** Lanterns and season decoration spots on the top row of the stands. */
    private static void standLights(BlockPlan p, Size s) {
        int top = s.wallHeight() + s.stands() - 1;
        double ring = s.outer() - 1;
        for (int deg = 0; deg < 360; deg += 6) {
            double a = Math.toRadians(deg);
            int x = (int) Math.round(Math.cos(a) * ring);
            int z = (int) Math.round(Math.sin(a) * ring);
            if (p.has(x, top, z) && !p.has(x, top + 1, z)) {
                if (deg % 24 == 0) {
                    p.set(x, top + 1, z, "lantern");
                } else if (deg % 12 == 0) {
                    p.marker("deco", x, top + 1, z, 0f);
                }
            }
        }
    }

    /** The arena floats: a rocky island grows underneath it. */
    private static void island(BlockPlan p, Size s, Random rnd) {
        int outer = s.outer();
        String[] rock = {"stone", "andesite", "tuff", "deepslate", "cobbled_deepslate"};
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > outer + 0.5) {
                    continue;
                }
                double t = d / (outer + 1);
                int depth = 6 + (int) ((1 - t * t) * (8 + outer * 0.35)) + rnd.nextInt(3);
                for (int y = -5; y >= -depth; y--) {
                    String b = rnd.nextInt(150) == 0 ? "glowstone" : rnd.nextInt(120) == 0 ? "amethyst_block" : rock[rnd.nextInt(rock.length)];
                    p.set(x, y, z, b);
                }
                if (rnd.nextInt(25) == 0) {
                    p.set(x, -depth - 1, z, "pointed_dripstone[vertical_direction=down,thickness=tip]");
                }
            }
        }
    }

    /** Every tier has its own look: daily = stone, weekly = deepslate & gold, monthly = quartz & purpur. */
    private static void palette(BlockPlan p, TournamentTier tier) {
        if (tier == TournamentTier.DAILY) {
            return;
        }
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (tier == TournamentTier.WEEKLY) {
            map.put("minecraft:stone_bricks", "minecraft:deepslate_bricks");
            map.put("minecraft:mossy_stone_bricks", "minecraft:deepslate_tiles");
            map.put("minecraft:cracked_stone_bricks", "minecraft:cracked_deepslate_bricks");
            map.put("minecraft:chiseled_stone_bricks", "minecraft:gilded_blackstone");
            map.put("minecraft:smooth_stone", "minecraft:polished_deepslate");
            map.put("minecraft:stone_brick_wall", "minecraft:deepslate_brick_wall");
        } else {
            map.put("minecraft:stone_bricks", "minecraft:quartz_bricks");
            map.put("minecraft:mossy_stone_bricks", "minecraft:smooth_quartz");
            map.put("minecraft:cracked_stone_bricks", "minecraft:chiseled_quartz_block");
            map.put("minecraft:chiseled_stone_bricks", "minecraft:purpur_pillar");
            map.put("minecraft:smooth_stone", "minecraft:smooth_quartz");
            map.put("minecraft:stone_brick_wall", "minecraft:end_stone_brick_wall");
            map.put("minecraft:polished_blackstone_bricks", "minecraft:purpur_block");
        }
        for (int[] e : p.entries()) {
            String b = p.get(e[0], e[1], e[2]);
            String to = b == null ? null : map.get(b);
            if (to != null) {
                p.set(e[0], e[1], e[2], to);
            }
        }
    }

    private static void wall(BlockPlan p, int x, int z, Size s, Random rnd) {
        double angle = Math.toDegrees(Math.atan2(z, x));
        boolean pillar = Math.abs(((angle % 12) + 12) % 12 - 6) > 5.2;
        for (int y = -4; y <= s.wallHeight(); y++) {
            String block;
            if (y < 0) {
                block = "stone";
            } else if (pillar) {
                block = y == 3 ? "sea_lantern" : "chiseled_stone_bricks";
            } else {
                int roll = rnd.nextInt(100);
                block = roll < 15 ? "mossy_stone_bricks" : roll < 28 ? "cracked_stone_bricks" : "stone_bricks";
            }
            p.set(x, y, z, block);
        }
        if (((int) Math.round(angle)) % 4 == 0 || pillar) {
            p.set(x, s.wallHeight() + 1, z, pillar ? "lantern" : "stone_brick_wall");
        }
        for (int y = s.wallHeight() + 2; y <= s.wallHeight() + 24; y++) {
            p.set(x, y, z, "barrier");
        }
    }

    private static void cover(BlockPlan p, Size s, Random rnd, TournamentTier tier) {
        int r = s.radius();
        int pillars = tier == TournamentTier.DAILY ? 4 : tier == TournamentTier.WEEKLY ? 8 : 12;
        for (int i = 0; i < pillars; i++) {
            double a = Math.PI * 2 * i / pillars + 0.3;
            int cx = (int) Math.round(Math.cos(a) * r * 0.45);
            int cz = (int) Math.round(Math.sin(a) * r * 0.45);
            int height = tier == TournamentTier.DAILY ? 4 : 2 + rnd.nextInt(5);
            for (int y = 1; y <= height; y++) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        boolean broken = y == height && rnd.nextBoolean();
                        if (!broken) {
                            p.set(cx + dx, y, cz + dz, rnd.nextInt(5) == 0 ? "mossy_stone_bricks" : "stone_bricks");
                        }
                    }
                }
            }
        }
        if (tier == TournamentTier.DAILY) {
            return;
        }
        int mounds = tier == TournamentTier.WEEKLY ? 4 : 8;
        for (int i = 0; i < mounds; i++) {
            double a = Math.PI * 2 * i / mounds + 0.8;
            int cx = (int) Math.round(Math.cos(a) * r * 0.72);
            int cz = (int) Math.round(Math.sin(a) * r * 0.72);
            int rad = 3 + rnd.nextInt(2);
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    int h = (int) Math.floor(Math.sqrt(Math.max(0, rad * rad - dx * dx - dz * dz)) * 0.6);
                    for (int y = 1; y <= h; y++) {
                        p.set(cx + dx, y, cz + dz, y == h ? "grass_block" : "dirt");
                    }
                }
            }
        }
        if (tier != TournamentTier.MONTHLY) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + 0.5;
            int cx = (int) Math.round(Math.cos(a) * r * 0.86);
            int cz = (int) Math.round(Math.sin(a) * r * 0.86);
            boolean alongX = i % 2 == 0;
            for (int k = -3; k <= 3; k++) {
                if (k == 0) {
                    continue;
                }
                int x = alongX ? cx + k : cx;
                int z = alongX ? cz : cz + k;
                int h = 1 + rnd.nextInt(3);
                for (int y = 1; y <= h; y++) {
                    p.set(x, y, z, rnd.nextInt(3) == 0 ? "cracked_stone_bricks" : "stone_bricks");
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + 1.1;
            int cx = (int) Math.round(Math.cos(a) * r * 0.6);
            int cz = (int) Math.round(Math.sin(a) * r * 0.6);
            for (int y = 1; y <= 5; y++) {
                p.set(cx, y, cz, "oak_log");
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 4; dy <= 7; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 5) <= 4) {
                            p.setIfEmpty(cx + dx, dy, cz + dz, "oak_leaves[persistent=true]");
                        }
                    }
                }
            }
        }
    }
}
