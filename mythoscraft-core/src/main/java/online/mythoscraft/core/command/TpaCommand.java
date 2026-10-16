package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel. */
public final class TpaCommand extends BaseCommand {

    public TpaCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player player = player(sender);
        permission(sender, Perms.TPA);
        switch (label) {
            case "tpaccept" -> plugin.tpa().accept(player, args.length > 0 ? args[0] : null);
            case "tpdeny" -> plugin.tpa().deny(player, args.length > 0 ? args[0] : null);
            case "tpacancel" -> plugin.tpa().cancel(player);
            default -> {
                if (args.length == 0) {
                    throw CommandFail.usage("/" + label + " <Spieler>");
                }
                plugin.tpa().request(player, online(sender, args[0]), label.equals("tpahere"));
            }
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        return switch (label) {
            case "tpaccept", "tpdeny" -> plugin.tpa().incomingNames(player);
            case "tpacancel" -> List.of();
            default -> onlineNames(sender).stream().filter(n -> !n.equals(player.getName())).toList();
        };
    }
}
