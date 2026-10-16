package online.mythoscraft.core.web;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.rank.MythosOwner;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /website – link to the admin panel; /website login &lt;code&gt; signs the browser in; MinePro641 manages access:
 * /website allow &lt;player&gt; &lt;view|edit|admin&gt;, /website deny &lt;player&gt;.
 */
public final class WebsiteCommand extends BaseCommand {

    public WebsiteCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        WebPanelService web = plugin.webPanel();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "login" -> {
                Player p = player(sender);
                if (args.length < 2) {
                    throw CommandFail.usage("/website login <Code>");
                }
                switch (web.confirm(p, args[1])) {
                    case "ok" -> plugin.messages().send(p, "web.login-ok");
                    case "sent" -> plugin.messages().send(p, "web.login-sent");
                    case "denied" -> plugin.messages().send(p, "web.login-denied");
                    default -> plugin.messages().send(p, "web.login-unknown");
                }
            }
            case "allow", "erlauben" -> {
                owner(sender);
                if (args.length < 3) {
                    throw CommandFail.usage("/website allow <Spieler> <view|edit|admin>");
                }
                WebPanelService.Role role;
                try {
                    role = WebPanelService.Role.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw CommandFail.usage("/website allow <Spieler> <view|edit|admin>");
                }
                if (role == WebPanelService.Role.OWNER) {
                    role = WebPanelService.Role.ADMIN;
                }
                @SuppressWarnings("deprecation")
                var off = Bukkit.getOfflinePlayer(args[1]);
                web.allow(off.getUniqueId(), args[1], role);
                plugin.messages().send(sender, "web.allowed", "player", args[1], "role", role.name().toLowerCase(Locale.ROOT));
            }
            case "deny", "entziehen" -> {
                owner(sender);
                if (args.length < 2) {
                    throw CommandFail.usage("/website deny <Spieler>");
                }
                @SuppressWarnings("deprecation")
                var off = Bukkit.getOfflinePlayer(args[1]);
                plugin.messages().send(sender, web.deny(off.getUniqueId()) ? "web.denied" : "web.not-allowed", "player", args[1]);
            }
            case "relay" -> {
                owner(sender);
                plugin.messages().send(sender, web.relayStatus() ? "web.relay-connected" : "web.relay-offline", "url", web.url());
                plugin.messages().send(sender, "web.relay-secret", "secret", web.secretForOwner());
            }
            default -> plugin.messages().send(sender, "web.link", "url", web.url());
        }
    }

    private static void owner(CommandSender sender) throws CommandFail {
        if (!MythosOwner.is(sender) && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            throw new CommandFail("web.owner-only");
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return MythosOwner.is(sender) ? List.of("login", "allow", "deny", "relay") : List.of("login");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("allow")) {
            return List.of("view", "edit", "admin");
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("login")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
