package online.mythoscraft.core.player;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {

    private final MythosCraft plugin;
    private final PlayerDataManager data;

    public PlayerDataListener(MythosCraft plugin, PlayerDataManager data) {
        this.plugin = plugin;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (!data.loadBlocking(event.getUniqueId(), event.getName())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messages().get("general.login-data-failed"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            data.handleDeniedLogin(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        data.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        data.handleQuit(event.getPlayer());
    }
}
