package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SocialCommands;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.protection.RegionManager;
import online.mythoscraft.core.stats.ButtonManager;
import online.mythoscraft.core.stats.HologramManager;
import java.util.Arrays;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /mythos holo create &lt;name&gt; &lt;stat&gt; | delete &lt;name&gt; | list – floating leaderboards. */
public final class HologramSub implements SubCommand {

    private final MythosCraft plugin;

    public HologramSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "holo";
    }

    @Override
    public String permission() {
        return Perms.HOLOGRAM;
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        String action = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (action) {
            case "create" -> {
                if (!(sender instanceof Player p)) {
                    throw new CommandFail("general.players-only");
                }
                if (args.length < 3) {
                    throw CommandFail.usage("/mythos holo create <Name> <Statistik>");
                }
                if (!RegionManager.NAME.matcher(args[1].toLowerCase()).matches()) {
                    throw new CommandFail("region.invalid-name");
                }
                Stat stat = Stat.byId(args[2]);
                if (stat == null) {
                    throw new CommandFail("stats.unknown", "stats", String.join(", ", SocialCommands.statIds()));
                }
                plugin.holograms().create(args[1], stat, p.getLocation().add(0, 2.2, 0), 10);
                plugin.messages().send(sender, "holograms.created", "name", args[1].toLowerCase());
            }
            case "button" -> {
                if (!(sender instanceof Player p)) {
                    throw new CommandFail("general.players-only");
                }
                if (args.length < 3) {
                    throw CommandFail.usage("/mythos holo button <Name> <Befehl ...>");
                }
                if (!RegionManager.NAME.matcher(args[1].toLowerCase()).matches()) {
                    throw new CommandFail("region.invalid-name");
                }
                String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.buttons().create(args[1], p.getLocation().add(0, 1.8, 0), command, null);
                plugin.messages().send(sender, "buttons.created", "name", args[1].toLowerCase(), "command", "/" + command.replaceFirst("^/", ""));
            }
            case "text" -> {
                if (args.length < 3) {
                    throw CommandFail.usage("/mythos holo text <Button> <Text>");
                }
                if (!plugin.buttons().setText(args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)))) {
                    throw new CommandFail("holograms.unknown", "name", args[1]);
                }
                plugin.messages().send(sender, "buttons.text-set", "name", args[1].toLowerCase());
            }
            case "delete" -> {
                if (args.length < 2) {
                    throw CommandFail.usage("/mythos holo delete <Name>");
                }
                if (plugin.buttons().delete(args[1])) {
                    plugin.messages().send(sender, "holograms.deleted", "name", args[1].toLowerCase());
                    return;
                }
                if (!plugin.holograms().delete(args[1])) {
                    throw new CommandFail("holograms.unknown", "name", args[1]);
                }
                plugin.messages().send(sender, "holograms.deleted", "name", args[1].toLowerCase());
            }
            default -> {
                plugin.messages().send(sender, "holograms.list-header", "count", plugin.holograms().all().size());
                for (HologramManager.Hologram h : plugin.holograms().all()) {
                    plugin.messages().sendRaw(sender, "holograms.list-line", "name", h.name(), "stat", h.stat().id(),
                            "location", LocationCodec.describe(h.location()));
                }
                for (ButtonManager.Button b : plugin.buttons().all()) {
                    plugin.messages().sendRaw(sender, "buttons.list-line", "name", b.name(), "command", "/" + b.command(),
                            "location", LocationCodec.describe(b.location()));
                }
            }
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("create", "button", "text", "delete", "list");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("text"))) {
            List<String> names = new ArrayList<>();
            plugin.buttons().all().forEach(b -> names.add(b.name()));
            if (args[0].equalsIgnoreCase("delete")) {
                plugin.holograms().all().forEach(h -> names.add(h.name()));
            }
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return SocialCommands.statIds();
        }
        return List.of();
    }
}
