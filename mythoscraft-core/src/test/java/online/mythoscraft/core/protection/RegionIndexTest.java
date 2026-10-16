package online.mythoscraft.core.protection;

import online.mythoscraft.core.util.Cuboid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionIndexTest {

    private static Region region(String name, int priority, int x1, int z1, int x2, int z2) {
        return new Region(name, Region.Type.REGION, "world", Cuboid.of(x1, -64, z1, x2, 319, z2), priority);
    }

    @Test
    void higherPriorityWinsAndUnsetFlagsFallThrough() {
        Region spawn = region("spawn", 10, -50, -50, 50, 50);
        spawn.flag(Flag.PVP, false);
        spawn.flag(Flag.BUILD, false);
        Region arena = new Region("arena:pvp", Region.Type.ARENA, "world", Cuboid.of(10, 60, 10, 20, 80, 20), 100);
        arena.flag(Flag.PVP, true);
        RegionIndex index = RegionIndex.build(List.of(spawn, arena));

        assertEquals(Boolean.FALSE, index.flag("world", 0, 64, 0, Flag.PVP));
        assertEquals(Boolean.TRUE, index.flag("world", 15, 70, 15, Flag.PVP));
        // arena does not set BUILD -> falls through to spawn
        assertEquals(Boolean.FALSE, index.flag("world", 15, 70, 15, Flag.BUILD));
        assertEquals(arena, index.top("world", 15, 70, 15));
        // outside of the arena's height -> spawn again
        assertEquals(spawn, index.top("world", 15, 100, 15));
        assertNull(index.flag("world", 500, 64, 500, Flag.PVP));
        assertNull(index.flag("world_nether", 0, 64, 0, Flag.PVP));
        assertTrue(index.isEmpty("world_nether"));
        assertFalse(index.isEmpty("world"));
    }

    @Test
    void boundariesAreInclusiveAcrossCells() {
        Region r = region("edge", 0, 63, 63, 64, 64);
        r.flag(Flag.BREAK, false);
        RegionIndex index = RegionIndex.build(List.of(r));
        assertEquals(Boolean.FALSE, index.flag("world", 63, 0, 63, Flag.BREAK));
        assertEquals(Boolean.FALSE, index.flag("world", 64, 0, 64, Flag.BREAK));
        assertNull(index.flag("world", 65, 0, 64, Flag.BREAK));
        assertNull(index.flag("world", 62, 0, 63, Flag.BREAK));
    }

    @Test
    void negativeCoordinatesWork() {
        Region r = region("neg", 0, -130, -130, -120, -120);
        r.flag(Flag.FIRE, false);
        RegionIndex index = RegionIndex.build(List.of(r));
        assertEquals(Boolean.FALSE, index.flag("world", -125, 10, -125, Flag.FIRE));
        assertNull(index.flag("world", -119, 10, -125, Flag.FIRE));
    }

    @Test
    void hugeRegionsUseTheLargeList() {
        Region world = region("everything", 0, -30_000_000, -30_000_000, 30_000_000, 30_000_000);
        world.flag(Flag.MOB_SPAWNING, false);
        Region small = region("small", 5, 0, 0, 10, 10);
        small.flag(Flag.MOB_SPAWNING, true);
        RegionIndex index = RegionIndex.build(List.of(world, small));
        assertEquals(Boolean.FALSE, index.flag("world", 1_000_000, 64, -2_000_000, Flag.MOB_SPAWNING));
        assertEquals(Boolean.TRUE, index.flag("world", 5, 64, 5, Flag.MOB_SPAWNING));
        assertEquals(2, index.at("world", 5, 64, 5).size());
        assertEquals("small", index.at("world", 5, 64, 5).get(0).name());
    }

    @Test
    void cuboidMath() {
        Cuboid c = Cuboid.of(5, 10, 5, -5, 0, -5);
        assertEquals(-5, c.minX());
        assertEquals(11L * 11 * 11, c.volume());
        assertTrue(c.contains(0, 5, 0));
        assertFalse(c.contains(6, 5, 0));
        assertTrue(c.intersects(Cuboid.of(5, 10, 5, 8, 12, 8)));
        assertFalse(c.intersects(Cuboid.of(6, 0, 6, 8, 2, 8)));
    }
}
