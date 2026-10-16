package online.mythoscraft.core.command;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.rank.MythosOwner;
import online.mythoscraft.core.rank.Rank;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moderation for the team (MOD and above): /mute, /unmute, /kick, /tempban, /ban (ADMIN+), /unban, /warn, /warns,
 * /goto, /modlog and /testkey (crate keys that show the opening but give nothing). Nobody can punish a player of the
 * same or a higher rank – only MinePro641 (owner) may act on everyone. Every action is logged.
 */
public final class ModerationService extends BaseCommand implements Listener {

    private record Punishment(long until, String reason, String by) {
        boolean active() {
            return until < 0 || until > System.currentTimeMillis();
        }
    }

    private final File file;
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> bans = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> warns = new ConcurrentHashMap<>();
    private final List<String> log = new ArrayList<>();
    /** last known rank weight of every player (offline targets keep their protection) */
    private final Map<UUID, Integer> knownWeight = new ConcurrentHashMap<>();

    public ModerationService(MythosCraft plugin) {
        super(plugin);
        file = new File(plugin.getDataFolder(), "data/moderation.yml");
        load();
    }

    // ------------------------------------------------------------------ storage
    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String kind : new String[]{"mutes", "bans"}) {
            var sec = y.getConfigurationSection(kind);
            if (sec == null) {
                continue;
            }
            for (String id : sec.getKeys(false)) {
                try {
                    Punishment p = new Punishment(sec.getLong(id + ".until"), sec.getString(id + ".reason", ""), sec.getString(id + ".by", "?"));
                    (kind.equals("mutes") ? mutes : bans).put(UUID.fromString(id), p);
                } catch (IllegalArgumentException ignored) {
                    // broken entry
                }
            }
        }
        var w = y.getConfigurationSection("warns");
        if (w != null) {
            for (String id : w.getKeys(false)) {
                try {
                    warns.put(UUID.fromString(id), new ArrayList<>(w.getStringList(id)));
                } catch (IllegalArgumentException ignored) {
                    // broken entry
                }
            }
        }
        log.addAll(y.getStringList("log"));
        var kw = y.getConfigurationSection("ranks");
        if (kw != null) {
            for (String id : kw.getKeys(false)) {
                try {
                    knownWeight.put(UUID.fromString(id), kw.getInt(id));
                } catch (IllegalArgumentException ignored) {
                    // broken entry
                }
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        mutes.forEach((id, p) -> put(y, "mutes." + id, p));
        bans.forEach((id, p) -> put(y, "bans." + id, p));
        warns.forEach((id, l) -> y.set("warns." + id, l));
        y.set("log", log.size() > 500 ? log.subList(log.size() - 500, log.size()) : log);
        knownWeight.forEach((id, w) -> y.set("ranks." + id, w));
        try {
            file.getParentFile().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("moderation.yml: " + e.getMessage());
        }
    }

    private static void put(YamlConfiguration y, String path, Punishment p) {
        y.set(path + ".until", p.until());
        y.set(path + ".reason", p.reason());
        y.set(path + ".by", p.by());
    }

    private void audit(CommandSender by, String what) {
        String line = new SimpleDateFormat("dd.MM. HH:mm").format(new Date()) + " " + by.getName() + ": " + what;
        log.add(line);
        plugin.getLogger().info("[Moderation] " + line);
        save();
    }

    // ------------------------------------------------------------------ rank rules
    private int weight(UUID id) {
        Player online = plugin.getServer().getPlayer(id);
        Rank r = online != null ? plugin.ranks().rankOf(online) : null;
        if (r == null) {
            var data = plugin.playerData().get(id);
            r = data == null ? null : plugin.ranks().rankOf(data);
        }
        if (r != null) {
            return r.weight();
        }
        int known = knownWeight.getOrDefault(id, 0);
        // operators are always treated as top team
        return plugin.getServer().getOfflinePlayer(id).isOp() ? Math.max(known, 1_000_000) : known;
    }

    private int weight(CommandSender s) {
        if (!(s instanceof Player p)) {
            return Integer.MAX_VALUE;                       // console
        }
        if (MythosOwner.is(p.getUniqueId())) {
            return Integer.MAX_VALUE;
        }
        return weight(p.getUniqueId());
    }

    private boolean staff(CommandSender s, String rankId) {
        if (!(s instanceof Player p) || p.isOp() || p.hasPermission(Perms.ADMIN) && rankId.equals("MOD")) {
            return true;
        }
        Rank need = plugin.ranks().byId(rankId);
        return need != null && weight(p.getUniqueId()) >= need.weight() || MythosOwner.is(p.getUniqueId());
    }

    /** the target must have a LOWER rank than the moderator (owner may do everything) */
    private void mayTouch(CommandSender by, OfflinePlayer target) throws CommandFail {
        if (target.getUniqueId().equals(by instanceof Player p ? p.getUniqueId() : null)) {
            throw new CommandFail("moderation.not-yourself");
        }
        if (MythosOwner.is(target.getUniqueId()) || weight(target.getUniqueId()) >= weight(by)) {
            throw new CommandFail("moderation.higher-rank", "player", String.valueOf(target.getName()));
        }
    }

    private OfflinePlayer target(String name) throws CommandFail {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer o = plugin.getServer().getOfflinePlayerIfCached(name);
        if (o == null || !o.hasPlayedBefore()) {
            throw new CommandFail("moderation.unknown", "player", name);
        }
        return o;
    }

    /** 10m, 2h, 7d, 1w, perm */
    static long duration(String s) throws CommandFail {
        String v = s.toLowerCase(Locale.ROOT);
        if (v.equals("perm") || v.equals("permanent")) {
            return -1;
        }
        try {
            long n = Long.parseLong(v.substring(0, v.length() - 1));
            return switch (v.charAt(v.length() - 1)) {
                case 's' -> n * 1000;
                case 'm' -> n * 60_000;
                case 'h' -> n * 3_600_000;
                case 'd' -> n * 86_400_000;
                case 'w' -> n * 7 * 86_400_000;
                default -> throw new NumberFormatException();
            };
        } catch (RuntimeException e) {
            throw new CommandFail("moderation.bad-time", "value", s);
        }
    }

    private static String reason(String[] args, int from) {
        return args.length > from ? String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)) : "-";
    }

    private static String left(Punishment p) {
        if (p.until() < 0) {
            return "permanent";
        }
        long m = Math.max(1, (p.until() - System.currentTimeMillis()) / 60_000);
        return m >= 1440 ? (m / 1440) + "d " + (m % 1440 / 60) + "h" : m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    // ------------------------------------------------------------------ commands
    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        if (!staff(sender, "MOD")) {
            throw new CommandFail("general.no-permission");
        }
        switch (label) {
            case "mute" -> {
                if (args.length < 2) {
                    throw CommandFail.usage("/mute <Spieler> <10m|2h|7d|perm> [Grund]");
                }
                OfflinePlayer t = target(args[0]);
                mayTouch(sender, t);
                long d = duration(args[1]);
                Punishment p = new Punishment(d < 0 ? -1 : System.currentTimeMillis() + d, reason(args, 2), sender.getName());
                mutes.put(t.getUniqueId(), p);
                audit(sender, "mute " + t.getName() + " " + args[1] + " – " + p.reason());
                plugin.messages().send(sender, "moderation.muted", "player", t.getName(), "time", left(p), "reason", p.reason());
                if (t.getPlayer() != null) {
                    plugin.messages().send(t.getPlayer(), "moderation.you-muted", "time", left(p), "reason", p.reason());
                }
            }
            case "unmute" -> {
                OfflinePlayer t = target(need(args, "/unmute <Spieler>"));
                mutes.remove(t.getUniqueId());
                audit(sender, "unmute " + t.getName());
                plugin.messages().send(sender, "moderation.unmuted", "player", t.getName());
            }
            case "kick" -> {
                Player t = online(sender, need(args, "/kick <Spieler> [Grund]"));
                mayTouch(sender, t);
                String r = reason(args, 1);
                audit(sender, "kick " + t.getName() + " – " + r);
                t.kick(plugin.messages().get("moderation.kick-screen", "reason", r, "by", sender.getName()));
                plugin.messages().send(sender, "moderation.kicked", "player", t.getName());
            }
            case "tempban", "ban" -> {
                boolean perm = label.equals("ban");
                if (perm && !staff(sender, "ADMIN")) {
                    throw new CommandFail("moderation.admin-only");
                }
                if (args.length < (perm ? 1 : 2)) {
                    throw CommandFail.usage(perm ? "/ban <Spieler> [Grund]" : "/tempban <Spieler> <1h|7d|…> [Grund]");
                }
                OfflinePlayer t = target(args[0]);
                mayTouch(sender, t);
                long d = perm ? -1 : duration(args[1]);
                if (d < 0 && !staff(sender, "ADMIN")) {
                    throw new CommandFail("moderation.admin-only");
                }
                Punishment p = new Punishment(d < 0 ? -1 : System.currentTimeMillis() + d, reason(args, perm ? 1 : 2), sender.getName());
                bans.put(t.getUniqueId(), p);
                audit(sender, (perm ? "ban " : "tempban ") + t.getName() + (perm ? "" : " " + args[1]) + " – " + p.reason());
                if (t.getPlayer() != null) {
                    t.getPlayer().kick(banScreen(p));
                }
                plugin.messages().send(sender, "moderation.banned", "player", t.getName(), "time", left(p), "reason", p.reason());
            }
            case "unban" -> {
                OfflinePlayer t = target(need(args, "/unban <Spieler>"));
                bans.remove(t.getUniqueId());
                audit(sender, "unban " + t.getName());
                plugin.messages().send(sender, "moderation.unbanned", "player", t.getName());
            }
            case "warn" -> {
                if (args.length < 2) {
                    throw CommandFail.usage("/warn <Spieler> <Grund>");
                }
                OfflinePlayer t = target(args[0]);
                mayTouch(sender, t);
                String r = reason(args, 1);
                List<String> list = warns.computeIfAbsent(t.getUniqueId(), k -> new ArrayList<>());
                list.add(new SimpleDateFormat("dd.MM.yy").format(new Date()) + " – " + r + " (" + sender.getName() + ")");
                audit(sender, "warn " + t.getName() + " – " + r);
                plugin.messages().send(sender, "moderation.warned", "player", t.getName(), "count", list.size());
                if (t.getPlayer() != null) {
                    plugin.messages().send(t.getPlayer(), "moderation.you-warned", "reason", r, "count", list.size());
                }
            }
            case "warns" -> {
                OfflinePlayer t = target(need(args, "/warns <Spieler>"));
                List<String> list = warns.getOrDefault(t.getUniqueId(), List.of());
                plugin.messages().send(sender, "moderation.warns-header", "player", t.getName(), "count", list.size());
                for (String w : list) {
                    sender.sendMessage(Component.text(" • " + w));
                }
            }
            case "goto" -> {
                Player me = player(sender);
                Player t = online(sender, need(args, "/goto <Spieler>"));
                online.mythoscraft.core.cosmetic.CompanionService.unmount(me);
                me.teleportAsync(t.getLocation());
                audit(sender, "goto " + t.getName());
            }
            case "modlog" -> {
                int from = Math.max(0, log.size() - 15);
                plugin.messages().send(sender, "moderation.log-header");
                for (String l : log.subList(from, log.size())) {
                    sender.sendMessage(Component.text(" " + l));
                }
            }
            case "testkey" -> {
                Player me = player(sender);
                String crate = need(args, "/testkey <Kiste> [Anzahl]");
                int n = args.length > 1 ? Math.max(1, Math.min(64, Integer.parseInt(args[1].replaceAll("\\D", "0")))) : 1;
                var key = plugin.crates().createTestKey(crate, n);
                if (key == null) {
                    throw new CommandFail("moderation.unknown-crate", "crate", crate);
                }
                online.mythoscraft.core.item.CustomItemManager.give(me, key);
                audit(sender, "testkey " + crate + " x" + n);
                plugin.messages().send(sender, "moderation.testkey", "crate", crate, "amount", n);
            }
            default -> {
            }
        }
    }

    private static String need(String[] args, String usage) throws CommandFail {
        if (args.length < 1) {
            throw CommandFail.usage(usage);
        }
        return args[0];
    }

    private Component banScreen(Punishment p) {
        return plugin.messages().get("moderation.ban-screen", "reason", p.reason(), "time", left(p), "by", p.by());
    }

    // ------------------------------------------------------------------ enforcement
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(60, "remember rank", () -> {
            if (p.isOnline()) {
                Rank r = plugin.ranks().rankOf(p);
                Integer before = knownWeight.put(p.getUniqueId(), r == null ? 0 : r.weight());
                if (before == null || before != (r == null ? 0 : r.weight())) {
                    save();
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        Punishment p = bans.get(event.getUniqueId());
        if (p == null) {
            return;
        }
        if (!p.active()) {
            bans.remove(event.getUniqueId());
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(p));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Punishment p = mutes.get(event.getPlayer().getUniqueId());
        if (p == null) {
            return;
        }
        if (!p.active()) {
            mutes.remove(event.getPlayer().getUniqueId());
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "moderation.you-muted", "time", left(p), "reason", p.reason());
    }

    /** muted players can't write private messages either */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Punishment p = mutes.get(event.getPlayer().getUniqueId());
        if (p == null || !p.active()) {
            return;
        }
        String cmd = event.getMessage().toLowerCase(Locale.ROOT).split(" ")[0];
        if (List.of("/msg", "/tell", "/w", "/r", "/reply", "/me", "/pronounce", "/bc", "/helpop").contains(cmd)) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "moderation.you-muted", "time", left(p), "reason", p.reason());
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (!staff(sender, "MOD")) {
            return List.of();
        }
        if (label.equals("testkey") && args.length == 1) {
            return plugin.crates().types().stream().map(t -> t.id()).toList();
        }
        if (args.length == 1 && !label.equals("modlog")) {
            return onlineNames(sender);
        }
        if (args.length == 2 && (label.equals("mute") || label.equals("tempban"))) {
            return List.of("10m", "1h", "1d", "7d", "perm");
        }
        return List.of();
    }
}
