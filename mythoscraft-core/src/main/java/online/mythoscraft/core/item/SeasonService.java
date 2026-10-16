package online.mythoscraft.core.item;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.Text;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seasonal events (Halloween "Schauernacht", Frostfest, …) by calendar date in the configured time zone. While a
 * season runs, hostile mobs can drop the season's items and crate keys.
 */
public final class SeasonService implements Listener {

    public record Season(String id, MonthDay start, MonthDay end, String name, String crate, double dropChance,
                         List<CustomItem> drops, double keyChance) {

        @Override
        public String name() {
            return online.mythoscraft.core.config.ConfigText.tr(name);
        }

        boolean contains(MonthDay day) {
            if (!start.isAfter(end)) {
                return !day.isBefore(start) && !day.isAfter(end);
            }
            return !day.isBefore(start) || !day.isAfter(end);
        }
    }

    private final MythosCraft plugin;
    private final List<Season> seasons = new ArrayList<>();
    private final online.mythoscraft.core.config.YamlStore overrideStore;

    public SeasonService(MythosCraft plugin) {
        this.plugin = plugin;
        this.overrideStore = new online.mythoscraft.core.config.YamlStore(plugin, "data/season.yml");
        overrideStore.load(false);
    }

    /** "auto" (by date), "none" (no season) or a season id (started early by an admin). */
    public String mode() {
        return overrideStore.yaml().getString("mode", "auto");
    }

    /** Admin override: a season id, "none" or "auto". Returns false for an unknown season id. */
    public boolean setMode(String mode) {
        String m = mode.toLowerCase(java.util.Locale.ROOT);
        if (!m.equals("auto") && !m.equals("none") && byId(m) == null) {
            return false;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = overrideStore.yaml();
        yaml.set("mode", m);
        overrideStore.replace(yaml);
        overrideStore.saveAsync(plugin.tasks());
        return true;
    }

    public Season byId(String id) {
        for (Season s : seasons) {
            if (s.id().equalsIgnoreCase(id)) {
                return s;
            }
        }
        return null;
    }

    public void reload(ConfigReader root) {
        seasons.clear();
        ConfigReader cfg = root.sub("seasons");
        for (String id : cfg.keys()) {
            ConfigReader s = cfg.sub(id);
            try {
                MonthDay start = MonthDay.parse("--" + s.string("start", "01-01"));
                MonthDay end = MonthDay.parse("--" + s.string("end", "01-01"));
                List<CustomItem> drops = new ArrayList<>();
                for (String itemId : s.strings("drops.items")) {
                    CustomItem item = CustomItem.byId(itemId);
                    if (item == null || !item.giveable()) {
                        s.warn("drops.items", "unbekanntes Item '" + itemId + "'");
                    } else {
                        drops.add(item);
                    }
                }
                seasons.add(new Season(id, start, end, s.string("name", id), s.string("crate", id),
                        s.decimal("drops.chance", 0.03, 0, 1), drops, s.decimal("drops.key-chance", 0.005, 0, 1)));
            } catch (DateTimeParseException e) {
                s.warn("start", "Datum muss MM-DD sein (z.B. 10-15)");
            }
        }
    }

    public List<Season> all() {
        return List.copyOf(seasons);
    }

    public Season active() {
        String mode = mode();
        if (mode.equals("none")) {
            return null;
        }
        if (!mode.equals("auto")) {
            Season forced = byId(mode);
            if (forced != null) {
                return forced;
            }
        }
        return active(plugin.multipliers().today());
    }

    public Season active(LocalDate date) {
        MonthDay day = MonthDay.from(date);
        for (Season s : seasons) {
            if (s.contains(day)) {
                return s;
            }
        }
        return null;
    }

    public Component statusLine() {
        Season s = active();
        return s == null ? plugin.messages().get("seasons.none") : Text.parse(s.name());
    }

    public void greet(Player player) {
        Season s = active();
        if (s != null) {
            plugin.messages().send(player, "seasons.active", "season", Text.parse(s.name()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !(entity instanceof Enemy) || entity.fromMobSpawner()) {
            return;
        }
        Season s = active();
        if (s == null) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (!s.drops().isEmpty() && rnd.nextDouble() < s.dropChance()) {
            CustomItem item = s.drops().get(rnd.nextInt(s.drops().size()));
            event.getDrops().add(plugin.items().create(item));
        }
        if (rnd.nextDouble() < s.keyChance()) {
            ItemStack key = plugin.crates().createKey(s.crate(), 1);
            if (key != null) {
                event.getDrops().add(key);
                plugin.messages().broadcast("seasons.key-drop", "player", killer.getName(),
                        "key", Text.parse(plugin.crates().type(s.crate()).keyName()));
            }
        }
    }
}
