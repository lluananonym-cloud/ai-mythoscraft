package online.mythoscraft.core.display;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One scoreboard per player. It carries the MythosCraft sidebar and one team per rank whose prefix is the rank
 * badge – rendered per viewer, so players with the resource pack see the custom badge in tab list and above heads
 * while everyone else gets a coloured text prefix. Sidebar lines are only re-sent when their content changed.
 */
public final class ScoreboardManager {

    private final MythosCraft plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private boolean sidebarEnabled = true;
    private boolean nametagsEnabled = true;
    private String title = "MythosCraft";
    private List<String> lines = List.of();
    private List<String> linesEn = List.of();
    private int fullRefreshTicks = 100;
    private int tick;

    private static final class Board {
        final Scoreboard scoreboard;
        Objective sidebar;
        final List<Component> shown = new ArrayList<>();

        Board(Scoreboard scoreboard) {
            this.scoreboard = scoreboard;
        }
    }

    public ScoreboardManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("scoreboard");
        sidebarEnabled = cfg.bool("enabled", true);
        nametagsEnabled = root.bool("nametags.enabled", true);
        title = cfg.string("title", "<gradient:#c084fc:#f59e0b><bold>MythosCraft</bold></gradient>");
        lines = cfg.strings("lines");
        linesEn = cfg.strings("lines-en");
        if (linesEn.size() > 15) {
            linesEn = linesEn.subList(0, 15);
        }
        if (lines.size() > 15) {
            cfg.warn("lines", "maximal 15 Zeilen – der Rest wird ignoriert");
            lines = lines.subList(0, 15);
        }
        int interval = cfg.integer("update-interval-ticks", 20, 5, 400);
        fullRefreshTicks = Math.max(interval, cfg.integer("full-refresh-ticks", 100, 20, 1200));
        plugin.tasks().cancel(this);
        if (active()) {
            plugin.tasks().repeat(this, interval, interval, "scoreboard", () -> update(interval));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            remove(p);
            setup(p);
        }
    }

    private boolean active() {
        return sidebarEnabled || nametagsEnabled;
    }

    public void setup(Player player) {
        if (!active()) {
            return;
        }
        Board board = new Board(Bukkit.getScoreboardManager().getNewScoreboard());
        boards.put(player.getUniqueId(), board);
        if (sidebarEnabled) {
            Objective obj = board.scoreboard.registerNewObjective("mythos", Criteria.DUMMY, plugin.messages().format(title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
            board.sidebar = obj;
        }
        player.setScoreboard(board.scoreboard);
        if (nametagsEnabled) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                assign(board, player, other);
                Board otherBoard = boards.get(other.getUniqueId());
                if (otherBoard != null && otherBoard != board) {
                    assign(otherBoard, other, player);
                }
            }
        }
        dirty.add(player.getUniqueId());
        renderSidebar(player, board);
    }

    public void remove(Player player) {
        Board board = boards.remove(player.getUniqueId());
        dirty.remove(player.getUniqueId());
        if (board != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        for (Board other : boards.values()) {
            Team t = other.scoreboard.getEntryTeam(player.getName());
            if (t != null) {
                t.removeEntry(player.getName());
            }
        }
    }

    public void markDirty(UUID player) {
        dirty.add(player);
    }

    /** Re-sorts a player into the right rank team on every board (after rank changes). */
    public void refreshTeam(Player target) {
        if (!nametagsEnabled) {
            return;
        }
        for (Map.Entry<UUID, Board> e : boards.entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            if (viewer != null) {
                assign(e.getValue(), viewer, target);
            }
        }
        dirty.add(target.getUniqueId());
    }

    /** The viewer's pack state changed: re-render all prefixes on their board. */
    public void refreshViewer(Player viewer) {
        Board board = boards.get(viewer.getUniqueId());
        if (board == null) {
            return;
        }
        if (nametagsEnabled) {
            for (Team team : board.scoreboard.getTeams()) {
                team.unregister();
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                assign(board, viewer, other);
            }
        }
        board.shown.clear();
        dirty.add(viewer.getUniqueId());
    }

    private void assign(Board board, Player viewer, Player target) {
        Rank rank = plugin.ranks().rankOf(target);
        boolean staff = target.hasPermission(Perms.TEAM_BADGE);
        net.kyori.adventure.text.Component suffix = plugin.display().suffix(target);
        String suffixKey = online.mythoscraft.core.util.Text.plain(suffix).isEmpty() ? ""
                : Integer.toHexString(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(suffix).hashCode());
        String name = String.format(Locale.ROOT, "%04d%s%s", 9999 - rank.weight(), staff ? "s" : "", rank.id().toLowerCase(Locale.ROOT));
        if (name.length() > 22) {
            name = name.substring(0, 22);
        }
        name = name + suffixKey;
        Team current = board.scoreboard.getEntryTeam(target.getName());
        if (current != null && current.getName().equals(name)) {
            return;
        }
        if (current != null) {
            current.removeEntry(target.getName());
        }
        Team team = board.scoreboard.getTeam(name);
        if (team == null) {
            team = board.scoreboard.registerNewTeam(name);
            boolean pack = plugin.display().hasPack(viewer);
            team.prefix(plugin.display().prefix(rank, staff, pack));
            team.color(rank.teamColor());
            team.suffix(suffix);
        }
        team.addEntry(target.getName());
    }

    private void update(int interval) {
        tick += interval;
        boolean full = tick >= fullRefreshTicks;
        if (full) {
            tick = 0;
        }
        if (!sidebarEnabled) {
            dirty.clear();
            return;
        }
        for (Map.Entry<UUID, Board> e : boards.entrySet()) {
            if (!full && !dirty.contains(e.getKey())) {
                continue;
            }
            Player player = Bukkit.getPlayer(e.getKey());
            if (player != null) {
                renderSidebar(player, e.getValue());
            }
        }
        dirty.clear();
    }

    private void renderSidebar(Player player, Board board) {
        if (board.sidebar == null) {
            return;
        }
        List<String> lines = !linesEn.isEmpty() && plugin.messages().lang(player).equals("en") ? linesEn : this.lines;
        Object[] placeholders = plugin.messages().in(player, () -> plugin.placeholders().of(player));
        int size = lines.size();
        for (int i = 0; i < size; i++) {
            Component line = plugin.messages().format(lines.get(i), placeholders);
            if (i < board.shown.size() && Objects.equals(board.shown.get(i), line)) {
                continue;
            }
            Score score = board.sidebar.getScore("l" + i);
            score.setScore(size - i);
            score.customName(line);
            if (i < board.shown.size()) {
                board.shown.set(i, line);
            } else {
                board.shown.add(line);
            }
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(p.getUniqueId())) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
        dirty.clear();
    }
}
