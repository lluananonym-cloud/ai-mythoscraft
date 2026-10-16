package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Full-set bonuses of the custom armour sets, refreshed every two seconds. */
public final class ArmorSetService {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final MythosCraft plugin;
    private final Map<UUID, String> active = new HashMap<>();

    public ArmorSetService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 40, "armor sets", this::tick);
    }

    /** Set id if all four pieces of one set are worn, else null. */
    public String fullSet(Player p) {
        String set = null;
        for (EquipmentSlot slot : SLOTS) {
            CustomItem piece = plugin.items().worn(p, slot);
            if (piece == null || !piece.isArmor()) {
                return null;
            }
            if (set == null) {
                set = piece.armorSet();
            } else if (!set.equals(piece.armorSet())) {
                return null;
            }
        }
        return set;
    }

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            String set = fullSet(p);
            String before = set == null ? active.remove(p.getUniqueId()) : active.put(p.getUniqueId(), set);
            if (set != null && !set.equals(before)) {
                plugin.messages().actionBar(p, "armor.bonus-active", "set", plugin.messages().get("armor.sets." + set));
            }
            if (set == null) {
                continue;
            }
            switch (set) {
                case "mythos" -> {
                    give(p, PotionEffectType.RESISTANCE, 0);
                    give(p, PotionEffectType.LUCK, 0);
                    sparkle(p, Color.fromRGB(0xC084FC));
                }
                case "pumpkin" -> {
                    give(p, PotionEffectType.NIGHT_VISION, 0);
                    give(p, PotionEffectType.SPEED, 0);
                    sparkle(p, Color.fromRGB(0xFF7A1A));
                }
                case "frost" -> {
                    give(p, PotionEffectType.RESISTANCE, 0);
                    p.setFreezeTicks(0);
                    if (p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                    sparkle(p, Color.fromRGB(0xA5F3FC));
                }
                case "inferno" -> {
                    give(p, PotionEffectType.FIRE_RESISTANCE, 0);
                    give(p, PotionEffectType.STRENGTH, 0);
                    sparkle(p, Color.fromRGB(0xFF4D1A));
                }
                case "dragon" -> {
                    give(p, PotionEffectType.FIRE_RESISTANCE, 0);
                    give(p, PotionEffectType.STRENGTH, 0);
                    sparkle(p, Color.fromRGB(0xA21CAF));
                }
                case "storm" -> {
                    give(p, PotionEffectType.SPEED, 1);
                    give(p, PotionEffectType.JUMP_BOOST, 0);
                    sparkle(p, Color.fromRGB(0x7DD3FC));
                }
                case "ninja" -> {
                    give(p, PotionEffectType.SPEED, 0);
                    if (p.isSneaking()) {
                        give(p, PotionEffectType.INVISIBILITY, 0);
                    }
                }
                case "knight" -> {
                    give(p, PotionEffectType.RESISTANCE, 0);
                    if (p.getAbsorptionAmount() < 2 && ThreadLocalRandom.current().nextInt(15) == 0) {
                        give(p, PotionEffectType.ABSORPTION, 0);
                    }
                    sparkle(p, Color.fromRGB(0xCBD5E1));
                }
                case "miner" -> {
                    give(p, PotionEffectType.HASTE, 1);
                    if (p.getLocation().getBlockY() < 50) {
                        give(p, PotionEffectType.NIGHT_VISION, 0);
                    }
                }
                case "viking" -> {
                    give(p, PotionEffectType.RESISTANCE, 0);
                    double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                            : p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    if (p.getHealth() <= max / 2) {
                        give(p, PotionEffectType.STRENGTH, 1);
                        sparkle(p, Color.fromRGB(0xDC2626));
                    }
                }
                case "ocean" -> {
                    give(p, PotionEffectType.WATER_BREATHING, 0);
                    if (p.isInWater()) {
                        give(p, PotionEffectType.DOLPHINS_GRACE, 0);
                        give(p, PotionEffectType.CONDUIT_POWER, 0);
                    }
                    sparkle(p, Color.fromRGB(0x22D3EE));
                }
                case "druid" -> {
                    give(p, PotionEffectType.REGENERATION, 0);
                    if (p.getFoodLevel() < 20) {
                        p.setFoodLevel(Math.min(20, p.getFoodLevel() + 1));
                    }
                    sparkle(p, Color.fromRGB(0x4ADE80));
                }
                case "sky" -> {
                    give(p, PotionEffectType.SPEED, 0);
                    give(p, PotionEffectType.JUMP_BOOST, 1);
                    sparkle(p, Color.fromRGB(0xE0F2FE));
                }
                case "pharaoh" -> {
                    give(p, PotionEffectType.FIRE_RESISTANCE, 0);
                    give(p, PotionEffectType.SPEED, 0);
                    if (p.getWorld().isDayTime() && p.getLocation().getBlock().getLightFromSky() >= 14) {
                        give(p, PotionEffectType.STRENGTH, 0);
                    }
                    sparkle(p, Color.fromRGB(0xFBBF24));
                }
                case "astronaut" -> {
                    give(p, PotionEffectType.JUMP_BOOST, 2);
                    give(p, PotionEffectType.SLOW_FALLING, 0);
                    give(p, PotionEffectType.NIGHT_VISION, 0);
                    sparkle(p, Color.fromRGB(0xE2E8F0));
                }
                case "crystal" -> {
                    give(p, PotionEffectType.HASTE, 0);
                    give(p, PotionEffectType.LUCK, 0);
                    if (p.getAbsorptionAmount() < 2 && ThreadLocalRandom.current().nextInt(10) == 0) {
                        give(p, PotionEffectType.ABSORPTION, 0);
                    }
                    sparkle(p, Color.fromRGB(0xE879F9));
                }
                case "angel" -> {
                    give(p, PotionEffectType.REGENERATION, 0);
                    sparkle(p, Color.fromRGB(0xFEF3C7));
                }
                case "sculk" -> {
                    give(p, PotionEffectType.HASTE, 0);
                    if (p.hasPotionEffect(PotionEffectType.DARKNESS)) {
                        p.removePotionEffect(PotionEffectType.DARKNESS);
                    }
                    sparkle(p, Color.fromRGB(0x14B8A6));
                }
                default -> {
                }
            }
        }
    }

    private static void give(Player p, PotionEffectType type, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, type == PotionEffectType.NIGHT_VISION ? 300 : 60, amplifier, true, false, true));
    }

    private static void sparkle(Player p, Color color) {
        if (ThreadLocalRandom.current().nextInt(3) == 0) {
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0,
                    new Particle.DustOptions(color, 0.9f));
        }
    }

    public void forget(UUID player) {
        active.remove(player);
    }
}
