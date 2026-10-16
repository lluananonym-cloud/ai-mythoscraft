package online.mythoscraft.core.arena;

public enum ArenaState {
    /** Closed – nobody can join. */
    DISABLED,
    /** Open and empty. */
    WAITING,
    /** Open with players inside. */
    RUNNING,
    /** Blocks are being restored – joining is blocked. */
    RESETTING
}
