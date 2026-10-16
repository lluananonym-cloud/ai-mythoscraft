package online.mythoscraft.core.progress;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.events.impl.ParkourRaceEvent;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spawn parkour: step on the start plate, reach the checkpoints and the finish plate. Falling back to the ground
 * resets you to your last checkpoint. The first finish per day pays gems; best times are stored per player.
 */
public final class ParkourService implements Listener {

    private record Run(long started, int checkpoint, Location respawn) {
    }

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Map<UUID, Run> runs = new HashMap<>();

    public boolean inRun(Player p) {
        return runs.containsKey(p.getUniqueId());
    }
    private Location start;
    private final List<Location> checkpoints = new ArrayList<>();
    private Location finish;
    private long dailyGems = 3;
    private long dailyCoins = 300;

    public ParkourService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/parkour.yml");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("parkour");
        dailyGems = cfg.longValue("daily-gems", 3, 0, 100_000);
        dailyCoins = cfg.longValue("daily-coins", 300, 0, 100_000_000);
        YamlConfiguration yaml = store.load(false);
        start = LocationCodec.fromString(yaml.getString("start"));
        finish = LocationCodec.fromString(yaml.getString("finish"));
        checkpoints.clear();
        for (String s : yaml.getStringList("checkpoints")) {
            Location l = LocationCodec.fromString(s);
            if (l != null) {
                checkpoints.add(l);
            }
        }
    }

    public boolean exists() {
        return start != null && finish != null;
    }

    /** Called after /spawn place built the course. */
    public void define(Location start, List<Location> checkpoints, Location finish) {
        this.start = start;
        this.finish = finish;
        this.checkpoints.clear();
        this.checkpoints.addAll(checkpoints);
        runs.clear();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("start", LocationCodec.toString(start));
        yaml.set("finish", LocationCodec.toString(finish));
        List<String> cps = new ArrayList<>();
        checkpoints.forEach(c -> cps.add(LocationCodec.toString(c)));
        yaml.set("checkpoints", cps);
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
    }

    private static boolean same(Block b, Location l) {
        return l != null && b.getWorld().equals(l.getWorld()) && b.getX() == l.getBlockX() && b.getY() == l.getBlockY()
                && b.getZ() == l.getBlockZ();
    }

    @EventHandler
    public void onPlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null || !exists()) {
            return;
        }
        Block b = event.getClickedBlock();
        Player p = event.getPlayer();
        if (same(b, start)) {
            Run old = runs.get(p.getUniqueId());
            if (old == null || System.currentTimeMillis() - old.started() > 3000) {
                runs.put(p.getUniqueId(), new Run(System.currentTimeMillis(), 0, start.clone().add(0.5, 0.1, 0.5)));
                plugin.messages().actionBar(p, "parkour.started");
                SoundSpec.of("block.note_block.pling", 0.8f, 1.6f).play(p);
            }
            return;
        }
        Run run = runs.get(p.getUniqueId());
        if (run == null) {
            return;
        }
        for (int i = 0; i < checkpoints.size(); i++) {
            if (same(b, checkpoints.get(i)) && run.checkpoint() < i + 1) {
                runs.put(p.getUniqueId(), new Run(run.started(), i + 1, checkpoints.get(i).clone().add(0.5, 0.1, 0.5)));
                plugin.messages().actionBar(p, "parkour.checkpoint", "number", i + 1, "total", checkpoints.size(),
                        "time", ProgressMath.time(System.currentTimeMillis() - run.started()));
                SoundSpec.of("block.note_block.bell", 0.8f, 1.4f).play(p);
                return;
            }
        }
        if (same(b, finish)) {
            finish(p, run);
        }
    }

    private void finish(Player p, Run run) {
        runs.remove(p.getUniqueId());
        long time = System.currentTimeMillis() - run.started();
        long best = plugin.progress().get(p, "parkour.best");
        boolean record = best <= 0 || time < best;
        if (record) {
            plugin.progress().set(p, "parkour.best", time);
        }
        plugin.progress().add(p, "parkour.finishes", 1);
        plugin.messages().send(p, record ? "parkour.finished-record" : "parkour.finished", "time", ProgressMath.time(time),
                "best", ProgressMath.time(record ? time : best));
        online.mythoscraft.core.attraction.ActivityRewards.pay(plugin, p, "parkour", dailyGems, dailyCoins);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
        SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.2f).play(p);
        if (plugin.events().active() instanceof ParkourRaceEvent race) {
            race.finished(p, time);
        }
        plugin.achievements().check(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Run run = runs.get(p.getUniqueId());
        if (run == null || !event.hasChangedBlock()) {
            return;
        }
        if (System.currentTimeMillis() - run.started() > 15 * 60_000L || !p.getWorld().equals(run.respawn().getWorld())
                || p.getLocation().distanceSquared(start) > 40 * 40) {
            runs.remove(p.getUniqueId());
            plugin.messages().actionBar(p, "parkour.cancelled");
            return;
        }
        if (p.isFlying() || p.isGliding()) {
            runs.remove(p.getUniqueId());
            plugin.messages().actionBar(p, "parkour.cancelled");
            return;
        }
        if (run.checkpoint() > 0 && ((org.bukkit.entity.Entity) p).isOnGround() && p.getLocation().getY() < run.respawn().getY() - 2) {
            Location back = run.respawn().clone();
            back.setYaw(p.getLocation().getYaw());
            back.setPitch(p.getLocation().getPitch());
            p.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
            plugin.messages().actionBar(p, "parkour.reset", "number", run.checkpoint());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
    }

    /** /parkour: own best time and the top 5. */
    public void info(Player p) {
        if (!exists()) {
            plugin.messages().send(p, "parkour.none");
            return;
        }
        long best = plugin.progress().get(p, "parkour.best");
        plugin.messages().send(p, "parkour.info", "best", best > 0 ? ProgressMath.time(best) : "-",
                "finishes", plugin.progress().get(p, "parkour.finishes"));
        plugin.progress().lowest("parkour.best", 5).thenAccept(top -> plugin.tasks().sync(() -> {
            int place = 0;
            for (Map.Entry<String, Long> e : top) {
                place++;
                plugin.messages().sendRaw(p, "parkour.top-line", "place", place, "player", e.getKey(), "time", ProgressMath.time(e.getValue()));
            }
        }));
    }
}
