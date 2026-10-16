package online.mythoscraft.core.util;

import online.mythoscraft.core.config.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderTest {

    private static String render(String template, Object... ph) {
        return Text.plain(Text.parse(template.replaceAll("\\{([a-z0-9_]+)}", "<$1>"), Messages.resolver(ph)));
    }

    @Test
    void placeholdersWinOverBuiltInTags() {
        assertEquals("Key: X", render("Key: {key}", "key", "X"));
        assertEquals("Score: 5", render("Score: {score}", "score", "5"));
        assertEquals("1.234 Coins", render("{amount} Coins", "amount", 1234L));
    }

    @Test
    void userInputCannotInjectTags() {
        assertEquals("<red>hi</red>", render("{message}", "message", "<red>hi</red>"));
    }
}
