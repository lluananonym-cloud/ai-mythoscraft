package online.mythoscraft.core.events;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.reward.RewardTable;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Shared lifecycle of all events: IDLE → STARTING (countdown) → RUNNING → ENDING → IDLE, with boss bar,
 * broadcasts, titles, sounds, a score leaderboard, placement rewards, statistics and cooldowns. Subclasses only
 * implement the actual mechanic. The event is registered as listener only while it is running.
 */
public abstract class MythosEvent implements Listener {

    private static final Set<Integer> ANNOUNCE_AT = Set.of(60, 30, 15, 10, 5, 4, 3, 2, 1);

    /** Where an event takes place: around every player, at the spawn, in a sky arena or far out in the wilderness. */
    public enum Venue { PLAYER, SPAWN, ARENA, WILD }

    protected final MythosCraft plugin;
    private final String id;

    protected boolean enabled = true;
    protected int durationSeconds = 120;
    protected int countdownSeconds = 30;
    protected double weight = 1;
    protected int cooldownMinutes = 30;
    protected int minPlayers = 1;
    /** Season id (seasons: in config.yml) – automatic starts only while it is active; empty = all year. */
    protected String season = "";
    protected Set<String> worlds = new HashSet<>();
    protected RewardTable rewards = new RewardTable();
    protected BossBar.Color barColor = BossBar.Color.PURPLE;
    protected SoundSpec startSound = SoundSpec.NONE;
    protected SoundSpec countdownSound = SoundSpec.NONE;
    protected SoundSpec endSound = SoundSpec.NONE;

    protected Venue venueType = Venue.PLAYER;
    private String venueArena = "";
    private double wildMin = 1200;
    private double wildMax = 3500;
    /** Resolved place of the current run (null = around the players). */
    protected org.bukkit.Location venue;
    private int runDuration;

    private EventState state = EventState.IDLE;
    private int countdownLeft;
    private int secondsLeft;
    private long lastEnded;
    private BossBar bossBar;
    protected final Map<UUID, Double> scores = new HashMap<>();
    protected final Map<UUID, String> names = new HashMap<>();
    protected String session = "";

