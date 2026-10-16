package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Arena;
import online.mythoscraft.core.core.Perms;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Player side of arenas: /arena join|spectate &lt;name&gt;, /arena leave, /arena list. */
public final class ArenaCommand extends BaseCommand {

    public ArenaCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (sub) {
            case "list" -> {
                plugin.messages().send(sender, "arena.list-header");
                for (Arena a : plugin.arenas().arenas()) {
                    plugin.messages().sendRaw(sender, "arena.list-line", "arena", a.name(),
                            "state", plugin.messages().get("arena.states." + a.state().name().toLowerCase()),
                            "players", plugin.arenas().players(a), "max", a.maxPlayers());
                }
            }
            case "join", "spectate" -> {
                Player player = player(sender);
                permission(sender, Perms.ARENA_JOIN);
                if (args.length < 2) {
                    throw CommandFail.usage("/arena " + sub + " <Arena>");
                }
                Arena arena = plugin.arenas().get(args[1]);
                if (arena == null) {
                    throw new CommandFail("arena.unknown", "arena", args[1]);
                }
                plugin.arenas().join(player, arena, sub.equals("spectate"));
            }
            case "leave" -> plugin.arenas().leave(player(sender), true, true);
            default -> throw CommandFail.usage("/arena <join|spectate|leave|list>");
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return List.of("join", "spectate", "leave", "list");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("spectate"))) {
            return plugin.arenas().arenas().stream().map(Arena::name).toList();
        }
        return List.of();
    }
}
