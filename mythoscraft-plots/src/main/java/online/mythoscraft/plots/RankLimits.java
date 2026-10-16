package online.mythoscraft.plots;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the main server says about a player: rank (badge, name and chat colours), coins, gems, how many plots he may
 * have and whether he may fly on his plot. Filled by {@link SyncClient}; shown in chat, tab list, above the head and
 * on the scoreboard – the same as on the survival server.
 */
final class RankLimits implements Listener {

    record Info(String rank, Component rankDisplay, int plots, Component prefix, Component textPrefix, Component name,
                NamedTextColor nameColor, TextColor chatColor, int weight, boolean fly, boolean staff, long coins, long gems,
                Map<String, Component[]> icons) {
    }

    private final MythosPlots plugin;
    private final Map<UUID, Info> known = new ConcurrentHashMap<>();
    private final Set<UUID> packLoaded = ConcurrentHashMap.newKeySet();

    RankLimits(MythosPlots plugin) {
        this.plugin = plugin;
    }

    int plots(Player p) {
        Info i = known.get(p.getUniqueId());
        return i == null ? 0 : i.plots();
    }

    Info info(UUID uuid) {
        return known.get(uuid);
    }

    boolean hasPack(Player p) {
        return packLoaded.contains(p.getUniqueId());
    }

    /** badge (with resource pack) or text prefix of a player, as seen by {@code viewer} */
    Component prefix(UUID player, Player viewer) {
        Info i = known.get(player);
        if (i == null) {
            return Component.empty();
        }
        return viewer == null || packLoaded.contains(viewer.getUniqueId()) ? i.prefix() : i.textPrefix();
    }

    Component icon(Player viewer, String id) {
        Info i = known.get(viewer.getUniqueId());
        Component[] c = i == null ? null : i.icons().get(id);
        if (c == null) {
            return Component.empty();
        }
        return packLoaded.contains(viewer.getUniqueId()) ? c[0] : c[1];
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static Component comp(JsonObject o, String key) {
        String s = str(o, key, null);
        if (s == null) {
            return Component.empty();
        }
        try {
            return GsonComponentSerializer.gson().deserialize(s);
        } catch (RuntimeException e) {
            return Component.empty();
        }
    }

    /** new data from the main server (pull on join, info every half minute) */
    void accept(UUID uuid, JsonObject o) {
        NamedTextColor name = NamedTextColor.NAMES.value(str(o, "name-color", "white"));
        TextColor chat = TextColor.fromHexString(str(o, "chat-color", "#AAAAAA"));
        Info old = known.get(uuid);
        Map<String, Component[]> icons = new HashMap<>();
        if (o.has("icons") && o.get("icons").isJsonObject()) {
            JsonObject all = o.getAsJsonObject("icons");
            for (String id : all.keySet()) {
                JsonObject i = all.getAsJsonObject(id);
                icons.put(id, new Component[]{comp(i, "pack"), comp(i, "text")});
            }
        }
        Component nameComp = o.has("name") ? comp(o, "name") : old != null ? old.name() : Component.empty();
        known.put(uuid, new Info(str(o, "rank", "?"), comp(o, "rank-display"), o.has("plots") ? o.get("plots").getAsInt() : 1,
                comp(o, "prefix"), comp(o, "text-prefix"), nameComp,
                name == null ? NamedTextColor.WHITE : name, chat == null ? NamedTextColor.GRAY : chat,
                o.has("weight") ? o.get("weight").getAsInt() : 0,
                o.has("fly") && o.get("fly").getAsBoolean(), o.has("staff") && o.get("staff").getAsBoolean(),
                o.has("coins") ? o.get("coins").getAsLong() : 0, o.has("gems") ? o.get("gems").getAsLong() : 0, icons));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                apply(p);
            }
        });
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
        plugin.board().copyTeam(p, t);
    }
}
