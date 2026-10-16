package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Effects;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Timed gem race: mining ores and killing hostile mobs earn rush points; every action may also reveal a gem shard
 * (instant gem). At the end points are converted into gems and the top players get extra rewards. Blocks placed
 * during the event never count, so ores can't be farmed by placing and breaking them again.
 */
public final class GemRushEvent extends MythosEvent {

    private final Map<Material, Integer> orePoints = new EnumMap<>(Material.class);
    private int mobPoints = 2;
    private double shardChance = 0.08;
    private int pointsPerGem = 20;
    private int maxGems = 25;
    private SoundSpec shardSound = SoundSpec.NONE;
    private final Set<Long> placed = new HashSet<>();

    public GemRushEvent(MythosCraft plugin) {
        super(plugin, "gemrush");
    }

    @Override
    protected int defaultDuration() {
        return 300;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        orePoints.clear();
        ConfigReader ores = cfg.sub("points.ores");
        for (String key : ores.keys()) {
            Material m = Material.matchMaterial(key);
            if (m == null || !m.isBlock()) {
                ores.warn(key, "unbekannter Block");
                continue;
            }
            orePoints.put(m, ores.integer(key, 1, 0, 10_000));
        }
        mobPoints = cfg.integer("points.hostile-mob", 2, 0, 10_000);
        shardChance = cfg.decimal("shard-chance", 0.08, 0, 1);
        pointsPerGem = cfg.integer("points-per-gem", 20, 1, 1_000_000);
        maxGems = cfg.integer("max-gems-per-player", 25, 0, 1_000_000);
        SoundSpec s = SoundSpec.parse(cfg.string("sounds.shard", "block.amethyst_block.chime 1 1.4"));
        shardSound = s == null ? SoundSpec.NONE : s;
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.points";
    }

    @Override
    protected void onStart() {
        placed.clear();
    }

    @Override
    protected void onTick() {
        // purely action driven
    }

    private static long key(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | (b.getY() & 0xFFF);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (orePoints.containsKey(event.getBlockPlaced().getType())) {
            placed.add(key(event.getBlockPlaced()) ^ event.getBlockPlaced().getWorld().getUID().getMostSignificantBits());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Integer points = orePoints.get(block.getType());
        Player player = event.getPlayer();
        if (points == null || points <= 0 || !eligible(player)) {
            return;
        }
        if (placed.remove(key(block) ^ block.getWorld().getUID().getMostSignificantBits())) {
            return;
        }
        score(player, points, block.getLocation().add(0.5, 0.5, 0.5));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || mobPoints <= 0 || !(event.getEntity() instanceof Enemy) || !eligible(killer)
                || event.getEntity().fromMobSpawner()) {
            return;
        }
        score(killer, mobPoints, event.getEntity().getLocation().add(0, 1, 0));
    }

    private void score(Player player, int points, org.bukkit.Location where) {
        addScore(player, points);
        plugin.messages().actionBar(player, "events.gemrush.points", "points", points, "total", Math.round(score(player.getUniqueId())));
        if (ThreadLocalRandom.current().nextDouble() < shardChance) {
            PlayerData data = plugin.playerData().get(player);
            if (data != null) {
                long gems = plugin.economy().earn(data, Currency.GEMS, Math.max(1, Math.round(plugin.multipliers().global(MultiplierType.EVENTS))), false);
                Effects.floatingItem(plugin, where, plugin.items().createUnique(CustomItem.GEM_SHARD), 0.7f);
                shardSound.play(player);
                plugin.messages().send(player, "events.gemrush.shard", "gems", gems);
            }
        }
    }

    @Override
    protected void onEnd(boolean aborted) {
        if (aborted) {
            return;
        }
        double multiplier = plugin.multipliers().global(MultiplierType.EVENTS);
        for (Map.Entry<UUID, Double> e : scores.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            PlayerData data = p == null ? null : plugin.playerData().get(p);
            if (data == null) {
                continue;
            }
            long gems = Math.min(maxGems, (long) Math.floor(e.getValue() / pointsPerGem));
            if (gems > 0) {
                long credited = plugin.economy().earn(data, Currency.GEMS, Math.round(gems * multiplier), false);
                plugin.messages().send(p, "events.gemrush.converted", "points", Math.round(e.getValue()), "gems", credited);
            }
        }
    }

    @Override
    protected void cleanup() {
        placed.clear();
    }
}
