package online.mythoscraft.core.rank;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RankManager {

    public enum Check { OK, NO_PERMISSION, MYTHOS_OWNER_ONLY, OWNER_LOCKED, OWNER_ONLY_RANK }

    private final MythosCraft plugin;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private Rank defaultRank;

    public RankManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader config) {
        ranks.clear();
        ConfigReader list = config.sub("ranks.list");
        List<Rank> parsed = new ArrayList<>();
        for (String key : list.keys()) {
            ConfigReader r = list.sub(key);
            String id = key.toUpperCase(Locale.ROOT);
            NamedTextColor color = NamedTextColor.NAMES.value(r.string("team-color", "white").toLowerCase(Locale.ROOT));
            if (color == null) {
                r.warn("team-color", "unbekannte Farbe – verwende white (erlaubt: green, aqua, light_purple, …)");
                color = NamedTextColor.WHITE;
            }
            TextColor chat = Text.color(r.string("chat-color", "white"));
            if (chat == null) {
                r.warn("chat-color", "unbekannte Farbe – verwende white");
                chat = NamedTextColor.WHITE;
            }
            parsed.add(new Rank(id, r.integer("weight", 0, 0, 10_000), r.string("display", id), r.string("prefix", ""),
                    r.string("badge", ""), color, chat, r.integer("homes", 3, 0, 1000),
                    r.decimal("coin-multiplier", 1.0, 0.0, 100.0), r.decimal("gem-multiplier", 1.0, 0.0, 100.0),
                    r.longValue("daily-bonus", 0, 0, 1_000_000_000L), r.strings("permissions"), r.bool("owner-only", false)));
        }
        if (parsed.stream().noneMatch(Rank::isMythos)) {
            config.warn("ranks.list", "Rang MYTHOS fehlt – eingebauter Standard wird verwendet");
            parsed.add(new Rank(Rank.MYTHOS, 100, "<gradient:#c084fc:#f59e0b><bold>MYTHOS</bold></gradient>",
                    "<gradient:#c084fc:#f59e0b><bold>MYTHOS</bold></gradient> <dark_gray>| ", "mythos",
                    NamedTextColor.LIGHT_PURPLE, NamedTextColor.WHITE, 15, 1.5, 1.5, 250, List.of(), true));
        }
        parsed.sort(Comparator.comparingInt(Rank::weight));
        parsed.forEach(r -> ranks.put(r.id(), r));
        String def = config.string("ranks.default", "SPIELER").toUpperCase(Locale.ROOT);
        defaultRank = ranks.get(def);
        if (defaultRank == null) {
            config.warn("ranks.default", "Rang '" + def + "' existiert nicht – erster Rang wird Standard");
            defaultRank = ranks.values().iterator().next();
        }
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20 * 60, 20 * 60, "rank expiry", this::checkExpiry);
        Bukkit.getOnlinePlayers().forEach(this::applyPermissions);
    }

    public Collection<Rank> ranks() {
        return ranks.values();
    }

    public Rank byId(String id) {
        return id == null ? null : ranks.get(id.toUpperCase(Locale.ROOT));
    }

    public Rank defaultRank() {
        return defaultRank;
    }

    public Rank mythos() {
        return ranks.get(Rank.MYTHOS);
    }

    /** Effective rank: expired or unknown ranks fall back to the default rank. */
    public Rank rankOf(PlayerData data) {
        if (data == null) {
            return defaultRank;
        }
        Rank r = byId(data.rankId());
        if (MythosOwner.is(data.uuid()) && (r == null || isExpired(data))) {
            return mythos();
        }
        if (r == null || isExpired(data)) {
            return defaultRank;
        }
        return r;
    }

    public Rank rankOf(Player player) {
        return rankOf(plugin.playerData().get(player));
    }

    public static boolean isExpired(PlayerData data) {
        return data.rankExpires() > 0 && data.rankExpires() < System.currentTimeMillis();
    }

    /**
     * Who may give {@code target} to a player that currently holds {@code current}:
     * MYTHOS (giving or taking it away) is reserved for MinePro641, his own rank can't be changed by anybody,
     * everything else needs mythos.rank.admin.
     */
    public Check check(CommandSender actor, Rank target, Rank current, UUID targetPlayer) {
        boolean staffRestricted = (target.ownerOnly() && !target.isMythos()) || (current != null && current.ownerOnly() && !current.isMythos());
        if (staffRestricted && !MythosOwner.is(actor) && !MythosOwner.is(targetPlayer)) {
            return Check.OWNER_ONLY_RANK;
        }
        return RankRules.check(MythosOwner.is(actor), actor.hasPermission(Perms.RANK_ADMIN), MythosOwner.is(targetPlayer),
                target.isMythos(), current != null && current.isMythos());
    }

    /** Join: enforce the owner's rank, clean up expired ranks and apply rank permissions. */
    public void handleJoin(Player player, PlayerData data) {
        if (MythosOwner.is(player.getUniqueId())) {
            // MinePro641 may choose any rank for himself; without a valid one he always falls back to MYTHOS
            if (byId(data.rankId()) == null || isExpired(data)) {
                data.rank(Rank.MYTHOS, 0);
            }
        } else if (data.rankId() != null && (isExpired(data) || byId(data.rankId()) == null)) {
            boolean expired = isExpired(data);
            data.rank(null, 0);
            if (expired) {
                plugin.messages().send(player, "rank.expired");
            }
        }
        applyPermissions(player);
    }

    public void handleQuit(Player player) {
        PermissionAttachment att = attachments.remove(player.getUniqueId());
        if (att != null) {
            try {
                player.removeAttachment(att);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
        }
    }

    public void applyPermissions(Player player) {
        handleQuit(player);
        Rank rank = rankOf(player);
        Rank mod = byId("MOD");
        boolean staff = mod != null && rank.weight() >= mod.weight();
        if (rank.permissions().isEmpty() && !staff) {
            return;
        }
        PermissionAttachment att = player.addAttachment(plugin);
        if (staff) {
            // the separate Vanish plugin (/vanish): MOD, ADMIN, OWNER … may use it and see vanished team members
            att.setPermission("vanish.use", true);
            att.setPermission("vanish.see", true);
            att.setPermission("mythos.invsee", true);         // /invsee and /endersee for the team
        }
        for (String perm : rank.permissions()) {
            boolean value = !perm.startsWith("-");
            att.setPermission(value ? perm : perm.substring(1), value);
        }
        attachments.put(player.getUniqueId(), att);
    }

    private void checkExpiry() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.playerData().get(p);
            if (d != null && d.rankId() != null && isExpired(d) && !MythosOwner.is(p.getUniqueId())) {
                d.rank(null, 0);
                plugin.messages().send(p, "rank.expired");
                applyPermissions(p);
                plugin.display().refresh(p);
            }
        }
    }

    public int maxHomes(Player player) {
        if (player.hasPermission(Perms.HOMES_UNLIMITED)) {
            return Integer.MAX_VALUE;
        }
        return rankOf(player).homes();
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            handleQuit(p);
        }
    }
}
