package online.mythoscraft.core.teleport;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.player.Home;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Cooldowns;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class HomeManager {

    public static final String DEFAULT_NAME = "home";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");

    private final MythosCraft plugin;
    private final Cooldowns cooldowns;
    private int delay = 3;
    private boolean cancelOnMove = true;
    private int cooldownSeconds = 5;
    private final Set<String> blockedWorlds = new HashSet<>();

    public HomeManager(MythosCraft plugin, Cooldowns cooldowns) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("homes");
        delay = cfg.integer("teleport-delay", 3, 0, 120);
        cancelOnMove = cfg.bool("cancel-on-move", true);
        cooldownSeconds = cfg.integer("cooldown-seconds", 5, 0, 3600);
        blockedWorlds.clear();
        cfg.strings("blocked-worlds").forEach(w -> blockedWorlds.add(w.toLowerCase(Locale.ROOT)));
    }

    public void setHome(Player player, PlayerData data, String name) {
        if (!NAME.matcher(name).matches()) {
            plugin.messages().send(player, "homes.invalid-name");
            return;
        }
        Location loc = player.getLocation();
        if (blockedWorlds.contains(loc.getWorld().getName().toLowerCase(Locale.ROOT))) {
            plugin.messages().send(player, "homes.blocked-world");
            return;
        }
        if (plugin.arenas().isInArena(player) || plugin.arenas().arenaAt(loc) != null) {
            plugin.messages().send(player, "homes.no-arena");
            return;
        }
        boolean replacing = data.home(name) != null;
        int max = plugin.ranks().maxHomes(player);
        if (!replacing && data.homes().size() >= max) {
            plugin.messages().send(player, "homes.limit", "max", max);
            return;
        }
        Home home = Home.of(name, loc);
        data.putHome(home);
        plugin.playerData().saveHome(player.getUniqueId(), home).exceptionally(ex -> {
            plugin.tasks().sync(() -> plugin.messages().send(player, "general.save-failed"));
            return null;
        });
        plugin.messages().send(player, replacing ? "homes.updated" : "homes.set", "home", name,
                "count", data.homes().size(), "max", max == Integer.MAX_VALUE ? "∞" : String.valueOf(max));
    }

    public void deleteHome(Player player, PlayerData data, String name) {
        Home removed = data.removeHome(name);
        if (removed == null) {
            plugin.messages().send(player, "homes.not-found", "home", name);
            return;
        }
        plugin.playerData().deleteHome(player.getUniqueId(), removed.name());
        plugin.messages().send(player, "homes.deleted", "home", removed.name());
    }

    public void teleport(Player player, PlayerData data, String name) {
        Home home = name == null ? (data.homes().size() == 1 ? data.homes().values().iterator().next() : data.home(DEFAULT_NAME))
                : data.home(name);
        if (home == null) {
            if (name == null && data.homes().size() > 1) {
                plugin.messages().send(player, "homes.choose");
            } else {
                plugin.messages().send(player, name == null ? "homes.none" : "homes.not-found", "home", name == null ? "" : name);
            }
            return;
        }
        Location loc = home.location();
        if (loc == null) {
            plugin.messages().send(player, "homes.world-missing", "home", home.name());
            return;
        }
        if (plugin.arenas().isInArena(player)) {
            plugin.messages().send(player, "arena.leave-first");
            return;
        }
        long combat = plugin.combat().remainingSeconds(player);
        if (combat > 0) {
            plugin.messages().send(player, "teleport.in-combat", "seconds", combat);
            return;
        }
        long cd = cooldowns.remainingSeconds("home", player.getUniqueId());
        if (cd > 0) {
            plugin.messages().send(player, "teleport.cooldown", "seconds", cd);
            return;
        }
        plugin.teleports().teleport(player, loc, delay, cancelOnMove, plugin.messages().get("homes.name", "home", home.name()),
                () -> cooldowns.set("home", player.getUniqueId(), cooldownSeconds * 1000L));
    }
}
