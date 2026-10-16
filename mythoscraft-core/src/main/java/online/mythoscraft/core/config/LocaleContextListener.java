package online.mythoscraft.core.config;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * Sets the text language to the acting player's language for the duration of an event, so menus, item names, titles
 * and rewards built in event handlers appear in that player's language.
 */
public final class LocaleContextListener implements Listener {

    private final Messages messages;

    public LocaleContextListener(Messages messages) {
        this.messages = messages;
    }

    private void on(Player p) {
        messages.context(p);
    }

    private void off() {
        messages.context(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerInteractEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerInteractEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerInteractEntityEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerInteractEntityEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerInteractAtEntityEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerInteractAtEntityEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PrePlayerAttackEntityEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PrePlayerAttackEntityEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            on(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(InventoryClickEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(BlockBreakEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(BlockBreakEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(BlockPlaceEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(BlockPlaceEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerItemConsumeEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerItemConsumeEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerFishEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerFishEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerMoveEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerMoveEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerJoinEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerJoinEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerRespawnEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerRespawnEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(PlayerToggleFlightEvent e) {
        on(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(PlayerToggleFlightEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            on(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(EntityDamageByEntityEvent e) {
        off();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void a(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            on(e.getEntity().getKiller());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void b(EntityDeathEvent e) {
        off();
    }
}
