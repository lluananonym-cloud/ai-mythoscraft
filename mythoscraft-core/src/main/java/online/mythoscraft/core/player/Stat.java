package online.mythoscraft.core.player;

import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Numbers;

import java.util.Locale;
import java.util.function.ToLongFunction;

/** Leaderboard statistics. The column names are fixed whitelisted identifiers, never user input. */
public enum Stat {
    COINS("coins", "coins", PlayerData::coins),
    GEMS("gems", "gems", PlayerData::gems),
    KILLS("kills", "kills", PlayerData::kills),
    DEATHS("deaths", "deaths", PlayerData::deaths),
    PLAYTIME("playtime", "playtime", d -> d.livePlaytime(System.currentTimeMillis())),
    EVENT_POINTS("eventpoints", "event_points", PlayerData::eventPoints),
    EVENTS_WON("eventswon", "events_won", PlayerData::eventsWon),
    BOSS_DAMAGE("bossdamage", "boss_damage", PlayerData::bossDamage),
    MOB_KILLS("mobkills", "mob_kills", PlayerData::mobKills),
    RELICS("relics", "relics_found", PlayerData::relicsFound),
    CRATES("crates", "crates_opened", PlayerData::cratesOpened),
    STREAK("streak", "best_streak", PlayerData::bestStreak);

    private final String id;
    private final String column;
    private final ToLongFunction<PlayerData> getter;

    Stat(String id, String column, ToLongFunction<PlayerData> getter) {
        this.id = id;
        this.column = column;
        this.getter = getter;
    }

    public String id() {
        return id;
    }

    public String column() {
        return column;
    }

    public long value(PlayerData data) {
        return getter.applyAsLong(data);
    }

    public String format(long value) {
        return this == PLAYTIME ? Durations.formatSeconds(value) : Numbers.format(value);
    }

    public static Stat byId(String id) {
        String s = id.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (Stat stat : values()) {
            if (stat.id.equals(s)) {
                return stat;
            }
        }
        return null;
    }
}
