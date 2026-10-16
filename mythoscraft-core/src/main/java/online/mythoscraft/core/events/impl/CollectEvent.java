package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
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
 * Collect race used by several (seasonal) events: objects appear around every participant – lying on the ground
 * (pumpkins, eggs) or falling from the sky (gifts, shooting stars). Each pickup scores points and pays a few
 * coins; rare golden objects give much more plus a bonus reward.
 */
public final class CollectEvent extends MythosEvent {

    /** Look and behaviour of the collectibles. */
    public enum Style {
        PUMPKINS(false, Particle.FLAME, Color.fromRGB(0xFF7A1A), "entity.witch.celebrate"),
        GIFTS(true, Particle.SNOWFLAKE, Color.fromRGB(0xEF4444), "entity.player.levelup"),
        STARS(true, Particle.END_ROD, Color.fromRGB(0x22D3EE), "entity.firework_rocket.twinkle"),
        EGGS(false, Particle.HAPPY_VILLAGER, Color.fromRGB(0xFDE68A), "entity.chicken.egg"),
        GOLD(true, Particle.WAX_ON, Color.fromRGB(0xFACC15), "entity.experience_orb.pickup"),
        KEYS(true, Particle.ENCHANT, Color.fromRGB(0xC084FC), "block.amethyst_block.chime"),
        DIAMONDS(true, Particle.ELECTRIC_SPARK, Color.fromRGB(0x67E8F9), "block.amethyst_block.resonate"),
        CRYSTALS(false, Particle.ENCHANT, Color.fromRGB(0xC084FC), "block.amethyst_cluster.break"),
        LUCKY(true, Particle.FIREWORK, Color.fromRGB(0xFDE047), "entity.firework_rocket.twinkle"),
        PETALS(true, Particle.SPORE_BLOSSOM_AIR, Color.fromRGB(0xF9A8D4), "block.azalea_leaves.place"),
        HEARTS(true, Particle.HEART, Color.fromRGB(0xF472B6), "entity.player.levelup"),
        SHELLS(false, Particle.SPLASH, Color.fromRGB(0x38BDF8), "entity.dolphin.play"),
        MUSHROOMS(false, Particle.HAPPY_VILLAGER, Color.fromRGB(0xDC2626), "block.fungus.place"),
        FEATHERS(true, Particle.CLOUD, Color.fromRGB(0xF8FAFC), "entity.parrot.fly");

        final boolean falling;
        final Particle particle;
        final Color color;
        final String sound;

        Style(boolean falling, Particle particle, Color color, String sound) {
            this.falling = falling;
            this.particle = particle;
            this.color = color;
            this.sound = sound;
        }
    }

    private static final String TAG = "event_collect";

    private final Style style;
    private final List<Item> objects = new ArrayList<>();
    private int waveInterval = 3;
    private int perWave = 2;
    private double radius = 14;
    private int despawnSeconds = 30;
    private double rareChance = 0.05;
    private int rarePoints = 5;
    private long coinsPerPickup = 5;
    private Reward rareReward;
    private int waveTimer;

    public CollectEvent(MythosCraft plugin, String id, Style style) {
        super(plugin, id);
        this.style = style;
    }

    @Override
    protected int defaultDuration() {
        return 150;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        waveInterval = cfg.integer("wave-interval-seconds", 3, 1, 60);
        perWave = cfg.integer("per-wave", 2, 1, 30);
        radius = cfg.decimal("radius", 14, 3, 64);
        despawnSeconds = cfg.integer("despawn-seconds", 30, 5, 600);
        rareChance = cfg.decimal("rare-chance", 0.05, 0, 1);
        rarePoints = cfg.integer("rare-points", 5, 1, 1000);
        coinsPerPickup = cfg.longValue("coins-per-pickup", 5, 0, 100_000);
        String rare = cfg.string("rare-reward", "");
        try {
            rareReward = rare.isBlank() ? null : Reward.parse(rare);
        } catch (IllegalArgumentException e) {
            cfg.warn("rare-reward", e.getMessage());
            rareReward = null;
        }
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.points";
    }

    @Override
    protected void onStart() {
        waveTimer = 0;
    }

