package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coins rain down around every participant. Picking one up credits its value directly; golden coins are rare and
 * worth more. The player who collected the most coins wins.
 */
public final class CoinRainEvent extends MythosEvent {

    private int waveInterval = 2;
    private int coinsPerWave = 3;
    private double radius = 9;
    private long minValue = 5;
    private long maxValue = 25;
    private double goldenChance = 0.03;
    private int goldenFactor = 10;
    private int despawnSeconds = 20;
    private SoundSpec pickupSound = SoundSpec.NONE;

    private final List<Item> coins = new ArrayList<>();
    private int waveTimer;

    public CoinRainEvent(MythosCraft plugin) {
        super(plugin, "coinrain");
    }

    @Override
    protected int defaultDuration() {
        return 90;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        waveInterval = cfg.integer("wave-interval-seconds", 2, 1, 60);
        coinsPerWave = cfg.integer("coins-per-wave", 3, 1, 50);
        radius = cfg.decimal("radius", 9, 2, 64);
        minValue = cfg.longValue("coin-value.min", 5, 1, 1_000_000);
        maxValue = Math.max(minValue, cfg.longValue("coin-value.max", 25, 1, 1_000_000));
        goldenChance = cfg.decimal("golden-coin.chance", 0.03, 0, 1);
        goldenFactor = cfg.integer("golden-coin.multiplier", 10, 1, 1000);
        despawnSeconds = cfg.integer("despawn-seconds", 20, 3, 300);
        SoundSpec s = SoundSpec.parse(cfg.string("sounds.pickup", "entity.experience_orb.pickup 0.7 1.6"));
        pickupSound = s == null ? SoundSpec.NONE : s;
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.coins";
    }

    @Override
    protected void onStart() {
        waveTimer = 0;
    }

    @Override
    protected void onTick() {
        Iterator<Item> it = coins.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (!item.isValid()) {
                it.remove();
            } else if (item.getTicksLived() > despawnSeconds * 20) {
                item.getWorld().spawnParticle(Particle.SMOKE, item.getLocation(), 4, 0.1, 0.1, 0.1, 0.01);
                item.remove();
                it.remove();
            } else if (item.getTicksLived() % 20 < 1 || ThreadLocalRandom.current().nextInt(3) == 0) {
                item.getWorld().spawnParticle(Particle.WAX_ON, item.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0);
            }
        }
        if (++waveTimer < waveInterval) {
            return;
        }
        waveTimer = 0;
        for (Player p : participants()) {
            for (int i = 0; i < coinsPerWave; i++) {
                spawnCoin(p);
            }
        }
    }

    private void spawnCoin(Player player) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location base = player.getLocation();
        World world = base.getWorld();
        double angle = rnd.nextDouble(Math.PI * 2);
        double r = rnd.nextDouble(1.5, radius);
        Location loc = base.clone().add(Math.cos(angle) * r, rnd.nextDouble(4, 7), Math.sin(angle) * r);
        if (!loc.getBlock().isPassable() || !loc.clone().add(0, -1, 0).getBlock().isPassable()) {
            loc = base.clone().add(Math.cos(angle) * r, 2.5, Math.sin(angle) * r);
            if (!loc.getBlock().isPassable()) {
                return;
            }
        }
        boolean golden = rnd.nextDouble() < goldenChance;
        long value = rnd.nextLong(minValue, maxValue + 1) * (golden ? goldenFactor : 1);
        ItemStack stack = plugin.items().createUnique(CustomItem.MYTHOS_COIN);
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.COIN_VALUE, PersistentDataType.LONG, value));
        Item item = world.dropItem(loc, stack, drop -> {
            drop.setPersistent(false);
            drop.setCanMobPickup(false);
            drop.setPickupDelay(5);
            drop.setVelocity(new Vector(0, -0.15, 0));
            drop.setGlowing(golden);
            drop.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "coinrain:" + session);
        });
        coins.add(item);
        world.spawnParticle(golden ? Particle.TOTEM_OF_UNDYING : Particle.END_ROD, loc, golden ? 12 : 3, 0.2, 0.2, 0.2, 0.02);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        Long value = stack.getPersistentDataContainer().get(Keys.COIN_VALUE, PersistentDataType.LONG);
        if (value == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        event.getItem().remove();
        coins.remove(event.getItem());
        PlayerData data = plugin.playerData().get(player);
        if (data == null) {
            return;
        }
        long amount = Math.round(value * plugin.multipliers().global(MultiplierType.EVENTS));
        long credited = plugin.economy().earn(data, Currency.COINS, amount, false);
        addScore(player, credited);
        pickupSound.play(player, 1.4f + ThreadLocalRandom.current().nextFloat() * 0.4f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0);
        plugin.messages().actionBar(player, "events.coinrain.pickup", "coins", credited, "total", Math.round(score(player.getUniqueId())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (event.getItem().getItemStack().getPersistentDataContainer().has(Keys.COIN_VALUE, PersistentDataType.LONG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (event.getEntity().getItemStack().getPersistentDataContainer().has(Keys.COIN_VALUE, PersistentDataType.LONG)) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void cleanup() {
        for (Item item : coins) {
            if (item.isValid()) {
                item.remove();
            }
        }
        coins.clear();
    }
}
