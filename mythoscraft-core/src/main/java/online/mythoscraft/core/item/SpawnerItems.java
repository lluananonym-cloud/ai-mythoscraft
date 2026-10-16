package online.mythoscraft.core.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.Text;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/** Spawner items that remember their mob (crate rewards, spawner pickaxe drops) and keep it when placed. */
public final class SpawnerItems {

    static final String KEY = "spawner_type";

    private SpawnerItems() {
    }

    public static EntityType parse(String id) {
        try {
            EntityType t = EntityType.valueOf(id.toUpperCase(Locale.ROOT));
            return t.isSpawnable() && t.isAlive() ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ItemStack create(EntityType type, int amount) {
        ItemStack item = new ItemStack(Material.SPAWNER, Math.max(1, Math.min(64, amount)));
        item.editMeta(meta -> {
            if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof CreatureSpawner spawner) {
                spawner.setSpawnedType(type);
                bsm.setBlockState(spawner);
            }
            meta.displayName(Text.noItalic(Component.translatable(type.translationKey()).color(NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(" Spawner", NamedTextColor.LIGHT_PURPLE))));
            meta.lore(List.of(Text.noItalic(Component.text("Platzieren: spawnt ", NamedTextColor.GRAY)
                    .append(Component.translatable(type.translationKey(), NamedTextColor.WHITE)))));
            meta.getPersistentDataContainer().set(Keys.key(KEY), PersistentDataType.STRING, type.name());
        });
        return item;
    }

    /** The mob of a spawner item from this plugin, or null. */
    public static EntityType typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }
        String s = item.getItemMeta().getPersistentDataContainer().get(Keys.key(KEY), PersistentDataType.STRING);
        return s == null ? null : parse(s);
    }
}
