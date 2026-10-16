package online.mythoscraft.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.player.Home;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.teleport.HomeManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /home [name], /sethome [name], /delhome &lt;name&gt;, /homes. */
public final class HomeCommand extends BaseCommand {

    public HomeCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player player = player(sender);
        permission(sender, Perms.HOME);
        PlayerData data = data(player);
        HomeManager homes = plugin.homes();
        switch (label) {
            case "sethome" -> homes.setHome(player, data, args.length > 0 ? args[0] : HomeManager.DEFAULT_NAME);
            case "delhome" -> {
                if (args.length == 0) {
                    throw CommandFail.usage("/delhome <Name>");
                }
                homes.deleteHome(player, data, args[0]);
            }
            case "homes" -> list(player, data);
            default -> homes.teleport(player, data, args.length > 0 ? args[0] : null);
        }
    }

    private void list(Player player, PlayerData data) {
        if (data.homes().isEmpty()) {
            plugin.messages().send(player, "homes.none");
            return;
        }
        int max = plugin.ranks().maxHomes(player);
        plugin.messages().send(player, "homes.list-header", "count", data.homes().size(),
                "max", max == Integer.MAX_VALUE ? "∞" : String.valueOf(max));
        Component line = Component.empty();
        boolean first = true;
        for (Home home : data.homes().values()) {
            if (!first) {
                line = line.append(plugin.messages().get("homes.list-separator"));
            }
            first = false;
            line = line.append(plugin.messages().get("homes.list-entry", "home", home.name())
                    .clickEvent(ClickEvent.runCommand("/home " + home.name()))
                    .hoverEvent(HoverEvent.showText(plugin.messages().get("homes.list-hover", "home", home.name(),
                            "world", home.world(), "x", (int) home.x(), "y", (int) home.y(), "z", (int) home.z()))));
        }
        player.sendMessage(plugin.messages().prefix().append(line));
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player) || label.equals("homes") || label.equals("sethome")) {
            return List.of();
        }
        PlayerData data = plugin.playerData().get(player);
        if (data == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        data.homes().values().forEach(h -> names.add(h.name()));
        return names;
    }
}
