package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.reward.RewardTable;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fifth item wave: return crystal, sponge bomb, repair hammer, enchant scroll, cloud in a bottle, hunger charm,
 * soul jar (stores XP) and lucky dice.
 */
public final class WaveFiveListener implements Listener {

    private static final String MARK = "return_mark";
    private static final String STORED_XP = "stored_xp";

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private ConfigReader cfg;
    private List<List<Reward>> diceRewards = List.of();

    public WaveFiveListener(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        cfg = root.sub("items");
        List<List<Reward>> sides = new ArrayList<>();
        for (int side = 1; side <= 6; side++) {
            List<Reward> list = RewardTable.parseList(cfg, "lucky-dice.side-" + side);
            if (list.isEmpty()) {
                list = List.of(Reward.parse(switch (side) {
                    case 1 -> "coins 50";
                    case 2 -> "coins 150";
                    case 3 -> "coins 400";
                    case 4 -> "gems 2";
                    case 5 -> "tool key_fragment 2";
                    default -> "key vip 1";
                }));
            }
            sides.add(list);
        }
        diceRewards = sides;
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
            case RETURN_CRYSTAL -> {
                event.setCancelled(true);
                String mark = item.getPersistentDataContainer().get(Keys.key(MARK), PersistentDataType.STRING);
                if (mark == null || p.isSneaking()) {
                    String here = LocationCodec.toString(p.getLocation());
                    item.editMeta(m -> {
                        m.getPersistentDataContainer().set(Keys.key(MARK), PersistentDataType.STRING, here);
                        m.lore(plugin.messages().getList("abilities.crystal-lore", "location", LocationCodec.describe(p.getLocation()))
                                .stream().map(Text::noItalic).toList());
                        m.setEnchantmentGlintOverride(true);
                    });
                    plugin.messages().actionBar(p, "abilities.crystal-marked");
                    SoundSpec.of("block.amethyst_block.resonate", 1f, 1.4f).play(p);
                    return;
                }
                Location target = LocationCodec.fromString(mark);
                if (target == null || plugin.auctions().blockedHere(p)) {
                    plugin.messages().actionBar(p, "abilities.crystal-invalid");
                    return;
                }
                if (support.onCooldown(p, item, "return_crystal", num("return-crystal.cooldown-seconds", 120))) {
                    return;
                }
                plugin.teleports().teleport(p, target, 3, true, plugin.messages().get("abilities.crystal-target"), null);
            }
            case SPONGE_BOMB -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "sponge_bomb", 2)) {
                    return;
                }
                int r = (int) num("sponge-bomb.radius", 4);
                int dried = 0;
                Block c = p.getLocation().getBlock();
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx * dx + dy * dy + dz * dz > r * r) {
                                continue;
                            }
                            Block b = c.getRelative(dx, dy, dz);
                            if (!plugin.protection().allows(p, b, Flag.BUILD)) {
                                continue;
                            }
                            if (b.getType() == Material.WATER || b.getType() == Material.SEAGRASS || b.getType() == Material.KELP_PLANT
                                    || b.getType() == Material.KELP || b.getType() == Material.TALL_SEAGRASS) {
                                b.setType(Material.AIR);
                                dried++;
                            } else if (b.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) {
                                w.setWaterlogged(false);
                                b.setBlockData(w);
                                dried++;
                            }
                        }
                    }
                }
                if (dried > 0) {
                    AbilitySupport.consumeOne(item);
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 60, r / 2.0, r / 2.0, r / 2.0, 0.05);
                SoundSpec.of("block.sponge.absorb", 1f, 1f).playAt(p.getLocation());
                plugin.messages().actionBar(p, "abilities.sponge", "count", dried);
            }
            case REPAIR_HAMMER -> {
                event.setCancelled(true);
                ItemStack target = p.getInventory().getItemInOffHand();
                if (!(target.getItemMeta() instanceof Damageable dmg) || !dmg.hasDamage() || target.getType().getMaxDurability() <= 0) {
                    plugin.messages().actionBar(p, "abilities.repair-none");
                    return;
                }
                int repair = (int) Math.ceil(target.getType().getMaxDurability() * num("repair-hammer.percent", 0.25));
                target.editMeta(Damageable.class, d -> d.setDamage(Math.max(0, d.getDamage() - repair)));
                AbilitySupport.consumeOne(item);
                SoundSpec.of("block.anvil.use", 0.8f, 1.4f).play(p);
                plugin.messages().actionBar(p, "abilities.repaired", "amount", repair);
            }
            case ENCHANT_SCROLL -> {
                event.setCancelled(true);
                ItemStack target = p.getInventory().getItemInOffHand();
                List<Enchantment> upgradable = new ArrayList<>();
                for (Map.Entry<Enchantment, Integer> e : target.getEnchantments().entrySet()) {
                    if (e.getValue() < e.getKey().getMaxLevel()) {
                        upgradable.add(e.getKey());
                    }
                }
                if (target.isEmpty() || upgradable.isEmpty()) {
                    plugin.messages().actionBar(p, "abilities.enchant-none");
                    return;
                }
                Enchantment ench = upgradable.get(ThreadLocalRandom.current().nextInt(upgradable.size()));
                int level = target.getEnchantmentLevel(ench) + 1;
                target.addUnsafeEnchantment(ench, level);
                AbilitySupport.consumeOne(item);
                p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.5, 0), 60, 0.5, 0.5, 0.5, 1);
                SoundSpec.of("block.enchantment_table.use", 1f, 1.2f).play(p);
                plugin.messages().send(p, "abilities.enchanted", "enchantment", ench.displayName(level));
            }
            case CLOUD_BOTTLE -> {
                event.setCancelled(true);
                if (((org.bukkit.entity.Entity) p).isOnGround()) {
                    plugin.messages().actionBar(p, "abilities.cloud-ground");
                    return;
                }
                if (support.onCooldown(p, item, "cloud_bottle", num("cloud-bottle.cooldown-seconds", 4))) {
                    return;
                }
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX() * 1.2, num("cloud-bottle.power", 0.85), v.getZ() * 1.2));
                p.setFallDistance(0);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.4, 0.1, 0.4, 0.03);
                SoundSpec.of("entity.breeze.jump", 1f, 1.3f).playAt(p.getLocation());
            }
            case SOUL_JAR -> {
                event.setCancelled(true);
                int stored = item.getPersistentDataContainer().getOrDefault(Keys.key(STORED_XP), PersistentDataType.INTEGER, 0);
                int total = totalXp(p);
                if (p.isSneaking() || total == 0) {
                    if (stored == 0) {
                        plugin.messages().actionBar(p, "abilities.jar-empty");
                        return;
                    }
                    p.giveExp(stored);
                    setStored(item, 0);
                    plugin.messages().actionBar(p, "abilities.jar-released", "xp", stored);
                    SoundSpec.of("entity.player.levelup", 0.8f, 1.2f).play(p);
                } else {
                    p.setExp(0);
                    p.setLevel(0);
                    p.setTotalExperience(0);
                    setStored(item, stored + total);
                    plugin.messages().actionBar(p, "abilities.jar-stored", "xp", stored + total);
                    SoundSpec.of("block.respawn_anchor.charge", 0.8f, 1.4f).play(p);
                }
            }
            case LUCKY_DICE -> {
                event.setCancelled(true);
                if (support.onCooldown(p, item, "lucky_dice", 1)) {
                    return;
                }
                AbilitySupport.consumeOne(item);
                int side = 1 + ThreadLocalRandom.current().nextInt(6);
                List<Reward> options = diceRewards.get(side - 1);
                Reward r = options.get(ThreadLocalRandom.current().nextInt(options.size()));
                r.give(plugin, p, 1.0);
                p.getWorld().spawnParticle(side == 6 ? Particle.TOTEM_OF_UNDYING : Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0),
                        side * 5, 0.4, 0.4, 0.4, 0.1);
                SoundSpec.of("block.note_block.pling", 1f, 0.6f + side * 0.2f).play(p);
                plugin.messages().send(p, "abilities.dice", "side", side, "reward", r.describe(plugin, 1.0));
            }
            default -> {
            }
        }
    }

    private void setStored(ItemStack jar, int xp) {
        jar.editMeta(m -> {
            m.getPersistentDataContainer().set(Keys.key(STORED_XP), PersistentDataType.INTEGER, xp);
            m.lore(plugin.messages().getList("abilities.jar-lore", "xp", xp).stream().map(Text::noItalic).toList());
            m.setEnchantmentGlintOverride(xp > 0 ? Boolean.TRUE : null);
        });
    }

    /** Total experience points of the player (the vanilla total counter is unreliable). */
    private static int totalXp(Player p) {
        int level = p.getLevel();
        int points;
        if (level <= 16) {
            points = level * level + 6 * level;
        } else if (level <= 31) {
            points = (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            points = (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        return points + Math.round(p.getExp() * p.getExpToLevel());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && event.getFoodLevel() < p.getFoodLevel()
                && plugin.items().is(p.getInventory().getItemInOffHand(), CustomItem.HUNGER_CHARM)
                && ThreadLocalRandom.current().nextDouble() < num("hunger-charm.chance", 0.6)) {
            event.setCancelled(true);
        }
    }
}
