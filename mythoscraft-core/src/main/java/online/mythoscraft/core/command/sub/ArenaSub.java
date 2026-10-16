package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Arena;
import online.mythoscraft.core.arena.ArenaManager;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.protection.RegionManager;
import online.mythoscraft.core.tournament.TournamentTier;
import online.mythoscraft.core.util.Cuboid;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /mythos arena create|delete|info|list|pos1|pos2|setspawn|setspectator|enable|disable|set|snapshot|reset.
 * Commands without an arena name use the arena the admin is standing in.
 */
public final class ArenaSub implements SubCommand {

    private static final List<String> ACTIONS = List.of("place", "create", "delete", "info", "list", "pos1", "pos2", "setspawn",
            "clearspawns", "setspectator", "enable", "disable", "set", "redefine", "snapshot", "reset");

    private final MythosCraft plugin;

    public ArenaSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "arena";
    }

    @Override
    public String permission() {
        return Perms.ARENA;
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        String action = args.length == 0 ? "help" : args[0].toLowerCase();
        ArenaManager arenas = plugin.arenas();
        // "/mythos arena daily place [name] [confirm]" equals "/mythos arena place daily [name] [confirm]"
        if (args.length >= 2 && TournamentTier.byId(action) != null && args[1].equalsIgnoreCase("place")) {
            String[] swapped = args.clone();
            swapped[0] = "place";
            swapped[1] = action;
            args = swapped;
            action = "place";
        }
        switch (action) {
            case "place" -> {
                Player p = player(sender);
                TournamentTier tier = args.length > 1 ? TournamentTier.byId(args[1]) : null;
                if (tier == null) {
                    throw CommandFail.usage("/mythos arena place <daily|weekly|monthly> [Name]");
                }
                boolean confirm = args[args.length - 1].equalsIgnoreCase("confirm");
                String name = args.length > 2 && !args[2].equalsIgnoreCase("confirm") ? args[2] : tier.id();
                if (!RegionManager.NAME.matcher(name.toLowerCase()).matches()) {
                    throw new CommandFail("region.invalid-name");
                }
                plugin.builds().requestArena(p, tier, name, confirm);
            }
            case "pos1", "pos2" -> {
                Player p = player(sender);
                plugin.selections().set(p, action.equals("pos1") ? 0 : 1, p.getLocation());
                plugin.messages().send(sender, "region." + action, "x", p.getLocation().getBlockX(),
                        "y", p.getLocation().getBlockY(), "z", p.getLocation().getBlockZ());
            }
            case "create" -> {
                Player p = player(sender);
                if (args.length < 2) {
                    throw CommandFail.usage("/mythos arena create <Name>");
                }
                String name = args[1].toLowerCase();
                if (!RegionManager.NAME.matcher(name).matches()) {
                    throw new CommandFail("region.invalid-name");
                }
                if (arenas.get(name) != null) {
                    throw new CommandFail("arena.exists", "arena", name);
                }
                Cuboid c = plugin.selections().cuboid(p);
                if (c == null) {
                    throw new CommandFail("region.no-selection");
                }
                arenas.create(name, plugin.selections().world(p).getName(), c);
                plugin.messages().send(sender, "arena.created", "arena", name);
            }
            case "delete" -> {
                Arena a = arena(sender, args);
                arenas.delete(a);
                plugin.messages().send(sender, "arena.deleted", "arena", a.name());
            }
            case "info" -> {
                Arena a = arena(sender, args);
                StringBuilder settings = new StringBuilder();
                for (String key : Arena.settingKeys()) {
                    if (!settings.isEmpty()) {
                        settings.append(", ");
                    }
                    settings.append(key).append('=').append(a.setting(key));
                }
                plugin.messages().send(sender, "arena.info", "arena", a.name(), "world", a.world(), "bounds", a.bounds().toString(),
                        "state", plugin.messages().get("arena.states." + a.state().name().toLowerCase()),
                        "spawns", a.spawns().size(), "spectator", a.spectator() == null ? "-" : "✔",
                        "players", arenas.players(a), "snapshot", arenas.snapshots().exists(a) ? "✔" : "-",
                        "settings", settings.toString());
            }
            case "list" -> plugin.getServer().dispatchCommand(sender, "arena list");
            case "setspawn" -> {
                Player p = player(sender);
                Arena a = arena(sender, args);
                if (!a.contains(p.getLocation())) {
                    throw new CommandFail("arena.outside", "arena", a.name());
                }
                a.spawns().add(LocationCodec.toString(p.getLocation()));
                arenas.save();
                plugin.messages().send(sender, "arena.spawn-added", "arena", a.name(), "count", a.spawns().size());
            }
            case "clearspawns" -> {
                Arena a = arena(sender, args);
                a.spawns().clear();
                arenas.save();
                plugin.messages().send(sender, "arena.spawns-cleared", "arena", a.name());
            }
            case "setspectator" -> {
                Player p = player(sender);
                Arena a = arena(sender, args);
                a.spectator(LocationCodec.toString(p.getLocation()));
                arenas.save();
                plugin.messages().send(sender, "arena.spectator-set", "arena", a.name());
            }
            case "enable" -> {
                Arena a = arena(sender, args);
                if (a.spawns().isEmpty()) {
                    throw new CommandFail("arena.no-spawns", "arena", a.name());
                }
                arenas.enable(a);
                plugin.messages().send(sender, "arena.enabled", "arena", a.name());
            }
            case "disable" -> {
                Arena a = arena(sender, args);
                arenas.disable(a);
                plugin.messages().send(sender, "arena.disabled-admin", "arena", a.name());
            }
            case "set" -> {
                // set <arena> <key> <value>
                if (args.length < 4) {
                    throw CommandFail.usage("/mythos arena set <Arena> <" + String.join("|", Arena.settingKeys()) + "> <Wert>");
                }
                Arena a = arenas.get(args[1]);
                if (a == null) {
                    throw new CommandFail("arena.unknown", "arena", args[1]);
                }
                try {
                    if (!a.set(args[2], args[3])) {
                        throw new CommandFail("arena.unknown-setting", "settings", String.join(", ", Arena.settingKeys()));
                    }
                } catch (IllegalArgumentException e) {
                    throw new CommandFail("arena.invalid-value", "setting", args[2]);
                }
                if (args[2].equalsIgnoreCase("kit") && !a.kit().isEmpty() && arenas.kit(a.kit()) == null) {
                    plugin.messages().send(sender, "arena.unknown-kit", "kit", a.kit(), "kits", String.join(", ", arenas.kitIds()));
                }
                arenas.publishRegions();
                arenas.save();
                plugin.messages().send(sender, "arena.setting-set", "arena", a.name(), "setting", args[2], "value", a.setting(args[2].toLowerCase()));
            }
            case "redefine" -> {
                Player p = player(sender);
                Arena a = arena(sender, args);
                Cuboid c = plugin.selections().cuboid(p);
                if (c == null) {
                    throw new CommandFail("region.no-selection");
                }
                a.redefine(plugin.selections().world(p).getName(), c);
                arenas.publishRegions();
                arenas.save();
                plugin.messages().send(sender, "arena.redefined", "arena", a.name());
            }
            case "snapshot" -> {
                Arena a = arena(sender, args);
                plugin.messages().send(sender, "arena.snapshot-started", "arena", a.name(), "volume", a.bounds().volume());
                long start = System.currentTimeMillis();
                arenas.snapshots().capture(a, error -> plugin.messages().send(sender, error == null ? "arena.snapshot-done" : "arena.snapshot-failed",
                        "arena", a.name(), "ms", System.currentTimeMillis() - start, "error", error == null ? "" : error));
            }
            case "reset" -> {
                Arena a = arena(sender, args);
                plugin.messages().send(sender, "arena.reset-started", "arena", a.name());
                arenas.reset(a, sender);
            }
            default -> plugin.messages().sendRaw(sender, "help.arena");
        }
    }

    /** Arena from args[1] or the one the player is standing in. */
    private Arena arena(CommandSender sender, String[] args) throws CommandFail {
        if (args.length > 1) {
            Arena a = plugin.arenas().get(args[1]);
            if (a == null) {
                throw new CommandFail("arena.unknown", "arena", args[1]);
            }
            return a;
        }
        if (sender instanceof Player p) {
            Arena a = plugin.arenas().arenaAt(p.getLocation());
            if (a != null) {
                return a;
            }
        }
        throw CommandFail.usage("/mythos arena " + args[0] + " <Arena>");
    }

    private static Player player(CommandSender sender) throws CommandFail {
        if (sender instanceof Player p) {
            return p;
        }
        throw new CommandFail("general.players-only");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        List<String> names = new ArrayList<>();
        plugin.arenas().arenas().forEach(a -> names.add(a.name()));
        String action = args[0].toLowerCase();
        if (action.equals("place")) {
            return args.length == 2 ? List.of("daily", "weekly", "monthly") : args.length == 4 ? List.of("confirm") : List.of();
        }
        if (args.length == 2 && TournamentTier.byId(action) != null) {
            return List.of("place");
        }
        if (args.length == 2 && !action.equals("create") && !action.equals("pos1") && !action.equals("pos2") && !action.equals("list")) {
            return names;
        }
        if (action.equals("set")) {
            if (args.length == 3) {
                return Arena.settingKeys();
            }
            if (args.length == 4) {
                return switch (args[2].toLowerCase()) {
                    case "inventory" -> List.of("separate", "keep");
                    case "kit" -> {
                        List<String> kits = new ArrayList<>(plugin.arenas().kitIds());
                        kits.add("none");
                        yield kits;
                    }
                    case "max-players", "priority" -> List.of("10", "20", "50", "100");
                    default -> List.of("true", "false");
                };
            }
        }
        return List.of();
    }
}
