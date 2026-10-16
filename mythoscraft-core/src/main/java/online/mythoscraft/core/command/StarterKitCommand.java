package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Kit;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * /new – the starter kit for new players (once per player, only while the playtime is low). Contents are configured
 * under starter-kit in config.yml: vanilla items like arena kits, custom items ("home_stone 2") and start coins.
 * Admins: /new reset &lt;player&gt;.
 */
public final class StarterKitCommand extends BaseCommand {

    static final String CLAIMED = "starter.kit-claimed";

    public StarterKitCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            permission(sender, Perms.ADMIN);
            Player target = online(sender, args[1]);
            plugin.progress().set(target, CLAIMED, 0);
            plugin.messages().send(sender, "starter-kit.reset", "player", target.getName());
            return;
        }
        Player p = player(sender);
        PlayerData data = data(p);
        ConfigReader cfg = new ConfigReader(plugin.getConfig(), "config.yml", new online.mythoscraft.core.config.ConfigProblems(plugin.getLogger())).sub("starter-kit");
        if (!cfg.bool("enabled", true)) {
            throw new CommandFail("starter-kit.disabled");
        }
        if (!plugin.progress().loaded(p)) {
            throw new CommandFail("general.data-loading");
        }
        if (plugin.progress().get(p, CLAIMED) > 0) {
            throw new CommandFail("starter-kit.already");
        }
        int maxHours = cfg.integer("max-playtime-hours", 10, 0, 100_000);
        if (maxHours > 0 && data.livePlaytime(System.currentTimeMillis()) > maxHours * 3600L) {
            throw new CommandFail("starter-kit.too-late", "hours", maxHours);
        }
        plugin.progress().set(p, CLAIMED, System.currentTimeMillis());
        give(p, Kit.parse("starter", cfg));
        for (String entry : cfg.raw().getStringList("custom-items")) {
            String[] parts = entry.trim().split("\\s+");
            CustomItem type = CustomItem.byId(parts[0]);
            if (type == null) {
                plugin.getLogger().warning("starter-kit.custom-items: unknown item '" + parts[0] + "'");
                continue;
            }
            int amount = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1])) : 1;
            CustomItemManager.give(p, plugin.items().create(type, amount));
        }
        long coins = cfg.longValue("coins", 250, 0, 1_000_000_000);
        if (coins > 0) {
            plugin.economy().deposit(data, Currency.COINS, coins);
            plugin.playerData().save(data);
            plugin.messages().send(p, "starter-kit.coins", "amount", coins);
        }
        plugin.messages().send(p, "starter-kit.received");
        SoundSpec.of("entity.player.levelup", 0.8f, 1.3f).play(p);
    }

    /** Like Kit#give, but never replaces armour the player already wears – it goes into the inventory instead. */
    private static void give(Player p, Kit kit) {
        PlayerInventory inv = p.getInventory();
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack item : kit.items()) {
            extra.add(item.clone());
        }
        if (kit.helmet() != null && isEmpty(inv.getHelmet())) {
            inv.setHelmet(kit.helmet().clone());
        } else if (kit.helmet() != null) {
            extra.add(kit.helmet().clone());
        }
        if (kit.chestplate() != null && isEmpty(inv.getChestplate())) {
            inv.setChestplate(kit.chestplate().clone());
        } else if (kit.chestplate() != null) {
            extra.add(kit.chestplate().clone());
        }
        if (kit.leggings() != null && isEmpty(inv.getLeggings())) {
            inv.setLeggings(kit.leggings().clone());
        } else if (kit.leggings() != null) {
            extra.add(kit.leggings().clone());
        }
        if (kit.boots() != null && isEmpty(inv.getBoots())) {
            inv.setBoots(kit.boots().clone());
        } else if (kit.boots() != null) {
            extra.add(kit.boots().clone());
        }
        if (kit.offhand() != null && isEmpty(inv.getItemInOffHand())) {
            inv.setItemInOffHand(kit.offhand().clone());
        } else if (kit.offhand() != null) {
            extra.add(kit.offhand().clone());
        }
        for (ItemStack item : extra) {
            inv.addItem(item).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    /** First join: point new players to /new (clickable, plus an action bar hint for players who skip the chat). */
    public static void hint(MythosCraft plugin, Player p) {
        if (!plugin.getConfig().getBoolean("starter-kit.enabled", true)) {
            return;
        }
        plugin.tasks().later(100, "starter kit hint", () -> {
            if (p.isOnline() && plugin.progress().get(p, CLAIMED) == 0) {
                plugin.messages().send(p, "starter-kit.hint");
                plugin.messages().actionBar(p, "starter-kit.hint-bar");
            }
        });
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(Perms.ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("reset");
        }
        return args.length == 2 ? onlineNames(sender) : List.of();
    }
}
