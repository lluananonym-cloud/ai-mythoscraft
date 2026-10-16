package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Personal boosters as items: coin, gem, job-XP and find boosters (only for the player who uses them, stackable in
 * time), the mutation serum (a random mutation for a while: giant, dwarf, cat, Midas, lucky mushroom, feather) and
 * the personal meteor shower (meteorites crash around you and leave ores behind – only you can pick them up first).
 */
public final class BoosterService implements Listener {

    public enum Type { COINS, GEMS, XP, FIND }

    public enum Mutation { GIANT, DWARF, CAT, MIDAS, LUCKY, FEATHER }

    private final MythosCraft plugin;
    private final NamespacedKey modifierKey;
    private int boosterMinutes = 30;
    private int maxHours = 3;
    private int mutationMinutes = 20;
    private double coinFactor = 2;
    private double gemFactor = 1.5;
    private double xpFactor = 2;
    private double findFactor = 3;
    private int meteors = 6;

    public BoosterService(MythosCraft plugin) {
        this.plugin = plugin;
        this.modifierKey = Keys.key("mutation");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("boosters");
        boosterMinutes = cfg.integer("personal.minutes", 30, 1, 1440);
        maxHours = cfg.integer("personal.max-hours", 3, 1, 48);
        coinFactor = cfg.decimal("personal.coins", 2, 1, 10);
        gemFactor = cfg.decimal("personal.gems", 1.5, 1, 10);
        xpFactor = cfg.decimal("personal.job-xp", 2, 1, 10);
        findFactor = cfg.decimal("personal.find", 3, 1, 20);
        mutationMinutes = cfg.integer("mutation.minutes", 20, 1, 600);
        meteors = cfg.integer("meteor-shower.meteors", 6, 1, 30);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 20, "personal boosters", this::tick);
    }

    // ---- personal boosters --------------------------------------------------------------------------------

    private static String key(Type t) {
        return "booster." + t.name().toLowerCase(Locale.ROOT) + ".until";
    }

    private long until(UUID player, Type t) {
        Player p = plugin.getServer().getPlayer(player);
        return p == null || !plugin.progress().loaded(p) ? 0 : plugin.progress().get(p, key(t));
    }

    public boolean active(UUID player, Type t) {
        return until(player, t) > System.currentTimeMillis();
    }

    /** Factor of an active personal booster (1 if none). */
    public double factor(UUID player, Type t) {
        if (!active(player, t)) {
            return 1;
        }
        return switch (t) {
            case COINS -> coinFactor;
            case GEMS -> gemFactor;
            case XP -> xpFactor;
            case FIND -> findFactor;
        };
    }

    /** For the multiplier service: personal coin / gem booster. */
    public double factor(UUID player, MultiplierType type) {
        return switch (type) {
            case COINS -> factor(player, Type.COINS);
            case GEMS -> factor(player, Type.GEMS);
            default -> 1;
        };
    }

    private void start(Player p, Type t) {
        long now = System.currentTimeMillis();
        long from = Math.max(now, plugin.progress().get(p, key(t)));
        long to = Math.min(now + maxHours * 3_600_000L, from + Math.round(boosterMinutes * 60_000L * plugin.rankPerks().boosterTimeFactor(p)));
        plugin.progress().set(p, key(t), to);
        long minutes = Math.max(1, (to - now) / 60_000);
        plugin.messages().send(p, "boosters.started", "booster", plugin.messages().get("boosters.names." + t.name().toLowerCase(Locale.ROOT)),
                "minutes", minutes);
        SoundSpec.of("block.beacon.activate", 1f, 1.6f).play(p);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.2);
    }

    // ---- mutations ----------------------------------------------------------------------------------------

    public Mutation mutation(Player p) {
        if (!plugin.progress().loaded(p) || plugin.progress().get(p, "mutation.until") <= System.currentTimeMillis()) {
            return null;
        }
        long idx = plugin.progress().get(p, "mutation.kind");
        return idx >= 0 && idx < Mutation.values().length ? Mutation.values()[(int) idx] : null;
    }

    private void mutate(Player p) {
        Mutation[] all = Mutation.values();
        Mutation old = mutation(p);
        Mutation m = all[ThreadLocalRandom.current().nextInt(all.length)];
        if (old != null && all.length > 1) {
            while (m == old) {
                m = all[ThreadLocalRandom.current().nextInt(all.length)];
            }
        }
        clearMutation(p);
        plugin.progress().set(p, "mutation.kind", m.ordinal());
        plugin.progress().set(p, "mutation.until", System.currentTimeMillis() + mutationMinutes * 60_000L);
        applyMutation(p, m);
        plugin.messages().send(p, "boosters.mutation." + m.name().toLowerCase(Locale.ROOT), "minutes", mutationMinutes);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(0x84cc16), 1.4f));
        SoundSpec.of("entity.zombie_villager.cure", 0.8f, 1.4f).play(p);
    }

    private void modifier(Player p, Attribute a, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst != null) {
            inst.removeModifier(modifierKey);
            inst.addModifier(new AttributeModifier(modifierKey, amount, op, EquipmentSlotGroup.ANY));
        }
    }

    private void applyMutation(Player p, Mutation m) {
        switch (m) {
            case GIANT -> {
                modifier(p, Attribute.SCALE, 0.4, AttributeModifier.Operation.ADD_SCALAR);
                modifier(p, Attribute.MAX_HEALTH, 6, AttributeModifier.Operation.ADD_NUMBER);
                modifier(p, Attribute.BLOCK_INTERACTION_RANGE, 1.5, AttributeModifier.Operation.ADD_NUMBER);
            }
            case DWARF -> {
                modifier(p, Attribute.SCALE, -0.4, AttributeModifier.Operation.ADD_SCALAR);
                modifier(p, Attribute.MOVEMENT_SPEED, 0.25, AttributeModifier.Operation.ADD_SCALAR);
            }
            case CAT -> {
                modifier(p, Attribute.SAFE_FALL_DISTANCE, 20, AttributeModifier.Operation.ADD_NUMBER);
                modifier(p, Attribute.JUMP_STRENGTH, 0.15, AttributeModifier.Operation.ADD_NUMBER);
            }
            case FEATHER -> modifier(p, Attribute.GRAVITY, -0.5, AttributeModifier.Operation.ADD_SCALAR);
            default -> {
            }
        }
    }

    private void clearMutation(Player p) {
        for (Attribute a : new Attribute[]{Attribute.SCALE, Attribute.MAX_HEALTH, Attribute.BLOCK_INTERACTION_RANGE, Attribute.MOVEMENT_SPEED,
                Attribute.SAFE_FALL_DISTANCE, Attribute.JUMP_STRENGTH, Attribute.GRAVITY}) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null) {
                inst.removeModifier(modifierKey);
            }
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) {
            p.setHealth(hp.getValue());
        }
    }

    /** Midas: +50 % coins, lucky mushroom: gem finds ×2 (used by the multiplier service and the jobs). */
    public double mutationFactor(UUID player, MultiplierType type) {
        Player p = plugin.getServer().getPlayer(player);
        return p != null && type == MultiplierType.COINS && mutation(p) == Mutation.MIDAS ? 1.5 : 1;
    }

    public double findFactor(Player p) {
        return factor(p.getUniqueId(), Type.FIND) * (mutation(p) == Mutation.LUCKY ? 2 : 1);
    }

    // ---- meteor shower ------------------------------------------------------------------------------------

    private void meteorShower(Player p) {
        plugin.messages().send(p, "boosters.meteor-start", "meteors", meteors);
        for (int i = 0; i < meteors; i++) {
            plugin.tasks().later(20L + i * 30L + ThreadLocalRandom.current().nextInt(15), "personal meteor", () -> {
                if (p.isOnline()) {
                    meteor(p);
                }
            });
        }
    }

    private void meteor(Player p) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World w = p.getWorld();
        double a = rnd.nextDouble(Math.PI * 2);
        double r = rnd.nextDouble(4, 14);
        int x = (int) Math.floor(p.getX() + Math.cos(a) * r);
        int z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
        Location impact = w.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
        if (Math.abs(impact.getY() - p.getY()) > 12) {
            impact = p.getLocation().clone().add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
        }
        Location from = impact.clone().add(rnd.nextDouble(-12, 12), 30, rnd.nextDouble(-12, 12));
        final Location hit = impact;
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            Location step = from.clone().add(hit.clone().subtract(from).toVector().multiply(t));
            plugin.tasks().later(i, "meteor trail", () -> {
                w.spawnParticle(Particle.FLAME, step, 6, 0.2, 0.2, 0.2, 0.02, null, true);
                w.spawnParticle(Particle.LARGE_SMOKE, step, 2, 0.1, 0.1, 0.1, 0.01, null, true);
            });
        }
        SoundSpec.of("entity.ghast.shoot", 1f, 0.6f).playAt(from);
        plugin.tasks().later(21, "meteor impact", () -> {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, hit, 1);
            w.spawnParticle(Particle.LAVA, hit, 20, 0.6, 0.3, 0.6, 0);
            SoundSpec.of("entity.generic.explode", 1.2f, 0.9f).playAt(hit);
            for (ItemStack loot : loot(rnd)) {
                w.dropItem(hit, loot, drop -> {
                    drop.setOwner(p.getUniqueId());
                    drop.setGlowing(true);
                    drop.setVelocity(new org.bukkit.util.Vector(rnd.nextDouble(-0.2, 0.2), 0.4, rnd.nextDouble(-0.2, 0.2)));
                });
            }
            if (rnd.nextDouble() < 0.3) {
                PlayerData data = plugin.playerData().get(p);
                if (data != null) {
                    long gems = plugin.economy().earn(data, Currency.GEMS, 1 + rnd.nextInt(2), false);
                    plugin.messages().actionBar(p, "boosters.meteor-gems", "gems", gems);
                }
            }
        });
    }

    private static List<ItemStack> loot(ThreadLocalRandom rnd) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Material.IRON_INGOT, 2 + rnd.nextInt(4)));
        if (rnd.nextBoolean()) {
            out.add(new ItemStack(Material.GOLD_INGOT, 1 + rnd.nextInt(3)));
        }
        if (rnd.nextDouble() < 0.35) {
            out.add(new ItemStack(Material.DIAMOND, 1 + rnd.nextInt(2)));
        }
        if (rnd.nextDouble() < 0.3) {
            out.add(new ItemStack(Material.EMERALD, 1 + rnd.nextInt(3)));
        }
        if (rnd.nextDouble() < 0.4) {
            out.add(new ItemStack(Material.AMETHYST_SHARD, 3 + rnd.nextInt(5)));
        }
        if (rnd.nextDouble() < 0.06) {
            out.add(new ItemStack(Material.ANCIENT_DEBRIS, 1));
        }
        return out;
    }

    // ---- items --------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        CustomItem type = plugin.items().identify(item);
        if (type == null) {
            return;
        }
        Player p = event.getPlayer();
        Type booster = switch (type) {
            case COIN_BOOSTER -> Type.COINS;
            case GEM_BOOSTER -> Type.GEMS;
            case XP_BOOSTER -> Type.XP;
            case FIND_BOOSTER -> Type.FIND;
            default -> null;
        };
        if (booster != null) {
            event.setCancelled(true);
            AbilitySupport.consumeOne(item);
            start(p, booster);
        } else if (type == CustomItem.FLY_BOOSTER) {
            event.setCancelled(true);
            AbilitySupport.consumeOne(item);
            plugin.rankPerks().addFlyBooster(p, 30);
            long minutes = (plugin.rankPerks().flyBoosterLeft(p) + 59_999) / 60_000;
            plugin.messages().send(p, "rank-perks.fly-booster-on", "minutes", minutes);
            online.mythoscraft.core.util.SoundSpec.of("entity.phantom.flap", 0.8f, 1.4f).play(p);
        } else if (type == CustomItem.MUTATION_SERUM) {
            event.setCancelled(true);
            AbilitySupport.consumeOne(item);
            mutate(p);
        } else if (type == CustomItem.METEOR_SHOWER) {
            event.setCancelled(true);
            if (plugin.arenas().isInArena(p) || plugin.attractionStore().inside(p.getLocation(), 10)) {
                plugin.messages().send(p, "boosters.meteor-not-here");
                return;
            }
            AbilitySupport.consumeOne(item);
            meteorShower(p);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.progress().loaded(p)) {
                continue;
            }
            long mu = plugin.progress().get(p, "mutation.until");
            if (mu > 0 && mu <= now) {
                plugin.progress().set(p, "mutation.until", 0);
                clearMutation(p);
                plugin.messages().send(p, "boosters.mutation-ended");
            }
            Mutation m = mutation(p);
            if (m == Mutation.CAT) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 15, 0, true, false, false));
            }
            for (Type t : Type.values()) {
                long until = plugin.progress().get(p, key(t));
                if (until > 0 && until <= now) {
                    plugin.progress().set(p, key(t), 0);
                    plugin.messages().send(p, "boosters.ended", "booster", plugin.messages().get("boosters.names." + t.name().toLowerCase(Locale.ROOT)));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && event.getCause() == EntityDamageEvent.DamageCause.FALL && mutation(p) == Mutation.FEATHER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(40, "mutation restore", () -> {
            if (!p.isOnline()) {
                return;
            }
            clearMutation(p);
            Mutation m = mutation(p);
            if (m != null) {
                applyMutation(p, m);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearMutation(event.getPlayer());
    }

    /** Lines for /booster: what is active and how long. */
    public void info(Player p) {
        long now = System.currentTimeMillis();
        plugin.messages().send(p, "boosters.info-header");
        boolean any = false;
        for (Type t : Type.values()) {
            long until = plugin.progress().get(p, key(t));
            if (until > now) {
                any = true;
                plugin.messages().sendRaw(p, "boosters.info-line", "booster", plugin.messages().get("boosters.names." + t.name().toLowerCase(Locale.ROOT)),
                        "minutes", Math.max(1, (until - now) / 60_000));
            }
        }
        Mutation m = mutation(p);
        if (m != null) {
            any = true;
            plugin.messages().sendRaw(p, "boosters.info-line", "booster", plugin.messages().get("boosters.mutation-names." + m.name().toLowerCase(Locale.ROOT)),
                    "minutes", Math.max(1, (plugin.progress().get(p, "mutation.until") - now) / 60_000));
        }
        if (!any) {
            plugin.messages().sendRaw(p, "boosters.info-none");
        }
    }
}
