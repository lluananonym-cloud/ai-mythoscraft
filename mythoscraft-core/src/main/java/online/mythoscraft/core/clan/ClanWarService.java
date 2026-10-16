package online.mythoscraft.core.clan;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clan war without PvP: every week the clans collect points through what their members do anyway – event places,
 * quests, job levels. On Sunday evening the clan with the most points wins: coins into the clan bank, gems for
 * every member online and a crown in front of the clan tag for a whole week.
 */
public final class ClanWarService {

    public enum Source { EVENT_WIN, EVENT_PLACE, EVENT_JOIN, DAILY_QUEST, WEEKLY_QUEST, JOB_LEVEL }

    private final MythosCraft plugin;
    private final File file;
    private YamlConfiguration data = new YamlConfiguration();
    private boolean enabled = true;
    private final Map<Source, Integer> points = new HashMap<>();
    private DayOfWeek endDay = DayOfWeek.SUNDAY;
    private LocalTime endTime = LocalTime.of(20, 0);
    private long bankPrize = 25_000;
    private long memberGems = 15;

    public ClanWarService(MythosCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clanwar.yml");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("clans.war");
        enabled = cfg.bool("enabled", true);
        endDay = cfg.enumValue("end.day", DayOfWeek.class, DayOfWeek.SUNDAY);
        try {
            endTime = LocalTime.parse(cfg.string("end.time", "20:00"));
        } catch (RuntimeException e) {
            endTime = LocalTime.of(20, 0);
        }
        bankPrize = cfg.longValue("prize.clan-bank", 25_000, 0, 1_000_000_000L);
        memberGems = cfg.longValue("prize.gems-per-member", 15, 0, 100_000);
        points.put(Source.EVENT_WIN, cfg.integer("points.event-win", 10, 0, 10_000));
        points.put(Source.EVENT_PLACE, cfg.integer("points.event-top3", 5, 0, 10_000));
        points.put(Source.EVENT_JOIN, cfg.integer("points.event-join", 1, 0, 10_000));
        points.put(Source.DAILY_QUEST, cfg.integer("points.daily-quest", 3, 0, 10_000));
        points.put(Source.WEEKLY_QUEST, cfg.integer("points.weekly-quest", 10, 0, 10_000));
        points.put(Source.JOB_LEVEL, cfg.integer("points.job-level", 2, 0, 10_000));
        data = YamlConfiguration.loadConfiguration(file);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 200, 600, "clan war", this::check);
    }

    /** "2026-W41" – the war week (it ends on the configured day/time, so Sunday 20:00+ already counts for the next one). */
    String week(ZonedDateTime now) {
        ZonedDateTime end = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(endDay)).with(endTime);
        if (!now.isBefore(end)) {
            end = end.plusWeeks(1);
        }
        return end.get(IsoFields.WEEK_BASED_YEAR) + "-W" + end.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(plugin.multipliers().zone());
    }

    /** Adds points for the clan of the player (nothing if the player has no clan). */
    public void add(UUID player, Source source) {
        if (!enabled) {
            return;
        }
        ClanService.Clan clan = plugin.clans().of(player);
        int amount = points.getOrDefault(source, 0);
        if (clan == null || amount <= 0) {
            return;
        }
        check();
        String w = week(now());
        String path = "weeks." + w + "." + clan.id();
        data.set(path + ".points", data.getLong(path + ".points") + amount);
        data.set(path + ".members." + player, data.getLong(path + ".members." + player) + amount);
        save();
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            plugin.messages().actionBar(p, "clans.war.points", "points", amount, "clan", clan.name());
        }
    }

    /** Clan ids sorted by points of the current week. */
    public List<Map.Entry<String, Long>> ranking() {
        return ranking(week(now()));
    }

    private List<Map.Entry<String, Long>> ranking(String w) {
        List<Map.Entry<String, Long>> out = new ArrayList<>();
        ConfigurationSection sec = data.getConfigurationSection("weeks." + w);
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                out.add(Map.entry(id, sec.getLong(id + ".points")));
            }
        }
        out.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return out;
    }

    public long points(ClanService.Clan clan) {
        return data.getLong("weeks." + week(now()) + "." + clan.id() + ".points");
    }

    /** Clan id of last week's winner (crown in front of the tag). */
    public String champion() {
        return data.getString("champion", "");
    }

    public java.time.Duration timeLeft() {
        ZonedDateTime now = now();
        ZonedDateTime end = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(endDay)).with(endTime);
        if (!now.isBefore(end)) {
            end = end.plusWeeks(1);
        }
        return java.time.Duration.between(now, end);
    }

    /** When a week is over: crown the winner and pay the prizes (once). */
    private void check() {
        if (!enabled) {
            return;
        }
        String current = week(now());
        String last = data.getString("current-week", "");
        if (last.isEmpty()) {
            data.set("current-week", current);
            save();
            return;
        }
        if (last.equals(current)) {
            return;
        }
        data.set("current-week", current);
        List<Map.Entry<String, Long>> ranking = ranking(last);
        if (!ranking.isEmpty() && ranking.get(0).getValue() > 0) {
            ClanService.Clan winner = null;
            for (ClanService.Clan c : plugin.clans().all()) {
                if (c.id().equals(ranking.get(0).getKey())) {
                    winner = c;
                }
            }
            if (winner != null) {
                data.set("champion", winner.id());
                plugin.clans().addBank(winner, bankPrize);
                for (Player p : plugin.clans().online(winner)) {
                    PlayerData pd = plugin.playerData().get(p);
                    if (pd != null && memberGems > 0) {
                        plugin.economy().earn(pd, Currency.GEMS, memberGems, false);
                    }
                }
                plugin.messages().broadcast("clans.war.winner", "clan", winner.name(), "points", ranking.get(0).getValue(),
                        "coins", bankPrize, "gems", memberGems);
            }
        }
        // keep only the last few weeks
        ConfigurationSection weeks = data.getConfigurationSection("weeks");
        if (weeks != null) {
            for (String w : weeks.getKeys(false)) {
                if (!w.equals(current) && !w.equals(last)) {
                    data.set("weeks." + w, null);
                }
            }
        }
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("clanwar.yml: " + e.getMessage());
        }
    }

    public boolean enabled() {
        return enabled;
    }
}
