package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Turns helmets from older plugin versions into the new 3D helmets when their owner joins. */
public final class HelmetUpgradeListener implements Listener {

    private final MythosCraft plugin;

    public HelmetUpgradeListener(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(20, "helmet upgrade", () -> {
            if (!p.isOnline()) {
                return;
            }
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null) {
                    plugin.items().upgradeHelmet(item);
                }
            }
            for (ItemStack item : p.getEnderChest().getContents()) {
                if (item != null) {
                    plugin.items().upgradeHelmet(item);
                }
            }
        });
    }
}
