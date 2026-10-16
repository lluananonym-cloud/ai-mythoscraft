package online.mythoscraft.plots;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Join (spawn, game mode, welcome), plot enter messages, denied players, void protection and chat. */
final class PlayerListener implements Listener {

    private final MythosPlots plugin;
    private final Map<UUID, PlotId> current = new HashMap<>();

    PlayerListener(MythosPlots plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.joinMessage(plugin.msg().get("join", "player", p.getName()));
        if (!plugin.isPlotWorld(p.getWorld()) || !p.hasPlayedBefore() || plugin.getConfig().getBoolean("world.spawn-on-join", false)) {
            p.teleport(plugin.spawn());
        }
        // everyone builds in creative here (own inventory of this server – nothing comes over from survival)
        // the inventory from MythosCraft is a survival inventory – everyone starts in survival (admins: /gm)
        GameMode gm = mode();
        if (p.getGameMode() != gm) {
            p.setGameMode(gm);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.msg().plain(p, p.hasPlayedBefore() ? "welcome-back" : "welcome", "player", p.getName());
            }
        }, 40);
    }

    private GameMode mode() {
        String mode = plugin.getConfig().getString("world.gamemode", "CREATIVE");
        try {
            return GameMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("world.gamemode: unknown game mode " + mode);
            return GameMode.CREATIVE;
        }
    }

    /** the game mode stays what the plot world uses (team members may still go spectator) */
    @EventHandler(ignoreCancelled = true)
    public void onGameMode(org.bukkit.event.player.PlayerGameModeChangeEvent e) {
        if (!plugin.getConfig().getBoolean("world.force-gamemode", true)) {
            return;
        }
        GameMode gm = mode();
        if (e.getNewGameMode() != gm && !plugin.mayChangeGameMode(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        current.remove(e.getPlayer().getUniqueId());
        e.quitMessage(plugin.msg().get(plugin.hopped(e.getPlayer()) ? "quit-to-main" : "quit", "player", e.getPlayer().getName()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.spawn());
    }

    @EventHandler(ignoreCancelled = true)
    public void onVoid(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.VOID && plugin.isPlotWorld(p.getWorld())) {
            e.setCancelled(true);
            p.teleport(plugin.spawn());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (denied(e.getPlayer(), e.getTo())) {
            e.setCancelled(true);
            plugin.msg().send(e.getPlayer(), "you-are-denied");
        }
    }

    private boolean denied(Player p, Location to) {
        if (to == null || plugin.bypass(p)) {
            return false;
        }
        Plot plot = plugin.store().get(plugin.plotAt(to));
        return plot != null && plot.denied.contains(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player p = e.getPlayer();
        if (to.getY() < p.getWorld().getMinHeight() - 5) {
            p.teleport(plugin.spawn());
            return;
        }
        if (denied(p, to)) {
            Location back = e.getFrom().clone();
            back.setDirection(back.getDirection().multiply(-1));
            e.setTo(back);
            plugin.msg().bar(p, "you-are-denied");
            return;
        }
        PlotId now = plugin.plotAt(to);
        if (Objects.equals(now, current.get(p.getUniqueId()))) {
            return;
        }
        if (now == null) {
            current.remove(p.getUniqueId());
            return;
        }
        current.put(p.getUniqueId(), now);
        Plot plot = plugin.store().get(now);
        if (plot == null) {
            plugin.msg().bar(p, "enter-free", "id", now);
        } else {
            plugin.msg().bar(p, plot.owner().equals(p.getUniqueId()) ? "enter-own" : "enter", "id", now, "owner", plot.ownerName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.format", true)) {
            return;
        }
        RankLimits ranks = plugin.ranks();
        e.renderer((source, name, message, viewer) -> {
            RankLimits.Info i = ranks.info(source.getUniqueId());
            return Component.text()
                    .append(ranks.prefix(source.getUniqueId(), viewer instanceof Player v ? v : null))
                    .append(i != null && !Component.empty().equals(i.name()) ? i.name() : name.color(i == null ? NamedTextColor.WHITE : i.nameColor()))
                    .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                    .append(message.color(i == null ? NamedTextColor.GRAY : i.chatColor()))
                    .build();
        });
    }
}
