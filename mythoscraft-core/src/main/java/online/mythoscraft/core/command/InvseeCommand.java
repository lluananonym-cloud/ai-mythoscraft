package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /invsee &lt;Spieler&gt; and /endersee &lt;Spieler&gt;: opens the live inventory (or ender chest) of an online player.
 * Changes are made directly in the player's inventory.
 */
public final class InvseeCommand extends BaseCommand {

    public InvseeCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player viewer = player(sender);
        permission(sender, "mythos.invsee");
        boolean ender = label.startsWith("ender") || label.equals("ec");
        if (args.length < 1) {
            throw CommandFail.usage(ender ? "/endersee <Spieler>" : "/invsee <Spieler>");
        }
        Player target = online(sender, args[0]);
        if (ender) {
            viewer.openInventory(target.getEnderChest());
            plugin.messages().send(viewer, "invsee.ender", "player", target.getName());
        } else {
            if (target.equals(viewer)) {
                throw new CommandFail("invsee.self");
            }
            viewer.openInventory(target.getInventory());
            plugin.messages().send(viewer, "invsee.opened", "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
    }
}
