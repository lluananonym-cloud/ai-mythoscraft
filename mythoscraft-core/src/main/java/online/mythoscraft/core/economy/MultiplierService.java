package online.mythoscraft.core.economy;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.util.Durations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Daily multipliers per weekday (in the configured time zone, default Europe/Berlin) combined with temporary
 * boosters started by admins. Boosters survive restarts via state.yml.
 */
public final class MultiplierService {

    private final MythosCraft plugin;
    private final YamlStore state;
    private final Map<DayOfWeek, EnumMap<MultiplierType, Double>> daily = new EnumMap<>(DayOfWeek.class);
    private final EnumMap<MultiplierType, Booster> boosters = new EnumMap<>(MultiplierType.class);
    private ZoneId zone = ZoneId.of("Europe/Berlin");
    private boolean announceDayChange = true;
    private LocalDate lastDay;

    public record Booster(double factor, long until, String startedBy) {
        public boolean active() {
            return until > System.currentTimeMillis();
        }
    }

    public MultiplierService(MythosCraft plugin) {
        this.plugin = plugin;
        this.state = new YamlStore(plugin, "data/state.yml");
    }

    public void reload(ConfigReader root) {
        zone = root.zone("timezone", ZoneId.of("Europe/Berlin"));
        ConfigReader cfg = root.sub("multipliers");
        announceDayChange = cfg.bool("announce-day-change", true);
        daily.clear();
        for (DayOfWeek day : DayOfWeek.values()) {
            ConfigReader d = cfg.sub("daily." + day.name().toLowerCase(Locale.ROOT));
            EnumMap<MultiplierType, Double> values = new EnumMap<>(MultiplierType.class);
            for (MultiplierType type : MultiplierType.values()) {
                values.put(type, d.decimal(type.id(), 1.0, 0.0, 20.0));
            }
            daily.put(day, values);
        }
        loadBoosters();
        lastDay = today();
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20 * 30, 20 * 30, "day change", this::tick);
    }

    private void loadBoosters() {
        YamlConfiguration yaml = state.load(false);
        boosters.clear();
        ConfigurationSection sec = yaml.getConfigurationSection("boosters");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            MultiplierType type = MultiplierType.byId(key);
            if (type == null) {
                continue;
            }
            Booster b = new Booster(sec.getDouble(key + ".factor", 1.0), sec.getLong(key + ".until"), sec.getString(key + ".by", "?"));
            if (b.active()) {
                boosters.put(type, b);
            }
        }
    }

    private void saveBoosters() {
        YamlConfiguration yaml = state.yaml();
        yaml.set("boosters", null);
        boosters.forEach((type, b) -> {
            yaml.set("boosters." + type.id() + ".factor", b.factor());
            yaml.set("boosters." + type.id() + ".until", b.until());
            yaml.set("boosters." + type.id() + ".by", b.startedBy());
        });
        state.saveAsync(plugin.tasks());
    }

    private void tick() {
        boolean changed = boosters.entrySet().removeIf(e -> {
            if (!e.getValue().active()) {
                plugin.messages().broadcast("booster.ended", "type", plugin.messages().get("multiplier." + e.getKey().id()));
                return true;
            }
            return false;
        });
        if (changed) {
            saveBoosters();
        }
        LocalDate now = today();
        if (!now.equals(lastDay)) {
            lastDay = now;
            plugin.getLogger().info("Day changed (" + zone.getId() + "): " + now);
            plugin.onDayChange(now);
            if (announceDayChange && (daily(MultiplierType.COINS) != 1 || daily(MultiplierType.GEMS) != 1
                    || daily(MultiplierType.EVENTS) != 1)) {
                plugin.messages().broadcast("multiplier.new-day", "coins", daily(MultiplierType.COINS),
                        "gems", daily(MultiplierType.GEMS), "events", daily(MultiplierType.EVENTS));
            }
        }
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public Duration untilMidnight() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone));
    }

    public double daily(MultiplierType type) {
        EnumMap<MultiplierType, Double> today = daily.get(today().getDayOfWeek());
        return today == null ? 1.0 : today.getOrDefault(type, 1.0);
    }

    public Booster booster(MultiplierType type) {
        Booster b = boosters.get(type);
        return b != null && b.active() ? b : null;
    }

    public double global(MultiplierType type) {
        Booster b = booster(type);
        return daily(type) * (b == null ? 1.0 : b.factor());
    }

    /** Global multiplier times the rank bonus of the player (coins/gems only). */
    public double forPlayer(MultiplierType type, PlayerData data) {
        double m = global(type);
        if (data != null && type != MultiplierType.EVENTS) {
            Rank rank = plugin.ranks().rankOf(data);
            m *= type == MultiplierType.COINS ? rank.coinMultiplier() : rank.gemMultiplier();
            if (plugin.boosters() != null) {
                m *= plugin.boosters().factor(data.uuid(), type) * plugin.boosters().mutationFactor(data.uuid(), type);
            }
        }
        return m;
    }

    public void startBooster(MultiplierType type, double factor, Duration duration, String by) {
        boosters.put(type, new Booster(factor, System.currentTimeMillis() + duration.toMillis(), by));
        saveBoosters();
        plugin.messages().broadcast("booster.started", "type", plugin.messages().get("multiplier." + type.id()),
                "factor", factor, "duration", Durations.format(duration), "player", by);
    }

    public boolean stopBooster(MultiplierType type) {
        if (boosters.remove(type) == null) {
            return false;
        }
        saveBoosters();
        return true;
    }
}
