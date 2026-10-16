package online.mythoscraft.core.protection;

import online.mythoscraft.core.util.Cuboid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable spatial index: regions are bucketed into 64×64 column cells per world, so a lookup only looks at the
 * handful of regions overlapping the cell instead of all regions. Very large regions (e.g. a whole-world region)
 * are kept in a small per-world list instead of filling thousands of cells.
 */
public final class RegionIndex {

    static final int CELL_SHIFT = 6;
    static final int MAX_CELLS_PER_REGION = 4096;
    private static final Region[] NONE = new Region[0];
    private static final Comparator<Region> ORDER =
            Comparator.comparingInt((Region r) -> r.priority()).reversed().thenComparing(Region::name);

    public static final RegionIndex EMPTY = new RegionIndex(Map.of());

    private final Map<String, WorldIndex> worlds;

    private record WorldIndex(Map<Long, Region[]> cells, Region[] large) {
    }

    private RegionIndex(Map<String, WorldIndex> worlds) {
        this.worlds = worlds;
    }

    public static RegionIndex build(Collection<Region> regions) {
        Map<String, Map<Long, List<Region>>> cells = new HashMap<>();
        Map<String, List<Region>> large = new HashMap<>();
        for (Region r : regions) {
            Cuboid b = r.bounds();
            int cx1 = b.minX() >> CELL_SHIFT;
            int cx2 = b.maxX() >> CELL_SHIFT;
            int cz1 = b.minZ() >> CELL_SHIFT;
            int cz2 = b.maxZ() >> CELL_SHIFT;
            long count = (long) (cx2 - cx1 + 1) * (cz2 - cz1 + 1);
            if (count > MAX_CELLS_PER_REGION) {
                large.computeIfAbsent(r.world(), w -> new ArrayList<>()).add(r);
                continue;
            }
            Map<Long, List<Region>> worldCells = cells.computeIfAbsent(r.world(), w -> new HashMap<>());
            for (int cx = cx1; cx <= cx2; cx++) {
                for (int cz = cz1; cz <= cz2; cz++) {
                    worldCells.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(r);
                }
            }
        }
        Map<String, WorldIndex> result = new HashMap<>();
        for (String world : union(cells.keySet(), large.keySet())) {
            Map<Long, Region[]> frozen = new HashMap<>();
            cells.getOrDefault(world, Map.of()).forEach((k, list) -> {
                list.sort(ORDER);
                frozen.put(k, list.toArray(NONE));
            });
            List<Region> big = large.getOrDefault(world, new ArrayList<>());
            big.sort(ORDER);
            result.put(world, new WorldIndex(frozen, big.toArray(NONE)));
        }
        return new RegionIndex(result);
    }

    private static List<String> union(Collection<String> a, Collection<String> b) {
        List<String> out = new ArrayList<>(a);
        for (String s : b) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /** True if the world has no regions at all (fast path for high-frequency events). */
    public boolean isEmpty(String world) {
        return !worlds.containsKey(world);
    }

    /** Highest-priority region containing the point, or null. */
    public Region top(String world, int x, int y, int z) {
        WorldIndex wi = worlds.get(world);
        if (wi == null) {
            return null;
        }
        Region best = firstContaining(wi.cells.getOrDefault(key(x >> CELL_SHIFT, z >> CELL_SHIFT), NONE), x, y, z, null);
        Region bigBest = firstContaining(wi.large, x, y, z, null);
        return better(best, bigBest);
    }

    /** Value of the flag in the highest-priority region that contains the point and sets the flag, or null. */
    public Boolean flag(String world, int x, int y, int z, Flag flag) {
        WorldIndex wi = worlds.get(world);
        if (wi == null) {
            return null;
        }
        Region a = firstContaining(wi.cells.getOrDefault(key(x >> CELL_SHIFT, z >> CELL_SHIFT), NONE), x, y, z, flag);
        Region b = firstContaining(wi.large, x, y, z, flag);
        Region best = better(a, b);
        return best == null ? null : best.flag(flag);
    }

    /** All regions containing the point, highest priority first. */
    public List<Region> at(String world, int x, int y, int z) {
        List<Region> out = new ArrayList<>();
        WorldIndex wi = worlds.get(world);
        if (wi == null) {
            return out;
        }
        for (Region r : wi.cells.getOrDefault(key(x >> CELL_SHIFT, z >> CELL_SHIFT), NONE)) {
            if (r.bounds().contains(x, y, z)) {
                out.add(r);
            }
        }
        for (Region r : wi.large) {
            if (r.bounds().contains(x, y, z)) {
                out.add(r);
            }
        }
        out.sort(ORDER);
        return out;
    }

    private static Region firstContaining(Region[] sorted, int x, int y, int z, Flag requiredFlag) {
        for (Region r : sorted) {
            if (r.bounds().contains(x, y, z) && (requiredFlag == null || r.flag(requiredFlag) != null)) {
                return r;
            }
        }
        return null;
    }

    private static Region better(Region a, Region b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return ORDER.compare(a, b) <= 0 ? a : b;
    }
}
