package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.progress.ProgressMath;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Smaller spawn games: jump pads on the paths, the dropper tower (fall through the tube into the pool) and the hedge
 * maze (reach the golden plate in the middle). Daily rewards go through {@link ActivityRewards}.
 */
public final class SpawnGamesService implements Listener {

    private final MythosCraft plugin;
    private final Map<UUID, Long> dropperRuns = new HashMap<>();
    private final Map<UUID, Long> safeUntil = new HashMap<>();
    private final Map<UUID, Long> mazeStart = new HashMap<>();
    private double padPower = 1.8;
    private long dropperGems = 2;
    private long dropperCoins = 400;
    private long mazeGems = 2;
    private long mazeCoins = 400;

    public SpawnGamesService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    /** In the dropper (from the lift until landing) – the spawn glide is off then. */
    public boolean busy(Player p) {
        return dropperRuns.containsKey(p.getUniqueId());
    }

    /** Back to the foot of the dropper tower after a try. */
    private void reset(Player p) {
        Location lift = plugin.attractionStore().first("dropper_lift");
        if (lift == null) {
            return;
        }
        Location back = lift.clone().add(1.5, 0, -1.5);
        back.setYaw(p.getLocation().getYaw());
        plugin.tasks().later(25, "dropper reset", () -> {
            if (p.isOnline()) {
                p.setFallDistance(0);
                p.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        });
    }

    /**
     * Watchdog: nobody may stay stuck in the dropper tube (landed on a platform without moving on, or inside the tube
     * without a running try) – back to the foot of the tower.
     */
    private void dropperWatch() {
        Location pool = plugin.attractionStore().first("dropper_pool");
        Location start = plugin.attractionStore().first("dropper_start");
        if (pool == null || start == null || !pool.isChunkLoaded()) {
            return;
        }
        for (Player p : pool.getWorld().getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - pool.getX();
            double dz = l.getZ() - pool.getZ();
            if (dx * dx + dz * dz > 4.8 * 4.8 || l.getY() < pool.getY() - 5 || l.getY() > start.getY() - 1.5
                    || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                stuckSince.remove(p.getUniqueId());
                continue;
            }
            boolean water = l.getBlock().getType() == Material.WATER;
            boolean standing = ((org.bukkit.entity.Entity) p).isOnGround() && !water;
            Long run = dropperRuns.get(p.getUniqueId());
            if (run == null && water) {
                continue;                                  // finished, waiting for the reset
            }
            if (standing || run == null) {
                long since = stuckSince.computeIfAbsent(p.getUniqueId(), k -> System.currentTimeMillis());
                if (System.currentTimeMillis() - since > 1500) {
                    stuckSince.remove(p.getUniqueId());
                    if (run != null) {
                        dropperRuns.remove(p.getUniqueId());
                        plugin.messages().actionBar(p, "attractions.dropper.failed");
                        SoundSpec.of("entity.villager.no", 0.8f, 1f).play(p);
                    }
                    safeUntil.put(p.getUniqueId(), System.currentTimeMillis() + 3000);
                    reset(p);
                }
            } else {
                stuckSince.remove(p.getUniqueId());
            }
        }
    }

    private final Map<UUID, Long> stuckSince = new HashMap<>();

    public void reload(ConfigReader root) {
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 10, "dropper watchdog", this::dropperWatch);
        ConfigReader cfg = root.sub("attractions");
        padPower = cfg.decimal("jump-pads.power", 1.8, 0.2, 6);
        dropperGems = cfg.longValue("dropper.daily-gems", 2, 0, 100_000);
        dropperCoins = cfg.longValue("dropper.daily-coins", 400, 0, 100_000_000);
        mazeGems = cfg.longValue("maze.daily-gems", 2, 0, 100_000);
        mazeCoins = cfg.longValue("maze.daily-coins", 400, 0, 100_000_000);
    }

    private static boolean same(Block b, Location l) {
        return l != null && b.getWorld().equals(l.getWorld()) && b.getX() == l.getBlockX() && b.getY() == l.getBlockY()
                && b.getZ() == l.getBlockZ();
    }

