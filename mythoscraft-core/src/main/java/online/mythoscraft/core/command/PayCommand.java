package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Cooldowns;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.OptionalLong;

/** /pay &lt;player&gt; &lt;amount&gt; [coins|gems] – e.g. /pay MinePro641 500 or /pay MinePro641 1,5k. */
public final class PayCommand extends BaseCommand {

    private final Cooldowns cooldowns;

    public PayCommand(MythosCraft plugin, Cooldowns cooldowns) {
        super(plugin);
        this.cooldowns = cooldowns;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player player = player(sender);
        permission(sender, Perms.PAY);
        boolean gemPay = label.equalsIgnoreCase("gempay") || label.equalsIgnoreCase("paygems");
        if (args.length < 2) {
            throw CommandFail.usage(gemPay ? "/gempay <Spieler> <Betrag>" : "/pay <Spieler> <Betrag> [coins|gems]");
        }
        Currency currency = gemPay ? Currency.GEMS : args.length > 2 ? Currency.byId(args[2]) : Currency.COINS;
        if (currency == null) {
            throw CommandFail.usage("/pay <Spieler> <Betrag> [coins|gems]");
        }
        EconomyManager eco = plugin.economy();
        // /gempay always sends gems; /pay … gems only if gems.transferable is on
        if (!gemPay && !eco.transferable(currency)) {
            throw new CommandFail("economy.not-transferable", "currency", plugin.messages().get("economy.names." + currency.id()));
        }
        Player target = online(sender, args[0]);
        if (target.equals(player)) {
            throw new CommandFail("economy.pay-self");
        }
        OptionalLong amount = Numbers.parseAmount(args[1], eco.maxPay());
        if (amount.isEmpty()) {
            throw new CommandFail("economy.invalid-amount", "max", eco.maxPay());
        }
        long left = cooldowns.remainingSeconds("pay", player.getUniqueId());
        if (left > 0) {
            throw new CommandFail("economy.pay-cooldown", "seconds", left);
        }
        PlayerData from = data(player);
        PlayerData to = plugin.playerData().get(target);
        if (to == null) {
            throw new CommandFail("general.data-loading");
        }
        long value = amount.getAsLong();
        String currencyName = currency.id();
        switch (eco.transfer(from, to, currency, value, "pay")) {
            case SUCCESS -> {
                cooldowns.set("pay", player.getUniqueId(), eco.payCooldownSeconds() * 1000L);
                plugin.messages().send(player, "economy.paid", "amount", value, "player", target.getName(),
                        "currency", plugin.messages().get("economy.names." + currencyName), "balance", currency.get(from));
                plugin.messages().send(target, "economy.received", "amount", value, "player", player.getName(),
                        "currency", plugin.messages().get("economy.names." + currencyName));
                SoundSpec.of("entity.experience_orb.pickup", 0.8f, 1.3f).play(target);
                plugin.getLogger().info("[Economy] " + player.getName() + " paid " + value + " " + currencyName + " to " + target.getName());
            }
            case INSUFFICIENT_FUNDS -> throw new CommandFail("economy.not-enough", "balance", currency.get(from),
                    "currency", plugin.messages().get("economy.names." + currencyName));
            case LIMIT_REACHED -> throw new CommandFail("economy.limit", "player", target.getName());
            case DISABLED -> throw new CommandFail("economy.disabled");
            default -> throw new CommandFail("economy.invalid-amount", "max", eco.maxPay());
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (label.equalsIgnoreCase("gempay") || label.equalsIgnoreCase("paygems")) {
            return switch (args.length) {
                case 1 -> onlineNames(sender).stream().filter(n -> !n.equals(sender.getName())).toList();
                case 2 -> List.of("1", "5", "10", "50");
                default -> List.of();
            };
        }
        return switch (args.length) {
            case 1 -> onlineNames(sender).stream().filter(n -> !n.equals(sender.getName())).toList();
            case 2 -> List.of("100", "500", "1k", "10k");
            case 3 -> plugin.economy().transferable(Currency.GEMS) ? List.of("coins", "gems") : List.of("coins");
            default -> List.of();
        };
    }
}
