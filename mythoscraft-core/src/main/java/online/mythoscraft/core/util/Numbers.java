package online.mythoscraft.core.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.OptionalLong;

public final class Numbers {

    private Numbers() {
    }

    /** German grouping: 1234567 → "1.234.567". Thread-safe. */
    public static String format(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    /** Formats a decimal without unnecessary trailing zeros: 1.5 → "1,5", 2.0 → "2". */
    public static String formatDecimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return format((long) value);
        }
        String s = String.format(Locale.GERMANY, "%,.2f", value);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(",")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Short form for tight spaces: 1234 → "1,2k", 2500000 → "2,5M". */
    public static String compact(long value) {
        long abs = Math.abs(value);
        if (abs < 10_000) {
            return format(value);
        }
        if (abs < 1_000_000) {
            return formatDecimal(Math.floor(value / 100.0) / 10.0) + "k";
        }
        if (abs < 1_000_000_000) {
            return formatDecimal(Math.floor(value / 100_000.0) / 10.0) + "M";
        }
        return formatDecimal(Math.floor(value / 100_000_000.0) / 10.0) + "Mrd";
    }

    /**
     * Parses a positive whole amount. Accepts "500", "1.000" (German grouping), "1k", "2,5k", "1.5m", "3b".
     * Returns empty for anything invalid, zero, negative, fractional or above {@code max}.
     */
    public static OptionalLong parseAmount(String input, long max) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String s = input.trim().toLowerCase(Locale.ROOT).replace("_", "");
        if (s.isEmpty() || s.length() > 24) {
            return OptionalLong.empty();
        }
        long multiplier = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            return OptionalLong.empty();
        }
        // "1.000.000" is German grouping, "2,5" a German decimal, "2.5" an English decimal.
        if (s.matches("\\d{1,3}(\\.\\d{3})+")) {
            s = s.replace(".", "");
        } else {
            s = s.replace(',', '.');
        }
        if (!s.matches("\\d+(\\.\\d+)?")) {
            return OptionalLong.empty();
        }
        try {
            BigDecimal value = new BigDecimal(s).multiply(BigDecimal.valueOf(multiplier));
            if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
                return OptionalLong.empty();
            }
            if (value.compareTo(BigDecimal.valueOf(max)) > 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(value.longValueExact());
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    public static Integer parseInt(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    public static Double parseDouble(String input) {
        try {
            double d = Double.parseDouble(input.trim().replace(',', '.'));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** Overflow-safe addition clamped to [0, max]. */
    public static long clampedAdd(long a, long b, long max) {
        long r;
        try {
            r = Math.addExact(a, b);
        } catch (ArithmeticException e) {
            r = b > 0 ? Long.MAX_VALUE : 0;
        }
        return Math.max(0, Math.min(max, r));
    }
}
