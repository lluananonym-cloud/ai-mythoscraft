package online.mythoscraft.core.economy;

import online.mythoscraft.core.player.PlayerData;

import java.util.Locale;

public enum Currency {
    COINS("coins"),
    GEMS("gems");

    private final String id;

    Currency(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public long get(PlayerData data) {
        return this == COINS ? data.coins() : data.gems();
    }

    void set(PlayerData data, long value) {
        if (this == COINS) {
            data.coins(value);
        } else {
            data.gems(value);
        }
    }

    public MultiplierType multiplier() {
        return this == COINS ? MultiplierType.COINS : MultiplierType.GEMS;
    }

    public static Currency byId(String s) {
        String id = s.toLowerCase(Locale.ROOT);
        if (id.startsWith("coin")) {
            return COINS;
        }
        if (id.startsWith("gem")) {
            return GEMS;
        }
        return null;
    }
}
