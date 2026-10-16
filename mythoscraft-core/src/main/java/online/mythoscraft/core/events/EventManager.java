package online.mythoscraft.core.events;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.impl.AncientHuntEvent;
import online.mythoscraft.core.events.impl.AncientWardenEvent;
import online.mythoscraft.core.events.impl.CoinRainEvent;
import online.mythoscraft.core.events.impl.CollectEvent;
import online.mythoscraft.core.events.impl.FishingEvent;
import online.mythoscraft.core.events.impl.HarvestEvent;
import online.mythoscraft.core.events.impl.KillEvent;
import online.mythoscraft.core.events.impl.LumberjackEvent;
import online.mythoscraft.core.events.impl.ParkourRaceEvent;
import online.mythoscraft.core.events.impl.SeasonBossEvent;
import online.mythoscraft.core.events.impl.SnowballFightEvent;
import online.mythoscraft.core.events.impl.GemRushEvent;
import online.mythoscraft.core.events.impl.MeteorEvent;
import online.mythoscraft.core.events.impl.TreasureHuntEvent;
import online.mythoscraft.core.util.WeightedRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Owns all events, runs at most one at a time and schedules automatic events. */
public final class EventManager {

    public enum StartResult { STARTED, UNKNOWN, DISABLED, ALREADY_RUNNING, COOLDOWN, NOT_ENOUGH_PLAYERS, SYSTEM_DISABLED }

    private enum AutoMode { OFF, INTERVAL, TIMES }

    private final MythosCraft plugin;
    private final Map<String, MythosEvent> events = new LinkedHashMap<>();
    private MythosEvent active;
    private boolean systemEnabled = true;
    private AutoMode autoMode = AutoMode.INTERVAL;
    private int autoIntervalMinutes = 45;
    private int autoMinPlayers = 2;
    private List<LocalTime> autoTimes = List.of();
    private long nextAuto;
    private LocalTime lastTimeTriggered;
    private int[] placementPoints = {10, 7, 5, 3, 1};

    public EventManager(MythosCraft plugin) {
        this.plugin = plugin;
        register(new CoinRainEvent(plugin));
        register(new GemRushEvent(plugin));
        register(new AncientHuntEvent(plugin));
        register(new MeteorEvent(plugin));
        register(new AncientWardenEvent(plugin));
        register(new CollectEvent(plugin, "pumpkinhunt", CollectEvent.Style.PUMPKINS));
        register(new KillEvent(plugin, "ghostnight", KillEvent.Mobs.GHOSTS));
        register(new SeasonBossEvent(plugin, "pumpkinking", SeasonBossEvent.Kind.PUMPKIN_KING));
        register(new CollectEvent(plugin, "giftrain", CollectEvent.Style.GIFTS));
        register(new SnowballFightEvent(plugin));
        register(new SeasonBossEvent(plugin, "frostgiant", SeasonBossEvent.Kind.FROST_GIANT));
        register(new CollectEvent(plugin, "starfall", CollectEvent.Style.STARS));
        register(new CollectEvent(plugin, "egghunt", CollectEvent.Style.EGGS));
        register(new KillEvent(plugin, "mobhunt", KillEvent.Mobs.ANY_HOSTILE));
        register(new FishingEvent(plugin));
        register(new HarvestEvent(plugin));
        register(new CollectEvent(plugin, "goldrush", CollectEvent.Style.GOLD));
        register(new CollectEvent(plugin, "keyrain", CollectEvent.Style.KEYS));
        register(new LumberjackEvent(plugin));
        register(new KillEvent(plugin, "chickenhunt", KillEvent.Mobs.CHICKENS));
        register(new ParkourRaceEvent(plugin));
        // more easy events (spawn / everywhere)
        register(new CollectEvent(plugin, "diamondrain", CollectEvent.Style.DIAMONDS));
        register(new CollectEvent(plugin, "crystalhunt", CollectEvent.Style.CRYSTALS));
        register(new CollectEvent(plugin, "mushroomhunt", CollectEvent.Style.MUSHROOMS));
        register(new CollectEvent(plugin, "featherfall", CollectEvent.Style.FEATHERS));
        register(new KillEvent(plugin, "slimeplague", KillEvent.Mobs.SLIMES));
        register(new KillEvent(plugin, "spidernight", KillEvent.Mobs.SPIDERS));
        register(new KillEvent(plugin, "pigchase", KillEvent.Mobs.PIGS));
        // seasonal easy events
        register(new CollectEvent(plugin, "luckrain", CollectEvent.Style.LUCKY));
        register(new CollectEvent(plugin, "petalrain", CollectEvent.Style.PETALS));
        register(new CollectEvent(plugin, "heartrain", CollectEvent.Style.HEARTS));
        register(new CollectEvent(plugin, "shellhunt", CollectEvent.Style.SHELLS));
        // daily bosses (arenas)
        register(new SeasonBossEvent(plugin, "spiderqueen", SeasonBossEvent.Kind.SPIDER_QUEEN));
        register(new SeasonBossEvent(plugin, "boneking", SeasonBossEvent.Kind.BONE_KING));
        register(new SeasonBossEvent(plugin, "magmalord", SeasonBossEvent.Kind.MAGMA_LORD));
        register(new SeasonBossEvent(plugin, "swampwitch", SeasonBossEvent.Kind.SWAMP_WITCH));
        register(new SeasonBossEvent(plugin, "stormknight", SeasonBossEvent.Kind.STORM_KNIGHT));
        register(new SeasonBossEvent(plugin, "killerbunny", SeasonBossEvent.Kind.KILLER_BUNNY));
        // end of the month
        register(new SeasonBossEvent(plugin, "mythostitan", SeasonBossEvent.Kind.MYTHOS_TITAN));
        // all day in the background
        register(new TreasureHuntEvent(plugin));
    }

