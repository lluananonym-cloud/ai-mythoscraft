package online.mythoscraft.plots;

/**
 * The plot raster: every cell is a road strip ({@code road} blocks) followed by the plot ({@code size} blocks), on
 * both axes. Local coordinate 0 … road-1 of a cell is road, road … cell-1 is the plot. The first and the last road
 * block next to a plot carry its border slab. Pure math – no Bukkit, easy to test.
 */
public final class PlotGrid {

    public static final int SURFACE = 64;

    private final int size;
    private final int road;
    private final int cell;

    public PlotGrid(int size, int road) {
        if (size < 4 || road < 3) {
            throw new IllegalArgumentException("plot size >= 4 and road width >= 3");
        }
        this.size = size;
        this.road = road;
        this.cell = size + road;
    }

    public int size() {
        return size;
    }

    public int road() {
        return road;
    }

    public int cell() {
        return cell;
    }

    /** The plot at block x/z, or null on a road. */
    public PlotId plotAt(int x, int z) {
        int lx = Math.floorMod(x, cell);
        int lz = Math.floorMod(z, cell);
        if (lx < road || lz < road) {
            return null;
        }
        return new PlotId(Math.floorDiv(x, cell), Math.floorDiv(z, cell));
    }

    public boolean isRoad(int x, int z) {
        return plotAt(x, z) == null;
    }

    /** First block (x) of a plot – inclusive. */
    public int minX(PlotId id) {
        return id.x() * cell + road;
    }

    public int minZ(PlotId id) {
        return id.z() * cell + road;
    }

    /** Last block (x) of a plot – inclusive. */
    public int maxX(PlotId id) {
        return minX(id) + size - 1;
    }

    public int maxZ(PlotId id) {
        return minZ(id) + size - 1;
    }

    /** Road block directly next to a plot (where the border slab stands)? */
    public boolean isBorder(int x, int z) {
        int lx = Math.floorMod(x, cell);
        int lz = Math.floorMod(z, cell);
        boolean edgeX = lx == road - 1 || lx == 0;
        boolean edgeZ = lz == road - 1 || lz == 0;
        boolean insideX = lx >= road - 1 || lx == 0;
        boolean insideZ = lz >= road - 1 || lz == 0;
        return (edgeX && insideZ) || (edgeZ && insideX);
    }

    /** The plot whose border ring contains x/z (null = none). */
    public PlotId borderOwner(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                PlotId id = plotAt(x + dx, z + dz);
                if (id != null && x >= minX(id) - 1 && x <= maxX(id) + 1 && z >= minZ(id) - 1 && z <= maxZ(id) + 1) {
                    return id;
                }
            }
        }
        return null;
    }

    /** Centre of a plot (block coordinates). */
    public int centerX(PlotId id) {
        return minX(id) + size / 2;
    }

    public int centerZ(PlotId id) {
        return minZ(id) + size / 2;
    }
}
