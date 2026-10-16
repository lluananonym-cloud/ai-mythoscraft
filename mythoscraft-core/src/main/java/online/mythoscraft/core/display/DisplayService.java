package online.mythoscraft.core.display;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.UUID;

/**
 * Everything players see about ranks: badges (pack glyph or text) in chat, tab list and name tags, the sidebar,
 * the tab header and personalised join/quit messages.
 */
public final class DisplayService implements Listener {

    private final MythosCraft plugin;
    private final ScoreboardManager scoreboards;
    private final TabListManager tablist;
    private boolean joinMessages = true;
    private String joinFormat = "";
    private String quitFormat = "";
    private String firstJoinFormat = "";
    private boolean mythosAnnounce = true;
    private String teamTextPrefix = "&c&lTEAM &8| ";

    public DisplayService(MythosCraft plugin) {
        this.plugin = plugin;
        this.scoreboards = new ScoreboardManager(plugin);
        this.tablist = new TabListManager(plugin);
    }

    public void reload(ConfigReader root) {
        ConfigReader jm = root.sub("join-messages");
        joinMessages = jm.bool("enabled", true);
        joinFormat = jm.string("join", "<dark_gray>[<green>+</green>]</dark_gray> {badge}{name}");
        quitFormat = jm.string("quit", "<dark_gray>[<red>-</red>]</dark_gray> {badge}{name}");
        firstJoinFormat = jm.string("first-join", "");
        mythosAnnounce = jm.bool("mythos-announce", true);
        teamTextPrefix = root.string("ranks.team-prefix", "&c&lTEAM &8| ");
        scoreboards.reload(root);
        tablist.reload(root);
    }

    public boolean hasPack(Player player) {
        return plugin.resourcePack().hasPack(player);
    }

    /** Rank badge for one viewer: glyph with the pack, coloured text prefix without. */
    public Component badge(Rank rank, Player viewer, boolean pack) {
        if (pack && Glyphs.hasBadge(rank.badge())) {
            return Glyphs.badge(rank.badge()).append(Component.space());
        }
        return Text.parse(rank.textPrefix());
    }

    /** Full name prefix (team badge + rank badge) for one viewer. */
    public Component prefix(Rank rank, boolean staff, boolean pack) {
        Component rankPart = pack && Glyphs.hasBadge(rank.badge())
                ? Glyphs.badge(rank.badge()).append(Component.space())
                : Text.parse(rank.textPrefix());
        if (!staff) {
            return rankPart;
        }
        Component staffPart = pack ? Glyphs.badge("team").append(Component.space()) : Text.parse(teamTextPrefix);
        return Component.empty().append(staffPart).append(rankPart);
    }

    /** Clan tag and LIVE badge behind the name (chat, tab list, name tag). */
    public Component suffix(org.bukkit.entity.Player player) {
        Component out = Component.empty();
        if (plugin.clans() != null && plugin.clans().of(player.getUniqueId()) != null) {
            out = out.append(Component.space()).append(plugin.clans().tag(player.getUniqueId()));
        }
        if (plugin.attractions() != null && plugin.progress().loaded(player) && plugin.attractions().music().streaming(player)) {
            out = out.append(Component.space()).append(plugin.messages().get("stream.badge"));
        }
        return out;
    }

    public void markDirty(UUID player) {
        scoreboards.markDirty(player);
    }

    /** Rank or permission change of a player. */
    public void refresh(Player player) {
        scoreboards.refreshTeam(player);
        tablist.update(player);
    }

    /** The player's client loaded or dropped the resource pack. */
    public void packChanged(Player player) {
        scoreboards.refreshViewer(player);
        tablist.update(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (joinMessages) {
            event.joinMessage(null);
        }
        plugin.resourcePack().send(event.getPlayer());
    }

    /** Called once the player's data is loaded. */
    public void handleJoin(Player player, PlayerData data, boolean firstJoin) {
        scoreboards.setup(player);
        tablist.update(player);
        Rank rank = plugin.ranks().rankOf(data);
        if (joinMessages) {
            boolean staff = player.hasPermission(Perms.TEAM_BADGE);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.sendMessage(plugin.messages().format(joinFormat, "badge", prefix(rank, staff, hasPack(viewer)),
                        "name", Component.text(player.getName(), rank.teamColor()), "player", player.getName()));
            }
            if (firstJoin && !firstJoinFormat.isEmpty()) {
                plugin.database().query("count players", c -> plugin.playerData().repository().countPlayers(c))
                        .thenAccept(count -> plugin.tasks().sync(() -> Bukkit.getServer().sendMessage(
                                plugin.messages().format(firstJoinFormat, "player", player.getName(), "number", count))));
            }
        }
        if (mythosAnnounce && rank.isMythos()) {
            announceMythos(player);
        }
    }

    private void announceMythos(Player player) {
        java.util.function.Supplier<Title> title = () -> Title.title(plugin.messages().get("join.mythos-title"),
                plugin.messages().get("join.mythos-subtitle", "player", player.getName()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player)) {
                p.showTitle(plugin.messages().in(p, title));
                p.playSound(Sound.sound(org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, Sound.Source.MASTER, 0.5f, 1.4f));
            }
        }
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 60, 0.6, 1.0, 0.6, 0.05);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 0.2, 0), 40, 0.8, 0.1, 0.8, 0.01);
    }

    // LOW: before the Vanish plugin removes its "vanish" metadata on quit
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (joinMessages) {
            event.quitMessage(null);
        }
        // vanished staff (separate Vanish plugin sets the "vanish" metadata) leaves silently
        if (joinMessages && !player.hasMetadata("vanish")) {
            Rank rank = plugin.ranks().rankOf(player);
            boolean staff = player.hasPermission(Perms.TEAM_BADGE);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player)) {
                    viewer.sendMessage(plugin.messages().format(quitFormat, "badge", prefix(rank, staff, hasPack(viewer)),
                            "name", Component.text(player.getName(), rank.teamColor()), "player", player.getName()));
                }
            }
        }
        scoreboards.remove(player);
    }

    public void shutdown() {
        scoreboards.shutdown();
    }
}
