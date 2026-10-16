package online.mythoscraft.core.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The blacksmith (/schmied): weapons, armour and tools can be upgraded from +1 to +10 with coins, gems and rare
 * materials. Weapons hit harder, armour takes less damage, tools dig faster (Efficiency) – from +7 an upgrade can
 * fail (the item stays as it is, the materials are gone). +10 items glow.
 */
public final class UpgradeService implements Listener {

    public enum Kind { WEAPON, RANGED, ARMOR, TOOL }

    public enum Result { DONE, FAILED, NOTHING_IN_HAND, NOT_UPGRADABLE, MAX_LEVEL, NOT_ENOUGH_COINS, NOT_ENOUGH_GEMS, NOT_ENOUGH_MATERIAL, DISABLED }

    /** What the next level costs. */
    public record Cost(long coins, long gems, Material material, int amount, int chance) {
    }

    private final MythosCraft plugin;
    private final NamespacedKey key;
    private final NamespacedKey arrowKey;
    private boolean enabled = true;
    private int maxLevel = 10;
    private int[] chances = {100, 100, 100, 100, 100, 100, 80, 65, 50, 35};
    private long coinsBase = 1500;
    private long gemsPerLevel = 3;
    private final List<Material> materials = new ArrayList<>();
    private final List<Integer> amounts = new ArrayList<>();
    private double damagePerLevel = 0.05;
    private double armorPerLevel = 0.01;
    private double armorCap = 0.35;

