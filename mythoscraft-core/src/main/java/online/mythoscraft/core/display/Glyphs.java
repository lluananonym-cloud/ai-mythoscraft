package online.mythoscraft.core.display;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Map;

/**
 * Characters of the MythosCraft resource pack fonts. Every glyph has a plain-text fallback for players without
 * the pack; callers decide per viewer which variant to use.
 * Must stay in sync with assets/mythoscraft/font/*.json of the resource pack.
 */
public final class Glyphs {

    public static final Key ICONS = Key.key("mythoscraft", "icons");
    public static final Key GUI = Key.key("mythoscraft", "gui");

    public static final char LOGO = '';
    public static final char GUI_CRATE_OPEN = '';
    public static final char GUI_CRATE_PREVIEW = '';

    private static final Map<String, Character> BADGES = Map.ofEntries(
            Map.entry("vip", ''),
            Map.entry("elite", ''),
            Map.entry("mythos", ''),
            Map.entry("team", ''),
            Map.entry("vipplus", ''),
            Map.entry("held", ''),
            Map.entry("legende", ''),
            Map.entry("titan", ''),
            Map.entry("supporter", ''),
            Map.entry("builder", ''),
            Map.entry("mod", ''),
            Map.entry("admin", ''),
            Map.entry("owner", ''),
            Map.entry("creator", ''));

    private record Icon(char glyph, String fallback, TextColor color) {
    }

    private static final Map<String, Icon> ICONS_BY_ID = Map.of(
            "coin", new Icon('', "●", NamedTextColor.GOLD),
            "gem", new Icon('', "◆", NamedTextColor.LIGHT_PURPLE),
            "kill", new Icon('', "⚔", NamedTextColor.RED),
            "death", new Icon('', "☠", NamedTextColor.GRAY),
            "time", new Icon('', "⌛", NamedTextColor.AQUA),
            "event", new Icon('', "✦", NamedTextColor.YELLOW),
            "key", new Icon('', "✧", NamedTextColor.GOLD),
            "rank", new Icon('', "★", NamedTextColor.LIGHT_PURPLE),
            "streak", new Icon('', "✹", NamedTextColor.GOLD));

    /** Negative/positive space characters of the gui font, by advance in pixels. */
    private static final int[] SPACE_VALUES = {256, 128, 64, 32, 16, 8, 4, 2, 1};
    private static final char NEG_BASE = '';
    private static final char POS_BASE = '';

    private Glyphs() {
    }

    public static boolean hasBadge(String id) {
        return id != null && BADGES.containsKey(id);
    }

    /** Pack badge glyph, rendered white and without text shadow so the texture keeps its colours. */
    public static Component badge(String id) {
        Character c = BADGES.get(id);
        if (c == null) {
            return Component.empty();
        }
        return glyph(c, ICONS);
    }

    public static Component icon(String id, boolean pack) {
        Icon icon = ICONS_BY_ID.get(id);
        if (icon == null) {
            return Component.empty();
        }
        return pack ? glyph(icon.glyph, ICONS) : Component.text(icon.fallback, icon.color);
    }

    public static Iterable<String> iconIds() {
        return ICONS_BY_ID.keySet();
    }

    public static Component logo() {
        return glyph(LOGO, ICONS);
    }

    public static TextComponent glyph(char c, Key font) {
        return Component.text(String.valueOf(c)).font(font).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /** Horizontal offset in pixels (negative moves left) built from the space glyphs of the gui font. */
    public static Component space(int pixels) {
        if (pixels == 0) {
            return Component.empty();
        }
        StringBuilder sb = new StringBuilder();
        int remaining = Math.abs(pixels);
        char base = pixels < 0 ? NEG_BASE : POS_BASE;
        for (int i = 0; i < SPACE_VALUES.length; i++) {
            while (remaining >= SPACE_VALUES[i]) {
                sb.append((char) (base + i));
                remaining -= SPACE_VALUES[i];
            }
        }
        return Component.text(sb.toString()).font(GUI);
    }

    /**
     * Inventory title that draws a full custom background: shift left to the GUI edge, draw the background glyph
     * (176 px wide), shift back and append the normal title text.
     */
    public static Component guiTitle(char background, Component title) {
        return Component.text()
                .append(space(-8))
                .append(glyph(background, GUI))
                .append(space(-177 + 8))
                .append(title)
                .build();
    }
}
