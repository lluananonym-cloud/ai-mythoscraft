package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.command.CommandSender;

import java.util.List;

/** /tournament join | leave | info (alias /turnier). */
public final class TournamentCommand extends BaseCommand {

    public TournamentCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "join", "beitreten" -> plugin.tournaments().join(player(sender));
            case "leave", "verlassen" -> plugin.tournaments().leave(player(sender));
            case "info" -> plugin.tournaments().info(sender);
            default -> throw CommandFail.usage("/tournament <join|leave|info>");
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 ? List.of("join", "leave", "info") : List.of();
    }
}
