package online.mythoscraft.plots;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plots per MythosCraft rank: on join the main server's website is asked for the player's rank
 * ({@code /api/public/plotperks?uuid=…} → {@code {"rank":"VIP","plots":2}}). The main plugin sets the numbers in its
 * config under rank-perks.&lt;rank&gt;.plots. Without an answer the config / permissions here count.
 */
final class RankLimits implements Listener {

    private static final Pattern PLOTS = Pattern.compile("\"plots\"\\s*:\\s*(\\d+)");
    private static final Pattern RANK = Pattern.compile("\"rank\"\\s*:\\s*\"([A-Za-z0-9_]+)\"");

    record Info(String rank, int plots) {
    }

    private final MythosPlots plugin;
    private final Map<UUID, Info> known = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    RankLimits(MythosPlots plugin) {
        this.plugin = plugin;
    }

    int plots(Player p) {
        Info i = known.get(p.getUniqueId());
        return i == null ? 0 : i.plots();
    }

    String rank(Player p) {
        Info i = known.get(p.getUniqueId());
        return i == null ? null : i.rank();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        fetch(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        known.remove(e.getPlayer().getUniqueId());
    }

    void fetch(UUID uuid) {
        String base = plugin.getConfig().getString("main-server.api-url", "").trim();
        if (base.isEmpty()) {
            return;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(base + "/api/public/plotperks?uuid=" + uuid))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "MythosPlots").GET().build();
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("main-server.api-url is not a valid URL: " + base);
            return;
        }
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            if (err != null || res.statusCode() != 200) {
                plugin.getLogger().fine("Rank lookup for " + uuid + " failed: " + (err != null ? err : res.statusCode()));
                return;
            }
            Matcher m = PLOTS.matcher(res.body());
            if (m.find()) {
                Matcher r = RANK.matcher(res.body());
                known.put(uuid, new Info(r.find() ? r.group(1) : "?", Integer.parseInt(m.group(1))));
            }
        });
    }
}
