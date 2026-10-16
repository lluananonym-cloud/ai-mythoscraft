package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.protection.Region;
import online.mythoscraft.core.protection.RegionManager;
import online.mythoscraft.core.util.Cuboid;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** /mythos region pos1|pos2|wand|create|delete|info|list|set|priority|redefine|flags|reload */
public final class RegionSub implements SubCommand {

    private static final List<String> ACTIONS = List.of("pos1", "pos2", "wand", "create", "delete", "info", "list", "set",
            "priority", "redefine", "flags", "reload");

    private final MythosCraft plugin;

    public RegionSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "region";
    }

    @Override
    public String permission() {
        return Perms.REGION;
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        String action = args.length == 0 ? "help" : args[0].toLowerCase();
        RegionManager regions = plugin.regions();
        switch (action) {
            case "pos1", "pos2" -> {
                Player p = player(sender);
                int index = action.equals("pos1") ? 0 : 1;
                plugin.selections().set(p, index, p.getLocation());
                plugin.messages().send(sender, "region." + action, "x", p.getLocation().getBlockX(),
                        "y", p.getLocation().getBlockY(), "z", p.getLocation().getBlockZ());
            }
            case "wand" -> {
                CustomItemManager.give(player(sender), plugin.items().create(CustomItem.REGION_WAND));
                plugin.messages().send(sender, "region.wand");
            }
            case "create" -> {
                Player p = player(sender);
                if (args.length < 2) {
                    throw CommandFail.usage("/mythos region create <Name> [Priorität]");
                }
                String name = args[1].toLowerCase();
                if (!RegionManager.NAME.matcher(name).matches()) {
                    throw new CommandFail("region.invalid-name");
                }
                if (regions.get(name) != null) {
                    throw new CommandFail("region.exists", "region", name);
                }
                Cuboid c = selection(p);
                int priority = args.length > 2 ? integer(args[2]) : 0;
                Region r = regions.create(name, plugin.selections().world(p).getName(), c, priority);
                plugin.messages().send(sender, "region.created", "region", r.name(), "size", c.sizeX() + "×" + c.sizeY() + "×" + c.sizeZ(),
                        "volume", c.volume());
            }
            case "delete" -> {
                Region r = region(args, 1);
                if (r.name().equals(plugin.spawn().regionName())) {
                    plugin.messages().send(sender, "region.spawn-delete-warning");
                }
                regions.delete(r.name());
                plugin.messages().send(sender, "region.deleted", "region", r.name());
            }
            case "info" -> info(sender, args.length > 1 ? region(args, 1) : here(sender));
            case "list" -> {
                plugin.messages().send(sender, "region.list-header", "count", regions.regions().size(),
                        "arenas", regions.externalCount());
                for (Region r : regions.regions()) {
                    plugin.messages().sendRaw(sender, "region.list-line", "region", r.name(), "world", r.world(),
                            "priority", r.priority(), "volume", r.bounds().volume());
                }
            }
            case "set" -> {
                // /mythos region set <flag> <value>  (region you stand in)  or  set <region> <flag> <value>
                Region r;
                String flagName;
                String value;
                if (args.length >= 4) {
                    r = region(args, 1);
                    flagName = args[2];
                    value = args[3];
                } else if (args.length == 3) {
                    r = here(sender);
                    flagName = args[1];
                    value = args[2];
                } else {
                    throw CommandFail.usage("/mythos region set [Region] <Flag> <true|false|none>");
                }
                Flag flag = Flag.byKey(flagName);
                if (flag == null) {
                    throw new CommandFail("region.unknown-flag", "flags", flagNames());
                }
                Boolean v = switch (value.toLowerCase()) {
                    case "true", "allow", "an", "ja", "on" -> Boolean.TRUE;
                    case "false", "deny", "aus", "nein", "off" -> Boolean.FALSE;
                    case "none", "unset", "-" -> null;
                    default -> throw new CommandFail("region.invalid-value");
                };
                r.flag(flag, v);
                if (r.name().equals(plugin.spawn().regionName())) {
                    plugin.spawn().syncFlag(flag, v);
                }
                regions.save();
                plugin.messages().send(sender, "region.flag-set", "region", r.name(), "flag", flag.key(),
                        "value", v == null ? "none" : String.valueOf(v));
            }
            case "priority" -> {
                Region r = region(args, 1);
                if (args.length < 3) {
                    throw CommandFail.usage("/mythos region priority <Region> <Zahl>");
                }
                r.priority(integer(args[2]));
                regions.rebuild();
                regions.save();
                plugin.messages().send(sender, "region.priority-set", "region", r.name(), "priority", r.priority());
            }
            case "redefine" -> {
                Player p = player(sender);
                Region r = region(args, 1);
                Cuboid c = selection(p);
                r.redefine(plugin.selections().world(p).getName(), c);
                regions.rebuild();
                regions.save();
                plugin.messages().send(sender, "region.redefined", "region", r.name(), "volume", c.volume());
            }
            case "flags" -> plugin.messages().send(sender, "region.flags", "flags", flagNames());
            case "reload" -> {
                plugin.regions().reload(plugin.rootConfig());
                plugin.arenas().publishRegions();
                plugin.spawn().reload(plugin.rootConfig());
                plugin.messages().send(sender, "region.reloaded", "count", regions.regions().size());
            }
            default -> plugin.messages().sendRaw(sender, "help.region");
        }
    }

    private void info(CommandSender sender, Region r) {
        StringBuilder flags = new StringBuilder();
        for (Map.Entry<Flag, Boolean> e : r.flags().entrySet()) {
            if (!flags.isEmpty()) {
                flags.append(", ");
            }
            flags.append(e.getKey().key()).append('=').append(e.getValue());
        }
        plugin.messages().send(sender, "region.info", "region", r.name(), "world", r.world(), "bounds", r.bounds().toString(),
                "priority", r.priority(), "volume", r.bounds().volume(), "flags", flags.isEmpty() ? "-" : flags.toString(),
                "type", r.type().name().toLowerCase());
    }

    private Region here(CommandSender sender) throws CommandFail {
        Player p = player(sender);
        Region r = plugin.regions().top(p.getLocation());
        if (r == null) {
            throw new CommandFail("region.none-here");
        }
        if (r.type() == Region.Type.ARENA) {
            throw new CommandFail("region.is-arena", "region", r.name());
        }
        return r;
    }

    private Region region(String[] args, int index) throws CommandFail {
        if (args.length <= index) {
            throw CommandFail.usage("/mythos region " + args[0] + " <Region>");
        }
        Region r = plugin.regions().get(args[index]);
        if (r == null) {
            throw new CommandFail("region.unknown", "region", args[index]);
        }
        return r;
    }

    private Cuboid selection(Player p) throws CommandFail {
        Cuboid c = plugin.selections().cuboid(p);
        World w = plugin.selections().world(p);
        if (c == null || w == null) {
            throw new CommandFail("region.no-selection");
        }
        return c;
    }

    private static Player player(CommandSender sender) throws CommandFail {
        if (sender instanceof Player p) {
            return p;
        }
        throw new CommandFail("general.players-only");
    }

    private static int integer(String s) throws CommandFail {
        Integer i = Numbers.parseInt(s);
        if (i == null) {
            throw new CommandFail("general.not-a-number", "value", s);
        }
        return i;
    }

    private static String flagNames() {
        return String.join(", ", Arrays.stream(Flag.values()).map(Flag::key).toList());
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        String action = args[0].toLowerCase();
        List<String> names = new ArrayList<>();
        plugin.regions().regions().forEach(r -> names.add(r.name()));
        List<String> flags = Arrays.stream(Flag.values()).map(Flag::key).toList();
        if (args.length == 2) {
            return switch (action) {
                case "delete", "info", "priority", "redefine" -> names;
                case "set" -> {
                    List<String> both = new ArrayList<>(names);
                    both.addAll(flags);
                    yield both;
                }
                default -> List.of();
            };
        }
        if (action.equals("set")) {
            boolean firstIsFlag = Flag.byKey(args[1]) != null && plugin.regions().get(args[1]) == null;
            if (args.length == 3) {
                return firstIsFlag ? List.of("true", "false", "none") : flags;
            }
            if (args.length == 4 && !firstIsFlag) {
                return List.of("true", "false", "none");
            }
        }
        return List.of();
    }
}
