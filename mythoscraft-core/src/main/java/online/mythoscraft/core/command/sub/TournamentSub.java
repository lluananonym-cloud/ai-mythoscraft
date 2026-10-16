package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Arena;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.tournament.TournamentManager;
import online.mythoscraft.core.tournament.TournamentTier;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /mythos tournament start|test|stop|setarena|info */
public final class TournamentSub implements SubCommand {

    private final MythosCraft plugin;

    public TournamentSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "tournament";
    }

    @Override
    public String permission() {
        return "mythos.tournament.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        String action = args.length == 0 ? "info" : args[0].toLowerCase();
        TournamentManager t = plugin.tournaments();
        switch (action) {
            case "start", "test" -> {
                TournamentTier tier = tier(args);
                boolean test = action.equals("test");
                int seconds = test ? 20 : 60;
                int bots = 0;
                if (test) {
                    Integer n = args.length > 2 ? Numbers.parseInt(args[2]) : null;
                    bots = n == null ? tier.defaultSlots() - 1 : Math.max(1, n);
                } else if (args.length > 2) {
                    Integer n = Numbers.parseInt(args[2]);
                    if (n != null) {
                        seconds = Math.max(10, n);
                    }
                }
                TournamentManager.OpenResult r = t.open(tier, test, seconds, bots);
                switch (r) {
                    case OPENED -> plugin.messages().send(sender, test ? "tournament.admin-test" : "tournament.admin-opened",
                            "tier", t.tierName(tier), "bots", bots, "seconds", seconds);
                    case ALREADY_RUNNING -> throw new CommandFail("tournament.already-running");
                    case NO_KITS -> throw new CommandFail("tournament.no-kits");
                    default -> throw new CommandFail("tournament.arena-problem", "problem", t.arenaProblem(tier));
                }
            }
            case "stop" -> {
                if (!t.stop()) {
                    throw new CommandFail("tournament.none-running");
                }
            }
            case "setarena" -> {
                TournamentTier tier = tier(args);
                if (args.length < 3) {
                    throw CommandFail.usage("/mythos tournament setarena <daily|weekly|monthly> <Arena>");
                }
                Arena arena = plugin.arenas().get(args[2]);
                if (arena == null) {
                    throw new CommandFail("arena.unknown", "arena", args[2]);
                }
                t.setArena(tier, arena.name());
                String problem = t.arenaProblem(tier);
                plugin.messages().send(sender, problem == null ? "tournament.arena-set" : "tournament.arena-set-warning",
                        "tier", t.tierName(tier), "arena", arena.name(), "problem", problem == null ? "" : problem);
            }
            default -> t.info(sender);
        }
    }

    private static TournamentTier tier(String[] args) throws CommandFail {
        TournamentTier tier = args.length > 1 ? TournamentTier.byId(args[1]) : null;
        if (tier == null) {
            throw CommandFail.usage("/mythos tournament " + args[0] + " <daily|weekly|monthly>");
        }
        return tier;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("start", "test", "stop", "setarena", "info");
        }
        String a = args[0].toLowerCase();
        if (args.length == 2 && (a.equals("start") || a.equals("test") || a.equals("setarena"))) {
            return Arrays.stream(TournamentTier.values()).map(TournamentTier::id).toList();
        }
        if (args.length == 3 && a.equals("setarena")) {
            List<String> names = new ArrayList<>();
            plugin.arenas().arenas().forEach(ar -> names.add(ar.name()));
            return names;
        }
        if (args.length == 3 && a.equals("test")) {
            return List.of("4", "10", "19", "99");
        }
        return List.of();
    }
}
