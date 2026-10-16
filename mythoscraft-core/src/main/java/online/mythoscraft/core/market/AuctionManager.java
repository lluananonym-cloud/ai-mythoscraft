package online.mythoscraft.core.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Auction house. All listings live in memory and are only changed on the main thread (that makes every buy
 * atomic – an item can never be sold twice); each change is written to the database right away.
 */
public final class AuctionManager {

    public enum Sort { NEWEST, CHEAPEST, EXPENSIVE, ENDING }

    private final MythosCraft plugin;
    private final Map<String, AuctionListing> listings = new LinkedHashMap<>();
    private MarketRepository repo;
    private boolean loaded;
    private boolean loading;

    private boolean enabled = true;
    private long durationMillis = 48 * 3_600_000L;
    private int maxListings = 5;
    private final Map<String, Integer> maxByRank = new HashMap<>();
    private double taxPercent = 5;
    private long minPrice = 1;
    private long maxPrice = 1_000_000_000L;
    private boolean gemsAllowed = true;
    private boolean broadcast = true;
    private final Set<Material> blocked = EnumSet.noneOf(Material.class);

    public AuctionManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("auction");
        enabled = cfg.bool("enabled", true);
        durationMillis = cfg.integer("duration-hours", 48, 1, 24 * 30) * 3_600_000L;
        maxListings = cfg.integer("max-listings", 5, 1, 500);
        maxByRank.clear();
        ConfigReader ranks = cfg.sub("max-listings-by-rank");
        for (String rank : ranks.keys()) {
            maxByRank.put(rank.toUpperCase(Locale.ROOT), ranks.integer(rank, maxListings, 1, 500));
        }
        taxPercent = cfg.decimal("tax-percent", 5, 0, 100);
        minPrice = cfg.longValue("min-price", 1, 1, Long.MAX_VALUE / 4);
        maxPrice = cfg.longValue("max-price", 1_000_000_000L, minPrice, Long.MAX_VALUE / 4);
        gemsAllowed = cfg.bool("allow-gems", true);
        broadcast = cfg.bool("broadcast-new", true);
        blocked.clear();
        for (String name : cfg.strings("blocked-items")) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                cfg.warn("blocked-items", "unbekanntes Material '" + name + "'");
            } else {
                blocked.add(m);
            }
        }
        if (repo == null) {
            repo = new MarketRepository(plugin.database().dialect());
        }
        if (!loaded && !loading) {
            load();
        }
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20 * 30, 20 * 30, "auction expiry", this::expire);
    }

    private void load() {
        loading = true;
        plugin.database().query("load auctions", repo::openAuctions).whenComplete((rows, error) -> plugin.tasks().sync(() -> {
            loading = false;
            if (error != null) {
                plugin.getLogger().severe("Auction house could not be loaded – it stays closed until the next reload.");
                return;
            }
            listings.clear();
            for (MarketRepository.AuctionRow r : rows) {
                try {
                    ItemStack item = ItemStack.deserializeBytes(r.item());
                    Currency currency = Currency.byId(r.currency());
                    listings.put(r.id(), new AuctionListing(r.id(), UUID.fromString(r.seller()), r.sellerName(), item, r.price(),
                            currency == null ? Currency.COINS : currency, r.created(), r.expires(),
                            AuctionListing.State.valueOf(r.state()), r.buyer() == null ? null : UUID.fromString(r.buyer()), r.finished()));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Skipping unreadable auction " + r.id() + ": " + e.getMessage());
                }
            }
            loaded = true;
            plugin.getLogger().info("Auction house: " + listings.size() + " open listings loaded");
            expire();
        }));
    }

    public boolean ready() {
        return loaded && enabled;
    }

    public double taxPercent() {
        return taxPercent;
    }

    public boolean gemsAllowed() {
        return gemsAllowed;
    }

    // ---- queries ------------------------------------------------------------------------------------------

    public List<AuctionListing> active(Sort sort, String filter) {
        long now = System.currentTimeMillis();
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.state() == AuctionListing.State.ACTIVE && l.expires() > now && (f.isEmpty() || matches(l, f))) {
                out.add(l);
            }
        }
        Comparator<AuctionListing> cmp = switch (sort) {
            case NEWEST -> Comparator.comparingLong(AuctionListing::created).reversed();
            case CHEAPEST -> Comparator.comparingLong(AuctionListing::price);
            case EXPENSIVE -> Comparator.comparingLong(AuctionListing::price).reversed();
            case ENDING -> Comparator.comparingLong(AuctionListing::expires);
        };
        out.sort(cmp);
        return out;
    }

    private static boolean matches(AuctionListing l, String filter) {
        ItemStack item = l.item();
        if (item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(filter)
                || l.sellerName().toLowerCase(Locale.ROOT).contains(filter)) {
            return true;
        }
        Component name = item.getItemMeta() != null && item.getItemMeta().hasDisplayName() ? item.getItemMeta().displayName() : null;
        return name != null && Text.plain(name).toLowerCase(Locale.ROOT).contains(filter);
    }

    public List<AuctionListing> activeOf(UUID seller) {
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.state() == AuctionListing.State.ACTIVE && l.seller().equals(seller)) {
                out.add(l);
            }
        }
        return out;
    }

    public List<AuctionListing> claimable(UUID seller) {
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.claimable() && l.seller().equals(seller)) {
                out.add(l);
            }
        }
        return out;
    }

    public int maxListings(Player player) {
        if (player.hasPermission("mythos.auction.unlimited")) {
            return Integer.MAX_VALUE;
        }
        Integer byRank = maxByRank.get(plugin.ranks().rankOf(player).id().toUpperCase(Locale.ROOT));
        return byRank == null ? maxListings : Math.max(maxListings, byRank);
    }

    /** True while the player's inventory is a kit (arena, tournament) – trading would lose or duplicate items. */
    public boolean blockedHere(Player player) {
        return plugin.arenas().isInArena(player) || plugin.tournaments().isParticipant(player);
    }

    // ---- actions ------------------------------------------------------------------------------------------

    /** Lists the stack in the main hand. Returns false (with a message) if not possible. */
    public boolean sell(Player player, long price, Currency currency) {
        if (!checkReady(player)) {
            return false;
        }
        if (blockedHere(player)) {
            plugin.messages().send(player, "auction.blocked-here");
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            plugin.messages().send(player, "auction.empty-hand");
            return false;
        }
        if (MarketMath.alwaysBlocked(hand.getType()) || blocked.contains(hand.getType())) {
            plugin.messages().send(player, "auction.item-blocked");
            return false;
        }
        if (currency == Currency.GEMS && !gemsAllowed) {
            plugin.messages().send(player, "auction.gems-disabled");
            return false;
        }
        if (price < minPrice || price > maxPrice) {
            plugin.messages().send(player, "auction.price-range", "min", minPrice, "max", maxPrice);
            return false;
        }
        int max = maxListings(player);
        if (activeOf(player.getUniqueId()).size() >= max) {
            plugin.messages().send(player, "auction.limit", "max", max);
            return false;
        }
        ItemStack item = hand.clone();
        player.getInventory().setItemInMainHand(null);
        long now = System.currentTimeMillis();
        AuctionListing listing = new AuctionListing(UUID.randomUUID().toString(), player.getUniqueId(), player.getName(), item, price,
                currency, now, now + durationMillis, AuctionListing.State.ACTIVE, null, 0);
        listings.put(listing.id(), listing);
        persist(listing, "list auction");
        Component itemName = itemName(item);
        plugin.messages().send(player, "auction.listed", "item", itemName, "amount", item.getAmount(), "price", price,
                "currency", currencyName(currency), "tax", taxPercent);
        SoundSpec.of("block.note_block.chime", 0.8f, 1.4f).play(player);
        if (broadcast) {
            Component msg = plugin.messages().prefix().append(plugin.messages().get("auction.broadcast", "player", player.getName(),
                    "item", itemName, "amount", item.getAmount(), "price", price, "currency", currencyName(currency)))
                    .clickEvent(ClickEvent.runCommand("/ah"))
                    .hoverEvent(HoverEvent.showText(plugin.messages().get("auction.broadcast-hover")));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player)) {
                    p.sendMessage(msg);
                }
            }
        }
        plugin.getLogger().info("[Auction] " + player.getName() + " listed " + item.getAmount() + "x " + item.getType() + " for " + price
                + " " + currency.id());
        return true;
    }

    /** Buys a listing. Re-checks everything, so a stale menu can never buy twice. */
    public boolean buy(Player player, AuctionListing listing) {
        if (!checkReady(player)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (listings.get(listing.id()) != listing || listing.state() != AuctionListing.State.ACTIVE || listing.expires() <= now) {
            plugin.messages().send(player, "auction.gone");
            return false;
        }
        if (listing.seller().equals(player.getUniqueId())) {
            plugin.messages().send(player, "auction.own");
            return false;
        }
        if (blockedHere(player)) {
            plugin.messages().send(player, "auction.blocked-here");
            return false;
        }
        PlayerData buyer = plugin.playerData().get(player);
        if (buyer == null) {
            plugin.messages().send(player, "general.data-loading");
            return false;
        }
        EconomyManager eco = plugin.economy();
        EconomyManager.Result result = eco.withdraw(buyer, listing.currency(), listing.price());
        if (result != EconomyManager.Result.SUCCESS) {
            plugin.messages().send(player, "auction.not-enough", "price", listing.price(), "currency", currencyName(listing.currency()),
                    "balance", listing.currency().get(buyer));
            return false;
        }
        listing.finish(AuctionListing.State.SOLD, player.getUniqueId(), now);
        listings.remove(listing.id());
        CustomItemManager.give(player, listing.item().clone());
        long payout = MarketMath.payout(listing.price(), taxPercent);
        plugin.playerData().save(buyer);
        persist(listing, "sell auction");
        eco.log(player.getUniqueId(), null, listing.currency(), listing.price(), "auction-buy");
        eco.credit(listing.seller(), listing.currency(), payout, player.getUniqueId(), "auction-sale");
        Component itemName = itemName(listing.item());
        plugin.messages().send(player, "auction.bought", "item", itemName, "amount", listing.item().getAmount(), "price", listing.price(),
                "currency", currencyName(listing.currency()), "seller", listing.sellerName());
        SoundSpec.of("entity.player.levelup", 0.6f, 1.6f).play(player);
        Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            plugin.messages().send(seller, "auction.sold", "item", itemName, "amount", listing.item().getAmount(), "buyer", player.getName(),
                    "payout", payout, "currency", currencyName(listing.currency()));
            SoundSpec.of("entity.experience_orb.pickup", 0.8f, 1.2f).play(seller);
        }
        plugin.getLogger().info("[Auction] " + player.getName() + " bought " + listing.item().getAmount() + "x " + listing.item().getType()
                + " from " + listing.sellerName() + " for " + listing.price() + " " + listing.currency().id());
        return true;
    }

    /** Takes an active listing off the market; the item goes to the seller's pickup list (and straight back if they cancel). */
    public boolean cancel(Player actor, AuctionListing listing, boolean admin) {
        if (!checkReady(actor)) {
            return false;
        }
        if (listings.get(listing.id()) != listing || listing.state() != AuctionListing.State.ACTIVE) {
            plugin.messages().send(actor, "auction.gone");
            return false;
        }
        boolean own = listing.seller().equals(actor.getUniqueId());
        if (!own && !admin) {
            return false;
        }
        listing.finish(AuctionListing.State.CANCELLED, null, System.currentTimeMillis());
        persist(listing, "cancel auction");
        if (own) {
            plugin.messages().send(actor, "auction.cancelled", "item", itemName(listing.item()));
            claim(actor, listing);
        } else {
            plugin.messages().send(actor, "auction.removed", "item", itemName(listing.item()), "player", listing.sellerName());
            Player seller = Bukkit.getPlayer(listing.seller());
            if (seller != null) {
                plugin.messages().send(seller, "auction.removed-by-team", "item", itemName(listing.item()));
            }
            plugin.getLogger().info("[Auction] " + actor.getName() + " removed the listing of " + listing.sellerName());
        }
        return true;
    }

    /** Gives an expired or cancelled item back. Whatever does not fit stays in the pickup list. */
    public boolean claim(Player player, AuctionListing listing) {
        if (!checkReady(player)) {
            return false;
        }
        if (listings.get(listing.id()) != listing || !listing.claimable() || !listing.seller().equals(player.getUniqueId())) {
            plugin.messages().send(player, "auction.gone");
            return false;
        }
        if (blockedHere(player)) {
            plugin.messages().send(player, "auction.blocked-here");
            return false;
        }
        Map<Integer, ItemStack> rest = player.getInventory().addItem(listing.item().clone());
        if (rest.isEmpty()) {
            listing.finish(AuctionListing.State.RETURNED, null, System.currentTimeMillis());
            listings.remove(listing.id());
            plugin.messages().send(player, "auction.claimed", "item", itemName(listing.item()), "amount", listing.item().getAmount());
        } else {
            int left = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            listing.item().setAmount(left);
            plugin.messages().send(player, "auction.inventory-full");
        }
        persist(listing, "claim auction");
        return true;
    }

    public int claimAll(Player player) {
        int n = 0;
        for (AuctionListing l : claimable(player.getUniqueId())) {
            if (player.getInventory().firstEmpty() < 0) {
                plugin.messages().send(player, "auction.inventory-full");
                break;
            }
            if (claim(player, l) && l.state() == AuctionListing.State.RETURNED) {
                n++;
            }
        }
        return n;
    }

    private void expire() {
        if (!loaded) {
            return;
        }
        long now = System.currentTimeMillis();
        for (AuctionListing l : listings.values()) {
            if (l.state() == AuctionListing.State.ACTIVE && l.expires() <= now) {
                l.finish(AuctionListing.State.EXPIRED, null, now);
                persist(l, "expire auction");
                Player seller = Bukkit.getPlayer(l.seller());
                if (seller != null) {
                    plugin.messages().send(seller, "auction.expired", "item", itemName(l.item()));
                }
            }
        }
    }

    /** Reminder on join when items wait for pickup. */
    public void greet(Player player) {
        if (loaded && !claimable(player.getUniqueId()).isEmpty()) {
            plugin.messages().send(player, "auction.pickup-reminder", "count", claimable(player.getUniqueId()).size());
        }
    }

    private boolean checkReady(Player player) {
        if (!enabled) {
            plugin.messages().send(player, "auction.disabled");
            return false;
        }
        if (!loaded) {
            plugin.messages().send(player, "auction.loading");
            return false;
        }
        return true;
    }

    private void persist(AuctionListing l, String operation) {
        MarketRepository.AuctionRow row = new MarketRepository.AuctionRow(l.id(), l.seller().toString(), l.sellerName(),
                l.item().serializeAsBytes(), l.price(), l.currency().id(), l.created(), l.expires(), l.state().name(),
                l.buyer() == null ? null : l.buyer().toString(), l.finished());
        plugin.database().query(operation, c -> {
            repo.saveAuction(c, row);
            return null;
        });
    }

    public Component currencyName(Currency currency) {
        return plugin.messages().get("economy.names." + currency.id());
    }

    public static Component itemName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName();
        }
        if (item.getItemMeta() != null && item.getItemMeta().hasItemName()) {
            return item.getItemMeta().itemName();
        }
        return Component.translatable(item.getType().translationKey());
    }

    public int openCount() {
        int n = 0;
        for (AuctionListing l : listings.values()) {
            if (l.state() == AuctionListing.State.ACTIVE) {
                n++;
            }
        }
        return n;
    }
}