    protected MythosEvent(MythosCraft plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public final void load(ConfigReader cfg) {
        enabled = cfg.bool("enabled", true);
        durationSeconds = cfg.integer("duration-seconds", defaultDuration(), 10, 86_400);
        countdownSeconds = cfg.integer("countdown-seconds", 30, 0, 600);
        weight = cfg.decimal("weight", 1, 0, 1000);
        cooldownMinutes = cfg.integer("cooldown-minutes", 30, 0, 10_080);
        minPlayers = cfg.integer("min-players", 1, 0, 1000);
        season = cfg.string("season", "").trim().toLowerCase(Locale.ROOT);
        worlds = new HashSet<>();
        cfg.strings("worlds").forEach(w -> worlds.add(w.toLowerCase(Locale.ROOT)));
        rewards = RewardTable.parse(cfg.sub("rewards"));
        barColor = cfg.enumValue("bossbar-color", BossBar.Color.class, barColor);
        startSound = sound(cfg, "sounds.start", "entity.ender_dragon.growl 0.6 1.3");
        countdownSound = sound(cfg, "sounds.countdown", "block.note_block.pling 0.7 1.4");
        endSound = sound(cfg, "sounds.end", "ui.toast.challenge_complete 0.8 1.0");
        parseVenue(cfg.string("venue", defaultVenue()));
        loadSettings(cfg);
    }

    /** "player", "spawn", "arena:<name>" or "wild[:min-max]". */
    private void parseVenue(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        venueType = Venue.PLAYER;
        venueArena = "";
        if (v.equals("spawn")) {
            venueType = Venue.SPAWN;
        } else if (v.startsWith("arena")) {
            venueType = Venue.ARENA;
            venueArena = v.contains(":") ? v.substring(v.indexOf(':') + 1).trim() : "daily";
        } else if (v.startsWith("wild")) {
            venueType = Venue.WILD;
            if (v.contains(":")) {
                String[] r = v.substring(v.indexOf(':') + 1).split("-");
                try {
                    wildMin = Double.parseDouble(r[0].trim());
                    wildMax = Math.max(wildMin + 50, Double.parseDouble(r[1].trim()));
                } catch (RuntimeException ignored) {
                    // keep defaults
                }
            }
        }
    }

    /** Default place of this event (overridden in config.yml with "venue"). */
    protected String defaultVenue() {
        return "player";
    }

    public Venue venueType() {
        return venueType;
    }

    /** Place of the running event, or null when it happens around the players. */
    public org.bukkit.Location venue() {
        return venue == null ? null : venue.clone();
    }

    /** Resolves the place for this run; for the wilderness the search runs during the countdown. */
    private void resolveVenue(String arenaOverride) {
        venue = null;
        switch (venueType) {
            case SPAWN -> venue = plugin.spawn().spawn();
            case ARENA -> {
                String name = arenaOverride != null && !arenaOverride.isBlank() ? arenaOverride : venueArena;
                online.mythoscraft.core.arena.Arena arena = plugin.arenas().get(name);
                if (arena == null) {
                    for (online.mythoscraft.core.arena.Arena a : plugin.arenas().arenas()) {
                        arena = a;
                        break;
                    }
                }
                if (arena != null) {
                    org.bukkit.Location l = arena.randomSpawn() != null ? arena.randomSpawn() : arena.spectatorLocation();
                    if (l == null) {
                        org.bukkit.World w = Bukkit.getWorld(arena.world());
                        if (w != null) {
                            online.mythoscraft.core.util.Cuboid c = arena.bounds();
                            l = new org.bukkit.Location(w, (c.minX() + c.maxX()) / 2.0 + 0.5, c.minY() + 3, (c.minZ() + c.maxZ()) / 2.0 + 0.5);
                        }
                    }
                    venue = l;
                }
                if (venue != null) {
                    announceVenue();
                }
            }
            case WILD -> {
                org.bukkit.Location base = plugin.spawn().spawn();
                org.bukkit.World w = base != null ? base.getWorld() : Bukkit.getWorlds().get(0);
                double cx = base != null ? base.getX() : w.getSpawnLocation().getX();
                double cz = base != null ? base.getZ() : w.getSpawnLocation().getZ();
try {
                                    SurfaceFinder.find(plugin, w, cx, cz, wildMin, wildMax, 20,
                        b -> plugin.regions().top(b.getLocation()) == null && plugin.arenas().arenaAt(b.getLocation()) == null)
                        .thenAccept(found -> {
                            if (found != null && state != EventState.IDLE && state != EventState.ENDING) {
                                venue = found;
                                announceVenue();
                            }
                        });
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Event " + id + ": no place in the wilderness (" + e.getMessage() + ") – it runs around the players");
                }
            }
            default -> {
            }
        }
        if (venueType == Venue.SPAWN && venue != null) {
            announceVenue();
        }
    }

    private void announceVenue() {
        if (venue == null) {
            return;
        }
        plugin.messages().broadcast("events.venue." + venueType.name().toLowerCase(Locale.ROOT), "event", displayName(),
                "x", venue.getBlockX(), "y", venue.getBlockY(), "z", venue.getBlockZ());
    }

    /** True if the player is at the place of the event (always true for events around the players). */
    protected boolean atVenue(Player p) {
        if (venue == null || venueType == Venue.PLAYER) {
            return true;
        }
        double range = venueType == Venue.SPAWN ? 150 : venueType == Venue.ARENA ? 120 : 200;
        return p.getWorld().equals(venue.getWorld()) && p.getLocation().distanceSquared(venue) < range * range;
    }

    /** No PvP between visitors while an event is held in a (PvP) tournament arena. */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onVenuePvp(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (venueType != Venue.ARENA || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker != null && attacker != victim && !plugin.arenas().isInArena(victim)) {
            event.setCancelled(true);
        }
    }

    /** Seconds of the current run (a schedule can make an event longer, e.g. on Fridays). */
    public int runSeconds() {
        return runDuration > 0 ? runDuration : durationSeconds;
    }

    private static SoundSpec sound(ConfigReader cfg, String path, String def) {
        SoundSpec s = SoundSpec.parse(cfg.string(path, def));
        if (s == null) {
            cfg.warn(path, "ungültiger Sound – deaktiviert");
            return SoundSpec.NONE;
        }
        return s;
    }

    protected int defaultDuration() {
        return 120;
    }

    protected abstract void loadSettings(ConfigReader cfg);

    /** Called when the countdown ends. Throwing aborts the event cleanly. */
    protected abstract void onStart();

    /** Called once per second while running. */
    protected abstract void onTick();

    /** Called before results are announced (not called again for cleanup). */
    protected void onEnd(boolean aborted) {
    }

    /** Removes everything the event spawned. Must be safe to call more than once. */
    protected abstract void cleanup();

    /** Ends the event early when the goal is reached (all relics found, boss dead, …). */
    protected boolean isFinished() {
        return false;
    }

    /** Unit of the score in results, e.g. "Coins", "Punkte", "Damage". */
    protected abstract String scoreUnitKey();

    /** Message key of the results headline. */
    protected String resultsHeaderKey() {
        return "events.results-header";
    }

    /** False if the goal was missed (e.g. the boss escaped) – results are shown but no rewards are given. */
    protected boolean grantRewards() {
        return true;
    }

    // ---- lifecycle (driven by EventManager) ---------------------------------------------------------------

    final void begin(int countdown) {
        begin(countdown, null, 0);
    }

    final void begin(int countdown, String arenaOverride, int durationOverride) {
        runDuration = Math.max(0, durationOverride);
        state = EventState.STARTING;
        countdownLeft = Math.max(0, countdown);
        scores.clear();
        names.clear();
        session = UUID.randomUUID().toString();
        bossBar = BossBar.bossBar(Component.empty(), 1f, barColor, BossBar.Overlay.PROGRESS);
        plugin.getLogger().info("Event " + id + " starting (countdown " + countdownLeft + "s)");
        if (countdownLeft > 0) {
            plugin.messages().broadcast("events.announce", "event", displayName(), "seconds", countdownLeft);
        }
        resolveVenue(arenaOverride);
        updateBossBar();
        if (countdownLeft == 0) {
            startRunning();
        }
    }

    final void tick() {
        try {
            if (state == EventState.STARTING) {
                countdownLeft--;
                if (countdownLeft <= 0) {
                    startRunning();
                } else {
                    if (ANNOUNCE_AT.contains(countdownLeft)) {
                        announceCountdown();
                    }
                    updateBossBar();
                }
            } else if (state == EventState.RUNNING) {
                secondsLeft--;
                onTick();
                if (state == EventState.RUNNING && (secondsLeft <= 0 || isFinished())) {
                    finish(false);
                    return;
                }
                updateBossBar();
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Event " + id + " failed – stopping it", e);
            finish(true);
        }
    }

    private void announceCountdown() {
        if (countdownLeft <= 5) {
            java.util.function.Supplier<Title> title = () -> Title.title(displayName(), plugin.messages().get("events.countdown-subtitle", "seconds", countdownLeft),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(plugin.messages().in(p, title));
                countdownSound.play(p, 1.0f + (5 - countdownLeft) * 0.1f);
            }
        } else {
            plugin.messages().broadcast("events.countdown", "event", displayName(), "seconds", countdownLeft);
        }
    }

    private void startRunning() {
        state = EventState.RUNNING;
        secondsLeft = runSeconds();
        // listen only while running, so nothing is counted during the countdown
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        try {
            onStart();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Event " + id + " could not start", e);
            plugin.messages().broadcast("events.start-failed", "event", displayName());
            finish(true);
            return;
        }
        if (state != EventState.RUNNING) {
            return;
        }
        plugin.messages().broadcast("events.started", "event", displayName(), "duration", Durations.formatSeconds(runSeconds()));
        java.util.function.Supplier<Title> title = () -> Title.title(displayName(), plugin.messages().get("events." + id + ".subtitle"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(600)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(plugin.messages().in(p, title));
            startSound.play(p);
        }
        updateBossBar();
    }

    /** Ends the event. {@code aborted} skips results and rewards. */
    public final void finish(boolean aborted) {
        if (state == EventState.IDLE || state == EventState.ENDING) {
            return;
        }
        boolean wasRunning = state == EventState.RUNNING;
        state = EventState.ENDING;
        try {
            if (wasRunning) {
                onEnd(aborted);
            }
            if (!aborted && wasRunning) {
                announceResults();
            } else {
                plugin.messages().broadcast("events.stopped", "event", displayName());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Error while ending event " + id, e);
        } finally {
            try {
                cleanup();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Error while cleaning up event " + id, e);
            }
            HandlerList.unregisterAll(this);
            if (bossBar != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.hideBossBar(bossBar);
                }
                bossBar = null;
            }
            lastEnded = System.currentTimeMillis();
            venue = null;
            runDuration = 0;
            state = EventState.IDLE;
            plugin.events().onEventEnded(this);
            plugin.getLogger().info("Event " + id + (aborted ? " stopped" : " finished"));
        }
    }

    private void announceResults() {
        List<Map.Entry<UUID, Double>> ranking = ranking();
        double multiplier = plugin.multipliers().global(MultiplierType.EVENTS);
        Component unit = plugin.messages().get(scoreUnitKey());
        boolean reward = grantRewards();
        plugin.messages().broadcast(ranking.isEmpty() ? "events.results-empty" : resultsHeaderKey(), "event", displayName());
        int place = 0;
        int[] points = plugin.events().placementPoints();
        for (Map.Entry<UUID, Double> entry : ranking) {
            place++;
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);
            List<Reward> placeRewards = reward ? rewards.forPlace(place) : List.of();
            List<Reward> given = new ArrayList<>(placeRewards);
            if (reward && entry.getValue() >= rewards.minScore()) {
                given.addAll(rewards.participation());
            }
            Component rewardText = describe(placeRewards.isEmpty() ? given : placeRewards, multiplier);
            if (place <= Math.max(3, rewards.placesCount())) {
                Bukkit.getServer().sendMessage(plugin.messages().get("events.results-line", "place", place,
                        "player", names.getOrDefault(uuid, "?"), "score", formatScore(entry.getValue()), "unit", unit,
                        "reward", rewardText));
            }
            if (player == null) {
                continue;
            }
            for (Reward r : given) {
                r.give(plugin, player, multiplier);
            }
            plugin.clanWar().add(uuid, place == 1 ? online.mythoscraft.core.clan.ClanWarService.Source.EVENT_WIN
                    : place <= 3 ? online.mythoscraft.core.clan.ClanWarService.Source.EVENT_PLACE
                    : online.mythoscraft.core.clan.ClanWarService.Source.EVENT_JOIN);
            PlayerData data = plugin.playerData().get(player);
            if (data != null) {
                data.addEventPoints(place <= points.length ? points[place - 1] : points[points.length - 1]);
                if (place == 1) {
                    data.addEventWin();
                }
                plugin.display().markDirty(uuid);
            }
            plugin.messages().send(player, "events.your-place", "place", place, "score", formatScore(entry.getValue()),
                    "unit", unit, "reward", describe(given, multiplier));
            endSound.play(player);
        }
    }

    private Component describe(List<Reward> list, double multiplier) {
        if (list.isEmpty()) {
            return plugin.messages().get("events.no-reward");
        }
        Component out = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out = out.append(Component.text(", "));
            }
            out = out.append(list.get(i).describe(plugin, multiplier));
        }
        return out;
    }

