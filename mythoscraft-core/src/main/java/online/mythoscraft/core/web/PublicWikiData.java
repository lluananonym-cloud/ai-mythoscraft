package online.mythoscraft.core.web;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.item.SeasonService;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.tournament.TournamentTier;
import org.bukkit.Bukkit;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public, login-free data for the wiki website: leaderboards, the running and next event, tournament dates,
 * seasons. Nothing private (no coordinates, no IPs). Leaderboards are refreshed every minute in the background.
 */
public final class PublicWikiData {

    private static final Stat[] BOARDS = {Stat.COINS, Stat.KILLS, Stat.PLAYTIME, Stat.EVENTS_WON, Stat.EVENT_POINTS,
            Stat.BOSS_DAMAGE, Stat.MOB_KILLS, Stat.CRATES, Stat.STREAK};

    private final MythosCraft plugin;
    private final Map<Stat, List<Map<String, Object>>> boards = new EnumMap<>(Stat.class);

    public PublicWikiData(MythosCraft plugin) {
        this.plugin = plugin;
        plugin.tasks().repeat(this, 100, 1200, "wiki leaderboards", this::refresh);
    }

    private void refresh() {
        for (Stat stat : BOARDS) {
            plugin.leaderboards().top(stat, 10).thenAccept(list -> {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (var e : list) {
                    rows.add(Map.of("name", e.name() == null ? "?" : e.name(), "uuid", e.uuid().toString(), "value", e.value()));
                }
                boards.put(stat, rows);
            });
        }
    }

    private static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Must run on the main thread. */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generatedAt", System.currentTimeMillis());
        m.put("online", Bukkit.getOnlinePlayers().size());
        m.put("max", Bukkit.getMaxPlayers());
        // seasons
        List<Map<String, Object>> seasons = new ArrayList<>();
        SeasonService.Season active = null;
        try {
            active = plugin.seasons().active();
            for (SeasonService.Season s : plugin.seasons().all()) {
                seasons.add(Map.of("id", s.id(), "name", plain(online.mythoscraft.core.util.Text.parse(s.name())),
                        "start", s.start().toString().substring(2), "end", s.end().toString().substring(2),
                        "crate", s.crate() == null ? "" : s.crate()));
            }
        } catch (RuntimeException ignored) {
            // seasons disabled
        }
        m.put("seasons", seasons);
        m.put("season", active == null ? null : active.id());
        // events
        MythosEvent ev = plugin.events().active();
        m.put("event", ev == null ? null : Map.of("id", ev.id(), "name", plain(ev.displayName())));
        long next = plugin.events().nextAutoMillis();
        m.put("nextEventAt", next < 0 ? null : System.currentTimeMillis() + next);
        List<Map<String, Object>> events = new ArrayList<>();
        for (MythosEvent e : plugin.events().all()) {
            events.add(Map.of("id", e.id(), "name", plain(e.displayName())));
        }
        m.put("events", events);
        // tournaments
        List<Map<String, Object>> tours = new ArrayList<>();
        for (TournamentTier tier : TournamentTier.values()) {
            ZonedDateTime at = null;
            try {
                at = plugin.tournaments().nextStart(tier);
            } catch (RuntimeException ignored) {
                // tier disabled
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("tier", tier.id());
            t.put("name", plain(plugin.tournaments().tierName(tier)));
            t.put("next", at == null ? null : at.toInstant().toEpochMilli());
            tours.add(t);
        }
        m.put("tournaments", tours);
        // leaderboards
        Map<String, Object> b = new LinkedHashMap<>();
        for (Stat s : BOARDS) {
            b.put(s.id(), boards.getOrDefault(s, List.of()));
        }
        m.put("leaderboards", b);
        return m;
    }
}
