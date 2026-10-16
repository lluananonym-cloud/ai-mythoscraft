package online.mythoscraft.core.display;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Placeholder values for scoreboard, tab list and other per-viewer texts. */
public final class Placeholders {

    private final MythosCraft plugin;

    public Placeholders(MythosCraft plugin) {
        this.plugin = plugin;
    }

    /** Key/value pairs for {@link online.mythoscraft.core.config.Messages#format}. */
    public Object[] of(Player viewer) {
        boolean pack = plugin.display().hasPack(viewer);
        PlayerData data = plugin.playerData().get(viewer);
        Rank rank = plugin.ranks().rankOf(data);
        long now = System.currentTimeMillis();
        List<Object> list = new ArrayList<>(64);
        add(list, "player", viewer.getName());
        add(list, "rank", Text.parse(rank.display()));
        add(list, "badge", plugin.display().badge(rank, viewer, pack));
        add(list, "online", Bukkit.getOnlinePlayers().size());
        add(list, "max", Bukkit.getMaxPlayers());
        add(list, "ping", viewer.getPing());
        add(list, "tps", Numbers.formatDecimal(Math.min(20.0, Math.round(Bukkit.getTPS()[0] * 10) / 10.0)));
        add(list, "world", viewer.getWorld().getName());
        add(list, "event", plugin.events().statusLine());
        add(list, "season", plugin.seasons().statusLine());
        add(list, "boost_coins", plugin.multipliers().forPlayer(MultiplierType.COINS, data));
        add(list, "boost_gems", plugin.multipliers().forPlayer(MultiplierType.GEMS, data));
        add(list, "boost_events", plugin.multipliers().global(MultiplierType.EVENTS));
        if (data != null) {
            add(list, "coins", Numbers.format(data.coins()));
            add(list, "coins_short", Numbers.compact(data.coins()));
            add(list, "gems", Numbers.format(data.gems()));
            add(list, "kills", data.kills());
            add(list, "deaths", data.deaths());
            add(list, "kd", Numbers.formatDecimal(Math.round(data.kills() * 100.0 / Math.max(1, data.deaths())) / 100.0));
            add(list, "playtime", Durations.formatSeconds(data.livePlaytime(now)));
            add(list, "mobkills", data.mobKills());
            add(list, "killstreak", data.killStreak());
            add(list, "daily_streak", data.dailyStreak());
            add(list, "event_points", data.eventPoints());
        } else {
            for (String k : new String[]{"coins", "coins_short", "gems", "kills", "deaths", "kd", "playtime", "mobkills",
                    "killstreak", "daily_streak", "event_points"}) {
                add(list, k, "…");
            }
        }
        for (String id : Glyphs.iconIds()) {
            add(list, "icon_" + id.toLowerCase(Locale.ROOT), Glyphs.icon(id, pack));
        }
        return list.toArray();
    }

    private static void add(List<Object> list, String key, Object value) {
        list.add(key);
        list.add(value);
    }
}
