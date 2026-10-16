package online.mythoscraft.core.config;

import online.mythoscraft.core.core.Tasks;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/** A YAML data file with crash-safe (write temp, then atomic move) asynchronous saving. */
public final class YamlStore {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    public YamlStore(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
    }

    /** Loads the file, copying the bundled default first if it does not exist. Broken files are backed up. */
    public YamlConfiguration load(boolean copyDefault) {
        if (!file.exists() && copyDefault && plugin.getResource(file.getName()) != null) {
            plugin.saveResource(file.getName(), false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        if (file.exists()) {
            try {
                loaded.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                File backup = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
                plugin.getLogger().log(Level.SEVERE, "Failed to load " + file.getName() + " (" + e.getMessage()
                        + "). A copy was saved as " + backup.getName() + " and defaults are used.");
                try {
                    Files.copy(file.toPath(), backup.toPath());
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
        InputStream defaults = plugin.getResource(file.getName());
        if (defaults != null) {
            try (Reader r = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(r));
            } catch (IOException ignored) {
                // defaults are optional
            }
        }
        this.yaml = loaded;
        return loaded;
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    /** Replaces the whole content (used by stores that rebuild their file from memory on every save). */
    public void replace(YamlConfiguration content) {
        this.yaml = content;
    }

    public File file() {
        return file;
    }

    /** Serialises on the calling (main) thread and writes on the IO thread. */
    public void saveAsync(Tasks tasks) {
        String data = yaml.saveToString();
        tasks.io("save " + file.getName(), () -> write(data));
    }

    public void saveNow() {
        write(yaml.saveToString());
    }

    private void write(String data) {
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(tmp, data, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName() + ": " + e.getMessage(), e);
        }
    }
}
