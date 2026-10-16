package online.mythoscraft.core.util;

import org.bukkit.NamespacedKey;

/** Persistent-data keys shared across systems. */
public final class Keys {

    public static final String NAMESPACE = "mythoscraft";

    /** Custom item id on item stacks. */
    public static final NamespacedKey ITEM = key("item");
    /** Crate id on keys. */
    public static final NamespacedKey CRATE = key("crate");
    /** Marks entities spawned by an event/visual so they can be identified and cleaned up. */
    public static final NamespacedKey ENTITY = key("entity");
    /** Coin value of a CoinRain coin. */
    public static final NamespacedKey COIN_VALUE = key("coin_value");
    /** Unique id so dropped event items never merge. */
    public static final NamespacedKey UNIQUE = key("unique");

    private Keys() {
    }

    public static NamespacedKey key(String value) {
        return new NamespacedKey(NAMESPACE, value);
    }
}
