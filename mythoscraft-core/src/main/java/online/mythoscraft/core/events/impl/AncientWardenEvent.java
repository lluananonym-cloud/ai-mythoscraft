package online.mythoscraft.core.events.impl;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.EventState;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.events.SurfaceFinder;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.protection.DamageSources;
import online.mythoscraft.core.util.Effects;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss fight against an empowered Warden with its own health pool (not limited by the vanilla 1024 cap), phases,
 * four special attacks, minions and exact damage tracking. Damage is counted after armour and capped at the
 * remaining health, so overkill hits can't inflate the ranking.
 */
public final class AncientWardenEvent extends MythosEvent {

    private static final NamespacedKey BOSS = Keys.key("boss");
    private static final Particle.DustOptions SCULK = new Particle.DustOptions(Color.fromRGB(0x0E7C86), 1.8f);

    private String fixedLocation = "";
    private double spawnMin = 16;
    private double spawnMax = 32;
    private double maxHealth = 1500;
    private double attackDamage = 18;
    private double armor = 12;
    private double speed = 0.32;
    private double scale = 1.6;
    private double followRange = 48;
    private int abilityInterval = 8;
    private int enragedInterval = 5;
    private double enrageThreshold = 0.3;
    private int minionCount = 3;
    private int maxMinions = 6;
    private double minionHealth = 24;
    private int despawnSeconds = 180;
    private double bladeChance = 0.35;
    private int droppedXp = 500;
    private double shockDamage = 7;
    private double slamDamage = 6;

    private Location override;
    private Warden boss;
    private double health;
    private boolean killed;
    private boolean enraged;
    private int abilityTimer;
    private int lonelySeconds;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;
    private World chunkWorld;
    private final List<LivingEntity> minions = new ArrayList<>();
    private final List<BukkitRunnable> abilities = new ArrayList<>();

    public AncientWardenEvent(MythosCraft plugin) {
        super(plugin, "ancientwarden");
        barColor = BossBar.Color.BLUE;
    }

    @Override
    protected int defaultDuration() {
        return 900;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        fixedLocation = cfg.string("location", "");
        spawnMin = cfg.decimal("spawn-distance.min", 16, 4, 200);
        spawnMax = Math.max(spawnMin + 1, cfg.decimal("spawn-distance.max", 32, 5, 400));
        maxHealth = cfg.decimal("health", 1500, 50, 1_000_000);
        attackDamage = cfg.decimal("damage", 18, 0, 500);
        armor = cfg.decimal("armor", 12, 0, 30);
        speed = cfg.decimal("speed", 0.32, 0.05, 1.0);
        scale = cfg.decimal("scale", 1.6, 0.5, 4.0);
        followRange = cfg.decimal("follow-range", 48, 8, 128);
        abilityInterval = cfg.integer("ability-interval-seconds", 8, 2, 120);
        enragedInterval = cfg.integer("enraged-interval-seconds", 5, 1, 120);
        enrageThreshold = cfg.decimal("enrage-threshold", 0.3, 0, 1);
        minionCount = cfg.integer("minions.count", 3, 0, 20);
        maxMinions = cfg.integer("minions.max-alive", 6, 0, 50);
        minionHealth = cfg.decimal("minions.health", 24, 1, 1000);
        despawnSeconds = cfg.integer("despawn-without-players-seconds", 180, 30, 3600);
        bladeChance = cfg.decimal("loot.warden-blade-chance", 0.35, 0, 1);
        droppedXp = cfg.integer("loot.xp", 500, 0, 100_000);
        shockDamage = cfg.decimal("abilities.shockwave-damage", 7, 0, 200);
        slamDamage = cfg.decimal("abilities.slam-damage", 6, 0, 200);
    }

    /** Next start spawns the boss here (admin command). */
    public void overrideLocation(Location location) {
        this.override = location;
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.damage";
    }

    @Override
    protected String resultsHeaderKey() {
        return killed ? "events.ancientwarden.defeated" : "events.ancientwarden.escaped";
    }

    @Override
    protected boolean grantRewards() {
        return killed;
    }

    // ---- spawning -----------------------------------------------------------------------------------------

    @Override
    protected void onStart() {
        killed = false;
        enraged = false;
        health = maxHealth;
        lonelySeconds = 0;
        abilityTimer = abilityInterval;
        Location loc = override != null ? override : LocationCodec.fromString(fixedLocation.replace(',', ';'));
        override = null;
        if (loc == null && venue != null) {
            loc = venue.clone().add(0, 0.5, 0);
        }
        if (loc != null) {
            spawnBoss(loc);
            return;
        }
        List<Player> players = participants();
        if (players.isEmpty()) {
            throw new IllegalStateException("no eligible players for the boss");
        }
        Player anchor = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        SurfaceFinder.find(plugin, anchor.getWorld(), anchor.getX(), anchor.getZ(), spawnMin, spawnMax, 15,
                b -> plugin.arenas().arenaAt(b.getLocation()) == null).thenAccept(found -> {
            if (state() != EventState.RUNNING) {
                return;
            }
            if (found == null) {
                plugin.getLogger().warning("Ancient Warden: no spawn location found – stopping");
                finish(true);
                return;
            }
            spawnBoss(found);
        });
    }

