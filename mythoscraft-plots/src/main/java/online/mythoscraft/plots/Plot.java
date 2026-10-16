package online.mythoscraft.plots;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A claimed plot: owner, trusted builders, denied players and an optional home point. */
public final class Plot {

    final PlotId id;
    UUID owner;
    String ownerName;
    long claimedAt;
    final Set<UUID> trusted = new LinkedHashSet<>();
    final Set<UUID> denied = new LinkedHashSet<>();
    /** home inside the plot: x, y, z, yaw (null = plot centre) */
    double[] home;

    Plot(PlotId id, UUID owner, String ownerName) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.claimedAt = System.currentTimeMillis();
    }

    public PlotId id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean mayBuild(UUID player) {
        return owner.equals(player) || trusted.contains(player);
    }
}
