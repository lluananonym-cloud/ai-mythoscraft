package online.mythoscraft.core.player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cached state of one player. Mutated on the main thread only; persisted through immutable {@link Snapshot}s on
 * the database thread. Every mutator marks the data dirty so the autosave picks it up.
 */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private long coins;
    private long gems;
    private int kills;
    private int deaths;
    private int mobKills;
    private long playtime;
    private long eventPoints;
    private int eventsWon;
    private long bossDamage;
    private int relicsFound;
    private int cratesOpened;
    private int bestStreak;
    private String rankId;
    private long rankExpires;
    private int dailyStreak;
    private String lastDaily;
    private long firstJoin;
    private long lastSeen;

    private final Map<String, Home> homes = new LinkedHashMap<>();
    private volatile boolean dirty;
    private boolean fresh;
    private long playtimeAnchor;
    private int killStreak;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static PlayerData fromSnapshot(Snapshot s) {
        PlayerData d = new PlayerData(s.uuid(), s.name());
        d.coins = s.coins();
        d.gems = s.gems();
        d.kills = s.kills();
        d.deaths = s.deaths();
        d.mobKills = s.mobKills();
        d.playtime = s.playtime();
        d.eventPoints = s.eventPoints();
        d.eventsWon = s.eventsWon();
        d.bossDamage = s.bossDamage();
        d.relicsFound = s.relicsFound();
        d.cratesOpened = s.cratesOpened();
        d.bestStreak = s.bestStreak();
        d.rankId = s.rankId();
        d.rankExpires = s.rankExpires();
        d.dailyStreak = s.dailyStreak();
        d.lastDaily = s.lastDaily();
        d.firstJoin = s.firstJoin();
        d.lastSeen = s.lastSeen();
        return d;
    }

    public Snapshot snapshot() {
        return new Snapshot(uuid, name, coins, gems, kills, deaths, mobKills, playtime, eventPoints, eventsWon, bossDamage,
                relicsFound, cratesOpened, bestStreak, rankId, rankExpires, dailyStreak, lastDaily, firstJoin, lastSeen);
    }

    // ---- session / playtime -------------------------------------------------------------------------------

    public void startSession(long now) {
        playtimeAnchor = now;
        killStreak = 0;
    }

    /** Moves elapsed online time into the persistent playtime counter. */
    public void flushPlaytime(long now) {
        if (playtimeAnchor <= 0) {
            return;
        }
        long seconds = (now - playtimeAnchor) / 1000;
        if (seconds > 0) {
            playtime += seconds;
            playtimeAnchor += seconds * 1000;
            dirty = true;
        }
    }

    public void endSession(long now) {
        flushPlaytime(now);
        playtimeAnchor = 0;
    }

    /** Playtime including the not yet flushed part of the current session. */
    public long livePlaytime(long now) {
        return playtime + (playtimeAnchor > 0 ? (now - playtimeAnchor) / 1000 : 0);
    }

    // ---- accessors ----------------------------------------------------------------------------------------

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (!name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    public long coins() {
        return coins;
    }

    public void coins(long value) {
        this.coins = value;
        dirty = true;
    }

    public long gems() {
        return gems;
    }

    public void gems(long value) {
        this.gems = value;
        dirty = true;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int mobKills() {
        return mobKills;
    }

    public long playtime() {
        return playtime;
    }

    public long eventPoints() {
        return eventPoints;
    }

    public int eventsWon() {
        return eventsWon;
    }

    public long bossDamage() {
        return bossDamage;
    }

    public int relicsFound() {
        return relicsFound;
    }

    public int cratesOpened() {
        return cratesOpened;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public int killStreak() {
        return killStreak;
    }

    /** Adds a kill and returns the new streak. */
    public int addKill() {
        kills++;
        killStreak++;
        if (killStreak > bestStreak) {
            bestStreak = killStreak;
        }
        dirty = true;
        return killStreak;
    }

    public void addDeath() {
        deaths++;
        killStreak = 0;
        dirty = true;
    }

    public void addMobKill() {
        mobKills++;
        dirty = true;
    }

    public void addEventPoints(long points) {
        eventPoints += Math.max(0, points);
        dirty = true;
    }

    public void addEventWin() {
        eventsWon++;
        dirty = true;
    }

    public void addBossDamage(long damage) {
        bossDamage += Math.max(0, damage);
        dirty = true;
    }

    public void addRelic() {
        relicsFound++;
        dirty = true;
    }

    public void addCrateOpened() {
        cratesOpened++;
        dirty = true;
    }

    public String rankId() {
        return rankId;
    }

    public long rankExpires() {
        return rankExpires;
    }

    /** @param expires epoch millis, 0 for permanent */
    public void rank(String rankId, long expires) {
        this.rankId = rankId;
        this.rankExpires = expires;
        dirty = true;
    }

    public int dailyStreak() {
        return dailyStreak;
    }

    public String lastDaily() {
        return lastDaily;
    }

    public void daily(String date, int streak) {
        this.lastDaily = date;
        this.dailyStreak = streak;
        dirty = true;
    }

    public long firstJoin() {
        return firstJoin;
    }

    public void firstJoin(long time) {
        this.firstJoin = time;
        dirty = true;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long time) {
        this.lastSeen = time;
        dirty = true;
    }

    public Map<String, Home> homes() {
        return Collections.unmodifiableMap(homes);
    }

    public Home home(String name) {
        return homes.get(name.toLowerCase(Locale.ROOT));
    }

    public void putHome(Home home) {
        homes.put(home.name().toLowerCase(Locale.ROOT), home);
    }

    public Home removeHome(String name) {
        return homes.remove(name.toLowerCase(Locale.ROOT));
    }

    void loadHomes(Map<String, Home> loaded) {
        homes.clear();
        loaded.values().forEach(this::putHome);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    void clearDirty() {
        dirty = false;
    }

    /** True for the very first session of a player (row was just created). */
    public boolean isFresh() {
        return fresh;
    }

    void fresh(boolean fresh) {
        this.fresh = fresh;
    }

    public record Snapshot(UUID uuid, String name, long coins, long gems, int kills, int deaths, int mobKills, long playtime,
                           long eventPoints, int eventsWon, long bossDamage, int relicsFound, int cratesOpened,
                           int bestStreak, String rankId, long rankExpires, int dailyStreak, String lastDaily,
                           long firstJoin, long lastSeen) {
    }
}
