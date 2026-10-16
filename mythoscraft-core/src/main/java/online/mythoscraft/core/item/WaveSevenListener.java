package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Abilities of the ocean, forest, sky and crystal collections. */
public final class WaveSevenListener implements Listener {

    private static final String P = "projectile";
    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Map<UUID, Long> prism = new HashMap<>();
    private ConfigReader cfg;

    public WaveSevenListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 30, 10, "charms wave 7", this::charms);
    }

    private double num(String path, double def) {
        return cfg.decimal(path, def, 0, 100_000);
    }

    private static void refresh(Player p, PotionEffectType type, int amp) {
        PotionEffect e = p.getPotionEffect(type);
        if (e == null || e.getDuration() < 60) {
            p.addPotionEffect(new PotionEffect(type, type == PotionEffectType.NIGHT_VISION ? 400 : 120, amp, true, false, true));
        }
    }

    private void charms() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CustomItem off = plugin.items().identify(p.getInventory().getItemInOffHand());
            if (off == null) {
                continue;
            }
            switch (off) {
                case PEARL_CHARM -> {
                    if (p.isInWater()) {
                        refresh(p, PotionEffectType.CONDUIT_POWER, 0);
                    }
                }
                case OWL_CHARM -> refresh(p, PotionEffectType.NIGHT_VISION, 0);
                case SKY_GLIDER -> {
                    if (p.isSneaking() && !((Entity) p).isOnGround() && p.getVelocity().getY() < -0.1) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20, 0, true, false, true));
                        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.3, 0.1, 0.3, 0);
                    }
                }
                case CRYSTAL_HEART -> {
                    if (p.getAbsorptionAmount() < 1 && ThreadLocalRandom.current().nextInt(60) == 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 0, true, false, true));
                    }
                }
                default -> {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        CustomItem type = plugin.items().identify(item);
        if (type == null) {
            return;
        }
        switch (type) {
            case TIDE_STAFF -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "tide_staff", num("tide-staff.cooldown-seconds", 6))) {
                    return;
                }
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                for (Entity e : p.getNearbyEntities(7, 3, 7)) {
                    Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (e instanceof LivingEntity le && to.lengthSquared() > 0.01 && to.clone().normalize().dot(dir) > 0.4
                            && support.mayHurt(p, le)) {
                        le.setVelocity(to.normalize().multiply(1.6).setY(0.5));
                        le.damage(num("tide-staff.damage", 3), p);
                    }
                }
                for (int i = 1; i <= 7; i++) {
                    p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(dir.clone().multiply(i)).add(0, 0.8, 0), 25, 0.8, 0.4, 0.8, 0.1);
                }
                SoundSpec.of("entity.player.splash.high_speed", 1f, 0.9f).playAt(p.getLocation());
            }
            case SHELL_HORN -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "shell_horn", num("shell-horn.cooldown-seconds", 60))) {
                    return;
                }
                for (Player other : p.getWorld().getPlayers()) {
                    if (other.getLocation().distanceSquared(p.getLocation()) <= 400) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
                        other.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 600, 0));
                        other.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 600, 0));
                    }
                }
                SoundSpec.of("item.goat_horn.sound.0", 2f, 1.2f).playAt(p.getLocation());
            }
            case TREASURE_NET -> {
                event.setCancelled(true);
                RayTraceResult water = p.rayTraceBlocks(6, FluidCollisionMode.ALWAYS);
                if (water == null || water.getHitBlock() == null || water.getHitBlock().getType() != Material.WATER) {
                    plugin.messages().actionBar(p, "abilities.net-water");
                    return;
                }
                if (support.onCooldown(p, item, "treasure_net", 3)) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                Material[] loot = {Material.COD, Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH, Material.NAUTILUS_SHELL,
                        Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS, Material.INK_SAC, Material.EMERALD};
                for (int i = 0; i < 3; i++) {
                    CustomItemManager.give(p, new ItemStack(loot[ThreadLocalRandom.current().nextInt(loot.length)]));
                }
                water.getHitBlock().getWorld().spawnParticle(Particle.SPLASH, water.getHitBlock().getLocation().add(0.5, 1, 0.5), 40, 0.6, 0.2, 0.6, 0.2);
                SoundSpec.of("entity.fishing_bobber.splash", 1f, 1f).playAt(p.getLocation());
            }
            case DRUID_STAFF -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "druid_staff", num("druid-staff.cooldown-seconds", 30))) {
                    return;
                }
                for (Player other : p.getWorld().getPlayers()) {
                    if (other.getLocation().distanceSquared(p.getLocation()) <= 64) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
                        other.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, other.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0);
                    }
                }
                p.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, p.getLocation().add(0, 2, 0), 60, 3, 1, 3, 0);
                SoundSpec.of("block.azalea_leaves.break", 1f, 0.8f).playAt(p.getLocation());
            }
            case VINE_WHIP -> {
                event.setCancelled(true);
                RayTraceResult hit = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 7, FluidCollisionMode.NEVER,
                        true, 0.5, e -> e instanceof LivingEntity && !e.equals(p) && support.mayHurt(p, e));
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
                    plugin.messages().actionBar(p, "tools.no-target");
                    return;
                }
                if (support.onCooldown(p, item, "vine_whip", num("vine-whip.cooldown-seconds", 4))) {
                    return;
                }
                target.damage(num("vine-whip.damage", 5), p);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                Location eye = p.getEyeLocation();
                Vector dir = target.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
                for (double d = 0.5; d < dir.length(); d += 0.4) {
                    p.getWorld().spawnParticle(Particle.DUST, eye.clone().add(dir.clone().normalize().multiply(d)), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(0x15803D), 1f));
                }
                SoundSpec.of("entity.player.attack.sweep", 1f, 1.4f).playAt(p.getLocation());
            }
            case SEED_OF_LIFE -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "seed_of_life", num("seed-of-life.cooldown-seconds", 300))) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                        : p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                p.setHealth(max);
                p.setFoodLevel(20);
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 1));
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
                SoundSpec.of("block.beacon.activate", 1f, 1.4f).play(p);
            }
            case WIND_HORN -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "wind_horn", num("wind-horn.cooldown-seconds", 10))) {
                    return;
                }
                p.setVelocity(p.getVelocity().setY(1.4));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
                p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 2, 0.3, 0.1, 0.3, 0);
                SoundSpec.of("item.goat_horn.sound.4", 1.2f, 1.4f).playAt(p.getLocation());
            }
            case STAR_MAP -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "star_map", 3)) {
                    return;
                }
                Location l = p.getLocation();
                Location spawn = plugin.spawn().spawn();
                long dist = spawn == null || !spawn.getWorld().equals(l.getWorld()) ? -1 : Math.round(spawn.distance(l));
                plugin.messages().send(p, "abilities.star-map", "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(),
                        "biome", net.kyori.adventure.text.Component.translatable(l.getBlock().getBiome().translationKey()),
                        "spawn", dist < 0 ? "?" : String.valueOf(dist));
                if (spawn != null) {
                    p.setCompassTarget(spawn);
                }
            }
            case COMET_SHARD -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                p.giveExp((int) num("comet-shard.xp", 300));
                p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
                SoundSpec.of("entity.player.levelup", 1f, 1.5f).play(p);
            }
            case BALLOON -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "balloon", num("balloon.cooldown-seconds", 15))) {
                    return;
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0));
                SoundSpec.of("entity.chicken.egg", 1f, 0.6f).playAt(p.getLocation());
            }
            case CRYSTAL_WAND -> {
                event.setCancelled(true);
                RayTraceResult hit = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 24, FluidCollisionMode.NEVER,
                        true, 0.5, e -> e instanceof LivingEntity && !e.equals(p) && support.mayHurt(p, e));
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
                    plugin.messages().actionBar(p, "tools.no-target");
                    return;
                }
                if (support.onCooldown(p, item, "crystal_wand", num("crystal-wand.cooldown-seconds", 5))) {
                    return;
                }
                target.damage(num("crystal-wand.damage", 7), p);
                target.setGlowing(true);
                plugin.tasks().later(60, "crystal glow", () -> target.setGlowing(false));
                Location eye = p.getEyeLocation();
                Vector dir = target.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
                for (double d = 0.5; d < dir.length(); d += 0.4) {
                    p.getWorld().spawnParticle(Particle.DUST, eye.clone().add(dir.clone().normalize().multiply(d)), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(0xE879F9), 1.2f));
                }
                SoundSpec.of("block.amethyst_block.break", 1f, 1.6f).playAt(target.getLocation());
            }
            case ARCANE_TOME -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                p.giveExp((int) num("arcane-tome.xp", 1000));
                p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.5, 0), 80, 0.5, 0.5, 0.5, 1);
                SoundSpec.of("block.enchantment_table.use", 1f, 1f).play(p);
            }
            case PRISM_SHIELD -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "prism_shield", num("prism-shield.cooldown-seconds", 60))) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                prism.put(p.getUniqueId(), System.currentTimeMillis() + 10_000);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.6, 0.9, 0.6, 0.05);
                plugin.messages().actionBar(p, "abilities.prism");
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        CustomItem type = plugin.items().identify(event.getItem());
        Player p = event.getPlayer();
        if (type == null) {
            return;
        }
        switch (type) {
            case DIVING_FLASK -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 300, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 300, 0));
            }
            case SUSHI -> p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 20 * 60, 0));
            case BERRY_PIE -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0));
            case SKY_COOKIE -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 60, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 60, 1));
            }
            case MANA_POTION -> {
                support.resetCooldowns(p);
                p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1);
                plugin.messages().actionBar(p, "abilities.mana");
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof Player defender && prism.getOrDefault(defender.getUniqueId(), 0L) > System.currentTimeMillis()
                && event.getDamager() instanceof LivingEntity attacker && support.mayHurt(defender, attacker)) {
            attacker.damage(event.getFinalDamage() * 0.3);
        }
        if (!(event.getDamager() instanceof Player p) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(victim instanceof LivingEntity le)) {
            return;
        }
        CustomItem type = plugin.items().identify(p.getInventory().getItemInMainHand());
        if (type == CustomItem.CORAL_BLADE) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0));
            if (le.isInWater()) {
                le.damage(3, p);
            }
        } else if (type == CustomItem.CLOUD_SWORD) {
            le.setVelocity(le.getVelocity().setY(0.6));
            le.getWorld().spawnParticle(Particle.CLOUD, le.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.02);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.CLOUD_SWORD)
                && p.getFallDistance() > 1.5) {
            event.setDamage(event.getDamage() * 1.4);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p) || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        CustomItem bow = plugin.items().identify(event.getBow());
        if (bow == CustomItem.LEAF_BOW) {
            arrow.getPersistentDataContainer().set(Keys.key(P), PersistentDataType.STRING, "leaf_bow");
        } else if (bow == CustomItem.CRYSTAL_BOW) {
            for (int side : new int[]{-1, 1}) {
                Vector v = arrow.getVelocity().clone().rotateAroundY(Math.toRadians(8 * side));
                Arrow extra = p.launchProjectile(Arrow.class, v);
                extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                extra.setDamage(arrow.getDamage() * 0.6);
            }
        }
    }

    @EventHandler
    public void onProjectile(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        if (event.getEntity() instanceof AbstractArrow arrow
                && "leaf_bow".equals(arrow.getPersistentDataContainer().get(Keys.key(P), PersistentDataType.STRING))
                && event.getHitEntity() instanceof LivingEntity le && support.mayHurt(shooter, le)) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 4));
            le.getWorld().spawnParticle(Particle.BLOCK, le.getLocation(), 20, 0.4, 0.2, 0.4, 0, Material.OAK_LEAVES.createBlockData());
            return;
        }
        if (!(event.getEntity() instanceof Snowball ball)) {
            return;
        }
        CustomItem thrown = plugin.items().identify(ball.getItem());
        if (thrown == null) {
            return;
        }
        Location at = ball.getLocation();
        World w = at.getWorld();
        switch (thrown) {
            case KRAKEN_INK -> {
                w.spawnParticle(Particle.SQUID_INK, at, 80, 1.5, 1, 1.5, 0.05);
                for (Entity e : w.getNearbyEntities(at, 3, 3, 3)) {
                    if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                    }
                }
            }
            case ACORN_BOMB -> {
                Location ground = event.getHitBlock() != null && event.getHitBlockFace() != null
                        ? event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation() : at;
                if (plugin.protection().allows(shooter, ground, Flag.BUILD) && w.generateTree(ground, TreeType.TREE)) {
                    w.spawnParticle(Particle.HAPPY_VILLAGER, ground.add(0.5, 1, 0.5), 30, 1, 1, 1, 0);
                    SoundSpec.of("block.azalea.place", 1f, 0.8f).playAt(ground);
                } else {
                    w.spawnParticle(Particle.BLOCK, at, 20, 0.3, 0.3, 0.3, 0, Material.OAK_LEAVES.createBlockData());
                }
            }
            case THUNDER_BOTTLE -> {
                w.strikeLightningEffect(at);
                for (Entity e : w.getNearbyEntities(at, 2.5, 3, 2.5)) {
                    if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                        le.damage(num("thunder-bottle.damage", 5), shooter);
                    }
                }
            }
            case SHARD_BOMB -> {
                w.spawnParticle(Particle.BLOCK, at, 60, 1, 0.6, 1, 0, Material.AMETHYST_BLOCK.createBlockData());
                SoundSpec.of("block.amethyst_block.break", 1.5f, 0.8f).playAt(at);
                for (Entity e : w.getNearbyEntities(at, 3, 3, 3)) {
                    if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                        le.damage(num("shard-bomb.damage", 4), shooter);
                        le.setGlowing(true);
                        plugin.tasks().later(100, "shard glow", () -> le.setGlowing(false));
                    }
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player p = event.getPlayer();
        String type = event.getBlockState().getType().name();
        boolean ore = type.endsWith("_ORE") || type.equals("ANCIENT_DEBRIS");
        boolean log = type.endsWith("_LOG") || type.endsWith("_STEM");
        boolean crystalPick = ore && plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.CRYSTAL_PICKAXE)
                && ThreadLocalRandom.current().nextDouble() < num("crystal-pickaxe.double-chance", 0.12);
        boolean lumber = log && plugin.items().is(p.getInventory().getItemInOffHand(), CustomItem.LUMBER_CHARM);
        if (!crystalPick && !lumber) {
            return;
        }
        List<Item> items = List.copyOf(event.getItems());
        for (Item drop : items) {
            event.getItems().add(drop.getWorld().dropItem(drop.getLocation(), drop.getItemStack().clone()));
        }
        event.getBlock().getWorld().spawnParticle(Particle.WAX_OFF, event.getBlock().getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0);
    }
}
