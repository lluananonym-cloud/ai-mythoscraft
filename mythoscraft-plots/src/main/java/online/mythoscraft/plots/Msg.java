package online.mythoscraft.plots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** messages.yml (MiniMessage, {placeholders}); missing keys fall back to the texts inside the jar. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private YamlConfiguration texts = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();

    void load(JavaPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        texts = YamlConfiguration.loadConfiguration(f);
        var in = plugin.getResource("messages.yml");
        if (in != null) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        // texts of an older version: replaced by the new ones from the jar (old file kept as messages-old.yml)
        if (texts.getInt("messages-version", 1) < defaults.getInt("messages-version", 1)) {
            File old = new File(plugin.getDataFolder(), "messages-old.yml");
            old.delete();
            f.renameTo(old);
            plugin.saveResource("messages.yml", true);
            texts = YamlConfiguration.loadConfiguration(f);
        }
    }

    private String raw(String key) {
        String s = texts.getString(key);
        return s != null ? s : defaults.getString(key, "<red>" + key);
    }

    /** {name} placeholders: pairs of name, value (value may be a Component). */
    public Component get(String key, Object... pairs) {
        String text = raw(key);
        TagResolver.Builder b = TagResolver.builder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String name = String.valueOf(pairs[i]);
            text = text.replace("{" + name + "}", "<" + name + ">");
            Object v = pairs[i + 1];
            b.resolver(v instanceof Component c ? Placeholder.component(name, c) : Placeholder.unparsed(name, String.valueOf(v)));
        }
        return MM.deserialize(text, b.build());
    }

    public void send(CommandSender to, String key, Object... pairs) {
        to.sendMessage(MM.deserialize(raw("prefix")).append(get(key, pairs)));
    }

    public void plain(CommandSender to, String key, Object... pairs) {
        to.sendMessage(get(key, pairs));
    }

    public void bar(Player to, String key, Object... pairs) {
        to.sendActionBar(get(key, pairs));
    }
}
