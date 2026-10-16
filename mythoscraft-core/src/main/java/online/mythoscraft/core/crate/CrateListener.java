package online.mythoscraft.core.crate;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right click a crate to open it, left click for the reward preview, sneak + right click to open a whole stack. */
public final class CrateListener implements Listener {

    private final MythosCraft plugin;
    private final CrateManager crates;

    public CrateListener(MythosCraft plugin, CrateManager crates) {
        this.plugin = plugin;
        this.crates = crates;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        PlacedCrate crate = crates.byEntity(event.getRightClicked());
        if (crate == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        CrateType type = crates.type(crate.type());
        Player player = event.getPlayer();
        if (type == null) {
            plugin.messages().send(player, "crates.type-missing", "crate", crate.type());
            plugin.getLogger().warning("Placed crate " + crate.id() + " uses unknown crate type '" + crate.type() + "' (crates.yml)");
            return;
        }
        crates.open(player, crate, type, player.isSneaking());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        PlacedCrate crate = crates.byEntity(event.getAttacked());
        if (crate == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        CrateType type = crates.type(crate.type());
        if (type == null) {
            return;
        }
        if (player.isSneaking() && player.hasPermission(Perms.CRATE_ADMIN) && player.getInventory().getItemInMainHand().isEmpty()) {
            plugin.messages().send(player, "crates.admin-info", "id", crate.id(), "type", type.id());
            return;
        }
        new CratePreview(plugin, crates, type, player).open(player);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        org.bukkit.Chunk chunk = event.getChunk();
        // spawn visuals one tick later, once the chunk is fully ready
        plugin.tasks().later(1, "chunk visuals", () -> {
            if (chunk.isLoaded()) {
                crates.onChunkLoad(chunk);
                plugin.holograms().onChunkLoad(chunk);
                plugin.buttons().onChunkLoad(chunk);
            }
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        crates.onChunkUnload(event.getChunk());
        plugin.holograms().onChunkUnload(event.getChunk());
        plugin.buttons().onChunkUnload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        crates.handleQuit(event.getPlayer());
    }
}
