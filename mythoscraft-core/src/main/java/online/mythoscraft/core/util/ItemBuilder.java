package online.mythoscraft.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String text) {
        meta.displayName(Text.item(text));
        return this;
    }

    public ItemBuilder name(Component component) {
        meta.displayName(Text.noItalic(component));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Text.item(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        List<Component> lore = new ArrayList<>();
        for (Component c : lines) {
            lore.add(Text.noItalic(c));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder addLore(String line) {
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.item(line));
        meta.lore(lore);
        return this;
    }

    /** Sets a custom model data string that the resource pack selects on (vanilla look as fallback). */
    public ItemBuilder model(String modelId) {
        if (modelId != null && !modelId.isEmpty()) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(modelId));
            meta.setCustomModelDataComponent(cmd);
        }
        return this;
    }

    public ItemBuilder glint(boolean glint) {
        meta.setEnchantmentGlintOverride(glint);
        return this;
    }

    public ItemBuilder rarity(ItemRarity rarity) {
        meta.setRarity(rarity);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    public ItemBuilder maxStack(int size) {
        meta.setMaxStackSize(Math.max(1, Math.min(99, size)));
        return this;
    }

    public ItemBuilder color(Color color) {
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
        }
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, long value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, value);
        return this;
    }

    /** Own cooldown group, so a cooldown shown on this item does not grey out other items of the same material. */
    public ItemBuilder cooldownGroup(NamespacedKey group, float seconds) {
        UseCooldownComponent cd = meta.getUseCooldown();
        cd.setCooldownSeconds(Math.max(0.05f, seconds));
        cd.setCooldownGroup(group);
        meta.setUseCooldown(cd);
        return this;
    }

    public ItemBuilder hideTooltip() {
        meta.setHideTooltip(true);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
