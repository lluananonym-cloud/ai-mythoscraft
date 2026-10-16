package online.mythoscraft.core.progress;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.stats.LeaderboardService;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * Weekly leaderboard rewards: every week (default Sunday 20:00) the top 3 of coins, kills, playtime and won events get
 * gems – also when they are offline. Paid once per calendar week; the last paid week is stored in data/weekly.yml.
 */
public final class WeeklyRewardService {

    private static final Stat[] STATS = {Stat.COINS, Stat.KILLS, Stat.PLAYTIME, Stat.EVENTS_WON, Stat.MOB_KILLS};

    private final MythosCraft plugin;
    private final YamlStore store;
    private boolean enabled = true;
    private DayOfWeek day = DayOfWeek.SUNDAY;
    private LocalTime time = LocalTime.of(20, 0);
    private long[] gems = {25, 15, 8};

    public WeeklyRewardService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/weekly.yml");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("weekly-rewards");
        enabled = cfg.bool("enabled", true);
        day = cfg.enumValue("day", DayOfWeek.class, DayOfWeek.SUNDAY);
        List<LocalTime> times = cfg.times("time");
        time = times.isEmpty() ? LocalTime.of(20, 0) : times.get(0);
        gems = new long[]{cfg.longValue("gems.1", 25, 0, 100_000), cfg.longValue("gems.2", 15, 0, 100_000),
                cfg.longValue("gems.3", 8, 0, 100_000)};
        store.load(false);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20 * 60, 20 * 60, "weekly rewards", this::tick);
    }

    static String weekId(ZonedDateTime now) {
        return now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** True if the payout for {@code now}'s week is due (right day and time reached, not yet paid). */
    static boolean due(ZonedDateTime now, DayOfWeek day, LocalTime time, String lastPaid) {
        return now.getDayOfWeek() == day && !now.toLocalTime().isBefore(time) && !weekId(now).equals(lastPaid);
    }

    private void tick() {
        if (!enabled) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        YamlConfiguration yaml = store.yaml();
        if (!due(now, day, time, yaml.getString("last-paid", ""))) {
            return;
        }
        yaml.set("last-paid", weekId(now));
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
        plugin.messages().broadcast("weekly.header");
        for (Stat stat : STATS) {
            plugin.leaderboards().top(stat, 3).thenAccept(top -> plugin.tasks().sync(() -> pay(stat, top)));
        }
    }

    private void pay(Stat stat, List<LeaderboardService.Entry> top) {
        for (int i = 0; i < top.size() && i < 3; i++) {
            LeaderboardService.Entry e = top.get(i);
            if (e.value() <= 0 || gems[i] <= 0) {
                continue;
            }
            plugin.economy().credit(e.uuid(), Currency.GEMS, gems[i], null, "weekly-" + stat.id());
            plugin.messages().broadcast("weekly.line", "stat", plugin.messages().get("stats.names." + stat.id()), "place", i + 1,
                    "player", e.name(), "gems", gems[i]);
        }
    }

    /** Admin trigger (/mythos weekly): pays right now regardless of the schedule. */
    public void payNow() {
        YamlConfiguration yaml = store.yaml();
        yaml.set("last-paid", weekId(ZonedDateTime.now(plugin.multipliers().zone())));
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
        plugin.messages().broadcast("weekly.header");
        for (Stat stat : STATS) {
            plugin.leaderboards().top(stat, 3).thenAccept(top -> plugin.tasks().sync(() -> pay(stat, top)));
        }
    }
}
