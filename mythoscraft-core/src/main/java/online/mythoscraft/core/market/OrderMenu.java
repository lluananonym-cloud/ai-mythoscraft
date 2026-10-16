package online.mythoscraft.core.market;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.Messages;
import online.mythoscraft.core.gui.ConfirmMenu;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Farm orders: all open orders (deliver by clicking) or the player's own orders (collect / cancel). */
public final class OrderMenu extends Menu {

    public enum Mode { BROWSE, MINE }

    private static final int PAGE_SIZE = 45;

    private final MythosCraft plugin;
    private final OrderManager orders;
    private final Mode mode;
    private final int page;
    private final Map<Integer, FarmOrder> slots = new HashMap<>();

    public OrderMenu(MythosCraft plugin, Player viewer, Mode mode, int page) {
        super(6, plugin.messages().get("orders.title." + mode.name().toLowerCase(), "page", page + 1));
        this.plugin = plugin;
        this.orders = plugin.orders();
        this.mode = mode;
        Messages m = plugin.messages();
        List<FarmOrder> list = mode == Mode.BROWSE ? orders.open() : orders.of(viewer.getUniqueId());
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        long now = System.currentTimeMillis();
        int start = this.page * PAGE_SIZE;
        for (int i = start; i < Math.min(list.size(), start + PAGE_SIZE); i++) {
            FarmOrder o = list.get(i);
            slots.put(i - start, o);
            getInventory().setItem(i - start, icon(viewer, o, now));
        }
        MenuItems.bottomBar(this);
        if (this.page > 0) {
            getInventory().setItem(45, MenuItems.button(Material.ARROW, m.get("gui.previous"), List.of()));
        }
        if (this.page < pages - 1) {
            getInventory().setItem(53, MenuItems.button(Material.ARROW, m.get("gui.next"), List.of()));
        }
        if (mode == Mode.BROWSE) {
            long mine = orders.of(viewer.getUniqueId()).size();
            getInventory().setItem(48, MenuItems.button(Material.CHEST, m.get("orders.button.mine"),
                    m.getList("orders.button.mine-lore", "count", mine)));
        } else {
            getInventory().setItem(48, MenuItems.button(Material.SPYGLASS, m.get("orders.button.browse"), List.of()));
        }
        getInventory().setItem(49, MenuItems.button(Material.WRITABLE_BOOK, m.get("orders.button.info"),
                m.getList("orders.button.info-lore", "max", orders.maxAmount())));
        getInventory().setItem(50, MenuItems.button(Material.GOLD_INGOT, m.get("orders.button.auction"), m.getList("orders.button.auction-lore")));
    }

    private ItemStack icon(Player viewer, FarmOrder o, long now) {
        Messages m = plugin.messages();
        String left = Durations.format(Duration.ofMillis(Math.max(0, o.expires() - now)));
        String bar = MarketMath.bar(o.delivered(), o.amount(), 10);
        List<Component> lore = new ArrayList<>(m.getList("orders.lore.common", "owner", o.ownerName(), "bar", bar,
                "delivered", o.delivered(), "amount", o.amount(), "price", o.priceEach(), "remaining", o.remaining(),
                "value", o.remaining() * o.priceEach(), "time", left));
        if (mode == Mode.BROWSE) {
            if (o.owner().equals(viewer.getUniqueId())) {
                lore.addAll(m.getList("orders.lore.own"));
            } else {
                int has = OrderManager.count(viewer.getInventory(), o.material());
                int n = MarketMath.deliverable(has, o.amount(), o.delivered());
                lore.addAll(m.getList(n > 0 ? "orders.lore.deliver" : "orders.lore.deliver-none", "has", has, "n", n,
                        "pay", n * o.priceEach()));
            }
            if (viewer.hasPermission("mythos.orders.admin")) {
                lore.add(m.get("orders.lore.admin"));
            }
        } else {
            lore.addAll(m.getList("orders.lore.mine", "state", m.get("orders.state." + o.state().name().toLowerCase()),
                    "collectable", o.collectable(), "refund", o.escrow()));
        }
        ItemStack icon = new ItemBuilder(o.material()).build();
        int stack = Math.max(1, Math.min(64, mode == Mode.MINE ? Math.max(1, o.collectable()) : o.remaining()));
        icon.setAmount(Math.min(stack, o.material().getMaxStackSize()));
        return MenuItems.withLore(icon, lore);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        FarmOrder order = slots.get(slot);
        if (order != null) {
            handleOrder(player, order, event);
            return;
        }
        switch (slot) {
            case 45 -> new OrderMenu(plugin, player, mode, page - 1).open(player);
            case 53 -> new OrderMenu(plugin, player, mode, page + 1).open(player);
            case 48 -> new OrderMenu(plugin, player, mode == Mode.BROWSE ? Mode.MINE : Mode.BROWSE, 0).open(player);
            case 49 -> {
                player.closeInventory();
                plugin.messages().send(player, "orders.create-hint", "max", orders.maxAmount());
            }
            case 50 -> new AuctionMenu(plugin, player, AuctionMenu.Mode.BROWSE, 0, AuctionManager.Sort.NEWEST, null).open(player);
            default -> {
            }
        }
    }

    private void handleOrder(Player player, FarmOrder order, InventoryClickEvent event) {
        if (mode == Mode.BROWSE) {
            if (event.isShiftClick() && event.isRightClick() && player.hasPermission("mythos.orders.admin")
                    && !order.owner().equals(player.getUniqueId())) {
                orders.cancel(player, order, true);
            } else if (order.owner().equals(player.getUniqueId())) {
                new OrderMenu(plugin, player, Mode.MINE, 0).open(player);
                return;
            } else {
                orders.deliver(player, order);
            }
            new OrderMenu(plugin, player, Mode.BROWSE, page).open(player);
            return;
        }
        if (event.isShiftClick() && event.isRightClick() && order.state() == FarmOrder.State.OPEN) {
            Messages m = plugin.messages();
            new ConfirmMenu(m.get("orders.title.cancel"), new ItemStack(order.material()), m.get("orders.cancel-yes"),
                    m.getList("orders.cancel-yes-lore", "refund", order.escrow()), m.get("orders.cancel-no"),
                    p -> {
                        orders.cancel(p, order, false);
                        new OrderMenu(plugin, p, Mode.MINE, page).open(p);
                    },
                    p -> new OrderMenu(plugin, p, Mode.MINE, page).open(p)).open(player);
            return;
        }
        if (order.collectable() > 0) {
            orders.collect(player, order);
        } else {
            plugin.messages().send(player, "orders.nothing-to-collect");
        }
        new OrderMenu(plugin, player, Mode.MINE, page).open(player);
    }
}
