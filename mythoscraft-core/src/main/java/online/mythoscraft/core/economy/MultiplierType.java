package online.mythoscraft.core.economy;

import java.util.Locale;

public enum MultiplierType {
    COINS("coins"),
    GEMS("gems"),
    EVENTS("events");

    private final String id;

    MultiplierType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MultiplierType byId(String s) {
        for (MultiplierType t : values()) {
            if (t.id.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) {
                return t;
            }
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("coin")) {
            return COINS;
        }
        if (lower.startsWith("gem")) {
            return GEMS;
        }
        if (lower.startsWith("event")) {
            return EVENTS;
        }
        return null;
    }
}
