package online.mythoscraft.core.arena;

import org.bukkit.GameMode;
import org.bukkit.Location;

/**
 * A player inside an arena. {@code savedState} holds the serialised inventory in memory (the database copy only
 * exists for crash recovery).
 */
public record ArenaSession(String arena, boolean spectator, Location returnLocation, GameMode previousGameMode,
                           byte[] savedState) {
}
