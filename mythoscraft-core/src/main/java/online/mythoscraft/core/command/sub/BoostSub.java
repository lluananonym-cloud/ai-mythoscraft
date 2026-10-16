package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.economy.MultiplierService;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** /mythos boost &lt;coins|gems|events&gt; &lt;factor&gt; &lt;duration&gt; | /mythos boost stop &lt;type&gt; | /mythos boost info */
public final class BoostSub implements SubCommand {

    private final MythosCraft plugin;

    public BoostSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "boost";
    }

    @Override
    public String permission() {
        return Perms.BOOST;
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        MultiplierService m = plugin.multipliers();
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            plugin.messages().send(sender, "booster.info-header", "day", m.today().getDayOfWeek().toString().toLowerCase());
            for (MultiplierType type : MultiplierType.values()) {
                MultiplierService.Booster b = m.booster(type);
                plugin.messages().sendRaw(sender, "booster.info-line", "type", plugin.messages().get("multiplier." + type.id()),
                        "daily", m.daily(type), "booster", b == null ? "-" : "x" + Numbers.formatDecimal(b.factor())
                                + " (" + Durations.format(Duration.ofMillis(b.until() - System.currentTimeMillis())) + ")",
                        "total", m.global(type));
            }
            return;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            MultiplierType type = args.length > 1 ? MultiplierType.byId(args[1]) : null;
            if (type == null) {
                throw CommandFail.usage("/mythos boost stop <coins|gems|events>");
            }
            if (!m.stopBooster(type)) {
                throw new CommandFail("booster.none");
            }
            plugin.messages().send(sender, "booster.stopped", "type", plugin.messages().get("multiplier." + type.id()));
            return;
        }
        if (args.length < 3) {
            throw CommandFail.usage("/mythos boost <coins|gems|events> <Faktor> <Dauer>");
        }
        MultiplierType type = MultiplierType.byId(args[0]);
        Double factor = Numbers.parseDouble(args[1]);
        Optional<Duration> duration = Durations.parse(args[2]);
        if (type == null || factor == null || factor <= 0 || factor > 20 || duration.isEmpty()) {
            throw CommandFail.usage("/mythos boost <coins|gems|events> <Faktor 0.1-20> <Dauer z.B. 1h>");
        }
        m.startBooster(type, factor, duration.get(), sender.getName());
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 1 -> List.of("coins", "gems", "events", "stop", "info");
            case 2 -> args[0].equalsIgnoreCase("stop") ? List.of("coins", "gems", "events") : List.of("1.5", "2", "3");
            case 3 -> List.of("30m", "1h", "2h", "1d");
            default -> List.of();
        };
    }
}
