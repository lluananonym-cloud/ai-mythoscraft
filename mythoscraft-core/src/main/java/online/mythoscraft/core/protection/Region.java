package online.mythoscraft.core.protection;

import online.mythoscraft.core.util.Cuboid;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A protected box in one world. Flags that are not set fall through to lower-priority regions and finally to the
 * wilderness defaults. Mutated on the main thread only; changes to bounds or priority require an index rebuild.
 */
public final class Region {

    public enum Type { REGION, ARENA }

    private final String name;
    private final Type type;
    private String world;
    private Cuboid bounds;
    private int priority;
    private final EnumMap<Flag, Boolean> flags = new EnumMap<>(Flag.class);

    public Region(String name, Type type, String world, Cuboid bounds, int priority) {
        this.name = name;
        this.type = type;
        this.world = world;
        this.bounds = bounds;
        this.priority = priority;
    }

    public String name() {
        return name;
    }

    public Type type() {
        return type;
    }

    public String world() {
        return world;
    }

    public Cuboid bounds() {
        return bounds;
    }

    public void redefine(String world, Cuboid bounds) {
        this.world = world;
        this.bounds = bounds;
    }

    public int priority() {
        return priority;
    }

    public void priority(int priority) {
        this.priority = priority;
    }

    public Boolean flag(Flag flag) {
        return flags.get(flag);
    }

    public void flag(Flag flag, Boolean value) {
        if (value == null) {
            flags.remove(flag);
        } else {
            flags.put(flag, value);
        }
    }

    public Map<Flag, Boolean> flags() {
        return Collections.unmodifiableMap(flags);
    }

    public boolean contains(String world, int x, int y, int z) {
        return this.world.equals(world) && bounds.contains(x, y, z);
    }

    @Override
    public String toString() {
        return name + "@" + world + bounds;
    }
}
