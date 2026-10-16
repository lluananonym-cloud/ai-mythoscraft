package online.mythoscraft.plots;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /smp, /mythos, /hub – back to the main server. */
final class BackCommand implements CommandExecutor {

    private final MythosPlots plugin;

    BackCommand(MythosPlots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        plugin.msg().send(p, "transfer.sending");
        plugin.transferToMain(p);
        return true;
    }
}
