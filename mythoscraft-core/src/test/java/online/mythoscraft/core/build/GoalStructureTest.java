package online.mythoscraft.core.build;

import online.mythoscraft.core.tournament.TournamentTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arena placement far away in the sky, season markers on spawn and arenas. */
class GoalStructureTest {

    @Test
    void arenasAreAtLeast10000BlocksAwayAndTouchTheBuildLimit() {
        int maxHeight = 320;
        TournamentTier[] tiers = TournamentTier.values();
        for (int i = 0; i < tiers.length; i++) {
            int[] o = BuildService.arenaOrigin(100, -50, maxHeight, tiers[i], i);
            double dist = Math.hypot(o[0] - 100, o[2] + 50);
            assertTrue(dist >= 10_000, tiers[i] + " only " + dist + " blocks away");
            BlockPlan plan = ArenaStructure.generate(tiers[i]);
            assertEquals(maxHeight - 2, o[1] + plan.maxY(), tiers[i] + ": highest block must be one below the build limit");
            assertTrue(o[1] + plan.minY() > -64, tiers[i] + " reaches below the world");
        }
    }

    @Test
    void arenasLookBetterAndHaveSeasonSpots() {
        for (TournamentTier t : TournamentTier.values()) {
            BlockPlan p = ArenaStructure.generate(t);
            assertTrue(p.markers("deco").size() >= 10, t + " season spots: " + p.markers("deco").size());
            assertTrue(p.minY() < -10, t + " has no floating island underneath");
            assertTrue(p.palette().contains("minecraft:sea_lantern"), t + " glow ring missing");
        }
        assertTrue(ArenaStructure.generate(TournamentTier.MONTHLY).palette().contains("minecraft:quartz_bricks"));
        assertTrue(ArenaStructure.generate(TournamentTier.WEEKLY).palette().contains("minecraft:deepslate_bricks"));
    }

    @Test
    void seasonsChangeMuchOfTheSpawn() {
        BlockPlan p = SpawnStructure.generate();
        assertTrue(p.markers("ground").size() >= 300, "ground spots: " + p.markers("ground").size());
        assertTrue(p.markers("obelisk_accent").size() >= 16, "obelisk accents");
        assertEquals(4, p.markers("arch_top").size());
        assertTrue(p.markers("deco").size() >= 80, "deco spots");
        assertTrue(p.markers("lamp").size() >= 30, "lamps");
    }
}
