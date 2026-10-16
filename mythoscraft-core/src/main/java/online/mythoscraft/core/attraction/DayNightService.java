package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Day and night at the spawn: lamps switch on at dusk and off at dawn, fireflies dance in the gardens at night. */
public final class DayNightService {

    private final MythosCraft plugin;
    private boolean enabled = true;
    private boolean fireflies = true;
    private Boolean litState;

    public DayNightService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.day-night");
        enabled = cfg.bool("enabled", true);
        fireflies = cfg.bool("fireflies", true);
        litState = null;
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 60, 100, "spawn lamps", this::lamps);
        plugin.tasks().repeat(this, 40, 10, "fireflies", this::fireflies);
    }

    public static boolean isNight(World w) {
        long t = w.getTime() % 24000;
        return t >= 12800 && t <= 23200;
    }

    /** Forces the lamps to be set again (after a rebuild). */
    public void refresh() {
        litState = null;
        lamps();
    }

    private void lamps() {
        Location c = plugin.attractionStore().center();
        if (!enabled || c == null || c.getWorld() == null) {
            return;
        }
        boolean night = isNight(c.getWorld());
        for (Location l : plugin.attractionStore().all("lamp")) {
            if (!l.isChunkLoaded()) {
                continue;
            }
            Block b = l.getBlock();
            BlockData data = b.getBlockData();
            if (data instanceof Lightable lamp && lamp.isLit() != night) {
                lamp.setLit(night);
                b.setBlockData(lamp, false);
            }
        }
        litState = night;
    }

    private void fireflies() {
        Location c = plugin.attractionStore().center();
        if (!enabled || !fireflies || c == null || c.getWorld() == null || !isNight(c.getWorld())) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Player p : c.getWorld().getPlayers()) {
            if (!plugin.attractionStore().inside(p.getLocation(), 0)) {
                continue;
            }
            for (int i = 0; i < 4; i++) {
                Location at = p.getLocation().add(rnd.nextDouble(-12, 12), rnd.nextDouble(0.5, 4), rnd.nextDouble(-12, 12));
                p.spawnParticle(Particle.FIREFLY, at, 1, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
    }
}
