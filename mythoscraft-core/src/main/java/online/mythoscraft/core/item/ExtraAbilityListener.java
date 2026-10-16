package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.reward.RewardTable;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abilities of the farm, storm, dragon and utility items (second item wave): crop tools, lightning weapons,
 * dragon fire, builder's wand, auto-smelting, pouches, key fragments, charms and the angel set's fall protection.
 */
public final class ExtraAbilityListener implements Listener {

    private static final Particle.DustOptions DRAGON = new Particle.DustOptions(Color.fromRGB(0xA21CAF), 1.4f);
    private static final Particle.DustOptions STORM = new Particle.DustOptions(Color.fromRGB(0x7DD3FC), 1.2f);
    private static final String PROJECTILE = "projectile";

    private static final Map<Material, Material> CROP_SEEDS = new EnumMap<>(Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART));
    private static final Map<Material, Material> SEED_CROPS = new EnumMap<>(Map.of(
            Material.WHEAT_SEEDS, Material.WHEAT,
            Material.CARROT, Material.CARROTS,
            Material.POTATO, Material.POTATOES,
            Material.BEETROOT_SEEDS, Material.BEETROOTS));
    private static final Map<Material, Material> SMELT = new EnumMap<>(Material.class);

    static {
        SMELT.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        SMELT.put(Material.COBBLESTONE, Material.STONE);
        SMELT.put(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
        SMELT.put(Material.SAND, Material.GLASS);
        SMELT.put(Material.RED_SAND, Material.GLASS);
        SMELT.put(Material.NETHERRACK, Material.NETHER_BRICK);
        SMELT.put(Material.CLAY_BALL, Material.BRICK);
        SMELT.put(Material.CACTUS, Material.GREEN_DYE);
        SMELT.put(Material.KELP, Material.DRIED_KELP);
        SMELT.put(Material.WET_SPONGE, Material.SPONGE);
        SMELT.put(Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS);
    }

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Set<UUID> chaining = new HashSet<>();
    private ConfigReader cfg;
    private List<Reward> fragmentRewards = List.of();

    public ExtraAbilityListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        fragmentRewards = RewardTable.parseList(cfg, "key-fragment.rewards");
        if (fragmentRewards.isEmpty()) {
            fragmentRewards = List.of(Reward.parse("key vip 1"), Reward.parse("key vip 1"), Reward.parse("key elite 1"),
                    Reward.parse("key mythos 1"));
        }
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 25, 10, "item charms 2", this::charmTick);
    }

    private double num(String path, double def) {
        return cfg.decimal(path, def, 0, 100_000);
    }

    private static int rnd(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, Math.max(min, max) + 1);
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
        Block clicked = event.getClickedBlock();
        switch (type) {
            case HARVEST_SICKLE -> {
                if (clicked == null) {
                    return;
                }
                event.setCancelled(true);
                if (support.onCooldown(p, item, "harvest_sickle", num("harvest-sickle.cooldown-seconds", 0.5))) {
                    return;
                }
                harvest(p, item, clicked, (int) num("harvest-sickle.radius", 2));
            }
            case WATERING_CAN -> {
                if (clicked == null) {
                    return;
                }
                event.setCancelled(true);
                if (support.onCooldown(p, item, "watering_can", num("watering-can.cooldown-seconds", 3))) {
                    return;
                }
                water(p, clicked, (int) num("watering-can.radius", 1));
            }
            case SEED_BAG -> {
                if (clicked == null) {
                    return;
                }
                event.setCancelled(true);
                plant(p, clicked, (int) num("seed-bag.radius", 2));
            }
            case THUNDER_HAMMER -> {
                event.setCancelled(true);
                RayTraceResult hit = rayEntity(p, num("thunder-hammer.range", 25));
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
                    plugin.messages().actionBar(p, "tools.no-target");
                    return;
                }
                if (support.onCooldown(p, item, "thunder_hammer", num("thunder-hammer.cooldown-seconds", 15))) {
                    return;
                }
                lightning(p, target, num("thunder-hammer.damage", 6));
                target.setVelocity(target.getVelocity().setY(0.6));
            }
            case WIND_BLADE -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "wind_blade", num("wind-blade.cooldown-seconds", 5))) {
                    return;
                }
                Vector dir = p.getLocation().getDirection().setY(0).normalize().multiply(num("wind-blade.power", 1.6)).setY(0.3);
                p.setVelocity(dir);
                p.setFallDistance(0);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.5, 0), 30, 0.4, 0.2, 0.4, 0.08);
                p.getWorld().spawnParticle(Particle.GUST, p.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
                SoundSpec.of("entity.breeze.wind_burst", 1f, 1.2f).playAt(p.getLocation());
            }
            case DRAGON_STAFF -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "dragon_staff", num("dragon-staff.cooldown-seconds", 8))) {
                    return;
                }
                Snowball ball = p.launchProjectile(Snowball.class);
                ball.setItem(new ItemStack(Material.FIRE_CHARGE));
                ball.setVelocity(p.getLocation().getDirection().multiply(1.7));
                ball.getPersistentDataContainer().set(Keys.key(PROJECTILE), PersistentDataType.STRING, "dragon_staff");
                trail(ball, Particle.DRAGON_BREATH);
                SoundSpec.of("entity.ender_dragon.shoot", 0.8f, 1.2f).playAt(p.getLocation());
            }
            case DRAGON_HEART -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "dragon_heart", num("dragon-heart.cooldown-seconds", 120))) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 60, 0));
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0, DRAGON);
                SoundSpec.of("entity.ender_dragon.growl", 0.6f, 1.4f).play(p);
            }
            case ENDER_POUCH -> {
                event.setCancelled(true);
                if (plugin.auctions().blockedHere(p)) {
                    plugin.messages().send(p, "arena.leave-first");
                    return;
                }
                p.openInventory(p.getEnderChest());
                SoundSpec.of("block.ender_chest.open", 0.8f, 1.2f).play(p);
            }
            case BUILDER_WAND -> {
                if (clicked == null) {
                    return;
                }
                event.setCancelled(true);
                if (support.onCooldown(p, item, "builder_wand", num("builder-wand.cooldown-seconds", 0.4))) {
                    return;
                }
                build(p, clicked, event.getBlockFace(), (int) num("builder-wand.radius", 1));
            }
            case COIN_POUCH -> {
                event.setCancelled(true);
                PlayerData data = plugin.playerData().get(p);
                if (data == null) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                long coins = plugin.economy().earn(data, Currency.COINS,
                        rnd((int) num("coin-pouch.min", 100), (int) num("coin-pouch.max", 1000)), false);
                plugin.messages().actionBar(p, "abilities.coin-pouch", "amount", coins);
                p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0);
                SoundSpec.of("entity.experience_orb.pickup", 1f, 0.8f).play(p);
            }
            case GEM_POUCH -> {
                event.setCancelled(true);
                PlayerData data = plugin.playerData().get(p);
                if (data == null) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                long gems = plugin.economy().earn(data, Currency.GEMS, rnd((int) num("gem-pouch.min", 1), (int) num("gem-pouch.max", 5)), false);
                plugin.messages().actionBar(p, "abilities.gem-pouch", "amount", gems);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
                SoundSpec.of("block.amethyst_block.chime", 1f, 1.4f).play(p);
            }
            case KEY_FRAGMENT -> {
                event.setCancelled(true);
                int need = (int) num("key-fragment.needed", 9);
                int have = countCustom(p.getInventory(), CustomItem.KEY_FRAGMENT);
                if (have < need) {
                    plugin.messages().actionBar(p, "abilities.fragments-needed", "have", have, "need", need);
                    return;
                }
                removeCustom(p.getInventory(), CustomItem.KEY_FRAGMENT, need);
                Reward r = fragmentRewards.get(ThreadLocalRandom.current().nextInt(fragmentRewards.size()));
                r.give(plugin, p, 1.0);
                plugin.messages().send(p, "abilities.fragment-key", "reward", r.describe(plugin, 1.0), "need", need);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
                SoundSpec.of("block.anvil.use", 0.6f, 1.6f).play(p);
            }
            case SWIFT_RUNE -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 180, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 180, 0));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 25, 0.4, 0.1, 0.4, 0.05);
                SoundSpec.of("block.beacon.activate", 1f, 2f).play(p);
            }
            case RTP_SCROLL -> {
                event.setCancelled(true);
                plugin.rtp().start(p, null, true, () -> {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (plugin.items().is(hand, CustomItem.RTP_SCROLL)) {
                        AbilitySupport.consumeOne(hand);
                    } else {
                        removeCustom(p.getInventory(), CustomItem.RTP_SCROLL, 1);
                    }
                });
            }
            default -> {
            }
        }
    }

    private void harvest(Player p, ItemStack tool, Block center, int radius) {
        int harvested = 0;
        List<ItemStack> loot = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = center.getRelative(dx, dy, dz);
                    Material seed = CROP_SEEDS.get(b.getType());
                    if (seed == null || !(b.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()
                            || !plugin.protection().allows(p, b, Flag.BREAK)) {
                        continue;
                    }
                    boolean replanted = false;
                    for (ItemStack drop : b.getDrops(tool, p)) {
                        if (!replanted && drop.getType() == seed) {
                            drop.setAmount(drop.getAmount() - 1);
                            replanted = true;
                        }
                        if (drop.getAmount() > 0) {
                            loot.add(drop);
                        }
                    }
                    age.setAge(0);
                    b.setBlockData(age);
                    b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0);
                    harvested++;
                }
            }
        }
        if (harvested == 0) {
            plugin.messages().actionBar(p, "abilities.harvest-none");
            return;
        }
        if (offhand(p) == CustomItem.COMPOST_CHARM && ThreadLocalRandom.current().nextDouble() < num("compost-charm.chance", 0.25)) {
            loot.addAll(new ArrayList<>(loot.stream().map(ItemStack::clone).toList()));
        }
        loot.forEach(drop -> CustomItemManager.give(p, drop));
        plugin.messages().actionBar(p, "abilities.harvest", "count", harvested);
        SoundSpec.of("item.crop.plant", 1f, 1.2f).playAt(p.getLocation());
    }

    private void water(Player p, Block clicked, int radius) {
        int grown = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Block b = clicked.getRelative(dx, dy, dz);
                    if (b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()
                            && plugin.protection().allows(p, b, Flag.BUILD) && b.applyBoneMeal(BlockFace.UP)) {
                        grown++;
                    }
                }
            }
        }
        clicked.getWorld().spawnParticle(Particle.SPLASH, clicked.getLocation().add(0.5, 1.2, 0.5), 50, radius + 0.3, 0.2, radius + 0.3, 0.1);
        SoundSpec.of("item.bucket.empty", 0.8f, 1.5f).playAt(clicked.getLocation());
        plugin.messages().actionBar(p, "abilities.watered", "count", grown);
    }

    private void plant(Player p, Block clicked, int radius) {
        Block base = clicked.getType() == Material.FARMLAND ? clicked : clicked.getRelative(0, -1, 0);
        int planted = 0;
        PlayerInventory inv = p.getInventory();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block soil = base.getRelative(dx, 0, dz);
                Block above = soil.getRelative(0, 1, 0);
                if (soil.getType() != Material.FARMLAND || !above.getType().isAir() || !plugin.protection().allows(p, above, Flag.BUILD)) {
                    continue;
                }
                Material seed = takeSeed(inv, p.getGameMode() == GameMode.CREATIVE);
                if (seed == null) {
                    if (planted == 0) {
                        plugin.messages().actionBar(p, "abilities.seeds-none");
                    }
                    break;
                }
                above.setType(SEED_CROPS.get(seed));
                planted++;
            }
        }
        if (planted > 0) {
            SoundSpec.of("item.crop.plant", 1f, 1f).playAt(clicked.getLocation());
            plugin.messages().actionBar(p, "abilities.planted", "count", planted);
        }
    }

    private static Material takeSeed(PlayerInventory inv, boolean creative) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s != null && SEED_CROPS.containsKey(s.getType()) && s.isSimilar(new ItemStack(s.getType()))) {
                Material m = s.getType();
                if (!creative) {
                    s.setAmount(s.getAmount() - 1);
                    inv.setItem(i, s.getAmount() <= 0 ? null : s);
                }
                return m;
            }
        }
        return null;
    }

    private void build(Player p, Block clicked, BlockFace face, int radius) {
        Material type = clicked.getType();
        if (face == null || !type.isItem() || !type.isSolid() || !type.isOccluding() || clicked.getState() instanceof TileState) {
            plugin.messages().actionBar(p, "abilities.builder-invalid");
            return;
        }
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        int available = creative ? Integer.MAX_VALUE : countPlain(p.getInventory(), type);
        List<Block> targets = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                Block source = switch (face) {
                    case UP, DOWN -> clicked.getRelative(a, 0, b);
                    case NORTH, SOUTH -> clicked.getRelative(a, b, 0);
                    default -> clicked.getRelative(0, a, b);
                };
                Block target = source.getRelative(face);
                if (source.getType() == type && (target.getType().isAir() || target.isReplaceable())
                        && target.getWorld().getNearbyEntities(target.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5,
                        e -> e instanceof LivingEntity).isEmpty()
                        && plugin.protection().allows(p, target, Flag.BUILD)) {
                    targets.add(target);
                }
            }
        }
        int placed = 0;
        for (Block t : targets) {
            if (placed >= available) {
                break;
            }
            t.setBlockData(clicked.getBlockData().clone(), true);
            placed++;
        }
        if (!creative && placed > 0) {
            removePlain(p.getInventory(), type, placed);
        }
        if (placed == 0) {
            plugin.messages().actionBar(p, available == 0 ? "abilities.builder-no-blocks" : "abilities.builder-no-space");
            return;
        }
        SoundSpec.of("block.stone.place", 1f, 1.2f).playAt(clicked.getLocation());
        plugin.messages().actionBar(p, "abilities.builder-placed", "count", placed);
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
            case HONEY_BREAD -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
            case FORTUNE_COOKIE -> fortune(p);
            case STORM_FLASK -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 60, 1));
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
            }
            case MYTHOS_ELIXIR -> {
                int t = 20 * 90;
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, t, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, t, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, t, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, t, 0));
                p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1);
                SoundSpec.of("block.beacon.power_select", 0.8f, 1.6f).play(p);
            }
            default -> {
            }
        }
    }

    private void fortune(Player p) {
        List<String> lines = plugin.messages().rawList("abilities.fortunes");
        if (!lines.isEmpty()) {
            p.sendMessage(plugin.messages().prefix().append(plugin.messages().format(lines.get(ThreadLocalRandom.current().nextInt(lines.size())))));
        }
        PlayerData data = plugin.playerData().get(p);
        double roll = ThreadLocalRandom.current().nextDouble();
        if (data != null && roll < 0.05) {
            plugin.economy().earn(data, Currency.GEMS, 1, false);
            plugin.messages().actionBar(p, "abilities.gem-pouch", "amount", 1);
        } else if (data != null && roll < 0.35) {
            long coins = plugin.economy().earn(data, Currency.COINS, rnd(25, 150), false);
            plugin.messages().actionBar(p, "abilities.coin-pouch", "amount", coins);
        }
    }

    // =====================================================================================================
    // Combat
    // =====================================================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitModify(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && !(event.getEntity() instanceof Player) && plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.DRAGON_BLADE)) {
            event.setDamage(event.getDamage() * 1.3);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity damager = event.getDamager();
        if (victim instanceof Player defender && damager instanceof LivingEntity attacker && offhand(defender) == CustomItem.STATIC_CHARM
                && ThreadLocalRandom.current().nextDouble() < num("static-charm.chance", 0.2) && support.mayHurt(defender, attacker)
                && !chaining.contains(defender.getUniqueId())) {
            chaining.add(defender.getUniqueId());
            try {
                attacker.damage(num("static-charm.damage", 2), defender);
            } finally {
                chaining.remove(defender.getUniqueId());
            }
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            attacker.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, attacker.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.1);
            SoundSpec.of("entity.lightning_bolt.impact", 0.4f, 2f).playAt(attacker.getLocation());
        }
        if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter
                && "storm_bow".equals(arrow.getPersistentDataContainer().get(Keys.key(PROJECTILE), PersistentDataType.STRING))
                && !chaining.contains(shooter.getUniqueId()) && support.mayHurt(shooter, victim)) {
            lightning(shooter, victim, num("storm-bow.damage", 3));
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
            case THUNDER_HAMMER -> {
                if (ThreadLocalRandom.current().nextDouble() < num("thunder-hammer.hit-chance", 0.25)) {
                    lightning(p, victim, num("thunder-hammer.hit-damage", 4));
                }
            }
            case WIND_BLADE -> victim.setVelocity(victim.getVelocity().add(new Vector(0, 0.45, 0)));
            case DRAGON_BLADE -> {
                victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0, DRAGON);
            }
            default -> {
            }
        }
    }

    private void lightning(Player p, LivingEntity target, double damage) {
        target.getWorld().strikeLightningEffect(target.getLocation());
        chaining.add(p.getUniqueId());
        try {
            target.damage(damage, p);
        } finally {
            chaining.remove(p.getUniqueId());
        }
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0, STORM);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && event.getProjectile() instanceof AbstractArrow arrow
                && plugin.items().is(event.getBow(), CustomItem.STORM_BOW)) {
            arrow.getPersistentDataContainer().set(Keys.key(PROJECTILE), PersistentDataType.STRING, "storm_bow");
            trail(arrow, Particle.ELECTRIC_SPARK);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        String tag = event.getEntity().getPersistentDataContainer().get(Keys.key(PROJECTILE), PersistentDataType.STRING);
        if ("dragon_staff".equals(tag)) {
            Location at = event.getEntity().getLocation();
            World w = at.getWorld();
            w.spawnParticle(Particle.DRAGON_BREATH, at, 80, 1.2, 0.6, 1.2, 0.02);
            w.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
            SoundSpec.of("entity.generic.explode", 0.8f, 1.4f).playAt(at);
            double r = num("dragon-staff.radius", 3);
            chaining.add(shooter.getUniqueId());
            try {
                for (Entity e : w.getNearbyEntities(at, r, r, r)) {
                    if (e instanceof LivingEntity le && support.mayHurt(shooter, le)) {
                        le.damage(num("dragon-staff.damage", 6), shooter);
                        le.setFireTicks(Math.max(le.getFireTicks(), 60));
                    }
                }
            } finally {
                chaining.remove(shooter.getUniqueId());
            }
        } else if ("storm_bow".equals(tag) && event.getHitBlock() != null && ThreadLocalRandom.current().nextDouble() < 0.3) {
            event.getEntity().getWorld().strikeLightningEffect(event.getEntity().getLocation());
        }
    }

    private void trail(Entity projectile, Particle particle) {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!projectile.isValid() || ticks++ > 100 || (projectile instanceof AbstractArrow a && a.isInBlock())) {
                    cancel();
                    return;
                }
                projectile.getWorld().spawnParticle(particle, projectile.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private RayTraceResult rayEntity(Player p, double range) {
        return p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.5, e -> e instanceof LivingEntity && !e.equals(p) && support.mayHurt(p, e));
    }

    // =====================================================================================================
    // Mining, fishing, farming passives
    // =====================================================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player p = event.getPlayer();
        if (!plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.SMELTER_PICKAXE)) {
            return;
        }
        boolean smelted = false;
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            Material result = SMELT.get(stack.getType());
            if (result != null && stack.isSimilar(new ItemStack(stack.getType()))) {
                drop.setItemStack(new ItemStack(result, stack.getAmount()));
                smelted = true;
            }
        }
        if (smelted) {
            Location l = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
            l.getWorld().spawnParticle(Particle.FLAME, l, 8, 0.25, 0.25, 0.25, 0.02);
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                p.giveExp(1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block b = event.getBlock();
        if (offhand(p) != CustomItem.COMPOST_CHARM || !CROP_SEEDS.containsKey(b.getType()) || !(b.getBlockData() instanceof Ageable age)
                || age.getAge() < age.getMaximumAge() || ThreadLocalRandom.current().nextDouble() >= num("compost-charm.chance", 0.25)) {
            return;
        }
        for (ItemStack drop : b.getDrops(p.getInventory().getItemInMainHand(), p)) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
        }
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        if (!plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.LUCKY_ROD)) {
            return;
        }
        FishHook hook = event.getHook();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            hook.setMinWaitTime(Math.max(20, hook.getMinWaitTime() / 2));
            hook.setMaxWaitTime(Math.max(40, hook.getMaxWaitTime() / 2));
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() < num("lucky-rod.double-chance", 0.35)) {
            Item copy = caught.getWorld().dropItem(caught.getLocation(), caught.getItemStack().clone());
            copy.setVelocity(caught.getVelocity());
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, caught.getLocation(), 8, 0.2, 0.2, 0.2, 0);
        }
        PlayerData data = plugin.playerData().get(p);
        if (data != null && ThreadLocalRandom.current().nextDouble() < num("lucky-rod.coin-chance", 0.1)) {
            long coins = plugin.economy().earn(data, Currency.COINS, rnd(10, 60), false);
            plugin.messages().actionBar(p, "abilities.coin-pouch", "amount", coins);
        }
    }

    // =====================================================================================================
    // Charms (off hand) and the angel set
    // =====================================================================================================

    private CustomItem offhand(Player p) {
        return plugin.items().identify(p.getInventory().getItemInOffHand());
    }

    private void charmTick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CustomItem off = offhand(p);
            if (off == null) {
                continue;
            }
            switch (off) {
                case DRAGON_SCALE -> refresh(p, PotionEffectType.FIRE_RESISTANCE, 100, 0);
                case JUMP_CHARM -> refresh(p, PotionEffectType.JUMP_BOOST, 100, 0);
                case AQUA_CHARM -> {
                    refresh(p, PotionEffectType.WATER_BREATHING, 100, 0);
                    if (p.isInWater()) {
                        refresh(p, PotionEffectType.DOLPHINS_GRACE, 60, 0);
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player p
                && "angel".equals(plugin.armorSets().fullSet(p))) {
            event.setCancelled(true);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.4, 0.1, 0.4, 0.02);
        }
    }

    // ---- inventory helpers --------------------------------------------------------------------------------

    private int countCustom(PlayerInventory inv, CustomItem type) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (plugin.items().is(s, type)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private void removeCustom(PlayerInventory inv, CustomItem type, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            if (plugin.items().is(contents[i], type)) {
                int take = Math.min(amount, contents[i].getAmount());
                contents[i].setAmount(contents[i].getAmount() - take);
                amount -= take;
                if (contents[i].getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        inv.setStorageContents(contents);
    }

    private static int countPlain(PlayerInventory inv, Material type) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s != null && s.getType() == type && s.isSimilar(new ItemStack(type))) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static void removePlain(PlayerInventory inv, Material type, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack s = contents[i];
            if (s != null && s.getType() == type && s.isSimilar(new ItemStack(type))) {
                int take = Math.min(amount, s.getAmount());
                s.setAmount(s.getAmount() - take);
                amount -= take;
                contents[i] = s.getAmount() <= 0 ? null : s;
            }
        }
        inv.setStorageContents(contents);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chaining.remove(event.getPlayer().getUniqueId());
    }
}
