package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.reward.RewardTable;
import online.mythoscraft.core.util.Effects;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abilities of the collection items: right-click actives, on-hit effects, special bows and projectiles, food,
 * charms that work from the off hand and the Phoenix Feather that saves its owner from death once.
 */
public final class ItemAbilityListener implements Listener {

    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(0xFF7A1A), 1.3f);
    private static final Particle.DustOptions ICE = new Particle.DustOptions(Color.fromRGB(0xA5F3FC), 1.3f);
    private static final Particle.DustOptions VOID = new Particle.DustOptions(Color.fromRGB(0x7C3AED), 1.4f);

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Set<UUID> chaining = new HashSet<>();
    private ConfigReader cfg;
    private List<Reward> giftRewards = List.of();

    public ItemAbilityListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        giftRewards = RewardTable.parseList(cfg, "gift-box.rewards");
        if (giftRewards.isEmpty()) {
            giftRewards = List.of(Reward.parse("coins 250"), Reward.parse("gems 3"), Reward.parse("tool hot_cocoa 2"));
        }
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 10, "item charms", this::charmTick);
    }

    private double num(String path, double def) {
        return cfg.decimal(path, def, 0, 100_000);
    }

    // =====================================================================================================
    // Right-click abilities
    // =====================================================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        CustomItem type = plugin.items().identify(item);
        if (type == null) {
            return;
        }
        switch (type) {
            case WITCH_BROOM -> {
                event.setCancelled(true);
                plugin.broom().toggle(p, item);
            }
            case GHOST_LANTERN -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "ghost_lantern", num("ghost-lantern.cooldown-seconds", 30))) {
                    return;
                }
                double r = num("ghost-lantern.radius", 12);
                int marked = 0;
                for (Entity e : p.getNearbyEntities(r, r, r)) {
                    if (e instanceof LivingEntity le && support.mayHurt(p, le)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 160, 0));
                        marked++;
                    }
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 600, 0, false, false));
                p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 60, r / 3, 1, r / 3, 0.02);
                SoundSpec.of("ambient.soul_sand_valley.mood", 1f, 1.5f).playAt(p.getLocation());
                plugin.messages().actionBar(p, "abilities.ghost-lantern", "count", marked);
            }
            case CANDY_BUCKET -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "candy_bucket", num("candy-bucket.cooldown-seconds", 300))) {
                    return;
                }
                CustomItem[] candies = {CustomItem.CANDY_CORN, CustomItem.LOLLIPOP, CustomItem.CARAMEL_APPLE};
                CustomItem candy = candies[ThreadLocalRandom.current().nextInt(candies.length)];
                CustomItemManager.give(p, plugin.items().create(candy, 1 + ThreadLocalRandom.current().nextInt(3)));
                SoundSpec.of("entity.item.pickup", 1f, 0.7f).play(p);
                plugin.messages().actionBar(p, "abilities.candy", "item", plugin.messages().get("items." + candy.id() + ".name"));
            }
            case ICE_WAND -> {
                event.setCancelled(true);
                RayTraceResult hit = rayEntity(p, num("ice-wand.range", 20));
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
                    plugin.messages().actionBar(p, "tools.no-target");
                    return;
                }
                if (support.onCooldown(p, item, "ice_wand", num("ice-wand.cooldown-seconds", 10))) {
                    return;
                }
                beam(p, target.getLocation().add(0, 1, 0), ICE);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3));
                target.setFreezeTicks(Math.max(target.getFreezeTicks(), 200));
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.02);
                SoundSpec.of("block.glass.break", 1f, 1.6f).playAt(target.getLocation());
            }
            case SNOW_CANNON -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "snow_cannon", num("snow-cannon.cooldown-seconds", 2))) {
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    Snowball ball = p.launchProjectile(Snowball.class);
                    Vector dir = p.getLocation().getDirection().multiply(1.8);
                    dir.add(new Vector(rnd(0.08), rnd(0.05), rnd(0.08)));
                    ball.setVelocity(dir);
                    ball.getPersistentDataContainer().set(Keys.key("projectile"), PersistentDataType.STRING, "snow_cannon");
                }
                SoundSpec.of("entity.snow_golem.shoot", 1f, 0.8f).playAt(p.getLocation());
            }
            case VOID_SCEPTER -> {
                event.setCancelled(true);
                Location target = blinkTarget(p, num("void-scepter.distance", 8));
                if (target == null) {
                    plugin.messages().actionBar(p, "tools.no-target");
                    return;
                }
                if (support.onCooldown(p, item, "void_scepter", num("void-scepter.cooldown-seconds", 8))) {
                    return;
                }
                Location from = p.getLocation();
                p.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 60, 0.3, 0.6, 0.3, 0.5);
                p.teleport(target);
                p.getWorld().spawnParticle(Particle.DUST, target.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0, VOID);
                SoundSpec.of("entity.enderman.teleport", 1f, 1.3f).playAt(target);
            }
            case EMBER_WAND -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "ember_wand", num("ember-wand.cooldown-seconds", 3))) {
                    return;
                }
                SmallFireball ball = p.launchProjectile(SmallFireball.class);
                ball.setVelocity(p.getLocation().getDirection().multiply(1.6));
                SoundSpec.of("entity.blaze.shoot", 1f, 1.2f).playAt(p.getLocation());
            }
            case HEALING_TOTEM -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "healing_totem", num("healing-totem.cooldown-seconds", 60))) {
                    return;
                }
                double r = num("healing-totem.radius", 6);
                double heal = num("healing-totem.heal", 6);
                for (Player other : p.getWorld().getPlayers()) {
                    if (other.getLocation().distanceSquared(p.getLocation()) <= r * r) {
                        heal(other, heal);
                        other.getWorld().spawnParticle(Particle.HEART, other.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
                    }
                }
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation(), 60, r / 2, 0.5, r / 2, 0);
                SoundSpec.of("block.beacon.power_select", 1f, 1.5f).playAt(p.getLocation());
            }
            case MYTHOS_BACKPACK -> {
                event.setCancelled(true);
                new BackpackMenu(plugin, p, item).open(p);
            }
            case XP_TOME -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                p.giveExp((int) num("xp-tome.xp", 500));
                SoundSpec.of("entity.player.levelup", 1f, 1.2f).play(p);
                p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.5);
            }
            case RETURN_FEATHER -> {
                event.setCancelled(true);
                Location spawn = plugin.spawn().spawn();
                if (spawn == null || plugin.arenas().isInArena(p) || plugin.tournaments().isParticipant(p)) {
                    plugin.messages().send(p, "arena.leave-first");
                    return;
                }
                plugin.teleports().teleport(p, spawn, 3, true, plugin.messages().get("spawn.name"), () -> {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (plugin.items().is(hand, CustomItem.RETURN_FEATHER)) {
                        AbilitySupport.consumeOne(hand);
                    }
                });
            }
            case LUCKY_CLOVER -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 60 * 30, 1));
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0);
                SoundSpec.of("block.amethyst_block.chime", 1f, 1.6f).play(p);
                plugin.messages().actionBar(p, "abilities.lucky");
            }
            case HASTE_RUNE -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 180, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 180, 0));
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1);
                SoundSpec.of("block.beacon.activate", 1f, 1.8f).play(p);
            }
            case GIFT_BOX -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                Reward r = giftRewards.get(ThreadLocalRandom.current().nextInt(giftRewards.size()));
                r.give(plugin, p, 1.0);
                Effects.burst(p.getLocation().add(0, 1.5, 0), Color.RED, Color.GREEN, false);
                plugin.messages().send(p, "abilities.gift", "reward", r.describe(plugin, 1.0));
            }
            case PUMPKIN_BOMB, CANDY_CORN, LOLLIPOP, CARAMEL_APPLE, HOT_COCOA, DRAGON_FLASK -> {
                // vanilla handles throwing/eating; effects follow in the dedicated handlers
            }
            default -> {
            }
        }
    }

    private static double rnd(double spread) {
        return ThreadLocalRandom.current().nextDouble(-spread, spread);
    }

    private RayTraceResult rayEntity(Player p, double range) {
        return p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.5, e -> e instanceof LivingEntity && !e.equals(p) && support.mayHurt(p, e));
    }

    private void beam(Player p, Location to, Particle.DustOptions dust) {
        Location eye = p.getEyeLocation();
        Vector dir = to.toVector().subtract(eye.toVector());
        double len = dir.length();
        dir.normalize();
        for (double d = 0.5; d < len; d += 0.4) {
            p.getWorld().spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
        }
    }

    /** Furthest passable spot (feet + head free) along the view direction, or null. */
    private Location blinkTarget(Player p, double distance) {
        RayTraceResult block = p.rayTraceBlocks(distance, FluidCollisionMode.NEVER);
        double max = block == null ? distance : block.getHitPosition().distance(p.getEyeLocation().toVector()) - 0.8;
        Vector dir = p.getLocation().getDirection().setY(Math.max(-0.4, Math.min(0.4, p.getLocation().getDirection().getY()))).normalize();
        for (double d = max; d >= 2; d -= 0.5) {
            Location l = p.getLocation().add(dir.clone().multiply(d));
            Block feet = l.getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && !feet.isLiquid()) {
                l.setYaw(p.getLocation().getYaw());
                l.setPitch(p.getLocation().getPitch());
                return l;
            }
        }
        return null;
    }

    private static void heal(LivingEntity e, double amount) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        e.setHealth(Math.min(cap, e.getHealth() + amount));
    }

    // =====================================================================================================
    // Food
    // =====================================================================================================

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        CustomItem type = plugin.items().identify(event.getItem());
        if (type == null) {
            return;
        }
        Player p = event.getPlayer();
        switch (type) {
            case CANDY_CORN -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1));
            case LOLLIPOP -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
            case CARAMEL_APPLE -> p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 1));
            case HOT_COCOA -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
                p.setFreezeTicks(0);
            }
            case DRAGON_FLASK -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 120, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 30, 0));
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
            }
            default -> {
                return;
            }
        }
        SoundSpec.of("entity.player.burp", 0.6f, 1.4f).play(p);
    }

    // =====================================================================================================
    // On-hit effects
    // =====================================================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            String bow = arrow.getPersistentDataContainer().get(Keys.key("projectile"), PersistentDataType.STRING);
            if ("ghost_bow".equals(bow)) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            } else if ("comet_bow".equals(bow)) {
                cometImpact(shooter, victim.getLocation());
            }
            return;
        }
        if (!(damager instanceof Player p) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || chaining.contains(p.getUniqueId())) {
            return;
        }
        CustomItem type = plugin.items().identify(p.getInventory().getItemInMainHand());
        if (type == null) {
            return;
        }
        switch (type) {
            case PUMPKIN_SCYTHE -> {
                chaining.add(p.getUniqueId());
                try {
                    double dmg = event.getFinalDamage() * 0.5;
                    for (Entity e : victim.getNearbyEntities(3, 1.5, 3)) {
                        if (e instanceof LivingEntity le && support.mayHurt(p, le)) {
                            le.damage(dmg, p);
                        }
                    }
                } finally {
                    chaining.remove(p.getUniqueId());
                }
                victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 3, 1, 0.2, 1, 0);
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 20, 1.2, 0.4, 1.2, 0, ORANGE);
            }
            case BAT_BLADE -> {
                heal(p, event.getFinalDamage() * 0.15);
                p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1, 0), 4, 0.2, 0.3, 0.2, 0);
            }
            case FROST_BLADE -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
            }
            case CANDY_CANE -> {
                Vector push = victim.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (push.lengthSquared() > 0.01) {
                    victim.setVelocity(victim.getVelocity().add(push.normalize().multiply(0.9).setY(0.35)));
                }
            }
            case STAR_SABER -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                    chaining.add(p.getUniqueId());
                    try {
                        victim.damage(4, p);
                    } finally {
                        chaining.remove(p.getUniqueId());
                    }
                    Location l = victim.getLocation();
                    for (int y = 6; y > 0; y--) {
                        victim.getWorld().spawnParticle(Particle.END_ROD, l.clone().add(0, y, 0), 3, 0.1, 0.1, 0.1, 0);
                    }
                    SoundSpec.of("entity.firework_rocket.twinkle", 1f, 1.4f).playAt(l);
                }
            }
            case INFERNO_AXE -> {
                victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.03);
            }
            default -> {
            }
        }
    }

    // =====================================================================================================
    // Bows and projectiles
    // =====================================================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        CustomItem bow = plugin.items().identify(event.getBow());
        if (bow != CustomItem.GHOST_BOW && bow != CustomItem.COMET_BOW) {
            return;
        }
        arrow.getPersistentDataContainer().set(Keys.key("projectile"), PersistentDataType.STRING, bow.id());
        if (bow == CustomItem.GHOST_BOW) {
            arrow.setGravity(false);
            arrow.setGlowing(true);
            arrow.setVelocity(arrow.getVelocity().multiply(1.2));
            plugin.tasks().later(60, "ghost arrow", () -> {
                if (arrow.isValid()) {
                    arrow.getWorld().spawnParticle(Particle.SOUL, arrow.getLocation(), 6, 0.1, 0.1, 0.1, 0.01);
                    arrow.remove();
                }
            });
        }
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || ticks++ > 100) {
                    cancel();
                    return;
                }
                if (bow == CustomItem.GHOST_BOW) {
                    arrow.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, arrow.getLocation(), 1, 0, 0, 0, 0);
                } else {
                    arrow.getWorld().spawnParticle(Particle.FIREWORK, arrow.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
                    arrow.getWorld().spawnParticle(Particle.END_ROD, arrow.getLocation(), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        String tag = projectile.getPersistentDataContainer().get(Keys.key("projectile"), PersistentDataType.STRING);
        if (projectile instanceof Snowball ball) {
            CustomItem thrown = plugin.items().identify(ball.getItem());
            if (thrown == CustomItem.PUMPKIN_BOMB) {
                pumpkinBlast(shooter, projectile.getLocation());
                return;
            }
            if ("snow_cannon".equals(tag) && event.getHitEntity() instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                le.damage(num("snow-cannon.damage", 2), shooter);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
            }
            return;
        }
        if ("comet_bow".equals(tag) && event.getHitBlock() != null) {
            cometImpact(shooter, projectile.getLocation());
        }
    }

    private void pumpkinBlast(Player shooter, Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
        w.spawnParticle(Particle.DUST, at, 60, 1.5, 1, 1.5, 0, ORANGE);
        w.spawnParticle(Particle.SOUL, at, 20, 1, 0.5, 1, 0.03);
        SoundSpec.of("entity.generic.explode", 1f, 1.4f).playAt(at);
        SoundSpec.of("entity.witch.celebrate", 1f, 1.2f).playAt(at);
        double r = num("pumpkin-bomb.radius", 3);
        for (Entity e : w.getNearbyEntities(at, r, r, r)) {
            if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                le.damage(num("pumpkin-bomb.damage", 5), shooter);
            }
        }
    }

    private void cometImpact(Player shooter, Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.FIREWORK, at, 40, 0.5, 0.5, 0.5, 0.15);
        w.spawnParticle(Particle.END_ROD, at, 20, 0.4, 0.4, 0.4, 0.1);
        SoundSpec.of("entity.firework_rocket.blast", 1f, 1.2f).playAt(at);
        chaining.add(shooter.getUniqueId());
        try {
            for (Entity e : w.getNearbyEntities(at, 2.5, 2.5, 2.5)) {
                if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                    le.damage(3, shooter);
                }
            }
        } finally {
            chaining.remove(shooter.getUniqueId());
        }
    }

    // =====================================================================================================
    // Grappling hook
    // =====================================================================================================

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        ItemStack rod = p.getInventory().getItemInMainHand();
        if (!plugin.items().is(rod, CustomItem.GRAPPLING_HOOK)) {
            rod = p.getInventory().getItemInOffHand();
            if (!plugin.items().is(rod, CustomItem.GRAPPLING_HOOK)) {
                return;
            }
        }
        PlayerFishEvent.State state = event.getState();
        if (state != PlayerFishEvent.State.IN_GROUND && state != PlayerFishEvent.State.REEL_IN
                && state != PlayerFishEvent.State.FAILED_ATTEMPT) {
            return;
        }
        FishHook hook = event.getHook();
        Location target = hook.getLocation();
        Vector diff = target.toVector().subtract(p.getLocation().toVector());
        double dist = diff.length();
        if (dist < 2 || (state != PlayerFishEvent.State.IN_GROUND && !hook.getLocation().clone().add(0, -0.3, 0).getBlock().isSolid())) {
            return;
        }
        if (support.onCooldown(p, rod, "grappling_hook", num("grappling-hook.cooldown-seconds", 1.5))) {
            return;
        }
        double power = num("grappling-hook.power", 1.0);
        Vector v = diff.normalize().multiply(Math.min(2.6, 0.5 + dist * 0.12) * power);
        v.setY(Math.max(v.getY(), 0.35) + 0.25);
        p.setVelocity(v);
        p.setFallDistance(0);
        SoundSpec.of("entity.fishing_bobber.retrieve", 1f, 0.6f).playAt(p.getLocation());
    }

    // =====================================================================================================
    // Charms (off hand) and Phoenix Feather
    // =====================================================================================================

    private void charmTick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CustomItem off = plugin.items().identify(p.getInventory().getItemInOffHand());
            if (off == null) {
                continue;
            }
            switch (off) {
                case MAGNET_CHARM -> {
                    double r = num("magnet-charm.radius", 7);
                    for (Entity e : p.getNearbyEntities(r, r, r)) {
                        if (e instanceof Item drop && drop.getPickupDelay() <= 0 && drop.canPlayerPickup()) {
                            Vector pull = p.getLocation().add(0, 0.5, 0).toVector().subtract(drop.getLocation().toVector());
                            if (pull.lengthSquared() > 0.5) {
                                drop.setVelocity(pull.normalize().multiply(0.45));
                            }
                        }
                    }
                }
                case MINER_LANTERN -> refresh(p, PotionEffectType.NIGHT_VISION, 400, 0);
                case NEBULA_ORB -> {
                    refresh(p, PotionEffectType.NIGHT_VISION, 400, 0);
                    refresh(p, PotionEffectType.REGENERATION, 60, 0);
                    if (ThreadLocalRandom.current().nextInt(4) == 0) {
                        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 3, 0.5, 0.6, 0.5, 0, VOID);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void refresh(Player p, PotionEffectType type, int duration, int amplifier) {
        PotionEffect current = p.getPotionEffect(type);
        if (current == null || current.getDuration() < duration / 2) {
            p.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || event.getFinalDamage() < p.getHealth() + p.getAbsorptionAmount()) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        if (inv.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING || inv.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL) {
            return;
        }
        for (ItemStack stack : inv.getContents()) {
            if (plugin.items().is(stack, CustomItem.PHOENIX_FEATHER)) {
                AbilitySupport.consumeOne(stack);
                event.setCancelled(true);
                p.setHealth(Math.min(12, p.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : p.getAttribute(Attribute.MAX_HEALTH).getValue()));
                p.setFireTicks(0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.4);
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.08);
                SoundSpec.of("item.totem.use", 1f, 1.2f).playAt(p.getLocation());
                plugin.messages().send(p, "abilities.phoenix");
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chaining.remove(event.getPlayer().getUniqueId());
    }
}
