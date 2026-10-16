package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /spawn [player] and /setspawn. */
public final class SpawnCommand extends BaseCommand {

    public SpawnCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        if (label.equals("setspawn")) {
            Player player = player(sender);
            permission(sender, Perms.SPAWN_SET);
            Location loc = player.getLocation();
            plugin.spawn().setSpawn(loc);
            plugin.messages().send(sender, "spawn.set", "location", LocationCodec.describe(loc));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("place")) {
            Player player = player(sender);
            permission(sender, Perms.SPAWN_SET);
            plugin.builds().requestSpawn(player, args.length > 1 && args[1].equalsIgnoreCase("confirm"));
            return;
        }
        if (args.length > 0 && sender.hasPermission(Perms.ADMIN)) {
            Player target = online(sender, args[0]);
            Location spawn = plugin.spawn().spawn();
            if (spawn == null) {
                throw new CommandFail("spawn.not-set");
            }
            online.mythoscraft.core.cosmetic.CompanionService.unmount(target);
            target.teleportAsync(spawn);
            plugin.messages().send(sender, "spawn.sent-other", "player", target.getName());
            return;
        }
        Player player = player(sender);
        permission(sender, Perms.SPAWN);
        plugin.spawn().teleport(player);
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (!label.equals("spawn") || args.length != 1) {
            return List.of();
        }
        List<String> options = new java.util.ArrayList<>();
        if (sender.hasPermission(Perms.SPAWN_SET)) {
            options.add("place");
        }
        if (sender.hasPermission(Perms.ADMIN)) {
            options.addAll(onlineNames(sender));
        }
        return options;
    }
}
