package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.rank.MythosOwner;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.rank.RankManager;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /rank set &lt;player&gt; &lt;rank&gt; [duration] · /rank remove &lt;player&gt; · /rank info [player] · /rank list.
 * The MYTHOS rank can only be given or taken by MinePro641 himself (checked by UUID, see {@link MythosOwner}).
 */
public final class RankCommand extends BaseCommand {

    public RankCommand(MythosCraft plugin) {
        super(plugin);
    }

    private boolean mayManage(CommandSender sender) {
        return sender.hasPermission(Perms.RANK_ADMIN) || MythosOwner.is(sender);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        if (args.length == 0 && sender instanceof Player viewer && mayManage(sender)) {
            new online.mythoscraft.core.rank.RankManagerMenu(plugin, viewer).open(viewer);
            return;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "list" -> {
                plugin.messages().send(sender, "rank.list-header");
                for (Rank r : plugin.ranks().ranks()) {
                    plugin.messages().sendRaw(sender, "rank.list-line", "rank", Text.parse(r.display()), "id", r.id(),
                            "weight", r.weight(), "homes", r.homes(), "multiplier", r.coinMultiplier());
                }
            }
            case "info" -> {
                if (args.length < 2) {
                    Player p = player(sender);
                    PlayerData d = data(p);
                    info(sender, p.getName(), d.snapshot().rankId(), d.rankExpires(), p.getUniqueId());
                    return;
                }
                String name = args[1];
                plugin.playerData().lookup(name).whenComplete((snap, ex) -> plugin.tasks().sync(() -> {
                    if (snap == null || snap.isEmpty()) {
                        plugin.messages().send(sender, "general.player-unknown", "player", name);
                    } else {
                        info(sender, snap.get().name(), snap.get().rankId(), snap.get().rankExpires(), snap.get().uuid());
                    }
                }));
            }
            case "set", "give" -> {
                if (!mayManage(sender)) {
                    throw new CommandFail("general.no-permission");
                }
                if (args.length < 3) {
                    throw CommandFail.usage("/rank set <Spieler> <Rang> [Dauer z.B. 30d]");
                }
                Rank rank = plugin.ranks().byId(args[2]);
                if (rank == null) {
                    throw new CommandFail("rank.unknown", "rank", args[2]);
                }
                long expires = 0;
                if (args.length > 3 && !args[3].equalsIgnoreCase("perm")) {
                    Optional<Duration> d = Durations.parse(args[3]);
                    if (d.isEmpty()) {
                        throw new CommandFail("rank.invalid-duration");
                    }
                    expires = System.currentTimeMillis() + d.get().toMillis();
                }
                assign(sender, args[1], rank, expires);
            }
            case "remove", "reset" -> {
                if (!mayManage(sender)) {
                    throw new CommandFail("general.no-permission");
                }
                if (args.length < 2) {
                    throw CommandFail.usage("/rank remove <Spieler>");
                }
                assign(sender, args[1], plugin.ranks().defaultRank(), 0);
            }
            default -> throw CommandFail.usage("/rank <set|remove|info|list>");
        }
    }

    private void info(CommandSender to, String name, String rankId, long expires, java.util.UUID uuid) {
        boolean invalid = plugin.ranks().byId(rankId) == null || (expires > 0 && expires < System.currentTimeMillis());
        Rank rank = invalid ? (MythosOwner.is(uuid) ? plugin.ranks().mythos() : plugin.ranks().defaultRank()) : plugin.ranks().byId(rankId);
        String until = expires > 0 && rank != plugin.ranks().defaultRank()
                ? Durations.format(Duration.ofMillis(expires - System.currentTimeMillis())) : "∞";
        plugin.messages().send(to, "rank.info", "player", name, "rank", Text.parse(rank.display()), "remaining", until);
    }

    private void assign(CommandSender sender, String name, Rank rank, long expires) {
        plugin.playerData().lookup(name).whenComplete((snap, ex) -> plugin.tasks().sync(() -> {
            if (ex != null) {
                plugin.messages().send(sender, "general.error");
                return;
            }
            if (snap.isEmpty()) {
                plugin.messages().send(sender, "general.player-unknown", "player", name);
                return;
            }
            PlayerData.Snapshot s = snap.get();
            Rank current = plugin.ranks().byId(s.rankId()) != null ? plugin.ranks().byId(s.rankId())
                    : MythosOwner.is(s.uuid()) ? plugin.ranks().mythos() : null;
            RankManager.Check check = plugin.ranks().check(sender, rank, current, s.uuid());
            switch (check) {
                case NO_PERMISSION -> {
                    plugin.messages().send(sender, "general.no-permission");
                    return;
                }
                case MYTHOS_OWNER_ONLY -> {
                    plugin.messages().send(sender, "rank.mythos-owner-only", "owner", MythosOwner.NAME);
                    plugin.getLogger().warning(sender.getName() + " tried to change the MYTHOS rank of " + s.name() + " (denied)");
                    return;
                }
                case OWNER_LOCKED -> {
                    plugin.messages().send(sender, "rank.owner-locked", "owner", MythosOwner.NAME);
                    return;
                }
                case OWNER_ONLY_RANK -> {
                    plugin.messages().send(sender, "rank.owner-only-rank", "rank", Text.parse(rank.display()), "owner", MythosOwner.NAME);
                    plugin.getLogger().warning(sender.getName() + " tried to change the restricted rank of " + s.name() + " (denied)");
                    return;
                }
                default -> {
                }
            }
            String id = rank == plugin.ranks().defaultRank() ? null : rank.id();
            plugin.playerData().modify(s.uuid(), d -> d.rank(id, expires)).whenComplete((found, ex2) -> plugin.tasks().sync(() -> {
                if (ex2 != null || !Boolean.TRUE.equals(found)) {
                    plugin.messages().send(sender, "general.error");
                    return;
                }
                String duration = expires > 0 ? Durations.format(Duration.ofMillis(expires - System.currentTimeMillis())) : "∞";
                plugin.messages().send(sender, "rank.set", "player", s.name(), "rank", Text.parse(rank.display()), "duration", duration);
                plugin.getLogger().info("[Rank] " + sender.getName() + " set rank of " + s.name() + " to " + rank.id()
                        + (expires > 0 ? " for " + duration : ""));
                Player target = Bukkit.getPlayer(s.uuid());
                if (target != null) {
                    plugin.ranks().applyPermissions(target);
                    plugin.display().refresh(target);
                    plugin.messages().send(target, "rank.received", "rank", Text.parse(rank.display()), "duration", duration);
                }
                if (rank.isMythos()) {
                    plugin.messages().broadcast("rank.mythos-granted", "player", s.name(), "owner", MythosOwner.NAME);
                }
            }));
        }));
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return mayManage(sender) ? List.of("set", "remove", "info", "list") : List.of("info", "list");
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2 && (sub.equals("set") || sub.equals("remove") || sub.equals("info"))) {
            return onlineNames(sender);
        }
        if (args.length == 3 && sub.equals("set") && mayManage(sender)) {
            List<String> ids = new ArrayList<>();
            for (Rank r : plugin.ranks().ranks()) {
                if (!r.restricted() || MythosOwner.is(sender)) {
                    ids.add(r.id());
                }
            }
            return ids;
        }
        if (args.length == 4 && sub.equals("set")) {
            return List.of("7d", "30d", "90d", "perm");
        }
        return List.of();
    }
}
