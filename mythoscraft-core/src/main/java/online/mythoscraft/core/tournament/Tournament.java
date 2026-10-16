package online.mythoscraft.core.tournament;

import online.mythoscraft.core.arena.Arena;
import online.mythoscraft.core.arena.Kit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** State of the one running tournament. Players and bots are both identified by their entity UUID. */
final class Tournament {

    enum Phase { REGISTRATION, STARTING, RUNNING, ENDING }

    final String id = UUID.randomUUID().toString();
    final TournamentTier tier;
    final Arena arena;
    final String kitId;
    final Kit kit;
    final boolean test;
    final int slots;
    final BlockChangeTracker tracker = new BlockChangeTracker();

    Phase phase = Phase.REGISTRATION;
    /** True once fighters were teleported into the arena (arena needs a rollback). */
    boolean started;
    int secondsLeft;
    int botsToAdd;

    /** Registration order – the first {@code slots} players get a place. */
    final Set<UUID> registered = new LinkedHashSet<>();
    final Set<UUID> alive = new LinkedHashSet<>();
    final Set<UUID> bots = new LinkedHashSet<>();
    final Map<UUID, String> names = new HashMap<>();
    final Map<UUID, Integer> kills = new HashMap<>();
    final Map<UUID, UUID> lastAttacker = new HashMap<>();
    final Map<UUID, Location> returns = new HashMap<>();
    final Map<UUID, byte[]> saved = new HashMap<>();
    /** Eliminated fighters, first eliminated first. */
    final List<UUID> eliminated = new ArrayList<>();

    Tournament(TournamentTier tier, Arena arena, String kitId, Kit kit, boolean test, int slots) {
        this.tier = tier;
        this.arena = arena;
        this.kitId = kitId;
        this.kit = kit;
        this.test = test;
        this.slots = slots;
    }

    boolean fighting() {
        return phase == Phase.STARTING || phase == Phase.RUNNING;
    }

    /** Final ranking: survivors (most kills first) followed by the eliminated in reverse order. */
    List<UUID> ranking(Map<UUID, Double> health) {
        List<UUID> survivors = new ArrayList<>(alive);
        survivors.sort((a, b) -> {
            int k = Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0));
            return k != 0 ? k : Double.compare(health.getOrDefault(b, 0.0), health.getOrDefault(a, 0.0));
        });
        List<UUID> out = new ArrayList<>(survivors);
        for (int i = eliminated.size() - 1; i >= 0; i--) {
            out.add(eliminated.get(i));
        }
        return out;
    }
}
