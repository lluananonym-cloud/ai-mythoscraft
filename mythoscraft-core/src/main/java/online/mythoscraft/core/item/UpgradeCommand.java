package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /schmied – shows what the next upgrade of the item in hand costs; /schmied ok upgrades it. */
public final class UpgradeCommand extends BaseCommand {

    public UpgradeCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        UpgradeService up = plugin.upgrades();
        if (!up.enabled()) {
            throw new CommandFail("upgrades.disabled");
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (args.length > 0 && (args[0].equalsIgnoreCase("ok") || args[0].equalsIgnoreCase("confirm"))) {
            UpgradeService.Result r = up.upgrade(p);
            switch (r) {
                case DONE -> plugin.messages().send(p, "upgrades.done", "level", up.level(p.getInventory().getItemInMainHand()));
                case FAILED -> plugin.messages().send(p, "upgrades.failed");
                default -> plugin.messages().send(p, "upgrades." + r.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
            }
            if (r == UpgradeService.Result.DONE || r == UpgradeService.Result.FAILED) {
                showNext(p);
            }
            return;
        }
        if (item.getType().isAir() || UpgradeService.kind(item) == null) {
            plugin.messages().send(p, "upgrades.help");
            return;
        }
        showNext(p);
    }

    private void showNext(Player p) {
        UpgradeService up = plugin.upgrades();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (UpgradeService.kind(item) == null) {
            return;
        }
        int level = up.level(item);
        if (level >= up.maxLevel()) {
            plugin.messages().send(p, "upgrades.max-level");
            return;
        }
        UpgradeService.Cost c = up.cost(level, p);
        plugin.messages().send(p, "upgrades.info", "item", item.effectiveName(), "level", level, "next", level + 1,
                "coins", Numbers.format(c.coins()), "gems", Numbers.format(c.gems()), "amount", c.amount(),
                "material", UpgradeService.materialName(c.material()), "chance", c.chance());
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 ? List.of("ok") : List.of();
    }
}
