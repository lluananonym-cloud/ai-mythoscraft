package online.mythoscraft.core.protection;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Set;

/** Precomputed material sets so the hot interaction checks are plain EnumSet lookups. */
public final class MaterialGroups {

    /** Blocks with an inventory or stored items. */
    public static final Set<Material> CONTAINERS = EnumSet.noneOf(Material.class);
    /** Doors, gates, buttons, levers, plates, bells. */
    public static final Set<Material> USABLES = EnumSet.noneOf(Material.class);
    /** Items that change the clicked block when used on it (strip, till, path, bone meal, wax, …). */
    public static final Set<Material> BLOCK_CHANGING_ITEMS = EnumSet.noneOf(Material.class);

    static {
        for (String name : new String[]{"CHEST", "TRAPPED_CHEST", "BARREL", "HOPPER", "FURNACE", "BLAST_FURNACE", "SMOKER",
                "BREWING_STAND", "DISPENSER", "DROPPER", "CRAFTER", "LECTERN", "CHISELED_BOOKSHELF", "DECORATED_POT",
                "JUKEBOX", "BEACON", "COMPOSTER", "CAMPFIRE", "SOUL_CAMPFIRE", "VAULT"}) {
            add(CONTAINERS, name);
        }
        CONTAINERS.addAll(Tag.SHULKER_BOXES.getValues());
        CONTAINERS.addAll(Tag.COPPER_CHESTS.getValues());
        CONTAINERS.addAll(Tag.WOODEN_SHELVES.getValues());

        USABLES.addAll(Tag.DOORS.getValues());
        USABLES.addAll(Tag.TRAPDOORS.getValues());
        USABLES.addAll(Tag.FENCE_GATES.getValues());
        USABLES.addAll(Tag.BUTTONS.getValues());
        USABLES.addAll(Tag.PRESSURE_PLATES.getValues());
        add(USABLES, "LEVER");
        add(USABLES, "BELL");
        add(USABLES, "TRIPWIRE");

        BLOCK_CHANGING_ITEMS.addAll(Tag.ITEMS_AXES.getValues());
        BLOCK_CHANGING_ITEMS.addAll(Tag.ITEMS_SHOVELS.getValues());
        BLOCK_CHANGING_ITEMS.addAll(Tag.ITEMS_HOES.getValues());
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.name().endsWith("_SPAWN_EGG")) {
                BLOCK_CHANGING_ITEMS.add(m);
            }
        }
        for (String name : new String[]{"BONE_MEAL", "HONEYCOMB", "BRUSH", "FLINT_AND_STEEL", "FIRE_CHARGE", "SHEARS",
                "GLASS_BOTTLE", "INK_SAC", "GLOW_INK_SAC", "ENDER_EYE", "END_CRYSTAL", "ARMOR_STAND"}) {
            add(BLOCK_CHANGING_ITEMS, name);
        }
    }

    private MaterialGroups() {
    }

    private static void add(Set<Material> set, String name) {
        Material m = Material.getMaterial(name);
        if (m != null) {
            set.add(m);
        }
    }
}
