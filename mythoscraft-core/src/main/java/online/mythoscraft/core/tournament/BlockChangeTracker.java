package online.mythoscraft.core.tournament;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the original state of every block that changes during a tournament (placed blocks, liquids, fire,
 * obsidian/cobblestone, falling blocks …) and puts everything back afterwards. Only tracked blocks may be broken.
 */
public final class BlockChangeTracker {

    private final Map<Long, BlockData> originals = new LinkedHashMap<>();
    private World world;

    public void start(World world) {
        this.world = world;
        originals.clear();
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Stores the current state as original unless this block was already tracked. */
    public void record(Block block) {
        record(block, block.getBlockData());
    }

    public void record(Block block, BlockData before) {
        if (world != null && block.getWorld().equals(world)) {
            originals.putIfAbsent(key(block.getX(), block.getY(), block.getZ()), before.clone());
        }
    }

    public boolean isTracked(Block block) {
        return world != null && block.getWorld().equals(world) && originals.containsKey(key(block.getX(), block.getY(), block.getZ()));
    }

    public int size() {
        return originals.size();
    }

    /** Restores all original states (newest changes first) and forgets them. Returns the number of blocks. */
    public int restore() {
        if (world == null) {
            return 0;
        }
        Object[] entries = originals.entrySet().toArray();
        for (int i = entries.length - 1; i >= 0; i--) {
            @SuppressWarnings("unchecked")
            Map.Entry<Long, BlockData> e = (Map.Entry<Long, BlockData>) entries[i];
            int[] p = decode(e.getKey());
            Block b = world.getBlockAt(p[0], p[1], p[2]);
            if (!b.getBlockData().equals(e.getValue())) {
                b.setBlockData(e.getValue(), false);
            }
        }
        int n = originals.size();
        originals.clear();
        return n;
    }

    /** Decodes a key (package visible for tests). */
    static int[] decode(long k) {
        int x = (int) (k >> 38);
        int z = (int) ((k >> 12) & 0x3FFFFFF);
        int y = (int) (k & 0xFFF);
        return new int[]{(x << 6) >> 6, (y << 20) >> 20, (z << 6) >> 6};
    }
}
