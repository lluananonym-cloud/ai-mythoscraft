package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Silvester job gear: the hoe harvests and replants 7×7, the rod bites fast and catches double, the sword cuts
 * through everything around you, the job elixir doubles job XP for an hour, the lucky rocket pays coins.
 * (Pickaxe 5×5×5 and axe tree felling live in {@link ToolListener}; the XP bonus in the jobs service.)
 */
public final class SilvesterListener implements Listener {

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Set<UUID> busy = new HashSet<>();

    public SilvesterListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    private static boolean ripe(Block b) {
        return b.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()
                && b.getType() != Material.SUGAR_CANE && b.getType() != Material.CACTUS && b.getType() != Material.BAMBOO;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block center = event.getBlock();
        if (busy.contains(p.getUniqueId()) || p.isSneaking() || !ripe(center)
                || !plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.NEWYEAR_HOE)) {
            return;
        }
        busy.add(p.getUniqueId());
        try {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Block b = center.getRelative(dx, 0, dz);
                    if ((dx == 0 && dz == 0) || !ripe(b) || !plugin.protection().allows(p, b.getLocation(), Flag.BREAK)) {
                        continue;
                    }
                    Material type = b.getType();
                    p.breakBlock(b);
                    if (b.getType().isAir() && b.getRelative(0, -1, 0).getType() == Material.FARMLAND) {
                        b.setType(type); // replant (age 0)
                    }
                }
            }
            // replant the clicked one, too
            Material type = center.getType();
            plugin.tasks().later(1, "replant", () -> {
                if (center.getType().isAir() && center.getRelative(0, -1, 0).getType() == Material.FARMLAND) {
                    center.setType(type);
                }
            });
        } finally {
            busy.remove(p.getUniqueId());
        }
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center.getLocation().add(0.5, 0.5, 0.5), 30, 3, 0.3, 3, 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        if (!plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.NEWYEAR_ROD)) {
            return;
        }
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            FishHook hook = event.getHook();
            hook.setWaitTime(20, 80);
        } else if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caught) {
            ItemStack extra = caught.getItemStack().clone();
            plugin.tasks().later(1, "double catch", () -> CustomItemManager.give(p, extra));
            p.getWorld().spawnParticle(Particle.FIREWORK, caught.getLocation(), 10, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || busy.contains(p.getUniqueId()) || !plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.NEWYEAR_SWORD)) {
            return;
        }
        busy.add(p.getUniqueId());
        try {
            double dmg = event.getDamage() * 0.6;
            for (Entity e : event.getEntity().getNearbyEntities(4, 2, 4)) {
                if (e instanceof LivingEntity le && !e.equals(p) && support.mayHurt(p, le)) {
                    le.damage(dmg, p);
                }
            }
            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, event.getEntity().getLocation().add(0, 1, 0), 6, 1.5, 0.3, 1.5, 0);
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.items().is(event.getItem(), CustomItem.JOB_ELIXIR)) {
            Player p = event.getPlayer();
            long until = Math.max(System.currentTimeMillis(), plugin.progress().get(p, "jobs.boost-until")) + 3_600_000L;
            plugin.progress().set(p, "jobs.boost-until", until);
            plugin.messages().send(p, "abilities.job-elixir");
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRocket(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (!plugin.items().is(item, CustomItem.LUCKY_ROCKET)) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        PlayerData data = plugin.playerData().get(p);
        if (data == null || support.onCooldown(p, item, "lucky_rocket", 2)) {
            return;
        }
        AbilitySupport.consumeOne(item);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        p.getWorld().spawn(p.getLocation().add(0, 1, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.fromRGB(rnd.nextInt(0xFFFFFF)),
                    Color.YELLOW).withFade(Color.WHITE).flicker(true).trail(true).build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(online.mythoscraft.core.util.Keys.ENTITY, PersistentDataType.STRING, "season_firework");
        });
        if (rnd.nextDouble() < 0.08) {
            long gems = plugin.economy().earn(data, Currency.GEMS, 1 + rnd.nextInt(3), false);
            plugin.messages().send(p, "abilities.lucky-rocket-gems", "gems", gems);
        } else {
            long coins = plugin.economy().earn(data, Currency.COINS, 300 + rnd.nextInt(1700), false);
            plugin.messages().send(p, "abilities.lucky-rocket", "coins", coins);
        }
        plugin.playerData().save(data);
        SoundSpec.of("entity.firework_rocket.launch", 1f, 1f).play(p);
    }
}
