package online.mythoscraft.core.tournament;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TournamentScheduleTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime EVENING = LocalTime.of(20, 0);

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, BERLIN);
    }

    @Test
    void dailyStartsTodayOrTomorrow() {
        assertEquals(at(2026, 10, 12, 20, 0), TournamentSchedule.next(TournamentTier.DAILY, EVENING, DayOfWeek.SUNDAY, 1, at(2026, 10, 12, 19, 59)));
        assertEquals(at(2026, 10, 13, 20, 0), TournamentSchedule.next(TournamentTier.DAILY, EVENING, DayOfWeek.SUNDAY, 1, at(2026, 10, 12, 20, 0)));
        assertEquals(at(2027, 1, 1, 20, 0), TournamentSchedule.next(TournamentTier.DAILY, EVENING, DayOfWeek.SUNDAY, 1, at(2026, 12, 31, 21, 0)));
    }

    @Test
    void weeklyUsesConfiguredWeekday() {
        // 2026-10-12 is a Monday
        assertEquals(at(2026, 10, 18, 20, 0), TournamentSchedule.next(TournamentTier.WEEKLY, EVENING, DayOfWeek.SUNDAY, 1, at(2026, 10, 12, 10, 0)));
        assertEquals(at(2026, 10, 12, 20, 0), TournamentSchedule.next(TournamentTier.WEEKLY, EVENING, DayOfWeek.MONDAY, 1, at(2026, 10, 12, 10, 0)));
        assertEquals(at(2026, 10, 19, 20, 0), TournamentSchedule.next(TournamentTier.WEEKLY, EVENING, DayOfWeek.MONDAY, 1, at(2026, 10, 12, 20, 30)));
    }

    @Test
    void monthlyClampsToMonthLength() {
        assertEquals(at(2026, 11, 1, 20, 0), TournamentSchedule.next(TournamentTier.MONTHLY, EVENING, DayOfWeek.SUNDAY, 1, at(2026, 10, 12, 10, 0)));
        assertEquals(at(2026, 10, 31, 20, 0), TournamentSchedule.next(TournamentTier.MONTHLY, EVENING, DayOfWeek.SUNDAY, 31, at(2026, 10, 12, 10, 0)));
        assertEquals(at(2026, 11, 30, 20, 0), TournamentSchedule.next(TournamentTier.MONTHLY, EVENING, DayOfWeek.SUNDAY, 31, at(2026, 10, 31, 21, 0)));
        assertEquals(at(2027, 2, 28, 20, 0), TournamentSchedule.next(TournamentTier.MONTHLY, EVENING, DayOfWeek.SUNDAY, 30, at(2027, 1, 31, 21, 0)));
    }

    @Test
    void tierIdsIncludingGermanNames() {
        assertSame(TournamentTier.DAILY, TournamentTier.byId("Daily"));
        assertSame(TournamentTier.WEEKLY, TournamentTier.byId("wöchentlich"));
        assertSame(TournamentTier.MONTHLY, TournamentTier.byId("monatlich"));
        assertNull(TournamentTier.byId("yearly"));
        assertEquals(5, TournamentTier.DAILY.defaultSlots());
        assertEquals(20, TournamentTier.WEEKLY.defaultSlots());
        assertEquals(100, TournamentTier.MONTHLY.defaultSlots());
    }

    @Test
    void trackerKeysRoundTrip() {
        int[][] points = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-12345, 70, 98765}, {5, -1, -5}};
        for (int[] p : points) {
            assertArrayEquals(p, BlockChangeTracker.decode(BlockChangeTracker.key(p[0], p[1], p[2])));
        }
    }
}
