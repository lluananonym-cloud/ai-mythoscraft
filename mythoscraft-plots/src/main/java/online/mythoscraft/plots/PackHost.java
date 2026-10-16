package online.mythoscraft.plots;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * The same resource pack as the main server (built into the jar from mythoscraft-core/resourcepack): served by a
 * tiny web server of its own (MineKeep: ports 2053, 2083, 2087, 2096) or from an external link, sent on join.
 */
final class PackHost implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("mythoscraft-pack".getBytes());

    private final MythosPlots plugin;
    private HttpServer server;
    private String url;
    private byte[] hash;

    PackHost(MythosPlots plugin) {
        this.plugin = plugin;
    }

    void start() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("resource-pack.enabled", true)) {
            return;
        }
        String external = cfg.getString("resource-pack.url", "").trim();
        String sha = cfg.getString("resource-pack.sha1", "").trim();
        if (!external.isEmpty()) {
            url = external;
            hash = sha.length() == 40 ? HexFormat.of().parseHex(sha) : null;
        } else {
            byte[] zip;
            try (InputStream in = plugin.getResource("resourcepack/MythosCraft-ResourcePack.zip")) {
                if (in == null) {
                    plugin.getLogger().warning("No resource pack inside the jar – players see plain items.");
                    return;
                }
                zip = in.readAllBytes();
                hash = MessageDigest.getInstance("SHA-1").digest(zip);
            } catch (Exception e) {
                plugin.getLogger().warning("Resource pack could not be read: " + e.getMessage());
                return;
            }
            String hex = HexFormat.of().formatHex(hash);
            String path = "/mythosplots-" + hex.substring(0, 12) + ".zip";
            int port = cfg.getInt("resource-pack.self-host.port", 2053);
            try {
                server = HttpServer.create(new InetSocketAddress(cfg.getString("resource-pack.self-host.bind", "0.0.0.0"), port), 16);
                byte[] body = zip;
                server.createContext("/", ex -> {
                    if (!ex.getRequestURI().getPath().equals(path)) {
                        ex.sendResponseHeaders(404, -1);
                        ex.close();
                        return;
                    }
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    ex.sendResponseHeaders(200, body.length);
                    ex.getResponseBody().write(body);
                    ex.close();
                });
                server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "MythosPlots-Pack");
                    t.setDaemon(true);
                    return t;
                }));
                server.start();
            } catch (Exception e) {
                plugin.getLogger().warning("Pack web server on port " + port + " failed: " + e.getMessage()
                        + " – set resource-pack.url to an external link instead.");
                return;
            }
            String base = cfg.getString("resource-pack.self-host.public-url", "https://mythoscraftsmpplot.minekeep.dev:2053").trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            url = base + path;
            plugin.getLogger().info("Resource pack (" + zip.length / 1024 + " KB) served at " + url);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (url == null) {
            return;
        }
        Player p = e.getPlayer();
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.setResourcePack(PACK_ID, url, hash, plugin.msg().get("pack-prompt"), required);
            }
        }, 20);
    }
}
