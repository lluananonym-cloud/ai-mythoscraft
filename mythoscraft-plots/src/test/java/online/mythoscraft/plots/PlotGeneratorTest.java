package online.mythoscraft.plots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotGeneratorTest {

    private final PlotGrid grid = new PlotGrid(48, 7);
    private final PlotGenerator gen = new PlotGenerator(grid, Material.POLISHED_ANDESITE, Material.STONE_BRICKS, Material.SMOOTH_STONE_SLAB);

    @Test
    void layers() {
        int y = PlotGrid.SURFACE;
        assertEquals(Material.BEDROCK, gen.blockAt(20, -64, 20, -64));
        assertEquals(Material.STONE, gen.blockAt(20, 0, 20, -64));
        assertEquals(Material.DIRT, gen.blockAt(20, y - 1, 20, -64));
        assertEquals(Material.GRASS_BLOCK, gen.blockAt(20, y, 20, -64), "plot surface");
        assertEquals(Material.AIR, gen.blockAt(20, y + 1, 20, -64), "nothing on a fresh plot");
        assertEquals(Material.POLISHED_ANDESITE, gen.blockAt(3, y, 20, -64), "road");
        assertEquals(Material.AIR, gen.blockAt(3, y + 1, 20, -64), "road is free to walk");
        assertEquals(Material.STONE_BRICKS, gen.blockAt(6, y, 20, -64), "road edge");
        assertEquals(Material.SMOOTH_STONE_SLAB, gen.blockAt(6, y + 1, 20, -64), "border slab");
    }
}
