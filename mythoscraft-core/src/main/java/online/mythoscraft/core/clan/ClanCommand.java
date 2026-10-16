package online.mythoscraft.core.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.clan.ClanService.Clan;
import online.mythoscraft.core.clan.ClanService.Role;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /clan … and /cc (clan chat). */
public final class ClanCommand extends BaseCommand {

    private static final List<String> SUBS = List.of("krieg", "create", "invite", "accept", "deny", "leave", "kick", "promote", "demote",
            "transfer", "disband", "info", "list", "top", "chat", "bank", "deposit", "withdraw", "sethome", "home", "color");

    private final ClanService clans;

    public ClanCommand(MythosCraft plugin, ClanService clans) {
        super(plugin);
        this.clans = clans;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        if (!clans.enabled()) {
            throw new CommandFail("clans.disabled");
        }
        if (label.equals("cc") || label.equals("clanchat")) {
            if (args.length == 0) {
                requireClan(p);
                plugin.messages().send(p, clans.toggleChat(p.getUniqueId()) ? "clans.chat-on" : "clans.chat-off");
            } else {
                clans.chat(p, String.join(" ", args));
            }
            return;
        }
        if (args.length == 0) {
            Clan c = clans.of(p.getUniqueId());
            if (c == null) {
                plugin.messages().send(p, "clans.help", "cost", Numbers.format(clans.createCost()));
            } else {
                info(p, c);
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create", "gruenden" -> create(p, args);
            case "invite", "einladen" -> invite(p, args);
            case "accept", "annehmen", "join" -> accept(p);
            case "deny", "ablehnen" -> {
                clans.clearInvite(p.getUniqueId());
                plugin.messages().send(p, "clans.invite-denied");
            }
            case "leave", "verlassen" -> leave(p);
            case "kick" -> kick(p, args);
            case "promote", "befoerdern" -> setRole(p, args, true);
            case "demote", "degradieren" -> setRole(p, args, false);
            case "transfer", "uebergeben" -> transfer(p, args);
            case "disband", "aufloesen" -> disband(p, args);
            case "info" -> {
                Clan c = args.length > 1 ? clans.byName(args[1]) : clans.of(p.getUniqueId());
                if (c == null) {
                    throw new CommandFail(args.length > 1 ? "clans.unknown" : "clans.not-in-clan", "clan", args.length > 1 ? args[1] : "");
                }
                info(p, c);
            }
            case "list", "liste" -> list(p);
            case "krieg", "war" -> war(p);
            case "top" -> top(p);
            case "chat" -> {
                requireClan(p);
                plugin.messages().send(p, clans.toggleChat(p.getUniqueId()) ? "clans.chat-on" : "clans.chat-off");
            }
            case "bank" -> {
                Clan c = requireClan(p);
                plugin.messages().send(p, "clans.bank", "bank", Numbers.format(c.bank()));
            }
            case "deposit", "einzahlen" -> deposit(p, args);
            case "withdraw", "auszahlen" -> withdraw(p, args);
            case "sethome" -> {
                Clan c = requireRole(p, Role.OFFICER);
                clans.setHome(c, p.getLocation());
                clans.broadcast(c, "clans.home-set", "player", p.getName());
            }
            case "home" -> {
                Clan c = requireClan(p);
                if (c.home() == null) {
                    throw new CommandFail("clans.no-home");
                }
                plugin.teleports().teleport(p, c.home(), 3, true, plugin.messages().get("clans.home-name", "clan", c.name()), null);
            }
            case "color", "farbe" -> color(p, args);
            default -> plugin.messages().send(p, "clans.help", "cost", Numbers.format(clans.createCost()));
        }
    }

    private Clan requireClan(Player p) throws CommandFail {
        Clan c = clans.of(p.getUniqueId());
        if (c == null) {
            throw new CommandFail("clans.not-in-clan");
        }
        return c;
    }

    private Clan requireRole(Player p, Role role) throws CommandFail {
        Clan c = requireClan(p);
        if (c.role(p.getUniqueId()).ordinal() < role.ordinal()) {
            throw new CommandFail(role == Role.LEADER ? "clans.leader-only" : "clans.officer-only");
        }
        return c;
    }

    private UUID member(Clan c, String name) throws CommandFail {
        for (Map.Entry<UUID, Role> e : c.members().entrySet()) {
            if (c.nameOf(e.getKey()).equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        throw new CommandFail("clans.not-member", "player", name);
    }

    private void create(Player p, String[] args) throws CommandFail {
        if (args.length < 3) {
            throw CommandFail.usage("/clan create <Name> <Tag>");
        }
        if (clans.of(p.getUniqueId()) != null) {
            throw new CommandFail("clans.already-in-clan");
        }
        String name = args[1];
        String tag = args[2];
        if (!ClanService.NAME.matcher(name).matches()) {
            throw new CommandFail("clans.bad-name");
        }
        if (!ClanService.TAG.matcher(tag).matches()) {
            throw new CommandFail("clans.bad-tag");
        }
        if (clans.byName(name) != null || clans.tagTaken(tag)) {
            throw new CommandFail("clans.taken");
        }
        PlayerData data = data(p);
        if (clans.createCost() > 0 && plugin.economy().withdraw(data, Currency.COINS, clans.createCost()) != EconomyManager.Result.SUCCESS) {
            throw new CommandFail("clans.too-expensive", "cost", Numbers.format(clans.createCost()));
        }
        plugin.playerData().save(data);
        Clan c = clans.create(p, name, tag);
        plugin.messages().broadcast("clans.created", "player", p.getName(), "clan", c.name(), "tag", c.tag());
    }

    private void invite(Player p, String[] args) throws CommandFail {
        if (args.length < 2) {
            throw CommandFail.usage("/clan invite <Spieler>");
        }
        Clan c = requireRole(p, Role.OFFICER);
        Player target = online(p, args[1]);
        if (clans.of(target.getUniqueId()) != null) {
            throw new CommandFail("clans.target-in-clan", "player", target.getName());
        }
        if (c.members().size() >= clans.maxMembers()) {
            throw new CommandFail("clans.full", "max", clans.maxMembers());
        }
        clans.invite(c, target);
        plugin.messages().send(p, "clans.invited", "player", target.getName());
        plugin.messages().send(target, "clans.invite-received", "clan", c.name(), "player", p.getName());
    }

    private void accept(Player p) throws CommandFail {
        Clan c = clans.pendingInvite(p.getUniqueId());
        if (c == null) {
            throw new CommandFail("clans.no-invite");
        }
        if (clans.of(p.getUniqueId()) != null) {
            throw new CommandFail("clans.already-in-clan");
        }
        if (c.members().size() >= clans.maxMembers()) {
            throw new CommandFail("clans.full", "max", clans.maxMembers());
        }
        clans.join(c, p);
        clans.broadcast(c, "clans.joined", "player", p.getName(), "clan", c.name());
    }

    private void leave(Player p) throws CommandFail {
        Clan c = requireClan(p);
        if (c.role(p.getUniqueId()) == Role.LEADER && c.members().size() > 1) {
            throw new CommandFail("clans.leader-leave");
        }
        clans.broadcast(c, "clans.left", "player", p.getName());
        clans.remove(c, p.getUniqueId());
    }

    private void kick(Player p, String[] args) throws CommandFail {
        if (args.length < 2) {
            throw CommandFail.usage("/clan kick <Spieler>");
        }
        Clan c = requireRole(p, Role.OFFICER);
        UUID target = member(c, args[1]);
        if (target.equals(p.getUniqueId()) || c.role(target).ordinal() >= c.role(p.getUniqueId()).ordinal()) {
            throw new CommandFail("clans.cannot-kick");
        }
        String name = c.nameOf(target);
        clans.broadcast(c, "clans.kicked", "player", name, "by", p.getName());
        clans.remove(c, target);
        Player t = Bukkit.getPlayer(target);
        if (t != null) {
            plugin.messages().send(t, "clans.you-were-kicked", "clan", c.name());
        }
    }

    private void setRole(Player p, String[] args, boolean up) throws CommandFail {
        if (args.length < 2) {
            throw CommandFail.usage(up ? "/clan promote <Spieler>" : "/clan demote <Spieler>");
        }
        Clan c = requireRole(p, Role.LEADER);
        UUID target = member(c, args[1]);
        if (target.equals(p.getUniqueId())) {
            throw new CommandFail("clans.cannot-kick");
        }
        Role role = up ? Role.OFFICER : Role.MEMBER;
        clans.setRole(c, target, role);
        clans.broadcast(c, up ? "clans.promoted" : "clans.demoted", "player", c.nameOf(target));
    }

    private void transfer(Player p, String[] args) throws CommandFail {
        if (args.length < 2) {
            throw CommandFail.usage("/clan transfer <Spieler>");
        }
        Clan c = requireRole(p, Role.LEADER);
        UUID target = member(c, args[1]);
        if (target.equals(p.getUniqueId())) {
            return;
        }
        clans.setRole(c, target, Role.LEADER);
        clans.setRole(c, p.getUniqueId(), Role.OFFICER);
        clans.broadcast(c, "clans.transferred", "player", c.nameOf(target));
    }

    private void disband(Player p, String[] args) throws CommandFail {
        Clan c = requireRole(p, Role.LEADER);
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            plugin.messages().send(p, "clans.disband-confirm", "bank", Numbers.format(c.bank()));
            return;
        }
        long bank = c.bank();
        if (bank > 0) {
            plugin.economy().credit(p.getUniqueId(), Currency.COINS, bank, null, "clan disband " + c.name());
        }
        clans.broadcast(c, "clans.disbanded", "clan", c.name());
        clans.disband(c);
    }

    private long amount(String[] args, String usage) throws CommandFail {
        if (args.length < 2) {
            throw CommandFail.usage(usage);
        }
        try {
            long v = Long.parseLong(args[1].replace(".", "").replace("_", ""));
            if (v <= 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new CommandFail("clans.bad-amount");
        }
    }

    private void deposit(Player p, String[] args) throws CommandFail {
        Clan c = requireClan(p);
        long amount = amount(args, "/clan deposit <Coins>");
        PlayerData data = data(p);
        if (plugin.economy().withdraw(data, Currency.COINS, amount) != EconomyManager.Result.SUCCESS) {
            throw new CommandFail("clans.not-enough", "amount", Numbers.format(amount));
        }
        plugin.playerData().save(data);
        clans.addBank(c, amount);
        plugin.economy().log(p.getUniqueId(), null, Currency.COINS, amount, "clan deposit " + c.name());
        clans.broadcast(c, "clans.deposited", "player", p.getName(), "amount", Numbers.format(amount), "bank", Numbers.format(c.bank()));
    }

    private void withdraw(Player p, String[] args) throws CommandFail {
        Clan c = requireRole(p, Role.OFFICER);
        long amount = amount(args, "/clan withdraw <Coins>");
        if (c.bank() < amount) {
            throw new CommandFail("clans.bank-too-low", "bank", Numbers.format(c.bank()));
        }
        PlayerData data = data(p);
        clans.addBank(c, -amount);
        plugin.economy().deposit(data, Currency.COINS, amount);
        plugin.playerData().save(data);
        plugin.economy().log(null, p.getUniqueId(), Currency.COINS, amount, "clan withdraw " + c.name());
        clans.broadcast(c, "clans.withdrawn", "player", p.getName(), "amount", Numbers.format(amount), "bank", Numbers.format(c.bank()));
    }

    private void color(Player p, String[] args) throws CommandFail {
        Clan c = requireRole(p, Role.LEADER);
        if (args.length < 2 || NamedTextColor.NAMES.value(args[1].toLowerCase(Locale.ROOT)) == null) {
            throw new CommandFail("clans.bad-color", "colors", String.join(", ", NamedTextColor.NAMES.keys()));
        }
        clans.setColor(c, args[1].toLowerCase(Locale.ROOT));
        plugin.messages().send(p, "clans.color-set", "tag", Component.text(c.tag(), c.color()));
    }

    private void info(Player p, Clan c) {
        List<String> leaders = new ArrayList<>();
        List<String> officers = new ArrayList<>();
        List<String> members = new ArrayList<>();
        for (Map.Entry<UUID, Role> e : c.members().entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            String n = (op.isOnline() ? "<green>" : "<gray>") + c.nameOf(e.getKey());
            switch (e.getValue()) {
                case LEADER -> leaders.add(n);
                case OFFICER -> officers.add(n);
                default -> members.add(n);
            }
        }
        plugin.messages().send(p, "clans.info", "clan", c.name(), "tag", Component.text(c.tag(), c.color()),
                "count", c.members().size(), "max", clans.maxMembers(), "bank", Numbers.format(c.bank()),
                "leader", plugin.messages().format(String.join("<gray>, ", leaders)),
                "officers", plugin.messages().format(officers.isEmpty() ? "<dark_gray>-" : String.join("<gray>, ", officers)),
                "members", plugin.messages().format(members.isEmpty() ? "<dark_gray>-" : String.join("<gray>, ", members)));
    }

    private void list(Player p) {
        if (clans.all().isEmpty()) {
            plugin.messages().send(p, "clans.none");
            return;
        }
        plugin.messages().send(p, "clans.list-header", "count", clans.all().size());
        clans.all().stream().sorted(Comparator.comparing(Clan::name, String.CASE_INSENSITIVE_ORDER)).forEach(c ->
                plugin.messages().sendRaw(p, "clans.list-line", "tag", Component.text(c.tag(), c.color()), "clan", c.name(),
                        "count", c.members().size()));
    }

    /** /clan krieg – this week's clan war (no PvP: points from events, quests and job levels). */
    private void war(Player p) {
        ClanWarService war = plugin.clanWar();
        plugin.messages().send(p, "clans.war.header", "time", online.mythoscraft.core.util.Durations.format(war.timeLeft()));
        List<java.util.Map.Entry<String, Long>> ranking = war.ranking();
        if (ranking.isEmpty()) {
            plugin.messages().sendRaw(p, "clans.war.empty");
        }
        int place = 0;
        for (java.util.Map.Entry<String, Long> e : ranking) {
            Clan c = null;
            for (Clan x : clans.all()) {
                if (x.id().equals(e.getKey())) {
                    c = x;
                }
            }
            if (c == null) {
                continue;
            }
            place++;
            if (place <= 10) {
                plugin.messages().sendRaw(p, "clans.war.line", "place", place, "tag", Component.text(c.tag(), c.color()), "clan", c.name(),
                        "points", Numbers.format(e.getValue()));
            }
        }
        Clan own = clans.of(p.getUniqueId());
        if (own != null) {
            plugin.messages().sendRaw(p, "clans.war.yours", "clan", own.name(), "points", Numbers.format(war.points(own)));
        }
        plugin.messages().sendRaw(p, "clans.war.how");
    }

    private void top(Player p) {
        List<Clan> list = new ArrayList<>(clans.all());
        list.sort(Comparator.comparingLong((Clan c) -> c.bank() + c.members().size() * 1000L).reversed());
        plugin.messages().send(p, "clans.top-header");
        for (int i = 0; i < list.size() && i < 10; i++) {
            Clan c = list.get(i);
            plugin.messages().sendRaw(p, "clans.top-line", "place", i + 1, "tag", Component.text(c.tag(), c.color()), "clan", c.name(),
                    "bank", Numbers.format(c.bank()), "count", c.members().size());
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (label.equals("cc") || label.equals("clanchat")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBS;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("invite")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            if (sub.equals("color")) {
                return new ArrayList<>(NamedTextColor.NAMES.keys());
            }
            if (sub.equals("info")) {
                return clans.all().stream().map(Clan::name).toList();
            }
            if (sender instanceof Player p && List.of("kick", "promote", "demote", "transfer").contains(sub)) {
                Clan c = clans.of(p.getUniqueId());
                if (c != null) {
                    return c.members().keySet().stream().map(c::nameOf).toList();
                }
            }
            if (sub.equals("disband")) {
                return List.of("confirm");
            }
        }
        return List.of();
    }
}
