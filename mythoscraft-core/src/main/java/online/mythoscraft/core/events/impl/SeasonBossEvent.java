package online.mythoscraft.core.events.impl;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.EventState;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.events.SurfaceFinder;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seasonal world boss. PUMPKIN_KING (Halloween): giant pumpkin-headed zombie, throws exploding pumpkins and calls
 * pumpkin minions. FROST_GIANT (Frostfest): giant ice zombie with a freezing nova and stray archers. Health scales
 * with the number of participants; the score is the damage dealt. If time runs out, the boss escapes (no rewards).
 */
public final class SeasonBossEvent extends MythosEvent {

    /** Special attack of a boss. */
    enum Ability { BARRAGE, NOVA, POISON, SHOCKWAVE, LIGHTNING }

    /** All world bosses: body, attack, particles, spawn sound and speed. */
    public enum Kind {
        PUMPKIN_KING(Zombie.class, Ability.BARRAGE, Particle.SOUL_FIRE_FLAME, "entity.wither.spawn", 0.3),
        FROST_GIANT(Zombie.class, Ability.NOVA, Particle.SNOWFLAKE, "entity.elder_guardian.curse", 0.26),
        SPIDER_QUEEN(org.bukkit.entity.Spider.class, Ability.POISON, Particle.WITCH, "entity.spider.ambient", 0.34),
        BONE_KING(org.bukkit.entity.WitherSkeleton.class, Ability.SHOCKWAVE, Particle.SMOKE, "entity.wither_skeleton.ambient", 0.28),
        MAGMA_LORD(org.bukkit.entity.Husk.class, Ability.BARRAGE, Particle.FLAME, "entity.blaze.shoot", 0.27),
        SWAMP_WITCH(org.bukkit.entity.Witch.class, Ability.POISON, Particle.WITCH, "entity.witch.celebrate", 0.27),
        KILLER_BUNNY(org.bukkit.entity.Rabbit.class, Ability.SHOCKWAVE, Particle.HEART, "entity.rabbit.attack", 0.42),
        STORM_KNIGHT(Zombie.class, Ability.LIGHTNING, Particle.ELECTRIC_SPARK, "entity.lightning_bolt.thunder", 0.27),
        MYTHOS_TITAN(Zombie.class, Ability.LIGHTNING, Particle.END_ROD, "entity.wither.spawn", 0.25);

        final Class<? extends LivingEntity> type;
        final Ability ability;
        final Particle particle;
        final String spawnSound;
        final double speed;

        Kind(Class<? extends LivingEntity> type, Ability ability, Particle particle, String spawnSound, double speed) {
            this.type = type;
            this.ability = ability;
            this.particle = particle;
            this.spawnSound = spawnSound;
            this.speed = speed;
        }
    }

    private static final String TAG = "season_boss";

    private final Kind kind;
    private LivingEntity boss;
    private final List<LivingEntity> minions = new ArrayList<>();
    private double baseHealth = 300;
    private double healthPerPlayer = 120;
    private double scale = 2.4;
    private double damage = 9;
    private int abilityInterval = 7;
    private int abilityTimer;
    private double maxHealth;
    private boolean killed;
    private World chunkWorld;
    private int chunkX;
    private int chunkZ;

    public SeasonBossEvent(MythosCraft plugin, String id, Kind kind) {
        super(plugin, id);
        this.kind = kind;
    }

    @Override
    protected int defaultDuration() {
        return 480;
    }

    @Override
    protected String defaultVenue() {
        return kind == Kind.MYTHOS_TITAN ? "arena:monthly" : "arena:daily";
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        baseHealth = cfg.decimal("health.base", 300, 20, 1024);
        healthPerPlayer = cfg.decimal("health.per-player", 120, 0, 1024);
        scale = cfg.decimal("scale", 2.4, 0.5, 6);
        damage = cfg.decimal("damage", 9, 1, 100);
        abilityInterval = cfg.integer("ability-interval-seconds", 7, 2, 120);
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.damage";
    }

