package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Common command plumbing: error handling, permission checks, player lookup and tab filtering. */
public abstract class BaseCommand implements TabExecutor {

    protected final MythosCraft plugin;

    protected BaseCommand(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            plugin.messages().in(sender, () -> {
                try {
                    execute(sender, label.toLowerCase(Locale.ROOT), args);
                } catch (CommandFail fail) {
                    throw new FailWrapper(fail);
                }
            });
        } catch (FailWrapper w) {
            CommandFail fail = w.fail;
            plugin.messages().send(sender, fail.key(), fail.placeholders());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Command /" + label + " " + String.join(" ", args) + " failed", e);
            plugin.messages().send(sender, "general.error");
        }
        return true;
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        try {
            List<String> options = complete(sender, label.toLowerCase(Locale.ROOT), args);
            return filter(args.length == 0 ? "" : args[args.length - 1], options);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    protected abstract void execute(CommandSender sender, String label, String[] args) throws CommandFail;

    /** Carries a CommandFail through the language wrapper. */
    private static final class FailWrapper extends RuntimeException {
        final CommandFail fail;

        FailWrapper(CommandFail fail) {
            super(null, null, false, false);
            this.fail = fail;
        }
    }

    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return List.of();
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    protected static Player player(CommandSender sender) throws CommandFail {
        if (sender instanceof Player p) {
            return p;
        }
        throw new CommandFail("general.players-only");
    }

    protected static void permission(CommandSender sender, String permission) throws CommandFail {
        if (!sender.hasPermission(permission)) {
            throw new CommandFail("general.no-permission");
        }
    }

    protected static Player online(CommandSender viewer, String name) throws CommandFail {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            p = Bukkit.getPlayer(name);
        }
        if (p == null || (viewer instanceof Player v && !v.canSee(p))) {
            throw new CommandFail("general.player-not-found", "player", name);
        }
        return p;
    }

    protected PlayerData data(Player player) throws CommandFail {
        PlayerData data = plugin.playerData().get(player);
        if (data == null) {
            throw new CommandFail("general.data-loading");
        }
        return data;
    }

    protected static List<String> onlineNames(CommandSender viewer) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(viewer instanceof Player v) || v.canSee(p)) {
                names.add(p.getName());
            }
        }
        return names;
    }

    protected static List<String> filter(String prefix, Collection<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }

    protected static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
