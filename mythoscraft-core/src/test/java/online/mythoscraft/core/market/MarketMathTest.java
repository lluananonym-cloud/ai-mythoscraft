package online.mythoscraft.core.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketMathTest {

    @Test
    void taxIsRoundedDownAndTakenFromTheSeller() {
        assertEquals(50, MarketMath.tax(1000, 5));
        assertEquals(950, MarketMath.payout(1000, 5));
        assertEquals(0, MarketMath.tax(19, 5));
        assertEquals(19, MarketMath.payout(19, 5));
        assertEquals(0, MarketMath.tax(1000, 0));
        assertEquals(1000, MarketMath.tax(1000, 150));
    }

    @Test
    void orderTotalDetectsOverflow() {
        assertEquals(6400, MarketMath.total(640, 10));
        assertEquals(-1, MarketMath.total(Long.MAX_VALUE / 2, 3));
    }

    @Test
    void deliveryNeverExceedsTheOrder() {
        assertEquals(64, MarketMath.deliverable(64, 640, 0));
        assertEquals(40, MarketMath.deliverable(200, 640, 600));
        assertEquals(0, MarketMath.deliverable(10, 640, 640));
        assertEquals(0, MarketMath.deliverable(0, 640, 10));
    }

    @Test
    void progressBar() {
        assertEquals("■■■■■□□□□□", MarketMath.bar(50, 100, 10));
        assertEquals("□□□□□□□□□□", MarketMath.bar(0, 100, 10));
        assertEquals("■■■■■■■■■■", MarketMath.bar(100, 100, 10));
    }
}
