package online.mythoscraft.core.command.sub;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /mythos item give &lt;player&gt; &lt;item&gt; | /mythos item list */
public final class ItemSub implements SubCommand {

    private final MythosCraft plugin;

    public ItemSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "item";
    }

    @Override
    public String permission() {
        return Perms.ITEM_GIVE;
    }

    private static final List<String> SETS = List.of("mythos", "pumpkin", "frost", "inferno", "sculk", "dragon", "storm", "angel", "ninja", "knight", "miner", "viking", "ocean", "druid", "sky", "crystal", "pharaoh", "astronaut");

    private static List<String> ids() {
        List<String> ids = new ArrayList<>(Arrays.stream(CustomItem.values()).filter(CustomItem::giveable).map(CustomItem::id).toList());
        SETS.forEach(set -> ids.add(set + "_set"));
        return ids;
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            plugin.messages().send(sender, "items.list", "items", String.join(", ", ids()));
            return;
        }
        if (!args[0].equalsIgnoreCase("give") || args.length < 3) {
            throw CommandFail.usage("/mythos item give <Spieler> <Item|Set> [Anzahl]");
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            throw new CommandFail("general.player-not-found", "player", args[1]);
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (id.endsWith("_set") && SETS.contains(id.substring(0, id.length() - 4))) {
            for (CustomItem piece : CustomItem.armorSet(id.substring(0, id.length() - 4))) {
                CustomItemManager.give(target, plugin.items().create(piece));
            }
            plugin.messages().send(sender, "items.given", "player", target.getName(), "item",
                    plugin.messages().get("armor.sets." + id.substring(0, id.length() - 4)));
            return;
        }
        CustomItem item = CustomItem.byId(id);
        if (item == null || !item.giveable()) {
            throw new CommandFail("items.unknown", "items", String.join(", ", ids()));
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                throw CommandFail.usage("/mythos item give <Spieler> <Item|Set> [Anzahl]");
            }
        }
        for (int i = 0; i < amount; i++) {
            CustomItemManager.give(target, plugin.items().create(item));
        }
        Component name = plugin.messages().get("items." + item.id() + ".name");
        plugin.messages().send(sender, "items.given", "player", target.getName(), "item", amount > 1 ? Component.text(amount + "× ").append(name) : name);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 1 -> List.of("give", "list");
            case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case 3 -> ids().stream().filter(i -> i.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            case 4 -> List.of("1", "16", "64");
            default -> List.of();
        };
    }
}
