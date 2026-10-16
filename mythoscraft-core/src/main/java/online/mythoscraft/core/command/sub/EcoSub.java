package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

/** /mythos eco &lt;give|take|set&gt; &lt;player&gt; &lt;coins|gems&gt; &lt;amount&gt; – works for offline players too. */
public final class EcoSub implements SubCommand {

    private final MythosCraft plugin;

    public EcoSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "eco";
    }

    @Override
    public String permission() {
        return Perms.ECONOMY_ADMIN;
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        if (args.length < 4) {
            throw CommandFail.usage("/mythos eco <give|take|set> <Spieler> <coins|gems> <Betrag>");
        }
        EconomyManager.AdminOp op;
        try {
            op = EconomyManager.AdminOp.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw CommandFail.usage("/mythos eco <give|take|set> <Spieler> <coins|gems> <Betrag>");
        }
        Currency currency = Currency.byId(args[2]);
        if (currency == null) {
            throw CommandFail.usage("/mythos eco <give|take|set> <Spieler> <coins|gems> <Betrag>");
        }
        OptionalLong amount = args[3].equals("0") && op == EconomyManager.AdminOp.SET ? OptionalLong.of(0)
                : Numbers.parseAmount(args[3], plugin.economy().maxBalance());
        if (amount.isEmpty()) {
            throw new CommandFail("economy.invalid-amount", "max", plugin.economy().maxBalance());
        }
        String name = args[1];
        UUID actor = sender instanceof Player p ? p.getUniqueId() : null;
        plugin.playerData().lookup(name).whenComplete((snap, ex) -> plugin.tasks().sync(() -> {
            if (ex != null || snap.isEmpty()) {
                plugin.messages().send(sender, "general.player-unknown", "player", name);
                return;
            }
            long[] result = new long[1];
            plugin.economy().adminChange(snap.get().uuid(), currency, op, amount.getAsLong(), actor, result)
                    .whenComplete((ok, ex2) -> plugin.tasks().sync(() -> {
                        if (ex2 != null || !Boolean.TRUE.equals(ok)) {
                            plugin.messages().send(sender, "general.error");
                            return;
                        }
                        plugin.messages().send(sender, "economy.admin-done", "player", snap.get().name(),
                                "currency", plugin.messages().get("economy.names." + currency.id()), "balance", result[0],
                                "action", op.name().toLowerCase(Locale.ROOT), "amount", amount.getAsLong());
                        plugin.getLogger().info("[Economy] " + sender.getName() + " " + op + " " + amount.getAsLong() + " "
                                + currency.id() + " " + snap.get().name() + " -> " + result[0]);
                        Player target = Bukkit.getPlayer(snap.get().uuid());
                        if (target != null && !target.equals(sender)) {
                            plugin.messages().send(target, "economy.admin-notify", "currency",
                                    plugin.messages().get("economy.names." + currency.id()), "balance", result[0]);
                        }
                    }));
        }));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 1 -> List.of("give", "take", "set");
            case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case 3 -> List.of("coins", "gems");
            case 4 -> List.of("100", "1000", "10k");
            default -> List.of();
        };
    }
}
