package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.protection.DamageSources;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Abilities of the custom tools. Every block change goes through Player#breakBlock, so protection still applies. */
public final class ToolListener implements Listener {

    private static final Particle.DustOptions SCULK = new Particle.DustOptions(Color.fromRGB(0x0B6E79), 1.4f);

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Set<UUID> busy = new HashSet<>();

    private boolean hammer = true;
    private boolean excavator = true;
    private float maxHardness = 50f;
    private boolean timber = true;
    private int maxLogs = 128;
    private boolean blade = true;
    private double bladeDamage = 10;
    private double bladeRange = 16;
    private int bladeCooldown = 8;
    private boolean staff = true;
    private double staffDamage = 8;
    private double staffRadius = 3.5;
    private double staffRange = 40;
    private int staffCooldown = 15;

    public ToolListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("tools");
        hammer = cfg.bool("mythos-hammer.enabled", true);
        maxHardness = (float) cfg.decimal("mythos-hammer.max-hardness", 50, 0, 1000);
        excavator = cfg.bool("excavator.enabled", true);
        timber = cfg.bool("timber-axe.enabled", true);
        maxLogs = cfg.integer("timber-axe.max-logs", 128, 2, 1024);
        blade = cfg.bool("warden-blade.enabled", true);
        bladeDamage = cfg.decimal("warden-blade.damage", 10, 0, 1000);
        bladeRange = cfg.decimal("warden-blade.range", 16, 2, 64);
        bladeCooldown = cfg.integer("warden-blade.cooldown-seconds", 8, 0, 600);
        staff = cfg.bool("meteor-staff.enabled", true);
        staffDamage = cfg.decimal("meteor-staff.damage", 8, 0, 1000);
        staffRadius = cfg.decimal("meteor-staff.radius", 3.5, 0.5, 16);
        staffRange = cfg.decimal("meteor-staff.range", 40, 4, 120);
        staffCooldown = cfg.integer("meteor-staff.cooldown-seconds", 15, 0, 600);
    }

    // ---- mining tools -------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (busy.contains(player.getUniqueId()) || player.isSneaking()) {
            return;
        }
        CustomItem type = plugin.items().identify(player.getInventory().getItemInMainHand());
        if (type == null) {
            return;
        }
        switch (type) {
            case MYTHOS_HAMMER -> {
                if (hammer) {
                    areaBreak(player, event.getBlock(), Tag.MINEABLE_PICKAXE);
                }
            }
            case EXCAVATOR -> {
                if (excavator) {
                    areaBreak(player, event.getBlock(), Tag.MINEABLE_SHOVEL);
                }
            }
            case TIMBER_AXE -> {
                if (timber && Tag.LOGS.isTagged(event.getBlock().getType())) {
                    fellTree(player, event.getBlock(), CustomItem.TIMBER_AXE);
                }
            }
            case NEWYEAR_AXE -> {
                if (Tag.LOGS.isTagged(event.getBlock().getType())) {
                    fellTree(player, event.getBlock(), CustomItem.NEWYEAR_AXE);
                }
            }
            case NEWYEAR_PICKAXE -> cubeBreak(player, event.getBlock(), Tag.MINEABLE_PICKAXE, CustomItem.NEWYEAR_PICKAXE);
            default -> {
            }
        }
    }

    private void areaBreak(Player player, Block center, Tag<Material> mineable) {
        if (!mineable.isTagged(center.getType())) {
            return;
        }
        BlockFace face = player.getTargetBlockFace(6);
        if (face == null) {
            float pitch = player.getLocation().getPitch();
            face = Math.abs(pitch) > 50 ? BlockFace.UP : player.getFacing();
        }
        busy.add(player.getUniqueId());
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    Block target = switch (face) {
                        case UP, DOWN -> center.getRelative(a, 0, b);
                        case NORTH, SOUTH -> center.getRelative(a, b, 0);
                        default -> center.getRelative(0, b, a);
                    };
                    Material m = target.getType();
                    if (m.isAir() || !mineable.isTagged(m)) {
                        continue;
                    }
                    float hardness = m.getHardness();
                    if (hardness < 0 || hardness > maxHardness) {
                        continue;
                    }
                    player.breakBlock(target);
                    if (plugin.items().identify(player.getInventory().getItemInMainHand()) == null) {
                        return; // the tool broke
                    }
                }
            }
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    /**
     * Silvester pickaxe: mines a 5×5×5 cube (5×5 across the face you hit, 5 deep), spread over a few ticks.
     * Sneaking mines a single block.
     */
    private void cubeBreak(Player player, Block center, Tag<Material> mineable, CustomItem tool) {
        if (!mineable.isTagged(center.getType())) {
            return;
        }
        BlockFace face = player.getTargetBlockFace(6);
        if (face == null) {
            float pitch = player.getLocation().getPitch();
            face = Math.abs(pitch) > 50 ? (pitch > 0 ? BlockFace.UP : BlockFace.DOWN) : player.getFacing().getOppositeFace();
        }
        // dig away from the player: opposite of the face that was hit
        BlockFace into = face.getOppositeFace();
        List<Block> blocks = new ArrayList<>();
        for (int d = 0; d < 5; d++) {
            Block slice = center.getRelative(into, d);
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    if (d == 0 && a == 0 && b == 0) {
                        continue;
                    }
                    Block target = switch (face) {
                        case UP, DOWN -> slice.getRelative(a, 0, b);
                        case NORTH, SOUTH -> slice.getRelative(a, b, 0);
                        default -> slice.getRelative(0, b, a);
                    };
                    blocks.add(target);
                }
            }
        }
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (!player.isOnline() || plugin.items().identify(player.getInventory().getItemInMainHand()) != tool) {
                    cancel();
                    return;
                }
                busy.add(player.getUniqueId());
                try {
                    for (int i = 0; i < 40 && index < blocks.size(); i++, index++) {
                        Block target = blocks.get(index);
                        Material m = target.getType();
                        if (m.isAir() || !mineable.isTagged(m)) {
                            continue;
                        }
                        float hardness = m.getHardness();
                        if (hardness < 0 || hardness > maxHardness) {
                            continue;
                        }
                        player.breakBlock(target);
                        if (plugin.items().identify(player.getInventory().getItemInMainHand()) != tool) {
                            cancel();
                            return;
                        }
                    }
                } finally {
                    busy.remove(player.getUniqueId());
                }
                if (index >= blocks.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void fellTree(Player player, Block start, CustomItem tool) {
        List<Block> logs = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        boolean natural = false;
        while (!queue.isEmpty() && logs.size() < maxLogs) {
            Block b = queue.poll();
            logs.add(b);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (seen.add(n)) {
                            if (Tag.LOGS.isTagged(n.getType())) {
                                queue.add(n);
                            } else if (!natural && n.getBlockData() instanceof Leaves leaves && !leaves.isPersistent()) {
                                natural = true;
                            }
                        }
                    }
                }
            }
        }
        logs.remove(start);
        if (!natural || logs.isEmpty()) {
            return;
        }
        logs.sort(Comparator.comparingInt(Block::getY));
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (!player.isOnline() || plugin.items().identify(player.getInventory().getItemInMainHand()) != tool) {
                    cancel();
                    return;
                }
                busy.add(player.getUniqueId());
                try {
                    for (int i = 0; i < 6 && index < logs.size(); i++, index++) {
                        Block log = logs.get(index);
                        if (Tag.LOGS.isTagged(log.getType())) {
                            player.breakBlock(log);
                        }
                    }
                } finally {
                    busy.remove(player.getUniqueId());
                }
                if (index >= logs.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    // ---- ability tools ------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        CustomItem type = plugin.items().identify(item);
        if (type == null) {
            return;
        }
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        switch (type) {
            case REGION_WAND -> {
                if (!player.hasPermission(Perms.REGION) || event.getClickedBlock() == null) {
                    return;
                }
                event.setCancelled(true);
                int index = action == Action.LEFT_CLICK_BLOCK ? 0 : 1;
                plugin.selections().set(player, index, event.getClickedBlock().getLocation());
                plugin.messages().send(player, index == 0 ? "region.pos1" : "region.pos2",
                        "x", event.getClickedBlock().getX(), "y", event.getClickedBlock().getY(), "z", event.getClickedBlock().getZ());
            }
            case WARDEN_BLADE -> {
                if (right && blade) {
                    event.setCancelled(true);
                    sonicBeam(player, item);
                }
            }
            case METEOR_STAFF -> {
                if (right && staff) {
                    event.setCancelled(true);
                    meteorStrike(player, item);
                }
            }
            case METEOR_ROCK, METEOR_CORE -> event.setCancelled(true);
            default -> {
            }
        }
    }

    private void sonicBeam(Player player, ItemStack item) {
        if (support.onCooldown(player, item, "warden_blade", bladeCooldown)) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult hit = world.rayTrace(eye, dir, bladeRange, FluidCollisionMode.NEVER, true, 0.6,
                e -> e instanceof LivingEntity && !e.equals(player) && !(e instanceof ArmorStand) && !DamageSources.isPluginEntity(e));
        double length = hit == null ? bladeRange : hit.getHitPosition().distance(eye.toVector());
        for (double d = 1; d <= length; d += 1.2) {
            world.spawnParticle(Particle.SONIC_BOOM, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
        SoundSpec.of("entity.warden.sonic_boom", 1.2f, 1.1f).playAt(player.getLocation());
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target && support.mayHurt(player, target)) {
            target.damage(bladeDamage, player);
            target.setVelocity(target.getVelocity().add(dir.clone().multiply(1.1).setY(0.35)));
            world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, SCULK);
        }
    }

    private void meteorStrike(Player player, ItemStack item) {
        RayTraceResult target = player.rayTraceBlocks(staffRange, FluidCollisionMode.NEVER);
        if (target == null || target.getHitBlock() == null) {
            plugin.messages().actionBar(player, "tools.no-target");
            return;
        }
        if (support.onCooldown(player, item, "meteor_staff", staffCooldown)) {
            return;
        }
        Location impact = target.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
        World world = impact.getWorld();
        SoundSpec.of("entity.blaze.shoot", 1f, 0.6f).playAt(player.getLocation());
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (step < 14) {
                    double height = 16 - step * (16.0 / 14);
                    Location p = impact.clone().add(0, height, 0);
                    world.spawnParticle(Particle.FLAME, p, 12, 0.25, 0.25, 0.25, 0.02);
                    world.spawnParticle(Particle.LARGE_SMOKE, p, 4, 0.2, 0.2, 0.2, 0.01);
                    step++;
                    return;
                }
                cancel();
                world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
                world.spawnParticle(Particle.LAVA, impact, 25, 1.2, 0.4, 1.2, 0);
                world.spawnParticle(Particle.FLAME, impact, 60, staffRadius / 2, 0.3, staffRadius / 2, 0.08);
                SoundSpec.of("entity.generic.explode", 1.2f, 0.9f).playAt(impact);
                if (!player.isOnline()) {
                    return;
                }
                for (Entity e : world.getNearbyEntities(impact, staffRadius, staffRadius, staffRadius)) {
                    if (e instanceof LivingEntity le && !e.equals(player) && !(e instanceof ArmorStand) && support.mayHurt(player, le)) {
                        le.damage(staffDamage, player);
                        Vector push = le.getLocation().toVector().subtract(impact.toVector()).setY(0);
                        if (push.lengthSquared() > 0.01) {
                            push.normalize().multiply(0.6);
                        }
                        le.setVelocity(le.getVelocity().add(push.setY(0.45)));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
    }
}
