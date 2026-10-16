package online.mythoscraft.core.protection;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.Cuboid;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Admin pos1/pos2 selection shared by regions and arenas, with a short particle preview of the box. */
public final class SelectionManager {

    private static final Particle.DustOptions EDGE = new Particle.DustOptions(Color.fromRGB(0xC084FC), 1.2f);

    private final MythosCraft plugin;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public SelectionManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void set(Player player, int index, Location loc) {
        Location[] sel = selections.computeIfAbsent(player.getUniqueId(), u -> new Location[2]);
        sel[index] = loc.toBlockLocation();
        Cuboid c = cuboid(player);
        if (c != null) {
            preview(player, sel[0].getWorld(), c);
        }
    }

    public Location get(Player player, int index) {
        Location[] sel = selections.get(player.getUniqueId());
        return sel == null ? null : sel[index];
    }

    /** Null unless both points are set in the same world. */
    public Cuboid cuboid(Player player) {
        Location[] sel = selections.get(player.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null || !sel[0].getWorld().equals(sel[1].getWorld())) {
            return null;
        }
        return Cuboid.of(sel[0].getBlockX(), sel[0].getBlockY(), sel[0].getBlockZ(),
                sel[1].getBlockX(), sel[1].getBlockY(), sel[1].getBlockZ());
    }

    public World world(Player player) {
        Location[] sel = selections.get(player.getUniqueId());
        return sel == null || sel[0] == null ? null : sel[0].getWorld();
    }

    public void clear(UUID player) {
        selections.remove(player);
    }

    /** Draws the edges of the box for a few seconds (only for the selecting player). */
    public void preview(Player player, World world, Cuboid c) {
        for (int i = 0; i < 6; i++) {
            plugin.tasks().later(1 + i * 15L, "selection preview", () -> {
                if (player.isOnline() && player.getWorld().equals(world)) {
                    drawEdges(player, c);
                }
            });
        }
    }

    private static void drawEdges(Player player, Cuboid c) {
        double x1 = c.minX();
        double y1 = c.minY();
        double z1 = c.minZ();
        double x2 = c.maxX() + 1;
        double y2 = c.maxY() + 1;
        double z2 = c.maxZ() + 1;
        double[][] corners = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        Location eye = player.getLocation();
        for (int[] e : edges) {
            double[] a = corners[e[0]];
            double[] b = corners[e[1]];
            double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            int points = (int) Math.min(48, Math.max(2, len));
            for (int i = 0; i <= points; i++) {
                double t = i / (double) points;
                double x = a[0] + (b[0] - a[0]) * t;
                double y = a[1] + (b[1] - a[1]) * t;
                double z = a[2] + (b[2] - a[2]) * t;
                if (sq(x - eye.getX()) + sq(z - eye.getZ()) < 96 * 96) {
                    player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, EDGE);
                }
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
