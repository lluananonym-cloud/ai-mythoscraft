package online.mythoscraft.core.item;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.SurfaceFinder;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.reward.RewardTable;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Third item wave: ore vein pickaxe, mob net, ender bow, boomerang, healing flask, wisdom charm, treasure map,
 * frost and smoke bombs and the spring items (easter egg, carrot blade, blossom wand).
 */
public final class WaveThreeListener implements Listener {

    private static final Set<Material> ORES = EnumSet.noneOf(Material.class);

    static {
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isBlock() && (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS)) {
                ORES.add(m);
            }
        }
    }

    private static final String NET_ENTITY = "net_entity";
    private static final String NET_TYPE = "net_type";

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Set<UUID> veinMining = new HashSet<>();
    private ConfigReader cfg;
    private List<Reward> eggRewards = List.of();
    private List<Reward> treasureRewards = List.of();

    public WaveThreeListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        eggRewards = RewardTable.parseList(cfg, "easter-egg.rewards");
        if (eggRewards.isEmpty()) {
            eggRewards = List.of(Reward.parse("coins 300"), Reward.parse("gems 2"), Reward.parse("tool lucky_clover 1"));
        }
        treasureRewards = RewardTable.parseList(cfg, "treasure-map.loot");
        if (treasureRewards.isEmpty()) {
            treasureRewards = List.of(Reward.parse("item DIAMOND 3"), Reward.parse("item EMERALD 6"), Reward.parse("tool coin_pouch 2"),
                    Reward.parse("tool gem_pouch 1"), Reward.parse("tool key_fragment 3"));
        }
    }

    private double num(String path, double def) {
        return cfg.decimal(path, def, 0, 100_000);
    }

    // =====================================================================================================
    // Right-click
    // =====================================================================================================

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
            case MOB_NET -> {
                String stored = item.getPersistentDataContainer().get(Keys.key(NET_ENTITY), PersistentDataType.STRING);
                if (stored == null || event.getClickedBlock() == null) {
                    return;
                }
                event.setCancelled(true);
                Location at = event.getClickedBlock().getRelative(event.getBlockFace() == null ? BlockFace.UP : event.getBlockFace())
                        .getLocation().add(0.5, 0, 0.5);
                if (!plugin.protection().allows(p, at, Flag.INTERACT)) {
                    plugin.messages().actionBar(p, "abilities.net-protected");
                    return;
                }
                try {
                    EntitySnapshot snap = Bukkit.getEntityFactory().createEntitySnapshot(stored);
                    snap.createEntity(at);
                } catch (IllegalArgumentException e) {
                    plugin.messages().actionBar(p, "abilities.net-broken");
                }
                item.editMeta(meta -> {
                    meta.getPersistentDataContainer().remove(Keys.key(NET_ENTITY));
                    meta.getPersistentDataContainer().remove(Keys.key(NET_TYPE));
                    meta.lore(plugin.messages().getList("items.mob_net.lore").stream().map(Text::noItalic).toList());
                    meta.setEnchantmentGlintOverride(null);
                });
                at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0);
                SoundSpec.of("entity.chicken.egg", 1f, 0.8f).playAt(at);
            }
            case BOOMERANG -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "boomerang", num("boomerang.cooldown-seconds", 3))) {
                    return;
                }
                throwBoomerang(p);
            }
            case TREASURE_MAP -> {
                event.setCancelled(true);
                if (plugin.auctions().blockedHere(p)) {
                    plugin.messages().send(p, "arena.leave-first");
                    return;
                }
                if (support.onCooldown(p, item, "treasure_map", 5)) {
                    return;
                }
                hideTreasure(p, item);
            }
            case SMOKE_BOMB -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "smoke_bomb", num("smoke-bomb.cooldown-seconds", 20))) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                Location l = p.getLocation();
                l.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l.clone().add(0, 1, 0), 80, 1.5, 1, 1.5, 0.02);
                l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 60, 1.5, 1, 1.5, 0.02);
                SoundSpec.of("entity.generic.extinguish_fire", 1f, 0.7f).playAt(l);
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
                for (Entity e : p.getNearbyEntities(4, 3, 4)) {
                    if (e instanceof LivingEntity le && support.mayHurt(p, le)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    }
                }
            }
            case EASTER_EGG -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                Reward r = eggRewards.get(ThreadLocalRandom.current().nextInt(eggRewards.size()));
                r.give(plugin, p, 1.0);
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.5, 0), 30, 0.4, 0.4, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(0xF9A8D4), 1.2f));
                SoundSpec.of("entity.chicken.egg", 1f, 1.4f).play(p);
                plugin.messages().send(p, "abilities.easter-egg", "reward", r.describe(plugin, 1.0));
            }
            case BLOSSOM_WAND -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "blossom_wand", num("blossom-wand.cooldown-seconds", 8))) {
                    return;
                }
                Block center = event.getClickedBlock() == null ? p.getLocation().getBlock().getRelative(0, -1, 0) : event.getClickedBlock();
                int bloomed = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Block b = center.getRelative(dx, 0, dz);
                        if (b.getType() == Material.GRASS_BLOCK && b.getRelative(0, 1, 0).getType().isAir()
                                && plugin.protection().allows(p, b, Flag.BUILD) && ThreadLocalRandom.current().nextInt(3) == 0
                                && b.applyBoneMeal(BlockFace.UP)) {
                            bloomed++;
                        }
                    }
                }
                for (Player other : p.getWorld().getPlayers()) {
                    if (other.getLocation().distanceSquared(p.getLocation()) <= 16) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
                    }
                }
                p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, p.getLocation().add(0, 1.5, 0), 60, 2, 1, 2, 0.02);
                SoundSpec.of("block.azalea_leaves.place", 1f, 1.3f).playAt(p.getLocation());
                plugin.messages().actionBar(p, "abilities.blossom", "count", bloomed);
            }
            default -> {
            }
        }
    }

    // ---- mob net ------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCatch(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!plugin.items().is(item, CustomItem.MOB_NET)) {
            return;
        }
        event.setCancelled(true);
        Entity e = event.getRightClicked();
        if (item.getPersistentDataContainer().has(Keys.key(NET_ENTITY), PersistentDataType.STRING)) {
            plugin.messages().actionBar(p, "abilities.net-full");
            return;
        }
        boolean foreignPet = e instanceof Tameable t && t.isTamed() && !p.equals(t.getOwner());
        boolean catchable = (e instanceof Animals || e instanceof WaterMob) && !foreignPet && e.getPassengers().isEmpty() && !e.isInsideVehicle();
        if (!catchable || !plugin.protection().allows(p, e.getLocation(), Flag.INTERACT)) {
            plugin.messages().actionBar(p, "abilities.net-invalid");
            return;
        }
        EntitySnapshot snap = e.createSnapshot();
        if (snap == null) {
            plugin.messages().actionBar(p, "abilities.net-invalid");
            return;
        }
        String data = snap.getAsString();
        Component name = Component.translatable(e.getType().translationKey());
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(Keys.key(NET_ENTITY), PersistentDataType.STRING, data);
            meta.getPersistentDataContainer().set(Keys.key(NET_TYPE), PersistentDataType.STRING, e.getType().name());
            meta.lore(plugin.messages().getList("abilities.net-lore", "entity", name).stream().map(Text::noItalic).toList());
            meta.setEnchantmentGlintOverride(true);
        });
        e.getWorld().spawnParticle(Particle.POOF, e.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.02);
        SoundSpec.of("entity.item.pickup", 1f, 0.6f).playAt(e.getLocation());
        e.remove();
        plugin.messages().actionBar(p, "abilities.net-caught", "entity", name);
    }

    // ---- boomerang ----------------------------------------------------------------------------------------

    private void throwBoomerang(Player p) {
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
        Vector dir = p.getLocation().getDirection().normalize();
        ItemDisplay display = p.getWorld().spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(new ItemBuilder(Material.PAPER).model("boomerang").build());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
        });
        double damage = num("boomerang.damage", 5);
        double range = num("boomerang.range", 14);
        Set<UUID> hit = new HashSet<>();
        SoundSpec.of("entity.breeze.shoot", 0.8f, 1.6f).playAt(start);
        new BukkitRunnable() {
            int t;
            boolean back;
            double travelled;

            @Override
            public void run() {
                t++;
                if (!p.isOnline() || !display.isValid() || t > 80) {
                    display.remove();
                    cancel();
                    return;
                }
                Location pos = display.getLocation();
                Vector step;
                if (!back) {
                    step = dir.clone().multiply(0.9);
                    travelled += 0.9;
                    if (travelled >= range || !pos.clone().add(step).getBlock().isPassable()) {
                        back = true;
                        hit.clear();
                    }
                } else {
                    Vector to = p.getEyeLocation().toVector().subtract(pos.toVector());
                    if (to.lengthSquared() < 1.5) {
                        display.remove();
                        SoundSpec.of("entity.item.pickup", 0.8f, 1.4f).play(p);
                        cancel();
                        return;
                    }
                    step = to.normalize().multiply(1.0);
                }
                Location next = pos.clone().add(step);
                display.teleport(next);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(t * 0.9f), new Vector3f(0.9f),
                        new Quaternionf(new AxisAngle4f())));
                next.getWorld().spawnParticle(Particle.SWEEP_ATTACK, next, 1, 0, 0, 0, 0);
                for (Entity e : next.getWorld().getNearbyEntities(next, 1.0, 1.0, 1.0)) {
                    if (e instanceof LivingEntity le && hit.add(le.getUniqueId()) && support.mayHurt(p, le)) {
                        le.damage(damage, p);
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    // ---- treasure map -------------------------------------------------------------------------------------

    private void hideTreasure(Player p, ItemStack map) {
        plugin.messages().actionBar(p, "abilities.treasure-searching");
        double min = num("treasure-map.min-distance", 150);
        double max = Math.max(min + 10, num("treasure-map.max-distance", 400));
        SurfaceFinder.find(plugin, p.getWorld(), p.getX(), p.getZ(), min, max, 20,
                b -> plugin.regions().at(b.getLocation()).isEmpty() && plugin.arenas().arenaAt(b.getLocation()) == null)
                .thenAccept(found -> plugin.tasks().sync(() -> {
                    if (!p.isOnline()) {
                        return;
                    }
                    if (found == null) {
                        plugin.messages().send(p, "abilities.treasure-failed");
                        return;
                    }
                    Block chestBlock = found.getBlock().getRelative(0, -1, 0);
                    chestBlock.setType(Material.CHEST);
                    if (chestBlock.getState() instanceof Chest chest) {
                        fillTreasure(chest.getBlockInventory());
                    }
                    if (plugin.items().is(map, CustomItem.TREASURE_MAP)) {
                        AbilitySupport.consumeOne(map);
                    }
                    Location l = chestBlock.getLocation();
                    plugin.messages().send(p, "abilities.treasure-hidden", "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(),
                            "distance", Math.round(l.distance(p.getLocation())));
                    p.setCompassTarget(l);
                    SoundSpec.of("ui.cartography_table.take_result", 1f, 1f).play(p);
                }));
    }

    private void fillTreasure(Inventory inv) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int rolls = 3 + r.nextInt(3);
        for (int i = 0; i < rolls; i++) {
            Reward reward = treasureRewards.get(r.nextInt(treasureRewards.size()));
            ItemStack stack = switch (reward.type()) {
                case TOOL -> plugin.items().create(CustomItem.byId(reward.id()), (int) Math.min(64, reward.amount()));
                case ITEM -> new ItemStack(Material.valueOf(reward.id()), (int) Math.min(64, reward.amount()));
                case KEY -> plugin.crates().createKey(reward.id(), (int) Math.min(64, reward.amount()));
                case BOOK -> reward.enchantedBook();
                default -> null;
            };
            if (stack != null) {
                inv.setItem(r.nextInt(inv.getSize()), stack);
            }
        }
    }

    // ---- bows, bombs, blades ------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p) || !(event.getProjectile() instanceof AbstractArrow arrow)
                || !plugin.items().is(event.getBow(), CustomItem.ENDER_BOW)) {
            return;
        }
        if (support.onCooldown(p, event.getBow(), "ender_bow", num("ender-bow.cooldown-seconds", 8))) {
            event.setCancelled(true);
            return;
        }
        arrow.getPersistentDataContainer().set(Keys.key("projectile"), PersistentDataType.STRING, "ender_bow");
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        if (event.getEntity() instanceof AbstractArrow arrow
                && "ender_bow".equals(arrow.getPersistentDataContainer().get(Keys.key("projectile"), PersistentDataType.STRING))) {
            Location at = arrow.getLocation();
            if (event.getHitBlockFace() != null && event.getHitBlock() != null) {
                at = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
            }
            if (at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable() && !plugin.auctions().blockedHere(shooter)) {
                at.setYaw(shooter.getLocation().getYaw());
                at.setPitch(shooter.getLocation().getPitch());
                shooter.getWorld().spawnParticle(Particle.PORTAL, shooter.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.5);
                shooter.teleport(at);
                shooter.setFallDistance(0);
                SoundSpec.of("entity.enderman.teleport", 1f, 1.2f).playAt(at);
            }
            arrow.remove();
            return;
        }
        if (event.getEntity() instanceof Snowball ball && plugin.items().identify(ball.getItem()) == CustomItem.FROST_BOMB) {
            Location at = ball.getLocation();
            World w = at.getWorld();
            w.spawnParticle(Particle.SNOWFLAKE, at, 80, 1.5, 1, 1.5, 0.05);
            w.spawnParticle(Particle.ITEM_SNOWBALL, at, 30, 1, 0.5, 1, 0.1);
            SoundSpec.of("block.glass.break", 1f, 1.4f).playAt(at);
            double radius = num("frost-bomb.radius", 3.5);
            for (Entity e : w.getNearbyEntities(at, radius, radius, radius)) {
                if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                    le.damage(num("frost-bomb.damage", 2), shooter);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                    le.setFreezeTicks(Math.max(le.getFreezeTicks(), 200));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.CARROT_BLADE)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false));
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, event.getEntity().getLocation().add(0, 1, 0), 5, 0.3, 0.4, 0.3, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.items().identify(event.getItem()) == CustomItem.HEALING_FLASK) {
            Player p = event.getPlayer();
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                    : p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + num("healing-flask.heal", 8)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onXp(PlayerExpChangeEvent event) {
        Player p = event.getPlayer();
        if (event.getAmount() > 0 && plugin.items().is(p.getInventory().getItemInOffHand(), CustomItem.WISDOM_CHARM)) {
            event.setAmount((int) Math.ceil(event.getAmount() * num("wisdom-charm.multiplier", 1.5)));
        }
    }

    // ---- ore vein pickaxe ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block origin = event.getBlock();
        if (veinMining.contains(p.getUniqueId()) || !ORES.contains(origin.getType())
                || !plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.ORE_PICKAXE)) {
            return;
        }
        Material type = origin.getType();
        int limit = (int) num("ore-pickaxe.max-blocks", 16);
        List<Block> vein = new ArrayList<>();
        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && vein.size() < limit) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (n.getType() == type && seen.add(n)) {
                            vein.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        if (vein.isEmpty()) {
            return;
        }
        veinMining.add(p.getUniqueId());
        try {
            for (Block b : vein) {
                if (vein.indexOf(b) >= limit) {
                    break;
                }
                p.breakBlock(b);
            }
        } finally {
            veinMining.remove(p.getUniqueId());
        }
        origin.getWorld().spawnParticle(Particle.WAX_OFF, origin.getLocation().add(0.5, 0.5, 0.5), 15, 0.5, 0.5, 0.5, 0);
    }
}
