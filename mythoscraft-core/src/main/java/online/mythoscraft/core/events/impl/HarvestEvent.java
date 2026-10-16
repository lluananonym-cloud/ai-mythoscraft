package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.MythosEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Harvest festival: fully grown crops score points (melons and pumpkins more). Blocks placed during the event never
 * count, so nobody can place and break pumpkins for points. Works great with the harvest sickle.
 */
public final class HarvestEvent extends MythosEvent {

    private final Map<Material, Integer> points = new EnumMap<>(Material.class);
    private final Set<String> placed = new HashSet<>();

    public HarvestEvent(MythosCraft plugin) {
        super(plugin, "harvest");
    }

    @Override
    protected int defaultDuration() {
        return 300;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        points.clear();
        points.put(Material.WHEAT, 1);
        points.put(Material.CARROTS, 1);
        points.put(Material.POTATOES, 1);
        points.put(Material.BEETROOTS, 1);
        points.put(Material.NETHER_WART, 2);
        points.put(Material.COCOA, 2);
        points.put(Material.MELON, 2);
        points.put(Material.PUMPKIN, 2);
        points.put(Material.SWEET_BERRY_BUSH, 1);
        ConfigReader custom = cfg.sub("points");
        for (String key : custom.keys()) {
            Material m = Material.matchMaterial(key);
            if (m == null || !m.isBlock()) {
                custom.warn(key, "unbekannter Block");
            } else {
                points.put(m, custom.integer(key, 1, 0, 1000));
            }
        }
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

    private static String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material m = event.getBlockPlaced().getType();
        if (m == Material.MELON || m == Material.PUMPKIN) {
            placed.add(key(event.getBlockPlaced()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Integer value = points.get(b.getType());
        Player p = event.getPlayer();
        if (value == null || value <= 0 || !eligible(p) || placed.remove(key(b))) {
            return;
        }
        if (b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
            return;
        }
        addScore(p, value);
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, 0);
        plugin.messages().actionBar(p, "events.harvest.point", "points", value, "total", Math.round(score(p.getUniqueId())));
    }

    @Override
    protected void cleanup() {
        placed.clear();
    }
}
