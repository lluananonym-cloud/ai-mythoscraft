package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.sub.ArenaSub;
import online.mythoscraft.core.command.sub.BoostSub;
import online.mythoscraft.core.command.sub.CrateSub;
import online.mythoscraft.core.command.sub.EcoSub;
import online.mythoscraft.core.command.sub.EventSub;
import online.mythoscraft.core.command.sub.HologramSub;
import online.mythoscraft.core.command.sub.ItemSub;
import online.mythoscraft.core.command.sub.RegionSub;
import online.mythoscraft.core.command.sub.SystemSubs;
import online.mythoscraft.core.command.sub.TournamentSub;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /mythos – dispatches to the sub commands; help and tab completion only show what the sender may use. */
public final class MythosCommand extends BaseCommand {

    private final Map<String, SubCommand> subs = new LinkedHashMap<>();

    public MythosCommand(MythosCraft plugin) {
        super(plugin);
        SystemSubs system = new SystemSubs(plugin, this);
        register(system.help());
        register(system.status());
        register(system.reload());
        register(system.bypass());
        register(system.top());
        register(new RegionSub(plugin));
        register(new ArenaSub(plugin));
        register(new EventSub(plugin));
        register(new EcoSub(plugin));
        register(new CrateSub(plugin));
        register(new ItemSub(plugin));
        register(new BoostSub(plugin));
        register(new HologramSub(plugin));
        register(new TournamentSub(plugin));
    }

    private void register(SubCommand sub) {
        subs.put(sub.name(), sub);
    }

    public List<SubCommand> visible(CommandSender sender) {
        List<SubCommand> list = new ArrayList<>();
        for (SubCommand s : subs.values()) {
            if (s.allowed(sender)) {
                list.add(s);
            }
        }
        return list;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        String name = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = subs.get(name);
        if (sub == null) {
            throw new CommandFail("general.unknown-subcommand", "command", "/mythos help");
        }
        if (!sub.allowed(sender)) {
            throw new CommandFail("general.no-permission");
        }
        sub.execute(sender, args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length <= 1) {
            return visible(sender).stream().map(SubCommand::name).toList();
        }
        SubCommand sub = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sub.allowed(sender)) {
            return List.of();
        }
        return sub.complete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
