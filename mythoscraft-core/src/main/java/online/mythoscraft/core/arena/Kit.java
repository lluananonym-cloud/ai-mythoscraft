package online.mythoscraft.core.arena;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** An item kit from config.yml (kits.&lt;id&gt;) handed out in arenas. */
public record Kit(String id, List<ItemStack> items, ItemStack helmet, ItemStack chestplate, ItemStack leggings,
                  ItemStack boots, ItemStack offhand) {

    public static Kit parse(String id, ConfigReader cfg) {
        List<ItemStack> items = new ArrayList<>();
        List<?> list = cfg.raw().getList("items");
        if (list != null) {
            for (Object o : list) {
                ItemStack item = parseItem(o, cfg);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return new Kit(id.toLowerCase(Locale.ROOT), items, parseItem(cfg.raw().get("helmet"), cfg),
                parseItem(cfg.raw().get("chestplate"), cfg), parseItem(cfg.raw().get("leggings"), cfg),
                parseItem(cfg.raw().get("boots"), cfg), parseItem(cfg.raw().get("offhand"), cfg));
    }

    /** Accepts "DIAMOND_SWORD", "ARROW 32" or a map with material, amount, name and enchantments. */
    private static ItemStack parseItem(Object o, ConfigReader cfg) {
        if (o == null) {
            return null;
        }
        String materialName;
        int amount = 1;
        String name = null;
        String potion = null;
        Map<?, ?> enchants = null;
        if (o instanceof String s) {
            String[] parts = s.trim().split("\\s+");
            materialName = parts[0];
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    cfg.warn("items", "ungültige Anzahl in '" + s + "'");
                }
            }
        } else if (o instanceof Map<?, ?> map) {
            materialName = String.valueOf(map.get("material"));
            if (map.get("amount") instanceof Number n) {
                amount = n.intValue();
            }
            if (map.get("name") != null) {
                name = String.valueOf(map.get("name"));
            }
            if (map.get("enchantments") instanceof Map<?, ?> e) {
                enchants = e;
            }
            if (map.get("potion") != null) {
                potion = String.valueOf(map.get("potion"));
            }
        } else if (o instanceof ConfigurationSection sec) {
            return parseItem(sec.getValues(false), cfg);
        } else {
            return null;
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            cfg.warn("items", "unbekanntes Item '" + materialName + "'");
            return null;
        }
        ItemBuilder builder = new ItemBuilder(material, amount);
        if (name != null) {
            builder.name(name);
        }
        if (enchants != null) {
            for (Map.Entry<?, ?> e : enchants.entrySet()) {
                Enchantment ench = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT)));
                if (ench == null) {
                    cfg.warn("items", "unbekannte Verzauberung '" + e.getKey() + "'");
                } else if (e.getValue() instanceof Number lvl) {
                    builder.enchant(ench, lvl.intValue());
                }
            }
        }
        ItemStack built = builder.build();
        if (potion != null && built.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta) {
            org.bukkit.potion.PotionType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION)
                    .get(NamespacedKey.minecraft(potion.toLowerCase(Locale.ROOT)));
            if (type == null) {
                cfg.warn("items", "unbekannter Trank '" + potion + "'");
            } else {
                built.editMeta(org.bukkit.inventory.meta.PotionMeta.class, m -> m.setBasePotionType(type));
            }
        }
        return built;
    }

    /** Main weapon of the kit (first sword/axe/mace), used to arm tournament bots. */
    public ItemStack weapon() {
        for (ItemStack item : items) {
            String n = item.getType().name();
            if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.equals("MACE") || n.equals("TRIDENT")) {
                return item.clone();
            }
        }
        return items.isEmpty() ? null : items.get(0).clone();
    }

    public void give(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : items) {
            inv.addItem(item.clone()).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
        if (helmet != null) {
            inv.setHelmet(helmet.clone());
        }
        if (chestplate != null) {
            inv.setChestplate(chestplate.clone());
        }
        if (leggings != null) {
            inv.setLeggings(leggings.clone());
        }
        if (boots != null) {
            inv.setBoots(boots.clone());
        }
        if (offhand != null) {
            inv.setItemInOffHand(offhand.clone());
        }
    }
}
