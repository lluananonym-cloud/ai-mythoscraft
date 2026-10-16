package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * Daily rewards of the spawn activities (parkour, ring course, dropper, maze): coins once per activity and day, but
 * gems only for the first {@code gemActivities} activities of the day (default 2).
 */
public final class ActivityRewards {

    private ActivityRewards() {
    }

    /**
     * Pays the daily reward of an activity (key e.g. "parkour") once per day. Returns false if it was already paid
     * today.
     */
    public static boolean pay(MythosCraft plugin, Player p, String activity, long gems, long coins) {
        plugin.quests().count(p, online.mythoscraft.core.progress.QuestService.Type.ACTIVITY, 1);
        plugin.bets().finished(p, activity);
        long today = plugin.multipliers().today().toEpochDay();
        String dayKey = activity + ".reward-day";
        if (plugin.progress().get(p, dayKey) == today) {
            return false;
        }
        PlayerData data = plugin.playerData().get(p);
        if (data == null) {
            return false;
        }
        plugin.progress().set(p, dayKey, today);
        if (plugin.progress().get(p, "activity.day") != today) {
            plugin.progress().set(p, "activity.day", today);
            plugin.progress().set(p, "activity.gems", 0);
        }
        int limit = plugin.rootConfig().integer("attractions.gem-activities-per-day", 2, 0, 100);
        boolean gemsAllowed = plugin.progress().get(p, "activity.gems") < limit;
        long gotGems = 0;
        if (gemsAllowed && gems > 0) {
            plugin.progress().add(p, "activity.gems", 1);
            gotGems = plugin.economy().earn(data, Currency.GEMS, gems, false);
        }
        long gotCoins = coins > 0 ? plugin.economy().earn(data, Currency.COINS, coins, false) : 0;
        plugin.playerData().save(data);
        if (gotGems > 0) {
            plugin.messages().send(p, "attractions.activity-reward", "gems", gotGems, "coins", gotCoins,
                    "left", Math.max(0, limit - plugin.progress().get(p, "activity.gems")));
        } else {
            plugin.messages().send(p, "attractions.activity-reward-coins", "coins", gotCoins, "limit", limit);
        }
        return true;
    }
}
