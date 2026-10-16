package online.mythoscraft.core.display;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.Text;
import org.bukkit.ServerLinks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLinksSendEvent;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Server Links…" in the pause menu (website, Discord, shop, wiki …) – configured under server-links in config.yml.
 * Entries without a URL are skipped.
 */
public final class ServerLinksService implements Listener {

    private final MythosCraft plugin;

    public ServerLinksService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLinks(PlayerLinksSendEvent event) {
        if (!plugin.getConfig().getBoolean("server-links.enabled", true)) {
            return;
        }
        List<Map<?, ?>> list = plugin.getConfig().getMapList("server-links.links");
        for (Map<?, ?> entry : list) {
            Object rawUrl = entry.get("url");
            if (rawUrl == null || String.valueOf(rawUrl).isBlank()) {
                continue;
            }
            try {
                URI uri = URI.create(String.valueOf(rawUrl).trim());
                Object type = entry.get("type");
                Object name = entry.get("name");
                if (type != null && (name == null || String.valueOf(name).isBlank())) {
                    event.getLinks().addLink(ServerLinks.Type.valueOf(String.valueOf(type).toUpperCase(Locale.ROOT)), uri);
                } else {
                    event.getLinks().addLink(Text.parse(String.valueOf(name)), uri);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("server-links: invalid entry " + entry + " (" + e.getMessage() + ")");
            }
        }
    }
}
