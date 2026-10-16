package online.mythoscraft.plots;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plots per MythosCraft rank: on join the main server's website is asked for the player's rank
 * ({@code /api/public/plotperks?uuid=…} → {@code {"rank":"VIP","plots":2}}). The main plugin sets the numbers in its
 * config under rank-perks.&lt;rank&gt;.plots. Without an answer the config / permissions here count.
 * The answer also carries the rank's looks – badge/prefix, name and chat colour – used here in chat, tab list and
 * above the head, just like on the main server.
 */
final class RankLimits implements Listener {

    record Info(String rank, int plots, Component prefix, Component textPrefix, NamedTextColor nameColor, TextColor chatColor,
                int weight) {
    }

    private final MythosPlots plugin;
    private final Map<UUID, Info> known = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> packLoaded = ConcurrentHashMap.newKeySet();
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

    Info info(UUID uuid) {
        return known.get(uuid);
    }

    /** badge (with resource pack) or text prefix of a player, as seen by {@code viewer} */
    Component prefix(UUID player, Player viewer) {
        Info i = known.get(player);
        if (i == null) {
            return Component.empty();
        }
        return viewer == null || packLoaded.contains(viewer.getUniqueId()) ? i.prefix() : i.textPrefix();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        fetch(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            packLoaded.add(e.getPlayer().getUniqueId());
            apply(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        known.remove(id);
        packLoaded.remove(id);
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(team(e.getPlayer()));
        if (t != null) {
            t.unregister();
        }
    }

    private static String team(Player p) {
        return "mr_" + p.getUniqueId().toString().substring(0, 8);
    }

    /** rank looks in the tab list and above the head */
    void apply(Player p) {
        Info i = known.get(p.getUniqueId());
        if (i == null || !p.isOnline()) {
            return;
        }
        Component prefix = packLoaded.contains(p.getUniqueId()) ? i.prefix() : i.textPrefix();
        p.playerListName(Component.empty().append(prefix).append(Component.text(p.getName(), i.nameColor())));
        try {
            p.setPlayerListOrder(Math.max(0, i.weight()));
        } catch (RuntimeException | LinkageError ignored) {
            // older API
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = board.getTeam(team(p));
        if (t == null) {
            t = board.registerNewTeam(team(p));
        }
        t.prefix(prefix);
        t.color(i.nameColor());
        t.addEntry(p.getName());
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
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
            try {
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                if (!o.has("plots")) {
                    return;
                }
                var gson = GsonComponentSerializer.gson();
                NamedTextColor name = NamedTextColor.NAMES.value(str(o, "name-color", "white"));
                TextColor chat = TextColor.fromHexString(str(o, "chat-color", "#AAAAAA"));
                known.put(uuid, new Info(str(o, "rank", "?"), o.get("plots").getAsInt(),
                        o.has("prefix") ? gson.deserialize(str(o, "prefix", "\"\"")) : Component.empty(),
                        o.has("text-prefix") ? gson.deserialize(str(o, "text-prefix", "\"\"")) : Component.empty(),
                        name == null ? NamedTextColor.WHITE : name, chat == null ? NamedTextColor.GRAY : chat,
                        o.has("weight") ? o.get("weight").getAsInt() : 0));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        apply(p);
                    }
                });
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("Rank answer for " + uuid + " not readable: " + ex);
            }
        });
    }
}
