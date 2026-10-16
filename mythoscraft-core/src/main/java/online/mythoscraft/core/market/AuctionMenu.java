package online.mythoscraft.core.market;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.Messages;
import online.mythoscraft.core.gui.ConfirmMenu;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Durations;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Auction house: browse all offers, own offers or the pickup list (expired/cancelled items). */
public final class AuctionMenu extends Menu {

    public enum Mode { BROWSE, MINE, PICKUP }

    private static final int PAGE_SIZE = 45;

    private final MythosCraft plugin;
    private final AuctionManager auctions;
    private final Mode mode;
    private final int page;
    private final AuctionManager.Sort sort;
    private final String filter;
    private final Map<Integer, AuctionListing> slots = new HashMap<>();
    private final int pages;

    public AuctionMenu(MythosCraft plugin, Player viewer, Mode mode, int page, AuctionManager.Sort sort, String filter) {
        super(6, plugin.messages().get("auction.title." + mode.name().toLowerCase(), "page", page + 1));
        this.plugin = plugin;
        this.auctions = plugin.auctions();
        this.mode = mode;
        this.sort = sort;
        this.filter = filter;
        Messages m = plugin.messages();
        List<AuctionListing> list = switch (mode) {
            case BROWSE -> auctions.active(sort, filter);
            case MINE -> auctions.activeOf(viewer.getUniqueId());
            case PICKUP -> auctions.claimable(viewer.getUniqueId());
        };
        this.pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        long now = System.currentTimeMillis();
        int start = this.page * PAGE_SIZE;
        for (int i = start; i < Math.min(list.size(), start + PAGE_SIZE); i++) {
            AuctionListing l = list.get(i);
            int slot = i - start;
            slots.put(slot, l);
            String left = Durations.format(Duration.ofMillis(Math.max(0, l.expires() - now)));
            List<Component> lore = switch (mode) {
                case BROWSE -> m.getList(l.seller().equals(viewer.getUniqueId()) ? "auction.lore.own" : "auction.lore.browse",
                        "seller", l.sellerName(), "price", l.price(), "currency", auctions.currencyName(l.currency()), "time", left);
                case MINE -> m.getList("auction.lore.mine", "price", l.price(), "currency", auctions.currencyName(l.currency()),
                        "time", left);
                case PICKUP -> m.getList("auction.lore.pickup", "reason", m.get("auction.reason." + l.state().name().toLowerCase()));
            };
            if (mode == Mode.BROWSE && viewer.hasPermission("mythos.auction.admin")) {
                lore = new ArrayList<>(lore);
                lore.add(m.get("auction.lore.admin"));
            }
            getInventory().setItem(slot, MenuItems.withLore(l.item(), lore));
        }
        MenuItems.bottomBar(this);
        if (this.page > 0) {
            getInventory().setItem(45, MenuItems.button(Material.ARROW, m.get("gui.previous"), List.of()));
        }
        if (this.page < pages - 1) {
            getInventory().setItem(53, MenuItems.button(Material.ARROW, m.get("gui.next"), List.of()));
        }
        if (mode == Mode.BROWSE) {
            getInventory().setItem(46, MenuItems.button(Material.HOPPER, m.get("auction.button.sort"),
                    m.getList("auction.button.sort-lore", "sort", m.get("auction.sort." + sort.name().toLowerCase()))));
            getInventory().setItem(47, MenuItems.button(Material.OAK_SIGN, m.get("auction.button.search"),
                    m.getList(filter == null || filter.isBlank() ? "auction.button.search-lore" : "auction.button.search-active-lore",
                            "filter", filter == null ? "" : filter)));
        } else {
            getInventory().setItem(46, MenuItems.button(Material.SPYGLASS, m.get("auction.button.browse"), List.of()));
        }
        getInventory().setItem(48, MenuItems.button(Material.CHEST, m.get("auction.button.mine"),
                m.getList("auction.button.mine-lore", "count", auctions.activeOf(viewer.getUniqueId()).size(),
                        "max", auctions.maxListings(viewer) == Integer.MAX_VALUE ? "∞" : String.valueOf(auctions.maxListings(viewer)))));
        PlayerData data = plugin.playerData().get(viewer);
        getInventory().setItem(49, MenuItems.button(Material.SUNFLOWER, m.get("auction.button.info"),
                m.getList("auction.button.info-lore", "coins", data == null ? 0 : data.coins(), "gems", data == null ? 0 : data.gems(),
                        "tax", auctions.taxPercent())));
        int pickup = auctions.claimable(viewer.getUniqueId()).size();
        getInventory().setItem(50, MenuItems.button(pickup > 0 ? Material.ENDER_CHEST : Material.BARREL, m.get("auction.button.pickup"),
                m.getList("auction.button.pickup-lore", "count", pickup)));
        getInventory().setItem(51, MenuItems.button(Material.WHEAT, m.get("auction.button.orders"), m.getList("auction.button.orders-lore")));
    }

