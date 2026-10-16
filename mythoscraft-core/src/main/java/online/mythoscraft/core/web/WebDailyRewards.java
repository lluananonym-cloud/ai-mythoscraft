package online.mythoscraft.core.web;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily login rewards claimed on the website: the player signs in there with his Minecraft account
 * (/website login &lt;code&gt;) and clicks "claim" once a day. The website counts the streak (day 1 … 7, then again)
 * and hands the claim to the server through the relay; the reward of that streak day is given right away, or on the
 * next join when the player is offline (kept in data/web_rewards.yml).
 */
public final class WebDailyRewards implements Listener {

    private final MythosCraft plugin;
    private final YamlStore store;
    private final List<List<Reward>> days = new ArrayList<>();
    private boolean enabled = true;

    public WebDailyRewards(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/web_rewards.yml");
        store.load(false);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("web-daily");
        enabled = cfg.bool("enabled", true);
        days.clear();
        List<?> list = cfg.raw().getList("days");
        if (list != null) {
            int n = 0;
            for (Object o : list) {
                n++;
                List<Reward> day = new ArrayList<>();
                for (String line : String.valueOf(o).split(";")) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        day.add(Reward.parse(line.trim()));
                    } catch (IllegalArgumentException e) {
                        cfg.warn("days", "Tag " + n + ": " + e.getMessage());
                    }
                }
                days.add(day);
            }
        }
        if (days.isEmpty()) {
            days.add(List.of(Reward.parse("coins 500")));
        }
    }

    private List<Reward> rewardsFor(int streak) {
        return days.get(Math.floorMod(Math.max(1, streak) - 1, days.size()));
    }

    /** The reward table for the website (one line per streak day, in English). */
    public List<Map<String, Object>> table() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            List<String> parts = new ArrayList<>();
            int day = i + 1;
            plugin.messages().in(null, () -> {
                for (Reward r : rewardsFor(day)) {
                    parts.add(PlainTextComponentSerializer.plainText().serialize(r.describe(plugin, 1.0)));
                }
            });
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", day);
            m.put("rewards", parts);
            out.add(m);
        }
        return out;
    }

    public boolean enabled() {
        return enabled;
    }

    /** A claim from the website (relay thread → main thread). */
    void claimed(UUID uuid, String name, int streak) {
        if (!enabled) {
            return;
        }
        plugin.tasks().later(1, "web daily reward", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                give(p, streak);
                return;
            }
            String path = "pending." + uuid;
            List<Integer> pending = new ArrayList<>(store.yaml().getIntegerList(path));
            pending.add(streak);
            store.yaml().set(path, pending);
            store.yaml().set("names." + uuid, name);
            store.saveAsync(plugin.tasks());
        });
    }

    private void give(Player p, int streak) {
        for (Reward r : rewardsFor(streak)) {
            r.give(plugin, p, 1.0);
        }
        plugin.messages().send(p, "web.daily-received", "day", Math.floorMod(Math.max(1, streak) - 1, days.size()) + 1, "streak", streak);
        SoundSpec.of("entity.player.levelup", 0.8f, 1.3f).play(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String path = "pending." + p.getUniqueId();
        List<Integer> pending = store.yaml().getIntegerList(path);
        if (pending.isEmpty()) {
            return;
        }
        store.yaml().set(path, null);
        store.yaml().set("names." + p.getUniqueId(), null);
        store.saveAsync(plugin.tasks());
        plugin.tasks().later(60, "web daily pending", () -> {
            if (p.isOnline()) {
                pending.forEach(s -> give(p, s));
            }
        });
    }

    public void shutdown() {
        store.saveNow();
    }
}
