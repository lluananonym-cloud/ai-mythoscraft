package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.Keys;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Custom items and crate keys must not be crafted into vanilla items or placed as blocks. */
public final class ItemGuardListener implements Listener {

    private final MythosCraft plugin;

    public ItemGuardListener(MythosCraft plugin) {
        this.plugin = plugin;
    }

    private boolean special(ItemStack item) {
        return item != null && !item.isEmpty()
                && (plugin.items().isCustom(item) || item.getPersistentDataContainer().has(Keys.CRATE, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (special(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (special(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }
}