    public UpgradeService(MythosCraft plugin) {
        this.plugin = plugin;
        this.key = Keys.key("upgrade_level");
        this.arrowKey = Keys.key("upgrade_arrow");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("upgrades");
        enabled = cfg.bool("enabled", true);
        maxLevel = cfg.integer("max-level", 10, 1, 30);
        List<String> ch = cfg.strings("success-chance");
        chances = new int[maxLevel];
        for (int i = 0; i < maxLevel; i++) {
            int def = i < 6 ? 100 : Math.max(10, 100 - (i - 5) * 15 - 5);
            try {
                chances[i] = i < ch.size() ? Math.max(1, Math.min(100, Integer.parseInt(ch.get(i).trim()))) : def;
            } catch (NumberFormatException e) {
                chances[i] = def;
            }
        }
        coinsBase = cfg.longValue("coins-base", 1500, 0, 100_000_000);
        gemsPerLevel = cfg.longValue("gems-per-level", 3, 0, 100_000);
        materials.clear();
        amounts.clear();
        List<String> mats = cfg.strings("materials");
        for (int i = 0; i < maxLevel; i++) {
            String raw = i < mats.size() ? mats.get(i) : (i < 3 ? "DIAMOND:" + (i + 1) * 2 : i < 6 ? "NETHERITE_SCRAP:" + (i - 2) : "NETHER_STAR:1");
            String[] p = raw.trim().split(":");
            Material m = Material.matchMaterial(p[0].trim());
            if (m == null) {
                cfg.warn("materials", "'" + p[0] + "' ist kein Material – DIAMOND");
                m = Material.DIAMOND;
            }
            int amount = 1;
            try {
                amount = p.length > 1 ? Math.max(1, Integer.parseInt(p[1].trim())) : 1;
            } catch (NumberFormatException ignored) {
                // 1
            }
            materials.add(m);
            amounts.add(amount);
        }
        damagePerLevel = cfg.decimal("bonus.damage-per-level", 0.05, 0, 1);
        armorPerLevel = cfg.decimal("bonus.armor-per-level", 0.01, 0, 0.2);
        armorCap = cfg.decimal("bonus.armor-max", 0.35, 0, 0.9);
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public static Kind kind(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        String n = item.getType().name();
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.equals("MACE") || n.equals("TRIDENT")) {
            return Kind.WEAPON;
        }
        if (n.equals("BOW") || n.equals("CROSSBOW")) {
            return Kind.RANGED;
        }
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.equals("ELYTRA")
                || n.equals("TURTLE_HELMET")) {
            return Kind.ARMOR;
        }
        if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.equals("SHEARS") || n.equals("FISHING_ROD")) {
            return Kind.TOOL;
        }
        return null;
    }

    public int level(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer l = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return l == null ? 0 : l;
    }

    /** Cost of the step from {@code level} to {@code level + 1}. */
    public Cost cost(int level) {
        int next = level + 1;
        int i = Math.max(0, Math.min(maxLevel - 1, level));
        return new Cost(coinsBase * next * next, gemsPerLevel * next, materials.get(i), amounts.get(i), chances[i]);
    }

    /** Cost for a player: ranks get a blacksmith discount on coins and gems. */
    public Cost cost(int level, Player p) {
        Cost c = cost(level);
        int discount = plugin.rankPerks().upgradeDiscount(p);
        if (discount <= 0) {
            return c;
        }
        return new Cost(c.coins() * (100 - discount) / 100, c.gems() * (100 - discount) / 100, c.material(), c.amount(), c.chance());
    }

    public Result upgrade(Player p) {
        if (!enabled) {
            return Result.DISABLED;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            return Result.NOTHING_IN_HAND;
        }
        Kind kind = kind(item);
        if (kind == null || item.getAmount() != 1) {
            return Result.NOT_UPGRADABLE;
        }
        int level = level(item);
        if (level >= maxLevel) {
            return Result.MAX_LEVEL;
        }
        Cost c = cost(level, p);
        PlayerData data = plugin.playerData().get(p);
        if (data == null || !plugin.economy().has(data, Currency.COINS, c.coins())) {
            return Result.NOT_ENOUGH_COINS;
        }
        if (c.gems() > 0 && !plugin.economy().has(data, Currency.GEMS, c.gems())) {
            return Result.NOT_ENOUGH_GEMS;
        }
        if (!p.getInventory().containsAtLeast(new ItemStack(c.material()), c.amount())) {
            return Result.NOT_ENOUGH_MATERIAL;
        }
        if (c.coins() > 0 && plugin.economy().withdraw(data, Currency.COINS, c.coins()) != EconomyManager.Result.SUCCESS) {
            return Result.NOT_ENOUGH_COINS;
        }
        if (c.gems() > 0 && plugin.economy().withdraw(data, Currency.GEMS, c.gems()) != EconomyManager.Result.SUCCESS) {
            plugin.economy().earn(data, Currency.COINS, c.coins(), false);
            return Result.NOT_ENOUGH_GEMS;
        }
        p.getInventory().removeItem(new ItemStack(c.material(), c.amount()));
        plugin.playerData().save(data);
        if (ThreadLocalRandom.current().nextInt(100) >= c.chance()) {
            SoundSpec.of("block.anvil.destroy", 1f, 0.8f).play(p);
            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1.2, 0), 30, 0.3, 0.3, 0.3, 0.02);
            return Result.FAILED;
        }
        apply(item, kind, level + 1);
        p.getInventory().setItemInMainHand(item);
        SoundSpec.of("block.anvil.use", 1f, 1.2f).play(p);
        p.getWorld().spawnParticle(level + 1 >= maxLevel ? Particle.TOTEM_OF_UNDYING : Particle.ENCHANT, p.getLocation().add(0, 1.2, 0),
                level + 1 >= maxLevel ? 80 : 40, 0.4, 0.5, 0.4, 0.2);
        if (level + 1 >= 7) {
            plugin.messages().broadcast("upgrades.broadcast", "player", p.getName(), "level", level + 1,
                    "item", item.effectiveName());
        }
        return Result.DONE;
    }

    /** Writes the level, the lore line and the effects (Efficiency for tools, Unbreaking at +5/+10, glow at max). */
    void apply(ItemStack item, Kind kind, int level) {
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).startsWith("✦ Upgrade"));
            String bonus = switch (kind) {
                case WEAPON, RANGED -> "+" + Math.round(level * damagePerLevel * 100) + "% " + plugin.messages().raw("upgrades.bonus-damage");
                case ARMOR -> "-" + Math.round(level * armorPerLevel * 100) + "% " + plugin.messages().raw("upgrades.bonus-armor");
                case TOOL -> plugin.messages().raw("upgrades.bonus-tool");
            };
            lore.add(0, plugin.messages().format("<gradient:#fde047:#f59e0b>✦ Upgrade +" + level + "</gradient> <gray>" + bonus)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            if (kind == Kind.TOOL && level % 2 == 0) {
                Enchantment eff = kindEnchant(item);
                if (eff != null) {
                    meta.addEnchant(eff, meta.getEnchantLevel(eff) + 1, true);
                }
            }
            if (level == 5 || level == 10) {
                meta.addEnchant(Enchantment.UNBREAKING, meta.getEnchantLevel(Enchantment.UNBREAKING) + 1, true);
            }
            if (level >= maxLevel) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
    }

    private static Enchantment kindEnchant(ItemStack item) {
        String n = item.getType().name();
        if (n.equals("FISHING_ROD")) {
            return Enchantment.LURE;
        }
        return Enchantment.EFFICIENCY;
    }

    // ---- effects ------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        int level = level(event.getBow());
        if (level > 0) {
            event.getProjectile().getPersistentDataContainer().set(arrowKey, PersistentDataType.INTEGER, level);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!enabled) {
            return;
        }
        int level = 0;
        if (event.getDamager() instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (kind(hand) == Kind.WEAPON) {
                level = level(hand);
            }
        } else if (event.getDamager() instanceof Projectile pr) {
            Integer l = pr.getPersistentDataContainer().get(arrowKey, PersistentDataType.INTEGER);
            level = l == null ? 0 : l;
        }
        if (level > 0) {
            event.setDamage(event.getDamage() * (1 + level * damagePerLevel));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player p)) {
            return;
        }
        int levels = 0;
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (kind(armor) == Kind.ARMOR) {
                levels += level(armor);
            }
        }
        if (levels > 0) {
            event.setDamage(event.getDamage() * (1 - Math.min(armorCap, levels * armorPerLevel)));
        }
    }

    public static String materialName(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
