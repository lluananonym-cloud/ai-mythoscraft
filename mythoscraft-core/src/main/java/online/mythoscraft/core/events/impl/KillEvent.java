package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kill contest. GHOSTS: pumpkin-headed undead appear around every player (Halloween), every kill scores and may drop
 * candy. ANY_HOSTILE: every hostile mob kill counts (monster hunt). Event mobs never drop vanilla loot.
 */
public final class KillEvent extends MythosEvent {

    public enum Mobs { GHOSTS, ANY_HOSTILE, CHICKENS, SLIMES, SPIDERS, PIGS }

    private static final String TAG = "event_mob";

    private final Mobs mobs;
    private final List<LivingEntity> spawned = new ArrayList<>();
    private int waveInterval = 6;
    private int perWave = 2;
    private int maxPerPlayer = 6;
    private double dropChance = 0.25;
    private int waveTimer;

    public KillEvent(MythosCraft plugin, String id, Mobs mobs) {
        super(plugin, id);
        this.mobs = mobs;
    }

    @Override
    protected int defaultDuration() {
        return 240;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        waveInterval = cfg.integer("wave-interval-seconds", 6, 1, 120);
        perWave = cfg.integer("per-wave", 2, 0, 20);
        maxPerPlayer = cfg.integer("max-per-player", 6, 1, 50);
        dropChance = cfg.decimal("drop-chance", 0.25, 0, 1);
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.kills";
    }

    @Override
    protected void onStart() {
        waveTimer = 0;
    }

    @Override
    protected void onTick() {
        spawned.removeIf(e -> !e.isValid());
        if (mobs == Mobs.ANY_HOSTILE || ++waveTimer < waveInterval) {
            return;
        }
        waveTimer = 0;
        for (Player p : participants()) {
            long near = spawned.stream().filter(e -> e.getWorld().equals(p.getWorld())
                    && e.getLocation().distanceSquared(p.getLocation()) < 40 * 40).count();
            for (int i = 0; i < perWave && near + i < maxPerPlayer; i++) {
                spawnGhost(p);
            }
        }
    }

