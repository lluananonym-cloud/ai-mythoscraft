package online.mythoscraft.plots;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /gm &lt;c|s|sp|a&gt; [player] (also /gmc, /gms, /gmsp, /gma) – game mode for admins & co. Creative gets its own empty
 * inventory; the survival inventory comes back when switching back (creative items never reach MythosCraft).
 */
final class GameModeCommand implements TabExecutor {

    private final MythosPlots plugin;

    GameModeCommand(MythosPlots plugin) {
        this.plugin = plugin;
    }

    private static GameMode parse(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "c", "1", "creative", "gmc" -> GameMode.CREATIVE;
            case "s", "0", "survival", "gms" -> GameMode.SURVIVAL;
            case "sp", "3", "spectator", "gmsp" -> GameMode.SPECTATOR;
            case "a", "2", "adventure", "gma" -> GameMode.ADVENTURE;
            default -> null;
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean console = !(sender instanceof Player);
        if (!console && !plugin.mayChangeGameMode((Player) sender)) {
            plugin.msg().send(sender, "no-permission");
            return true;
        }
        GameMode mode = parse(label);
        int next = 0;
        if (mode == null) {
            mode = args.length > 0 ? parse(args[0]) : null;
            next = 1;
        }
        if (mode == null) {
            plugin.msg().send(sender, "usage", "usage", "/gm <c|s|sp|a> [player]");
            return true;
        }
        Player target = args.length > next ? Bukkit.getPlayerExact(args[next]) : console ? null : (Player) sender;
        if (target == null) {
            plugin.msg().send(sender, "unknown-player", "player", args.length > next ? args[next] : "?");
            return true;
        }
        target.setGameMode(mode);
        plugin.msg().send(sender, "gamemode.set", "player", target.getName(), "mode", mode.name().toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (parse(alias) == null && args.length == 1) {
            return List.of("c", "s", "sp", "a");
        }
        return null;
    }
}
