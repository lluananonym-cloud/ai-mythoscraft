package online.mythoscraft.core.item;

import java.util.Locale;

/** Item collections. Every special collection has its own crate (see crates.yml). */
public enum ItemCollection {
    CORE("core"),
    ANCIENT("ancient"),
    HALLOWEEN("halloween"),
    FROSTFEST("frostfest"),
    CELESTIAL("celestial"),
    INFERNO("inferno"),
    FARM("farm"),
    STORM("storm"),
    DRAGON("dragon"),
    SPRING("spring"),
    OCEAN("ocean"),
    FOREST("forest"),
    SKY("sky"),
    CRYSTAL("crystal"),
    MILLENNIUM("millennium"),
    COSMETIC("cosmetic"),
    BEGLEITER("begleiter"),
    SPACE("space"),
    SPAWNER("spawner"),
    SILVESTER("silvester"),
    RELIC("relic"),
    SHADOW("shadow"),
    TOURNAMENT("tournament");

    private final String id;

    ItemCollection(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ItemCollection byId(String s) {
        for (ItemCollection c : values()) {
            if (c.id.equalsIgnoreCase(s) || c.name().equalsIgnoreCase(s)) {
                return c;
            }
        }
        return null;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
