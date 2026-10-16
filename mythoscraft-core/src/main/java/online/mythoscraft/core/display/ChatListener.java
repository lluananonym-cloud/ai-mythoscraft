package online.mythoscraft.core.display;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.regex.Pattern;

/**
 * Chat format with per-viewer rank badges (pack glyph or text), rank chat colours, hover info on names and
 * mention highlighting with a ping sound.
 */
public final class ChatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final MythosCraft plugin;
    private volatile boolean enabled = true;
    private volatile String format = "{badge}{name} <dark_gray>»</dark_gray> {message}";
    private volatile boolean mentions = true;

    public ChatListener(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("chat");
        enabled = cfg.bool("enabled", true);
        format = cfg.string("format", "{badge}{name} <dark_gray>»</dark_gray> {message}");
        mentions = cfg.bool("mentions", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) {
            return;
        }
        Player source = event.getPlayer();
        PlayerData data = plugin.playerData().get(source);
        Rank rank = plugin.ranks().rankOf(data);
        boolean staff = source.hasPermission(Perms.TEAM_BADGE);
        Component message = event.message();
        if (source.hasPermission(Perms.CHAT_COLOR)) {
            message = LEGACY.deserialize(Text.plain(message));
        }
        Component body = message.colorIfAbsent(rank.chatColor());
        Component hover = plugin.messages().format(plugin.messages().raw("chat.hover"),
                "rank", Text.parse(rank.display()),
                "coins", data == null ? "?" : Numbers.format(data.coins()),
                "kills", data == null ? "?" : Numbers.format(data.kills()),
                "player", source.getName());
        Component name = Component.text(source.getName(), rank.teamColor())
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.suggestCommand("/msg " + source.getName() + " "));
        Component named = name.append(plugin.display().suffix(source));
        Component finalBody = body;
        String plainMessage = Text.plain(message);
        event.renderer((player, displayName, msg, viewer) -> {
            boolean pack = viewer instanceof Player p && plugin.display().hasPack(p);
            Component rendered = finalBody;
            if (mentions && viewer instanceof Player target && !target.equals(player)
                    && Pattern.compile("(?i)\\b" + Pattern.quote(target.getName()) + "\\b").matcher(plainMessage).find()) {
                rendered = rendered.replaceText(TextReplacementConfig.builder()
                        .match("(?i)\\b" + Pattern.quote(target.getName()) + "\\b")
                        .replacement(m -> m.color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                        .build());
                ping(target);
            }
            return plugin.messages().format(format,
                    "badge", plugin.display().prefix(rank, staff, pack),
                    "name", named,
                    "message", rendered);
        });
    }

    private void ping(Audience target) {
        plugin.tasks().sync(() -> target.playSound(Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, Sound.Source.PLAYER, 0.7f, 1.8f)));
    }
}
