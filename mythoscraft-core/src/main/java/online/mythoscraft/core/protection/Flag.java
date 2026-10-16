package online.mythoscraft.core.protection;

import java.util.Locale;

/** Region flags. {@code key} is the name used in config files and commands. */
public enum Flag {
    PVP("pvp"),
    BUILD("build"),
    BREAK("break"),
    INTERACT("interact"),
    USE("use"),
    CONTAINER("container"),
    EXPLOSIONS("explosions"),
    FIRE("fire"),
    ENTITY_DAMAGE("entity-damage"),
    MOB_DAMAGE("mob-damage"),
    DAMAGE("damage"),
    MOB_SPAWNING("mob-spawning"),
    HUNGER("hunger"),
    ITEM_DROP("item-drop");

    private final String key;

    Flag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Flag byKey(String key) {
        String k = key.toLowerCase(Locale.ROOT).replace('_', '-');
        for (Flag f : values()) {
            if (f.key.equals(k)) {
                return f;
            }
        }
        return null;
    }
}
