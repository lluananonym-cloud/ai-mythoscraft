package online.mythoscraft.plots;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /money, /coins, /gems, /bal – coins and gems from the survival server */
final class BalanceCommand implements CommandExecutor {

    private final MythosPlots plugin;

    BalanceCommand(MythosPlots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        RankLimits.Info i = plugin.ranks().info(p.getUniqueId());
        if (i == null) {
            plugin.msg().send(p, "sync.loading");
            return true;
        }
        plugin.msg().send(p, "balance", "coins", String.format(java.util.Locale.ENGLISH, "%,d", i.coins()),
                "gems", String.format(java.util.Locale.ENGLISH, "%,d", i.gems()));
        return true;
    }
}
