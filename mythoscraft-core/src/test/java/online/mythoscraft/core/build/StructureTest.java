package online.mythoscraft.core.build;

import online.mythoscraft.core.tournament.TournamentTier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureTest {

    /** Blocks a player can stand in. */
    private static boolean passable(String block) {
        return block == null || block.endsWith(":air") || block.contains("grass") && !block.contains("grass_block")
                || block.contains("flower") || block.contains("carpet") || block.contains("torch") || block.contains("button")
                || block.contains("pressure_plate") || block.contains("fern");
    }

    private static void assertStandable(BlockPlan p, BlockPlan.Marker m, String what) {
        int x = (int) Math.floor(m.x());
        int y = (int) Math.floor(m.y());
        int z = (int) Math.floor(m.z());
        String floor = p.get(x, y - 1, z);
        assertNotNull(floor, what + " has no floor at " + m);
        assertFalse(passable(floor), what + " floor is " + floor + " at " + m);
        assertTrue(passable(p.get(x, y, z)), what + " feet blocked by " + p.get(x, y, z) + " at " + m);
        assertTrue(passable(p.get(x, y + 1, z)), what + " head blocked by " + p.get(x, y + 1, z) + " at " + m);
    }

    @Test
    void planKeysRoundTrip() {
        int[][] points = {{0, 0, 0}, {-4096, -4096, -4096}, {4095, 4095, 4095}, {-61, 30, 17}};
        for (int[] pt : points) {
            assertArrayEquals(pt, BlockPlan.decode(BlockPlan.key(pt[0], pt[1], pt[2])));
        }
        BlockPlan p = new BlockPlan();
        p.fill(0, 0, 0, 2, 0, 2, "stone");
        p.set(1, 0, 1, "minecraft:gold_block");
        assertEquals(9, p.size());
        assertEquals(List.of("minecraft:stone", "minecraft:gold_block"), p.palette());
        assertEquals("minecraft:gold_block", p.get(1, 0, 1));
        p.setIfEmpty(1, 0, 1, "dirt");
        assertEquals("minecraft:gold_block", p.get(1, 0, 1));
    }

    @Test
    void spawnHasAllMarkersAndStaysInsideItsRadius() {
        BlockPlan p = SpawnStructure.generate();
        assertTrue(p.size() > 20_000, "spawn should be a big build, has " + p.size());
        assertEquals(1, p.markers("spawn").size());
        // crate carousel: 9 pedestals + 1 event pedestal
        assertEquals(9, p.markers("crate").size());
        assertEquals(1, p.markers("crate_event").size());
        assertEquals(1, p.markers("party_center").size());
        assertEquals(1, p.markers("spleef_center").size());
        assertEquals(1, p.markers("range_start").size());
        assertEquals(8, p.markers("target").size());
        assertEquals(3, p.markers("hologram").size());
        assertEquals(stats().length, p.markers("button").size());
        assertEquals(1, p.markers("dropper_start").size());
        assertEquals(1, p.markers("maze_goal").size());
        assertEquals(8, p.markers("jump_pad").size());
        assertEquals(3, p.markers("statue").size());
        assertEquals(1, p.markers("wheel").size());
        assertEquals(1, p.markers("well").size());
        assertEquals(3, p.markers("merchant").size());
        assertEquals(1, p.markers("merchant_wander").size());
        assertEquals(SpawnStructure.RINGS, p.markers("ring").size());
        assertEquals(1, p.markers("elytra_start").size());
        assertEquals(1, p.markers("elytra_lift").size());
        assertEquals(1, p.markers("season_tree").size());
        assertTrue(p.markers("lamp").size() >= 16, "lamps: " + p.markers("lamp").size());
        assertTrue(p.markers("deco").size() >= 60, "deco spots: " + p.markers("deco").size());
        for (String standable : new String[]{"statue", "merchant", "merchant_wander", "elytra_start", "season_tree"}) {
            for (BlockPlan.Marker m : p.markers(standable)) {
                assertStandable(p, m, standable);
            }
        }
        for (BlockPlan.Marker lamp : p.markers("lamp")) {
            assertEquals("minecraft:redstone_lamp", p.get((int) lamp.x(), (int) lamp.y(), (int) lamp.z()), "lamp at " + lamp);
        }
        for (BlockPlan.Marker deco : p.markers("deco")) {
            int x = (int) Math.floor(deco.x());
            int z = (int) Math.floor(deco.z());
            assertTrue(p.get(x, 1, z) == null, "deco spot blocked at " + deco);
            assertTrue(p.get(x, 2, z) == null, "deco spot has no headroom at " + deco);
        }
        for (BlockPlan.Marker ring : p.markers("ring")) {
            // the centre of every ring is free so you can fly through
            assertTrue(p.get((int) Math.floor(ring.x()), (int) Math.floor(ring.y()), (int) Math.floor(ring.z())) == null, "ring blocked " + ring);
        }
        assertStandable(p, p.markers("spawn").get(0), "spawn");
        int limit = SpawnStructure.RADIUS + 1;
        assertTrue(p.minX() >= -limit && p.maxX() <= limit && p.minZ() >= -limit && p.maxZ() <= limit,
                "spawn exceeds its clear radius");
        // relic pedestal, second wheel and the wilderness portal of the newcomer guide
        assertEquals(1, p.markers("wheel2").size());
        assertEquals(1, p.markers("relic_crate").size());
        assertEquals(5, p.markers("rtp_portal").size());
        for (BlockPlan.Marker plate : p.markers("rtp_portal")) {
            assertEquals("minecraft:heavy_weighted_pressure_plate", p.get((int) plate.x(), (int) plate.y(), (int) plate.z()), "portal plate " + plate);
            assertTrue(p.get((int) plate.x(), (int) plate.y() + 1, (int) plate.z()) == null, "portal blocked above " + plate);
        }
        BlockPlan.Marker relic = p.markers("relic_crate").get(0);
        assertNotNull(p.get((int) relic.x(), (int) relic.y() - 1, (int) relic.z()), "relic crate without pedestal");
        assertTrue(passable(p.get((int) relic.x(), (int) relic.y(), (int) relic.z())), "relic crate spot blocked");
        // nothing may stand inside the two wheels (they spin as entities in the air)
        for (String w : new String[]{"wheel", "wheel2"}) {
            BlockPlan.Marker m = p.markers(w).get(0);
            double yaw = Math.toRadians(m.yaw());
            double sx = Math.cos(yaw);
            double sz = Math.sin(yaw);
            for (double a = 0; a < 360; a += 6) {
                for (double r = 0; r <= 6.6; r += 0.5) {
                    double side = Math.cos(Math.toRadians(a)) * r;
                    int x = (int) Math.floor(m.x() + sx * side);
                    int y = (int) Math.floor(m.y() + Math.sin(Math.toRadians(a)) * r);
                    int z = (int) Math.floor(m.z() + sz * side);
                    assertTrue(y < 1 || passable(p.get(x, y, z)), w + " blocked by " + p.get(x, y, z) + " at " + x + " " + y + " " + z);
                }
            }
        }
        for (BlockPlan.Marker crate : p.markers("crate")) {
            int x = (int) Math.floor(crate.x());
            int y = (int) Math.floor(crate.y());
            int z = (int) Math.floor(crate.z());
            assertNotNull(p.get(x, y - 1, z), "crate without pedestal at " + crate);
            assertTrue(passable(p.get(x, y, z)), "crate spot blocked at " + crate);
        }
    }

    private static String[][] stats() {
        return online.mythoscraft.core.stats.ButtonManager.spawnPresets().toArray(new String[0][]);
    }

    @Test
    void arenasFitTheirTierAndEverySpawnIsFree() {
        for (TournamentTier tier : TournamentTier.values()) {
            ArenaStructure.Size size = ArenaStructure.size(tier);
            assertTrue(size.radius() * 2 + 1 >= tier.minArenaSize(), tier + " arena too small");
            BlockPlan p = ArenaStructure.generate(tier);
            List<BlockPlan.Marker> spawns = p.markers("spawn");
            assertEquals(tier.defaultSlots(), spawns.size(), tier + " spawn count");
            Set<String> distinct = new HashSet<>();
            for (BlockPlan.Marker m : spawns) {
                assertStandable(p, m, tier + " spawn");
                assertTrue(Math.hypot(m.x(), m.z()) < size.radius(), tier + " spawn outside the arena " + m);
                assertTrue(distinct.add((int) Math.floor(m.x()) + "," + (int) Math.floor(m.z())), tier + " duplicate spawn " + m);
            }
            assertEquals(1, p.markers("spectator").size());
            assertStandable(p, p.markers("spectator").get(0), tier + " spectator");
            assertEquals(1, p.clears().size());
        }
    }
}
