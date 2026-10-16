package online.mythoscraft.core.progress;

/** Pure formulas of jobs, milestones and parkour (unit tested). */
public final class ProgressMath {

    public static final int MAX_JOB_LEVEL = 50;

    private ProgressMath() {
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static long xpForNextLevel(int level) {
        return Math.round(80 * Math.pow(Math.max(1, level), 1.45));
    }

    /** Gems paid when reaching {@code level}: 3 per level, every 5th level 2 × level (5 → 10, 10 → 20, 15 → 30 …). */
    public static long gemsForLevel(int level) {
        return level % 5 == 0 ? level * 2L : 3;
    }

    /** Old payout (before the new economy) – needed for the back-payment. */
    static long oldGemsForLevel(int level) {
        return 1 + level / 5 + (level % 10 == 0 ? 5 : 0);
    }

    /** Gems still owed for a job level reached under the old payout (never negative). */
    public static long backPay(int level) {
        long owed = 0;
        for (int l = 2; l <= level; l++) {
            owed += gemsForLevel(l) - oldGemsForLevel(l);
        }
        return Math.max(0, owed);
    }

    /** Coin multiplier of a job at a level (2% per level). */
    public static double coinMultiplier(int level) {
        return 1 + Math.max(0, level - 1) * 0.02;
    }

    /** How many playtime milestones (every {@code hours} hours) are reached with {@code seconds} of playtime. */
    public static long milestones(long seconds, int hours) {
        return hours <= 0 ? 0 : seconds / (hours * 3600L);
    }

    /** Result of adding XP: new level, remaining XP and how many levels were gained. */
    public record Gain(int level, long xp, int levelsGained) {
    }

    public static Gain addXp(int level, long xp, long amount) {
        int lvl = Math.max(1, level);
        long total = xp + amount;
        int gained = 0;
        while (lvl < MAX_JOB_LEVEL && total >= xpForNextLevel(lvl)) {
            total -= xpForNextLevel(lvl);
            lvl++;
            gained++;
        }
        if (lvl >= MAX_JOB_LEVEL) {
            total = 0;
        }
        return new Gain(lvl, total, gained);
    }

    /** Parkour time in milliseconds as m:ss.SSS. */
    public static String time(long millis) {
        long m = millis / 60000;
        long s = (millis / 1000) % 60;
        long ms = millis % 1000;
        return m > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d.%03d", m, s, ms) : String.format(java.util.Locale.ROOT, "%d.%03ds", s, ms);
    }
}
