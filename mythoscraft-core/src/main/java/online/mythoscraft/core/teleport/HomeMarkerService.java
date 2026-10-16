package online.mythoscraft.core.teleport;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.player.Home;
import online.mythoscraft.core.player.PlayerData;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Makes the own homes clearly visible: a light beam up into the sky plus a turning ring of coloured dust on the
 * ground. Only the owner sees it (homes.particles.enabled / view-distance in config.yml).
 */
public final class HomeMarkerService {

    private final MythosCraft plugin;
    private int tick;

    public HomeMarkerService(MythosCraft plugin) {
        this.plugin = plugin;
        plugin.tasks().repeat(this, 40, 5, "home markers", this::tick);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("homes.particles.enabled", true)) {
            return;
        }
        double view = plugin.getConfig().getDouble("homes.particles.view-distance", 96);
        tick++;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PlayerData data = plugin.playerData().get(p);
            if (data == null) {
                continue;
            }
            for (Home h : data.homes().values()) {
                if (!h.world().equals(p.getWorld().getName())) {
                    continue;
                }
                Location base = new Location(p.getWorld(), h.x(), h.y(), h.z());
                double d2 = base.distanceSquared(p.getLocation());
                if (d2 > view * view) {
                    continue;
                }
                // beam: dense near the ground, reaching 24 blocks up so it is seen from far away
                for (double y = 0.2; y < 24; y += d2 < 16 * 16 ? 0.35 : 0.8) {
                    p.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0.02, 0.02, 0.02, 0);
                }
                // turning ring on the ground (gold + violet)
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16 + tick * 0.12;
                    Location r = base.clone().add(Math.cos(a) * 1.1, 0.15, Math.sin(a) * 1.1);
                    p.spawnParticle(Particle.DUST, r, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(i % 2 == 0 ? Color.fromRGB(0xf59e0b) : Color.fromRGB(0xc084fc), 1.3f));
                }
                if (tick % 4 == 0) {
                    p.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.05);
                }
            }
        }
    }
}
