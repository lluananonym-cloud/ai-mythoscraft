package online.mythoscraft.core.gui;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Yes/no dialog with the affected item in the middle. */
public final class ConfirmMenu extends Menu {

    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;

    public ConfirmMenu(Component title, ItemStack display, Component yes, List<Component> yesLore, Component no,
                       Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        super(3, title);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(Component.space()).hideTooltip().build();
        for (int i = 0; i < 27; i++) {
            getInventory().setItem(i, filler);
        }
        getInventory().setItem(13, display);
        ItemStack confirm = new ItemBuilder(Material.LIME_CONCRETE).name(Text.noItalic(yes))
                .loreComponents(yesLore.stream().map(Text::noItalic).toList()).build();
        ItemStack cancel = new ItemBuilder(Material.RED_CONCRETE).name(Text.noItalic(no)).build();
        for (int slot : new int[]{10, 11}) {
            getInventory().setItem(slot, confirm);
        }
        for (int slot : new int[]{15, 16}) {
            getInventory().setItem(slot, cancel);
        }
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 10 || slot == 11) {
            player.closeInventory();
            onConfirm.accept(player);
        } else if (slot == 15 || slot == 16) {
            player.closeInventory();
            onCancel.accept(player);
        }
    }
}
