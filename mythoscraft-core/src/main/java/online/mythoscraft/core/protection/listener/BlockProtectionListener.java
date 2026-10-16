package online.mythoscraft.core.protection.listener;

import online.mythoscraft.core.protection.DamageSources;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.protection.MaterialGroups;
import online.mythoscraft.core.protection.ProtectionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;

/** Direct block changes and block interactions by players. */
public final class BlockProtectionListener implements Listener {

    private final ProtectionService protection;

    public BlockProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!protection.check(event.getPlayer(), event.getBlock(), Flag.BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!protection.check(event.getPlayer(), event.getBlockPlaced(), Flag.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (!protection.check(event.getPlayer(), event.getBlock(), Flag.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!protection.check(event.getPlayer(), event.getBlock(), Flag.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!protection.check(event.getPlayer(), event.getBlock(), Flag.BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!protection.check(event.getPlayer(), event.getHarvestedBlock(), Flag.BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLecternTake(PlayerTakeLecternBookEvent event) {
        if (!protection.check(event.getPlayer(), event.getLectern().getBlock(), Flag.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        Player player = DamageSources.resolve(event.getPrimingEntity());
        boolean allowed = player != null
                ? protection.check(player, event.getBlock(), Flag.EXPLOSIONS)
                : protection.query(event.getBlock(), Flag.EXPLOSIONS);
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!protection.check(event.getPlayer(), event.getPlayer().getLocation(), Flag.ITEM_DROP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    @SuppressWarnings("deprecation") // Material#isInteractable has no non-experimental replacement yet
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        if (protection.canBypass(player)) {
            return;
        }
        Material type = block.getType();
        switch (event.getAction()) {
            case PHYSICAL -> {
                if (type == Material.FARMLAND || type == Material.TURTLE_EGG || type == Material.SNIFFER_EGG) {
                    if (!protection.query(block, Flag.BREAK)) {
                        event.setUseInteractedBlock(Event.Result.DENY);
                        event.setCancelled(true);
                    }
                } else if (MaterialGroups.USABLES.contains(type) && !protection.query(block, Flag.USE)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                    event.setCancelled(true);
                }
            }
            case RIGHT_CLICK_BLOCK -> {
                ItemStack item = event.getItem();
                boolean itemFirst = player.isSneaking() && item != null && !item.getType().isAir();
                if (!itemFirst) {
                    Flag flag = MaterialGroups.CONTAINERS.contains(type) ? Flag.CONTAINER
                            : MaterialGroups.USABLES.contains(type) ? Flag.USE
                            : type.isInteractable() ? Flag.INTERACT : null;
                    if (flag != null) {
                        if (!protection.check(player, block, flag)) {
                            event.setCancelled(true);
                        }
                        return;
                    }
                }
                if (item != null && MaterialGroups.BLOCK_CHANGING_ITEMS.contains(item.getType())
                        && !protection.check(player, block, Flag.BUILD)) {
                    event.setCancelled(true);
                }
            }
            case LEFT_CLICK_BLOCK -> {
                Block facing = event.getBlockFace() == null ? null : block.getRelative(event.getBlockFace());
                if (facing != null && (facing.getType() == Material.FIRE || facing.getType() == Material.SOUL_FIRE)
                        && !protection.check(player, facing, Flag.BREAK)) {
                    event.setCancelled(true);
                } else if (type == Material.DRAGON_EGG && !protection.check(player, block, Flag.INTERACT)) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }
}