    private void spawnBoss(Location loc) {
        World world = loc.getWorld();
        world.strikeLightningEffect(loc);
        world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0), 80, 1.5, 1.5, 1.5, 0.05, null, true);
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0, null, true);
        SoundSpec.of("entity.warden.emerge", 3f, 0.8f).playAt(loc);
        boss = world.spawn(loc, Warden.class, w -> {
            w.customName(plugin.messages().get("events.ancientwarden.boss-name"));
            w.setCustomNameVisible(true);
            w.setPersistent(false);
            w.setRemoveWhenFarAway(false);
            w.getPersistentDataContainer().set(BOSS, PersistentDataType.STRING, session);
            attribute(w, Attribute.MAX_HEALTH, 1024);
            attribute(w, Attribute.ATTACK_DAMAGE, attackDamage);
            attribute(w, Attribute.ARMOR, armor);
            attribute(w, Attribute.MOVEMENT_SPEED, speed);
            attribute(w, Attribute.FOLLOW_RANGE, followRange);
            attribute(w, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            attribute(w, Attribute.SCALE, scale);
            w.setHealth(1024);
        });
        plugin.messages().broadcast("events.ancientwarden.spawned", "x", loc.getBlockX(), "y", loc.getBlockY(),
                "z", loc.getBlockZ(), "world", world.getName());
        keepChunkLoaded();
    }

    private static void attribute(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    private void keepChunkLoaded() {
        if (boss == null || !boss.isValid()) {
            return;
        }
        Location l = boss.getLocation();
        int cx = l.getBlockX() >> 4;
        int cz = l.getBlockZ() >> 4;
        if (cx == chunkX && cz == chunkZ && l.getWorld().equals(chunkWorld)) {
            return;
        }
        releaseChunk();
        chunkWorld = l.getWorld();
        chunkX = cx;
        chunkZ = cz;
        chunkWorld.addPluginChunkTicket(cx, cz, plugin);
    }

    private void releaseChunk() {
        if (chunkWorld != null) {
            chunkWorld.removePluginChunkTicket(chunkX, chunkZ, plugin);
            chunkWorld = null;
        }
    }

    // ---- fight loop ---------------------------------------------------------------------------------------

    @Override
    protected void onTick() {
        if (boss == null) {
            return;
        }
        if (!boss.isValid()) {
            if (!killed) {
                plugin.messages().broadcast("events.ancientwarden.vanished");
                finish(true);
            }
            return;
        }
        keepChunkLoaded();
        minions.removeIf(m -> !m.isValid());
        Player target = nearestPlayer(followRange);
        if (target == null) {
            if (++lonelySeconds >= despawnSeconds) {
                finish(false);
            }
            return;
        }
        lonelySeconds = 0;
        boss.setAnger(target, 150);
        boss.setTarget(target);
        if (!enraged && health / maxHealth <= enrageThreshold) {
            enrage();
        }
        boss.getWorld().spawnParticle(Particle.SCULK_SOUL, boss.getLocation().add(0, 2, 0), enraged ? 6 : 3, 0.6, 0.8, 0.6, 0.02);
        if (--abilityTimer <= 0) {
            abilityTimer = enraged ? enragedInterval : abilityInterval;
            useAbility();
        }
    }

    private Player nearestPlayer(double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player p : boss.getWorld().getPlayers()) {
            if (!eligible(p) || p.isDead()) {
                continue;
            }
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private void enrage() {
        enraged = true;
        if (bossBar() != null) {
            bossBar().color(BossBar.Color.RED);
        }
        attribute(boss, Attribute.MOVEMENT_SPEED, speed * 1.25);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 3600, 0, false, false));
        plugin.messages().broadcast("events.ancientwarden.enraged");
        SoundSpec.of("entity.warden.roar", 4f, 0.6f).playAt(boss.getLocation());
    }

    private void useAbility() {
        int roll = ThreadLocalRandom.current().nextInt(minionCount > 0 ? 4 : 3);
        switch (roll) {
            case 0 -> shockwave();
            case 1 -> darknessPulse();
            case 2 -> groundSlam();
            default -> summonEchoes();
        }
    }

    /** Ring of sound that expands outwards and knocks players back. */
    private void shockwave() {
        Location center = boss.getLocation().clone();
        World world = center.getWorld();
        SoundSpec.of("entity.warden.sonic_charge", 3f, 0.8f).playAt(center);
        Set<UUID> hit = new HashSet<>();
        BukkitRunnable task = new BukkitRunnable() {
            double radius = 1;

            @Override
            public void run() {
                radius += 1.2;
                int points = (int) (radius * 6);
                for (int i = 0; i < points; i++) {
                    double a = i * Math.PI * 2 / points;
                    world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0.4, Math.sin(a) * radius), 1, 0, 0, 0, 0, SCULK, true);
                }
                world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                for (Player p : world.getPlayers()) {
                    double dx = p.getX() - center.getX();
                    double dz = p.getZ() - center.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(dist - radius) < 1.3 && Math.abs(p.getY() - center.getY()) < 3 && hit.add(p.getUniqueId()) && eligible(p)) {
                        p.damage(shockDamage, boss);
                        Vector push = new Vector(dx, 0, dz).normalize().multiply(1.4).setY(0.5);
                        p.setVelocity(push);
                    }
                }
                if (radius >= 13 || boss == null || !boss.isValid()) {
                    cancel();
                }
            }
        };
        abilities.add(task);
        task.runTaskTimer(plugin, 10, 2);
    }

    /** Blinds everyone nearby with darkness and slows them down. */
    private void darknessPulse() {
        Location center = boss.getLocation();
        SoundSpec.of("entity.warden.roar", 3f, 0.9f).playAt(center);
        center.getWorld().spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 1.5, 0), 120, 6, 2, 6, 0.04, null, true);
        for (Player p : center.getWorld().getPlayers()) {
            if (eligible(p) && p.getLocation().distanceSquared(center) < 20 * 20) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 8, 0, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 3, 1, false, true));
                plugin.messages().actionBar(p, "events.ancientwarden.darkness");
            }
        }
    }

    /** Leaps up and slams down, throwing nearby players into the air. */
    private void groundSlam() {
        boss.setVelocity(new Vector(0, 1.15, 0));
        SoundSpec.of("entity.warden.attack_impact", 3f, 0.6f).playAt(boss.getLocation());
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (boss == null || !boss.isValid()) {
                    return;
                }
                Location c = boss.getLocation();
                World w = c.getWorld();
                w.spawnParticle(Particle.BLOCK, c, 160, 4, 0.2, 4, 0.2, Material.SCULK.createBlockData(), true);
                w.spawnParticle(Particle.EXPLOSION, c, 6, 2.5, 0.2, 2.5, 0, null, true);
                SoundSpec.of("entity.generic.explode", 2.5f, 0.6f).playAt(c);
                for (Player p : w.getPlayers()) {
                    if (eligible(p) && p.getLocation().distanceSquared(c) < 8 * 8) {
                        p.damage(slamDamage, boss);
                        p.setVelocity(p.getVelocity().add(new Vector(0, 0.95, 0)));
                    }
                }
            }
        };
        abilities.add(task);
        task.runTaskLater(plugin, 18);
    }

    /** Calls sculk echoes (armoured zombies) to fight for the warden. */
    private void summonEchoes() {
        if (minions.size() >= maxMinions) {
            darknessPulse();
            return;
        }
        Location c = boss.getLocation();
        SoundSpec.of("block.sculk_shrieker.shriek", 3f, 0.8f).playAt(c);
        int count = Math.min(minionCount, maxMinions - minions.size());
        for (int i = 0; i < count; i++) {
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location at = c.clone().add(Math.cos(a) * 3, 0.5, Math.sin(a) * 3);
            if (!at.getBlock().isPassable()) {
                at = c.clone().add(0, 0.5, 0);
            }
            Zombie echo = c.getWorld().spawn(at, Zombie.class, z -> {
                z.setAdult();
                z.customName(plugin.messages().get("events.ancientwarden.minion-name"));
                z.setCustomNameVisible(true);
                z.setPersistent(false);
                z.setCanPickupItems(false);
                z.setShouldBurnInDay(false);
                z.getPersistentDataContainer().set(BOSS, PersistentDataType.STRING, session + ":minion");
                attribute(z, Attribute.MAX_HEALTH, minionHealth);
                z.setHealth(minionHealth);
                attribute(z, Attribute.MOVEMENT_SPEED, 0.3);
                EntityEquipment eq = z.getEquipment();
                eq.setHelmet(dyed(Material.LEATHER_HELMET));
                eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE));
                eq.setLeggings(dyed(Material.LEATHER_LEGGINGS));
                eq.setBoots(dyed(Material.LEATHER_BOOTS));
                eq.setHelmetDropChance(0);
                eq.setChestplateDropChance(0);
                eq.setLeggingsDropChance(0);
                eq.setBootsDropChance(0);
                eq.setItemInMainHandDropChance(0);
            });
            Player target = nearestPlayer(followRange);
            if (target != null) {
                echo.setTarget(target);
            }
            c.getWorld().spawnParticle(Particle.SCULK_SOUL, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.03);
            minions.add(echo);
        }
        plugin.messages().broadcast("events.ancientwarden.summon");
    }

    private static ItemStack dyed(Material leather) {
        ItemStack item = new ItemStack(leather);
        item.editMeta(org.bukkit.inventory.meta.LeatherArmorMeta.class, m -> m.setColor(Color.fromRGB(0x0B4F57)));
        return item;
    }

    // ---- damage & death -----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        if (boss == null || !event.getEntity().equals(boss) || killed) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION || event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
            return;
        }
        double dealt = Math.min(event.getFinalDamage(), health);
        if (dealt <= 0) {
            return;
        }
        Player attacker = DamageSources.attacker(event);
        if (attacker != null && attacker.isOnline()) {
            addScore(attacker, dealt);
            plugin.messages().actionBar(attacker, "events.ancientwarden.damage-dealt",
                    "damage", Numbers.formatDecimal(Math.round(dealt * 10) / 10.0), "total", Math.round(score(attacker.getUniqueId())));
        }
        health -= dealt;
        if (health <= 0.0001) {
            health = 0;
            killed = true;
            event.setDamage(10_000);
        } else {
            event.setDamage(0.0001);
            boss.setHealth(1024);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        String tag = event.getEntity().getPersistentDataContainer().get(BOSS, PersistentDataType.STRING);
        if (tag == null || !tag.startsWith(session)) {
            return;
        }
        event.getDrops().clear();
        if (tag.endsWith(":minion")) {
            event.setDroppedExp(5);
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                addScore(killer, minionHealth);
            }
            return;
        }
        if (!killed) {
            // killed by /kill or similar – still treat as a win for the damage dealers
            killed = true;
        }
        event.setDroppedExp(droppedXp);
        Location at = event.getEntity().getLocation();
        at.getWorld().spawnParticle(Particle.SCULK_SOUL, at.clone().add(0, 1.5, 0), 200, 1.5, 2, 1.5, 0.08, null, true);
        Effects.burst(at.clone().add(0, 2, 0), Color.fromRGB(0x0E7C86), Color.fromRGB(0x3FE0D0), true);
        SoundSpec.of("entity.warden.death", 4f, 0.7f).playAt(at);
        boss = null;
        if (state() == EventState.RUNNING) {
            finish(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTargetMinion(EntityTargetEvent event) {
        if (boss != null && event.getEntity().equals(boss) && event.getTarget() != null && minions.contains(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void onEnd(boolean aborted) {
        if (aborted || !killed) {
            return;
        }
        List<Map.Entry<UUID, Double>> ranking = ranking();
        if (!ranking.isEmpty() && ThreadLocalRandom.current().nextDouble() < bladeChance) {
            Player top = Bukkit.getPlayer(ranking.get(0).getKey());
            if (top != null) {
                CustomItemManager.give(top, plugin.items().create(CustomItem.WARDEN_BLADE));
                plugin.messages().broadcast("events.ancientwarden.blade-drop", "player", top.getName());
            }
        }
        for (Map.Entry<UUID, Double> e : ranking) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && plugin.playerData().get(p) != null) {
                plugin.playerData().get(p).addBossDamage(Math.round(e.getValue()));
            }
        }
    }

    @Override
    protected Component bossBarText() {
        if (boss == null) {
            return plugin.messages().get("events.ancientwarden.bossbar-searching", "event", displayName());
        }
        return plugin.messages().get(enraged ? "events.ancientwarden.bossbar-enraged" : "events.ancientwarden.bossbar",
                "health", Numbers.format(Math.round(Math.ceil(health))), "max", Numbers.format(Math.round(maxHealth)));
    }

    @Override
    protected float bossBarProgress() {
        return clamp((float) (health / maxHealth));
    }

    @Override
    protected boolean isFinished() {
        return killed;
    }

    @Override
    protected void cleanup() {
        for (BukkitRunnable r : abilities) {
            if (!r.isCancelled()) {
                try {
                    r.cancel();
                } catch (IllegalStateException ignored) {
                    // never scheduled
                }
            }
        }
        abilities.clear();
        for (LivingEntity m : minions) {
            if (m.isValid()) {
                m.remove();
            }
        }
        minions.clear();
        if (boss != null && boss.isValid()) {
            boss.getWorld().spawnParticle(Particle.SCULK_SOUL, boss.getLocation().add(0, 1, 0), 60, 1, 1.5, 1, 0.05);
            boss.remove();
        }
        boss = null;
        releaseChunk();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getLivingEntities()) {
                String tag = e.getPersistentDataContainer().get(BOSS, PersistentDataType.STRING);
                if (tag != null && tag.startsWith(session) && !((LivingEntity) e).isDead()) {
                    e.remove();
                }
            }
        }
    }
}
