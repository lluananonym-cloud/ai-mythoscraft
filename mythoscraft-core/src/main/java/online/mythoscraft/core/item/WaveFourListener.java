package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fourth item wave: golem bodyguard, light staff, climbing gloves, firework wand, gravity gauntlet, shield crystal,
 * time sand, weather orb, ore compass, mega mushroom and shrink potion.
 */
public final class WaveFourListener implements Listener {

    private static final String GOLEM_TAG = "bodyguard";
    private static final Set<Material> RARE_ORES = Set.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.ANCIENT_DEBRIS,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Map<UUID, IronGolem> golems = new HashMap<>();
    private final Map<UUID, Long> sizeUntil = new HashMap<>();
    private ConfigReader cfg;

    public WaveFourListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 30, 2, "climbing gloves", this::climbTick);
        plugin.tasks().repeat(this, 40, 20, "size reset", this::sizeTick);
    }

    private double num(String path, double def) {
        return cfg.decimal(path, def, 0, 100_000);
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
            case GOLEM_EGG -> {
                event.setCancelled(true);
                if (plugin.auctions().blockedHere(p) || !plugin.protection().query(p.getLocation(), Flag.MOB_SPAWNING)) {
                    plugin.messages().actionBar(p, "abilities.golem-blocked");
                    return;
                }
                if (support.onCooldown(p, item, "golem_egg", num("golem-egg.cooldown-seconds", 120))) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                IronGolem old = golems.remove(p.getUniqueId());
                if (old != null && old.isValid()) {
                    old.remove();
                }
                Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
                IronGolem golem = p.getWorld().spawn(at, IronGolem.class, g -> {
                    g.setPlayerCreated(true);
                    g.setPersistent(false);
                    g.customName(plugin.messages().get("abilities.golem-name", "player", p.getName()));
                    g.setCustomNameVisible(true);
                    g.getPersistentDataContainer().set(Keys.key(GOLEM_TAG), PersistentDataType.STRING, p.getUniqueId().toString());
                });
                golems.put(p.getUniqueId(), golem);
                p.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                SoundSpec.of("entity.iron_golem.repair", 1f, 0.8f).playAt(at);
                plugin.messages().actionBar(p, "abilities.golem-summoned");
                plugin.tasks().later((long) (num("golem-egg.seconds", 90) * 20), "golem expiry", () -> {
                    if (golem.isValid()) {
                        golem.getWorld().spawnParticle(Particle.CLOUD, golem.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                        golem.remove();
                    }
                    golems.remove(p.getUniqueId(), golem);
                });
            }
            case LIGHT_STAFF -> {
                event.setCancelled(true);
                RayTraceResult hit = p.rayTraceBlocks(num("light-staff.range", 20), FluidCollisionMode.NEVER);
                if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
                    plugin.messages().actionBar(p, "tools.no-target");
                    return;
                }
                Block target = hit.getHitBlock().getRelative(hit.getHitBlockFace());
                if (!target.getType().isAir() && target.getType() != Material.LIGHT) {
                    return;
                }
                if (!plugin.protection().allows(p, target, Flag.BUILD)) {
                    plugin.protection().deny(p, Flag.BUILD);
                    return;
                }
                if (support.onCooldown(p, item, "light_staff", num("light-staff.cooldown-seconds", 0.5))) {
                    return;
                }
                if (target.getType() == Material.LIGHT) {
                    target.setType(Material.AIR);
                    plugin.messages().actionBar(p, "abilities.light-removed");
                } else {
                    target.setType(Material.LIGHT);
                    plugin.messages().actionBar(p, "abilities.light-placed");
                }
                target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.5, 0.5, 0.5), 12, 0.2, 0.2, 0.2, 0.02);
                SoundSpec.of("block.amethyst_block.chime", 0.8f, 1.8f).playAt(target.getLocation());
            }
            case FIREWORK_WAND -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "firework_wand", num("firework-wand.cooldown-seconds", 2))) {
                    return;
                }
                Firework fw = p.getWorld().spawn(p.getEyeLocation(), Firework.class, f -> {
                    FireworkMeta meta = f.getFireworkMeta();
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.values()[r.nextInt(FireworkEffect.Type.values().length)])
                            .withColor(Color.fromRGB(r.nextInt(0xFFFFFF)), Color.fromRGB(r.nextInt(0xFFFFFF)))
                            .withFade(Color.WHITE).trail(true).flicker(r.nextBoolean()).build());
                    meta.setPower(1);
                    f.setFireworkMeta(meta);
                    f.setShotAtAngle(true);
                    f.setVelocity(p.getLocation().getDirection().multiply(1.2));
                    f.getPersistentDataContainer().set(Keys.key("harmless"), PersistentDataType.BYTE, (byte) 1);
                });
                if (fw.isValid()) {
                    SoundSpec.of("entity.firework_rocket.launch", 1f, 1.2f).playAt(p.getLocation());
                }
            }
            case GRAVITY_GAUNTLET -> {
                event.setCancelled(true);
                RayTraceResult hit = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), num("gravity-gauntlet.range", 20),
                        FluidCollisionMode.NEVER, true, 0.6, e -> e instanceof LivingEntity && !e.equals(p) && support.mayHurt(p, e));
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
                    plugin.messages().actionBar(p, "tools.no-target");
                    return;
                }
                if (support.onCooldown(p, item, "gravity_gauntlet", num("gravity-gauntlet.cooldown-seconds", 8))) {
                    return;
                }
                Vector pull = p.getLocation().toVector().subtract(target.getLocation().toVector());
                double dist = pull.length();
                target.setVelocity(pull.normalize().multiply(Math.min(2.4, 0.4 + dist * 0.12)).setY(0.45));
                Location eye = p.getEyeLocation();
                Vector dir = target.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
                for (double d = 0.5; d < dir.length(); d += 0.5) {
                    p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, eye.clone().add(dir.clone().normalize().multiply(d)), 2, 0.05, 0.05, 0.05, 0);
                }
                SoundSpec.of("entity.evoker.cast_spell", 1f, 1.4f).playAt(p.getLocation());
            }
            case SHIELD_CRYSTAL -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "shield_crystal", num("shield-crystal.cooldown-seconds", 90))) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 2));
                p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1, 0), 60, 0.8, 1, 0.8, 0.2);
                SoundSpec.of("block.beacon.activate", 1f, 1.5f).play(p);
                plugin.messages().actionBar(p, "abilities.shield");
            }
            case TIME_SAND -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "time_sand", 3)) {
                    return;
                }
                if (p.isPlayerTimeRelative() && p.getPlayerTimeOffset() == 0) {
                    boolean night = p.getWorld().getTime() > 12_500 && p.getWorld().getTime() < 23_500;
                    p.setPlayerTime(night ? 6_000 : 18_000, false);
                    plugin.messages().actionBar(p, night ? "abilities.time-day" : "abilities.time-night");
                } else {
                    p.resetPlayerTime();
                    plugin.messages().actionBar(p, "abilities.time-reset");
                }
                p.getWorld().spawnParticle(Particle.FALLING_DUST, p.getLocation().add(0, 2, 0), 30, 0.4, 0.2, 0.4, 0,
                        Material.SAND.createBlockData());
                SoundSpec.of("block.sand.fall", 1f, 0.6f).play(p);
            }
            case WEATHER_ORB -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "weather_orb", 3)) {
                    return;
                }
                if (p.getPlayerWeather() == null) {
                    WeatherType type2 = p.getWorld().hasStorm() ? WeatherType.CLEAR : WeatherType.DOWNFALL;
                    p.setPlayerWeather(type2);
                    plugin.messages().actionBar(p, type2 == WeatherType.CLEAR ? "abilities.weather-clear" : "abilities.weather-rain");
                } else {
                    p.resetPlayerWeather();
                    plugin.messages().actionBar(p, "abilities.weather-reset");
                }
                SoundSpec.of("weather.rain", 0.6f, 1.4f).play(p);
            }
            case ORE_COMPASS -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "ore_compass", num("ore-compass.cooldown-seconds", 10))) {
                    return;
                }
                int r = (int) num("ore-compass.radius", 16);
                Block best = null;
                double bestDist = Double.MAX_VALUE;
                Block origin = p.getLocation().getBlock();
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            Block b = origin.getRelative(dx, dy, dz);
                            if (RARE_ORES.contains(b.getType())) {
                                double d = dx * dx + dy * dy + dz * dz;
                                if (d < bestDist) {
                                    bestDist = d;
                                    best = b;
                                }
                            }
                        }
                    }
                }
                if (best == null) {
                    plugin.messages().actionBar(p, "abilities.ore-none", "radius", r);
                    return;
                }
                Location eye = p.getEyeLocation();
                Vector dir = best.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector()).normalize();
                for (int i = 1; i <= 8; i++) {
                    p.spawnParticle(Particle.WAX_OFF, eye.clone().add(dir.clone().multiply(i * 0.6)), 2, 0.02, 0.02, 0.02, 0);
                }
                plugin.messages().actionBar(p, "abilities.ore-found", "ore", net.kyori.adventure.text.Component.translatable(best.getType().translationKey()),
                        "distance", Math.round(Math.sqrt(bestDist)));
                SoundSpec.of("block.note_block.bell", 0.8f, 1.6f).play(p);
            }
            case MEGA_MUSHROOM -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "mega_mushroom", num("mega-mushroom.cooldown-seconds", 60))) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                resize(p, num("mega-mushroom.scale", 1.6), (long) num("mega-mushroom.seconds", 30));
                SoundSpec.of("entity.player.levelup", 1f, 0.5f).playAt(p.getLocation());
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.items().identify(event.getItem()) == CustomItem.SHRINK_POTION) {
            resize(event.getPlayer(), num("shrink-potion.scale", 0.5), (long) num("shrink-potion.seconds", 30));
            SoundSpec.of("entity.player.levelup", 1f, 2f).playAt(event.getPlayer().getLocation());
        }
    }

    // ---- size changes (scale attribute) -------------------------------------------------------------------

    private void resize(Player p, double scale, long seconds) {
        AttributeInstance inst = p.getAttribute(Attribute.SCALE);
        if (inst == null) {
            return;
        }
        inst.removeModifier(Keys.key("size_item"));
        inst.addTransientModifier(new AttributeModifier(Keys.key("size_item"), scale - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.ANY));
        sizeUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000);
        p.getWorld().spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
        plugin.messages().actionBar(p, scale > 1 ? "abilities.size-big" : "abilities.size-small", "seconds", seconds);
    }

    private void resetSize(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.SCALE);
        if (inst != null) {
            inst.removeModifier(Keys.key("size_item"));
        }
        sizeUntil.remove(p.getUniqueId());
    }

    private void sizeTick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : Map.copyOf(sizeUntil).entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null) {
                sizeUntil.remove(e.getKey());
            } else if (e.getValue() <= now) {
                resetSize(p);
                p.getWorld().spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.03);
                plugin.messages().actionBar(p, "abilities.size-normal");
            }
        }
    }

    // ---- climbing gloves ----------------------------------------------------------------------------------

    private void climbTick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.isSneaking() || p.isFlying() || p.isInsideVehicle()
                    || !plugin.items().is(p.getInventory().getItemInOffHand(), CustomItem.CLIMBING_GLOVES)) {
                continue;
            }
            BlockFace face = p.getFacing();
            Block front = p.getLocation().getBlock().getRelative(face);
            Block frontHead = front.getRelative(BlockFace.UP);
            if (front.getType().isSolid() || frontHead.getType().isSolid()) {
                p.setVelocity(new Vector(p.getVelocity().getX() * 0.2, 0.22, p.getVelocity().getZ() * 0.2));
                p.setFallDistance(0);
                if (ThreadLocalRandom.current().nextInt(4) == 0) {
                    p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0,
                            front.getType().isSolid() ? front.getBlockData() : frontHead.getBlockData());
                }
            }
        }
    }

    // ---- golem + harmless fireworks -----------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onGolemTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) {
            return;
        }
        String owner = golem.getPersistentDataContainer().get(Keys.key(GOLEM_TAG), PersistentDataType.STRING);
        if (owner != null && event.getTarget() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner) || !(event.getDamager() instanceof Mob attacker) || attacker instanceof IronGolem) {
            return;
        }
        IronGolem golem = golems.get(owner.getUniqueId());
        if (golem != null && golem.isValid() && golem.getWorld().equals(owner.getWorld())) {
            golem.setTarget(attacker);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw && fw.getPersistentDataContainer().has(Keys.key("harmless"), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGolemFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof IronGolem g && g.getPersistentDataContainer().has(Keys.key(GOLEM_TAG), PersistentDataType.STRING)
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        resetSize(p);
        IronGolem g = golems.remove(p.getUniqueId());
        if (g != null && g.isValid()) {
            g.remove();
        }
    }

    public void shutdown() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            resetSize(p);
        }
        for (Entity g : golems.values()) {
            if (g.isValid()) {
                g.remove();
            }
        }
        golems.clear();
    }
}
