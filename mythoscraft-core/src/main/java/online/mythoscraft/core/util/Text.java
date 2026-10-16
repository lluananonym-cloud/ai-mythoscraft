package online.mythoscraft.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Text parsing for config/message strings. Supports classic {@code &} colour codes, {@code &#RRGGBB} hex colours
 * and full MiniMessage tags in the same string.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY = new HashMap<>();

    static {
        String[] colors = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
                "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        String codes = "0123456789abcdef";
        for (int i = 0; i < codes.length(); i++) {
            LEGACY.put(codes.charAt(i), colors[i]);
        }
        LEGACY.put('k', "obfuscated");
        LEGACY.put('l', "bold");
        LEGACY.put('m', "strikethrough");
        LEGACY.put('n', "underlined");
        LEGACY.put('o', "italic");
        LEGACY.put('r', "reset");
    }

    private Text() {
    }

    public static Component parse(String input, TagResolver... resolvers) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(legacyToMini(input), resolvers);
    }

    /** Parses text for item names/lore, where Minecraft would otherwise render everything in italics. */
    public static Component item(String input, TagResolver... resolvers) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(parse(input, resolvers));
    }

    public static Component noItalic(Component component) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(component);
    }

    /** Parses "white", "#a855f7" or "&f" into a colour; null if invalid. */
    public static TextColor color(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String s = input.trim();
        if ((s.startsWith("&") || s.startsWith("§")) && s.length() == 2) {
            String name = LEGACY.get(Character.toLowerCase(s.charAt(1)));
            return name == null ? null : NamedTextColor.NAMES.value(name);
        }
        if ((s.startsWith("&#") || s.startsWith("§#")) && s.length() == 8) {
            s = s.substring(1);
        }
        if (s.startsWith("#")) {
            return TextColor.fromHexString(s);
        }
        return NamedTextColor.NAMES.value(s.toLowerCase(java.util.Locale.ROOT));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String escape(String raw) {
        return MINI.escapeTags(raw);
    }

    /** Converts legacy colour codes to MiniMessage tags while keeping legacy semantics (a colour resets formatting). */
    public static String legacyToMini(String in) {
        if (in.indexOf('&') < 0 && in.indexOf('§') < 0) {
            return in;
        }
        StringBuilder out = new StringBuilder(in.length() + 32);
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < in.length()) {
                char code = Character.toLowerCase(in.charAt(i + 1));
                if (code == '#' && i + 8 <= in.length() && isHex(in, i + 2, 6)) {
                    out.append("<reset><#").append(in, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = LEGACY.get(code);
                if (tag != null) {
                    if (code == 'r') {
                        out.append("<reset>");
                    } else if (Character.isDigit(code) || (code >= 'a' && code <= 'f')) {
                        out.append("<reset><").append(tag).append('>');
                    } else {
                        out.append('<').append(tag).append('>');
                    }
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isHex(String s, int start, int len) {
        for (int i = start; i < start + len; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
