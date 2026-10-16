package online.mythoscraft.core.progress;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Effects;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Jobs: normal play gives job XP and a few coins, every level-up pays gems. Blocks placed by players never count for
 * mining/woodcutting (no place-and-break farming), the builder job is capped per hour. Also handles the rare gem finds
 * when mining valuable ores or fishing.
 */
public final class JobsService implements Listener {

    /** XP and coins for one action. */
    record Pay(int xp, double coins) {
    }

    private static final Set<Material> STONE = Set.of(Material.STONE, Material.DEEPSLATE, Material.NETHERRACK, Material.ANDESITE,
            Material.DIORITE, Material.GRANITE, Material.TUFF, Material.BLACKSTONE, Material.BASALT, Material.END_STONE,
            Material.CALCITE, Material.SANDSTONE, Material.TERRACOTTA);
    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH, Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);
    private static final Map<Material, Pay> ORES = new EnumMap<>(Material.class);

    static {
        put(4, 1.5, "COAL_ORE", "DEEPSLATE_COAL_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE");
        put(7, 3, "IRON_ORE", "DEEPSLATE_IRON_ORE", "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE");
        put(10, 5, "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE");
        put(25, 15, "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE");
        put(30, 20, "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE");
        put(45, 30, "ANCIENT_DEBRIS");
        put(6, 2, "AMETHYST_CLUSTER");
    }

    private static void put(int xp, double coins, String... names) {
        for (String n : names) {
            ORES.put(Material.valueOf(n), new Pay(xp, coins));
        }
    }

    private final MythosCraft plugin;
    private final Set<Long> placed = new LinkedHashSet<>();
    private final Map<UUID, Double> coinRest = new HashMap<>();
    private final Map<UUID, int[]> builderHour = new HashMap<>();
    private final Map<UUID, Long> lastBar = new HashMap<>();
    private boolean enabled = true;
    private double coinFactor = 1.0;
    private int builderCap = 600;
    private double diamondGemChance = 0.04;
    private double emeraldGemChance = 0.05;
    private double debrisGemChance = 0.12;
    private double fishGemChance = 0.02;

    public JobsService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("jobs");
        enabled = cfg.bool("enabled", true);
        coinFactor = cfg.decimal("coin-factor", 1.0, 0, 100);
        builderCap = cfg.integer("builder-xp-per-hour", 600, 0, 1_000_000);
        ConfigReader gems = root.sub("gem-finds");
        diamondGemChance = gems.decimal("diamond", 0.04, 0, 1);
        emeraldGemChance = gems.decimal("emerald", 0.05, 0, 1);
        debrisGemChance = gems.decimal("ancient-debris", 0.12, 0, 1);
        fishGemChance = gems.decimal("fishing-treasure", 0.02, 0, 1);
    }

    public int level(Player p, Job job) {
        return (int) Math.max(1, plugin.progress().get(p, job.levelKey()));
    }

    public long xp(Player p, Job job) {
        return plugin.progress().get(p, job.xpKey());
    }

    public int totalLevels(Player p) {
        int n = 0;
        for (Job j : Job.values()) {
            n += level(p, j);
        }
        return n;
    }

    public Component name(Job job) {
        return plugin.messages().get("jobs.names." + job.id());
    }

    // ---- earning --------------------------------------------------------------------------------------------

    /** Job XP multiplier from Silvester gear: matching tool in the main hand, trowel / fortune charm, job elixir. */
    double xpBoost(Player p, Job job) {
        double m = 1;
        online.mythoscraft.core.item.CustomItem main = plugin.items().identify(p.getInventory().getItemInMainHand());
        online.mythoscraft.core.item.CustomItem off = plugin.items().identify(p.getInventory().getItemInOffHand());
        if (main != null) {
            boolean match = switch (main) {
                case NEWYEAR_PICKAXE -> job == Job.MINER;
                case NEWYEAR_AXE -> job == Job.LUMBERJACK;
                case NEWYEAR_HOE -> job == Job.FARMER;
                case NEWYEAR_ROD -> job == Job.FISHER;
                case NEWYEAR_SWORD -> job == Job.HUNTER;
                default -> false;
            };
            if (match) {
                m += 0.5;
            }
        }
        if (off == online.mythoscraft.core.item.CustomItem.NEWYEAR_TROWEL && job == Job.BUILDER) {
            m += 1.0;
        }
        if (off == online.mythoscraft.core.item.CustomItem.FORTUNE_CHARM) {
            m += 0.25;
        }
        if (plugin.progress().get(p, "jobs.boost-until") > System.currentTimeMillis()) {
            m *= 2;
        }
        m *= plugin.boosters().factor(p.getUniqueId(), online.mythoscraft.core.item.BoosterService.Type.XP);
        m *= plugin.rankPerks().jobXpFactor(p);
        return m;
    }

    void reward(Player p, Job job, Pay pay, Location where) {
        double boost = xpBoost(p, job);
        if (boost > 1) {
            pay = new Pay((int) Math.round(pay.xp() * boost), pay.coins() * Math.min(boost, 2));
        }
        if (!enabled || !plugin.progress().loaded(p) || p.getGameMode() == GameMode.CREATIVE || plugin.arenas().isInArena(p)
                || plugin.tournaments().isParticipant(p)) {
            return;
        }
        int level = level(p, job);
        ProgressMath.Gain gain = ProgressMath.addXp(level, xp(p, job), pay.xp());
        plugin.progress().set(p, job.xpKey(), gain.xp());
        if (gain.levelsGained() > 0) {
            plugin.progress().set(p, job.levelKey(), gain.level());
        }
        PlayerData data = plugin.playerData().get(p);
        long coins = 0;
        if (data != null && pay.coins() > 0) {
            double c = coinRest.getOrDefault(p.getUniqueId(), 0.0) + pay.coins() * coinFactor * ProgressMath.coinMultiplier(level);
            coins = (long) Math.floor(c);
            coinRest.put(p.getUniqueId(), c - coins);
            if (coins > 0) {
                plugin.economy().earn(data, Currency.COINS, coins, true);
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastBar.getOrDefault(p.getUniqueId(), 0L) > 700 || gain.levelsGained() > 0) {
            lastBar.put(p.getUniqueId(), now);
            plugin.messages().actionBar(p, "jobs.gain", "job", name(job), "xp", pay.xp(), "level", gain.level(),
                    "current", gain.xp(), "next", gain.level() >= ProgressMath.MAX_JOB_LEVEL ? "MAX" : String.valueOf(ProgressMath.xpForNextLevel(gain.level())));
        }
        for (int lvl = level + 1; lvl <= gain.level(); lvl++) {
            levelUp(p, job, lvl, data);
        }
    }

    /** One-time back-payment of the new level rewards for levels reached before (progress key jobs.backpay.v2). */
    public void backPay(Player p) {
        if (plugin.progress().has(p, "jobs.backpay.v2")) {
            return;
        }
        PlayerData data = plugin.playerData().get(p);
        if (data == null) {
            return;
        }
        long owed = 0;
        for (Job job : Job.values()) {
            owed += ProgressMath.backPay(level(p, job));
        }
        plugin.progress().set(p, "jobs.backpay.v2", 1);
        if (owed > 0) {
            long paid = plugin.economy().earn(data, Currency.GEMS, owed, false);
            plugin.playerData().save(data);
            plugin.messages().send(p, "jobs.backpay", "gems", paid);
        }
    }

    private void levelUp(Player p, Job job, int level, PlayerData data) {
        plugin.clanWar().add(p.getUniqueId(), online.mythoscraft.core.clan.ClanWarService.Source.JOB_LEVEL);
        long gems = ProgressMath.gemsForLevel(level);
        if (data != null) {
            gems = plugin.economy().earn(data, Currency.GEMS, gems, false);
            plugin.playerData().save(data);
        }
        final long shownGems = gems;
        p.showTitle(plugin.messages().in(p, () -> Title.title(plugin.messages().get("jobs.levelup-title", "job", name(job), "level", level),
                plugin.messages().get("jobs.levelup-subtitle", "gems", shownGems),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500)))));
        plugin.messages().send(p, "jobs.levelup", "job", name(job), "level", level, "gems", gems);
        SoundSpec.of("ui.toast.challenge_complete", 0.7f, 1.3f).play(p);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.2);
        if (level % 10 == 0) {
            plugin.messages().broadcast("jobs.levelup-broadcast", "player", p.getName(), "job", name(job), "level", level);
        }
        plugin.achievements().check(p);
    }

    private static long key(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | (b.getY() & 0xFFF)
                ^ ((long) b.getWorld().getUID().hashCode() << 20);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material m = event.getBlockPlaced().getType();
        if (ORES.containsKey(m) || STONE.contains(m) || isLog(m) || m == Material.MELON || m == Material.PUMPKIN) {
            placed.add(key(event.getBlockPlaced()));
            if (placed.size() > 300_000) {
                placed.remove(placed.iterator().next());
            }
        }
        Player p = event.getPlayer();
        if (m.isSolid() && builderCap > 0) {
            int hour = (int) (System.currentTimeMillis() / 3_600_000L);
            int[] slot = builderHour.computeIfAbsent(p.getUniqueId(), u -> new int[]{hour, 0});
            if (slot[0] != hour) {
                slot[0] = hour;
                slot[1] = 0;
            }
            if (slot[1] < builderCap) {
                slot[1]++;
                reward(p, Job.BUILDER, new Pay(1, 0.3), event.getBlockPlaced().getLocation());
                plugin.quests().count(p, QuestService.Type.PLACE, 1);
            }
        }
    }

    private static boolean isLog(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_WOOD") || n.endsWith("_HYPHAE");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Player p = event.getPlayer();
        Material m = b.getType();
        boolean wasPlaced = placed.remove(key(b));
        if (ORES.containsKey(m)) {
            if (wasPlaced) {
                return;
            }
            reward(p, Job.MINER, ORES.get(m), b.getLocation());
            plugin.quests().count(p, QuestService.Type.ORE, 1);
            gemFind(p, m, b.getLocation().add(0.5, 0.5, 0.5));
        } else if (STONE.contains(m)) {
            if (!wasPlaced) {
                reward(p, Job.MINER, new Pay(1, 0.1), b.getLocation());
            }
        } else if (isLog(m)) {
            if (!wasPlaced) {
                reward(p, Job.LUMBERJACK, new Pay(3, 1), b.getLocation());
                plugin.quests().count(p, QuestService.Type.LOGS, 1);
            }
        } else if (CROPS.contains(m) && b.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
            reward(p, Job.FARMER, new Pay(3, 1), b.getLocation());
            plugin.quests().count(p, QuestService.Type.HARVEST, 1);
        } else if ((m == Material.MELON || m == Material.PUMPKIN) && !wasPlaced) {
            reward(p, Job.FARMER, new Pay(4, 1.5), b.getLocation());
            plugin.quests().count(p, QuestService.Type.HARVEST, 1);
        }
    }

    private void gemFind(Player p, Material ore, Location at) {
        double chance = switch (ore) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> diamondGemChance;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> emeraldGemChance;
            case ANCIENT_DEBRIS -> debrisGemChance;
            default -> 0;
        };
        chance *= plugin.boosters().findFactor(p);
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() >= chance || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerData data = plugin.playerData().get(p);
        if (data == null) {
            return;
        }
        long gems = plugin.economy().earn(data, Currency.GEMS, ore == Material.ANCIENT_DEBRIS ? 2 : 1, true);
        Effects.floatingItem(plugin, at, plugin.items().createUnique(CustomItem.GEM_SHARD), 0.7f);
        SoundSpec.of("block.amethyst_block.chime", 1f, 1.5f).play(p);
        plugin.messages().send(p, "jobs.gem-find", "gems", gems);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead.fromMobSpawner()) {
            return;
        }
        if (dead instanceof Enemy) {
            reward(killer, Job.HUNTER, new Pay(6, 1), dead.getLocation());
            plugin.quests().count(killer, QuestService.Type.MOBS, 1);
        } else if (dead instanceof Animals) {
            reward(killer, Job.HUNTER, new Pay(2, 0.5), dead.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player p) {
            reward(p, Job.FARMER, new Pay(5, 1.5), event.getEntity().getLocation());
            plugin.quests().count(p, QuestService.Type.BREED, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        Player p = event.getPlayer();
        Material m = caught.getItemStack().getType();
        boolean fish = m == Material.COD || m == Material.SALMON || m == Material.TROPICAL_FISH || m == Material.PUFFERFISH;
        reward(p, Job.FISHER, fish ? new Pay(8, 3) : new Pay(15, 6), caught.getLocation());
        plugin.quests().count(p, QuestService.Type.FISH, 1);
        if (!fish && ThreadLocalRandom.current().nextDouble() < fishGemChance * 5 || ThreadLocalRandom.current().nextDouble() < fishGemChance) {
            PlayerData data = plugin.playerData().get(p);
            if (data != null) {
                long gems = plugin.economy().earn(data, Currency.GEMS, 1, true);
                plugin.messages().send(p, "jobs.gem-find", "gems", gems);
                SoundSpec.of("block.amethyst_block.chime", 1f, 1.5f).play(p);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        coinRest.remove(id);
        builderHour.remove(id);
        lastBar.remove(id);
    }
}
