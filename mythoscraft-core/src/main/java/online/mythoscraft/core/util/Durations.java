package online.mythoscraft.core.util;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)(w|d|h|m|s)");

    private Durations() {
    }

    /** Parses "30s", "10m", "2h", "7d", "1w" and combinations like "1d12h". Empty for invalid input. */
    public static Optional<Duration> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = PART.matcher(s);
        int pos = 0;
        long seconds = 0;
        while (m.find()) {
            if (m.start() != pos) {
                return Optional.empty();
            }
            pos = m.end();
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            long unit = switch (m.group(2)) {
                case "w" -> 604_800L;
                case "d" -> 86_400L;
                case "h" -> 3_600L;
                case "m" -> 60L;
                default -> 1L;
            };
            if (n > 100_000) {
                return Optional.empty();
            }
            seconds += n * unit;
        }
        if (pos != s.length() || seconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    /** "2d 3h", "1h 20m", "45s" – at most two units. */
    public static String format(Duration duration) {
        long total = Math.max(0, duration.getSeconds());
        long d = total / 86_400;
        long h = (total % 86_400) / 3_600;
        long m = (total % 3_600) / 60;
        long s = total % 60;
        if (d > 0) {
            return h > 0 ? d + "d " + h + "h" : d + "d";
        }
        if (h > 0) {
            return m > 0 ? h + "h " + m + "m" : h + "h";
        }
        if (m > 0) {
            return s > 0 ? m + "m " + s + "s" : m + "m";
        }
        return s + "s";
    }

    public static String formatSeconds(long seconds) {
        return format(Duration.ofSeconds(seconds));
    }

    /** mm:ss clock for countdowns. */
    public static String clock(long seconds) {
        long s = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
