package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.attraction.MusicService;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abilities of the millennium (pharaohs), space, spawner, Schauernacht chainsaw and Frostfest extras – including
 * planets that orbit around their owner and the rocket booster with durability.
 */
public final class WaveEightListener implements Listener {

    private static final String P = "projectile";

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Map<UUID, ItemDisplay> planets = new HashMap<>();
    private final Map<UUID, Long> chainsawUntil = new HashMap<>();
    private ConfigReader cfg;
    private double orbit;

    public WaveEightListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 2, "planets", this::planets);
        plugin.tasks().repeat(this, 30, 20, "charms wave 8", this::charms);
    }

    private double num(String path, double def) {
        return cfg.decimal(path, def, 0, 100_000);
    }

    private static void refresh(Player p, PotionEffectType type, int amp) {
        PotionEffect e = p.getPotionEffect(type);
        if (e == null || e.getDuration() < 60) {
            p.addPotionEffect(new PotionEffect(type, 120, amp, true, false, true));
        }
    }

    // ---- planets ---------------------------------------------------------------------------------------------

    private static boolean planet(CustomItem c) {
        return c == CustomItem.MARS_ORB || c == CustomItem.MOON_ORB || c == CustomItem.SATURN_ORB || c == CustomItem.EARTH_ORB;
    }

    /** The planet in the off hand flies in a circle around its owner. */
    private void planets() {
        orbit += 0.12;
        Set<UUID> seen = new HashSet<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CustomItem off = plugin.items().identify(p.getInventory().getItemInOffHand());
            if (off == null || !planet(off) || p.isDead()) {
                continue;
            }
            seen.add(p.getUniqueId());
            Location at = p.getLocation().add(Math.cos(orbit) * 1.4, 1.9 + Math.sin(orbit * 2) * 0.15, Math.sin(orbit) * 1.4);
            ItemDisplay d = planets.get(p.getUniqueId());
            String model = off.id();
            if (d == null || !d.isValid() || !d.getWorld().equals(p.getWorld()) || !model.equals(d.getPersistentDataContainer()
                    .get(Keys.ENTITY, PersistentDataType.STRING))) {
                if (d != null && d.isValid()) {
                    d.remove();
                }
                ItemStack icon = plugin.items().create(off);
                d = p.getWorld().spawn(at, ItemDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setItemStack(icon);
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                    e.setBillboard(Display.Billboard.CENTER);
                    e.setTeleportDuration(2);
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
                    e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, model);
                });
                planets.put(p.getUniqueId(), d);
            } else {
                d.teleport(at);
            }
        }
        planets.entrySet().removeIf(e -> {
            if (!seen.contains(e.getKey())) {
                if (e.getValue().isValid()) {
                    e.getValue().remove();
                }
                return true;
            }
            return false;
        });
    }

    private void charms() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CustomItem off = plugin.items().identify(p.getInventory().getItemInOffHand());
            if (off == null) {
                continue;
            }
            switch (off) {
                case MARS_ORB -> refresh(p, PotionEffectType.STRENGTH, 0);
                case MOON_ORB -> {
                    refresh(p, PotionEffectType.JUMP_BOOST, 1);
                    refresh(p, PotionEffectType.SLOW_FALLING, 0);
                }
                case SATURN_ORB -> refresh(p, PotionEffectType.RESISTANCE, 0);
                case EARTH_ORB -> refresh(p, PotionEffectType.REGENERATION, 0);
                case SCARAB_CHARM -> {
                    refresh(p, PotionEffectType.LUCK, 0);
                    refresh(p, PotionEffectType.HASTE, 0);
                }
                case MISTLETOE -> {
                    for (Player other : p.getWorld().getPlayers()) {
                        if (other.getLocation().distanceSquared(p.getLocation()) <= 36) {
                            refresh(other, PotionEffectType.REGENERATION, 0);
                        }
                    }
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.3, 0), 1, 0.3, 0.1, 0.3, 0);
                }
                default -> {
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ItemDisplay d = planets.remove(event.getPlayer().getUniqueId());
        if (d != null && d.isValid()) {
            d.remove();
        }
        chainsawUntil.remove(event.getPlayer().getUniqueId());
    }

    // ---- right click -----------------------------------------------------------------------------------------

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
            case ROCKET_BOOSTER -> {
                event.setCancelled(true);
                if (!p.isGliding()) {
                    plugin.messages().actionBar(p, "abilities.rocket-glide");
                    return;
                }
                if (support.onCooldown(p, item, "rocket_booster", 0.6)) {
                    return;
                }
                Vector dir = p.getLocation().getDirection();
                p.setVelocity(p.getVelocity().add(dir.multiply(num("rocket-booster.power", 1.6))));
                p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 20, 0.2, 0.2, 0.2, 0.05);
                SoundSpec.of("entity.firework_rocket.launch", 1f, 1f).playAt(p.getLocation());
                wear(p, item, (int) num("rocket-booster.uses", 48), 1);
            }
            case LASER_GUN -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "laser_gun", num("laser-gun.cooldown-seconds", 3))) {
                    return;
                }
                RayTraceResult hit = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 32, FluidCollisionMode.NEVER,
                        true, 0.4, e -> e instanceof LivingEntity && !e.equals(p) && support.mayHurt(p, e));
                double length = hit == null ? 32 : hit.getHitPosition().distance(p.getEyeLocation().toVector());
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                for (double d = 0.6; d < length; d += 0.35) {
                    p.getWorld().spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(0xEF4444), 0.9f));
                }
                SoundSpec.of("block.beacon.deactivate", 0.8f, 2f).playAt(p.getLocation());
                if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                    target.damage(num("laser-gun.damage", 6), p);
                    target.setFireTicks(40);
                }
            }
            case STAR_DUST -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                p.giveExp((int) num("star-dust.xp", 400));
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
                SoundSpec.of("block.amethyst_block.chime", 1f, 1.4f).play(p);
            }
            case PHARAOH_STAFF -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "pharaoh_staff", num("pharaoh-staff.cooldown-seconds", 20))) {
                    return;
                }
                for (Entity e : p.getNearbyEntities(7, 4, 7)) {
                    if (e instanceof LivingEntity le && support.mayHurt(p, le)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        le.damage(num("pharaoh-staff.damage", 3), p);
                    }
                }
                p.getWorld().spawnParticle(Particle.FALLING_DUST, p.getLocation().add(0, 1.5, 0), 160, 4, 1.5, 4, 0,
                        Material.SAND.createBlockData());
                SoundSpec.of("entity.husk.ambient", 1f, 0.6f).playAt(p.getLocation());
            }
            case MUMMY_WRAP -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "mummy_wrap", 10)) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                        : p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                p.setHealth(Math.min(max, p.getHealth() + 8));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
                SoundSpec.of("item.armor.equip_leather", 1f, 0.8f).play(p);
            }
            case PHARAOH_TREASURE -> {
                event.setCancelled(true);
                PlayerData data = plugin.playerData().get(p);
                if (data == null) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                long coins = ThreadLocalRandom.current().nextLong((long) num("pharaoh-treasure.min", 500), (long) num("pharaoh-treasure.max", 3000) + 1);
                long got = plugin.economy().earn(data, Currency.COINS, coins, false);
                plugin.playerData().save(data);
                plugin.messages().send(p, "abilities.treasure-coins", "coins", got);
                p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0);
                SoundSpec.of("entity.player.levelup", 0.8f, 1.6f).play(p);
            }
            case SUN_DISC -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "sun_disc", num("sun-disc.cooldown-seconds", 120))) {
                    return;
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0));
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.8, 1, 0.8, 0.02);
                SoundSpec.of("block.beacon.power_select", 1f, 1.3f).play(p);
            }
            case CHAINSAW -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "chainsaw", 8)) {
                    return;
                }
                chainsawUntil.put(p.getUniqueId(), System.currentTimeMillis() + 6000);
                plugin.messages().actionBar(p, "abilities.chainsaw");
                for (int i = 0; i < 6; i++) {
                    plugin.tasks().later(1 + i * 4, "chainsaw rev", () -> {
                        if (p.isOnline()) {
                            SoundSpec.of("entity.bee.loop_aggressive", 1f, 0.6f).playAt(p.getLocation());
                            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.02);
                        }
                    });
                }
            }
            case SNOW_GLOBE -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "snow_globe", num("snow-globe.cooldown-seconds", 30))) {
                    return;
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 300, 1));
                for (int i = 0; i < 10; i++) {
                    plugin.tasks().later(1 + i * 6, "snow globe", () -> {
                        if (p.isOnline()) {
                            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 4, 0), 60, 4, 1, 4, 0.02);
                        }
                    });
                }
                SoundSpec.of("block.amethyst_block.resonate", 1f, 1.6f).play(p);
            }
            case PRESENT_LAUNCHER -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "present_launcher", num("present-launcher.cooldown-seconds", 2))) {
                    return;
                }
                Snowball ball = p.launchProjectile(Snowball.class, p.getLocation().getDirection().multiply(1.6));
                ball.setItem(plugin.items().create(CustomItem.GIFT_BOX));
                ball.getPersistentDataContainer().set(Keys.key(P), PersistentDataType.STRING, "present");
                SoundSpec.of("entity.snowball.throw", 1f, 0.8f).playAt(p.getLocation());
            }
            case REINDEER_BELL -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "reindeer_bell", num("reindeer-bell.cooldown-seconds", 60))) {
                    return;
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
                MusicService.sleighBells(plugin, p);
            }
            default -> {
            }
        }
    }

    /** Uses up durability of an item with a durability bar (rocket booster); breaks it when empty. */
    private void wear(Player p, ItemStack item, int maxUses, int amount) {
        item.editMeta(Damageable.class, meta -> {
            if (!meta.hasMaxDamage()) {
                meta.setMaxDamage(maxUses);
            }
            meta.setDamage(meta.getDamage() + amount);
        });
        Damageable meta = (Damageable) item.getItemMeta();
        if (meta.getDamage() >= meta.getMaxDamage()) {
            item.setAmount(0);
            SoundSpec.of("entity.item.break", 1f, 1f).play(p);
        }
    }

    // ---- consumables -----------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        CustomItem type = plugin.items().identify(event.getItem());
        Player p = event.getPlayer();
        if (type == CustomItem.GINGERBREAD) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
        } else if (type == CustomItem.EGGNOG) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
        }
    }

    // ---- combat ----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getEntity() instanceof LivingEntity le)) {
            return;
        }
        CustomItem type = plugin.items().identify(p.getInventory().getItemInMainHand());
        if (type == CustomItem.KHOPESH && ThreadLocalRandom.current().nextDouble() < 0.25) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            le.getWorld().spawnParticle(Particle.FALLING_DUST, le.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0,
                    Material.SAND.createBlockData());
        } else if (type == CustomItem.NUTCRACKER_SWORD && ThreadLocalRandom.current().nextDouble() < 0.2) {
            event.setDamage(event.getDamage() + 4);
            SoundSpec.of("block.wood.break", 1f, 1.6f).playAt(le.getLocation());
        } else if (type == CustomItem.CHAINSAW && chainsawUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            event.setDamage(event.getDamage() * 1.5);
            le.getWorld().spawnParticle(Particle.BLOCK, le.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0,
                    Material.REDSTONE_BLOCK.createBlockData());
        }
    }

    /** Ankh: saves you from a deadly hit once (it crumbles). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        ItemStack[] contents = p.getInventory().getContents();
        for (ItemStack s : contents) {
            if (plugin.items().is(s, CustomItem.ANKH)) {
                event.setCancelled(true);
                s.setAmount(s.getAmount() - 1);
                p.setHealth(Math.min(10, p.getHealth() + 10));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 400, 0));
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.4);
                SoundSpec.of("item.totem.use", 1f, 1.2f).playAt(p.getLocation());
                plugin.messages().send(p, "abilities.ankh");
                return;
            }
        }
    }

    // ---- projectiles -----------------------------------------------------------------------------------------

    @EventHandler
    public void onProjectile(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter) || !(event.getEntity() instanceof Snowball ball)) {
            return;
        }
        Location at = ball.getLocation();
        World w = at.getWorld();
        if ("present".equals(ball.getPersistentDataContainer().get(Keys.key(P), PersistentDataType.STRING))) {
            w.spawnParticle(Particle.FIREWORK, at, 30, 0.4, 0.4, 0.4, 0.15);
            w.spawnParticle(Particle.BLOCK, at, 20, 0.3, 0.3, 0.3, 0, Material.RED_WOOL.createBlockData());
            SoundSpec.of("entity.firework_rocket.twinkle", 0.8f, 1.2f).playAt(at);
            if (event.getHitEntity() instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                le.setVelocity(le.getVelocity().add(ball.getVelocity().normalize().multiply(0.8).setY(0.4)));
            }
            return;
        }
        CustomItem thrown = plugin.items().identify(ball.getItem());
        if (thrown == CustomItem.SAND_BOMB) {
            w.spawnParticle(Particle.FALLING_DUST, at, 80, 1.5, 1, 1.5, 0, Material.SAND.createBlockData());
            for (Entity e : w.getNearbyEntities(at, 3, 3, 3)) {
                if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                }
            }
        } else if (thrown == CustomItem.ORNAMENT_BOMB) {
            w.spawnParticle(Particle.FIREWORK, at, 40, 1, 0.6, 1, 0.1);
            w.spawnParticle(Particle.WAX_ON, at, 20, 1, 0.6, 1, 0);
            SoundSpec.of("block.glass.break", 1f, 1.4f).playAt(at);
            for (Entity e : w.getNearbyEntities(at, 3, 3, 3)) {
                if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    le.damage(num("ornament-bomb.damage", 3), shooter);
                }
            }
        }
    }

    // ---- spawners and chainsaw felling -------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block b = event.getBlock();
        ItemStack tool = p.getInventory().getItemInMainHand();
        CustomItem type = plugin.items().identify(tool);
        if (b.getType() == Material.SPAWNER && type == CustomItem.SPAWNER_PICKAXE && b.getState() instanceof CreatureSpawner spawner) {
            EntityType mob = spawner.getSpawnedType() == null ? EntityType.PIG : spawner.getSpawnedType();
            event.setExpToDrop(0);
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), SpawnerItems.create(mob, 1));
            b.getWorld().spawnParticle(Particle.WITCH, b.getLocation().add(0.5, 0.5, 0.5), 30, 0.4, 0.4, 0.4, 0.1);
            SoundSpec.of("block.respawn_anchor.deplete", 1f, 1.2f).playAt(b.getLocation());
            wearTool(p, tool, (int) num("spawner-pickaxe.durability-per-spawner", 400));
            return;
        }
        if (type == CustomItem.CHAINSAW && chainsawUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()
                && Tag.LOGS.isTagged(b.getType())) {
            fell(p, b);
        }
    }

    private void wearTool(Player p, ItemStack tool, int amount) {
        tool.editMeta(Damageable.class, meta -> meta.setDamage(meta.getDamage() + amount));
        Damageable meta = (Damageable) tool.getItemMeta();
        if (meta.getDamage() >= tool.getType().getMaxDurability()) {
            tool.setAmount(0);
            SoundSpec.of("entity.item.break", 1f, 1f).play(p);
        }
    }

    /** Fells the connected logs above (max 128) – only while the chainsaw is revved. */
    private void fell(Player p, Block start) {
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        queue.add(start);
        int felled = 0;
        while (!queue.isEmpty() && felled < 128) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (!n.equals(start) && Tag.LOGS.isTagged(n.getType()) && seen.add(n)
                                && plugin.protection().allows(p, n.getLocation(), online.mythoscraft.core.protection.Flag.BUILD)) {
                            queue.add(n);
                            n.breakNaturally(p.getInventory().getItemInMainHand());
                            felled++;
                        }
                    }
                }
            }
        }
        if (felled > 0) {
            SoundSpec.of("entity.zombie.break_wooden_door", 0.8f, 1.3f).playAt(start.getLocation());
        }
    }

    /** Spawners from crates / the spawner pickaxe keep their mob when placed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        EntityType mob = SpawnerItems.typeOf(event.getItemInHand());
        if (mob == null || event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }
        if (event.getBlockPlaced().getState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(mob);
            spawner.update(true, false);
        }
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        planets.values().forEach(d -> {
            if (d.isValid()) {
                d.remove();
            }
        });
        planets.clear();
    }
}
