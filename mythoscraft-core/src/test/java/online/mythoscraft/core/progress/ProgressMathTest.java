package online.mythoscraft.core.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressMathTest {

    @Test
    void levelCurveGrows() {
        assertEquals(80, ProgressMath.xpForNextLevel(1));
        assertTrue(ProgressMath.xpForNextLevel(20) > ProgressMath.xpForNextLevel(10));
    }

    @Test
    void addXpLevelsUpAndCapsAtMax() {
        ProgressMath.Gain g = ProgressMath.addXp(1, 0, 100);
        assertEquals(2, g.level());
        assertEquals(1, g.levelsGained());
        ProgressMath.Gain max = ProgressMath.addXp(ProgressMath.MAX_JOB_LEVEL, 0, 1_000_000);
        assertEquals(ProgressMath.MAX_JOB_LEVEL, max.level());
        assertEquals(0, max.levelsGained());
    }

    @Test
    void gemsAndBonus() {
        assertEquals(3, ProgressMath.gemsForLevel(2));
        assertEquals(10, ProgressMath.gemsForLevel(5));
        assertEquals(20, ProgressMath.gemsForLevel(10));
        assertEquals(30, ProgressMath.gemsForLevel(15));
        // level 5 reached under the old payout (1+1+1+2 = 5 for levels 2..5) -> new 3+3+3+10 = 19 -> 14 owed
        assertEquals(14, ProgressMath.backPay(5));
        assertEquals(0, ProgressMath.backPay(1));
        assertEquals(1.0, ProgressMath.coinMultiplier(1), 1e-9);
    }

    @Test
    void playtimeMilestones() {
        assertEquals(0, ProgressMath.milestones(4 * 3600, 5));
        assertEquals(2, ProgressMath.milestones(10 * 3600 + 5, 5));
    }
}
