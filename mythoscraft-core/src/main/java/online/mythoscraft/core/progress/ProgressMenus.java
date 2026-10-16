package online.mythoscraft.core.progress;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.Messages;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.market.MarketMath;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menus for /jobs and /erfolge. */
public final class ProgressMenus {

    private ProgressMenus() {
    }

    /** Overview of all jobs with level, XP bar and how to earn XP. */
    public static final class JobsMenu extends Menu {

        private final MythosCraft plugin;

        public JobsMenu(MythosCraft plugin, Player viewer) {
            super(4, plugin.messages().get("jobs.title"));
            this.plugin = plugin;
            Messages m = plugin.messages();
            for (int i = 0; i < 36; i++) {
                getInventory().setItem(i, MenuItems.filler());
            }
            int[] slots = {10, 12, 14, 16, 20, 24};
            Job[] jobs = Job.values();
            for (int i = 0; i < jobs.length; i++) {
                Job job = jobs[i];
                int level = plugin.jobs().level(viewer, job);
                long xp = plugin.jobs().xp(viewer, job);
                long next = ProgressMath.xpForNextLevel(level);
                boolean max = level >= ProgressMath.MAX_JOB_LEVEL;
                List<Component> lore = new ArrayList<>();
                lore.add(m.get("jobs.lore.level", "level", level, "max", ProgressMath.MAX_JOB_LEVEL));
                lore.add(max ? m.get("jobs.lore.maxed") : m.get("jobs.lore.xp", "bar", MarketMath.bar(xp, next, 12), "xp", xp, "next", next));
                lore.add(m.get("jobs.lore.bonus", "bonus", Math.round((ProgressMath.coinMultiplier(level) - 1) * 100)));
                if (!max) {
                    lore.add(m.get("jobs.lore.reward", "gems", ProgressMath.gemsForLevel(level + 1)));
                }
                lore.add(Component.empty());
                lore.addAll(m.getList("jobs.how." + job.id()));
                ItemStack icon = MenuItems.button(job.icon(), m.get("jobs.lore.name", "job", plugin.jobs().name(job), "level", level), lore);
                icon.editMeta(meta -> meta.addItemFlags(ItemFlag.values()));
                icon.setAmount(Math.max(1, Math.min(64, level)));
                getInventory().setItem(slots[i], icon);
            }
            getInventory().setItem(31, MenuItems.button(Material.BOOK, m.get("jobs.info"), m.getList("jobs.info-lore",
                    "total", plugin.jobs().totalLevels(viewer))));
            getInventory().setItem(27, MenuItems.button(Material.NETHER_STAR, m.get("achievements.button"), List.of()));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (event.getRawSlot() == 27) {
                new AchievementsMenu(plugin, player, 0).open(player);
            }
        }
    }

    /** All achievements with progress, paged. */
    public static final class AchievementsMenu extends Menu {

        private final MythosCraft plugin;
        private final int page;

        public AchievementsMenu(MythosCraft plugin, Player viewer, int page) {
            super(6, plugin.messages().get("achievements.menu-title", "done", plugin.achievements().unlockedCount(viewer),
                    "total", plugin.achievements().all().size()));
            this.plugin = plugin;
            Messages m = plugin.messages();
            List<AchievementService.Achievement> list = plugin.achievements().all();
            int pages = Math.max(1, (list.size() + 44) / 45);
            this.page = Math.max(0, Math.min(page, pages - 1));
            for (int i = this.page * 45; i < Math.min(list.size(), this.page * 45 + 45); i++) {
                AchievementService.Achievement a = list.get(i);
                boolean done = plugin.achievements().unlocked(viewer, a);
                long value = Math.min(a.target(), plugin.achievements().value(viewer, a));
                List<Component> lore = new ArrayList<>(m.getList("achievements.lore", "desc", m.get("achievements.desc." + a.id()),
                        "bar", MarketMath.bar(value, a.target(), 10), "value", value, "target", a.target(), "gems", a.gems(), "coins", a.coins()));
                lore.add(m.get(done ? "achievements.done" : "achievements.open"));
                ItemStack icon = MenuItems.button(done ? a.icon() : Material.GRAY_DYE, m.get("achievements.names." + a.id()), lore);
                icon.editMeta(meta -> {
                    meta.addItemFlags(ItemFlag.values());
                    if (done) {
                        meta.setEnchantmentGlintOverride(true);
                    }
                });
                getInventory().setItem(i - this.page * 45, icon);
            }
            MenuItems.bottomBar(this);
            if (this.page > 0) {
                getInventory().setItem(45, MenuItems.button(Material.ARROW, m.get("gui.previous"), List.of()));
            }
            if (this.page < pages - 1) {
                getInventory().setItem(53, MenuItems.button(Material.ARROW, m.get("gui.next"), List.of()));
            }
            getInventory().setItem(49, MenuItems.button(Material.DIAMOND_PICKAXE, m.get("jobs.button"), List.of()));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            switch (event.getRawSlot()) {
                case 45 -> new AchievementsMenu(plugin, player, page - 1).open(player);
                case 53 -> new AchievementsMenu(plugin, player, page + 1).open(player);
                case 49 -> new JobsMenu(plugin, player).open(player);
                default -> {
                }
            }
        }
    }
}
