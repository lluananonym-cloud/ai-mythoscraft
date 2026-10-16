package online.mythoscraft.core.arena;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Death/respawn rules inside arenas and keeping sessions consistent when players leave by other means. */
public final class ArenaListener implements Listener {

    private final MythosCraft plugin;
    private final ArenaManager arenas;

    public ArenaListener(MythosCraft plugin, ArenaManager arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        ArenaSession session = arenas.session(event.getEntity());
        if (session == null || session.spectator()) {
            return;
        }
        Arena arena = arenas.get(session.arena());
        if (arena == null) {
            return;
        }
        if (arena.keepInventory()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        } else if (arena.inventoryMode() == Arena.InventoryMode.SEPARATE) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        ArenaSession session = arenas.session(player);
        if (session == null) {
            return;
        }
        Arena arena = arenas.get(session.arena());
        if (arena != null && arena.respawnInArena() && arena.state() == ArenaState.RUNNING) {
            Location spawn = arena.randomSpawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
                plugin.tasks().later(1, "arena respawn kit", () -> {
                    if (player.isOnline() && arenas.isInArena(player)) {
                        arenas.prepare(player, arena);
                    }
                });
                return;
            }
        }
        if (session.returnLocation() != null && session.returnLocation().getWorld() != null) {
            event.setRespawnLocation(session.returnLocation());
        }
        plugin.tasks().later(1, "arena leave after death", () -> {
            if (player.isOnline()) {
                arenas.leave(player, false, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        if (arenas.isInArena(event.getPlayer())) {
            arenas.leave(event.getPlayer(), true, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        ArenaSession session = arenas.session(player);
        if (session == null || arenas.isInternalTeleport(player)) {
            return;
        }
        Arena arena = arenas.get(session.arena());
        if (arena == null || session.spectator()) {
            return;
        }
        if (!arena.contains(event.getTo())) {
            plugin.tasks().later(1, "arena leave on teleport", () -> {
                if (player.isOnline() && arenas.isInArena(player)) {
                    arenas.leave(player, false, true);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        ArenaSession session = arenas.session(player);
        if (session == null) {
            return;
        }
        Arena arena = arenas.get(session.arena());
        if (arena == null || arena.contains(event.getTo())) {
            return;
        }
        if (session.spectator()) {
            Location back = arena.spectatorLocation();
            if (back != null) {
                event.setTo(back);
            }
        } else {
            arenas.leave(player, false, true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        ArenaSession session = arenas.session(event.getPlayer());
        if (session == null || event.getPlayer().hasPermission("mythos.arena.bypass-commands")) {
            return;
        }
        Arena arena = arenas.get(session.arena());
        if (arena == null || !arena.blockCommands()) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0];
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!arenas.commandAllowed(label)) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "arena.command-blocked");
        }
    }
}
