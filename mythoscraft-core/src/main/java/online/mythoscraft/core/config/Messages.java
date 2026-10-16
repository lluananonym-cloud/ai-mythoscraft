package online.mythoscraft.core.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * messages.yml access (German) plus messages_en.yml (English). Every player sees the plugin texts in the language
 * of their Minecraft client (German → messages.yml, everything else → English). Placeholders are written as {name} in the file and passed as key/value pairs:
 * {@code send(player, "pay.sent", "amount", 500, "player", target.getName())}. String values are inserted
 * unparsed, so player input can never inject formatting.
 */
public final class Messages {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_]+)}");

    /** Language of the texts built on this thread right now (set per receiving player). */
    private static final ThreadLocal<String> LANG = new ThreadLocal<>();

    private final YamlStore store;
    private final YamlStore englishStore;
    private YamlConfiguration yaml;
    private YamlConfiguration english;
    private Component prefix = Component.empty();
    private boolean perPlayer = true;
    private String defaultLang = "en";
    private static volatile String defaultStatic = "en";

    public Messages(JavaPlugin plugin) {
        this.store = new YamlStore(plugin, "messages.yml");
        this.englishStore = new YamlStore(plugin, "messages_en.yml");
    }

    /** language.per-player / language.default from config.yml */
    public void languageSettings(boolean perPlayer, String defaultLang) {
        this.perPlayer = perPlayer;
        this.defaultLang = "de".equalsIgnoreCase(defaultLang) ? "de" : "en";
        defaultStatic = this.defaultLang;
    }

    /** "de" or "en" for a player (by the language chosen in the Minecraft client). */
    public String lang(Player p) {
        if (!perPlayer || p == null) {
            return defaultLang;
        }
        Locale l = p.locale();
        return l != null && l.getLanguage().equalsIgnoreCase("de") ? "de" : "en";
    }

    /** Runs {@code task} with the texts in the language of {@code viewer}. */
    public void in(CommandSender viewer, Runnable task) {
        in(viewer, () -> {
            task.run();
            return null;
        });
    }

    public <T> T in(CommandSender viewer, java.util.function.Supplier<T> task) {
        String before = LANG.get();
        LANG.set(viewer instanceof Player p ? lang(p) : defaultLang);
        try {
            return task.get();
        } finally {
            if (before == null) {
                LANG.remove();
            } else {
                LANG.set(before);
            }
        }
    }

    /** Sets the language for the rest of this event (see LocaleContextListener); null clears it. */
    public void context(Player p) {
        if (p == null) {
            LANG.remove();
        } else {
            LANG.set(lang(p));
        }
    }

    /** Language of the text being built right now ("de"/"en"); outside a player context the default language. */
    public static String currentLang() {
        String l = LANG.get();
        return l == null ? defaultStatic : l;
    }

    private YamlConfiguration current() {
        String l = LANG.get();
        if (l == null) {
            l = defaultLang;
        }
        return l.equals("en") && english != null ? english : yaml;
    }

    /**
     * @param prefixOverride optional prefix from config.yml (messages.prefix); messages.yml is used if null
     */
    public void load(String prefixOverride) {
        this.yaml = store.load(true);
        try {
            this.english = englishStore.load(true);
        } catch (RuntimeException e) {
            this.english = null;
        }
        this.prefix = Text.parse(prefixOverride != null ? prefixOverride : raw("prefix"));
    }

    public String raw(String key) {
        YamlConfiguration y = current();
        String s = y.getString(key);
        if (s == null && y != yaml) {
            s = yaml.getString(key);
        }
        return s == null ? "<red>[Nachricht fehlt: " + key + "]" : s;
    }

    public List<String> rawList(String key) {
        YamlConfiguration y = current();
        if (y != yaml && !y.contains(key)) {
            y = yaml;
        }
        List<String> list = y.getStringList(key);
        if (list.isEmpty() && y.isString(key)) {
            return List.of(y.getString(key, ""));
        }
        return list;
    }

    public boolean has(String key) {
        return yaml.contains(key);
    }

    public Component prefix() {
        return prefix;
    }

    public Component get(String key, Object... placeholders) {
        return format(raw(key), placeholders);
    }

    public List<Component> getList(String key, Object... placeholders) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(format(line, placeholders));
        }
        return out;
    }

    public Component format(String template, Object... placeholders) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        return Text.parse(PLACEHOLDER.matcher(template).replaceAll("<$1>"), resolver(placeholders));
    }

    public void send(CommandSender to, String key, Object... placeholders) {
        in(to, () -> {
            String raw = raw(key);
            if (!raw.isEmpty()) {
                to.sendMessage(prefix.append(format(raw, placeholders)));
            }
        });
    }

    public void sendRaw(Audience to, String key, Object... placeholders) {
        Runnable r = () -> {
            String raw = raw(key);
            if (raw.isEmpty()) {
                return;
            }
            for (String line : rawList(key)) {
                to.sendMessage(format(line, placeholders));
            }
        };
        if (to instanceof CommandSender cs) {
            in(cs, r);
        } else {
            r.run();
        }
    }

    public void actionBar(Player to, String key, Object... placeholders) {
        in(to, () -> {
            String raw = raw(key);
            if (!raw.isEmpty()) {
                to.sendActionBar(format(raw, placeholders));
            }
        });
    }

    /** Prefixed message to every online player and the console. */
    public void broadcast(String key, Object... placeholders) {
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            in(p, () -> {
                String raw = raw(key);
                if (!raw.isEmpty()) {
                    p.sendMessage(prefix.append(format(raw, placeholders)));
                }
            });
        }
        String raw = raw(key);
        if (!raw.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(prefix.append(format(raw, placeholders)));
        }
    }

    public static TagResolver resolver(Object... placeholders) {
        if (placeholders.length == 0) {
            return TagResolver.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]).toLowerCase(Locale.ROOT);
            Object value = placeholders[i + 1];
            if (value instanceof ComponentLike component) {
                builder.resolver(Placeholder.component(name, component));
            } else if (value instanceof Long || value instanceof Integer || value instanceof Short) {
                builder.resolver(Placeholder.unparsed(name, Numbers.format(((Number) value).longValue())));
            } else if (value instanceof Double || value instanceof Float) {
                builder.resolver(Placeholder.unparsed(name, Numbers.formatDecimal(((Number) value).doubleValue())));
            } else {
                builder.resolver(Placeholder.unparsed(name, String.valueOf(value)));
            }
        }
        return builder.build();
    }
}
