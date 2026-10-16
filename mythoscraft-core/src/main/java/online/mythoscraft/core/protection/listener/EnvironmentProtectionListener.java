package online.mythoscraft.core.protection.listener;

import online.mythoscraft.core.protection.DamageSources;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.protection.ProtectionService;
import online.mythoscraft.core.protection.RegionManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Indirect changes: explosions, fire, liquids, pistons, dispensers, growing structures and mobs. These events have
 * no responsible player (or only an indirect one), so they must never move a change across a region border that
 * the target region does not allow – e.g. TNT outside a region cannot destroy blocks inside it.
 */
public final class EnvironmentProtectionListener implements Listener {

    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.MOUNT, CreatureSpawnEvent.SpawnReason.TRAP,
            CreatureSpawnEvent.SpawnReason.LIGHTNING, CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
            CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER);

    private final ProtectionService protection;
    private final RegionManager regions;

    public EnvironmentProtectionListener(ProtectionService protection, RegionManager regions) {
        this.protection = protection;
        this.regions = regions;
    }

    // ---- explosions ---------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!protection.query(event.getEntity().getLocation(), Flag.EXPLOSIONS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    /** Removes every block that sits in a location where explosions are not allowed – wherever the blast came from. */
    private void filterExplosion(List<Block> blocks) {
        if (blocks.isEmpty() || regions.index().isEmpty(blocks.get(0).getWorld().getName())) {
            return;
        }
        blocks.removeIf(b -> !protection.query(b, Flag.EXPLOSIONS));
    }

    // ---- fire ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer() != null ? event.getPlayer() : DamageSources.resolve(event.getIgnitingEntity());
        boolean allowed = player != null
                ? protection.check(player, event.getBlock(), Flag.FIRE)
                : protection.query(event.getBlock(), Flag.FIRE);
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!protection.query(event.getBlock(), Flag.FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Material source = event.getSource().getType();
        if ((source == Material.FIRE || source == Material.SOUL_FIRE) && !protection.query(event.getBlock(), Flag.FIRE)) {
            event.setCancelled(true);
        }
    }

    // ---- liquids, pistons, dispensers, growth -------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (protection.blocksCrossing(event.getBlock(), event.getToBlock(), Flag.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonViolates(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonViolates(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonViolates(Block piston, List<Block> moved, BlockFace direction) {
        if (regions.index().isEmpty(piston.getWorld().getName())) {
            return false;
        }
        Block head = piston.getRelative(direction);
        if (protection.blocksCrossing(piston, head, Flag.BUILD)) {
            return true;
        }
        for (Block b : moved) {
            if (protection.blocksCrossing(piston, b, Flag.BREAK) || protection.blocksCrossing(piston, b.getRelative(direction), Flag.BUILD)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (dispenser.getBlockData() instanceof Directional directional
                && protection.blocksCrossing(dispenser, dispenser.getRelative(directional.getFacing()), Flag.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        Player player = event.getPlayer();
        Block origin = event.getLocation().getBlock();
        if (player != null && !protection.check(player, origin, Flag.BUILD)) {
            event.setCancelled(true);
            return;
        }
        event.getBlocks().removeIf((BlockState state) -> player != null
                ? !protection.allows(player, state.getBlock(), Flag.BUILD)
                : protection.blocksCrossing(origin, state.getBlock(), Flag.BUILD));
    }

    // ---- mobs ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        Block block = event.getBlock();
        Player player = DamageSources.resolve(entity);
        if (player != null) {
            if (!protection.check(player, block, Flag.BREAK)) {
                event.setCancelled(true);
            }
            return;
        }
        if (entity instanceof FallingBlock) {
            // landing: stop sand/gravel that started outside from settling inside a protected region
            if (!event.getTo().isAir() && entity.getOrigin() != null
                    && protection.blocksCrossing(entity.getOrigin().getBlock(), block, Flag.BUILD)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!protection.query(block, Flag.BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        Material type = event.getBlock().getType();
        if ((type == Material.FARMLAND || type == Material.TURTLE_EGG) && !protection.query(event.getBlock(), Flag.BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Enemy && NATURAL.contains(event.getSpawnReason())
                && !protection.query(event.getLocation(), Flag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }
}
