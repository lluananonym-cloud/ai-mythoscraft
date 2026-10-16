package online.mythoscraft.core.economy;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.entity.Player;

import java.time.LocalDate;

/** /daily – one claim per calendar day (configured time zone) with a growing streak. */
public final class DailyRewardService {

    private final MythosCraft plugin;
    private boolean enabled = true;
    private long baseCoins = 100;
    private long streakBonus = 25;
    private int streakCap = 7;
    private int gemsEvery = 7;
    private long gemsAmount = 3;
    private double gemChance = 0.3;
    private long gemsMin = 1;
    private long gemsMax = 3;
    private SoundSpec sound = SoundSpec.NONE;
    private final java.util.Map<Integer, java.util.List<online.mythoscraft.core.reward.Reward>> milestones = new java.util.TreeMap<>();

    public DailyRewardService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("daily");
        enabled = cfg.bool("enabled", true);
        baseCoins = cfg.longValue("base-coins", 100, 0, 1_000_000_000L);
        streakBonus = cfg.longValue("streak-bonus-coins", 25, 0, 1_000_000_000L);
        streakCap = cfg.integer("streak-cap", 7, 1, 365);
        gemsEvery = cfg.integer("gems-every-days", 7, 0, 365);
        gemsAmount = cfg.longValue("gems-amount", 3, 0, 1_000_000L);
        gemChance = cfg.decimal("gem-chance", 0.3, 0, 1);
        gemsMin = cfg.longValue("random-gems.min", 1, 0, 1_000_000L);
        gemsMax = Math.max(gemsMin, cfg.longValue("random-gems.max", 3, 0, 1_000_000L));
        SoundSpec s = SoundSpec.parse(cfg.string("sound", "entity.player.levelup 1 1.2"));
        sound = s == null ? SoundSpec.NONE : s;
        milestones.clear();
        ConfigReader ms = cfg.sub("streak-milestones");
        for (String day : ms.keys()) {
            try {
                int d = Integer.parseInt(day.trim());
                java.util.List<online.mythoscraft.core.reward.Reward> list =
                        online.mythoscraft.core.reward.RewardTable.parseList(ms, day);
                if (!list.isEmpty()) {
                    milestones.put(d, list);
                }
            } catch (NumberFormatException e) {
                ms.warn(day, "muss eine Zahl (Tage) sein");
            }
        }
    }

    /** The streak milestones (days → rewards), e.g. for the info menu. */
    public java.util.Map<Integer, java.util.List<online.mythoscraft.core.reward.Reward>> milestones() {
        return milestones;
    }

    public boolean enabled() {
        return enabled;
    }

    public void claim(Player player) {
        PlayerData data = plugin.playerData().get(player);
        if (data == null) {
            plugin.messages().send(player, "general.data-loading");
            return;
        }
        LocalDate today = plugin.multipliers().today();
        if (today.toString().equals(data.lastDaily())) {
            plugin.messages().send(player, "daily.already", "time", Durations.format(plugin.multipliers().untilMidnight()));
            return;
        }
        boolean continued = today.minusDays(1).toString().equals(data.lastDaily());
        int streak = continued ? data.dailyStreak() + 1 : 1;
        data.daily(today.toString(), streak);

        Rank rank = plugin.ranks().rankOf(data);
        long coins = baseCoins + streakBonus * (Math.min(streak, streakCap) - 1) + rank.dailyBonus();
        // random: either coins or gems (the gems grow a little with the streak)
        boolean gemDay = java.util.concurrent.ThreadLocalRandom.current().nextDouble() < gemChance;
        long earnedCoins = 0;
        long earnedGems = 0;
        long dayGems = 0;
        if (gemDay) {
            long g = java.util.concurrent.ThreadLocalRandom.current().nextLong(gemsMin, gemsMax + 1) + Math.min(streak, streakCap) / 3;
            dayGems = plugin.economy().earn(data, Currency.GEMS, g, true);
        } else {
            earnedCoins = plugin.economy().earn(data, Currency.COINS, coins, true);
        }
        if (gemsEvery > 0 && streak % gemsEvery == 0 && gemsAmount > 0) {
            earnedGems = plugin.economy().earn(data, Currency.GEMS, gemsAmount, true);
        }
        java.util.List<online.mythoscraft.core.reward.Reward> milestone = milestones.get(streak);
        if (milestone != null) {
            for (online.mythoscraft.core.reward.Reward r : milestone) {
                r.give(plugin, player, 1.0);
                plugin.messages().send(player, "daily.milestone", "streak", streak, "reward", r.describe(plugin, 1.0));
            }
            plugin.messages().broadcast("daily.milestone-broadcast", "player", player.getName(), "streak", streak);
        }
        plugin.playerData().save(data);
        sound.play(player);
        if (gemDay) {
            plugin.messages().send(player, "daily.claimed-gems", "gems", dayGems, "streak", streak);
        } else {
            plugin.messages().send(player, "daily.claimed", "coins", earnedCoins, "streak", streak);
        }
        if (earnedGems > 0) {
            plugin.messages().send(player, "daily.bonus-gems", "gems", earnedGems, "streak", streak);
        }
        if (!continued && data.dailyStreak() == 1) {
            plugin.messages().send(player, "daily.streak-hint");
        }
    }

    public boolean canClaim(PlayerData data) {
        return enabled && data != null && !plugin.multipliers().today().toString().equals(data.lastDaily());
    }
}
