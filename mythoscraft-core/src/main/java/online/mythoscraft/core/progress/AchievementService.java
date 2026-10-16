package online.mythoscraft.core.progress;

import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Achievements ("Erfolge"): one-time gems and coins for milestones, checked every 30 seconds and after job level-ups.
 * Also pays the playtime milestones (every N hours of active playtime).
 */
public final class AchievementService {

    /** One achievement. {@code value} reads the player's current progress, {@code target} is the goal. */
    public record Achievement(String id, Material icon, long target, long gems, long coins, String category,
                              BiFunction<Player, PlayerData, Long> value) {
    }

    private final MythosCraft plugin;
    private final List<Achievement> all = new ArrayList<>();
    private int playtimeHours = 5;
    private long playtimeGems = 5;
    private long playtimeCoins = 500;

    public AchievementService(MythosCraft plugin) {
        this.plugin = plugin;
        build();
    }

    private void stat(String id, Material icon, Stat stat, long target, long gems, long coins, String category) {
        all.add(new Achievement(id, icon, target, gems, coins, category, (p, d) -> stat.value(d)));
    }

    private void build() {
        long[] mobs = {10, 100, 500, 2000, 10000};
        long[] mobGems = {1, 3, 6, 12, 30};
        for (int i = 0; i < mobs.length; i++) {
            stat("mobkills_" + mobs[i], Material.ZOMBIE_HEAD, Stat.MOB_KILLS, mobs[i], mobGems[i], mobs[i] * 5, "kampf");
        }
        long[] kills = {1, 10, 50, 250};
        long[] killGems = {1, 4, 10, 25};
        for (int i = 0; i < kills.length; i++) {
            stat("kills_" + kills[i], Material.IRON_SWORD, Stat.KILLS, kills[i], killGems[i], kills[i] * 20, "kampf");
        }
        stat("streak_5", Material.BLAZE_POWDER, Stat.STREAK, 5, 3, 500, "kampf");
        stat("streak_10", Material.BLAZE_POWDER, Stat.STREAK, 10, 8, 1500, "kampf");
        stat("streak_25", Material.BLAZE_POWDER, Stat.STREAK, 25, 20, 5000, "kampf");
        long[] hours = {1, 10, 50, 200, 500};
        long[] hourGems = {1, 5, 15, 40, 100};
        for (int i = 0; i < hours.length; i++) {
            stat("playtime_" + hours[i] + "h", Material.CLOCK, Stat.PLAYTIME, hours[i] * 3600, hourGems[i], hours[i] * 200, "treue");
        }
        long[] events = {1, 5, 25, 100};
        long[] eventGems = {2, 6, 20, 60};
        for (int i = 0; i < events.length; i++) {
            stat("events_" + events[i], Material.FIREWORK_ROCKET, Stat.EVENTS_WON, events[i], eventGems[i], events[i] * 300, "events");
        }
        stat("bossdamage_1000", Material.WITHER_SKELETON_SKULL, Stat.BOSS_DAMAGE, 1000, 3, 1000, "events");
        stat("bossdamage_10000", Material.WITHER_SKELETON_SKULL, Stat.BOSS_DAMAGE, 10000, 10, 5000, "events");
        stat("bossdamage_100000", Material.WITHER_SKELETON_SKULL, Stat.BOSS_DAMAGE, 100000, 40, 20000, "events");
        stat("relics_1", Material.ECHO_SHARD, Stat.RELICS, 1, 1, 200, "events");
        stat("relics_25", Material.ECHO_SHARD, Stat.RELICS, 25, 8, 2500, "events");
        long[] crates = {1, 10, 50, 250, 1000};
        long[] crateGems = {1, 3, 8, 20, 60};
        for (int i = 0; i < crates.length; i++) {
            stat("crates_" + crates[i], Material.CHEST, Stat.CRATES, crates[i], crateGems[i], crates[i] * 20, "sammler");
        }
        long[] coins = {10_000, 100_000, 1_000_000, 10_000_000};
        long[] coinGems = {2, 8, 25, 75};
        for (int i = 0; i < coins.length; i++) {
            stat("coins_" + coins[i], Material.GOLD_INGOT, Stat.COINS, coins[i], coinGems[i], 0, "sammler");
        }
        for (Job job : Job.values()) {
            for (int lvl : new int[]{10, 25, 50}) {
                all.add(new Achievement("job_" + job.id() + "_" + lvl, job.icon(), lvl, lvl == 10 ? 5 : lvl == 25 ? 15 : 50,
                        lvl * 200L, "berufe", (p, d) -> (long) plugin.jobs().level(p, job)));
            }
        }
        all.add(new Achievement("jobs_total_60", Material.EXPERIENCE_BOTTLE, 60, 10, 5000, "berufe",
                (p, d) -> (long) plugin.jobs().totalLevels(p)));
        all.add(new Achievement("jobs_total_150", Material.EXPERIENCE_BOTTLE, 150, 30, 20000, "berufe",
                (p, d) -> (long) plugin.jobs().totalLevels(p)));
        all.add(new Achievement("daily_7", Material.SUNFLOWER, 7, 3, 500, "treue", (p, d) -> (long) d.dailyStreak()));
        all.add(new Achievement("daily_30", Material.SUNFLOWER, 30, 10, 3000, "treue", (p, d) -> (long) d.dailyStreak()));
        all.add(new Achievement("parkour_1", Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 1, 3, 500, "treue",
                (p, d) -> plugin.progress().get(p, "parkour.finishes")));
        all.add(new Achievement("parkour_25", Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 25, 15, 5000, "treue",
                (p, d) -> plugin.progress().get(p, "parkour.finishes")));
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("milestones");
        playtimeHours = cfg.integer("playtime.every-hours", 5, 1, 1000);
        playtimeGems = cfg.longValue("playtime.gems", 5, 0, 100_000);
        playtimeCoins = cfg.longValue("playtime.coins", 500, 0, 100_000_000);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20 * 30, 20 * 30, "achievements", () -> plugin.getServer().getOnlinePlayers().forEach(this::check));
    }

    public List<Achievement> all() {
        return all;
    }

    public boolean unlocked(Player p, Achievement a) {
        return plugin.progress().has(p, "ach." + a.id());
    }

    public long value(Player p, Achievement a) {
        PlayerData data = plugin.playerData().get(p);
        return data == null ? 0 : a.value().apply(p, data);
    }

    public int unlockedCount(Player p) {
        int n = 0;
        for (Achievement a : all) {
            if (unlocked(p, a)) {
                n++;
            }
        }
        return n;
    }

    /** Unlocks everything that is reached and pays playtime milestones. */
    public void check(Player p) {
        PlayerData data = plugin.playerData().get(p);
        if (data == null || !plugin.progress().loaded(p)) {
            return;
        }
        for (Achievement a : all) {
            if (!unlocked(p, a) && a.value().apply(p, data) >= a.target()) {
                unlock(p, data, a);
            }
        }
        long reached = ProgressMath.milestones(data.livePlaytime(System.currentTimeMillis()), playtimeHours);
        long paid = plugin.progress().get(p, "milestone.playtime");
        if (reached > paid) {
            plugin.progress().set(p, "milestone.playtime", reached);
            long gems = plugin.economy().earn(data, Currency.GEMS, playtimeGems * (reached - paid), false);
            long coins = plugin.economy().earn(data, Currency.COINS, playtimeCoins * (reached - paid), false);
            plugin.playerData().save(data);
            plugin.messages().send(p, "milestones.playtime", "hours", reached * playtimeHours, "gems", gems, "coins", coins);
            SoundSpec.of("entity.player.levelup", 0.7f, 1.4f).play(p);
        }
    }

    private void unlock(Player p, PlayerData data, Achievement a) {
        plugin.progress().set(p, "ach." + a.id(), System.currentTimeMillis() / 1000);
        long gems = a.gems() > 0 ? plugin.economy().earn(data, Currency.GEMS, a.gems(), false) : 0;
        long coins = a.coins() > 0 ? plugin.economy().earn(data, Currency.COINS, a.coins(), false) : 0;
        plugin.playerData().save(data);
        p.showTitle(plugin.messages().in(p, () -> Title.title(plugin.messages().get("achievements.title"), plugin.messages().get("achievements.names." + a.id()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500)))));
        plugin.messages().send(p, "achievements.unlocked", "name", plugin.messages().get("achievements.names." + a.id()),
                "gems", gems, "coins", coins);
        SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.1f).play(p);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.2);
        if (a.gems() >= 20) {
            plugin.messages().broadcast("achievements.broadcast", "player", p.getName(),
                    "name", plugin.messages().get("achievements.names." + a.id()));
        }
    }
}
