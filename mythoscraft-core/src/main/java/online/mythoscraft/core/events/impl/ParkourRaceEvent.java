package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.progress.ProgressMath;
import org.bukkit.entity.Player;

/**
 * Parkour race on the spawn course: every finish scores points – the faster, the more (minimum 10 per finish).
 * Only startable when the spawn (with parkour) was built with /spawn place.
 */
public final class ParkourRaceEvent extends MythosEvent {

    private int maxPoints = 300;

    public ParkourRaceEvent(MythosCraft plugin) {
        super(plugin, "parkourrace");
    }

    @Override
    protected int defaultDuration() {
        return 300;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        maxPoints = cfg.integer("max-points-per-finish", 300, 10, 100_000);
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.points";
    }

    @Override
    protected void onStart() {
        if (!plugin.parkour().exists()) {
            throw new IllegalStateException("no parkour course – build the spawn with /spawn place first");
        }
        plugin.messages().broadcast("events.parkourrace.hint");
    }

    @Override
    protected void onTick() {
    }

    /** Called by the parkour service for every finish during the event. */
    public void finished(Player p, long millis) {
        if (!eligible(p)) {
            return;
        }
        int points = (int) Math.max(10, maxPoints - millis / 500);
        addScore(p, points);
        plugin.messages().send(p, "events.parkourrace.scored", "points", points, "time", ProgressMath.time(millis),
                "total", Math.round(score(p.getUniqueId())));
    }

    @Override
    protected void cleanup() {
    }
}
