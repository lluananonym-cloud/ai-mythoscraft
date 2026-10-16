package online.mythoscraft.core.gui;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Small helpers for menu buttons. */
public final class MenuItems {

    private MenuItems() {
    }

    public static ItemStack filler() {
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(Component.space()).hideTooltip().build();
    }

    public static ItemStack button(Material material, Component name, List<Component> lore) {
        return new ItemBuilder(material).name(Text.noItalic(name)).loreComponents(lore.stream().map(Text::noItalic).toList()).build();
    }

    /** Copy of the item with extra lore lines below its own. */
    public static ItemStack withLore(ItemStack item, List<Component> extra) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (Component line : extra) {
            lore.add(Text.noItalic(line));
        }
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Fills the bottom row (slots 45-53) with filler panes. */
    public static void bottomBar(Menu menu) {
        for (int i = 45; i < 54; i++) {
            menu.getInventory().setItem(i, filler());
        }
    }
}
