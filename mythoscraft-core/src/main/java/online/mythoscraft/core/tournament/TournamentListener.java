package online.mythoscraft.core.tournament;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.protection.DamageSources;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * Keeps tournament arenas intact (only blocks placed during the fight can be broken, explosions only remove those,
 * every change is recorded for the rollback) and handles deaths, quits, the start freeze and command blocking.
 */
public final class TournamentListener implements Listener {

    private final MythosCraft plugin;
    private final TournamentManager manager;

    public TournamentListener(MythosCraft plugin, TournamentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private Tournament fightIn(Block block) {
        Tournament t = manager.active();
        return t != null && t.started && t.phase != Tournament.Phase.REGISTRATION && t.arena.contains(block.getLocation()) ? t : null;
    }

    // ---- block tracking -----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t == null) {
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                t.tracker.record(state.getBlock(), state.getBlockData());
            }
        } else {
            t.tracker.record(event.getBlock(), event.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null && !t.tracker.isTracked(event.getBlock())) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "tournament.no-break");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null) {
            t.tracker.record(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null && !t.tracker.isTracked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Tournament t = fightIn(event.getToBlock());
        if (t != null) {
            t.tracker.record(event.getToBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null) {
            t.tracker.record(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null) {
            t.tracker.record(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null) {
            t.tracker.record(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null && !t.tracker.isTracked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        Tournament t = fightIn(event.getBlock());
        if (t != null) {
            t.tracker.record(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filter(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filter(event.blockList());
    }

    /** Crystals, anchors and TNT only destroy what was built during the fight. */
    private void filter(List<Block> blocks) {
        Tournament t = manager.active();
        if (t == null || !t.started) {
            return;
        }
        blocks.removeIf(b -> t.arena.contains(b.getLocation()) && !t.tracker.isTracked(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        piston(event.getBlock(), event.getBlocks(), event.getDirection().getModX(), event.getDirection().getModY(),
                event.getDirection().getModZ(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        piston(event.getBlock(), event.getBlocks(), event.getDirection().getModX(), event.getDirection().getModY(),
                event.getDirection().getModZ(), event);
    }

    private void piston(Block piston, List<Block> moved, int dx, int dy, int dz, org.bukkit.event.Cancellable event) {
        Tournament t = fightIn(piston);
        if (t == null) {
            return;
        }
        for (Block b : moved) {
            if (!t.tracker.isTracked(b)) {
                event.setCancelled(true);
                return;
            }
        }
        for (Block b : moved) {
            t.tracker.record(b.getRelative(dx, dy, dz));
        }
    }

    // ---- fighters -----------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Tournament t = manager.active();
        if (t == null || !t.alive.contains(event.getEntity().getUniqueId())) {
            return;
        }
        if (t.phase == Tournament.Phase.STARTING) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            Player player = DamageSources.attacker(event);
            UUID attacker = player != null ? player.getUniqueId() : damager.getUniqueId();
            if (t.alive.contains(attacker)) {
                manager.recordAttack(event.getEntity().getUniqueId(), attacker);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Tournament t = manager.active();
        if (t == null || !t.alive.contains(p.getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        Player killer = p.getKiller();
        UUID killerId = killer != null ? killer.getUniqueId() : manager.lastAttacker(p.getUniqueId());
        manager.eliminate(p.getUniqueId(), killerId, false, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBotDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.getPersistentDataContainer().has(TournamentBots.BOT, PersistentDataType.STRING)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = e.getKiller();
        UUID killerId = killer != null ? killer.getUniqueId() : manager.lastAttacker(e.getUniqueId());
        manager.eliminate(e.getUniqueId(), killerId, true, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Location back = manager.pendingReturn(p.getUniqueId());
        if (back != null && back.getWorld() != null) {
            event.setRespawnLocation(back);
        }
        plugin.tasks().later(1, "tournament restore", () -> {
            if (p.isOnline()) {
                manager.completePendingRestore(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Tournament t = manager.active();
        if (t == null) {
            return;
        }
        if (t.phase == Tournament.Phase.REGISTRATION) {
            t.registered.remove(p.getUniqueId());
        } else if (t.alive.contains(p.getUniqueId())) {
            manager.eliminate(p.getUniqueId(), manager.lastAttacker(p.getUniqueId()), true, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Tournament t = manager.active();
        if (t == null || t.phase != Tournament.Phase.STARTING || !t.alive.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            event.setTo(new Location(from.getWorld(), from.getX(), to.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        Tournament t = manager.active();
        if (t == null || !t.fighting() || !t.alive.contains(p.getUniqueId()) || manager.isInternalTeleport(p)) {
            return;
        }
        if (!t.arena.contains(event.getTo())) {
            plugin.tasks().later(1, "tournament forfeit", () -> manager.eliminate(p.getUniqueId(), null, true, null));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!manager.isParticipant(p) || p.hasPermission("mythos.tournament.admin")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0];
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!manager.commandAllowed(label)) {
            event.setCancelled(true);
            plugin.messages().send(p, "tournament.command-blocked");
        }
    }
}
