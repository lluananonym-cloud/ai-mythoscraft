package online.mythoscraft.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Collects config validation warnings so they can be shown in the log and in /mythos status. */
public final class ConfigProblems {

    private final Logger logger;
    private final List<String> problems = new ArrayList<>();

    public ConfigProblems(Logger logger) {
        this.logger = logger;
    }

    public void warn(String file, String path, String message) {
        String line = file + " → '" + path + "': " + message;
        problems.add(line);
        logger.warning("[Config] " + line);
    }

    public void clear() {
        problems.clear();
    }

    public List<String> list() {
        return List.copyOf(problems);
    }
}
