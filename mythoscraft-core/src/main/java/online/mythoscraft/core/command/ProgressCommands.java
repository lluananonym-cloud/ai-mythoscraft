package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.progress.ProgressMenus;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /jobs (/berufe), /erfolge (/achievements) and /parkour. */
public final class ProgressCommands extends BaseCommand {

    public ProgressCommands(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String rawLabel, String[] args) throws CommandFail {
        Player p = player(sender);
        String label = rawLabel.substring(rawLabel.indexOf(':') + 1);
        if (!plugin.progress().loaded(p)) {
            throw new CommandFail("general.data-loading");
        }
        switch (label) {
            case "jobs", "berufe", "job", "beruf" -> new ProgressMenus.JobsMenu(plugin, p).open(p);
            case "parkour" -> plugin.parkour().info(p);
            default -> new ProgressMenus.AchievementsMenu(plugin, p, 0).open(p);
        }
    }
}