    private void spawnGhost(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = p.getWorld();
        // harmless animals (pigs, chickens) may appear in no-mob regions like the spawn – that is where players are
        boolean peaceful = mobs == Mobs.PIGS || mobs == Mobs.CHICKENS;
        Location loc = null;
        for (int attempt = 0; attempt < 8 && loc == null; attempt++) {
            double angle = r.nextDouble(Math.PI * 2);
            double dist = r.nextDouble(6, 16);
            int x = (int) Math.floor(p.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(angle) * dist);
            for (int dy = 5; dy >= -5; dy--) {
                Block b = w.getBlockAt(x, p.getLocation().getBlockY() + dy, z);
                if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, 2, 0).isPassable()
                        && !b.getRelative(0, 1, 0).isLiquid()) {
                    Location c = b.getLocation().add(0.5, 1, 0.5);
                    if ((peaceful || plugin.protection().query(c, Flag.MOB_SPAWNING)) && plugin.arenas().arenaAt(c) == null) {
                        loc = c;
                    }
                    break;
                }
            }
        }
        if (loc == null) {
            return;
        }
        if (mobs == Mobs.SLIMES || mobs == Mobs.SPIDERS || mobs == Mobs.PIGS) {
            Class<? extends LivingEntity> type = mobs == Mobs.SLIMES ? org.bukkit.entity.Slime.class
                    : mobs == Mobs.SPIDERS ? org.bukkit.entity.Spider.class : org.bukkit.entity.Pig.class;
            LivingEntity mob = w.spawn(loc, type, m -> {
                m.setPersistent(false);
                m.setGlowing(true);
                if (m instanceof org.bukkit.entity.Slime slime) {
                    slime.setSize(2);
                }
                m.customName(plugin.messages().get("events." + id() + ".mob-name"));
                m.getPersistentDataContainer().set(Keys.key(TAG), PersistentDataType.STRING, session);
            });
            spawned.add(mob);
            w.spawnParticle(Particle.WAX_ON, loc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0);
            return;
        }
        if (mobs == Mobs.CHICKENS) {
            LivingEntity chicken = w.spawn(loc, org.bukkit.entity.Chicken.class, c -> {
                c.setPersistent(false);
                c.setGlowing(true);
                c.customName(plugin.messages().get("events." + id() + ".mob-name"));
                c.getPersistentDataContainer().set(Keys.key(TAG), PersistentDataType.STRING, session);
            });
            spawned.add(chicken);
            w.spawnParticle(Particle.WAX_ON, loc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0);
            return;
        }
        boolean skeleton = r.nextInt(3) == 0;
        LivingEntity mob = skeleton ? w.spawn(loc, Skeleton.class, this::dress) : w.spawn(loc, Zombie.class, z2 -> {
            z2.setShouldBurnInDay(false);
            dress(z2);
        });
        if (mob instanceof Zombie zombie && r.nextInt(4) == 0) {
            zombie.setBaby();
        }
        spawned.add(mob);
        w.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.02);
        SoundSpec.of("ambient.soul_sand_valley.additions", 1f, 1.4f).playAt(loc);
    }

    private void dress(LivingEntity mob) {
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(true);
        mob.customName(plugin.messages().get("events." + id() + ".mob-name"));
        mob.setCustomNameVisible(false);
        mob.getPersistentDataContainer().set(Keys.key(TAG), PersistentDataType.STRING, session);
        EntityEquipment eq = mob.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(ThreadLocalRandom.current().nextBoolean() ? Material.CARVED_PUMPKIN : Material.JACK_O_LANTERN));
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            chest.editMeta(LeatherArmorMeta.class, m -> m.setColor(Color.fromRGB(0x2E1065)));
            eq.setChestplate(chest);
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setItemInMainHandDropChance(0);
        }
        if (mob.getAttribute(Attribute.MAX_HEALTH) != null) {
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(16);
            mob.setHealth(16);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplit(org.bukkit.event.entity.SlimeSplitEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityCombustEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        boolean eventMob = dead.getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING);
        if (eventMob) {
            event.getDrops().clear();
            event.setDroppedExp(2);
        }
        Player killer = dead.getKiller();
        if (killer == null || !eligible(killer)) {
            return;
        }
        if (mobs != Mobs.ANY_HOSTILE && !eventMob) {
            return;
        }
        if (mobs == Mobs.ANY_HOSTILE && (!(dead instanceof Enemy) || dead.fromMobSpawner())) {
            return;
        }
        int points = mobs == Mobs.GHOSTS && dead instanceof Skeleton ? 2 : 1;
        if (mobs == Mobs.CHICKENS || mobs == Mobs.SLIMES || mobs == Mobs.SPIDERS || mobs == Mobs.PIGS) {
            online.mythoscraft.core.player.PlayerData data = plugin.playerData().get(killer);
            if (data != null) {
                plugin.economy().earn(data, online.mythoscraft.core.economy.Currency.COINS, 15, true);
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.05 && data != null) {
                plugin.economy().earn(data, online.mythoscraft.core.economy.Currency.GEMS, 1, true);
                plugin.messages().actionBar(killer, "abilities.gem-pouch", "amount", 1);
            }
        }
        addScore(killer, points);
        plugin.messages().actionBar(killer, "events.kill.point", "points", points, "total", Math.round(score(killer.getUniqueId())));
        if (mobs == Mobs.GHOSTS && ThreadLocalRandom.current().nextDouble() < dropChance) {
            CustomItem[] candy = {CustomItem.CANDY_CORN, CustomItem.LOLLIPOP, CustomItem.CARAMEL_APPLE};
            CustomItemManager.give(killer, plugin.items().create(candy[ThreadLocalRandom.current().nextInt(candy.length)]));
        }
        dead.getWorld().spawnParticle(Particle.SOUL, dead.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.03);
    }

    @Override
    protected void cleanup() {
        Iterator<LivingEntity> it = spawned.iterator();
        while (it.hasNext()) {
            LivingEntity e = it.next();
            if (e.isValid()) {
                e.getWorld().spawnParticle(Particle.SOUL, e.getLocation().add(0, 1, 0), 10, 0.3, 0.6, 0.3, 0.02);
                e.remove();
            }
            it.remove();
        }
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                if (session.equals(e.getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING))) {
                    e.remove();
                }
            }
        }
    }
}
