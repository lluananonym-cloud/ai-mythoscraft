package online.mythoscraft.core.command;

/** Thrown inside commands to stop with a user facing message (message key + placeholders). */
public final class CommandFail extends Exception {

    private final String key;
    private final transient Object[] placeholders;

    public CommandFail(String key, Object... placeholders) {
        super(key, null, false, false);
        this.key = key;
        this.placeholders = placeholders;
    }

    public static CommandFail usage(String usage) {
        return new CommandFail("general.usage", "usage", usage);
    }

    public String key() {
        return key;
    }

    public Object[] placeholders() {
        return placeholders;
    }
}
