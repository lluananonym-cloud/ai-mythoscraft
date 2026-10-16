package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /rtp [Welt] – random teleport into the wild (aliases /wild, /randomtp, /zufall). */
public final class RtpCommand extends BaseCommand {

    public RtpCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player player = player(sender);
        permission(sender, "mythos.rtp");
        plugin.rtp().start(player, args.length > 0 ? args[0] : null, false, null);
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 ? plugin.rtp().worldNames() : List.of();
    }
}