    @Override
    protected void onTick() {
        Iterator<Item> it = objects.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (!item.isValid()) {
                it.remove();
            } else if (item.getTicksLived() > despawnSeconds * 20) {
                item.getWorld().spawnParticle(Particle.SMOKE, item.getLocation(), 4, 0.1, 0.1, 0.1, 0.01);
                item.remove();
                it.remove();
            } else {
                item.getWorld().spawnParticle(style.particle, item.getLocation().add(0, 0.4, 0), 1, 0.15, 0.15, 0.15, 0.01);
            }
        }
        if (++waveTimer < waveInterval) {
            return;
        }
        waveTimer = 0;
        for (Player p : participants()) {
            for (int i = 0; i < perWave; i++) {
                spawn(p);
            }
        }
    }

    private ItemStack look(boolean rare) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return switch (style) {
            case PUMPKINS -> new ItemBuilder(rare ? Material.JACK_O_LANTERN : Material.CARVED_PUMPKIN).glint(rare).build();
            case GIFTS -> {
                ItemStack gift = plugin.items().createUnique(CustomItem.GIFT_BOX);
                if (rare) {
                    gift.editMeta(m -> m.setEnchantmentGlintOverride(true));
                }
                yield gift;
            }
            case STARS -> rare ? new ItemBuilder(Material.NETHER_STAR).glint(true).build() : plugin.items().createUnique(CustomItem.NEBULA_ORB);
            case EGGS -> {
                Material[] eggs = {Material.EGG, Material.BLUE_EGG, Material.BROWN_EGG};
                yield new ItemBuilder(rare ? Material.GOLDEN_APPLE : eggs[r.nextInt(eggs.length)]).glint(rare).build();
            }
            case GOLD -> new ItemBuilder(rare ? Material.GOLD_BLOCK : r.nextBoolean() ? Material.GOLD_NUGGET : Material.GOLD_INGOT).glint(rare).build();
            case KEYS -> rare ? plugin.crates().createKey("vip", 1) : plugin.items().createUnique(CustomItem.KEY_FRAGMENT);
            case DIAMONDS -> new ItemBuilder(rare ? Material.DIAMOND_BLOCK : Material.DIAMOND).glint(rare).build();
            case CRYSTALS -> new ItemBuilder(rare ? Material.ECHO_SHARD : r.nextBoolean() ? Material.AMETHYST_CLUSTER : Material.AMETHYST_SHARD).glint(rare).build();
            case LUCKY -> new ItemBuilder(rare ? Material.TOTEM_OF_UNDYING
                    : new Material[]{Material.FIREWORK_ROCKET, Material.FIREWORK_STAR, Material.GOLD_NUGGET}[r.nextInt(3)]).glint(rare).build();
            case PETALS -> new ItemBuilder(rare ? Material.TORCHFLOWER : r.nextBoolean() ? Material.PINK_PETALS : Material.CHERRY_SAPLING).glint(rare).build();
            case HEARTS -> new ItemBuilder(rare ? Material.HEART_OF_THE_SEA : r.nextBoolean() ? Material.PINK_DYE : Material.POPPY).glint(rare).build();
            case SHELLS -> new ItemBuilder(rare ? Material.HEART_OF_THE_SEA : r.nextBoolean() ? Material.NAUTILUS_SHELL : Material.PRISMARINE_CRYSTALS).glint(rare).build();
            case MUSHROOMS -> new ItemBuilder(rare ? Material.GOLDEN_CARROT : r.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM).glint(rare).build();
            case FEATHERS -> new ItemBuilder(rare ? Material.ELYTRA : Material.FEATHER).glint(rare).build();
        };
    }

    private void spawn(Player player) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = player.getLocation();
        World world = base.getWorld();
        double angle = r.nextDouble(Math.PI * 2);
        double dist = r.nextDouble(3, radius);
        int x = (int) Math.floor(base.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(base.getZ() + Math.sin(angle) * dist);
        Location loc;
        if (style.falling) {
            loc = new Location(world, x + 0.5, base.getY() + r.nextDouble(6, 10), z + 0.5);
            if (!loc.getBlock().isPassable()) {
                return;
            }
        } else {
            Block ground = null;
            for (int dy = 4; dy >= -4; dy--) {
                Block b = world.getBlockAt(x, base.getBlockY() + dy, z);
                if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, 2, 0).isPassable()
                        && !b.getRelative(0, 1, 0).isLiquid()) {
                    ground = b;
                    break;
                }
            }
            if (ground == null) {
                return;
            }
            loc = ground.getLocation().add(0.5, 1.1, 0.5);
        }
        if (venueType != Venue.SPAWN && !plugin.protection().query(loc, Flag.ITEM_DROP)) {
            return;
        }
        boolean rare = r.nextDouble() < rareChance;
        ItemStack stack = look(rare);
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.key(TAG), PersistentDataType.STRING, rare ? "rare" : "normal"));
        Item item = world.dropItem(loc, stack, drop -> {
            drop.setPersistent(false);
            drop.setCanMobPickup(false);
            drop.setPickupDelay(10);
            drop.setGlowing(rare);
            drop.setVelocity(style.falling ? new Vector(0, -0.12, 0) : new Vector(0, 0.15, 0));
            drop.setUnlimitedLifetime(true);
        });
        objects.add(item);
        if (rare) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 10, 0.2, 0.2, 0.2, 0.05);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        String kind = event.getItem().getItemStack().getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING);
        if (kind == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player) || !eligible(player)) {
            return;
        }
        Location at = event.getItem().getLocation();
        event.getItem().remove();
        objects.remove(event.getItem());
        boolean rare = kind.equals("rare");
        int points = rare ? rarePoints : 1;
        addScore(player, points);
        PlayerData data = plugin.playerData().get(player);
        long coins = 0;
        if (data != null && coinsPerPickup > 0) {
            coins = plugin.economy().earn(data, Currency.COINS,
                    Math.round(coinsPerPickup * points * plugin.multipliers().global(MultiplierType.EVENTS)), false);
        }
        if (rare && rareReward != null) {
            rareReward.give(plugin, player, 1.0);
            plugin.messages().send(player, "events.collect.rare", "event", displayName(), "reward", rareReward.describe(plugin, 1.0));
        }
        SoundSpec.of(style.sound, 0.8f, rare ? 1.6f : 1.2f + ThreadLocalRandom.current().nextFloat() * 0.4f).play(player);
        at.getWorld().spawnParticle(Particle.DUST, at.add(0, 0.4, 0), 12, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(style.color, 1.2f));
        plugin.messages().actionBar(player, "events.collect.pickup", "points", points, "coins", coins,
                "total", Math.round(score(player.getUniqueId())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (event.getItem().getItemStack().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (event.getEntity().getItemStack().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void cleanup() {
        for (Item item : objects) {
            if (item.isValid()) {
                item.remove();
            }
        }
        objects.clear();
    }
}
