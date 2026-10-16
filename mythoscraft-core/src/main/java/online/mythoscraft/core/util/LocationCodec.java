package online.mythoscraft.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Stores locations by world name so they survive worlds that are not loaded yet. */
public final class LocationCodec {

    private LocationCodec() {
    }

    public static void write(ConfigurationSection section, Location loc) {
        section.set("world", loc.getWorld() == null ? null : loc.getWorld().getName());
        section.set("x", round(loc.getX()));
        section.set("y", round(loc.getY()));
        section.set("z", round(loc.getZ()));
        section.set("yaw", (double) Math.round(loc.getYaw() * 10) / 10.0);
        section.set("pitch", (double) Math.round(loc.getPitch() * 10) / 10.0);
    }

    /** Returns null when the section is incomplete or the world is not loaded. */
    public static Location read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || !section.contains("x") || !section.contains("y") || !section.contains("z")) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    /** Compact single-line form: "world;x;y;z;yaw;pitch". */
    public static String toString(Location loc) {
        return String.format(Locale.ROOT, "%s;%.3f;%.3f;%.3f;%.1f;%.1f", loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    public static Location fromString(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(";");
        if (p.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(p[0]);
        if (world == null) {
            return null;
        }
        try {
            float yaw = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
            float pitch = p.length > 5 ? Float.parseFloat(p[5]) : 0f;
            return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String describe(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
