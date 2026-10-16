package online.mythoscraft.core.stats;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Kills, deaths, mob kills, kill streaks, kill rewards and AFK-safe playtime rewards. */
public final class StatsListener implements Listener {

    private final MythosCraft plugin;
    private boolean mobRewards = true;
    private long mobMin = 1;
    private long mobMax = 3;
    private boolean ignoreSpawnerMobs = true;
    private final Map<EntityType, Long> specialMobs = new EnumMap<>(EntityType.class);
    private long playerKillCoins = 15;
    private long sameVictimCooldownMillis = 600_000;
    private Set<Integer> streakBroadcasts = Set.of(5, 10, 15, 25);
    private boolean deathBroadcast;
    private boolean hideVanillaDeaths;
    private boolean playtimeRewards = true;
    private int playtimeInterval = 30;
    private long playtimeCoins = 50;
    private long playtimeGems;

    private final Map<String, Long> lastKillReward = new HashMap<>();
    private final Map<UUID, Integer> activeMinutes = new HashMap<>();
    private final Map<UUID, Long> lastPosition = new HashMap<>();

    public StatsListener(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("rewards");
        mobRewards = cfg.bool("mob-kill.enabled", true);
        mobMin = cfg.longValue("mob-kill.min-coins", 1, 0, 1_000_000);
        mobMax = Math.max(mobMin, cfg.longValue("mob-kill.max-coins", 3, 0, 1_000_000));
        ignoreSpawnerMobs = cfg.bool("mob-kill.ignore-spawner-mobs", true);
        specialMobs.clear();
        ConfigReader special = cfg.sub("mob-kill.special");
        for (String key : special.keys()) {
            try {
                specialMobs.put(EntityType.valueOf(key.toUpperCase()), special.longValue(key, 0, 0, 1_000_000_000L));
            } catch (IllegalArgumentException e) {
                special.warn(key, "unbekannter Mob-Typ");
            }
        }
        playerKillCoins = cfg.longValue("player-kill.coins", 15, 0, 1_000_000_000L);
        sameVictimCooldownMillis = cfg.integer("player-kill.same-victim-cooldown-minutes", 10, 0, 1440) * 60_000L;
        streakBroadcasts = new HashSet<>();
        for (String s : cfg.strings("killstreak-broadcasts")) {
            try {
                streakBroadcasts.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                cfg.warn("killstreak-broadcasts", "'" + s + "' ist keine Zahl");
            }
        }
        playtimeRewards = cfg.bool("playtime.enabled", true);
        playtimeInterval = cfg.integer("playtime.interval-minutes", 30, 1, 1440);
        playtimeCoins = cfg.longValue("playtime.coins", 50, 0, 1_000_000_000L);
        playtimeGems = cfg.longValue("playtime.gems", 0, 0, 1_000_000L);
        ConfigReader deaths = root.sub("deaths");
        deathBroadcast = deaths.bool("broadcast", false);
        hideVanillaDeaths = deaths.bool("hide-vanilla-messages", false);

        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20 * 60, 20 * 60, "playtime rewards", this::minuteTick);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        PlayerData victimData = plugin.playerData().get(victim);
        if (victimData != null) {
            victimData.addDeath();
            plugin.display().markDirty(victim.getUniqueId());
        }
        Player killer = victim.getKiller();
        if (hideVanillaDeaths) {
            event.deathMessage(null);
        }
        if (killer == null || killer.equals(victim)) {
            if (deathBroadcast) {
                plugin.messages().broadcast("death.broadcast", "player", victim.getName());
            }
            return;
        }
        PlayerData killerData = plugin.playerData().get(killer);
        if (killerData == null) {
            return;
        }
        int streak = killerData.addKill();
        plugin.display().markDirty(killer.getUniqueId());
        if (deathBroadcast) {
            plugin.messages().broadcast("death.broadcast-killed", "player", victim.getName(), "killer", killer.getName());
        }
        if (streakBroadcasts.contains(streak)) {
            plugin.messages().broadcast("death.killstreak", "player", killer.getName(), "streak", streak);
        }
        if (playerKillCoins > 0) {
            String key = killer.getUniqueId() + ":" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = lastKillReward.get(key);
            if (last == null || now - last >= sameVictimCooldownMillis) {
                lastKillReward.put(key, now);
                long earned = plugin.economy().earn(killerData, Currency.COINS, playerKillCoins, true);
                plugin.messages().actionBar(killer, "rewards.player-kill", "coins", earned, "player", victim.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        PlayerData data = plugin.playerData().get(killer);
        if (data == null) {
            return;
        }
        Long special = specialMobs.get(entity.getType());
        if (!(entity instanceof Enemy) && special == null) {
            return;
        }
        data.addMobKill();
        if (!mobRewards || (ignoreSpawnerMobs && entity.fromMobSpawner())) {
            return;
        }
        long base = special != null ? special : ThreadLocalRandom.current().nextLong(mobMin, mobMax + 1);
        if (base > 0) {
            long earned = plugin.economy().earn(data, Currency.COINS, base, true);
            plugin.messages().actionBar(killer, "rewards.mob-kill", "coins", earned);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeMinutes.remove(event.getPlayer().getUniqueId());
        lastPosition.remove(event.getPlayer().getUniqueId());
    }

    private void minuteTick() {
        if (lastKillReward.size() > 5_000) {
            long now = System.currentTimeMillis();
            lastKillReward.values().removeIf(t -> now - t > sameVictimCooldownMillis);
        }
        if (!playtimeRewards) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            long pos = (((long) l.getBlockX() & 0xFFFFF) << 40) ^ (((long) l.getBlockZ() & 0xFFFFF) << 20)
                    ^ (l.getBlockY() & 0xFFF) ^ ((long) Math.round(l.getYaw()) << 52);
            Long previous = lastPosition.put(p.getUniqueId(), pos);
            if (previous != null && previous == pos) {
                continue;
            }
            int minutes = activeMinutes.merge(p.getUniqueId(), 1, Integer::sum);
            if (minutes < playtimeInterval) {
                continue;
            }
            activeMinutes.put(p.getUniqueId(), 0);
            PlayerData data = plugin.playerData().get(p);
            if (data == null) {
                continue;
            }
            long coins = plugin.economy().earn(data, Currency.COINS, playtimeCoins, true);
            long gems = plugin.economy().earn(data, Currency.GEMS, playtimeGems, true);
            plugin.messages().send(p, gems > 0 ? "rewards.playtime-gems" : "rewards.playtime",
                    "coins", coins, "gems", gems, "minutes", playtimeInterval);
        }
    }

    public List<Integer> streakMilestones() {
        return List.copyOf(streakBroadcasts);
    }
}
