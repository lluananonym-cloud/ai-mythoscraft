package online.mythoscraft.core.events;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Finds random safe surface spots without loading chunks synchronously. */
public final class SurfaceFinder {

    private SurfaceFinder() {
    }

    /** Completes with a location standing on solid ground, or null after all attempts failed. */
    public static CompletableFuture<Location> find(MythosCraft plugin, World world, double centerX, double centerZ,
                                                   double minRadius, double maxRadius, int attempts,
                                                   Predicate<Block> accept) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        attempt(plugin, world, centerX, centerZ, minRadius, maxRadius, attempts, accept, result);
        return result;
    }

    private static void attempt(MythosCraft plugin, World world, double cx, double cz, double minR, double maxR,
                                int attempts, Predicate<Block> accept, CompletableFuture<Location> result) {
        if (attempts <= 0 || !plugin.isEnabled()) {
            result.complete(null);
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double angle = rnd.nextDouble(Math.PI * 2);
        double r = minR + rnd.nextDouble() * Math.max(0.0001, maxR - minR);
        int x = (int) Math.floor(cx + Math.cos(angle) * r);
        int z = (int) Math.floor(cz + Math.sin(angle) * r);
        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, ex) -> plugin.tasks().sync(() -> {
            if (ex == null && chunk != null) {
                Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (isSafe(world, top) && accept.test(top)) {
                    result.complete(top.getLocation().add(0.5, 1, 0.5));
                    return;
                }
            }
            attempt(plugin, world, cx, cz, minR, maxR, attempts - 1, accept, result);
        }));
    }

    private static boolean isSafe(World world, Block top) {
        if (top.getY() <= world.getMinHeight() + 1 || top.getY() >= world.getMaxHeight() - 3) {
            return false;
        }
        Material m = top.getType();
        if (top.isLiquid() || m == Material.LAVA || m == Material.MAGMA_BLOCK || m == Material.CACTUS
                || m == Material.POWDER_SNOW || m == Material.BEDROCK || !m.isSolid()) {
            return false;
        }
        return top.getRelative(0, 1, 0).isPassable() && top.getRelative(0, 2, 0).isPassable();
    }
}
