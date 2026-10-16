package online.mythoscraft.core.util;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumbersTest {

    private static final long MAX = 1_000_000_000L;

    private static long parse(String s) {
        OptionalLong v = Numbers.parseAmount(s, MAX);
        assertTrue(v.isPresent(), "expected '" + s + "' to be valid");
        return v.getAsLong();
    }

    private static void invalid(String s) {
        assertTrue(Numbers.parseAmount(s, MAX).isEmpty(), "expected '" + s + "' to be rejected");
    }

    @Test
    void parsesPlainAndSuffixedAmounts() {
        assertEquals(500, parse("500"));
        assertEquals(1_000, parse("1k"));
        assertEquals(2_500, parse("2,5k"));
        assertEquals(2_500, parse("2.5k"));
        assertEquals(1_500_000, parse("1.5m"));
        assertEquals(1_000_000, parse("1.000.000"));
        assertEquals(1_000_000_000, parse("1b"));
        assertEquals(42, parse(" 42 "));
    }

    @Test
    void rejectsInvalidAmounts() {
        invalid("0");
        invalid("-5");
        invalid("abc");
        invalid("");
        invalid("1.5");
        invalid("10,5");
        invalid("NaN");
        invalid("1e5");
        invalid("2b");
        invalid("99999999999999999999999");
        invalid("k");
        invalid(null);
    }

    @Test
    void formatsGerman() {
        assertEquals("1.234.567", Numbers.format(1_234_567));
        assertEquals("1,5", Numbers.formatDecimal(1.5));
        assertEquals("2", Numbers.formatDecimal(2.0));
        assertEquals("1,25", Numbers.formatDecimal(1.25));
        assertEquals("12,3k", Numbers.compact(12_345));
    }

    @Test
    void clampedAddNeverOverflows() {
        assertEquals(100, Numbers.clampedAdd(50, 50, 1_000));
        assertEquals(1_000, Numbers.clampedAdd(900, 500, 1_000));
        assertEquals(Long.MAX_VALUE, Numbers.clampedAdd(Long.MAX_VALUE - 1, 10, Long.MAX_VALUE));
        assertEquals(0, Numbers.clampedAdd(5, -10, 1_000));
    }
}
