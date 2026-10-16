package online.mythoscraft.core.rank;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Extra rank perks on top of homes / coin and gem multiplier / daily bonus: more job XP, a daily free key, cheaper
 * blacksmith, extra free wheel spins, more mana, a join announcement, a light trail at the spawn – and for MYTHOS
 * flying at the spawn. Configured per rank under rank-perks.
 */
public final class RankPerksService implements Listener {

    public record Perks(double jobXp, List<Reward> dailyKeys, int upgradeDiscount, int extraSpins, int extraMana, boolean announce,
                        String trail, boolean spawnFly, double boosterTime, int homeFly, boolean baseFly, int plots) {
        static final Perks NONE = new Perks(0, List.of(), 0, 0, 0, false, "", false, 0, 0, false, 1);
    }

    private final MythosCraft plugin;
    private final Map<String, Perks> perks = new HashMap<>();
    private final Set<UUID> flying = new HashSet<>();
    /** Left the flight area in the air: they float down slowly instead of falling. */
    private final Set<UUID> floating = new HashSet<>();

    public RankPerksService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("rank-perks");
        perks.clear();
        for (String id : cfg.keys()) {
            ConfigReader r = cfg.sub(id);
            List<Reward> keys = new ArrayList<>();
            for (String s : r.strings("daily-keys")) {
                try {
                    keys.add(Reward.parse(s));
                } catch (IllegalArgumentException e) {
                    r.warn("daily-keys", e.getMessage());
                }
            }
            perks.put(id.toUpperCase(Locale.ROOT), new Perks(r.decimal("job-xp-bonus", 0, 0, 10), keys, r.integer("blacksmith-discount", 0, 0, 90),
                    r.integer("extra-wheel-spins", 0, 0, 20), r.integer("extra-mana", 0, 0, 10_000), r.bool("join-announce", false),
                    r.string("spawn-trail", ""), r.bool("spawn-fly", false), r.decimal("booster-time-bonus", 0, 0, 10),
                    r.integer("home-fly-radius", 0, 0, 500), r.bool("base-fly", false), r.integer("plots", 1, 0, 1000)));
        }
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 60, 10, "rank perks", this::tick);
    }

    public Perks of(Player p) {
        Rank r = plugin.ranks().rankOf(p);
        return r == null ? Perks.NONE : perks.getOrDefault(r.id().toUpperCase(Locale.ROOT), Perks.NONE);
    }

    public double jobXpFactor(Player p) {
        return 1 + of(p).jobXp();
    }

    public int extraSpins(Player p) {
        return of(p).extraSpins();
    }

    public int extraMana(Player p) {
        return of(p).extraMana();
    }

    public int upgradeDiscount(Player p) {
        return of(p).upgradeDiscount();
    }

    public double boosterTimeFactor(Player p) {
        return 1 + of(p).boosterTime();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(60, "rank perks join", () -> {
            if (!p.isOnline() || !plugin.progress().loaded(p)) {
                return;
            }
            Perks k = of(p);
            Rank rank = plugin.ranks().rankOf(p);
            if (k.announce()) {
                plugin.messages().broadcast("rank-perks.join", "rank", online.mythoscraft.core.util.Text.parse(rank.display()), "player", p.getName());
                if (rank.isMythos()) {
                    for (Player o : plugin.getServer().getOnlinePlayers()) {
                        SoundSpec.of("ui.toast.challenge_complete", 0.6f, 1.3f).play(o);
                    }
                    online.mythoscraft.core.util.Effects.burst(p.getLocation().add(0, 2, 0), Color.fromRGB(0xc084fc), Color.fromRGB(0xf59e0b), true);
                }
            }
            long today = plugin.multipliers().today().toEpochDay();
            if (!k.dailyKeys().isEmpty() && plugin.progress().get(p, "rankperks.key-day") != today) {
                plugin.progress().set(p, "rankperks.key-day", today);
                for (Reward r : k.dailyKeys()) {
                    r.give(plugin, p, 1.0);
                }
                plugin.messages().send(p, "rank-perks.daily-keys", "rank", online.mythoscraft.core.util.Text.parse(rank.display()));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flying.remove(event.getPlayer().getUniqueId());
        floating.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.progress().loaded(p) || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Perks k = of(p);
            boolean atSpawn = plugin.attractionStore().inside(p.getLocation(), 0) && !plugin.arenas().isInArena(p);
            boolean atHome = k.homeFly() > 0 && nearHome(p, k.homeFly()) && !plugin.arenas().isInArena(p)
                    && !plugin.tournaments().isParticipant(p);
            // own base (first place after /rtp): by rank, or anywhere near the own homes with an active Fly Booster
            boolean booster = flyBoosterLeft(p) > 0;
            boolean atBase = plugin.bases() != null && plugin.bases().mayFlyHere(p, p.getLocation());
            boolean boosted = booster && !plugin.arenas().isInArena(p) && !plugin.tournaments().isParticipant(p)
                    && (atBase || nearHome(p, 48));
            boolean may = fliesAtSpawn(p) && atSpawn || atHome || (atBase && k.baseFly()) || boosted;
            // MYTHOS / CREATOR may fly at the spawn (and CREATOR around the own homes)
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                if (may && !p.getAllowFlight()) {
                    p.setAllowFlight(true);
                    flying.add(p.getUniqueId());
                    plugin.messages().actionBar(p, "rank-perks.fly-on");
                } else if (flying.contains(p.getUniqueId()) && !may) {
                    flying.remove(p.getUniqueId());
                    boolean inAir = !((org.bukkit.entity.Entity) p).isOnGround();
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.setFallDistance(0);
                    if (inAir) {
                        // off the edge of the spawn island: float down gently
                        floating.add(p.getUniqueId());
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 20 * 120, 0, true, false, true));
                    }
                    plugin.messages().actionBar(p, "rank-perks.fly-off");
                }
            }
            if (floating.contains(p.getUniqueId()) && (((org.bukkit.entity.Entity) p).isOnGround() || p.isInWater())) {
                floating.remove(p.getUniqueId());
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
                p.setFallDistance(0);
            }
            if (atSpawn && !k.trail().isEmpty() && (p.getVelocity().lengthSquared() > 0.01 || p.isFlying())) {
                trail(p, k.trail());
            }
        }
    }

    /** milliseconds of Fly Booster left */
    public long flyBoosterLeft(Player p) {
        return Math.max(0, plugin.progress().get(p, "flybooster.until") - System.currentTimeMillis());
    }

    /** Fly Booster item: adds {@code minutes} of flight in the own base / around the own homes. */
    public void addFlyBooster(Player p, int minutes) {
        long start = Math.max(System.currentTimeMillis(), plugin.progress().get(p, "flybooster.until"));
        plugin.progress().set(p, "flybooster.until", start + minutes * 60_000L);
    }

    /** plots on the plot server for this rank (rank-perks.<rank>.plots) */
    public int plotsFor(String rankId) {
        Perks k = rankId == null ? null : perks.get(rankId.toUpperCase(Locale.ROOT));
        return k == null ? 1 : k.plots();
    }

    private boolean nearHome(Player p, int radius) {
        online.mythoscraft.core.player.PlayerData data = plugin.playerData().get(p);
        if (data == null) {
            return false;
        }
        for (online.mythoscraft.core.player.Home h : data.homes().values()) {
            if (h.world().equals(p.getWorld().getName())) {
                double dx = h.x() - p.getX();
                double dz = h.z() - p.getZ();
                if (dx * dx + dz * dz <= (double) radius * radius) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Spawn glide is replaced by real flight for these ranks. */
    public boolean fliesAtSpawn(Player p) {
        return of(p).spawnFly() || atLeastCreator(p);
    }

    /** CREATOR and every rank above it (by weight): fly at the spawn, host item and coin giveaways. */
    public boolean atLeastCreator(Player p) {
        online.mythoscraft.core.rank.Rank creator = plugin.ranks().byId("CREATOR");
        online.mythoscraft.core.rank.Rank own = plugin.ranks().rankOf(p);
        return creator != null && own != null && own.weight() >= creator.weight();
    }

    private static void trail(Player p, String kind) {
        var at = p.getLocation().add(0, 0.15, 0);
        switch (kind.toLowerCase(Locale.ROOT)) {
            case "sparkle" -> p.getWorld().spawnParticle(Particle.WAX_ON, at, 2, 0.2, 0.05, 0.2, 0);
            case "stars" -> p.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0.2, 0.05, 0.2, 0.005);
            case "mythos" -> {
                p.getWorld().spawnParticle(Particle.DUST, at, 3, 0.25, 0.1, 0.25, 0, new Particle.DustOptions(Color.fromRGB(0xc084fc), 1.1f));
                p.getWorld().spawnParticle(Particle.DUST, at, 2, 0.25, 0.1, 0.25, 0, new Particle.DustOptions(Color.fromRGB(0xf59e0b), 1.1f));
                p.getWorld().spawnParticle(Particle.END_ROD, at.add(0, 1.9, 0), 1, 0.3, 0.1, 0.3, 0.002);
            }
            default -> p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 1, 0.2, 0.05, 0.2, 0);
        }
    }

    public void shutdown() {
        for (UUID id : flying) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
        flying.clear();
    }
}
