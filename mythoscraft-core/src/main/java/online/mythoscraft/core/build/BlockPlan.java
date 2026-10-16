package online.mythoscraft.core.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A structure as relative block positions with block-data strings ("minecraft:stone_bricks",
 * "minecraft:lantern[hanging=true]"), named markers (spawn points, crate spots, …) and air-clearing cylinders.
 * Pure data – no Bukkit access, so generators can be unit tested.
 */
public final class BlockPlan {

    public record Marker(double x, double y, double z, float yaw) {
    }

    public record Clear(int radius, int y1, int y2) {
    }

    private static final int OFF = 4096;

    private final Map<Long, Integer> blocks = new HashMap<>();
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private final Map<String, List<Marker>> markers = new LinkedHashMap<>();
    private final List<Clear> clears = new ArrayList<>();
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;

    static long key(int x, int y, int z) {
        return ((long) (x + OFF) << 26) | ((long) (z + OFF) << 13) | (y + OFF);
    }

    static int[] decode(long key) {
        return new int[]{(int) (key >> 26) - OFF, (int) (key & 0x1FFF) - OFF, (int) ((key >> 13) & 0x1FFF) - OFF};
    }

    public void set(int x, int y, int z, String block) {
        String data = block.contains(":") ? block : "minecraft:" + block;
        Integer idx = paletteIndex.get(data);
        if (idx == null) {
            idx = palette.size();
            palette.add(data);
            paletteIndex.put(data, idx);
        }
        blocks.put(key(x, y, z), idx);
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    public void setIfEmpty(int x, int y, int z, String block) {
        if (!has(x, y, z)) {
            set(x, y, z, block);
        }
    }

    public boolean has(int x, int y, int z) {
        return blocks.containsKey(key(x, y, z));
    }

    public String get(int x, int y, int z) {
        Integer idx = blocks.get(key(x, y, z));
        return idx == null ? null : palette.get(idx);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, block);
                }
            }
        }
    }

    public void marker(String name, double x, double y, double z, float yaw) {
        markers.computeIfAbsent(name, n -> new ArrayList<>()).add(new Marker(x, y, z, yaw));
    }

    public List<Marker> markers(String name) {
        return markers.getOrDefault(name, List.of());
    }

    public void clear(int radius, int y1, int y2) {
        clears.add(new Clear(radius, y1, y2));
    }

    public List<Clear> clears() {
        return clears;
    }

    public int size() {
        return blocks.size();
    }

    public List<String> palette() {
        return palette;
    }

    /** Entries sorted bottom-up, as {x, y, z, paletteIndex}. */
    public List<int[]> entries() {
        List<int[]> out = new ArrayList<>(blocks.size());
        for (Map.Entry<Long, Integer> e : blocks.entrySet()) {
            int[] p = decode(e.getKey());
            out.add(new int[]{p[0], p[1], p[2], e.getValue()});
        }
        out.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[2], b[2]));
        return out;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }
}
