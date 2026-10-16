package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import online.mythoscraft.core.market.MarketMath;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wishing well: throw coins in for a chance on a prize (the bigger the throw, the better the chances). Every coin
 * fills the community pot – when it is full, a server-wide coin booster starts for everyone.
 */
public final class WellService {

    static final String TAG = "well";

    record Throw(long amount, List<Prize> prizes) {
    }

    record Prize(Reward reward, double chance) {
    }

    private final MythosCraft plugin;
    private final YamlStore store;
    private final List<Throw> throwsList = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private TextDisplay label;
    private long goal = 100_000;
    private double boosterFactor = 2;
    private int boosterMinutes = 30;

    public WellService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/well.yml");
        store.load(false);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.well");
        goal = cfg.longValue("goal", 100_000, 1000, 1_000_000_000_000L);
        boosterFactor = cfg.decimal("booster-factor", 2, 1, 10);
        boosterMinutes = cfg.integer("booster-minutes", 30, 1, 1440);
        throwsList.clear();
        ConfigReader t = cfg.sub("throws");
        for (String key : t.keys()) {
            try {
                long amount = Long.parseLong(key.trim());
                List<Prize> prizes = new ArrayList<>();
                List<?> list = t.raw().getList(key);
                if (list != null) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            prizes.add(new Prize(Reward.parse(String.valueOf(m.get("reward"))),
                                    Double.parseDouble(String.valueOf(m.get("chance")))));
                        }
                    }
                }
                throwsList.add(new Throw(amount, prizes));
            } catch (IllegalArgumentException e) {
                t.warn(key, e.getMessage());
            }
        }
        throwsList.sort(java.util.Comparator.comparingLong(Throw::amount));
    }

    public long pot() {
        return store.yaml().getLong("pot", 0);
    }

    public void rebuild() {
        clear();
        Location at = plugin.attractionStore().first("well");
        if (at == null || !at.isChunkLoaded()) {
            return;
        }
        label = at.getWorld().spawn(at.clone().add(0, 8.8, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0x70, 0x1b, 0x10, 0x22));
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        });
        spawned.add(label);
        spawned.add(at.getWorld().spawn(at.clone().add(0, -0.5, 0), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(6f);
            i.setInteractionHeight(2.4f);
            i.setResponsive(true);
            i.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        }));
        updateLabel();
    }

    private void updateLabel() {
        if (label != null && label.isValid()) {
            label.text(plugin.messages().get("attractions.well.hologram", "pot", Numbers.format(pot()), "goal", Numbers.format(goal),
                    "bar", MarketMath.bar(pot(), goal, 16), "factor", boosterFactor, "minutes", boosterMinutes));
        }
    }

    public boolean present() {
        return label != null && label.isValid();
    }

    public void open(Player p) {
        new WellMenu(p).open(p);
    }

    private void toss(Player p, Throw t) {
        if (!plugin.attractions().charge(p, Currency.COINS, t.amount())) {
            return;
        }
        Location at = plugin.attractionStore().first("well");
        if (at != null) {
            at.getWorld().spawnParticle(Particle.SPLASH, at.clone().add(0, 0.6, 0), 40, 1, 0.2, 1, 0.1);
            at.getWorld().spawnParticle(Particle.WAX_ON, at.clone().add(0, 0.8, 0), 12, 0.8, 0.3, 0.8, 0);
        }
        SoundSpec.of("entity.player.splash", 0.6f, 1.6f).play(p);
        SoundSpec.of("block.chain.place", 0.8f, 1.8f).play(p);
        Prize won = null;
        for (Prize prize : t.prizes()) {
            if (ThreadLocalRandom.current().nextDouble() < prize.chance()) {
                won = prize;
                break;
            }
        }
        if (won == null) {
            plugin.messages().send(p, "attractions.well.nothing", "amount", Numbers.format(t.amount()));
        } else {
            won.reward().give(plugin, p, 1.0);
            plugin.messages().send(p, "attractions.well.won", "reward", won.reward().describe(plugin, 1.0));
            SoundSpec.of("entity.player.levelup", 0.8f, 1.6f).play(p);
            if (won.chance() <= 0.01) {
                plugin.messages().broadcast("attractions.well.jackpot", "player", p.getName(), "reward", won.reward().describe(plugin, 1.0));
            }
        }
        YamlConfiguration yaml = store.yaml();
        long pot = yaml.getLong("pot", 0) + t.amount();
        if (pot >= goal) {
            pot = 0;
            plugin.multipliers().startBooster(MultiplierType.COINS, boosterFactor, Duration.ofMinutes(boosterMinutes),
                    plugin.messages().raw("attractions.well.booster-by"));
            plugin.messages().broadcast("attractions.well.goal-reached", "player", p.getName());
        }
        yaml.set("pot", pot);
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
        updateLabel();
    }

    private final class WellMenu extends Menu {

        WellMenu(Player viewer) {
            super(3, plugin.messages().get("attractions.well.menu-title"));
            for (int i = 0; i < throwsList.size() && i < 5; i++) {
                Throw t = throwsList.get(i);
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(plugin.messages().getList("attractions.well.throw-lore",
                        "amount", Numbers.format(t.amount())));
                for (Prize prize : t.prizes()) {
                    lore.add(plugin.messages().get("attractions.well.chance-line", "reward", prize.reward().describe(plugin, 1.0),
                            "chance", String.format(java.util.Locale.GERMANY, "%.1f", prize.chance() * 100)));
                }
                Material icon = i == 0 ? Material.GOLD_NUGGET : i == 1 ? Material.GOLD_INGOT : Material.GOLD_BLOCK;
                getInventory().setItem(11 + i * 2, MenuItems.button(icon, plugin.messages().get("attractions.well.throw",
                        "amount", Numbers.format(t.amount())), lore));
            }
            getInventory().setItem(22, MenuItems.button(Material.BEACON, plugin.messages().get("attractions.well.pot",
                    "pot", Numbers.format(pot()), "goal", Numbers.format(goal)), plugin.messages().getList("attractions.well.pot-lore",
                    "factor", boosterFactor, "minutes", boosterMinutes)));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            int slot = event.getRawSlot();
            if (slot >= 11 && slot < 21 && (slot - 11) % 2 == 0) {
                int index = (slot - 11) / 2;
                if (index < throwsList.size()) {
                    toss(player, throwsList.get(index));
                    new WellMenu(player).open(player);
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
        label = null;
    }

    public void shutdown() {
        clear();
    }
}