    private void reopen(Player player, Mode newMode, int newPage, AuctionManager.Sort newSort, String newFilter) {
        new AuctionMenu(plugin, player, newMode, newPage, newSort, newFilter).open(player);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        AuctionListing listing = slots.get(slot);
        if (listing != null) {
            handleListing(player, listing, event);
            return;
        }
        switch (slot) {
            case 45 -> reopen(player, mode, page - 1, sort, filter);
            case 53 -> reopen(player, mode, page + 1, sort, filter);
            case 46 -> {
                if (mode == Mode.BROWSE) {
                    AuctionManager.Sort[] all = AuctionManager.Sort.values();
                    reopen(player, mode, 0, all[(sort.ordinal() + 1) % all.length], filter);
                } else {
                    reopen(player, Mode.BROWSE, 0, sort, null);
                }
            }
            case 47 -> {
                if (mode == Mode.BROWSE && filter != null && !filter.isBlank()) {
                    reopen(player, mode, 0, sort, null);
                } else if (mode == Mode.BROWSE) {
                    player.closeInventory();
                    plugin.messages().send(player, "auction.search-hint");
                }
            }
            case 48 -> reopen(player, Mode.MINE, 0, sort, filter);
            case 50 -> reopen(player, Mode.PICKUP, 0, sort, filter);
            case 51 -> new OrderMenu(plugin, player, OrderMenu.Mode.BROWSE, 0).open(player);
            default -> {
            }
        }
    }

    private void handleListing(Player player, AuctionListing listing, InventoryClickEvent event) {
        switch (mode) {
            case BROWSE -> {
                if (event.isShiftClick() && event.isRightClick() && player.hasPermission("mythos.auction.admin")) {
                    auctions.cancel(player, listing, true);
                    reopen(player, mode, page, sort, filter);
                    return;
                }
                if (listing.seller().equals(player.getUniqueId())) {
                    plugin.messages().send(player, "auction.own");
                    return;
                }
                Messages m = plugin.messages();
                Component price = m.get("auction.confirm-price", "price", listing.price(), "currency", auctions.currencyName(listing.currency()));
                new ConfirmMenu(m.get("auction.title.confirm"), listing.item().clone(), m.get("auction.confirm-yes"),
                        List.of(price, m.get("auction.confirm-seller", "seller", listing.sellerName())), m.get("auction.confirm-no"),
                        p -> {
                            auctions.buy(p, listing);
                            reopen(p, Mode.BROWSE, page, sort, filter);
                        },
                        p -> reopen(p, Mode.BROWSE, page, sort, filter)).open(player);
            }
            case MINE -> {
                Messages m = plugin.messages();
                new ConfirmMenu(m.get("auction.title.cancel"), listing.item().clone(), m.get("auction.cancel-yes"), List.of(),
                        m.get("auction.confirm-no"),
                        p -> {
                            auctions.cancel(p, listing, false);
                            reopen(p, Mode.MINE, page, sort, filter);
                        },
                        p -> reopen(p, Mode.MINE, page, sort, filter)).open(player);
            }
            case PICKUP -> {
                auctions.claim(player, listing);
                reopen(player, Mode.PICKUP, page, sort, filter);
            }
        }
    }
}
