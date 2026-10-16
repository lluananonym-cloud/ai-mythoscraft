package online.mythoscraft.plots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** All claimed plots, saved in plugins/MythosPlots/plots.yml. */
public final class PlotStore {

    private final File file;
    private final Logger log;
    private final Map<PlotId, Plot> plots = new LinkedHashMap<>();

    PlotStore(File folder, Logger log) {
        this.file = new File(folder, "plots.yml");
        this.log = log;
    }

    public Plot get(PlotId id) {
        return id == null ? null : plots.get(id);
    }

    public Collection<Plot> all() {
        return plots.values();
    }

    public List<Plot> of(UUID owner) {
        List<Plot> out = new ArrayList<>();
        for (Plot p : plots.values()) {
            if (p.owner.equals(owner)) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingLong(p -> p.claimedAt));
        return out;
    }

    Plot claim(PlotId id, UUID owner, String name) {
        Plot p = new Plot(id, owner, name);
        plots.put(id, p);
        save();
        return p;
    }

    void remove(PlotId id) {
        plots.remove(id);
        save();
    }

    void load() {
        plots.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("plots");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(key);
            try {
                PlotId id = PlotId.parse(key);
                Plot p = new Plot(id, UUID.fromString(c.getString("owner")), c.getString("owner-name", "?"));
                p.claimedAt = c.getLong("claimed", System.currentTimeMillis());
                c.getStringList("trusted").forEach(u -> p.trusted.add(UUID.fromString(u)));
                c.getStringList("denied").forEach(u -> p.denied.add(UUID.fromString(u)));
                List<Double> home = c.getDoubleList("home");
                if (home.size() == 4) {
                    p.home = new double[]{home.get(0), home.get(1), home.get(2), home.get(3)};
                }
                plots.put(id, p);
            } catch (RuntimeException e) {
                log.warning("plots.yml: plot " + key + " skipped – " + e.getMessage());
            }
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Plot p : plots.values()) {
            String k = "plots." + p.id;
            y.set(k + ".owner", p.owner.toString());
            y.set(k + ".owner-name", p.ownerName);
            y.set(k + ".claimed", p.claimedAt);
            y.set(k + ".trusted", p.trusted.stream().map(UUID::toString).toList());
            y.set(k + ".denied", p.denied.stream().map(UUID::toString).toList());
            if (p.home != null) {
                y.set(k + ".home", List.of(p.home[0], p.home[1], p.home[2], p.home[3]));
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            log.warning("plots.yml could not be saved: " + e.getMessage());
        }
    }
}
