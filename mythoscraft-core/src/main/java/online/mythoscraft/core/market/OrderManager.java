package online.mythoscraft.core.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Farm orders ("Farmaufträge"). The owner pays all coins up front; deliverers are paid from that escrow per item,
 * the owner collects the items whenever they like. Everything runs on the main thread and is saved at once.
 */
public final class OrderManager {

    private final MythosCraft plugin;
    private final Map<String, FarmOrder> orders = new LinkedHashMap<>();
    private MarketRepository repo;
    private boolean loaded;
    private boolean loading;

    private boolean enabled = true;
    private long durationMillis = 7 * 86_400_000L;
    private int maxOrders = 3;
    private int maxAmount = 2304;
    private long minPrice = 1;
    private long maxPrice = 100_000;
    private boolean broadcast = true;
    private final Set<Material> blocked = EnumSet.noneOf(Material.class);

    public OrderManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("farm-orders");
        enabled = cfg.bool("enabled", true);
        durationMillis = cfg.integer("duration-days", 7, 1, 60) * 86_400_000L;
        maxOrders = cfg.integer("max-orders", 3, 1, 100);
        maxAmount = cfg.integer("max-amount", 2304, 1, 100_000);
        minPrice = cfg.longValue("min-price-each", 1, 1, 1_000_000_000L);
        maxPrice = cfg.longValue("max-price-each", 100_000, minPrice, 1_000_000_000L);
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
        plugin.tasks().repeat(this, 20 * 60, 20 * 60, "farm order expiry", this::expire);
    }

    private void load() {
        loading = true;
        plugin.database().query("load farm orders", repo::openOrders).whenComplete((rows, error) -> plugin.tasks().sync(() -> {
            loading = false;
            if (error != null) {
                plugin.getLogger().severe("Farm orders could not be loaded – they stay closed until the next reload.");
                return;
            }
            orders.clear();
            for (MarketRepository.OrderRow r : rows) {
                Material m = Material.matchMaterial(r.material());
                if (m == null) {
                    plugin.getLogger().warning("Skipping farm order " + r.id() + " with unknown material " + r.material());
                    continue;
                }
                orders.put(r.id(), new FarmOrder(r.id(), UUID.fromString(r.owner()), r.ownerName(), m, r.amount(), r.delivered(),
                        r.collected(), r.priceEach(), r.created(), r.expires(), FarmOrder.State.valueOf(r.state())));
            }
            loaded = true;
            plugin.getLogger().info("Farm orders: " + orders.size() + " loaded");
            expire();
        }));
    }

    public boolean ready() {
        return loaded && enabled;
    }

    public int maxAmount() {
        return maxAmount;
    }

    // ---- queries ------------------------------------------------------------------------------------------

    /** Open orders, best paid first. */
    public List<FarmOrder> open() {
        long now = System.currentTimeMillis();
        List<FarmOrder> out = new ArrayList<>();
        for (FarmOrder o : orders.values()) {
            if (o.state() == FarmOrder.State.OPEN && o.expires() > now) {
                out.add(o);
            }
        }
        out.sort(Comparator.comparingLong(FarmOrder::priceEach).reversed().thenComparingLong(FarmOrder::created));
        return out;
    }

    public List<FarmOrder> of(UUID owner) {
        List<FarmOrder> out = new ArrayList<>();
        for (FarmOrder o : orders.values()) {
            if (o.owner().equals(owner)) {
                out.add(o);
            }
        }
        return out;
    }

    public long openCountOf(UUID owner) {
        return of(owner).stream().filter(o -> o.state() == FarmOrder.State.OPEN).count();
    }

    public boolean allowed(Material material) {
        return !MarketMath.alwaysBlocked(material) && !blocked.contains(material);
    }

    /** Plain items only: no custom names, enchantments, damage or plugin items. */
    public static boolean matches(ItemStack item, Material material) {
        return item != null && item.getType() == material && item.isSimilar(new ItemStack(material));
    }

    public static int count(PlayerInventory inv, Material material) {
        int n = 0;
        for (ItemStack item : inv.getStorageContents()) {
            if (matches(item, material)) {
                n += item.getAmount();
            }
        }
        return n;
    }

    // ---- actions ------------------------------------------------------------------------------------------

    public boolean create(Player player, Material material, int amount, long priceEach) {
        if (!checkReady(player)) {
            return false;
        }
        if (plugin.auctions().blockedHere(player)) {
            plugin.messages().send(player, "orders.blocked-here");
            return false;
        }
        if (!allowed(material)) {
            plugin.messages().send(player, "orders.item-blocked");
            return false;
        }
        if (amount < 1 || amount > maxAmount) {
            plugin.messages().send(player, "orders.amount-range", "max", maxAmount);
            return false;
        }
        if (priceEach < minPrice || priceEach > maxPrice) {
            plugin.messages().send(player, "orders.price-range", "min", minPrice, "max", maxPrice);
            return false;
        }
        if (openCountOf(player.getUniqueId()) >= maxOrders && !player.hasPermission("mythos.orders.unlimited")) {
            plugin.messages().send(player, "orders.limit", "max", maxOrders);
            return false;
        }
        long total = MarketMath.total(amount, priceEach);
        PlayerData data = plugin.playerData().get(player);
        if (data == null) {
            plugin.messages().send(player, "general.data-loading");
            return false;
        }
        if (total < 0 || plugin.economy().withdraw(data, Currency.COINS, total) != EconomyManager.Result.SUCCESS) {
            plugin.messages().send(player, "orders.not-enough", "total", Math.max(0, total), "balance", data.coins());
            return false;
        }
        long now = System.currentTimeMillis();
        FarmOrder order = new FarmOrder(UUID.randomUUID().toString(), player.getUniqueId(), player.getName(), material, amount, 0, 0,
                priceEach, now, now + durationMillis, FarmOrder.State.OPEN);
        orders.put(order.id(), order);
        MarketRepository.OrderRow row = row(order);
        plugin.playerData().saveTogether("create farm order", List.of(data), c -> repo.saveOrder(c, row));
        plugin.economy().log(player.getUniqueId(), null, Currency.COINS, total, "order-escrow");
        Component item = Component.translatable(material.translationKey());
        plugin.messages().send(player, "orders.created", "amount", amount, "item", item, "price", priceEach, "total", total);
        SoundSpec.of("block.note_block.chime", 0.8f, 1.4f).play(player);
        if (broadcast) {
            Component msg = plugin.messages().prefix().append(plugin.messages().get("orders.broadcast", "player", player.getName(),
                    "amount", amount, "item", item, "price", priceEach)).clickEvent(ClickEvent.runCommand("/auftrag"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player)) {
                    p.sendMessage(msg);
                }
            }
        }
        plugin.getLogger().info("[Orders] " + player.getName() + " ordered " + amount + "x " + material + " for " + priceEach + " each");
        return true;
    }

    /** Delivers as many matching items as the player has (up to what the order still needs). */
    public boolean deliver(Player player, FarmOrder order) {
        if (!checkReady(player)) {
            return false;
        }
        if (orders.get(order.id()) != order || order.state() != FarmOrder.State.OPEN || order.expires() <= System.currentTimeMillis()) {
            plugin.messages().send(player, "orders.gone");
            return false;
        }
        if (order.owner().equals(player.getUniqueId())) {
            plugin.messages().send(player, "orders.own");
            return false;
        }
        if (plugin.auctions().blockedHere(player)) {
            plugin.messages().send(player, "orders.blocked-here");
            return false;
        }
        PlayerData data = plugin.playerData().get(player);
        if (data == null) {
            plugin.messages().send(player, "general.data-loading");
            return false;
        }
        PlayerInventory inv = player.getInventory();
        int n = MarketMath.deliverable(count(inv, order.material()), order.amount(), order.delivered());
        Component item = Component.translatable(order.material().translationKey());
        if (n <= 0) {
            plugin.messages().send(player, "orders.nothing-to-deliver", "item", item);
            return false;
        }
        int toRemove = n;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack stack = contents[i];
            if (matches(stack, order.material())) {
                int take = Math.min(toRemove, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                contents[i] = stack.getAmount() <= 0 ? null : stack;
                toRemove -= take;
            }
        }
        inv.setStorageContents(contents);
        order.addDelivered(n);
        long pay = n * order.priceEach();
        plugin.economy().earn(data, Currency.COINS, pay, false);
        MarketRepository.OrderRow row = row(order);
        plugin.playerData().saveTogether("deliver farm order", List.of(data), c -> repo.saveOrder(c, row));
        plugin.economy().log(order.owner(), player.getUniqueId(), Currency.COINS, pay, "order-delivery");
        plugin.messages().send(player, "orders.delivered", "amount", n, "item", item, "player", order.ownerName(), "pay", pay);
        SoundSpec.of("entity.experience_orb.pickup", 0.8f, 1.3f).play(player);
        Player owner = Bukkit.getPlayer(order.owner());
        if (owner != null) {
            plugin.messages().send(owner, order.state() == FarmOrder.State.COMPLETED ? "orders.owner-completed" : "orders.owner-delivery",
                    "player", player.getName(), "amount", n, "item", item, "delivered", order.delivered(), "total", order.amount());
        }
        return true;
    }

    /** Gives delivered items to the owner (as many as fit). */
    public int collect(Player player, FarmOrder order) {
        if (!checkReady(player) || orders.get(order.id()) != order || !order.owner().equals(player.getUniqueId())) {
            return 0;
        }
        if (plugin.auctions().blockedHere(player)) {
            plugin.messages().send(player, "orders.blocked-here");
            return 0;
        }
        int left = order.collectable();
        int given = 0;
        int stackSize = order.material().getMaxStackSize();
        while (left > 0) {
            int n = Math.min(left, stackSize);
            Map<Integer, ItemStack> rest = player.getInventory().addItem(new ItemStack(order.material(), n));
            int notGiven = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            given += n - notGiven;
            left -= n - notGiven;
            if (notGiven > 0) {
                break;
            }
        }
        Component item = Component.translatable(order.material().translationKey());
        if (given > 0) {
            order.addCollected(given);
            persist(order, "collect farm order");
            plugin.messages().send(player, "orders.collected", "amount", given, "item", item);
        }
        if (order.collectable() > 0) {
            plugin.messages().send(player, given > 0 ? "orders.inventory-full-partial" : "orders.inventory-full", "left", order.collectable());
        }
        if (order.done()) {
            orders.remove(order.id());
        }
        return given;
    }

    /** Cancels an open order: the coins for undelivered items go back to the owner. */
    public boolean cancel(Player actor, FarmOrder order, boolean admin) {
        if (!checkReady(actor) || orders.get(order.id()) != order || order.state() != FarmOrder.State.OPEN) {
            plugin.messages().send(actor, "orders.gone");
            return false;
        }
        boolean own = order.owner().equals(actor.getUniqueId());
        if (!own && !admin) {
            return false;
        }
        close(order, FarmOrder.State.CANCELLED);
        if (own) {
            plugin.messages().send(actor, "orders.cancelled", "refund", order.remaining() * order.priceEach());
        } else {
            plugin.messages().send(actor, "orders.removed", "player", order.ownerName());
        }
        return true;
    }

    private void close(FarmOrder order, FarmOrder.State state) {
        long refund = order.escrow();
        order.close(state);
        persist(order, "close farm order");
        if (refund > 0) {
            plugin.economy().credit(order.owner(), Currency.COINS, refund, null, "order-refund");
        }
        if (order.done()) {
            orders.remove(order.id());
        }
    }

    private void expire() {
        if (!loaded) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<FarmOrder> it = new ArrayList<>(orders.values()).iterator();
        while (it.hasNext()) {
            FarmOrder o = it.next();
            if (o.state() == FarmOrder.State.OPEN && o.expires() <= now) {
                long refund = o.escrow();
                close(o, FarmOrder.State.EXPIRED);
                Player owner = Bukkit.getPlayer(o.owner());
                if (owner != null) {
                    plugin.messages().send(owner, "orders.expired", "item", Component.translatable(o.material().translationKey()),
                            "refund", refund);
                }
            }
        }
    }

    public void greet(Player player) {
        if (!loaded) {
            return;
        }
        int items = of(player.getUniqueId()).stream().mapToInt(FarmOrder::collectable).sum();
        if (items > 0) {
            plugin.messages().send(player, "orders.pickup-reminder", "amount", items);
        }
    }

    private boolean checkReady(Player player) {
        if (!enabled) {
            plugin.messages().send(player, "orders.disabled");
            return false;
        }
        if (!loaded) {
            plugin.messages().send(player, "orders.loading");
            return false;
        }
        return true;
    }

    private void persist(FarmOrder order, String operation) {
        MarketRepository.OrderRow row = row(order);
        plugin.database().query(operation, c -> {
            repo.saveOrder(c, row);
            return null;
        });
    }

    private static MarketRepository.OrderRow row(FarmOrder o) {
        return new MarketRepository.OrderRow(o.id(), o.owner().toString(), o.ownerName(), o.material().name(), o.amount(), o.delivered(),
                o.collected(), o.priceEach(), o.created(), o.expires(), o.state().name());
    }

    public int openCount() {
        return open().size();
    }
}
