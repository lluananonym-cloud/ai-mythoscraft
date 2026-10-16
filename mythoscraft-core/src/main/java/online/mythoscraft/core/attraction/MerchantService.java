package online.mythoscraft.core.attraction;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.shop.ShopManager;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Merchants on the market square: fixed stalls with their own offers, plus a wandering merchant who visits every few
 * hours with a random selection of rare offers. Offers use the shop format (reward / price / currency).
 */
public final class MerchantService {

    static final String TAG = "merchant";

    record Merchant(String id, String name, Villager.Profession profession, List<ShopManager.Entry> offers) {
    }

    private final MythosCraft plugin;
    private final YamlStore store;
    private final List<Merchant> merchants = new ArrayList<>();
    private final List<ShopManager.Entry> wanderPool = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<java.util.UUID, Merchant> byEntity = new HashMap<>();
    private Entity wanderer;
    private List<ShopManager.Entry> wanderOffers = List.of();
    private String wanderName = "<gold>Wanderhändler";
    private int everyMinutes = 180;
    private int stayMinutes = 30;
    private int offerCount = 5;

    public MerchantService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/merchant.yml");
        store.load(false);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.merchants");
        merchants.clear();
        ConfigReader stalls = cfg.sub("stalls");
        for (String id : stalls.keys()) {
            ConfigReader s = stalls.sub(id);
            Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(org.bukkit.NamespacedKey.minecraft(
                    s.string("profession", "librarian").toLowerCase(Locale.ROOT)));
            merchants.add(new Merchant(id, online.mythoscraft.core.config.ConfigText.tr(s.string("name", id)), prof == null ? Villager.Profession.LIBRARIAN : prof,
                    plugin.shop().parseEntries(s, "offers")));
        }
        ConfigReader w = cfg.sub("wandering");
        wanderName = online.mythoscraft.core.config.ConfigText.tr(w.string("name", "<gold>Wanderhändler"));
        everyMinutes = w.integer("every-minutes", 180, 10, 10_000);
        stayMinutes = w.integer("stay-minutes", 30, 1, 1440);
        offerCount = w.integer("offers", 5, 1, 21);
        wanderPool.clear();
        wanderPool.addAll(plugin.shop().parseEntries(w, "pool"));
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 200, 600, "wandering merchant", this::tickWanderer);
    }

    public void rebuild() {
        clear();
        List<Location> spots = plugin.attractionStore().all("merchant");
        for (int i = 0; i < spots.size() && i < merchants.size(); i++) {
            Location at = spots.get(i);
            if (!at.isChunkLoaded()) {
                continue;
            }
            Merchant m = merchants.get(i);
            Villager v = at.getWorld().spawn(at, Villager.class, e -> {
                prepare(e, m.name());
                e.setProfession(m.profession());
                e.setVillagerLevel(5);
            });
            byEntity.put(v.getUniqueId(), m);
            spawned.add(v);
        }
        if (wanderActive()) {
            spawnWanderer();
        }
    }

    private void prepare(LivingEntity e, String name) {
        e.setPersistent(false);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setAI(false);
        e.setCollidable(false);
        e.setRemoveWhenFarAway(false);
        e.customName(Text.parse(name));
        e.setCustomNameVisible(true);
        e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
    }

    public boolean present() {
        return spawned.stream().anyMatch(Entity::isValid);
    }

    // ---- wandering merchant --------------------------------------------------------------------------------

    private boolean wanderActive() {
        return store.yaml().getLong("until", 0) > System.currentTimeMillis();
    }

    private void tickWanderer() {
        YamlConfiguration yaml = store.yaml();
        long now = System.currentTimeMillis();
        long until = yaml.getLong("until", 0);
        long next = yaml.getLong("next", 0);
        if (until > 0 && now >= until) {
            yaml.set("until", 0);
            store.replace(yaml);
            store.saveAsync(plugin.tasks());
            removeWanderer();
            plugin.messages().broadcast("attractions.merchant.wanderer-left");
            return;
        }
        if (until == 0 && now >= next && !wanderPool.isEmpty() && plugin.attractionStore().first("merchant_wander") != null) {
            arrive();
        }
    }

    /** Lets the wandering merchant arrive now (also /mythos merchant). */
    public void arrive() {
        List<ShopManager.Entry> pool = new ArrayList<>(wanderPool);
        Collections.shuffle(pool, ThreadLocalRandom.current());
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < pool.size() && i < offerCount; i++) {
            picked.add(String.valueOf(wanderPool.indexOf(pool.get(i))));
        }
        long now = System.currentTimeMillis();
        YamlConfiguration yaml = store.yaml();
        yaml.set("until", now + stayMinutes * 60_000L);
        yaml.set("next", now + everyMinutes * 60_000L);
        yaml.set("offers", picked);
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
        spawnWanderer();
        plugin.messages().broadcast("attractions.merchant.wanderer-arrived", "minutes", stayMinutes);
    }

    private void spawnWanderer() {
        removeWanderer();
        List<ShopManager.Entry> offers = new ArrayList<>();
        for (String s : store.yaml().getStringList("offers")) {
            try {
                int index = Integer.parseInt(s);
                if (index >= 0 && index < wanderPool.size()) {
                    offers.add(wanderPool.get(index));
                }
            } catch (NumberFormatException ignored) {
                // stale entry
            }
        }
        wanderOffers = offers;
        Location at = plugin.attractionStore().first("merchant_wander");
        if (at == null || !at.isChunkLoaded()) {
            return;
        }
        WanderingTrader t = at.getWorld().spawn(at, WanderingTrader.class, e -> {
            prepare(e, wanderName);
            e.setDespawnDelay(0);
        });
        wanderer = t;
        spawned.add(t);
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
        TextDisplay timer = at.getWorld().spawn(at.clone().add(0, 2.9, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0x70, 0x1b, 0x10, 0x22));
            d.text(plugin.messages().get("attractions.merchant.wanderer-hologram"));
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        });
        spawned.add(timer);
    }

    private void removeWanderer() {
        if (wanderer != null && wanderer.isValid()) {
            wanderer.getWorld().spawnParticle(Particle.CLOUD, wanderer.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
            wanderer.remove();
        }
        spawned.removeIf(e -> {
            boolean timer = e instanceof TextDisplay;
            if (timer && e.isValid()) {
                e.remove();
            }
            return timer || e == wanderer;
        });
        wanderer = null;
    }

    // ---- trading --------------------------------------------------------------------------------------------

    public boolean isMerchant(Entity e) {
        return TAG.equals(e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING));
    }

    public void open(Player p, Entity clicked) {
        if (clicked == wanderer || clicked instanceof WanderingTrader) {
            if (!wanderActive()) {
                return;
            }
            long left = store.yaml().getLong("until", 0) - System.currentTimeMillis();
            new OfferMenu(plugin.messages().get("attractions.merchant.wanderer-title", "time",
                    Durations.format(Duration.ofMillis(Math.max(0, left)))), wanderOffers).open(p);
            return;
        }
        Merchant m = byEntity.get(clicked.getUniqueId());
        if (m != null) {
            new OfferMenu(Text.parse(m.name()), m.offers()).open(p);
        }
        SoundSpec.of("entity.villager.trade", 0.8f, 1f).play(p);
    }

    private final class OfferMenu extends Menu {

        private final List<ShopManager.Entry> offers;
        private final Component title;

        OfferMenu(Component title, List<ShopManager.Entry> offers) {
            super(Math.min(6, 2 + (offers.size() + 6) / 7), title);
            this.offers = offers;
            this.title = title;
            ShopManager shop = plugin.shop();
            for (int i = 0; i < offers.size() && i < 28; i++) {
                ShopManager.Entry entry = offers.get(i);
                Component currency = plugin.messages().get("economy.names." + entry.currency().id());
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(plugin.messages().get("shop.lore.price", "price", entry.price(), "currency", currency));
                lore.add(plugin.messages().get("shop.lore.buy-one"));
                getInventory().setItem(slot(i), MenuItems.withLore(shop.icon(entry), lore));
            }
        }

        private int slot(int i) {
            return 10 + (i / 7) * 9 + i % 7;
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            for (int i = 0; i < offers.size() && i < 28; i++) {
                if (slot(i) == event.getRawSlot()) {
                    plugin.shop().buy(player, offers.get(i), 1);
                    new OfferMenu(title, offers).open(player);
                    return;
                }
            }
        }
    }

    public void clear() {
        spawned.forEach(e -> {
            if (e.isValid()) {
                e.remove();
            }
        });
        spawned.clear();
        byEntity.clear();
        wanderer = null;
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        clear();
    }
}
