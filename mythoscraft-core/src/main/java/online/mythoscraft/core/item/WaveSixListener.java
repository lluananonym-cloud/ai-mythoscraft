package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.teleport.HomeManager;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sixth item wave: frost walker charm, home stone, magic bean (climbable beanstalk), web shooter, player tracker,
 * rainbow potion, confetti cannon and lucky horseshoe. Party hat and golden crown are pure cosmetics (worn on the head).
 */
public final class WaveSixListener implements Listener {

    private static final PotionEffectType[] RAINBOW = {PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
            PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.WATER_BREATHING, PotionEffectType.NIGHT_VISION, PotionEffectType.LUCK};

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private ConfigReader cfg;

    public WaveSixListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 35, 4, "frost walker", this::tick);
    }

    private double num(String path, double def) {
        return cfg.decimal(path, def, 0, 100_000);
    }

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CustomItem off = plugin.items().identify(p.getInventory().getItemInOffHand());
            if (off == CustomItem.LUCKY_HORSESHOE) {
                PotionEffect luck = p.getPotionEffect(PotionEffectType.LUCK);
                if (luck == null || luck.getDuration() < 60) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 120, 1, true, false, true));
                }
            }
            if (off != CustomItem.FROST_WALKER_CHARM || p.isFlying() || p.isInsideVehicle()) {
                continue;
            }
            Block under = p.getLocation().getBlock().getRelative(0, -1, 0);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz > 5) {
                        continue;
                    }
                    Block b = under.getRelative(dx, 0, dz);
                    if (b.getType() == Material.WATER && b.getBlockData() instanceof Levelled lv && lv.getLevel() == 0
                            && b.getRelative(0, 1, 0).getType().isAir() && plugin.protection().query(b, Flag.BUILD)) {
                        b.setType(Material.FROSTED_ICE);
                    }
                }
            }
        }
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
            case HOME_STONE -> {
                event.setCancelled(true);
                PlayerData data = plugin.playerData().get(p);
                if (data == null) {
                    return;
                }
                if (data.homes().isEmpty()) {
                    plugin.messages().send(p, "homes.none");
                    return;
                }
                if (plugin.auctions().blockedHere(p)) {
                    plugin.messages().send(p, "arena.leave-first");
                    return;
                }
                String name = data.homes().containsKey(HomeManager.DEFAULT_NAME) ? HomeManager.DEFAULT_NAME
                        : data.homes().keySet().iterator().next();
                AbilitySupport.consumeOne(item);
                plugin.homes().teleport(p, data, name);
            }
            case MAGIC_BEAN -> {
                Block soil = event.getClickedBlock();
                if (soil == null) {
                    return;
                }
                event.setCancelled(true);
                if (soil.getType() != Material.GRASS_BLOCK && soil.getType() != Material.DIRT && soil.getType() != Material.FARMLAND
                        && soil.getType() != Material.MOSS_BLOCK) {
                    plugin.messages().actionBar(p, "abilities.bean-soil");
                    return;
                }
                int height = (int) num("magic-bean.height", 14);
                List<Block> column = new ArrayList<>();
                for (int y = 1; y <= height; y++) {
                    Block b = soil.getRelative(0, y, 0);
                    if (!b.getType().isAir() || !plugin.protection().allows(p, b, Flag.BUILD)) {
                        break;
                    }
                    column.add(b);
                }
                if (column.size() < 4) {
                    plugin.messages().actionBar(p, "abilities.bean-space");
                    return;
                }
                AbilitySupport.consumeOne(item);
                for (int i = 0; i < column.size(); i++) {
                    Block b = column.get(i);
                    int delay = i * 2 + 1;
                    boolean top = i == column.size() - 1;
                    plugin.tasks().later(delay, "beanstalk", () -> {
                        if (!b.getType().isAir()) {
                            return;
                        }
                        b.setType(top ? Material.AZALEA_LEAVES : Material.MOSS_BLOCK);
                        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, 0);
                        if (!top) {
                            for (Block side : new Block[]{b.getRelative(1, 0, 0), b.getRelative(-1, 0, 0), b.getRelative(0, 0, 1), b.getRelative(0, 0, -1)}) {
                                if (side.getType().isAir() && ThreadLocalRandom.current().nextInt(3) == 0 && plugin.protection().query(side, Flag.BUILD)) {
                                    side.setType(Material.VINE);
                                    if (side.getBlockData() instanceof org.bukkit.block.data.MultipleFacing mf) {
                                        org.bukkit.block.BlockFace toStalk = side.getFace(b);
                                        if (toStalk != null && mf.getAllowedFaces().contains(toStalk)) {
                                            mf.setFace(toStalk, true);
                                            side.setBlockData(mf, false);
                                        } else {
                                            side.setType(Material.AIR);
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
                SoundSpec.of("block.azalea.place", 1f, 0.8f).playAt(soil.getLocation());
            }
            case WEB_SHOOTER -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "web_shooter", num("web-shooter.cooldown-seconds", 4))) {
                    return;
                }
                Snowball ball = p.launchProjectile(Snowball.class);
                ball.setItem(new ItemStack(Material.COBWEB));
                ball.setVelocity(p.getLocation().getDirection().multiply(1.8));
                ball.getPersistentDataContainer().set(Keys.key("projectile"), PersistentDataType.STRING, "web_shooter");
                SoundSpec.of("entity.spider.ambient", 1f, 1.6f).playAt(p.getLocation());
            }
            case PLAYER_TRACKER -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "player_tracker", num("player-tracker.cooldown-seconds", 10))) {
                    return;
                }
                Player nearest = null;
                double best = Math.pow(num("player-tracker.range", 500), 2);
                for (Player other : p.getWorld().getPlayers()) {
                    if (!other.equals(p) && p.canSee(other) && !other.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                        double d = other.getLocation().distanceSquared(p.getLocation());
                        if (d < best) {
                            best = d;
                            nearest = other;
                        }
                    }
                }
                if (nearest == null) {
                    plugin.messages().actionBar(p, "abilities.tracker-none");
                    return;
                }
                p.setCompassTarget(nearest.getLocation());
                Location eye = p.getEyeLocation();
                org.bukkit.util.Vector dir = nearest.getLocation().toVector().subtract(eye.toVector()).setY(0).normalize();
                for (int i = 1; i <= 8; i++) {
                    p.spawnParticle(Particle.ELECTRIC_SPARK, eye.clone().add(dir.clone().multiply(i * 0.7)), 2, 0.02, 0.02, 0.02, 0);
                }
                plugin.messages().actionBar(p, "abilities.tracker-found", "player", nearest.getName(), "distance", Math.round(Math.sqrt(best)));
            }
            case CONFETTI_CANNON -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "confetti_cannon", 2)) {
                    return;
                }
                Location at = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5));
                int[] colors = {0xF472B6, 0x38BDF8, 0xFDE047, 0x86EFAC, 0xC084FC, 0xFB923C};
                for (int c : colors) {
                    p.getWorld().spawnParticle(Particle.DUST, at, 20, 1.2, 0.8, 1.2, 0, new Particle.DustOptions(Color.fromRGB(c), 1.1f));
                }
                p.getWorld().spawnParticle(Particle.FIREWORK, at, 30, 0.6, 0.6, 0.6, 0.1);
                SoundSpec.of("entity.firework_rocket.twinkle", 1f, 1.4f).playAt(at);
                SoundSpec.of("entity.player.levelup", 0.5f, 1.8f).playAt(at);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onWebHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)
                || !"web_shooter".equals(event.getEntity().getPersistentDataContainer().get(Keys.key("projectile"), PersistentDataType.STRING))) {
            return;
        }
        if (event.getHitEntity() instanceof LivingEntity le && support.mayHurt(shooter, le)) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
            le.getWorld().spawnParticle(Particle.BLOCK, le.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, Material.COBWEB.createBlockData());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.items().identify(event.getItem()) != CustomItem.RAINBOW_POTION) {
            return;
        }
        Player p = event.getPlayer();
        List<PotionEffectType> pool = new ArrayList<>(List.of(RAINBOW));
        java.util.Collections.shuffle(pool);
        for (int i = 0; i < 3; i++) {
            p.addPotionEffect(new PotionEffect(pool.get(i), 20 * 60, 0));
        }
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(ThreadLocalRandom.current().nextInt(0xFFFFFF)), 1.2f));
        plugin.messages().actionBar(p, "abilities.rainbow");
    }
}
