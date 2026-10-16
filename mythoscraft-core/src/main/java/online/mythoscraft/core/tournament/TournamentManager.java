package online.mythoscraft.core.tournament;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Arena;
import online.mythoscraft.core.arena.Kit;
import online.mythoscraft.core.arena.PlayerStateCodec;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.reward.RewardTable;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Effects;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import online.mythoscraft.core.util.WeightedRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Daily, weekly and monthly free-for-all tournaments in big arenas. The first N players who run /tournament join get
 * a slot. Everybody gets the same random kit; their own inventory is stored (crash-safe) and comes back on death or
 * victory while the kit disappears. The arena itself can't be damaged – only blocks placed during the fight can be
 * broken, and every change is rolled back afterwards.
 */
public final class TournamentManager {

    public enum OpenResult { OPENED, ALREADY_RUNNING, NO_ARENA, ARENA_TOO_SMALL, NO_KITS, DISABLED }

    record TierSettings(boolean enabled, LocalTime time, DayOfWeek day, int dayOfMonth, int slots, int durationMinutes,
                        String arena, RewardTable rewards) {
    }

    record TournamentKit(String id, String name, double weight, Kit kit) {
    }

    private final MythosCraft plugin;
    private final TournamentBots bots;
    private final Map<TournamentTier, TierSettings> tiers = new EnumMap<>(TournamentTier.class);
    private final Map<TournamentTier, ZonedDateTime> nextStart = new EnumMap<>(TournamentTier.class);
    private final Map<String, TournamentKit> kits = new LinkedHashMap<>();
    private final Map<UUID, byte[]> pendingRestore = new HashMap<>();
    private final Map<UUID, Location> pendingReturn = new HashMap<>();
    private final Set<UUID> internalTeleport = new HashSet<>();
    private boolean enabled = true;
    private int registrationMinutes = 10;
    private int minPlayers = 2;
    private int countdownSeconds = 5;
    private boolean fullReset = true;
    private List<String> allowedCommands = List.of("tournament", "turnier", "msg", "r", "helpop");
    private Tournament active;
    private BossBar bar;

    /** Backup of the arena of every tier (survives even if config.yml loses the value). */
    private final online.mythoscraft.core.config.YamlStore arenaStore;

    public TournamentManager(MythosCraft plugin) {
        this.plugin = plugin;
        this.bots = new TournamentBots(plugin);
        this.arenaStore = new online.mythoscraft.core.config.YamlStore(plugin, "data/tournament-arenas.yml");
        arenaStore.load(false);
    }

