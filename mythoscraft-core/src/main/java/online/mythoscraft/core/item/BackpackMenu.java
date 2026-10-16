package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.util.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 27 extra slots stored inside the backpack item itself. The backpack is found again by its unique id when the
 * menu closes; while open it can't be moved, dropped or put into itself, so contents can never be lost or duplicated.
 */
public final class BackpackMenu extends Menu {

    private static final NamespacedKey CONTENTS = Keys.key("backpack");

    private final MythosCraft plugin;
    private final String id;

    public BackpackMenu(MythosCraft plugin, Player player, ItemStack backpack) {
        super(3, plugin.messages().get("items.mythos_backpack.name"));
        this.plugin = plugin;
        String unique = backpack.getPersistentDataContainer().get(Keys.UNIQUE, PersistentDataType.STRING);
        if (unique == null) {
            unique = java.util.UUID.randomUUID().toString();
            String u = unique;
            backpack.editPersistentDataContainer(pdc -> pdc.set(Keys.UNIQUE, PersistentDataType.STRING, u));
        }
        this.id = unique;
        byte[] data = backpack.getPersistentDataContainer().get(CONTENTS, PersistentDataType.BYTE_ARRAY);
        if (data != null) {
            try {
                getInventory().setContents(ItemStack.deserializeItemsFromBytes(data));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Backpack of " + player.getName() + " could not be read: " + e.getMessage());
            }
        }
        online.mythoscraft.core.util.SoundSpec.of("item.armor.equip_leather", 1f, 0.8f).play(player);
    }

    @Override
    public boolean locked() {
        return false;
    }

    private boolean isBackpack(ItemStack item) {
        return item != null && plugin.items().is(item, CustomItem.MYTHOS_BACKPACK);
    }

    @Override
    public void onAnyClick(Player player, InventoryClickEvent event) {
        boolean touchesBackpack = isBackpack(event.getCurrentItem()) || isBackpack(event.getCursor())
                || (event.getClick().isKeyboardClick() && event.getHotbarButton() >= 0
                && isBackpack(player.getInventory().getItem(event.getHotbarButton())))
                || event.getAction() == InventoryAction.HOTBAR_SWAP && isBackpack(player.getInventory().getItemInOffHand());
        if (touchesBackpack) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean allowDrag(InventoryDragEvent event) {
        return !isBackpack(event.getOldCursor());
    }

    @Override
    public void onClose(Player player) {
        byte[] data = ItemStack.serializeItemsAsBytes(getInventory().getContents());
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isBackpack(stack) && id.equals(stack.getPersistentDataContainer().get(Keys.UNIQUE, PersistentDataType.STRING))) {
                stack.editPersistentDataContainer(pdc -> pdc.set(CONTENTS, PersistentDataType.BYTE_ARRAY, data));
                online.mythoscraft.core.util.SoundSpec.of("item.armor.equip_leather", 1f, 1.1f).play(player);
                return;
            }
        }
        // backpack no longer in the inventory (should be impossible) – never lose the contents
        for (ItemStack stack : getInventory().getContents()) {
            if (stack != null && !stack.isEmpty()) {
                CustomItemManager.give(player, stack);
            }
        }
        plugin.getLogger().warning("Backpack of " + player.getName() + " was missing on close – contents returned to the inventory.");
    }
}
