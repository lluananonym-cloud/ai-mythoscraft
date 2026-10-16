package online.mythoscraft.core.protection.listener;

import online.mythoscraft.core.protection.DamageSources;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.protection.ProtectionService;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** PvP (direct and indirect), mob damage, entity protection and entity interactions. */
public final class EntityProtectionListener implements Listener {

    private final ProtectionService protection;

    public EntityProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && DamageSources.isPluginEntity(byEntity.getDamager())) {
            event.setCancelled(true); // celebration fireworks and other plugin visuals never hurt anyone
            return;
        }
        Entity victim = event.getEntity();
        Player attacker = DamageSources.attacker(event);
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean explosion = cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;

        if (victim instanceof Player player) {
            if (attacker != null && !attacker.equals(player)) {
                if (!protection.pvpAllowed(attacker, player)) {
                    event.setCancelled(true);
                    protection.deny(attacker, Flag.PVP);
                }
                return;
            }
            if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL
                    || cause == EntityDamageEvent.DamageCause.SUICIDE || cause == EntityDamageEvent.DamageCause.WORLD_BORDER) {
                return;
            }
            if (!protection.query(player.getLocation(), Flag.DAMAGE)) {
                event.setCancelled(true);
                return;
            }
            if (explosion && !protection.query(player.getLocation(), Flag.EXPLOSIONS)) {
                event.setCancelled(true);
                return;
            }
            if (event.getDamageSource().getCausingEntity() instanceof LivingEntity mob && !(mob instanceof Player)
                    && !protection.query(player.getLocation(), Flag.MOB_DAMAGE)) {
                event.setCancelled(true);
            }
            return;
        }

        if (DamageSources.isPluginEntity(victim)) {
            event.setCancelled(true);
            return;
        }
        if (DamageSources.isNpc(victim)) {
            if (attacker == null || !protection.canBypass(attacker)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!DamageSources.isProtected(victim)) {
            return;
        }
        Flag flag = victim instanceof Hanging || victim instanceof ArmorStand ? Flag.BREAK : Flag.ENTITY_DAMAGE;
        if (attacker != null) {
            if (!protection.check(attacker, victim.getLocation(), flag)) {
                event.setCancelled(true);
            }
        } else if (explosion) {
            if (!protection.query(victim.getLocation(), Flag.EXPLOSIONS)) {
                event.setCancelled(true);
            }
        } else if (event.getDamageSource().getCausingEntity() != null && !protection.query(victim.getLocation(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotion(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        Player thrower = DamageSources.resolve(potion);
        if (thrower == null || !isHarmful(potion.getEffects())) {
            return;
        }
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim) {
                if (!victim.equals(thrower) && !protection.pvpAllowed(thrower, victim)) {
                    event.setIntensity(affected, 0);
                }
            } else if (DamageSources.isProtected(affected) && !protection.allows(thrower, affected.getLocation(), Flag.ENTITY_DAMAGE)) {
                event.setIntensity(affected, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        Player thrower = event.getEntity().getSource() instanceof Player p ? p : null;
        if (thrower == null) {
            return;
        }
        boolean harmful = event.getEntity().getBasePotionType() != null
                && isHarmful(event.getEntity().getBasePotionType().getPotionEffects());
        harmful |= isHarmful(event.getEntity().getCustomEffects());
        if (!harmful) {
            return;
        }
        event.getAffectedEntities().removeIf(e -> e instanceof Player victim
                ? !victim.equals(thrower) && !protection.pvpAllowed(thrower, victim)
                : DamageSources.isProtected(e) && !protection.allows(thrower, e.getLocation(), Flag.ENTITY_DAMAGE));
    }

    private static boolean isHarmful(Iterable<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || event.getCaught() == null) {
            return;
        }
        Entity caught = event.getCaught();
        if (caught instanceof Player victim) {
            if (!protection.pvpAllowed(event.getPlayer(), victim)) {
                event.setCancelled(true);
            }
        } else if (DamageSources.isProtected(caught) && !protection.check(event.getPlayer(), caught.getLocation(), Flag.ENTITY_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleEntityInteraction(Player player, Entity entity, PlayerInteractEntityEvent event) {
        if (entity instanceof Player || DamageSources.isPluginEntity(entity) || DamageSources.isNpc(entity)) {
            return;
        }
        // trading is never griefing – villagers and wandering traders can always be used (also at the spawn)
        if (entity instanceof org.bukkit.entity.AbstractVillager) {
            return;
        }
        Flag flag = entity instanceof InventoryHolder && entity instanceof Vehicle && !(entity instanceof LivingEntity)
                ? Flag.CONTAINER : Flag.INTERACT;
        if (!protection.check(player, entity.getLocation(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!protection.check(event.getPlayer(), event.getRightClicked().getLocation(), Flag.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (!protection.check(event.getPlayer(), event.getEntity().getLocation(), Flag.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (!protection.check(event.getPlayer(), event.getEntity().getLocation(), Flag.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (!protection.check(event.getPlayer(), event.getEntity().getLocation(), Flag.ENTITY_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && !protection.check(player, event.getEntity().getLocation(), Flag.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && !protection.check(player, event.getBlock().getRelative(event.getBlockFace()), Flag.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Hanging hanging = event.getEntity();
        if (event instanceof HangingBreakByEntityEvent byEntity) {
            Player player = DamageSources.resolve(byEntity.getRemover());
            if (player != null) {
                if (!protection.check(player, hanging.getLocation(), Flag.BREAK)) {
                    event.setCancelled(true);
                }
                return;
            }
        }
        switch (event.getCause()) {
            case EXPLOSION -> {
                if (!protection.query(hanging.getLocation(), Flag.EXPLOSIONS)) {
                    event.setCancelled(true);
                }
            }
            case ENTITY -> {
                if (!protection.query(hanging.getLocation(), Flag.BREAK)) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!vehicleChangeAllowed(event.getAttacker(), event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!vehicleChangeAllowed(event.getAttacker(), event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    private boolean vehicleChangeAllowed(Entity attacker, Vehicle vehicle) {
        Player player = DamageSources.resolve(attacker);
        if (player != null) {
            return protection.check(player, vehicle.getLocation(), Flag.BREAK);
        }
        return attacker == null || protection.query(vehicle.getLocation(), Flag.BREAK);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getFoodLevel() < player.getFoodLevel()
                && !protection.query(player.getLocation(), Flag.HUNGER)) {
            event.setCancelled(true);
        }
    }
}
