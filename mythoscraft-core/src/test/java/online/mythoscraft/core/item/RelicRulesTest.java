package online.mythoscraft.core.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Paint brush colours and spawner booster speed of the relic items. */
class RelicRulesTest {

    @Test
    void brushRecoloursEveryColouredFamily() {
        assertEquals(Material.RED_WOOL, RelicListener.recolor(Material.WHITE_WOOL, "red"));
        assertEquals(Material.BLACK_CONCRETE, RelicListener.recolor(Material.LIGHT_BLUE_CONCRETE, "black"));
        assertEquals(Material.LIME_CONCRETE_POWDER, RelicListener.recolor(Material.GRAY_CONCRETE_POWDER, "lime"));
        assertEquals(Material.BLUE_TERRACOTTA, RelicListener.recolor(Material.TERRACOTTA, "blue"));
        assertEquals(Material.PINK_GLAZED_TERRACOTTA, RelicListener.recolor(Material.RED_GLAZED_TERRACOTTA, "pink"));
        assertEquals(Material.CYAN_STAINED_GLASS_PANE, RelicListener.recolor(Material.GLASS_PANE, "cyan"));
        assertEquals(Material.YELLOW_CARPET, RelicListener.recolor(Material.MAGENTA_CARPET, "yellow"));
        assertNull(RelicListener.recolor(Material.STONE, "red"));
        assertNull(RelicListener.recolor(Material.OAK_PLANKS, "red"));
    }

    @Test
    void boostersStackAndGetFaster() {
        assertEquals(1.0, RelicListener.speedFactor(0), 1e-9);
        assertTrue(RelicListener.speedFactor(5) > RelicListener.speedFactor(4));
        assertEquals(5.0, RelicListener.speedFactor(10), 1e-9);
    }
}
