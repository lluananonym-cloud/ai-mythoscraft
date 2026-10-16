package online.mythoscraft.core.util;

import online.mythoscraft.core.MythosCraft;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class Chunks {

    private Chunks() {
    }

    /**
     * Loads every chunk overlapping the block box asynchronously, pins them with plugin tickets and then runs the job
     * on the main thread. The job receives a release action that removes the tickets again.
     */
    public static void withLoaded(MythosCraft plugin, World world, int minX, int minZ, int maxX, int maxZ, Consumer<Runnable> job) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
                futures.add(world.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, ex) -> plugin.tasks().sync(() -> {
            for (int[] c : chunks) {
                world.addPluginChunkTicket(c[0], c[1], plugin);
            }
            job.accept(() -> {
                for (int[] c : chunks) {
                    world.removePluginChunkTicket(c[0], c[1], plugin);
                }
            });
        }));
    }
}
