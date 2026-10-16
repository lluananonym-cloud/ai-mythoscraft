package online.mythoscraft.plots;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.text.NumberFormat.Style;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sidebar like on the survival server: rank, coins and gems (from the main server), the plot you stand on, your
 * plots and who is online. Every player has an own scoreboard, so the rank teams (badge above the head) are copied
 * into all of them.
 */
final class PlotBoard implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] ENTRIES = new String[15];

    static {
        for (int i = 0; i < ENTRIES.length; i++) {
            ENTRIES[i] = "§" + Integer.toHexString(i) + "§r";
        }
    }

    private final MythosPlots plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    PlotBoard(MythosPlots plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40, 20);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("scoreboard.enabled", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!enabled()) {
            return;
        }
        Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = b.registerNewObjective("mythosplots", Criteria.DUMMY, MM.deserialize(
                plugin.getConfig().getString("scoreboard.title", "<gradient:#c084fc:#f59e0b><bold>✦ MythosCraft ✦</bold></gradient>")));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            o.numberFormat(NumberFormat.blank());
        } catch (RuntimeException | LinkageError ignored) {
            // older server: numbers stay visible
        }
        boards.put(p.getUniqueId(), b);
        p.setScoreboard(b);
        // badges of everybody already here
        for (Player other : Bukkit.getOnlinePlayers()) {
            Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("mr_" + other.getUniqueId().toString().substring(0, 8));
            if (t != null) {
                copyInto(b, p, other, t);
            }
        }
        update(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
        String team = "mr_" + e.getPlayer().getUniqueId().toString().substring(0, 8);
        for (Scoreboard b : boards.values()) {
            Team t = b.getTeam(team);
            if (t != null) {
                t.unregister();
            }
        }
    }

    /** a player's rank team (badge + colour) into every personal scoreboard – badge as the viewer sees it */
    void copyTeam(Player owner, Team main) {
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            if (viewer != null) {
                copyInto(e.getValue(), viewer, owner, main);
            }
        }
    }

    private void copyInto(Scoreboard b, Player viewer, Player owner, Team main) {
        Team t = b.getTeam(main.getName());
        if (t == null) {
            t = b.registerNewTeam(main.getName());
        }
        t.prefix(plugin.ranks().prefix(owner.getUniqueId(), viewer));
        RankLimits.Info i = plugin.ranks().info(owner.getUniqueId());
        t.color(i != null ? i.nameColor() : NamedTextColor.WHITE);
        t.addEntry(owner.getName());
    }

    private void updateAll() {
        if (!enabled()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p);
        }
    }

    private static String number(long n) {
        if (n >= 10_000) {
            return java.text.NumberFormat.getCompactNumberInstance(Locale.ENGLISH, Style.SHORT).format(n);
        }
        return String.format(Locale.ENGLISH, "%,d", n);
    }

    private void update(Player p) {
        Scoreboard b = boards.get(p.getUniqueId());
        Objective o = b == null ? null : b.getObjective("mythosplots");
        if (o == null) {
            return;
        }
        RankLimits r = plugin.ranks();
        RankLimits.Info i = r.info(p.getUniqueId());
        Component arrow = Component.text(" » ", NamedTextColor.DARK_GRAY);
        Component line = Component.text("                         ", NamedTextColor.DARK_GRAY).decorate(TextDecoration.STRIKETHROUGH);
        // every line starts from an empty component: text appended to an icon would take over its icon font (boxes)
        List<Component> lines = new ArrayList<>();
        lines.add(line);
        lines.add(Component.empty().append(r.icon(p, "rank")).append(Component.text(" Rank", NamedTextColor.GRAY)).append(arrow)
                .append(i == null ? Component.text("…", NamedTextColor.GRAY) : i.rankDisplay()));
        lines.add(Component.empty().append(r.icon(p, "coin")).append(Component.text(" Coins", NamedTextColor.GRAY)).append(arrow)
                .append(Component.text(i == null ? "…" : number(i.coins()), NamedTextColor.GOLD)));
        lines.add(Component.empty().append(r.icon(p, "gem")).append(Component.text(" Gems", NamedTextColor.GRAY)).append(arrow)
                .append(Component.text(i == null ? "…" : number(i.gems()), NamedTextColor.LIGHT_PURPLE)));
        lines.add(Component.empty());
        PlotId here = plugin.plotAt(p.getLocation());
        Plot plot = plugin.store().get(here);
        Component where = here == null ? Component.text("Road", NamedTextColor.WHITE)
                : plot == null ? Component.text(here + " · free", NamedTextColor.GREEN)
                : Component.text(plot.ownerName(), plot.owner().equals(p.getUniqueId()) ? NamedTextColor.GREEN : NamedTextColor.WHITE);
        lines.add(Component.empty().append(r.icon(p, "key")).append(Component.text(" Plot", NamedTextColor.GRAY)).append(arrow).append(where));
        int max = plugin.maxPlots(p);
        lines.add(Component.empty().append(r.icon(p, "event")).append(Component.text(" Your plots", NamedTextColor.GRAY)).append(arrow)
                .append(Component.text(plugin.store().of(p.getUniqueId()).size() + "/" + (max == Integer.MAX_VALUE ? "∞" : max), NamedTextColor.WHITE)));
        lines.add(Component.empty().append(r.icon(p, "time")).append(Component.text(" Online", NamedTextColor.GRAY)).append(arrow)
                .append(Component.text(Bukkit.getOnlinePlayers().size(), NamedTextColor.WHITE)));
        lines.add(line.append(Component.space()));
        lines.add(MM.deserialize(plugin.getConfig().getString("scoreboard.footer", "<gradient:#c084fc:#f59e0b>mythoscraft.online</gradient>")));
        for (int n = 0; n < ENTRIES.length; n++) {
            if (n < lines.size()) {
                var score = o.getScore(ENTRIES[n]);
                score.setScore(lines.size() - n);
                score.customName(lines.get(n));
            } else {
                b.resetScores(ENTRIES[n]);
            }
        }
    }
}
