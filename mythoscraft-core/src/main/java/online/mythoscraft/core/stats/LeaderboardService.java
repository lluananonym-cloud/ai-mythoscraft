package online.mythoscraft.core.stats;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.player.Stat;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Top lists from the database, corrected with the live values of online players (whose newest numbers may not be
 * saved yet). Results are cached briefly so spamming /mythos top never floods the database.
 */
public final class LeaderboardService {

    public record Entry(UUID uuid, String name, long value) {
    }

    private record Cached(List<Entry> entries, int limit, long time) {
    }

    private static final long CACHE_MILLIS = 30_000;

    private final MythosCraft plugin;
    private final Map<Stat, Cached> cache = new EnumMap<>(Stat.class);

    public LeaderboardService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    /** Completes on the main thread. */
    public CompletableFuture<List<Entry>> top(Stat stat, int limit) {
        Cached cached = cache.get(stat);
        if (cached != null && cached.limit >= limit && System.currentTimeMillis() - cached.time < CACHE_MILLIS) {
            return CompletableFuture.completedFuture(cached.entries.subList(0, Math.min(limit, cached.entries.size())));
        }
        int fetch = limit + Bukkit.getOnlinePlayers().size();
        CompletableFuture<List<Entry>> result = new CompletableFuture<>();
        plugin.database().query("leaderboard " + stat.id(), c -> plugin.playerData().repository().top(c, stat, fetch))
                .whenComplete((rows, ex) -> plugin.tasks().sync(() -> {
                    if (ex != null) {
                        result.completeExceptionally(ex);
                        return;
                    }
                    Map<UUID, Entry> merged = new LinkedHashMap<>();
                    for (PlayerData.Snapshot s : rows) {
                        merged.put(s.uuid(), new Entry(s.uuid(), s.name(), value(stat, s)));
                    }
                    for (PlayerData live : plugin.playerData().cached()) {
                        merged.put(live.uuid(), new Entry(live.uuid(), live.name(), stat.value(live)));
                    }
                    List<Entry> list = new ArrayList<>(merged.values());
                    list.sort(Comparator.comparingLong(Entry::value).reversed());
                    List<Entry> top = new ArrayList<>(list.subList(0, Math.min(limit, list.size())));
                    cache.put(stat, new Cached(top, limit, System.currentTimeMillis()));
                    result.complete(top);
                }));
        return result;
    }

    private static long value(Stat stat, PlayerData.Snapshot s) {
        return switch (stat) {
            case COINS -> s.coins();
            case GEMS -> s.gems();
            case KILLS -> s.kills();
            case DEATHS -> s.deaths();
            case PLAYTIME -> s.playtime();
            case EVENT_POINTS -> s.eventPoints();
            case EVENTS_WON -> s.eventsWon();
            case BOSS_DAMAGE -> s.bossDamage();
            case MOB_KILLS -> s.mobKills();
            case RELICS -> s.relicsFound();
            case CRATES -> s.cratesOpened();
            case STREAK -> s.bestStreak();
        };
    }

    public void invalidate() {
        cache.clear();
    }
}