    @Override
    protected boolean grantRewards() {
        return killed;
    }

    @Override
    protected boolean isFinished() {
        return killed;
    }

    @Override
    protected void onStart() {
        killed = false;
        abilityTimer = abilityInterval;
        List<Player> players = participants();
        if (players.isEmpty()) {
            throw new IllegalStateException("no eligible players for the boss");
        }
        maxHealth = Math.min(1024, baseHealth + healthPerPlayer * players.size());
        if (venue != null) {
            spawnBoss(venue.clone().add(0, 0.5, 0));
            return;
        }
        Player anchor = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        SurfaceFinder.find(plugin, anchor.getWorld(), anchor.getX(), anchor.getZ(), 20, 45, 15,
                b -> plugin.arenas().arenaAt(b.getLocation()) == null && plugin.protection().query(b.getLocation().add(0, 1, 0), Flag.MOB_SPAWNING))
                .thenAccept(found -> {
                    if (state() != EventState.RUNNING) {
                        return;
                    }
                    if (found == null) {
                        plugin.getLogger().warning("Boss " + id() + ": no spawn location found – stopping");
                        finish(true);
                        return;
                    }
                    spawnBoss(found);
                });
    }

    private void spawnBoss(Location loc) {
        World w = loc.getWorld();
        w.strikeLightningEffect(loc);
        boss = w.spawn(loc, kind.type, z -> {
            if (z instanceof Zombie zombie) {
                zombie.setShouldBurnInDay(false);
                zombie.setAdult();
            }
            if (z instanceof org.bukkit.entity.Rabbit rabbit) {
                rabbit.setRabbitType(org.bukkit.entity.Rabbit.Type.THE_KILLER_BUNNY);
            }
            z.customName(plugin.messages().get("events." + id() + ".boss-name"));
            z.setCustomNameVisible(true);
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.getPersistentDataContainer().set(Keys.key(TAG), PersistentDataType.STRING, session);
            attribute(z, Attribute.MAX_HEALTH, maxHealth);
            attribute(z, Attribute.ATTACK_DAMAGE, damage);
            attribute(z, Attribute.SCALE, scale);
            attribute(z, Attribute.KNOCKBACK_RESISTANCE, 1);
            attribute(z, Attribute.FOLLOW_RANGE, 48);
            attribute(z, Attribute.MOVEMENT_SPEED, kind.speed);
            attribute(z, Attribute.ARMOR, 10);
            z.setHealth(maxHealth);
            EntityEquipment eq = z.getEquipment();
            if (eq == null) {
                return;
            }
            if (kind == Kind.MAGMA_LORD) {
                eq.setHelmet(new ItemStack(Material.MAGMA_BLOCK));
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, 0xB91C1C));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, 0x1C1917));
                eq.setBoots(leather(Material.LEATHER_BOOTS, 0xEA580C));
                eq.setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
            } else if (kind == Kind.BONE_KING) {
                eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            } else if (kind == Kind.STORM_KNIGHT) {
                eq.setHelmet(new ItemStack(Material.LIGHTNING_ROD));
                eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, 0x1E3A8A));
                eq.setBoots(new ItemStack(Material.IRON_BOOTS));
                eq.setItemInMainHand(new ItemStack(Material.TRIDENT));
            } else if (kind == Kind.MYTHOS_TITAN) {
                eq.setHelmet(new ItemStack(Material.DRAGON_HEAD));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, 0x7C3AED));
                eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                eq.setItemInMainHand(new ItemStack(Material.MACE));
            } else if (kind == Kind.PUMPKIN_KING) {
                eq.setHelmet(new ItemStack(Material.JACK_O_LANTERN));
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, 0xEA580C));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, 0x1C1917));
                eq.setBoots(leather(Material.LEATHER_BOOTS, 0xEA580C));
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_HOE));
            } else if (kind == Kind.FROST_GIANT) {
                eq.setHelmet(new ItemStack(Material.PACKED_ICE));
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, 0xBAE6FD));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, 0x7DD3FC));
                eq.setBoots(leather(Material.LEATHER_BOOTS, 0xF8FAFC));
                eq.setItemInMainHand(new ItemStack(Material.DIAMOND_AXE));
            }
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setLeggingsDropChance(0);
            eq.setBootsDropChance(0);
            eq.setItemInMainHandDropChance(0);
        });
        SoundSpec.of(kind.spawnSound, 2f, 0.8f).playAt(loc);
        w.spawnParticle(kind.particle, loc.clone().add(0, 2, 0), 120, 2, 2, 2, 0.08);
        plugin.messages().broadcast("events." + id() + ".spawned", "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ(),
                "world", w.getName());
        keepChunk();
    }

    private static ItemStack leather(Material m, int rgb) {
        ItemStack s = new ItemStack(m);
        s.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(rgb)));
        return s;
    }

    private static void attribute(LivingEntity e, Attribute a, double v) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) {
            inst.setBaseValue(v);
        }
    }

    private void keepChunk() {
        if (boss == null || !boss.isValid()) {
            return;
        }
        Location l = boss.getLocation();
        int cx = l.getBlockX() >> 4;
        int cz = l.getBlockZ() >> 4;
        if (l.getWorld().equals(chunkWorld) && cx == chunkX && cz == chunkZ) {
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

    @Override
    protected void onTick() {
        if (boss == null) {
            return;
        }
        if (!boss.isValid()) {
            if (!killed) {
                finish(true);
            }
            return;
        }
        keepChunk();
        minions.removeIf(m -> !m.isValid());
        boss.getWorld().spawnParticle(kind.particle, boss.getLocation().add(0, scale, 0), 6, 0.6, scale / 2, 0.6, 0.02);
        if (boss instanceof org.bukkit.entity.Mob mob && (mob.getTarget() == null || !(mob.getTarget() instanceof Player))) {
            Player closest = null;
            double best = 30 * 30;
            for (Player p : boss.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(boss.getLocation());
                if (eligible(p) && d < best && p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    best = d;
                    closest = p;
                }
            }
            if (closest != null) {
                mob.setTarget(closest);
            }
        }
        if (--abilityTimer > 0) {
            return;
        }
        abilityTimer = abilityInterval;
        List<Player> near = new ArrayList<>();
        for (Player p : boss.getWorld().getPlayers()) {
            if (eligible(p) && p.getLocation().distanceSquared(boss.getLocation()) < 24 * 24) {
                near.add(p);
            }
        }
        if (near.isEmpty()) {
            return;
        }
        if (ThreadLocalRandom.current().nextBoolean() && minions.size() < 8) {
            summon(near.size());
        } else {
            switch (kind.ability) {
                case BARRAGE -> pumpkinBarrage(near);
                case NOVA -> frostNova();
                case POISON -> poisonCloud();
                case SHOCKWAVE -> shockwave();
                case LIGHTNING -> lightning(near);
            }
        }
    }

    private void summon(int players) {
        Location l = boss.getLocation();
        int n = Math.min(4, 1 + players);
        for (int i = 0; i < n; i++) {
            Location at = l.clone().add(ThreadLocalRandom.current().nextDouble(-3, 3), 0, ThreadLocalRandom.current().nextDouble(-3, 3));
            if (!at.getBlock().isPassable()) {
                at = l.clone();
            }
            LivingEntity minion = switch (kind) {
                case PUMPKIN_KING -> l.getWorld().spawn(at, Zombie.class, z -> {
                    z.setBaby();
                    z.setShouldBurnInDay(false);
                    z.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                    z.getEquipment().setHelmetDropChance(0);
                    tagMinion(z);
                });
                case FROST_GIANT -> l.getWorld().spawn(at, Stray.class, this::tagMinion);
                case SPIDER_QUEEN -> l.getWorld().spawn(at, org.bukkit.entity.CaveSpider.class, this::tagMinion);
                case BONE_KING -> l.getWorld().spawn(at, org.bukkit.entity.Skeleton.class, this::tagMinion);
                case MAGMA_LORD -> l.getWorld().spawn(at, org.bukkit.entity.MagmaCube.class, m -> {
                    m.setSize(1);
                    tagMinion(m);
                });
                case SWAMP_WITCH -> l.getWorld().spawn(at, org.bukkit.entity.Slime.class, m -> {
                    m.setSize(1);
                    tagMinion(m);
                });
                case KILLER_BUNNY -> l.getWorld().spawn(at, org.bukkit.entity.Rabbit.class, r -> {
                    r.setRabbitType(org.bukkit.entity.Rabbit.Type.THE_KILLER_BUNNY);
                    tagMinion(r);
                });
                case STORM_KNIGHT -> l.getWorld().spawn(at, org.bukkit.entity.Drowned.class, this::tagMinion);
                case MYTHOS_TITAN -> l.getWorld().spawn(at, org.bukkit.entity.Vindicator.class, this::tagMinion);
            };
            minions.add(minion);
        }
        l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.add(0, 1, 0), 40, 2, 0.5, 2, 0.02);
        SoundSpec.of("entity.evoker.prepare_summon", 1.5f, 0.8f).playAt(boss.getLocation());
    }

    private void tagMinion(LivingEntity e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(Keys.key(TAG), PersistentDataType.STRING, session + ":minion");
        attribute(e, Attribute.MAX_HEALTH, 14);
        e.setHealth(14);
    }

    private void pumpkinBarrage(List<Player> targets) {
        for (Player p : targets.subList(0, Math.min(3, targets.size()))) {
            Location impact = p.getLocation();
            Location from = boss.getLocation().add(0, scale * 1.5, 0);
            for (int i = 0; i <= 10; i++) {
                double t = i / 10.0;
                Location step = from.clone().add(impact.clone().subtract(from).toVector().multiply(t)).add(0, Math.sin(t * Math.PI) * 3, 0);
                plugin.tasks().later(1 + i, "pumpkin arc", () -> step.getWorld().spawnParticle(Particle.FLAME, step, 3, 0.1, 0.1, 0.1, 0));
            }
            plugin.tasks().later(13, "pumpkin impact", () -> {
                World w = impact.getWorld();
                w.spawnParticle(Particle.EXPLOSION, impact, 2, 0.3, 0.3, 0.3, 0);
                w.spawnParticle(Particle.DUST, impact, 40, 1.5, 0.6, 1.5, 0, new Particle.DustOptions(Color.fromRGB(0xFF7A1A), 1.5f));
                SoundSpec.of("entity.generic.explode", 1f, 1.3f).playAt(impact);
                hurtAround(impact, 3, 6, null);
            });
        }
        SoundSpec.of("entity.witch.throw", 2f, 0.6f).playAt(boss.getLocation());
    }

    private void frostNova() {
        Location l = boss.getLocation();
        for (int ring = 1; ring <= 6; ring++) {
            int r = ring;
            plugin.tasks().later(ring * 2L, "frost nova", () -> {
                for (int a = 0; a < 360; a += 12) {
                    double rad = Math.toRadians(a);
                    l.getWorld().spawnParticle(Particle.SNOWFLAKE, l.clone().add(Math.cos(rad) * r, 0.3, Math.sin(rad) * r), 1, 0, 0, 0, 0);
                }
            });
        }
        SoundSpec.of("block.glass.break", 2f, 0.6f).playAt(l);
        hurtAround(l, 7, 5, p -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
            p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 100, 300));
        });
    }

    /** Poison cloud around the boss. */
    private void poisonCloud() {
        Location l = boss.getLocation();
        for (int i = 0; i < 4; i++) {
            plugin.tasks().later(i * 5L, "poison cloud", () -> l.getWorld().spawnParticle(Particle.WITCH, l.clone().add(0, 0.6, 0), 80, 4, 0.6, 4, 0.02));
        }
        SoundSpec.of("entity.witch.drink", 2f, 0.6f).playAt(l);
        hurtAround(l, 6, 2, p -> p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1)));
    }

    /** Ground slam: players nearby are thrown into the air. */
    private void shockwave() {
        Location l = boss.getLocation();
        for (int ring = 1; ring <= 7; ring++) {
            int r = ring;
            plugin.tasks().later(ring, "shockwave", () -> {
                for (int a = 0; a < 360; a += 10) {
                    double rad = Math.toRadians(a);
                    l.getWorld().spawnParticle(Particle.CLOUD, l.clone().add(Math.cos(rad) * r, 0.2, Math.sin(rad) * r), 1, 0, 0, 0, 0);
                }
            });
        }
        SoundSpec.of("entity.generic.explode", 1.5f, 0.6f).playAt(l);
        hurtAround(l, 7, 5, p -> p.setVelocity(p.getLocation().toVector().subtract(l.toVector()).normalize().multiply(1.1).setY(0.9)));
    }

    /** Lightning strikes at up to three players (warned by sparks first). */
    private void lightning(List<Player> targets) {
        for (Player p : targets.subList(0, Math.min(3, targets.size()))) {
            Location at = p.getLocation();
            at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.2, 0), 40, 1, 0.1, 1, 0.05);
            plugin.tasks().later(20, "boss lightning", () -> {
                at.getWorld().strikeLightningEffect(at);
                hurtAround(at, 2.5, 7, null);
            });
        }
    }

    private void hurtAround(Location at, double radius, double amount, java.util.function.Consumer<Player> extra) {
        for (Player p : at.getWorld().getPlayers()) {
            if (eligible(p) && p.getLocation().distanceSquared(at) <= radius * radius && plugin.protection().query(p.getLocation(), Flag.DAMAGE)) {
                p.damage(amount, boss);
                if (extra != null) {
                    extra.accept(p);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (boss == null || event.getEntity() != boss) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker != null && eligible(attacker)) {
            addScore(attacker, Math.min(event.getFinalDamage(), boss.getHealth()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        String tag = event.getEntity().getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING);
        if (tag != null && tag.startsWith(session) && event.getTarget() != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityCombustEvent event) {
        String tag = event.getEntity().getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING);
        if (tag != null && tag.startsWith(session)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        String tag = event.getEntity().getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING);
        if (tag == null || !tag.startsWith(session)) {
            return;
        }
        event.getDrops().clear();
        if (event.getEntity() == boss) {
            killed = true;
            event.setDroppedExp(500);
            Location l = boss.getLocation();
            l.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l.add(0, 2, 0), 200, 1.5, 2, 1.5, 0.4);
            SoundSpec.of("ui.toast.challenge_complete", 2f, 1f).playAt(l);
            plugin.messages().broadcast("events." + id() + ".defeated");
        }
    }

    @Override
    protected void onEnd(boolean aborted) {
        if (!aborted && !killed) {
            plugin.messages().broadcast("events." + id() + ".escaped");
        }
    }

    @Override
    protected Component bossBarText() {
        if (boss == null || !boss.isValid()) {
            return plugin.messages().get("events.boss-searching", "event", displayName());
        }
        return plugin.messages().get("events.boss-bar", "event", displayName(), "health", Numbers.format(Math.round(Math.ceil(boss.getHealth()))),
                "max", Numbers.format(Math.round(maxHealth)));
    }

    @Override
    protected float bossBarProgress() {
        return boss == null || !boss.isValid() ? 1f : clamp((float) (boss.getHealth() / maxHealth));
    }

    @Override
    protected void cleanup() {
        for (LivingEntity m : minions) {
            if (m.isValid()) {
                m.remove();
            }
        }
        minions.clear();
        if (boss != null && boss.isValid()) {
            boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, boss.getLocation().add(0, 1, 0), 60, 1, 1.5, 1, 0.05);
            boss.remove();
        }
        boss = null;
        releaseChunk();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getLivingEntities()) {
                String tag = e.getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING);
                if (tag != null && tag.startsWith(session)) {
                    e.remove();
                }
            }
        }
    }
}
