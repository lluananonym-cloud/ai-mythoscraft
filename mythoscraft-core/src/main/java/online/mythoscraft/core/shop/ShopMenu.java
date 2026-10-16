package online.mythoscraft.core.shop;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.Messages;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.player.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Gem shop menu: left click buys once, shift click buys several. */
public final class ShopMenu extends Menu {

    private final MythosCraft plugin;
    private final ShopManager shop;
    private final Map<Integer, ShopManager.Entry> slots = new HashMap<>();

    private final int page;

    public ShopMenu(MythosCraft plugin, Player viewer) {
        this(plugin, viewer, 1);
    }

    public ShopMenu(MythosCraft plugin, Player viewer, int page) {
        super(plugin.shop().rows(), plugin.shop().pages() > 1
                ? plugin.messages().get("shop.title-page", "page", Math.max(1, Math.min(page, plugin.shop().pages())), "pages", plugin.shop().pages())
                : plugin.messages().get("shop.title"));
        this.page = Math.max(1, Math.min(page, plugin.shop().pages()));
        this.plugin = plugin;
        this.shop = plugin.shop();
        Messages m = plugin.messages();
        int size = shop.rows() * 9;
        ItemStack filler = MenuItems.filler();
        for (int i = 0; i < size; i++) {
            getInventory().setItem(i, filler);
        }
        int next = 10;
        for (ShopManager.Entry entry : shop.entries()) {
            if (entry.page() != this.page) {
                continue;
            }
            int slot = entry.slot();
            if (slot < 0 || slot >= size - 9 || slots.containsKey(slot)) {
                while (next < size - 9 && (slots.containsKey(next) || next % 9 == 0 || next % 9 == 8)) {
                    next++;
                }
                slot = next++;
            }
            if (slot >= size - 9) {
                continue;
            }
            Component currency = m.get("economy.names." + entry.currency().id());
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(m.get("shop.lore.price", "price", entry.price(), "currency", currency));
            if (shop.seasonLocked(entry)) {
                lore.add(m.get("shop.lore.season-locked", "season", shop.seasonName(entry)));
            } else {
                if (entry.season() != null) {
                    lore.add(m.get("shop.lore.season-active", "season", shop.seasonName(entry)));
                }
                lore.add(m.get("shop.lore.buy-one"));
                lore.add(m.get("shop.lore.buy-many", "times", shop.bulkAmount(), "price", entry.price() * shop.bulkAmount(),
                        "currency", currency));
            }
            slots.put(slot, entry);
            getInventory().setItem(slot, MenuItems.withLore(shop.icon(entry), lore));
        }
        PlayerData data = plugin.playerData().get(viewer);
        getInventory().setItem(size - 5, MenuItems.button(Material.AMETHYST_CLUSTER, m.get("shop.balance"),
                m.getList("shop.balance-lore", "coins", data == null ? 0 : data.coins(), "gems", data == null ? 0 : data.gems())));
        getInventory().setItem(size - 9, MenuItems.button(Material.GOLD_INGOT, m.get("shop.to-auction"), List.of()));
        getInventory().setItem(size - 1, MenuItems.button(Material.WHEAT, m.get("shop.to-orders"), List.of()));
        if (this.page > 1) {
            getInventory().setItem(size - 7, MenuItems.button(Material.ARROW, m.get("shop.page-prev", "page", this.page - 1), List.of()));
        }
        if (this.page < shop.pages()) {
            getInventory().setItem(size - 3, MenuItems.button(Material.SPECTRAL_ARROW, m.get("shop.page-next", "page", this.page + 1), List.of()));
        }
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        int size = shop.rows() * 9;
        ShopManager.Entry entry = slots.get(slot);
        if (entry != null) {
            shop.buy(player, entry, event.isShiftClick() ? shop.bulkAmount() : 1);
            new ShopMenu(plugin, player, page).open(player);
        } else if (slot == size - 7 && page > 1) {
            new ShopMenu(plugin, player, page - 1).open(player);
        } else if (slot == size - 3 && page < shop.pages()) {
            new ShopMenu(plugin, player, page + 1).open(player);
        } else if (slot == size - 9) {
            player.performCommand("ah");
        } else if (slot == size - 1) {
            player.performCommand("auftrag");
        }
    }
}
