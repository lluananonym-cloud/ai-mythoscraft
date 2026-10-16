package online.mythoscraft.core.teleport;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Delayed teleports with an action-bar countdown. Moving to another block (or taking damage) cancels them when
 * configured. One pending teleport per player; a new one replaces the old one.
 */
public final class TeleportService implements Listener {

    private final MythosCraft plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private boolean cancelOnDamage = true;
    private SoundSpec tickSound = SoundSpec.NONE;
    private SoundSpec doneSound = SoundSpec.NONE;
    private SoundSpec cancelSound = SoundSpec.NONE;

    private static final class Pending {
        final Location target;
        final Component name;
        final boolean cancelOnMove;
        final int bx;
        final int by;
        final int bz;
        final Runnable after;
        int secondsLeft;

        Pending(Player p, Location target, Component name, int seconds, boolean cancelOnMove, Runnable after) {
            this.target = target;
            this.name = name;
            this.secondsLeft = seconds;
            this.cancelOnMove = cancelOnMove;
            this.after = after;
            Location l = p.getLocation();
            this.bx = l.getBlockX();
            this.by = l.getBlockY();
            this.bz = l.getBlockZ();
        }
    }

    public TeleportService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("teleport");
        cancelOnDamage = cfg.bool("cancel-on-damage", true);
        tickSound = sound(cfg, "sounds.countdown", "block.note_block.hat 0.6 1.6");
        doneSound = sound(cfg, "sounds.success", "entity.enderman.teleport 0.8 1.2");
        cancelSound = sound(cfg, "sounds.cancel", "block.note_block.bass 0.8 0.6");
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 20, "teleport countdown", this::tick);
    }

    private static SoundSpec sound(ConfigReader cfg, String path, String def) {
        SoundSpec s = SoundSpec.parse(cfg.string(path, def));
        if (s == null) {
            cfg.warn(path, "ungültiger Sound – deaktiviert");
            return SoundSpec.NONE;
        }
        return s;
    }

    /**
     * @param name display name of the destination for messages ("Spawn", "Home zuhause", …)
     */
    public void teleport(Player player, Location target, int delaySeconds, boolean cancelOnMove, Component name, Runnable after) {
        if (target == null || target.getWorld() == null) {
            plugin.messages().send(player, "teleport.invalid-target");
            return;
        }
        if (plugin.tournaments().isParticipant(player)) {
            plugin.messages().send(player, "tournament.no-teleport");
            return;
        }
        pending.remove(player.getUniqueId());
        if (delaySeconds <= 0 || player.hasPermission(Perms.TELEPORT_INSTANT)) {
            execute(player, target, name, after);
            return;
        }
        pending.put(player.getUniqueId(), new Pending(player, target, name, delaySeconds, cancelOnMove, after));
        plugin.messages().send(player, cancelOnMove ? "teleport.starting-still" : "teleport.starting",
                "target", name, "seconds", delaySeconds);
        plugin.messages().actionBar(player, "teleport.countdown", "target", name, "seconds", delaySeconds);
    }

    public boolean isPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void cancel(Player player, String reasonKey) {
        if (pending.remove(player.getUniqueId()) != null && reasonKey != null) {
            plugin.messages().send(player, reasonKey);
            cancelSound.play(player);
        }
    }

    private void tick() {
        if (pending.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            Player player = plugin.getServer().getPlayer(e.getKey());
            Pending p = e.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            p.secondsLeft--;
            if (p.secondsLeft <= 0) {
                it.remove();
                execute(player, p.target, p.name, p.after);
            } else {
                plugin.messages().actionBar(player, "teleport.countdown", "target", p.name, "seconds", p.secondsLeft);
                tickSound.play(player);
            }
        }
    }

    private void execute(Player player, Location target, Component name, Runnable after) {
        Location from = player.getLocation();
        player.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.4);
        online.mythoscraft.core.cosmetic.CompanionService.unmount(player);
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> plugin.tasks().sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (!success) {
                plugin.messages().send(player, "teleport.failed");
                return;
            }
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
            doneSound.play(player);
            plugin.messages().actionBar(player, "teleport.done", "target", name);
            if (after != null) {
                after.run();
            }
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (pending.isEmpty() || !event.hasChangedBlock()) {
            return;
        }
        Pending p = pending.get(event.getPlayer().getUniqueId());
        if (p == null || !p.cancelOnMove) {
            return;
        }
        Location to = event.getTo();
        if (to.getBlockX() != p.bx || to.getBlockY() != p.by || to.getBlockZ() != p.bz) {
            cancel(event.getPlayer(), "teleport.cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (cancelOnDamage && !pending.isEmpty() && event.getEntity() instanceof Player player && pending.containsKey(player.getUniqueId())) {
            cancel(player, "teleport.cancelled-damage");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        pending.clear();
    }
}
