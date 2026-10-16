package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.crate.CrateType;
import online.mythoscraft.core.crate.PlacedCrate;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /mythos crate place|remove|list|key|keyall|types */
public final class CrateSub implements SubCommand {

    private final MythosCraft plugin;

    public CrateSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "crate";
    }

    @Override
    public String permission() {
        return Perms.CRATE_ADMIN;
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        String action = args.length == 0 ? "help" : args[0].toLowerCase();
        switch (action) {
            case "place" -> {
                Player p = player(sender);
                CrateType type = type(args, 1);
                Block target = p.getTargetBlockExact(6);
                if (target == null) {
                    throw new CommandFail("crates.look-at-block");
                }
                Block above = target.getRelative(0, 1, 0);
                if (!above.isPassable()) {
                    throw new CommandFail("crates.no-space");
                }
                if (plugin.crates().nearest(above.getLocation().add(0.5, 0.5, 0.5), 0.6) != null) {
                    throw new CommandFail("crates.occupied");
                }
                float yaw = Math.round((p.getLocation().getYaw() + 180) / 90f) * 90f;
                PlacedCrate crate = plugin.crates().place(type, above.getLocation(), yaw);
                plugin.messages().send(sender, "crates.placed", "crate", Text.parse(type.name()), "id", crate.id(),
                        "location", LocationCodec.describe(above.getLocation()));
            }
            case "remove" -> {
                Player p = player(sender);
                PlacedCrate crate = plugin.crates().nearest(p.getLocation(), 4);
                if (crate == null) {
                    throw new CommandFail("crates.none-near");
                }
                plugin.crates().remove(crate);
                plugin.messages().send(sender, "crates.removed", "id", crate.id());
            }
            case "list" -> {
                plugin.messages().send(sender, "crates.list-header", "count", plugin.crates().placed().size());
                for (PlacedCrate c : plugin.crates().placed()) {
                    Location l = c.location();
                    plugin.messages().sendRaw(sender, "crates.list-line", "id", c.id(), "crate", plugin.crates().displayName(c.type()),
                            "location", LocationCodec.describe(l));
                }
            }
            case "types" -> {
                plugin.messages().send(sender, "crates.types-header");
                for (CrateType t : plugin.crates().types()) {
                    plugin.messages().sendRaw(sender, "crates.types-line", "id", t.id(), "crate", Text.parse(t.name()),
                            "rewards", t.rewards().size());
                }
            }
            case "key" -> {
                // key <player> <crate> [amount]
                if (args.length < 3) {
                    throw CommandFail.usage("/mythos crate key <Spieler> <Kiste> [Anzahl]");
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    throw new CommandFail("general.player-not-found", "player", args[1]);
                }
                CrateType type = type(args, 2);
                int amount = amount(args, 3);
                give(target, type, amount);
                plugin.messages().send(sender, "crates.key-given", "player", target.getName(), "amount", amount,
                        "key", Text.parse(type.keyName()));
            }
            case "keyall" -> {
                CrateType type = type(args, 1);
                int amount = amount(args, 2);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    give(p, type, amount);
                }
                plugin.messages().broadcast("crates.keyall", "amount", amount, "key", Text.parse(type.keyName()),
                        "player", sender.getName());
            }
            default -> plugin.messages().sendRaw(sender, "help.crate");
        }
    }

    private void give(Player target, CrateType type, int amount) {
        ItemStack key = plugin.crates().createKey(type.id(), amount);
        CustomItemManager.give(target, key);
        plugin.messages().send(target, "crates.key-received", "amount", amount, "key", Text.parse(type.keyName()));
    }

    private CrateType type(String[] args, int index) throws CommandFail {
        if (args.length <= index) {
            throw CommandFail.usage("/mythos crate " + args[0] + " <Kiste>");
        }
        CrateType type = plugin.crates().type(args[index]);
        if (type == null) {
            List<String> ids = new ArrayList<>();
            plugin.crates().types().forEach(t -> ids.add(t.id()));
            throw new CommandFail("crates.unknown", "crate", args[index], "crates", String.join(", ", ids));
        }
        return type;
    }

    private static int amount(String[] args, int index) throws CommandFail {
        if (args.length <= index) {
            return 1;
        }
        Integer n = Numbers.parseInt(args[index]);
        if (n == null || n < 1 || n > 64) {
            throw new CommandFail("general.not-a-number", "value", args[index]);
        }
        return n;
    }

    private static Player player(CommandSender sender) throws CommandFail {
        if (sender instanceof Player p) {
            return p;
        }
        throw new CommandFail("general.players-only");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> types = new ArrayList<>();
        plugin.crates().types().forEach(t -> types.add(t.id()));
        if (args.length == 1) {
            return List.of("place", "remove", "list", "types", "key", "keyall");
        }
        String action = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (action) {
                case "place", "keyall" -> types;
                case "key" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
        }
        if (args.length == 3 && action.equals("key")) {
            return types;
        }
        if ((args.length == 4 && action.equals("key")) || (args.length == 3 && action.equals("keyall"))) {
            return List.of("1", "3", "5", "10");
        }
        return List.of();
    }
}
