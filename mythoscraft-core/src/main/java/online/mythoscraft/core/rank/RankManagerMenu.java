package online.mythoscraft.core.rank;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.Messages;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rank manager (/rank without arguments): pick an online player, then a rank, then a duration. The actual change goes
 * through "/rank set …", so every rule (MYTHOS and staff ranks only by MinePro641) still applies.
 */
public final class RankManagerMenu extends Menu {

    private final MythosCraft plugin;
    private final Map<Integer, Player> players = new HashMap<>();

    public RankManagerMenu(MythosCraft plugin, Player viewer) {
        super(6, plugin.messages().get("rankmanager.title"));
        this.plugin = plugin;
        Messages m = plugin.messages();
        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            Rank rank = plugin.ranks().rankOf(p);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(p);
                meta.displayName(Text.noItalic(Component.text(p.getName())));
                meta.lore(m.getList("rankmanager.player-lore", "rank", Text.parse(rank.display())).stream().map(Text::noItalic).toList());
            });
            players.put(slot, p);
            getInventory().setItem(slot++, head);
        }
        MenuItems.bottomBar(this);
        getInventory().setItem(49, MenuItems.button(Material.BOOK, m.get("rankmanager.help"), m.getList("rankmanager.help-lore")));
    }

    @Override
    public void onClick(Player viewer, InventoryClickEvent event) {
        Player target = players.get(event.getRawSlot());
        if (target != null) {
            new RankSelect(plugin, viewer, target.getName()).open(viewer);
        }
    }

    /** Step 2: choose the rank. */
    static final class RankSelect extends Menu {

        private final MythosCraft plugin;
        private final String target;
        private final Map<Integer, Rank> ranks = new HashMap<>();

        RankSelect(MythosCraft plugin, Player viewer, String target) {
            super(4, plugin.messages().get("rankmanager.select-title", "player", target));
            this.plugin = plugin;
            this.target = target;
            Messages m = plugin.messages();
            int slot = 0;
            for (Rank r : plugin.ranks().ranks()) {
                if (r.restricted() && !MythosOwner.is(viewer)) {
                    continue;
                }
                Material icon = r.isMythos() ? Material.NETHER_STAR : r.ownerOnly() ? Material.REDSTONE_BLOCK
                        : r == plugin.ranks().defaultRank() ? Material.GRAY_DYE : Material.NAME_TAG;
                List<Component> lore = new ArrayList<>(m.getList("rankmanager.rank-lore", "weight", r.weight(), "homes", r.homes(),
                        "multiplier", r.coinMultiplier()));
                ranks.put(slot, r);
                getInventory().setItem(slot++, MenuItems.button(icon, Text.parse(r.display()), lore));
            }
            getInventory().setItem(31, MenuItems.button(Material.ARROW, m.get("rankmanager.back"), List.of()));
        }

        @Override
        public void onClick(Player viewer, InventoryClickEvent event) {
            if (event.getRawSlot() == 31) {
                new RankManagerMenu(plugin, viewer).open(viewer);
                return;
            }
            Rank r = ranks.get(event.getRawSlot());
            if (r != null) {
                new DurationSelect(plugin, target, r).open(viewer);
            }
        }
    }

    /** Step 3: choose how long. */
    static final class DurationSelect extends Menu {

        private static final String[] DURATIONS = {"1d", "7d", "30d", "90d", "perm"};
        private final MythosCraft plugin;
        private final String target;
        private final Rank rank;

        DurationSelect(MythosCraft plugin, String target, Rank rank) {
            super(3, plugin.messages().get("rankmanager.duration-title", "player", target));
            this.plugin = plugin;
            this.target = target;
            this.rank = rank;
            Messages m = plugin.messages();
            for (int i = 0; i < DURATIONS.length; i++) {
                Material icon = DURATIONS[i].equals("perm") ? Material.DIAMOND : Material.CLOCK;
                getInventory().setItem(11 + i, MenuItems.button(icon, m.get("rankmanager.duration." + DURATIONS[i]),
                        List.of(m.get("rankmanager.duration-lore", "rank", Text.parse(rank.display()), "player", target))));
            }
            getInventory().setItem(22, MenuItems.button(Material.ARROW, m.get("rankmanager.back"), List.of()));
        }

        @Override
        public void onClick(Player viewer, InventoryClickEvent event) {
            int slot = event.getRawSlot();
            if (slot == 22) {
                new RankSelect(plugin, viewer, target).open(viewer);
                return;
            }
            if (slot >= 11 && slot < 11 + DURATIONS.length) {
                viewer.closeInventory();
                String dur = DURATIONS[slot - 11];
                if (rank == plugin.ranks().defaultRank()) {
                    viewer.performCommand("rank remove " + target);
                } else {
                    viewer.performCommand("rank set " + target + " " + rank.id() + " " + dur);
                }
            }
        }
    }
}
