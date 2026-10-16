package online.mythoscraft.core.build;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.Chunks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Places a {@link BlockPlan} in the world without lag: chunks are loaded asynchronously, then the clearing pass and
 * the placement run with a fixed block budget per tick (bottom-up, without physics).
 */
public final class BuildJob {

    private BuildJob() {
    }

    public static void run(MythosCraft plugin, BlockPlan plan, World world, int ox, int oy, int oz, int blocksPerTick,
                           Consumer<String> progress, Runnable done) {
        BlockData[] palette = new BlockData[plan.palette().size()];
        for (int i = 0; i < palette.length; i++) {
            try {
                palette[i] = Bukkit.createBlockData(plan.palette().get(i));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid block in structure: " + plan.palette().get(i) + " – using stone");
                palette[i] = Material.STONE.createBlockData();
            }
        }
        List<int[]> entries = plan.entries();
        int radius = 0;
        for (BlockPlan.Clear c : plan.clears()) {
            radius = Math.max(radius, c.radius());
        }
        int minX = Math.min(ox + plan.minX(), ox - radius);
        int maxX = Math.max(ox + plan.maxX(), ox + radius);
        int minZ = Math.min(oz + plan.minZ(), oz - radius);
        int maxZ = Math.max(oz + plan.maxZ(), oz + radius);
        BlockData air = Material.AIR.createBlockData();
        Chunks.withLoaded(plugin, world, minX, minZ, maxX, maxZ, release -> new BukkitRunnable() {
            int clearIndex;
            int cx = Integer.MIN_VALUE;
            int cy;
            int cz;
            int placed;
            long lastReport;

            @Override
            public void run() {
                try {
                    int budget = blocksPerTick;
                    while (budget > 0 && clearIndex < plan.clears().size()) {
                        BlockPlan.Clear c = plan.clears().get(clearIndex);
                        if (cx == Integer.MIN_VALUE) {
                            cx = -c.radius();
                            cz = -c.radius();
                            cy = c.y1();
                        }
                        long r2 = (long) c.radius() * c.radius();
                        for (; budget > 0 && cy <= c.y2(); budget--) {
                            if ((long) cx * cx + (long) cz * cz <= r2 && !plan.has(cx, cy, cz)) {
                                Block b = world.getBlockAt(ox + cx, oy + cy, oz + cz);
                                if (!b.getType().isAir()) {
                                    b.setBlockData(air, false);
                                }
                            }
                            if (++cx > c.radius()) {
                                cx = -c.radius();
                                if (++cz > c.radius()) {
                                    cz = -c.radius();
                                    cy++;
                                }
                            }
                        }
                        if (cy > c.y2()) {
                            clearIndex++;
                            cx = Integer.MIN_VALUE;
                        }
                    }
                    for (; budget > 0 && placed < entries.size(); budget--, placed++) {
                        int[] e = entries.get(placed);
                        Block b = world.getBlockAt(ox + e[0], oy + e[1], oz + e[2]);
                        BlockData target = palette[e[3]];
                        if (!b.getBlockData().equals(target)) {
                            b.setBlockData(target, false);
                        }
                    }
                    long now = System.currentTimeMillis();
                    if (progress != null && now - lastReport > 3000) {
                        lastReport = now;
                        int percent = entries.isEmpty() ? 100 : (int) (placed * 100L / entries.size());
                        progress.accept(clearIndex < plan.clears().size() ? "Fläche wird freigeräumt…" : percent + "%");
                    }
                    if (clearIndex >= plan.clears().size() && placed >= entries.size()) {
                        cancel();
                        release.run();
                        done.run();
                    }
                } catch (RuntimeException ex) {
                    cancel();
                    release.run();
                    plugin.getLogger().severe("Building failed: " + ex.getMessage());
                    if (progress != null) {
                        progress.accept("Fehler: " + ex.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1));
    }
}
