package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.display.Glyphs;
import online.mythoscraft.core.player.PlayerData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /balance, /coins, /gems – optionally for another player (online or offline) with mythos.balance.others. */
public final class BalanceCommand extends BaseCommand {

    public BalanceCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        permission(sender, Perms.BALANCE);
        if (args.length > 0) {
            permission(sender, Perms.BALANCE_OTHERS);
            String name = args[0];
            plugin.playerData().lookup(name).whenComplete((snap, ex) -> plugin.tasks().sync(() -> {
                if (ex != null) {
                    plugin.messages().send(sender, "general.error");
                } else if (snap.isEmpty()) {
                    plugin.messages().send(sender, "general.player-unknown", "player", name);
                } else {
                    show(sender, label, snap.get().name(), snap.get().coins(), snap.get().gems(), false);
                }
            }));
            return;
        }
        Player player = player(sender);
        PlayerData data = data(player);
        show(sender, label, player.getName(), data.coins(), data.gems(), true);
    }

    private void show(CommandSender to, String label, String name, long coins, long gems, boolean self) {
        boolean pack = to instanceof Player p && plugin.display().hasPack(p);
        String suffix = self ? "" : "-other";
        switch (label) {
            case "coins" -> plugin.messages().send(to, "economy.coins" + suffix, "player", name, "coins", coins,
                    "icon", Glyphs.icon("coin", pack));
            case "gems" -> plugin.messages().send(to, "economy.gems" + suffix, "player", name, "gems", gems,
                    "icon", Glyphs.icon("gem", pack));
            default -> plugin.messages().send(to, "economy.balance" + suffix, "player", name, "coins", coins, "gems", gems,
                    "icon_coin", Glyphs.icon("coin", pack), "icon_gem", Glyphs.icon("gem", pack));
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 && sender.hasPermission(Perms.BALANCE_OTHERS) ? onlineNames(sender) : List.of();
    }
}
