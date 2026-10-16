package online.mythoscraft.plots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotGridTest {

    private final PlotGrid grid = new PlotGrid(48, 7);

    @Test
    void roadsAndPlots() {
        assertNull(grid.plotAt(0, 0), "origin is a road crossing");
        assertNull(grid.plotAt(6, 30), "road strip x 0..6");
        assertEquals(new PlotId(0, 0), grid.plotAt(7, 7));
        assertEquals(new PlotId(0, 0), grid.plotAt(54, 54));
        assertNull(grid.plotAt(55, 20), "next road starts at 55");
        assertEquals(new PlotId(1, 0), grid.plotAt(62, 20));
        assertEquals(new PlotId(-1, -1), grid.plotAt(-1, -1));
        assertNull(grid.plotAt(-49, -1), "road of cell -1");
    }

    @Test
    void boundsMatchLookup() {
        for (PlotId id : new PlotId[]{new PlotId(0, 0), new PlotId(-3, 5), new PlotId(7, -2)}) {
            assertEquals(48, grid.maxX(id) - grid.minX(id) + 1);
            assertEquals(id, grid.plotAt(grid.minX(id), grid.minZ(id)));
            assertEquals(id, grid.plotAt(grid.maxX(id), grid.maxZ(id)));
            assertNull(grid.plotAt(grid.minX(id) - 1, grid.minZ(id)));
            assertNull(grid.plotAt(grid.maxX(id) + 1, grid.minZ(id)));
            assertEquals(id, grid.plotAt(grid.centerX(id), grid.centerZ(id)));
        }
    }

    @Test
    void borderRing() {
        PlotId id = new PlotId(2, -1);
        assertTrue(grid.isBorder(grid.minX(id) - 1, grid.minZ(id) + 5));
        assertTrue(grid.isBorder(grid.maxX(id) + 1, grid.maxZ(id)));
        assertTrue(grid.isBorder(grid.minX(id) - 1, grid.minZ(id) - 1), "corner");
        assertFalse(grid.isBorder(grid.minX(id) - 3, grid.minZ(id) + 5), "middle of the road");
        assertEquals(id, grid.borderOwner(grid.minX(id) - 1, grid.minZ(id) + 5));
    }

    @Test
    void idParsing() {
        assertEquals(new PlotId(3, -2), PlotId.parse("3;-2"));
        assertEquals("3;-2", new PlotId(3, -2).toString());
    }
}
