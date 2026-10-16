package online.mythoscraft.plots;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Builds the plot world chunk by chunk while it is explored: bedrock, stone, dirt and grass on the plots, a road
 * between them and a slab border around every plot. No caves, ores, structures or mobs – an endless, flat plot raster.
 */
public final class PlotGenerator extends ChunkGenerator {

    private final PlotGrid grid;
    private final Material road;
    private final Material roadEdge;
    private final Material border;

    public PlotGenerator(PlotGrid grid, Material road, Material roadEdge, Material border) {
        this.grid = grid;
        this.road = road;
        this.roadEdge = roadEdge;
        this.border = border;
    }

    /** What stands at x / y / z in a fresh (unclaimed) plot world. */
    public Material blockAt(int x, int y, int z, int minY) {
        int top = PlotGrid.SURFACE;
        if (y == minY) {
            return Material.BEDROCK;
        }
        if (y < top - 4) {
            return Material.STONE;
        }
        boolean isRoad = grid.isRoad(x, z);
        if (y < top) {
            return Material.DIRT;
        }
        if (y == top) {
            if (!isRoad) {
                return Material.GRASS_BLOCK;
            }
            return grid.isBorder(x, z) ? roadEdge : road;
        }
        if (y == top + 1 && isRoad && grid.isBorder(x, z)) {
            return border;
        }
        return Material.AIR;
    }

    @Override
    public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = info.getMinHeight();
        data.setRegion(0, minY, 0, 16, minY + 1, 16, Material.BEDROCK);
        data.setRegion(0, minY + 1, 0, 16, PlotGrid.SURFACE - 4, 16, Material.STONE);
        data.setRegion(0, PlotGrid.SURFACE - 4, 0, 16, PlotGrid.SURFACE, 16, Material.DIRT);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunkX << 4) + lx;
                int z = (chunkZ << 4) + lz;
                data.setBlock(lx, PlotGrid.SURFACE, lz, blockAt(x, PlotGrid.SURFACE, z, minY));
                Material above = blockAt(x, PlotGrid.SURFACE + 1, z, minY);
                if (above != Material.AIR) {
                    data.setBlock(lx, PlotGrid.SURFACE + 1, lz, above);
                }
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.PLAINS);
            }
        };
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
