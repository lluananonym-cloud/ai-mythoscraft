package online.mythoscraft.core.tournament;

import online.mythoscraft.core.item.CustomItem;

import java.util.Locale;

/** Tournament sizes. Bigger tiers need bigger arenas. */
public enum TournamentTier {
    DAILY("daily", 5, 31, CustomItem.TROPHY_DAILY),
    WEEKLY("weekly", 20, 61, CustomItem.TROPHY_WEEKLY),
    MONTHLY("monthly", 100, 121, CustomItem.TROPHY_MONTHLY);

    private final String id;
    private final int defaultSlots;
    private final int minArenaSize;
    private final CustomItem trophy;

    TournamentTier(String id, int defaultSlots, int minArenaSize, CustomItem trophy) {
        this.id = id;
        this.defaultSlots = defaultSlots;
        this.minArenaSize = minArenaSize;
        this.trophy = trophy;
    }

    public String id() {
        return id;
    }

    public int defaultSlots() {
        return defaultSlots;
    }

    /** Minimum width and depth (blocks) an arena needs for this tier. */
    public int minArenaSize() {
        return minArenaSize;
    }

    public CustomItem trophy() {
        return trophy;
    }

    public static TournamentTier byId(String s) {
        String v = s.toLowerCase(Locale.ROOT);
        for (TournamentTier t : values()) {
            if (t.id.equals(v) || (v.equals("täglich") && t == DAILY) || (v.equals("wöchentlich") && t == WEEKLY)
                    || (v.equals("monatlich") && t == MONTHLY)) {
                return t;
            }
        }
        return null;
    }
}