    // ---- schedule: hourly / daily / Friday / end of month / all day ---------------------------------------

    private record Pending(String id, String arena, int seconds) {
    }

    private boolean scheduleOn;
    private int hourlyMinute = 30;
    private int hourlyMinPlayers = 1;
    private List<String> hourlyPool = List.of();
    private LocalTime dailyFrom = LocalTime.of(17, 0);
    private LocalTime dailyTo = LocalTime.of(21, 0);
    private List<String> dailyPool = List.of();
    private java.time.DayOfWeek weeklyDay = java.time.DayOfWeek.FRIDAY;
    private LocalTime weeklyTime = LocalTime.of(17, 0);
    private int weeklyMinutes = 60;
    private List<String> weeklyPool = List.of();
    private LocalTime monthlyTime = LocalTime.of(19, 0);
    private int monthlyMinutes = 45;
    private List<String> monthlyPool = List.of();
    private boolean allDayOn;
    private LocalTime allDayFrom = LocalTime.of(10, 0);
    private LocalTime allDayTo = LocalTime.of(22, 0);
    private java.util.Set<java.time.DayOfWeek> allDayDays = java.util.EnumSet.of(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
    private boolean allDayLastOfMonth = true;
    private java.util.Set<java.time.MonthDay> holidays = new java.util.HashSet<>();
    private List<String> allDayPool = List.of();
    private double seasonBoost = 4;
    private MythosEvent background;
    /** Day the all-day event already ran (in memory: after a restart it continues). */
    private String allDayDone = "";
    private final java.util.ArrayDeque<Pending> pending = new java.util.ArrayDeque<>();
    private org.bukkit.configuration.file.YamlConfiguration scheduleState;

    private void loadSchedule(ConfigReader cfg) {
        ConfigReader s = cfg.sub("schedule");
        scheduleOn = s.bool("enabled", true);
        hourlyMinute = s.integer("hourly.minute", 30, 0, 59);
        hourlyMinPlayers = s.integer("hourly.min-players", 1, 0, 1000);
        hourlyPool = ids(s.strings("hourly.pool"));
        dailyFrom = time(s.string("daily.from", "17:00"), LocalTime.of(17, 0));
        dailyTo = time(s.string("daily.to", "21:00"), LocalTime.of(21, 0));
        dailyPool = ids(s.strings("daily.pool"));
        weeklyDay = s.enumValue("weekly.day", java.time.DayOfWeek.class, java.time.DayOfWeek.FRIDAY);
        weeklyTime = time(s.string("weekly.time", "17:00"), LocalTime.of(17, 0));
        weeklyMinutes = s.integer("weekly.duration-minutes", 60, 5, 600);
        weeklyPool = ids(s.strings("weekly.pool"));
        monthlyTime = time(s.string("monthly.time", "19:00"), LocalTime.of(19, 0));
        monthlyMinutes = s.integer("monthly.duration-minutes", 45, 5, 600);
        monthlyPool = ids(s.strings("monthly.pool"));
        allDayOn = s.bool("all-day.enabled", true);
        allDayFrom = time(s.string("all-day.from", "10:00"), LocalTime.of(10, 0));
        allDayTo = time(s.string("all-day.to", "22:00"), LocalTime.of(22, 0));
        allDayDays = java.util.EnumSet.noneOf(java.time.DayOfWeek.class);
        for (String d : s.strings("all-day.days")) {
            try {
                allDayDays.add(java.time.DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                s.warn("all-day.days", "'" + d + "' ist kein Wochentag");
            }
        }
        allDayLastOfMonth = s.bool("all-day.last-day-of-month", true);
        holidays = new java.util.HashSet<>();
        for (String d : s.strings("all-day.holidays")) {
            try {
                String[] p = d.trim().split("-");
                holidays.add(java.time.MonthDay.of(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
            } catch (RuntimeException e) {
                s.warn("all-day.holidays", "'" + d + "' ist kein Datum (MM-TT)");
            }
        }
        allDayPool = ids(s.strings("all-day.pool"));
        seasonBoost = s.decimal("season-boost", 4, 1, 100);
        java.io.File f = new java.io.File(plugin.getDataFolder(), "event-schedule.yml");
        scheduleState = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
    }

    private static List<String> ids(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            out.add(r.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", ""));
        }
        return out;
    }

    private static LocalTime time(String s, LocalTime def) {
        try {
            return LocalTime.parse(s.trim().length() == 4 ? "0" + s.trim() : s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private String state(String key) {
        return scheduleState == null ? "" : scheduleState.getString(key, "");
    }

    private void mark(String key, String value) {
        if (scheduleState == null) {
            return;
        }
        scheduleState.set(key, value);
        try {
            scheduleState.save(new java.io.File(plugin.getDataFolder(), "event-schedule.yml"));
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("event-schedule.yml: " + e.getMessage());
        }
    }

    /** Within two hours after the planned time (so a restart in between still catches it, but not at midnight). */
    private static boolean due(LocalTime now, LocalTime at) {
        return !now.isBefore(at) && now.isBefore(at.plusHours(2)) && at.plusHours(2).isAfter(at);
    }

    private void scheduleTick() {
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        java.time.LocalDate today = now.toLocalDate();
        LocalTime t = now.toLocalTime();
        String date = today.toString();
        int online = Bukkit.getOnlinePlayers().size();
        // end of the month
        if (today.getDayOfMonth() == today.lengthOfMonth() && due(t, monthlyTime) && !state("monthly").equals(date) && online > 0) {
            mark("monthly", date);
            queue(monthlyPool, "monthly", monthlyMinutes * 60);
        }
        // Friday afternoon
        if (today.getDayOfWeek() == weeklyDay && due(t, weeklyTime) && !state("weekly").equals(date) && online > 0) {
            mark("weekly", date);
            queue(weeklyPool, "weekly", weeklyMinutes * 60);
        }
        // once a day at a random time in the window
        if (!state("daily-date").equals(date)) {
            long span = Math.max(1, java.time.Duration.between(dailyFrom, dailyTo).toMinutes());
            LocalTime at = dailyFrom.plusMinutes(ThreadLocalRandom.current().nextLong(span));
            scheduleState.set("daily-at", at.toString());
            mark("daily-date", date);
            scheduleState.set("daily", "");
        }
        LocalTime dailyAt = time(state("daily-at"), dailyFrom);
        if (due(t, dailyAt) && !state("daily").equals(date) && online > 0) {
            mark("daily", date);
            queue(dailyPool, "daily", 0);
        }
        // every hour
        String hourKey = date + "T" + now.getHour();
        if (now.getMinute() == hourlyMinute && !state("hourly").equals(hourKey)) {
            mark("hourly", hourKey);
            if (active == null && pending.isEmpty() && online >= Math.max(1, hourlyMinPlayers)) {
                queue(hourlyPool, null, 0);
            }
        }
        // all day in the background
        boolean special = allDayDays.contains(today.getDayOfWeek()) || allDayLastOfMonth && today.getDayOfMonth() == today.lengthOfMonth()
                || holidays.contains(java.time.MonthDay.from(today));
        if (allDayOn && special && background == null && !t.isBefore(allDayFrom) && t.isBefore(allDayTo) && !date.equals(allDayDone)) {
            MythosEvent e = pick(allDayPool);
            if (e != null && e != active) {
                allDayDone = date;
                int seconds = (int) java.time.Duration.between(t, allDayTo).toSeconds();
                background = e;
                plugin.getLogger().info("All-day event: " + e.id() + " (" + seconds / 60 + " min)");
                e.begin(60, null, Math.max(600, seconds));
            }
        }
    }

    private void queue(List<String> pool, String arena, int seconds) {
        MythosEvent e = pick(pool);
        if (e != null) {
            pending.add(new Pending(e.id(), arena, seconds));
        }
    }

    /** Weighted pick from a pool; seasonal events only in their season and then much more likely. */
    private MythosEvent pick(List<String> pool) {
        List<MythosEvent> candidates = new ArrayList<>();
        for (String id : pool) {
            MythosEvent e = events.get(id);
            if (e != null && e.enabled() && inSeason(e) && e != background && e.state() == EventState.IDLE) {
                candidates.add(e);
            }
        }
        WeightedRandom<MythosEvent> w = new WeightedRandom<>(candidates, e -> Math.max(0.01, e.weight()) * (e.season().isEmpty() ? 1 : seasonBoost));
        return w.isEmpty() ? null : w.pick(ThreadLocalRandom.current());
    }

    private void startPending() {
        while (!pending.isEmpty()) {
            Pending p = pending.poll();
            MythosEvent e = events.get(p.id());
            if (e == null || e == background || e.state() != EventState.IDLE) {
                continue;
            }
            plugin.getLogger().info("Scheduled event: " + e.id() + (p.arena() != null ? " (arena " + p.arena() + ")" : ""));
            active = e;
            e.begin(e.countdownSeconds(), p.arena(), p.seconds());
            return;
        }
    }

    /** The event /event tp leads to: the running one, otherwise the all-day event. */
    public MythosEvent teleportTarget() {
        if (active != null && active.venue() != null) {
            return active;
        }
        if (background != null && background.venue() != null) {
            return background;
        }
        return active != null ? active : background;
    }

    public MythosEvent background() {
        return background;
    }

    private void register(MythosEvent event) {
        events.put(event.id(), event);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("events");
        systemEnabled = cfg.bool("enabled", true);
        autoMode = cfg.enumValue("auto.mode", AutoMode.class, AutoMode.INTERVAL);
        autoIntervalMinutes = cfg.integer("auto.interval-minutes", 45, 5, 1440);
        autoMinPlayers = cfg.integer("auto.min-players", 2, 0, 1000);
        autoTimes = cfg.times("auto.times");
        List<String> pts = cfg.strings("placement-points");
        if (!pts.isEmpty()) {
            List<Integer> parsed = new ArrayList<>();
            for (String s : pts) {
                try {
                    parsed.add(Math.max(0, Integer.parseInt(s.trim())));
                } catch (NumberFormatException e) {
                    cfg.warn("placement-points", "'" + s + "' ist keine Zahl");
                }
            }
            if (!parsed.isEmpty()) {
                placementPoints = parsed.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        for (MythosEvent e : events.values()) {
            e.load(cfg.sub(e.id()));
        }
        loadSchedule(cfg);
        scheduleNextAuto();
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 20, "events", this::tick);
        plugin.getLogger().info("Event system initialized (" + events.size() + " events, auto: " + autoMode.name().toLowerCase(Locale.ROOT) + ")");
    }

    private void scheduleNextAuto() {
        nextAuto = System.currentTimeMillis() + autoIntervalMinutes * 60_000L;
    }

    private void tick() {
        if (background != null) {
            background.tick();
        }
        if (systemEnabled && scheduleOn) {
            try {
                scheduleTick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "event schedule", e);
            }
        }
        if (active != null) {
            active.tick();
            return;
        }
        if (!systemEnabled) {
            return;
        }
        if (scheduleOn) {
            startPending();
            return;
        }
        if (autoMode == AutoMode.INTERVAL && System.currentTimeMillis() >= nextAuto) {
            scheduleNextAuto();
            startRandom();
        } else if (autoMode == AutoMode.TIMES && !autoTimes.isEmpty()) {
            LocalTime now = ZonedDateTime.now(plugin.multipliers().zone()).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            if (autoTimes.contains(now) && !now.equals(lastTimeTriggered)) {
                lastTimeTriggered = now;
                startRandom();
            }
        }
    }

    private void startRandom() {
        if (Bukkit.getOnlinePlayers().size() < autoMinPlayers) {
            return;
        }
        List<MythosEvent> candidates = new ArrayList<>();
        for (MythosEvent e : events.values()) {
            if (e.enabled() && e.cooldownRemainingMillis() == 0 && Bukkit.getOnlinePlayers().size() >= e.minPlayers() && inSeason(e)) {
                candidates.add(e);
            }
        }
        WeightedRandom<MythosEvent> pick = new WeightedRandom<>(candidates, MythosEvent::weight);
        if (!pick.isEmpty()) {
            MythosEvent chosen = pick.pick(ThreadLocalRandom.current());
            plugin.getLogger().info("Automatic event: " + chosen.id());
            begin(chosen, chosen.countdownSeconds());
        }
    }

    /** Seasonal events only start automatically while their season runs. */
    public boolean inSeason(MythosEvent e) {
        if (e.season().isEmpty()) {
            return true;
        }
        online.mythoscraft.core.item.SeasonService.Season active = plugin.seasons().active();
        return active != null && active.id().equalsIgnoreCase(e.season());
    }

    /**
     * @param force admins skip cooldown and player minimum
     */
    public StartResult start(String id, boolean force, Integer countdown) {
        if (!systemEnabled && !force) {
            return StartResult.SYSTEM_DISABLED;
        }
        MythosEvent event = get(id);
        if (event == null) {
            return StartResult.UNKNOWN;
        }
        if (active != null) {
            return StartResult.ALREADY_RUNNING;
        }
        if (!event.enabled() && !force) {
            return StartResult.DISABLED;
        }
        if (!force && event.cooldownRemainingMillis() > 0) {
            return StartResult.COOLDOWN;
        }
        if (!force && !inSeason(event)) {
            return StartResult.DISABLED;
        }
        if (!force && Bukkit.getOnlinePlayers().size() < event.minPlayers()) {
            return StartResult.NOT_ENOUGH_PLAYERS;
        }
        begin(event, countdown == null ? event.countdownSeconds() : countdown);
        return StartResult.STARTED;
    }

    private void begin(MythosEvent event, int countdown) {
        active = event;
        event.begin(countdown);
    }

    /** Spawns the Ancient Warden right now at the given location (admin command). */
    public StartResult spawnBoss(Location location) {
        if (active != null) {
            return StartResult.ALREADY_RUNNING;
        }
        AncientWardenEvent warden = (AncientWardenEvent) events.get("ancientwarden");
        warden.overrideLocation(location);
        begin(warden, 0);
        return StartResult.STARTED;
    }

    public boolean stop() {
        if (active == null) {
            if (background != null) {
                background.finish(true);
                return true;
            }
            return false;
        }
        active.finish(true);
        return true;
    }

    void onEventEnded(MythosEvent event) {
        if (background == event) {
            background = null;
        }
        if (active == event) {
            active = null;
            scheduleNextAuto();
        }
    }

    public MythosEvent active() {
        return active;
    }

    public MythosEvent get(String id) {
        return events.get(id.toLowerCase(Locale.ROOT).replace("_", "").replace("-", ""));
    }

    public Collection<MythosEvent> all() {
        return events.values();
    }

    public int[] placementPoints() {
        return placementPoints;
    }

    public long nextAutoMillis() {
        if (scheduleOn) {
            ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
            ZonedDateTime next = now.withMinute(hourlyMinute).withSecond(0).withNano(0);
            if (!next.isAfter(now)) {
                next = next.plusHours(1);
            }
            return java.time.Duration.between(now, next).toMillis();
        }
        return autoMode == AutoMode.INTERVAL ? Math.max(0, nextAuto - System.currentTimeMillis()) : -1;
    }

    public Component statusLine() {
        return active == null ? plugin.messages().get("events.status-none") : active.statusLine();
    }

    public void shutdown() {
        if (active != null) {
            active.finish(true);
        }
        if (background != null) {
            background.finish(true);
        }
    }
}