    /**
     * The arena of a tier: the configured one, else the saved backup, else an arena named like the tier
     * (daily / weekly / monthly) – so a built tournament arena is always found again after a restart.
     */
    public Arena arenaOf(TournamentTier tier) {
        String configured = tiers.get(tier).arena();
        Arena a = configured.isEmpty() ? null : plugin.arenas().get(configured);
        if (a == null) {
            String saved = arenaStore.yaml().getString(tier.id(), "");
            a = saved.isEmpty() ? null : plugin.arenas().get(saved);
        }
        if (a == null) {
            a = plugin.arenas().get(tier.id());
        }
        return a;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("tournaments");
        enabled = cfg.bool("enabled", true);
        registrationMinutes = cfg.integer("registration-minutes", 10, 1, 120);
        minPlayers = cfg.integer("min-players", 2, 2, 100);
        countdownSeconds = cfg.integer("start-countdown-seconds", 5, 0, 60);
        fullReset = cfg.bool("full-arena-reset", true);
        allowedCommands = cfg.strings("allowed-commands").stream().map(s -> s.toLowerCase().replace("/", "")).toList();
        if (allowedCommands.isEmpty()) {
            allowedCommands = List.of("tournament", "turnier", "msg", "r", "helpop");
        }
        for (TournamentTier tier : TournamentTier.values()) {
            ConfigReader t = cfg.sub(tier.id());
            tiers.put(tier, new TierSettings(t.bool("enabled", true),
                    parseTime(t, tier == TournamentTier.DAILY ? "20:00" : "19:00"),
                    t.enumValue("day", DayOfWeek.class, DayOfWeek.SUNDAY), t.integer("day-of-month", 1, 1, 31),
                    t.integer("slots", tier.defaultSlots(), 2, 500), t.integer("duration-minutes", 10, 2, 180),
                    t.string("arena", ""), RewardTable.parse(t.sub("rewards"))));
        }
        kits.clear();
        ConfigReader kitSection = cfg.sub("kits");
        for (String id : kitSection.keys()) {
            ConfigReader k = kitSection.sub(id);
            kits.put(id.toLowerCase(), new TournamentKit(id.toLowerCase(), k.string("name", id), k.decimal("weight", 1, 0, 1000),
                    Kit.parse(id, k)));
        }
        bots.reload(cfg.sub("bots"));
        scheduleAll();
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 20, "tournaments", this::tick);
        plugin.tasks().repeat(this, 2, 2, "tournament bots", this::botTick);
    }

    private static LocalTime parseTime(ConfigReader t, String def) {
        List<LocalTime> list = t.times("time");
        if (!list.isEmpty()) {
            return list.get(0);
        }
        String s = t.string("time", def);
        try {
            return LocalTime.parse(s.length() == 4 ? "0" + s : s);
        } catch (RuntimeException e) {
            return LocalTime.parse(def);
        }
    }

    private void scheduleAll() {
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        for (TournamentTier tier : TournamentTier.values()) {
            schedule(tier, now);
        }
    }

    private void schedule(TournamentTier tier, ZonedDateTime now) {
        TierSettings s = tiers.get(tier);
        nextStart.put(tier, TournamentSchedule.next(tier, s.time(), s.day(), s.dayOfMonth(), now));
    }

    // ---- queries ------------------------------------------------------------------------------------------

    public boolean isParticipant(Player player) {
        return active != null && active.fighting() && active.alive.contains(player.getUniqueId());
    }

    public boolean isRegistered(Player player) {
        return active != null && active.registered.contains(player.getUniqueId());
    }

    Tournament active() {
        return active;
    }

    TournamentBots bots() {
        return bots;
    }

    boolean isInternalTeleport(Player p) {
        return internalTeleport.contains(p.getUniqueId());
    }

    boolean commandAllowed(String label) {
        return allowedCommands.contains(label.toLowerCase());
    }

    public ZonedDateTime nextStart(TournamentTier tier) {
        return nextStart.get(tier);
    }

    public Set<String> kitIds() {
        return kits.keySet();
    }

    public String arenaName(TournamentTier tier) {
        Arena a = arenaOf(tier);
        return a == null ? tiers.get(tier).arena() : a.name();
    }

    /** Persists the arena of a tier in config.yml. */
    public void setArena(TournamentTier tier, String arena) {
        plugin.getConfig().set("tournaments." + tier.id() + ".arena", arena);
        plugin.saveConfig();
        org.bukkit.configuration.file.YamlConfiguration yaml = arenaStore.yaml();
        yaml.set(tier.id(), arena);
        arenaStore.replace(yaml);
        arenaStore.saveNow();
        TierSettings s = tiers.get(tier);
        tiers.put(tier, new TierSettings(s.enabled(), s.time(), s.day(), s.dayOfMonth(), s.slots(), s.durationMinutes(), arena, s.rewards()));
    }

    /** Problem with the arena of a tier or null if it is usable. */
    public String arenaProblem(TournamentTier tier) {
        Arena a = arenaOf(tier);
        if (a == null) {
            return "keine Arena gesetzt";
        }
        if (a.bounds().sizeX() < tier.minArenaSize() || a.bounds().sizeZ() < tier.minArenaSize()) {
            return "Arena " + a.name() + " ist zu klein (" + a.bounds().sizeX() + "×" + a.bounds().sizeZ() + ", nötig "
                    + tier.minArenaSize() + "×" + tier.minArenaSize() + ")";
        }
        if (a.spawns().isEmpty()) {
            return "Arena " + a.name() + " hat keine Spawnpunkte";
        }
        return null;
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------

    private void tick() {
        if (active != null) {
            tickActive();
            return;
        }
        if (!enabled) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        for (TournamentTier tier : TournamentTier.values()) {
            TierSettings s = tiers.get(tier);
            ZonedDateTime start = nextStart.get(tier);
            if (!s.enabled() || start == null) {
                continue;
            }
            if (!now.isBefore(start.minusMinutes(registrationMinutes))) {
                schedule(tier, start.plusMinutes(1));
                int seconds = (int) Math.max(30, Duration.between(now, start).getSeconds());
                OpenResult r = open(tier, false, seconds, 0);
                if (r != OpenResult.OPENED) {
                    plugin.getLogger().warning("Scheduled " + tier.id() + " tournament skipped: " + r
                            + (r == OpenResult.NO_ARENA || r == OpenResult.ARENA_TOO_SMALL ? " (" + arenaProblem(tier) + ")" : ""));
                }
                return;
            }
        }
    }

    /**
     * Opens the registration. {@code bots} > 0 fills the remaining slots with bots at the start (admin test).
     */
    public OpenResult open(TournamentTier tier, boolean test, int registrationSeconds, int botCount) {
        if (active != null) {
            return OpenResult.ALREADY_RUNNING;
        }
        if (arenaProblem(tier) != null) {
            return arenaOf(tier) == null ? OpenResult.NO_ARENA : OpenResult.ARENA_TOO_SMALL;
        }
        WeightedRandom<TournamentKit> pick = new WeightedRandom<>(new ArrayList<>(kits.values()), TournamentKit::weight);
        if (pick.isEmpty()) {
            return OpenResult.NO_KITS;
        }
        TournamentKit kit = pick.pick(ThreadLocalRandom.current());
        Arena arena = arenaOf(tier);
        int slots = tiers.get(tier).slots();
        active = new Tournament(tier, arena, kit.id(), kit.kit(), test, slots);
        active.secondsLeft = Math.max(5, registrationSeconds);
        active.botsToAdd = botCount;
        bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        Component join = plugin.messages().get("tournament.join-button")
                .clickEvent(ClickEvent.runCommand("/tournament join"))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("tournament.join-hover")));
        plugin.messages().broadcast(test ? "tournament.opened-test" : "tournament.opened",
                "tier", tierName(tier), "kit", Text.parse(online.mythoscraft.core.config.ConfigText.tr(kit.name())), "slots", slots,
                "time", Durations.formatSeconds(active.secondsLeft), "arena", arena.name());
        Bukkit.getServer().sendMessage(plugin.messages().prefix().append(join));
        for (Player p : Bukkit.getOnlinePlayers()) {
            SoundSpec.of("block.note_block.chime", 1f, 1.2f).play(p);
        }
        plugin.getLogger().info("Tournament " + tier.id() + " registration opened (kit " + kit.id() + ", " + slots + " slots"
                + (test ? ", test with " + botCount + " bots" : "") + ")");
        return OpenResult.OPENED;
    }

    public Component tierName(TournamentTier tier) {
        return plugin.messages().get("tournament.tiers." + tier.id());
    }

    public void join(Player player) {
        Tournament t = active;
        if (t == null || t.phase != Tournament.Phase.REGISTRATION) {
            plugin.messages().send(player, "tournament.no-registration");
            return;
        }
        if (t.registered.contains(player.getUniqueId())) {
            plugin.messages().send(player, "tournament.already-joined");
            return;
        }
        if (plugin.arenas().isInArena(player)) {
            plugin.messages().send(player, "arena.leave-first");
            return;
        }
        if (t.registered.size() >= t.slots) {
            plugin.messages().send(player, "tournament.full", "slots", t.slots);
            return;
        }
        t.registered.add(player.getUniqueId());
        t.names.put(player.getUniqueId(), player.getName());
        plugin.messages().send(player, "tournament.joined", "tier", tierName(t.tier), "count", t.registered.size(), "slots", t.slots,
                "time", Durations.formatSeconds(t.secondsLeft));
        if (t.registered.size() == t.slots) {
            plugin.messages().broadcast("tournament.now-full", "tier", tierName(t.tier), "slots", t.slots);
        }
    }

    public void leave(Player player) {
        Tournament t = active;
        if (t == null || !t.registered.contains(player.getUniqueId())) {
            plugin.messages().send(player, "tournament.not-joined");
            return;
        }
        if (t.phase == Tournament.Phase.REGISTRATION) {
            t.registered.remove(player.getUniqueId());
            plugin.messages().send(player, "tournament.left");
            return;
        }
        if (t.alive.contains(player.getUniqueId())) {
            eliminate(player.getUniqueId(), null, true, "tournament.forfeit");
        }
    }

    private void tickActive() {
        Tournament t = active;
        t.secondsLeft--;
        switch (t.phase) {
            case REGISTRATION -> {
                if (Set.of(300, 180, 120, 60, 30, 10, 5).contains(t.secondsLeft)) {
                    plugin.messages().broadcast("tournament.reminder", "tier", tierName(t.tier), "time",
                            Durations.formatSeconds(t.secondsLeft), "count", t.registered.size(), "slots", t.slots);
                }
                updateBar(t);
                if (t.secondsLeft <= 0) {
                    beginFight(t);
                }
            }
            case STARTING -> {
                if (t.secondsLeft > 0) {
                    java.util.function.Supplier<Title> title = () -> Title.title(Component.text(t.secondsLeft, net.kyori.adventure.text.format.NamedTextColor.GOLD),
                            plugin.messages().get("tournament.get-ready"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(200)));
                    for (Player p : players(t)) {
                        p.showTitle(plugin.messages().in(p, title));
                        SoundSpec.of("block.note_block.pling", 1f, 1f + (5 - t.secondsLeft) * 0.1f).play(p);
                    }
                } else {
                    t.phase = Tournament.Phase.RUNNING;
                    t.secondsLeft = tiers.get(t.tier).durationMinutes() * 60;
                    java.util.function.Supplier<Title> go = () -> Title.title(plugin.messages().get("tournament.fight-title"), plugin.messages().get("tournament.fight-subtitle",
                            "kit", Text.parse(kits.get(t.kitId).name())), Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400)));
                    for (Player p : players(t)) {
                        p.showTitle(plugin.messages().in(p, go));
                        SoundSpec.of("entity.ender_dragon.growl", 0.6f, 1.5f).play(p);
                    }
                }
                updateBar(t);
            }
            case RUNNING -> {
                updateBar(t);
                if (t.alive.size() <= 1 || t.secondsLeft <= 0) {
                    finish(t);
                }
            }
            default -> {
            }
        }
    }

    private void updateBar(Tournament t) {
        if (bar == null) {
            return;
        }
        switch (t.phase) {
            case REGISTRATION -> {
                bar.name(plugin.messages().get("tournament.bar-registration", "tier", tierName(t.tier), "count", t.registered.size(),
                        "slots", t.slots, "time", Durations.clock(t.secondsLeft)));
                bar.progress(Math.min(1f, t.registered.size() / (float) t.slots));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showBossBar(bar);
                }
            }
            case STARTING, RUNNING -> {
                bar.name(plugin.messages().get("tournament.bar-running", "tier", tierName(t.tier), "alive", t.alive.size(),
                        "time", Durations.clock(Math.max(0, t.secondsLeft))));
                bar.color(BossBar.Color.RED);
                int total = t.alive.size() + t.eliminated.size();
                bar.progress(total == 0 ? 0f : Math.max(0f, Math.min(1f, t.alive.size() / (float) total)));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (t.registered.contains(p.getUniqueId()) || t.arena.contains(p.getLocation())) {
                        p.showBossBar(bar);
                    } else {
                        p.hideBossBar(bar);
                    }
                }
            }
            default -> {
            }
        }
    }

    private List<Player> players(Tournament t) {
        List<Player> list = new ArrayList<>();
        for (UUID id : t.alive) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    private void beginFight(Tournament t) {
        List<Player> players = new ArrayList<>();
        for (UUID id : t.registered) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead() && !plugin.arenas().isInArena(p)) {
                players.add(p);
            }
        }
        int botCount = t.test ? Math.max(0, Math.min(t.botsToAdd, t.slots - players.size())) : 0;
        if (players.size() + botCount < (t.test ? 2 : minPlayers)) {
            plugin.messages().broadcast("tournament.cancelled-players", "tier", tierName(t.tier), "min", minPlayers);
            close();
            return;
        }
        World world = Bukkit.getWorld(t.arena.world());
        if (world == null) {
            plugin.messages().broadcast("tournament.cancelled-arena", "tier", tierName(t.tier));
            close();
            return;
        }
        t.phase = Tournament.Phase.STARTING;
        t.started = true;
        t.secondsLeft = countdownSeconds;
        t.tracker.start(world);
        t.arena.tournamentMode(true);
        plugin.arenas().publishRegions();
        List<Location> spawns = spawnPoints(t, players.size() + botCount);
        int i = 0;
        for (Player p : players) {
            prepare(t, p, spawns.get(i++));
        }
        for (int b = 0; b < botCount; b++) {
            LivingEntity bot = bots.spawn(spawns.get(i++), b + 1, t.kit, t.id);
            t.alive.add(bot.getUniqueId());
            t.bots.add(bot.getUniqueId());
            t.names.put(bot.getUniqueId(), "Bot #" + (b + 1));
        }
        plugin.messages().broadcast("tournament.started", "tier", tierName(t.tier), "count", t.alive.size(),
                "kit", Text.parse(kits.get(t.kitId).name()));
        plugin.getLogger().info("Tournament " + t.tier.id() + " started with " + players.size() + " players and " + botCount + " bots");
    }

    /** As many spawn points as fighters; arena spawns are reused with a small offset when there are fewer. */
    private List<Location> spawnPoints(Tournament t, int count) {
        List<Location> base = t.arena.spawnLocations();
        List<Location> out = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Location> shuffled = new ArrayList<>(base);
        java.util.Collections.shuffle(shuffled);
        for (int i = 0; i < count; i++) {
            Location l = shuffled.get(i % shuffled.size()).clone();
            if (i >= shuffled.size()) {
                l.add(rnd.nextDouble(-2, 2), 0, rnd.nextDouble(-2, 2));
            }
            out.add(l);
        }
        return out;
    }

    private void prepare(Tournament t, Player p, Location spawn) {
        UUID id = p.getUniqueId();
        plugin.teleports().cancel(p, null);
        byte[] state = PlayerStateCodec.encode(p);
        t.saved.put(id, state);
        t.returns.put(id, p.getLocation());
        plugin.arenas().inventories().store(id, "tournament-" + t.tier.id(), state, LocationCodec.toString(p.getLocation()));
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        if (p.getGameMode() != GameMode.SURVIVAL) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setLevel(0);
        p.setExp(0);
        teleport(p, spawn);
        t.kit.give(p);
        t.alive.add(id);
        t.names.put(id, p.getName());
    }

    void teleport(Player p, Location to) {
        internalTeleport.add(p.getUniqueId());
        try {
            online.mythoscraft.core.cosmetic.CompanionService.unmount(p);
            p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            internalTeleport.remove(p.getUniqueId());
        }
    }

    /** Removes a fighter. {@code restoreNow} is false for players that just died (restored on respawn). */
    void eliminate(UUID id, UUID killer, boolean restoreNow, String messageKey) {
        Tournament t = active;
        if (t == null || !t.alive.remove(id)) {
            return;
        }
        t.eliminated.add(id);
        if (killer != null && !killer.equals(id)) {
            t.kills.merge(killer, 1, Integer::sum);
        }
        String name = t.names.getOrDefault(id, "?");
        String killerName = killer == null ? null : t.names.get(killer);
        for (UUID other : t.registered) {
            Player p = Bukkit.getPlayer(other);
            if (p != null) {
                plugin.messages().send(p, killerName == null ? "tournament.eliminated" : "tournament.eliminated-by",
                        "player", name, "killer", killerName == null ? "" : killerName, "alive", t.alive.size());
            }
        }
        if (t.bots.contains(id)) {
            bots.forget(id);
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid() && !(e instanceof LivingEntity le && le.isDead())) {
                e.remove();
            }
            return;
        }
        Player p = Bukkit.getPlayer(id);
        int place = t.alive.size() + 1;
        if (p != null) {
            plugin.messages().send(p, "tournament.your-place", "place", place, "kills", t.kills.getOrDefault(id, 0));
        }
        byte[] state = t.saved.remove(id);
        Location back = t.returns.remove(id);
        if (p != null && restoreNow) {
            restore(p, state, back);
        } else if (state != null) {
            pendingRestore.put(id, state);
            if (back != null) {
                pendingReturn.put(id, back);
            }
        }
    }

    void restore(Player p, byte[] state, Location back) {
        if (state != null) {
            try {
                PlayerStateCodec.restore(p, state);
                plugin.arenas().inventories().delete(p.getUniqueId());
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("Could not restore tournament inventory of " + p.getName() + " (kept in database): " + e.getMessage());
                plugin.messages().send(p, "arena.restore-failed");
            }
        }
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.setFireTicks(0);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (!p.isDead()) {
            p.setHealth(max == null ? 20 : max.getValue());
        }
        if (back != null && back.getWorld() != null) {
            teleport(p, back);
        }
    }

    /** Respawn handling for players eliminated by death. */
    Location pendingReturn(UUID id) {
        return pendingReturn.get(id);
    }

    void completePendingRestore(Player p) {
        byte[] state = pendingRestore.remove(p.getUniqueId());
        Location back = pendingReturn.remove(p.getUniqueId());
        if (state != null || back != null) {
            restore(p, state, null);
        }
    }

    void recordAttack(UUID victim, UUID attacker) {
        if (active != null) {
            active.lastAttacker.put(victim, attacker);
        }
    }

    UUID lastAttacker(UUID victim) {
        return active == null ? null : active.lastAttacker.get(victim);
    }

    private void finish(Tournament t) {
        t.phase = Tournament.Phase.ENDING;
        Map<UUID, Double> health = new HashMap<>();
        for (UUID id : t.alive) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le) {
                health.put(id, le.getHealth());
            }
        }
        List<UUID> ranking = t.ranking(health);
        RewardTable rewards = tiers.get(t.tier).rewards();
        plugin.messages().broadcast("tournament.results-header", "tier", tierName(t.tier));
        int place = 0;
        for (UUID id : ranking) {
            place++;
            String name = t.names.getOrDefault(id, "?");
            int kills = t.kills.getOrDefault(id, 0);
            List<Reward> given = new ArrayList<>(rewards.forPlace(place));
            given.addAll(rewards.participation());
            if (place <= 3) {
                Bukkit.getServer().sendMessage(plugin.messages().get("tournament.results-line", "place", place, "player", name, "kills", kills));
            }
            Player p = Bukkit.getPlayer(id);
            if (p == null || t.test && !t.registered.contains(id)) {
                continue;
            }
            // rewards come after the arena gave the own inventory back – otherwise the restore would delete them
            final int finalPlace = place;
            final List<Reward> finalGiven = given;
            final boolean test = t.test;
            final TournamentTier tier = t.tier;
            plugin.tasks().later(40, "tournament rewards", () -> {
                Player winner = Bukkit.getPlayer(id);
                if (winner == null) {
                    return;
                }
                if (!test) {
                    for (Reward r : finalGiven) {
                        r.give(plugin, winner, 1.0);
                    }
                }
                if (finalPlace == 1) {
                    CustomItemManager.give(winner, plugin.trophies().award(winner, tier, !test));
                    plugin.messages().send(winner, "tournament.trophy", "tier", tierName(tier));
                    if (!test) {
                        PlayerData data = plugin.playerData().get(winner);
                        if (data != null) {
                            data.addEventWin();
                            data.addEventPoints(25);
                        }
                    }
                }
            });
        }
        if (!ranking.isEmpty()) {
            UUID winner = ranking.get(0);
            String name = t.names.getOrDefault(winner, "?");
            java.util.function.Supplier<Title> title = () -> Title.title(plugin.messages().get("tournament.winner-title", "player", name),
                    plugin.messages().get("tournament.winner-subtitle", "tier", tierName(t.tier)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3500), Duration.ofMillis(800)));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showTitle(plugin.messages().in(online, title));
            }
            Entity we = Bukkit.getEntity(winner);
            if (we != null) {
                Effects.burst(we.getLocation().add(0, 2, 0), Color.fromRGB(0xF59E0B), Color.WHITE, true);
            }
        }
        if (t.test) {
            plugin.messages().broadcast("tournament.test-finished");
        }
        close();
    }

    /** Stops without rewards (admin). */
    public boolean stop() {
        if (active == null) {
            return false;
        }
        plugin.messages().broadcast("tournament.stopped", "tier", tierName(active.tier));
        close();
        return true;
    }

    /** Restores everyone, removes bots, rolls back the arena and clears the state. */
    private void close() {
        Tournament t = active;
        if (t == null) {
            return;
        }
        t.phase = Tournament.Phase.ENDING;
        for (UUID id : new ArrayList<>(t.alive)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                byte[] state = t.saved.remove(id);
                Location back = t.returns.remove(id);
                if (p.isDead()) {
                    if (state != null) {
                        pendingRestore.put(id, state);
                    }
                    if (back != null) {
                        pendingReturn.put(id, back);
                    }
                } else {
                    restore(p, state, back);
                }
            }
        }
        t.alive.clear();
        bots.removeAll();
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
        if (t.started) {
            int restored = t.tracker.restore();
            clearLoose(t.arena);
            t.arena.tournamentMode(false);
            plugin.arenas().publishRegions();
            plugin.getLogger().info("Tournament arena " + t.arena.name() + ": " + restored + " changed blocks restored");
            if (fullReset && plugin.arenas().snapshots().exists(t.arena)) {
                plugin.arenas().reset(t.arena, null);
            }
        }
        active = null;
    }

    private void clearLoose(Arena arena) {
        World w = Bukkit.getWorld(arena.world());
        if (w == null) {
            return;
        }
        for (Entity e : w.getEntities()) {
            if ((e instanceof Item || e instanceof Projectile || e instanceof EnderCrystal) && arena.contains(e.getLocation())) {
                e.remove();
            }
        }
    }

    private void botTick() {
        Tournament t = active;
        if (t == null || t.phase != Tournament.Phase.RUNNING || t.bots.isEmpty()) {
            return;
        }
        List<LivingEntity> fighters = new ArrayList<>();
        for (UUID id : t.alive) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le && le.isValid()) {
                fighters.add(le);
            }
        }
        bots.tick(fighters);
    }

    public void info(CommandSender to) {
        Tournament t = active;
        if (t == null) {
            plugin.messages().send(to, "tournament.info-none");
            for (TournamentTier tier : TournamentTier.values()) {
                TierSettings s = tiers.get(tier);
                ZonedDateTime next = nextStart.get(tier);
                plugin.messages().sendRaw(to, "tournament.info-next", "tier", tierName(tier),
                        "time", s.enabled() && next != null ? next.toLocalDate() + " " + next.toLocalTime() : "-",
                        "slots", s.slots(), "arena", arenaOf(tier) == null ? "-" : arenaOf(tier).name());
            }
            return;
        }
        plugin.messages().send(to, "tournament.info-active", "tier", tierName(t.tier), "phase",
                plugin.messages().get("tournament.phases." + t.phase.name().toLowerCase()), "count",
                t.phase == Tournament.Phase.REGISTRATION ? t.registered.size() : t.alive.size(), "slots", t.slots,
                "kit", Text.parse(kits.get(t.kitId).name()), "arena", t.arena.name(), "time", Durations.clock(Math.max(0, t.secondsLeft)));
    }

    public void shutdown() {
        if (active != null) {
            close();
        }
        bots.removeAll();
    }
}
