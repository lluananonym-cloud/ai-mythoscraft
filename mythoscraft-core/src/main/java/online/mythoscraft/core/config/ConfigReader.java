package online.mythoscraft.core.config;

import online.mythoscraft.core.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validating reader. Invalid values are reported once and replaced by a safe default, so a single broken option
 * never takes the whole plugin down.
 */
public final class ConfigReader {

    private final ConfigurationSection section;
    private final String file;
    private final ConfigProblems problems;

    public ConfigReader(ConfigurationSection section, String file, ConfigProblems problems) {
        this.section = section == null ? new MemoryConfiguration() : section;
        this.file = file;
        this.problems = problems;
    }

    public ConfigReader sub(String path) {
        ConfigurationSection child = section.getConfigurationSection(path);
        if (child == null && section.contains(path)) {
            warn(path, "muss ein Abschnitt sein – Standardwerte werden verwendet");
        }
        return new ConfigReader(child, file, problems);
    }

    public ConfigurationSection raw() {
        return section;
    }

    public boolean has(String path) {
        return section.contains(path);
    }

    public Set<String> keys() {
        return section.getKeys(false);
    }

    public int integer(String path, int def, int min, int max) {
        long v = longValue(path, def, min, max);
        return (int) v;
    }

    public long longValue(String path, long def, long min, long max) {
        Object o = section.get(path);
        if (o == null) {
            return def;
        }
        long value;
        if (o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            value = n.longValue();
        } else {
            try {
                value = Long.parseLong(String.valueOf(o).trim());
            } catch (NumberFormatException e) {
                warn(path, "'" + o + "' ist keine ganze Zahl – verwende " + def);
                return def;
            }
        }
        if (value < min || value > max) {
            warn(path, value + " liegt außerhalb von " + min + " bis " + max + " – verwende " + def);
            return def;
        }
        return value;
    }

    public double decimal(String path, double def, double min, double max) {
        Object o = section.get(path);
        if (o == null) {
            return def;
        }
        double value;
        if (o instanceof Number n) {
            value = n.doubleValue();
        } else {
            try {
                value = Double.parseDouble(String.valueOf(o).trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                warn(path, "'" + o + "' ist keine Zahl – verwende " + def);
                return def;
            }
        }
        if (!Double.isFinite(value) || value < min || value > max) {
            warn(path, value + " liegt außerhalb von " + min + " bis " + max + " – verwende " + def);
            return def;
        }
        return value;
    }

    public boolean bool(String path, boolean def) {
        Object o = section.get(path);
        if (o == null) {
            return def;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("yes") || s.equals("ja") || s.equals("an") || s.equals("on")) {
            return true;
        }
        if (s.equals("false") || s.equals("no") || s.equals("nein") || s.equals("aus") || s.equals("off")) {
            return false;
        }
        warn(path, "'" + o + "' ist kein true/false – verwende " + def);
        return def;
    }

    public String string(String path, String def) {
        Object o = section.get(path);
        if (o == null) {
            return def;
        }
        if (o instanceof ConfigurationSection || o instanceof List<?>) {
            warn(path, "muss ein Text sein – verwende Standard");
            return def;
        }
        return String.valueOf(o);
    }

    public List<String> strings(String path) {
        if (!section.contains(path)) {
            return Collections.emptyList();
        }
        Object o = section.get(path);
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object e : list) {
                out.add(String.valueOf(e));
            }
            return out;
        }
        if (o instanceof String s) {
            return List.of(s);
        }
        warn(path, "muss eine Liste sein");
        return Collections.emptyList();
    }

    public <E extends Enum<E>> E enumValue(String path, Class<E> type, E def) {
        String s = section.getString(path);
        if (s == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            warn(path, "'" + s + "' ist ungültig – verwende " + def.name());
            return def;
        }
    }

    public Material material(String path, Material def) {
        String s = section.getString(path);
        if (s == null) {
            return def;
        }
        Material m = Material.matchMaterial(s.trim());
        if (m == null) {
            warn(path, "unbekanntes Material '" + s + "' – verwende " + def.name());
            return def;
        }
        return m;
    }

    public ZoneId zone(String path, ZoneId def) {
        String s = section.getString(path);
        if (s == null) {
            return def;
        }
        try {
            return ZoneId.of(s.trim());
        } catch (DateTimeException e) {
            warn(path, "unbekannte Zeitzone '" + s + "' – verwende " + def.getId());
            return def;
        }
    }

    public Duration duration(String path, Duration def) {
        Object o = section.get(path);
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return Duration.ofSeconds(n.longValue());
        }
        return Durations.parse(String.valueOf(o)).orElseGet(() -> {
            warn(path, "'" + o + "' ist keine gültige Dauer (z.B. 30s, 10m, 2h) – verwende " + Durations.format(def));
            return def;
        });
    }

    public List<LocalTime> times(String path) {
        List<LocalTime> out = new ArrayList<>();
        for (String s : strings(path)) {
            try {
                out.add(LocalTime.parse(s.trim().length() == 4 ? "0" + s.trim() : s.trim()));
            } catch (DateTimeParseException e) {
                warn(path, "'" + s + "' ist keine Uhrzeit (HH:mm) – wird ignoriert");
            }
        }
        return out;
    }

    public void warn(String path, String message) {
        String full = section.getCurrentPath() == null || section.getCurrentPath().isEmpty()
                ? path : section.getCurrentPath() + "." + path;
        problems.warn(file, full, message);
    }
}
