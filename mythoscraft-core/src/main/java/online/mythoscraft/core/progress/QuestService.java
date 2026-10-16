package online.mythoscraft.core.progress;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.market.MarketMath;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quests: 3 daily and 3 weekly tasks per player (chosen per player and day/week) paying gems, plus a bonus when all
 * are done; and the starter quest line for new players (startup capital in coins – only while the playtime is low,
 * at most a few steps per day).
 */
public final class QuestService {

    public enum Type {
        ORE(Material.IRON_ORE), LOGS(Material.OAK_LOG), MOBS(Material.ZOMBIE_HEAD), FISH(Material.COD), HARVEST(Material.WHEAT),
        PLACE(Material.BRICKS), PLAY(Material.CLOCK), CRATES(Material.CHEST), ACTIVITY(Material.ELYTRA), BREED(Material.WHEAT_SEEDS);

        final Material icon;

        Type(Material icon) {
            this.icon = icon;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    record Template(Type type, int dailyAmount, int weeklyAmount) {
    }

    public record Quest(Type type, int amount, long gems) {
    }

    record Step(Type type, int amount, long coins) {
    }

    private static final List<Template> POOL = List.of(
            new Template(Type.ORE, 40, 300), new Template(Type.LOGS, 64, 500), new Template(Type.MOBS, 30, 250),
            new Template(Type.FISH, 12, 80), new Template(Type.HARVEST, 80, 600), new Template(Type.PLACE, 150, 1500),
            new Template(Type.PLAY, 45, 300), new Template(Type.CRATES, 3, 20), new Template(Type.ACTIVITY, 2, 10),
            new Template(Type.BREED, 10, 60));

    private static final List<Step> STARTER = List.of(
            new Step(Type.PLAY, 10, 1000), new Step(Type.ORE, 30, 1500), new Step(Type.LOGS, 48, 1500),
            new Step(Type.MOBS, 20, 2000), new Step(Type.ACTIVITY, 1, 2000), new Step(Type.HARVEST, 50, 2000),
            new Step(Type.FISH, 8, 2500), new Step(Type.CRATES, 1, 2500), new Step(Type.PLAY, 90, 3000),
            new Step(Type.PLACE, 200, 4000));

    private final MythosCraft plugin;
    private long dailyGems = 3;
    private long dailyBonus = 3;
    private long weeklyGems = 10;
    private long weeklyBonus = 10;
    private int starterMaxHours = 30;
    private int starterStepsPerDay = 3;

    public QuestService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("quests");
        dailyGems = cfg.longValue("daily-gems", 3, 0, 1000);
        dailyBonus = cfg.longValue("daily-bonus-gems", 3, 0, 1000);
        weeklyGems = cfg.longValue("weekly-gems", 10, 0, 10_000);
        weeklyBonus = cfg.longValue("weekly-bonus-gems", 10, 0, 10_000);
        starterMaxHours = cfg.integer("starter.max-playtime-hours", 30, 0, 100_000);
        starterStepsPerDay = cfg.integer("starter.steps-per-day", 3, 1, 100);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 1200, 1200, "quest playtime", () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                count(p, Type.PLAY, 1);
            }
        });
    }

    // ---- selection ---------------------------------------------------------------------------------------------

    private long day() {
        return plugin.multipliers().today().toEpochDay();
    }

    private long week() {
        LocalDate d = plugin.multipliers().today();
        return d.get(IsoFields.WEEK_BASED_YEAR) * 100L + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** The 3 quests of a player for today (daily) or this week (weekly) – the same for the whole period. */
    public List<Quest> quests(Player p, boolean weekly) {
        long period = weekly ? week() : day();
        Random rnd = new Random(p.getUniqueId().getMostSignificantBits() ^ (period * 0x9E3779B97F4A7C15L) ^ (weekly ? 77 : 13));
        List<Template> pool = new ArrayList<>(POOL);
        java.util.Collections.shuffle(pool, rnd);
        List<Quest> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Template t = pool.get(i);
            double scale = 0.8 + rnd.nextDouble() * 0.5;
            int amount = (int) Math.max(1, Math.round((weekly ? t.weeklyAmount() : t.dailyAmount()) * scale));
            out.add(new Quest(t.type(), amount, weekly ? weeklyGems : dailyGems));
        }
        return out;
    }

    private String key(boolean weekly, int index) {
        return "quest." + (weekly ? "w." + week() : "d." + day()) + "." + index;
    }

    public long progress(Player p, boolean weekly, int index) {
        return plugin.progress().get(p, key(weekly, index));
    }

    private boolean claimed(Player p, boolean weekly, int index) {
        return plugin.progress().has(p, key(weekly, index) + ".done");
    }

    // ---- counting ----------------------------------------------------------------------------------------------

    /** Something quest-relevant happened (called from jobs, crates, spawn activities, playtime). */
    public void count(Player p, Type type, int amount) {
        if (!plugin.progress().loaded(p) || amount <= 0) {
            return;
        }
        for (boolean weekly : new boolean[]{false, true}) {
            List<Quest> list = quests(p, weekly);
            for (int i = 0; i < list.size(); i++) {
                Quest q = list.get(i);
                if (q.type() != type || claimed(p, weekly, i)) {
                    continue;
                }
                long now = plugin.progress().add(p, key(weekly, i), amount);
                if (now >= q.amount()) {
                    complete(p, weekly, i, q);
                }
            }
        }
        starter(p, type, amount);
    }

    private void complete(Player p, boolean weekly, int index, Quest q) {
        plugin.progress().set(p, key(weekly, index) + ".done", 1);
        PlayerData data = plugin.playerData().get(p);
        if (data == null) {
            return;
        }
        long gems = plugin.economy().earn(data, Currency.GEMS, q.gems(), false);
        plugin.messages().send(p, weekly ? "quests.weekly-done" : "quests.daily-done", "quest", describe(p, q), "gems", gems);
        plugin.clanWar().add(p.getUniqueId(), weekly ? online.mythoscraft.core.clan.ClanWarService.Source.WEEKLY_QUEST
                : online.mythoscraft.core.clan.ClanWarService.Source.DAILY_QUEST);
        SoundSpec.of("ui.toast.challenge_complete", 0.7f, 1.4f).play(p);
        boolean all = true;
        for (int i = 0; i < 3; i++) {
            all &= claimed(p, weekly, i);
        }
        if (all) {
            long bonus = plugin.economy().earn(data, Currency.GEMS, weekly ? weeklyBonus : dailyBonus, false);
            plugin.messages().send(p, weekly ? "quests.weekly-all" : "quests.daily-all", "gems", bonus);
        }
        plugin.playerData().save(data);
    }

    public net.kyori.adventure.text.Component describe(Player p, Quest q) {
        return plugin.messages().in(p, () -> plugin.messages().get("quests.types." + q.type().id(), "amount", q.amount()));
    }

    // ---- starter quest line ------------------------------------------------------------------------------

    /** Is the starter quest line open for this player (low playtime, not finished)? */
    public boolean starterOpen(Player p) {
        PlayerData data = plugin.playerData().get(p);
        if (data == null || plugin.progress().get(p, "starter.step") >= STARTER.size()) {
            return false;
        }
        return data.livePlaytime(System.currentTimeMillis()) < starterMaxHours * 3600L;
    }

    private int stepsToday(Player p) {
        return plugin.progress().get(p, "starter.day") == day() ? (int) plugin.progress().get(p, "starter.today") : 0;
    }

    private void starter(Player p, Type type, int amount) {
        if (!starterOpen(p)) {
            return;
        }
        int step = (int) plugin.progress().get(p, "starter.step");
        Step s = STARTER.get(step);
        if (s.type() != type) {
            return;
        }
        if (stepsToday(p) >= starterStepsPerDay) {
            return; // not too fast: the next step counts again tomorrow
        }
        long now = plugin.progress().add(p, "starter.progress", amount);
        if (now < s.amount()) {
            return;
        }
        PlayerData data = plugin.playerData().get(p);
        if (data == null) {
            return;
        }
        plugin.progress().set(p, "starter.step", step + 1);
        plugin.progress().set(p, "starter.progress", 0);
        if (plugin.progress().get(p, "starter.day") != day()) {
            plugin.progress().set(p, "starter.day", day());
            plugin.progress().set(p, "starter.today", 0);
        }
        plugin.progress().add(p, "starter.today", 1);
        long coins = plugin.economy().earn(data, Currency.COINS, s.coins(), false);
        plugin.playerData().save(data);
        plugin.messages().send(p, "quests.starter-done", "step", step + 1, "steps", STARTER.size(), "coins", coins);
        SoundSpec.of("entity.player.levelup", 0.8f, 1.3f).play(p);
        if (step + 1 >= STARTER.size()) {
            plugin.messages().send(p, "quests.starter-finished");
        } else if (stepsToday(p) >= starterStepsPerDay) {
            plugin.messages().send(p, "quests.starter-limit", "max", starterStepsPerDay);
        }
    }

    // ---- menu -------------------------------------------------------------------------------------------------

    public void open(Player p) {
        new QuestMenu(p).open(p);
    }

    private final class QuestMenu extends Menu {

        QuestMenu(Player viewer) {
            super(5, plugin.messages().get("quests.title"));
            fill(viewer, false, 10);
            fill(viewer, true, 19);
            if (starterOpen(viewer)) {
                int step = (int) plugin.progress().get(viewer, "starter.step");
                Step s = STARTER.get(step);
                long prog = plugin.progress().get(viewer, "starter.progress");
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(plugin.messages().getList("quests.starter-lore",
                        "step", step + 1, "steps", STARTER.size(),
                        "quest", plugin.messages().get("quests.types." + s.type().id(), "amount", s.amount()),
                        "bar", MarketMath.bar(Math.min(prog, s.amount()), s.amount(), 12), "progress", Math.min(prog, s.amount()),
                        "amount", s.amount(), "coins", s.coins(), "today", stepsToday(viewer), "max", starterStepsPerDay));
                getInventory().setItem(31, MenuItems.button(Material.EMERALD, plugin.messages().get("quests.starter"), lore));
            } else {
                getInventory().setItem(31, MenuItems.button(Material.GRAY_DYE, plugin.messages().get("quests.starter-closed"), List.of()));
            }
            getInventory().setItem(9, MenuItems.button(Material.SUNFLOWER, plugin.messages().get("quests.daily"),
                    plugin.messages().getList("quests.daily-lore", "gems", dailyGems, "bonus", dailyBonus)));
            getInventory().setItem(18, MenuItems.button(Material.NETHER_STAR, plugin.messages().get("quests.weekly"),
                    plugin.messages().getList("quests.weekly-lore", "gems", weeklyGems, "bonus", weeklyBonus)));
        }

        private void fill(Player viewer, boolean weekly, int firstSlot) {
            List<Quest> list = quests(viewer, weekly);
            for (int i = 0; i < list.size(); i++) {
                Quest q = list.get(i);
                boolean done = claimed(viewer, weekly, i);
                long prog = Math.min(q.amount(), progress(viewer, weekly, i));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(plugin.messages().getList("quests.lore",
                        "bar", MarketMath.bar(done ? q.amount() : prog, q.amount(), 12), "progress", done ? q.amount() : prog,
                        "amount", q.amount(), "gems", q.gems()));
                lore.add(plugin.messages().get(done ? "quests.done" : "quests.open"));
                getInventory().setItem(firstSlot + 2 + i * 2, MenuItems.button(done ? Material.LIME_DYE : q.type().icon,
                        plugin.messages().get("quests.types." + q.type().id(), "amount", q.amount()), lore));
            }
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            // read-only
        }
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
    }
}
