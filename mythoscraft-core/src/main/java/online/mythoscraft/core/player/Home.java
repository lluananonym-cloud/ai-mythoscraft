package online.mythoscraft.core.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Home(String name, String world, double x, double y, double z, float yaw, float pitch, long created) {

    public static Home of(String name, Location loc) {
        return new Home(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                System.currentTimeMillis());
    }

    /** Null when the world is not loaded. */
    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
