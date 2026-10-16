package online.mythoscraft.core.shop;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.SeasonService;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Gem shop: crate keys and a few extras for gems (or coins), configured under shop: in config.yml. */
public final class ShopManager {

    /** One offer. {@code season} = only buyable while that season is active (null = always). */
    public record Entry(int slot, Reward reward, long price, Currency currency, String name, List<String> lore, Material icon,
                        String season, int page) {
        @Override
        public String name() {
            return online.mythoscraft.core.config.ConfigText.tr(name);
        }

        @Override
        public List<String> lore() {
            return online.mythoscraft.core.config.ConfigText.tr(lore);
        }
    }

    private final MythosCraft plugin;
    private final List<Entry> entries = new ArrayList<>();
    private boolean enabled = true;
    private int rows = 5;
    private int bulkAmount = 5;

    public ShopManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("shop");
        enabled = cfg.bool("enabled", true);
        rows = cfg.integer("rows", 5, 2, 6);
        bulkAmount = cfg.integer("bulk-amount", 5, 1, 64);
        entries.clear();
        entries.addAll(parseEntries(cfg, "entries"));
    }

    /** Parses a list of {reward, price, currency, slot, name, lore, icon, season} offers (shop, merchants). */
    public List<Entry> parseEntries(ConfigReader cfg, String path) {
        List<Entry> entries = new ArrayList<>();
        List<?> list = cfg.raw().getList(path);
        if (list == null) {
            return entries;
        }
        int index = 0;
        for (Object o : list) {
            index++;
            if (!(o instanceof Map<?, ?> map)) {
                cfg.warn(path, "Eintrag " + index + " ist kein {reward: …, price: …}-Objekt");
                continue;
            }
            try {
                Reward reward = Reward.parse(String.valueOf(map.get("reward")));
                long price = Long.parseLong(String.valueOf(map.get("price")));
                if (price <= 0) {
                    throw new IllegalArgumentException("Preis muss größer als 0 sein");
                }
                Object cur = map.get("currency");
                Currency currency = cur == null ? Currency.GEMS : Currency.byId(String.valueOf(cur));
                if (currency == null) {
                    throw new IllegalArgumentException("Währung muss coins oder gems sein");
                }
                if (reward.type() == Reward.Type.KEY && plugin.crates().type(reward.id()) == null) {
                    throw new IllegalArgumentException("Kiste '" + reward.id() + "' gibt es nicht (crates.yml)");
                }
                int slot = map.get("slot") == null ? -1 : Integer.parseInt(String.valueOf(map.get("slot")));
                Material icon = map.get("icon") == null ? null : Material.matchMaterial(String.valueOf(map.get("icon")));
                List<String> lore = new ArrayList<>();
                if (map.get("lore") instanceof List<?> l) {
                    l.forEach(line -> lore.add(String.valueOf(line)));
                }
                entries.add(new Entry(slot, reward, price, currency, map.get("name") == null ? null : String.valueOf(map.get("name")),
                        lore, icon, map.get("season") == null ? null : String.valueOf(map.get("season")),
                        map.get("page") == null ? 1 : Math.max(1, Integer.parseInt(String.valueOf(map.get("page"))))));
            } catch (IllegalArgumentException e) {
                cfg.warn(path, "Eintrag " + index + ": " + e.getMessage());
            }
        }
        return entries;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Configured rows, enlarged so every offer with a fixed slot fits above the bottom bar. */
    public int rows() {
        int maxSlot = entries.stream().mapToInt(Entry::slot).max().orElse(0);
        return Math.max(rows, Math.min(6, maxSlot / 9 + 2));
    }

    /** Number of shop pages (entries with "page: 2" … go to the next pages). */
    public int pages() {
        return Math.max(1, entries.stream().mapToInt(Entry::page).max().orElse(1));
    }

    public int bulkAmount() {
        return bulkAmount;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** True if the entry is limited to a season that is not running right now. */
    public boolean seasonLocked(Entry entry) {
        if (entry.season() == null || entry.season().isBlank()) {
            return false;
        }
        SeasonService.Season active = plugin.seasons().active();
        return active == null || !active.id().equalsIgnoreCase(entry.season());
    }

    public Component seasonName(Entry entry) {
        for (SeasonService.Season s : plugin.seasons().all()) {
            if (s.id().equalsIgnoreCase(entry.season())) {
                return Text.parse(s.name());
            }
        }
        return Component.text(entry.season());
    }

    /** Icon showing what you get (key, custom item, material …). */
    public ItemStack icon(Entry entry) {
        Reward r = entry.reward();
        int amount = (int) Math.max(1, Math.min(64, r.amount()));
        ItemStack base = switch (r.type()) {
            case KEY -> plugin.crates().createKey(r.id(), amount);
            case TOOL -> plugin.items().create(CustomItem.byId(r.id()), amount);
            case ITEM -> new ItemStack(Material.valueOf(r.id()), Math.min(amount, Material.valueOf(r.id()).getMaxStackSize()));
            case COINS -> new ItemBuilder(Material.GOLD_NUGGET).model("mythos_coin").build();
            case GEMS -> new ItemBuilder(Material.AMETHYST_SHARD).model("mythos_gem").build();
            case XP -> new ItemStack(Material.EXPERIENCE_BOTTLE);
            case VOUCHER -> plugin.vouchers().create(r.id().split("\\|")[0], r.id().split("\\|")[1], amount);
            case BOOK -> r.enchantedBook();
            case COMMAND -> new ItemStack(Material.PAPER);
            case SPAWNER -> online.mythoscraft.core.item.SpawnerItems.create(online.mythoscraft.core.item.SpawnerItems.parse(r.id()), 1);
        };
        if (base == null) {
            base = new ItemStack(Material.BARRIER);
        }
        if (entry.icon() != null) {
            base = base.withType(entry.icon());
        }
        ItemStack icon = base;
        icon.editMeta(meta -> {
            if (entry.name() != null) {
                meta.displayName(Text.item(entry.name()));
            } else if (r.type() != Reward.Type.KEY && r.type() != Reward.Type.TOOL) {
                meta.displayName(Text.noItalic(r.describe(plugin, 1.0)));
            }
            if (!entry.lore().isEmpty()) {
                meta.lore(entry.lore().stream().map(Text::item).toList());
            }
        });
        return icon;
    }

    /** Buys {@code times}× the entry. Returns false (with message) if not possible. */
    public boolean buy(Player player, Entry entry, int times) {
        if (!enabled) {
            plugin.messages().send(player, "shop.disabled");
            return false;
        }
        if (plugin.auctions().blockedHere(player)) {
            plugin.messages().send(player, "shop.blocked-here");
            return false;
        }
        if (seasonLocked(entry)) {
            plugin.messages().send(player, "shop.season-only", "season", seasonName(entry));
            return false;
        }
        PlayerData data = plugin.playerData().get(player);
        if (data == null) {
            plugin.messages().send(player, "general.data-loading");
            return false;
        }
        long total = entry.price() * times;
        Component currencyName = plugin.messages().get("economy.names." + entry.currency().id());
        if (plugin.economy().withdraw(data, entry.currency(), total) != EconomyManager.Result.SUCCESS) {
            plugin.messages().send(player, "shop.not-enough", "price", total, "currency", currencyName,
                    "balance", entry.currency().get(data));
            SoundSpec.of("entity.villager.no", 0.8f, 1f).play(player);
            return false;
        }
        for (int i = 0; i < times; i++) {
            entry.reward().give(plugin, player, 1.0);
        }
        plugin.playerData().save(data);
        plugin.economy().log(player.getUniqueId(), null, entry.currency(), total, "shop " + entry.reward().raw());
        plugin.messages().send(player, "shop.bought", "times", times, "item", entry.reward().describe(plugin, 1.0), "price", total,
                "currency", currencyName, "balance", entry.currency().get(data));
        SoundSpec.of("entity.player.levelup", 0.5f, 1.8f).play(player);
        plugin.getLogger().info("[Shop] " + player.getName() + " bought " + times + "x '" + entry.reward().raw() + "' for " + total + " "
                + entry.currency().id());
        return true;
    }
}
