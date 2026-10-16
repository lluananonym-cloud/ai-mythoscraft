package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawner booster (spawner crate) and the relic crate items V1–V4:
 * V1 sort wand, endless water bucket · V2 vein pickaxe, repair charm · V3 paint brush, concrete mixer, soul sword
 * (heart steal, souls circling the holder), dwarf hat and growth totem (size via item attributes) · V4 power drill,
 * mega chainsaw, MinePro641 totem (reusable totem of undying), blossom crown, bark hand (strips whole trees) and the
 * zap pickaxe / sword / rod.
 */
public final class RelicListener implements Listener {

    /** spawner booster level stored on the spawner block */
    static final String BOOST_KEY = "spawner_boost";
    static final String BASE_MIN = "spawner_base_min";
    static final String BASE_MAX = "spawner_base_max";
    /** colour index of the paint brush, battery of the power drill */
    static final String PAINT_KEY = "paint_color";
    static final String BATTERY_KEY = "drill_battery";

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] PAINTABLE = {"_wool", "_concrete_powder", "_concrete", "_terracotta", "_stained_glass_pane",
            "_stained_glass", "_carpet", "_glazed_terracotta", "_candle", "_bed", "_shulker_box", "_banner"};

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Set<UUID> busy = new HashSet<>();
    private int maxBoost = 10;
    private int tick;

    public RelicListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
        plugin.tasks().repeat(this, 40, 4, "relic items", this::tickHolders);
    }

    public void reload(online.mythoscraft.core.config.ConfigReader root) {
        maxBoost = root.sub("spawner-booster").integer("max-level", 10, 1, 50);
    }

    private boolean holds(Player p, CustomItem type) {
        return plugin.items().is(p.getInventory().getItemInMainHand(), type);
    }

    private boolean offhand(Player p, CustomItem type) {
        return plugin.items().is(p.getInventory().getItemInOffHand(), type);
    }

    // ================================================================================================ spawner booster
    @EventHandler(priority = EventPriority.HIGH)
    public void onBoost(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!plugin.items().is(hand, CustomItem.SPAWNER_BOOSTER)) {
            return;
        }
        event.setCancelled(true);
        Block b = event.getClickedBlock();
        if (b.getType() != Material.SPAWNER || !(b.getState() instanceof CreatureSpawner spawner)) {
            plugin.messages().actionBar(p, "relic.booster.not-spawner");
            return;
        }
        if (!plugin.protection().allows(p, b.getLocation(), Flag.BUILD)) {
            plugin.messages().actionBar(p, "relic.no-permission");
            return;
        }
        var pdc = spawner.getPersistentDataContainer();
        int level = pdc.getOrDefault(Keys.key(BOOST_KEY), PersistentDataType.INTEGER, 0);
        if (level >= maxBoost) {
            plugin.messages().actionBar(p, "relic.booster.max", "max", maxBoost);
            return;
        }
        if (!pdc.has(Keys.key(BASE_MIN), PersistentDataType.INTEGER)) {
            pdc.set(Keys.key(BASE_MIN), PersistentDataType.INTEGER, spawner.getMinSpawnDelay());
            pdc.set(Keys.key(BASE_MAX), PersistentDataType.INTEGER, spawner.getMaxSpawnDelay());
        }
        level++;
        pdc.set(Keys.key(BOOST_KEY), PersistentDataType.INTEGER, level);
        applyBoost(spawner, level);
        spawner.update(true, false);
        AbilitySupport.consumeOne(hand);
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, c, 40, 0.5, 0.5, 0.5, 0.02);
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c, 20, 0.6, 0.6, 0.6, 0);
        SoundSpec.of("block.trial_spawner.ominous_activate", 1f, 1.3f).playAt(c);
        plugin.messages().send(p, "relic.booster.applied", "level", level, "max", maxBoost,
                "speed", String.format(Locale.ROOT, "%.1f", speedFactor(level)));
    }

    /** every booster makes the spawner 40 % faster (stacking), from 4 boosters on it also spawns one mob more */
    static double speedFactor(int level) {
        return 1 + level * 0.4;
    }

    private static void applyBoost(CreatureSpawner spawner, int level) {
        var pdc = spawner.getPersistentDataContainer();
        int baseMin = pdc.getOrDefault(Keys.key(BASE_MIN), PersistentDataType.INTEGER, 200);
        int baseMax = pdc.getOrDefault(Keys.key(BASE_MAX), PersistentDataType.INTEGER, 800);
        double f = speedFactor(level);
        int min = Math.max(10, (int) Math.round(baseMin / f));
        int max = Math.max(min + 1, (int) Math.round(baseMax / f));
        // lower the minimum first – the maximum may never fall below it
        spawner.setMinSpawnDelay(Math.min(min, spawner.getMaxSpawnDelay()));
        spawner.setMaxSpawnDelay(max);
        spawner.setMinSpawnDelay(min);
        spawner.setDelay(Math.min(spawner.getDelay(), max));
        spawner.setSpawnCount(4 + level / 4);
        spawner.setMaxNearbyEntities(6 + level * 2);
    }

    // ================================================================================================ V1 sort wand
    @EventHandler(priority = EventPriority.HIGH)
    public void onSort(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND
                || !holds(event.getPlayer(), CustomItem.SORT_WAND)) {
            return;
        }
        Player p = event.getPlayer();
        if (!(event.getClickedBlock().getState() instanceof Container container)) {
            return;
        }
        event.setCancelled(true);
        if (!plugin.protection().allows(p, event.getClickedBlock().getLocation(), Flag.CONTAINER)
                || support.onCooldown(p, p.getInventory().getItemInMainHand(), "sort_wand", 1)) {
            return;
        }
        Inventory inv = container.getInventory();
        int moved = sort(inv);
        Location c = event.getClickedBlock().getLocation().add(0.5, 1.1, 0.5);
        c.getWorld().spawnParticle(Particle.ENCHANT, c, 30, 0.4, 0.2, 0.4, 0.5);
        SoundSpec.of("item.bundle.insert", 1f, 1.3f).play(p);
        plugin.messages().actionBar(p, "relic.sort.done", "stacks", moved);
    }

    /** Merges equal stacks and sorts by item type; returns the number of stacks afterwards. */
    static int sort(Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            boolean merged = false;
            for (ItemStack t : items) {
                if (t.isSimilar(s) && t.getAmount() < t.getMaxStackSize()) {
                    int take = Math.min(s.getAmount(), t.getMaxStackSize() - t.getAmount());
                    t.setAmount(t.getAmount() + take);
                    s.setAmount(s.getAmount() - take);
                    if (s.getAmount() <= 0) {
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged) {
                items.add(s.clone());
            }
        }
        items.sort(Comparator.comparing((ItemStack s) -> s.getType().isBlock() ? 0 : 1)
                .thenComparing(s -> s.getType().name()).thenComparing(s -> -s.getAmount()));
        ItemStack[] out = new ItemStack[inv.getStorageContents().length];
        for (int i = 0; i < items.size() && i < out.length; i++) {
            out[i] = items.get(i);
        }
        inv.setStorageContents(out);
        return items.size();
    }

    // ================================================================================================ V1 endless bucket
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        ItemStack used = event.getPlayer().getInventory().getItem(event.getHand());
        if (plugin.items().is(used, CustomItem.INFINITE_BUCKET)) {
            event.setItemStack(used.clone());
        }
    }

    // ================================================================================================ V2 vein pickaxe
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVein(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block start = event.getBlock();
        if (busy.contains(p.getUniqueId()) || p.isSneaking() || !isOre(start.getType()) || !holds(p, CustomItem.VEIN_PICKAXE)) {
            return;
        }
        Material type = start.getType();
        busy.add(p.getUniqueId());
        try {
            int broken = 0;
            ArrayDeque<Block> todo = new ArrayDeque<>(neighbours(start));
            Set<Block> seen = new HashSet<>(todo);
            seen.add(start);
            while (!todo.isEmpty() && broken < 48) {
                Block b = todo.poll();
                if (!sameOre(b.getType(), type) || !plugin.protection().allows(p, b.getLocation(), Flag.BREAK)) {
                    continue;
                }
                if (p.breakBlock(b)) {
                    broken++;
                    for (Block n : neighbours(b)) {
                        if (seen.add(n)) {
                            todo.add(n);
                        }
                    }
                }
            }
            if (broken > 0) {
                start.getWorld().spawnParticle(Particle.WAX_OFF, start.getLocation().add(0.5, 0.5, 0.5), 25, 1.2, 1.2, 1.2, 0.5);
                SoundSpec.of("block.amethyst_block.resonate", 0.8f, 1.6f).play(p);
                plugin.messages().actionBar(p, "relic.vein.done", "blocks", broken + 1);
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    private static boolean isOre(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS || m == Material.AMETHYST_CLUSTER;
    }

    /** deepslate and normal variants of one ore count as the same vein */
    private static boolean sameOre(Material a, Material b) {
        return a == b || a.name().replace("DEEPSLATE_", "").equals(b.name().replace("DEEPSLATE_", ""));
    }

    private static List<Block> neighbours(Block b) {
        List<Block> out = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        out.add(b.getRelative(dx, dy, dz));
                    }
                }
            }
        }
        return out;
    }

    // ================================================================================================ V3 paint brush
    @EventHandler(priority = EventPriority.HIGH)
    public void onPaint(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !holds(p, CustomItem.PAINT_BRUSH)) {
            return;
        }
        ItemStack brush = p.getInventory().getItemInMainHand();
        int color = brush.getPersistentDataContainer().getOrDefault(Keys.key(PAINT_KEY), PersistentDataType.INTEGER, 14);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || (event.getAction() == Action.RIGHT_CLICK_BLOCK && p.isSneaking())) {
            event.setCancelled(true);
            color = (color + 1) % COLORS.length;
            int c = color;
            brush.editPersistentDataContainer(pdc -> pdc.set(Keys.key(PAINT_KEY), PersistentDataType.INTEGER, c));
            plugin.messages().actionBar(p, "relic.paint.color", "color", colorName(c));
            SoundSpec.of("item.dye.use", 1f, 0.8f + c * 0.05f).play(p);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block b = event.getClickedBlock();
        Material target = recolor(b.getType(), COLORS[color]);
        if (target == null) {
            return;
        }
        event.setCancelled(true);
        if (target == b.getType() || !plugin.protection().allows(p, b.getLocation(), Flag.BUILD)) {
            return;
        }
        org.bukkit.block.data.BlockData old = b.getBlockData();
        if (Tag.BEDS.isTagged(b.getType()) || Tag.SHULKER_BOXES.isTagged(b.getType()) || Tag.BANNERS.isTagged(b.getType())) {
            plugin.messages().actionBar(p, "relic.paint.not-this");
            return;
        }
        b.setBlockData(copyState(old, target), true);
        Particle.DustOptions dust = new Particle.DustOptions(dyeColor(color), 1.2f);
        b.getWorld().spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5), 18, 0.35, 0.35, 0.35, 0, dust);
        SoundSpec.of("entity.glow_squid.squirt", 0.5f, 1.6f).playAt(b.getLocation());
    }

    /** Keeps facing / waterlogging etc. where both block types have them. */
    private static org.bukkit.block.data.BlockData copyState(org.bukkit.block.data.BlockData old, Material target) {
        String s = old.getAsString();
        int bracket = s.indexOf('[');
        String props = bracket < 0 ? "" : s.substring(bracket);
        try {
            return org.bukkit.Bukkit.createBlockData(target, props);
        } catch (IllegalArgumentException e) {
            return target.createBlockData();
        }
    }

    private Object colorName(int c) {
        return net.kyori.adventure.text.Component.translatable("color.minecraft." + COLORS[c]).color(
                net.kyori.adventure.text.format.TextColor.color(dyeColor(c).asRGB()));
    }

    private static Color dyeColor(int c) {
        return org.bukkit.DyeColor.valueOf(COLORS[c].toUpperCase(Locale.ROOT)).getColor();
    }

    /** The same block in another colour, or null if the block has no colour variants. */
    static Material recolor(Material m, String color) {
        String name = m.name().toLowerCase(Locale.ROOT);
        for (String suffix : PAINTABLE) {
            if (!name.endsWith(suffix)) {
                continue;
            }
            String prefix = name.substring(0, name.length() - suffix.length());
            boolean colored = false;
            for (String c : COLORS) {
                if (prefix.equals(c)) {
                    colored = true;
                    break;
                }
            }
            if (!colored) {
                continue;
            }
            return Material.matchMaterial(color + suffix);
        }
        if (m == Material.TERRACOTTA) {
            return Material.matchMaterial(color + "_terracotta");
        }
        if (m == Material.GLASS) {
            return Material.matchMaterial(color + "_stained_glass");
        }
        if (m == Material.GLASS_PANE) {
            return Material.matchMaterial(color + "_stained_glass_pane");
        }
        return null;
    }

    // ================================================================================================ V3 concrete mixer
    @EventHandler(priority = EventPriority.HIGH)
    public void onMixer(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !holds(p, CustomItem.CONCRETE_MIXER)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // mixes every concrete powder in the inventory
            int n = 0;
            for (ItemStack s : p.getInventory().getStorageContents()) {
                if (s != null && s.getType().name().endsWith("_CONCRETE_POWDER") && !plugin.items().isCustom(s)) {
                    s.setType(Material.valueOf(s.getType().name().replace("_POWDER", "")));
                    n += s.getAmount();
                }
            }
            if (n > 0) {
                SoundSpec.of("block.brewing_stand.brew", 1f, 0.7f).play(p);
                p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.2);
            }
            plugin.messages().actionBar(p, "relic.mixer.inventory", "amount", n);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block center = event.getClickedBlock();
        if (!center.getType().name().endsWith("_CONCRETE_POWDER")) {
            return;
        }
        event.setCancelled(true);
        int n = 0;
        // the clicked block and all connected powder up to 3 blocks away
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = center.getRelative(dx, dy, dz);
                    if (b.getType().name().endsWith("_CONCRETE_POWDER") && plugin.protection().allows(p, b.getLocation(), Flag.BUILD)) {
                        b.setType(Material.valueOf(b.getType().name().replace("_POWDER", "")));
                        b.getWorld().spawnParticle(Particle.SPLASH, b.getLocation().add(0.5, 1, 0.5), 8, 0.3, 0.1, 0.3, 0.1);
                        n++;
                    }
                }
            }
        }
        if (n > 0) {
            SoundSpec.of("block.brewing_stand.brew", 1f, 0.8f).playAt(center.getLocation());
            SoundSpec.of("block.mud.place", 1f, 1.2f).playAt(center.getLocation());
        }
    }

    // ================================================================================================ V3 soul sword / V4 zap sword
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getEntity() instanceof LivingEntity target) || !support.mayHurt(p, target)) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (holds(p, CustomItem.SOUL_SWORD) && p.getAttackCooldown() > 0.9f && r.nextDouble() < 0.12) {
            // steals a heart: two more damage, the attacker gets it as absorption heart (and is healed a bit)
            event.setDamage(event.getDamage() + 2);
            double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + 2));
            p.setAbsorptionAmount(Math.min(8, p.getAbsorptionAmount() + 2));
            Location from = target.getLocation().add(0, 1, 0);
            Vector to = p.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            for (int i = 0; i <= 10; i++) {
                p.getWorld().spawnParticle(Particle.SOUL, from.clone().add(to.clone().multiply(i / 10.0)), 2, 0.05, 0.05, 0.05, 0.01);
            }
            p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, from, 6, 0.3, 0.3, 0.3, 0.1);
            SoundSpec.of("particle.soul_escape", 1.2f, 0.8f).playAt(from);
            plugin.messages().actionBar(p, "relic.soul.stolen");
        }
        if (holds(p, CustomItem.ZAP_SWORD) && p.getAttackCooldown() > 0.9f && r.nextDouble() < 0.2) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            event.setDamage(event.getDamage() + 4);
            target.setFireTicks(0);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
        }
    }

    // ================================================================================================ V4 power drill
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrill(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !holds(p, CustomItem.POWER_DRILL)
                || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR)) {
            return;
        }
        event.setCancelled(true);
        ItemStack drill = p.getInventory().getItemInMainHand();
        int battery = drill.getPersistentDataContainer().getOrDefault(Keys.key(BATTERY_KEY), PersistentDataType.INTEGER, 100);
        if (p.isSneaking()) {
            // recharge: one redstone dust = 10 %
            int need = (100 - battery + 9) / 10;
            int took = 0;
            for (ItemStack s : p.getInventory().getStorageContents()) {
                if (need <= 0) {
                    break;
                }
                if (s != null && s.getType() == Material.REDSTONE && !plugin.items().isCustom(s)) {
                    int t = Math.min(need, s.getAmount());
                    s.setAmount(s.getAmount() - t);
                    need -= t;
                    took += t;
                }
            }
            int now = Math.min(100, battery + took * 10);
            drill.editPersistentDataContainer(pdc -> pdc.set(Keys.key(BATTERY_KEY), PersistentDataType.INTEGER, now));
            plugin.messages().actionBar(p, took > 0 ? "relic.drill.charged" : "relic.drill.no-redstone", "battery", now);
            if (took > 0) {
                SoundSpec.of("block.beacon.power_select", 0.6f, 2f).play(p);
            }
            return;
        }
        if (battery < 20) {
            plugin.messages().actionBar(p, "relic.drill.empty", "battery", battery);
            SoundSpec.of("block.note_block.bass", 1f, 0.5f).play(p);
            return;
        }
        if (busy.contains(p.getUniqueId()) || support.onCooldown(p, drill, "power_drill", 2.5)) {
            return;
        }
        int left = battery - 20;
        drill.editPersistentDataContainer(pdc -> pdc.set(Keys.key(BATTERY_KEY), PersistentDataType.INTEGER, left));
        tunnel(p, left);
    }

    /** Drills a 3×3 tunnel, 8 blocks deep, one layer every 3 ticks (with sparks and a whirring sound). */
    private void tunnel(Player p, int battery) {
        BlockFace face = p.getTargetBlockFace(6) == null ? p.getFacing() : p.getTargetBlockFace(6).getOppositeFace();
        Block target = p.getTargetBlockExact(6);
        Block start = target != null ? target : p.getEyeLocation().getBlock().getRelative(face);
        Vector dir = face.getDirection();
        Vector side = Math.abs(dir.getY()) > 0.5 ? new Vector(1, 0, 0) : new Vector(-dir.getZ(), 0, dir.getX());
        Vector up = Math.abs(dir.getY()) > 0.5 ? new Vector(0, 0, 1) : new Vector(0, 1, 0);
        busy.add(p.getUniqueId());
        plugin.messages().actionBar(p, "relic.drill.running", "battery", battery);
        int[] layer = {0};
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = plugin.tasks().repeat(new Object(), 1, 3, "power drill", new Runnable() {
            @Override
            public void run() {
                if (!p.isOnline() || layer[0] >= 8 || !holds(p, CustomItem.POWER_DRILL)) {
                    busy.remove(p.getUniqueId());
                    if (task[0] != null) {
                        task[0].cancel();
                    }
                    return;
                }
                Block c = start.getRelative(dir.getBlockX() * layer[0], dir.getBlockY() * layer[0], dir.getBlockZ() * layer[0]);
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        Block blk = c.getRelative(side.getBlockX() * a + up.getBlockX() * b, side.getBlockY() * a + up.getBlockY() * b,
                                side.getBlockZ() * a + up.getBlockZ() * b);
                        if (blk.getType().isAir() || blk.isLiquid() || blk.getType().getHardness() < 0 || blk.getType().getHardness() > 50
                                || blk.getState() instanceof Container || !plugin.protection().allows(p, blk.getLocation(), Flag.BREAK)) {
                            continue;
                        }
                        busy.add(p.getUniqueId());
                        p.breakBlock(blk);
                    }
                }
                Location fx = c.getLocation().add(0.5, 0.5, 0.5);
                fx.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, fx, 14, 0.6, 0.6, 0.6, 0.15);
                fx.getWorld().spawnParticle(Particle.BLOCK, fx, 10, 0.6, 0.6, 0.6, 0, c.getType().isAir() ? Material.STONE.createBlockData()
                        : c.getBlockData());
                SoundSpec.of("block.grindstone.use", 0.5f, 1.8f).playAt(fx);
                layer[0]++;
            }
        });
    }

    // ================================================================================================ V4 mega chainsaw / bark hand
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSaw(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block start = event.getBlock();
        if (busy.contains(p.getUniqueId()) || p.isSneaking() || !Tag.LOGS.isTagged(start.getType()) || !holds(p, CustomItem.MEGA_CHAINSAW)) {
            return;
        }
        busy.add(p.getUniqueId());
        try {
            Material sapling = saplingFor(start.getType());
            Block ground = start.getRelative(BlockFace.DOWN);
            List<Block> tree = tree(start, true, 400);
            int logs = 0;
            for (Block b : tree) {
                if (!plugin.protection().allows(p, b.getLocation(), Flag.BREAK)) {
                    continue;
                }
                if (Tag.LOGS.isTagged(b.getType())) {
                    logs++;
                }
                p.breakBlock(b);
            }
            if (sapling != null && (ground.getType() == Material.DIRT || ground.getType() == Material.GRASS_BLOCK
                    || ground.getType() == Material.PODZOL) && start.getType().isAir()) {
                Material s = sapling;
                plugin.tasks().later(2, "replant", () -> {
                    if (start.getType().isAir()) {
                        start.setType(s);
                    }
                });
            }
            Location c = start.getLocation().add(0.5, 1, 0.5);
            c.getWorld().spawnParticle(Particle.BLOCK, c, 40, 0.6, 1.5, 0.6, 0, Material.OAK_LOG.createBlockData());
            SoundSpec.of("entity.zombie.break_wooden_door", 0.7f, 1.6f).playAt(c);
            plugin.messages().actionBar(p, "relic.chainsaw.done", "logs", logs);
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    /** Logs connected to {@code start} (and their leaves when {@code leaves} is set). */
    private static List<Block> tree(Block start, boolean leaves, int max) {
        List<Block> out = new ArrayList<>();
        ArrayDeque<Block> todo = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        todo.add(start);
        seen.add(start);
        while (!todo.isEmpty() && out.size() < max) {
            Block b = todo.poll();
            boolean log = Tag.LOGS.isTagged(b.getType());
            boolean leaf = leaves && Tag.LEAVES.isTagged(b.getType()) && !(b.getBlockData() instanceof org.bukkit.block.data.type.Leaves l && l.isPersistent());
            if (!log && !leaf) {
                continue;
            }
            if (b != start) {
                out.add(b);
            }
            for (Block n : neighbours(b)) {
                if (n.getY() >= start.getY() && seen.add(n)) {
                    todo.add(n);
                }
            }
        }
        return out;
    }

    private static Material saplingFor(Material log) {
        String n = log.name();
        for (String wood : new String[]{"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "CHERRY", "PALE_OAK"}) {
            if (n.equals(wood + "_LOG") || n.equals(wood + "_WOOD")) {
                return Material.matchMaterial(wood + "_SAPLING");
            }
        }
        if (n.startsWith("MANGROVE")) {
            return Material.MANGROVE_PROPAGULE;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBark(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || !offhand(p, CustomItem.BARK_HAND)) {
            return;
        }
        Block start = event.getClickedBlock();
        if (!Tag.LOGS.isTagged(start.getType()) || start.getType().name().startsWith("STRIPPED_")) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.OFF_HAND && !p.getInventory().getItemInMainHand().isEmpty()) {
            return;
        }
        if (support.onCooldown(p, p.getInventory().getItemInOffHand(), "bark_hand", 1)) {
            return;
        }
        List<Block> trunk = new ArrayList<>();
        trunk.add(start);
        // the whole tree: every connected log, also below the clicked one
        ArrayDeque<Block> todo = new ArrayDeque<>(List.of(start));
        Set<Block> seen = new HashSet<>(todo);
        while (!todo.isEmpty() && trunk.size() < 300) {
            Block b = todo.poll();
            for (Block n : neighbours(b)) {
                if (Tag.LOGS.isTagged(n.getType()) && !n.getType().name().startsWith("STRIPPED_") && seen.add(n)) {
                    trunk.add(n);
                    todo.add(n);
                }
            }
        }
        int n = 0;
        for (Block b : trunk) {
            Material stripped = Material.matchMaterial("STRIPPED_" + b.getType().name());
            if (stripped == null || !plugin.protection().allows(p, b.getLocation(), Flag.BUILD)) {
                continue;
            }
            org.bukkit.block.data.BlockData data = stripped.createBlockData();
            if (data instanceof Orientable o && b.getBlockData() instanceof Orientable old) {
                o.setAxis(old.getAxis());
            }
            b.setBlockData(data, false);
            b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, 0, Material.OAK_PLANKS.createBlockData());
            n++;
        }
        SoundSpec.of("item.axe.strip", 1f, 0.8f).playAt(start.getLocation());
        p.swingOffHand();
        plugin.messages().actionBar(p, "relic.bark.done", "logs", n);
    }

    // ================================================================================================ V4 MinePro641 totem
    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player p) || event.getHand() == null) {
            return;
        }
        ItemStack totem = p.getInventory().getItem(event.getHand());
        if (!plugin.items().is(totem, CustomItem.MINEPRO_TOTEM)) {
            return;
        }
        if (p.hasCooldown(totem)) {
            event.setCancelled(true);      // recharging: no rescue, and the totem is not lost either
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        ItemStack keep = totem.clone();
        keep.setAmount(1);
        EquipmentSlot hand = event.getHand();
        plugin.tasks().later(1, "minepro totem", () -> {
            if (!p.isOnline()) {
                return;
            }
            ItemStack now = p.getInventory().getItem(hand);
            if (now == null || now.isEmpty()) {
                p.getInventory().setItem(hand, keep);
            } else {
                CustomItemManager.give(p, keep);
            }
            p.setCooldown(keep, 20 * 60 * 10);
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 20, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 1));
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 120, 0.6, 1, 0.6, 0.6);
            plugin.messages().send(p, "relic.minepro.saved");
        });
    }

    // ================================================================================================ V4 zap pickaxe & rod
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZapMine(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (busy.contains(p.getUniqueId()) || !holds(p, CustomItem.ZAP_PICKAXE)) {
            return;
        }
        Block center = event.getBlock();
        if (!p.isSneaking()) {
            BlockFace face = p.getTargetBlockFace(6);
            Vector n = face == null ? new Vector(0, 1, 0) : face.getDirection();
            busy.add(p.getUniqueId());
            try {
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        int dx = n.getBlockX() != 0 ? 0 : a;
                        int dy = n.getBlockY() != 0 ? 0 : (n.getBlockX() != 0 ? a : b);
                        int dz = n.getBlockZ() != 0 ? 0 : b;
                        Block blk = center.getRelative(dx, dy, dz);
                        if (blk.equals(center) || blk.getType().isAir() || blk.isLiquid() || blk.getType().getHardness() < 0
                                || blk.getType().getHardness() > 50 || blk.getState() instanceof Container
                                || !plugin.protection().allows(p, blk.getLocation(), Flag.BREAK)) {
                            continue;
                        }
                        p.breakBlock(blk);
                    }
                }
            } finally {
                busy.remove(p.getUniqueId());
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.04) {
            center.getWorld().strikeLightningEffect(center.getLocation().add(0.5, 1, 0.5));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 8, 2, true, false, true));
            plugin.messages().actionBar(p, "relic.zap.charged");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onZapFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        if (!holds(p, CustomItem.ZAP_ROD)) {
            return;
        }
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> hook.setWaitTime(10, 60);
            case CAUGHT_FISH -> {
                if (event.getCaught() instanceof Item caught) {
                    caught.getWorld().strikeLightningEffect(hook.getLocation());
                    ItemStack extra = caught.getItemStack().clone();
                    plugin.tasks().later(1, "zap catch", () -> CustomItemManager.give(p, extra));
                    event.setExpToDrop(event.getExpToDrop() * 2 + 3);
                }
            }
            case CAUGHT_ENTITY -> {
                Entity e = event.getCaught();
                if (e != null && !(e instanceof Player) && support.mayHurt(p, e)) {
                    Vector pull = p.getLocation().toVector().subtract(e.getLocation().toVector()).multiply(0.35).setY(0.6);
                    e.setVelocity(pull);
                    e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
                }
            }
            default -> {
            }
        }
    }

    // ================================================================================================ held / worn effects
    private void tickHolders() {
        tick++;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (holds(p, CustomItem.SOUL_SWORD)) {
                Location base = p.getLocation().add(0, 1.0, 0);
                for (int i = 0; i < 3; i++) {
                    double a = tick * 0.32 + i * (Math.PI * 2 / 3);
                    Location at = base.clone().add(Math.cos(a) * 0.9, Math.sin(tick * 0.2 + i) * 0.4, Math.sin(a) * 0.9);
                    p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 1, 0, 0, 0, 0);
                    if (tick % 3 == i) {
                        p.getWorld().spawnParticle(Particle.SOUL, at, 1, 0.05, 0.05, 0.05, 0.005);
                    }
                }
            }
            CustomItem head = plugin.items().identify(p.getInventory().getHelmet());
            if (head == CustomItem.BLOSSOM_CROWN) {
                Location base = p.getLocation();
                for (int i = 0; i < 2; i++) {
                    double a = tick * 0.25 + i * Math.PI;
                    double h = 0.2 + (tick % 16) * 0.12;
                    p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, base.clone().add(Math.cos(a) * 1.1, h, Math.sin(a) * 1.1), 1, 0, 0, 0, 0);
                }
                if (tick % 4 == 0) {
                    p.getWorld().spawnParticle(Particle.FALLING_SPORE_BLOSSOM, base.clone().add(0, 2.3, 0), 2, 0.6, 0.1, 0.6, 0);
                    p.getWorld().spawnParticle(Particle.COMPOSTER, base.clone().add(0, 1.2, 0), 2, 0.7, 0.6, 0.7, 0);
                }
                if (tick % 25 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, true, false, true));
                }
            }
            if (holds(p, CustomItem.MEGA_CHAINSAW) && tick % 5 == 0) {
                p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 0.9, 0).add(p.getLocation().getDirection().multiply(0.6)),
                        2, 0.05, 0.05, 0.05, 0.01);
            }
            if (holds(p, CustomItem.ZAP_SWORD) || holds(p, CustomItem.ZAP_PICKAXE) || holds(p, CustomItem.ZAP_ROD)) {
                if (tick % 2 == 0) {
                    p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1.1, 0)
                            .add(p.getLocation().getDirection().setY(0).normalize().multiply(0.5)), 2, 0.2, 0.3, 0.2, 0.05);
                }
            }
            if (offhand(p, CustomItem.REPAIR_CHARM) && tick % 15 == 0) {
                repairOne(p);
            }
        }
    }

    /** Repair charm: one durability point on the held item and on every armour piece. */
    private static void repairOne(Player p) {
        List<ItemStack> items = new ArrayList<>(List.of(p.getInventory().getArmorContents()));
        items.add(p.getInventory().getItemInMainHand());
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty() && s.getItemMeta() instanceof Damageable d && d.hasDamage()) {
                s.editMeta(Damageable.class, m -> m.setDamage(Math.max(0, m.getDamage() - 2)));
            }
        }
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
    }
}
