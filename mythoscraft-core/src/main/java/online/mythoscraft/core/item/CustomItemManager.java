package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

public final class CustomItemManager {

    private final MythosCraft plugin;

    public CustomItemManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(CustomItem type) {
        return create(type, 1);
    }

    public ItemStack create(CustomItem type, int amount) {
        CustomItem.Spec s = type.spec();
        String base = "items." + type.id();
        ItemBuilder builder = new ItemBuilder(type.base(), amount)
                .name(plugin.messages().raw(base + ".name"))
                .lore(plugin.messages().rawList(base + ".lore"))
                .model(type.id())
                .rarity(type.rarity())
                .tag(Keys.ITEM, type.id());
        if (s.glint) {
            builder.glint(true);
        }
        if (s.cooldownGroup) {
            builder.cooldownGroup(Keys.key(type.id()), 1f);
        }
        if (s.maxStack != null) {
            builder.maxStack(s.maxStack);
        }
        ItemStack item = builder.build();
        item.editMeta(meta -> applyComponents(type, s, meta));
        if (type == CustomItem.MYTHOS_BACKPACK) {
            item.editPersistentDataContainer(pdc -> pdc.set(Keys.UNIQUE, PersistentDataType.STRING, UUID.randomUUID().toString()));
        }
        return item;
    }

    private void applyComponents(CustomItem type, CustomItem.Spec s, ItemMeta meta) {
        if (s.damage != null) {
            EquipmentSlotGroup hand = EquipmentSlotGroup.MAINHAND;
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(Keys.key(type.id() + "_damage"),
                    s.damage - 1, AttributeModifier.Operation.ADD_NUMBER, hand));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(Keys.key(type.id() + "_speed"),
                    s.attackSpeed - 4, AttributeModifier.Operation.ADD_NUMBER, hand));
        }
        if (s.armorSet != null) {
            EquipmentSlotGroup group = switch (s.slot) {
                case HEAD -> EquipmentSlotGroup.HEAD;
                case CHEST -> EquipmentSlotGroup.CHEST;
                case LEGS -> EquipmentSlotGroup.LEGS;
                default -> EquipmentSlotGroup.FEET;
            };
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(Keys.key(type.id() + "_armor"),
                    s.armor, AttributeModifier.Operation.ADD_NUMBER, group));
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(Keys.key(type.id() + "_toughness"),
                    s.toughness, AttributeModifier.Operation.ADD_NUMBER, group));
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(Keys.key(type.id() + "_kb"),
                    s.knockback, AttributeModifier.Operation.ADD_NUMBER, group));
            if (s.health > 0) {
                meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(Keys.key(type.id() + "_health"),
                        s.health, AttributeModifier.Operation.ADD_NUMBER, group));
            }
            if (s.attackBonus != 0) {
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(Keys.key(type.id() + "_attack"),
                        s.attackBonus, AttributeModifier.Operation.ADD_NUMBER, group));
            }
            if (s.speedBonus != 0) {
                meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(Keys.key(type.id() + "_speed"),
                        s.speedBonus, AttributeModifier.Operation.ADD_SCALAR, group));
            }
            EquippableComponent eq = meta.getEquippable();
            eq.setSlot(s.slot);
            // helmets are 3D models worn on the head (like the masks), the rest uses the armour layers
            eq.setModel(s.slot == EquipmentSlot.HEAD ? null : new NamespacedKey(Keys.NAMESPACE, s.armorSet));
            eq.setEquipSound(org.bukkit.Sound.ITEM_ARMOR_EQUIP_NETHERITE);
            meta.setEquippable(eq);
        }
        if (s.hat) {
            EquippableComponent eq = meta.getEquippable();
            eq.setSlot(EquipmentSlot.HEAD);
            eq.setEquipSound(org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER);
            meta.setEquippable(eq);
        }
        if (s.nutrition != null) {
            FoodComponent food = meta.getFood();
            food.setNutrition(s.nutrition);
            food.setSaturation(s.saturation);
            food.setCanAlwaysEat(true);
            meta.setFood(food);
        }
    }

    /** Event drop that never merges with other items (unique tag). */
    public ItemStack createUnique(CustomItem type) {
        return new ItemBuilder(type.base()).model(type.id()).tag(Keys.ITEM, type.id())
                .tag(Keys.UNIQUE, UUID.randomUUID().toString()).glint(true).build();
    }

    public CustomItem identify(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
        return id == null ? null : CustomItem.byId(id);
    }

    public boolean is(ItemStack item, CustomItem type) {
        return identify(item) == type;
    }

    public boolean isCustom(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(Keys.ITEM, PersistentDataType.STRING);
    }

    /** Armour piece worn in the given slot, or null. */
    public CustomItem worn(Player player, EquipmentSlot slot) {
        return identify(player.getInventory().getItem(slot));
    }

    /** Adds the item; whatever does not fit is dropped at the player's feet. */
    /** Older helmets still carry the flat armour layer – switch them to the 3D model. Returns true if changed. */
    public boolean upgradeHelmet(ItemStack item) {
        CustomItem type = identify(item);
        if (type == null || !type.isArmor() || type.slot() != EquipmentSlot.HEAD || !item.hasItemMeta()) {
            return false;
        }
        var meta = item.getItemMeta();
        if (!meta.hasEquippable() || meta.getEquippable().getModel() == null) {
            return false;
        }
        EquippableComponent eq = meta.getEquippable();
        eq.setModel(null);
        meta.setEquippable(eq);
        item.setItemMeta(meta);
        return true;
    }

    public static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
        for (ItemStack r : rest.values()) {
            player.getWorld().dropItem(player.getLocation(), r, drop -> drop.setOwner(player.getUniqueId()));
        }
    }
}