    protected String formatScore(double score) {
        return Numbers.format(Math.round(score));
    }

    // ---- scores -------------------------------------------------------------------------------------------

    protected void addScore(Player player, double amount) {
        scores.merge(player.getUniqueId(), amount, Double::sum);
        names.put(player.getUniqueId(), player.getName());
    }

    public double score(UUID player) {
        return scores.getOrDefault(player, 0.0);
    }

    public List<Map.Entry<UUID, Double>> ranking() {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(scores.entrySet());
        list.removeIf(e -> e.getValue() <= 0);
        list.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        return list;
    }

    public int placeOf(UUID player) {
        List<Map.Entry<UUID, Double>> ranking = ranking();
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getKey().equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }

    // ---- presentation -------------------------------------------------------------------------------------

    private void updateBossBar() {
        if (bossBar == null) {
            return;
        }
        if (state == EventState.STARTING) {
            bossBar.name(plugin.messages().get("events.bossbar-starting", "event", displayName(), "seconds", countdownLeft));
            bossBar.progress(countdownSeconds <= 0 ? 1f : clamp(countdownLeft / (float) countdownSeconds));
        } else {
            bossBar.name(bossBarText());
            bossBar.progress(bossBarProgress());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bossBar);
        }
    }

    protected Component bossBarText() {
        List<Map.Entry<UUID, Double>> ranking = ranking();
        Component leader = ranking.isEmpty() ? plugin.messages().get("events.no-leader")
                : Component.text(names.getOrDefault(ranking.get(0).getKey(), "?"));
        return plugin.messages().get("events.bossbar-running", "event", displayName(),
                "time", Durations.clock(secondsLeft), "leader", leader);
    }

    protected float bossBarProgress() {
        return runSeconds() <= 0 ? 0f : clamp(secondsLeft / (float) runSeconds());
    }

    protected BossBar bossBar() {
        return bossBar;
    }

    protected static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public Component displayName() {
        return plugin.messages().get("events." + id + ".name");
    }

    public Component statusLine() {
        return switch (state) {
            case STARTING -> plugin.messages().get("events.status-starting", "event", displayName(), "time", Durations.clock(countdownLeft));
            case RUNNING -> plugin.messages().get("events.status-running", "event", displayName(), "time", Durations.clock(secondsLeft));
            default -> plugin.messages().get("events.status-none");
        };
    }

    /** Players that take part: online, not spectating, not in an arena and in one of the configured worlds. */
    protected boolean eligible(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR || plugin.arenas().isInArena(p)) {
            return false;
        }
        return worlds.isEmpty() || worlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    protected List<Player> participants() {
        List<Player> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (eligible(p) && (venueType != Venue.SPAWN || atVenue(p))) {
                list.add(p);
            }
        }
        return list;
    }

    // ---- accessors ----------------------------------------------------------------------------------------

    public String id() {
        return id;
    }

    public EventState state() {
        return state;
    }

    public boolean enabled() {
        return enabled;
    }

    public double weight() {
        return weight;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public String season() {
        return season;
    }

    public int secondsLeft() {
        return state == EventState.STARTING ? countdownLeft : secondsLeft;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public long cooldownRemainingMillis() {
        long until = lastEnded + cooldownMinutes * 60_000L;
        return Math.max(0, until - System.currentTimeMillis());
    }

    protected void addTime(int seconds) {
        secondsLeft += seconds;
    }

    /** Extra lines for /event and /mythos event info. */
    public List<Component> infoLines(Player viewer) {
        List<Component> lines = new ArrayList<>();
        if (viewer != null) {
            int place = placeOf(viewer.getUniqueId());
            lines.add(plugin.messages().get("events.info-you", "score", formatScore(score(viewer.getUniqueId())),
                    "unit", plugin.messages().get(scoreUnitKey()), "place", place == 0 ? "-" : String.valueOf(place)));
        }
        List<Map.Entry<UUID, Double>> ranking = ranking();
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Map.Entry<UUID, Double> e = ranking.get(i);
            lines.add(plugin.messages().get("events.info-top", "place", i + 1, "player", names.getOrDefault(e.getKey(), "?"),
                    "score", formatScore(e.getValue()), "unit", plugin.messages().get(scoreUnitKey())));
        }
        return lines;
    }
}
