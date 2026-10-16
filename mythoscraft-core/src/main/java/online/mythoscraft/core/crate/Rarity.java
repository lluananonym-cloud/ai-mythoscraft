package online.mythoscraft.core.crate;

import org.bukkit.Color;
import org.bukkit.Material;

public enum Rarity {
    COMMON("<gray>", Material.LIGHT_GRAY_STAINED_GLASS_PANE, Color.fromRGB(0xB0B0B0), "entity.experience_orb.pickup 1 1.2"),
    RARE("<aqua>", Material.LIGHT_BLUE_STAINED_GLASS_PANE, Color.fromRGB(0x38BDF8), "entity.player.levelup 0.8 1.4"),
    EPIC("<light_purple>", Material.PURPLE_STAINED_GLASS_PANE, Color.fromRGB(0xA855F7), "ui.toast.challenge_complete 0.7 1.4"),
    LEGENDARY("<gold>", Material.ORANGE_STAINED_GLASS_PANE, Color.fromRGB(0xF59E0B), "ui.toast.challenge_complete 1 1.0"),
    MYTHIC("<gradient:#c084fc:#f43f5e>", Material.MAGENTA_STAINED_GLASS_PANE, Color.fromRGB(0xE879F9), "entity.ender_dragon.growl 0.6 1.6");

    private final String colorTag;
    private final Material pane;
    private final Color color;
    private final String sound;

    Rarity(String colorTag, Material pane, Color color, String sound) {
        this.colorTag = colorTag;
        this.pane = pane;
        this.color = color;
        this.sound = sound;
    }

    public String colorTag() {
        return colorTag;
    }

    public Material pane() {
        return pane;
    }

    public Color color() {
        return color;
    }

    public String sound() {
        return sound;
    }
}
