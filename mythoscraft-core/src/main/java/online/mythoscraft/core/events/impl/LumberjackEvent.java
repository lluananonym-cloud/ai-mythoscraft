package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.MythosEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashSet;
import java.util.Set;

/** Lumberjack contest: every natural log counts, rare woods more. Placed logs never count. */
public final class LumberjackEvent extends MythosEvent {

    private final Set<String> placed = new HashSet<>();

    public LumberjackEvent(MythosCraft plugin) {
        super(plugin, "lumberjack");
    }

    @Override
    protected int defaultDuration() {
        return 240;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.points";
    }

    @Override
    protected void onStart() {
        placed.clear();
    }

    @Override
    protected void onTick() {
    }

    private static boolean log(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_WOOD") || n.endsWith("_HYPHAE");
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (log(event.getBlockPlaced().getType())) {
            placed.add(key(event.getBlockPlaced()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Player p = event.getPlayer();
        if (!log(b.getType()) || !eligible(p) || placed.remove(key(b))) {
            return;
        }
        String n = b.getType().name();
        int points = n.startsWith("CHERRY") || n.startsWith("PALE_OAK") || n.startsWith("CRIMSON") || n.startsWith("WARPED")
                || n.startsWith("MANGROVE") ? 2 : 1;
        addScore(p, points);
        b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0, b.getBlockData());
        plugin.messages().actionBar(p, "events.lumberjack.point", "points", points, "total", Math.round(score(p.getUniqueId())));
    }

    @Override
    protected void cleanup() {
        placed.clear();
    }
}
