package online.mythoscraft.core.tournament;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/** Pure calculation of the next tournament start (no Bukkit access, unit tested). */
public final class TournamentSchedule {

    private TournamentSchedule() {
    }

    /**
     * First start strictly after {@code now}.
     *
     * @param dayOfWeek  used for WEEKLY
     * @param dayOfMonth used for MONTHLY (clamped to the length of the month)
     */
    public static ZonedDateTime next(TournamentTier tier, LocalTime time, DayOfWeek dayOfWeek, int dayOfMonth, ZonedDateTime now) {
        ZonedDateTime candidate = now.with(time).withSecond(0).withNano(0);
        switch (tier) {
            case DAILY -> {
                if (!candidate.isAfter(now)) {
                    candidate = candidate.plusDays(1);
                }
            }
            case WEEKLY -> {
                candidate = candidate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
                if (!candidate.isAfter(now)) {
                    candidate = candidate.plusWeeks(1);
                }
            }
            case MONTHLY -> {
                candidate = atDay(candidate, dayOfMonth);
                if (!candidate.isAfter(now)) {
                    candidate = atDay(candidate.plusMonths(1).withDayOfMonth(1), dayOfMonth);
                }
            }
            default -> throw new IllegalArgumentException(tier.name());
        }
        return candidate;
    }

    private static ZonedDateTime atDay(ZonedDateTime base, int day) {
        int max = base.toLocalDate().lengthOfMonth();
        return base.withDayOfMonth(Math.max(1, Math.min(day, max)));
    }
}
