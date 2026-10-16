package online.mythoscraft.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Base for plugin inventories. Clicks are cancelled by default; subclasses react to the ones they care about. */
public abstract class Menu implements InventoryHolder {

    private final Inventory inventory;

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Locked menus cancel every click; unlocked ones (e.g. backpacks) let items move. */
    public boolean locked() {
        return true;
    }

    /** Called for clicks in the top inventory. For locked menus the event is already cancelled. */
    public void onClick(Player player, InventoryClickEvent event) {
    }

    /** Unlocked menus only: every click while the menu is open (top or bottom inventory). */
    public void onAnyClick(Player player, InventoryClickEvent event) {
    }

    /** Unlocked menus only: return false to block a drag. */
    public boolean allowDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        return true;
    }

    public void onClose(Player player) {
    }
}
