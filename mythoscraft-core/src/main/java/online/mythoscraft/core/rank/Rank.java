package online.mythoscraft.core.rank;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * A configured rank.
 *
 * @param display     coloured name, e.g. "&a&lVIP"
 * @param textPrefix  prefix shown to players without the resource pack
 * @param badge       glyph id of the pack badge ("" for none)
 * @param teamColor   name colour in tab list and above the head
 * @param chatColor   colour of chat messages
 * @param ownerOnly   only MinePro641 may give or take this rank (MYTHOS always, staff ranks by config)
 */
public record Rank(String id, int weight, String display, String textPrefix, String badge, NamedTextColor teamColor,
                   TextColor chatColor, int homes, double coinMultiplier, double gemMultiplier, long dailyBonus,
                   List<String> permissions, boolean ownerOnly) {

    public static final String MYTHOS = "MYTHOS";

    @Override
    public String display() {
        return online.mythoscraft.core.config.ConfigText.tr(display);
    }

    @Override
    public String textPrefix() {
        return online.mythoscraft.core.config.ConfigText.tr(textPrefix);
    }

    public boolean isMythos() {
        return MYTHOS.equals(id);
    }

    public boolean restricted() {
        return ownerOnly || isMythos();
    }
}