    @EventHandler
    public void onPlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }
        Block b = event.getClickedBlock();
        Player p = event.getPlayer();
        for (Location pad : plugin.attractionStore().all("jump_pad")) {
            if (same(b, pad)) {
                Vector dir = new Vector(-Math.sin(Math.toRadians(pad.getYaw())), 0, Math.cos(Math.toRadians(pad.getYaw())));
                p.setVelocity(dir.multiply(padPower).setY(1.1));
                safeUntil.put(p.getUniqueId(), System.currentTimeMillis() + 8000);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
                SoundSpec.of("entity.slime.jump", 1f, 0.8f).play(p);
                return;
            }
        }
        List<Location> targets = plugin.attractionStore().all("cannon_target");
        List<Location> cannons = plugin.attractionStore().all("cannon");
        for (int i = 0; i < cannons.size(); i++) {
            Location c = cannons.get(i);
            if (same(b, c)) {
                Location target = i < targets.size() ? targets.get(i) : null;
                Vector dir = target == null ? new Vector(-Math.sin(Math.toRadians(c.getYaw())), 0, Math.cos(Math.toRadians(c.getYaw())))
                        : target.toVector().subtract(c.toVector().add(new Vector(0.5, 0, 0.5))).setY(0);
                double dist = Math.max(4, dir.length());
                dir.normalize();
                p.setVelocity(dir.multiply(Math.min(3.2, dist / 9.5)).setY(1.25));
                safeUntil.put(p.getUniqueId(), System.currentTimeMillis() + 10_000);
                p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 2, 0.3, 0.2, 0.3, 0);
                p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 30, 0.3, 0.3, 0.3, 0.2);
                SoundSpec.of("entity.generic.explode", 0.7f, 1.6f).playAt(p.getLocation());
                SoundSpec.of("entity.firework_rocket.launch", 1f, 0.8f).play(p);
                plugin.messages().actionBar(p, "attractions.cannon");
                return;
            }
        }
        if (same(b, plugin.attractionStore().first("dropper_lift"))) {
            Location start = plugin.attractionStore().first("dropper_start");
            if (start != null) {
                dropperRuns.put(p.getUniqueId(), 0L);
                p.teleport(start, PlayerTeleportEvent.TeleportCause.PLUGIN);
                SoundSpec.of("entity.enderman.teleport", 0.8f, 1.4f).play(p);
                plugin.messages().send(p, "attractions.dropper.ready");
            }
            return;
        }
        Location goal = plugin.attractionStore().first("maze_goal");
        if (same(b, goal)) {
            long now = System.currentTimeMillis();
            Long last = mazeStart.get(p.getUniqueId());
            if (last != null && now - last < 5000) {
                return; // still standing on the plate
            }
            mazeStart.put(p.getUniqueId(), now);
            plugin.progress().add(p, "maze.finishes", 1);
            plugin.messages().send(p, "attractions.maze.finished", "finishes", plugin.progress().get(p, "maze.finishes"));
            SeasonAmbienceService.fireworks(goal.clone().add(0.5, 1, 0.5), 1);
            SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.2f).play(p);
            ActivityRewards.pay(plugin, p, "maze", mazeGems, mazeCoins);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Long run = dropperRuns.get(p.getUniqueId());
        if (run == null || !event.hasChangedBlock()) {
            return;
        }
        Location start = plugin.attractionStore().first("dropper_start");
        Location pool = plugin.attractionStore().first("dropper_pool");
        if (start == null || pool == null || !p.getWorld().equals(pool.getWorld())) {
            dropperRuns.remove(p.getUniqueId());
            return;
        }
        Location to = event.getTo();
        double dx = to.getX() - pool.getX();
        double dz = to.getZ() - pool.getZ();
        boolean inTube = dx * dx + dz * dz <= 25;
        if (run == 0L && inTube && to.getY() < start.getY() - 1) {
            dropperRuns.put(p.getUniqueId(), System.currentTimeMillis());
            plugin.messages().actionBar(p, "attractions.dropper.go");
            return;
        }
        if (run == 0L) {
            if (dx * dx + dz * dz > 30 * 30 || to.getY() < start.getY() - 10) {
                dropperRuns.remove(p.getUniqueId());
            }
            return;
        }
        if (!inTube) {
            // climbed or got pushed out of the tube
            dropperRuns.remove(p.getUniqueId());
            plugin.messages().actionBar(p, "attractions.dropper.failed");
            reset(p);
            return;
        }
        if (to.getBlock().getType() == Material.WATER && inTube && to.getY() < pool.getY() + 1.5) {
            dropperRuns.remove(p.getUniqueId());
            long time = System.currentTimeMillis() - run;
            long best = plugin.progress().get(p, "dropper.best");
            boolean record = best <= 0 || time < best;
            if (record) {
                plugin.progress().set(p, "dropper.best", time);
            }
            plugin.progress().add(p, "dropper.finishes", 1);
            plugin.messages().send(p, record ? "attractions.dropper.record" : "attractions.dropper.finished",
                    "time", ProgressMath.time(time), "best", ProgressMath.time(record ? time : best));
            p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation(), 60, 1, 0.5, 1, 0.3);
            SoundSpec.of("entity.player.splash.high_speed", 1f, 1.2f).play(p);
            ActivityRewards.pay(plugin, p, "dropper", dropperGems, dropperCoins);
            reset(p);
            return;
        }
        if (((org.bukkit.entity.Entity) p).isOnGround() && to.getBlock().getType() != Material.WATER) {
            dropperRuns.remove(p.getUniqueId());
            safeUntil.put(p.getUniqueId(), System.currentTimeMillis() + 2000);
            plugin.messages().actionBar(p, "attractions.dropper.failed");
            SoundSpec.of("entity.villager.no", 0.8f, 1f).play(p);
            reset(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && (dropperRuns.containsKey(p.getUniqueId()) || safeUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dropperRuns.remove(event.getPlayer().getUniqueId());
        safeUntil.remove(event.getPlayer().getUniqueId());
        mazeStart.remove(event.getPlayer().getUniqueId());
    }

    /** /dropper: own best time and the top 5. */
    public void dropperInfo(Player p) {
        long best = plugin.progress().get(p, "dropper.best");
        plugin.messages().send(p, "attractions.dropper.info", "best", best > 0 ? ProgressMath.time(best) : "-",
                "finishes", plugin.progress().get(p, "dropper.finishes"));
        plugin.progress().lowest("dropper.best", 5).thenAccept(top -> plugin.tasks().sync(() -> {
            int place = 0;
            for (Map.Entry<String, Long> e : top) {
                place++;
                plugin.messages().sendRaw(p, "parkour.top-line", "place", place, "player", e.getKey(), "time", ProgressMath.time(e.getValue()));
            }
        }));
    }

    /** /labyrinth: how often you found the way. */
    public void mazeInfo(Player p) {
        plugin.messages().send(p, "attractions.maze.info", "finishes", plugin.progress().get(p, "maze.finishes"));
    }

    static List<String> commands() {
        return List.of("dropper", "labyrinth");
    }
}
