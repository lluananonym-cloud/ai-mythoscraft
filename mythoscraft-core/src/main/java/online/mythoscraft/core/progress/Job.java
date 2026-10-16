package online.mythoscraft.core.progress;

import org.bukkit.Material;

import java.util.Locale;

/** The jobs ("Berufe"). Everybody has all of them and levels them by playing normally. */
public enum Job {
    MINER("miner", Material.DIAMOND_PICKAXE),
    LUMBERJACK("lumberjack", Material.DIAMOND_AXE),
    FARMER("farmer", Material.GOLDEN_HOE),
    HUNTER("hunter", Material.BOW),
    FISHER("fisher", Material.FISHING_ROD),
    BUILDER("builder", Material.BRICKS),
    /** wheel spins and crate openings */
    GAMBLER("gambler", Material.TRIAL_KEY),
    ENCHANTER("enchanter", Material.ENCHANTING_TABLE),
    BREWER("brewer", Material.BREWING_STAND),
    COOK("cook", Material.COOKED_BEEF);

    private final String id;
    private final Material icon;

    Job(String id, Material icon) {
        this.id = id;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public Material icon() {
        return icon;
    }

    public String levelKey() {
        return "job." + id + ".level";
    }

    public String xpKey() {
        return "job." + id + ".xp";
    }

    public static Job byId(String s) {
        for (Job j : values()) {
            if (j.id.equalsIgnoreCase(s) || j.name().equalsIgnoreCase(s.toUpperCase(Locale.ROOT))) {
                return j;
            }
        }
        return null;
    }
}
