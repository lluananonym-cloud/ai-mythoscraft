package online.mythoscraft.core.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAndDurationTest {

    @Test
    void legacyCodesBecomeMiniMessage() {
        assertEquals("<reset><dark_purple><bold>MythosCraft <reset><dark_gray>» ", Text.legacyToMini("&5&lMythosCraft &8» "));
        assertEquals("<reset><#a855f7>Hi", Text.legacyToMini("&#a855f7Hi"));
        assertEquals("<gold>already mini</gold>", Text.legacyToMini("<gold>already mini</gold>"));
        assertEquals("Tom & Jerry", Text.legacyToMini("Tom & Jerry"));
    }

    @Test
    void parsedTextIsPlainReadable() {
        assertEquals("MythosCraft » ", Text.plain(Text.parse("&5&lMythosCraft &8» ")));
        assertEquals("VIP | ", Text.plain(Text.parse("&a&lVIP &8| ")));
    }

    @Test
    void durationsParseAndFormat() {
        assertEquals(Duration.ofDays(30), Durations.parse("30d").orElseThrow());
        assertEquals(Duration.ofHours(36), Durations.parse("1d12h").orElseThrow());
        assertEquals(Duration.ofMinutes(90), Durations.parse("1h30m").orElseThrow());
        assertTrue(Durations.parse("abc").isEmpty());
        assertTrue(Durations.parse("10x").isEmpty());
        assertTrue(Durations.parse("0s").isEmpty());
        assertEquals("1h 30m", Durations.format(Duration.ofMinutes(90)));
        assertEquals("2d 3h", Durations.format(Duration.ofHours(51)));
        assertEquals("1:05", Durations.clock(65));
    }

    @Test
    void weightedRandomRespectsWeights() {
        WeightedRandom<String> table = new WeightedRandom<>(List.of("common", "rare", "never"),
                s -> switch (s) {
                    case "common" -> 90;
                    case "rare" -> 10;
                    default -> 0;
                });
        assertEquals(2, table.items().size());
        assertEquals(90.0, table.chance(0), 0.0001);
        assertEquals(10.0, table.chance(1), 0.0001);
        Random rnd = new Random(1);
        int rare = 0;
        for (int i = 0; i < 100_000; i++) {
            if (table.pick(rnd).equals("rare")) {
                rare++;
            }
        }
        assertTrue(rare > 9_000 && rare < 11_000, "rare picked " + rare + " times");
    }
}
