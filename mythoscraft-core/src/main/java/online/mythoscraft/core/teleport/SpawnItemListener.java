package online.mythoscraft.core.teleport;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * A compass that always stays in the inventory: right-click → teleport to spawn. For players who cannot use the chat
 * (console players, kids, phones). It cannot be dropped or stored and comes back after death or join.
 */
public final class SpawnItemListener implements Listener {

    private static final NamespacedKey KEY = new NamespacedKey(Keys.NAMESPACE, "spawn_item");

    private final MythosCraft plugin;

    public SpawnItemListener(MythosCraft plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("spawn.item.enabled", true);
    }

    public static boolean is(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    ItemStack create() {
        ItemStack item = new ItemBuilder(Material.COMPASS).name(plugin.messages().get("spawn.item-name")).build();
        item.editMeta(m -> m.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    /** Puts the compass into the last hotbar slot (or any free slot) unless the player already has one. */
    public void give(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            if (is(inv.getItem(i))) {
                if (found || !enabled()) {
                    inv.setItem(i, null); // duplicates / feature switched off
                }
                found = true;
            }
        }
        if (found || !enabled()) {
            return;
        }
        int slot = plugin.getConfig().getInt("spawn.item.slot", 8);
        if (slot >= 0 && slot < 36 && (inv.getItem(slot) == null || inv.getItem(slot).getType().isAir())) {
            inv.setItem(slot, create());
        } else if (inv.firstEmpty() >= 0) {
            inv.setItem(inv.firstEmpty(), create());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(30, "spawn item", () -> {
            if (p.isOnline()) {
                give(p);
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(2, "spawn item", () -> {
            if (p.isOnline()) {
                give(p);
            }
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(SpawnItemListener::is);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !is(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        plugin.spawn().teleport(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (is(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        boolean foreign = event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
        ItemStack moved = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if (is(moved) && (foreign && event.isShiftClick() || event.getClick().isKeyboardClick() && event.getClick().name().contains("DROP"))) {
            event.setCancelled(true);
        } else if (foreign && event.getClickedInventory() == event.getView().getTopInventory() && (is(cursor) || is(hotbar))) {
            event.setCancelled(true);
        } else if (event.getSlotType() == InventoryType.SlotType.OUTSIDE && is(cursor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!is(event.getOldCursor())) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && event.getRawSlots().stream().anyMatch(s -> s < top)) {
            event.setCancelled(true);
        }
    }
}
