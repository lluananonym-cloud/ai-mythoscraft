package online.mythoscraft.core.crate;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/**
 * A crate standing in the world. Its visuals (base + lid display, hitbox, label) are non-persistent entities that are
 * spawned whenever the chunk is loaded – nothing is ever saved into the world, so no duplicates or orphans.
 */
public final class PlacedCrate {

    private final String id;
    private final String type;
    private final Location location;
    private final float yaw;
    ItemDisplay base;
    ItemDisplay lid;
    Interaction hitbox;
    TextDisplay label;
    boolean opening;

    public PlacedCrate(String id, String type, Location location, float yaw) {
        this.id = id;
        this.type = type;
        this.location = location;
        this.yaw = yaw;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    /** Block position (lower north-west corner). */
    public Location location() {
        return location;
    }

    public float yaw() {
        return yaw;
    }

    public boolean spawned() {
        return hitbox != null && hitbox.isValid();
    }

    public boolean owns(Entity entity) {
        return entity.equals(hitbox) || entity.equals(base) || entity.equals(lid);
    }

    void despawn() {
        for (Entity e : new Entity[]{base, lid, hitbox, label}) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        base = null;
        lid = null;
        hitbox = null;
        label = null;
    }

    public long chunkKey() {
        return chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
