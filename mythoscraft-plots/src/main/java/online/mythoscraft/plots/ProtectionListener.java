package online.mythoscraft.plots;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;
import java.util.Objects;

/**
 * Everything in the plot world is protected: only owners and trusted players change a plot, nobody changes roads
 * (except admins), and nothing flows, grows, burns or gets pushed across plot edges. No explosions, no PvP.
 */
final class ProtectionListener implements Listener {

    private final MythosPlots plugin;

    ProtectionListener(MythosPlots plugin) {
        this.plugin = plugin;
    }

    private boolean guard(Player p, Location l, Cancellable e) {
        if (plugin.mayBuild(p, l)) {
            return false;
        }
        e.setCancelled(true);
        plugin.msg().bar(p, plugin.plotAt(l) == null ? "protected-road" : "protected-plot");
        return true;
    }

    private boolean world(Location l) {
        return plugin.isPlotWorld(l.getWorld());
    }

    /** same plot (or both outside any plot) – blocks may move between a and b */
    private boolean samePlot(Block a, Block b) {
        PlotId pa = plugin.grid().plotAt(a.getX(), a.getZ());
        PlotId pb = plugin.grid().plotAt(b.getX(), b.getZ());
        return pa != null && Objects.equals(pa, pb);
    }

    // ---- players
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        guard(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (guard(e.getPlayer(), e.getBlock().getLocation(), e)) {
            return;
        }
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState s : multi.getReplacedBlockStates()) {
                if (guard(e.getPlayer(), s.getLocation(), e)) {
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        guard(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        guard(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !world(b.getLocation())) {
            return;
        }
        if (e.getAction() == Action.PHYSICAL || (e.getAction() == Action.RIGHT_CLICK_BLOCK && b.getType().isInteractable())) {
            guard(e.getPlayer(), b.getLocation(), e);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem() != null && placesSomething(e.getItem().getType())) {
            // spawn eggs, boats, minecarts, bone meal … on the clicked face
            guard(e.getPlayer(), b.getRelative(e.getBlockFace() == null ? BlockFace.UP : e.getBlockFace()).getLocation(), e);
        }
    }

    /** items that put something into the world without a block place event */
    private static boolean placesSomething(org.bukkit.Material m) {
        String n = m.name();
        return n.endsWith("_SPAWN_EGG") || n.endsWith("_BOAT") || n.endsWith("_RAFT") || n.endsWith("MINECART")
                || m == org.bukkit.Material.BONE_MEAL || m == org.bukkit.Material.ARMOR_STAND || m == org.bukkit.Material.END_CRYSTAL
                || m == org.bukkit.Material.ITEM_FRAME || m == org.bukkit.Material.GLOW_ITEM_FRAME || m == org.bukkit.Material.PAINTING;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player)) {
            guard(e.getPlayer(), e.getRightClicked().getLocation(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        guard(e.getPlayer(), e.getRightClicked().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null) {
            guard(e.getPlayer(), e.getEntity().getLocation(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!world(e.getEntity().getLocation())) {
            return;
        }
        Entity remover = e.getRemover();
        if (remover instanceof Projectile pr && pr.getShooter() instanceof Player shooter) {
            remover = shooter;
        }
        if (remover instanceof Player p) {
            guard(p, e.getEntity().getLocation(), e);
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!world(e.getEntity().getLocation())) {
            return;
        }
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player shooter) {
            damager = shooter;
        }
        if (e.getEntity() instanceof Player) {
            if (damager instanceof Player && !plugin.getConfig().getBoolean("world.pvp", false)) {
                e.setCancelled(true);
            }
            return;
        }
        if (damager instanceof Player p) {
            guard(p, e.getEntity().getLocation(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (world(e.getPlayer().getLocation()) && !plugin.getConfig().getBoolean("world.item-drops", true)) {
            e.setCancelled(true);
        }
    }

    // ---- world
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (world(e.getBlock().getLocation()) && !samePlot(e.getBlock(), e.getToBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonOut(BlockPistonExtendEvent e) {
        if (!world(e.getBlock().getLocation())) {
            return;
        }
        Block piston = e.getBlock();
        if (!samePlot(piston, piston.getRelative(e.getDirection()))) {
            e.setCancelled(true);
            return;
        }
        for (Block b : e.getBlocks()) {
            if (!samePlot(piston, b.getRelative(e.getDirection()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonIn(BlockPistonRetractEvent e) {
        if (!world(e.getBlock().getLocation())) {
            return;
        }
        for (Block b : e.getBlocks()) {
            if (!samePlot(e.getBlock(), b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        if (!world(e.getLocation())) {
            return;
        }
        Block origin = e.getLocation().getBlock();
        List<BlockState> blocks = e.getBlocks();
        blocks.removeIf(s -> !samePlot(origin, s.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (world(e.getBlock().getLocation()) && !samePlot(e.getSource(), e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (world(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!world(e.getBlock().getLocation())) {
            return;
        }
        if (e.getPlayer() != null) {
            guard(e.getPlayer(), e.getBlock().getLocation(), e);
        } else if (e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (world(e.getLocation())) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (world(e.getBlock().getLocation())) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (!world(e.getBlock().getLocation())) {
            return;
        }
        if (e.getEntity() instanceof Player p) {
            guard(p, e.getBlock().getLocation(), e);
        } else if (!(e.getEntity() instanceof org.bukkit.entity.FallingBlock)
                || plugin.grid().plotAt(e.getBlock().getX(), e.getBlock().getZ()) == null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (world(e.getLocation())) {
            switch (e.getSpawnReason()) {
                case NATURAL, PATROL, RAID, REINFORCEMENTS, VILLAGE_INVASION, JOCKEY, TRAP -> e.setCancelled(true);
                default -> {
                }
            }
        }
    }
}
