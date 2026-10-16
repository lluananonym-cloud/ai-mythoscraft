package online.mythoscraft.core.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/** One branch of /mythos. Arguments exclude the sub command name itself. */
public interface SubCommand {

    String name();

    /** Null if everyone may use it. */
    String permission();

    /** Message key of the help line. */
    default String helpKey() {
        return "help.mythos." + name();
    }

    void execute(CommandSender sender, String[] args) throws CommandFail;

    default List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }

    default boolean allowed(CommandSender sender) {
        return permission() == null || sender.hasPermission(permission());
    }
}
