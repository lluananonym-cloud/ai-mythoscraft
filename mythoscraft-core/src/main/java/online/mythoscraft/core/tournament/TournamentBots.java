package online.mythoscraft.core.tournament;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Kit;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.Keys;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test opponents for admins. Mannequins (player-shaped, real skins) are steered by a small melee AI; zombies can be
 * configured instead and use the vanilla pathfinding.
 */
public final class TournamentBots {

    public static final NamespacedKey BOT = Keys.key("tournament_bot");

    private final MythosCraft plugin;
    private final Map<UUID, LivingEntity> bots = new HashMap<>();
    private final Map<UUID, Integer> attackCooldown = new HashMap<>();
    private EntityType type = EntityType.MANNEQUIN;
    private double health = 20;
    private double damage = 4;
    private double speed = 0.28;
    private List<String> names = List.of("Notch", "jeb_", "Dinnerbone", "Grumm", "Technoblade", "Dream", "Steve", "Alex");

    public TournamentBots(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader cfg) {
        String t = cfg.string("entity", "MANNEQUIN").toUpperCase();
        type = t.equals("ZOMBIE") ? EntityType.ZOMBIE : EntityType.MANNEQUIN;
        health = cfg.decimal("health", 20, 1, 1000);
        damage = cfg.decimal("damage", 4, 0, 100);
        speed = cfg.decimal("speed", 0.28, 0.05, 1.5);
        List<String> configured = cfg.strings("skins");
        if (!configured.isEmpty()) {
            names = configured;
        }
    }

    public LivingEntity spawn(Location at, int number, Kit kit, String tournamentId) {
        String skin = names.get(ThreadLocalRandom.current().nextInt(names.size()));
        Component name = Component.text("Bot #" + number, NamedTextColor.GRAY);
        LivingEntity bot;
        if (type == EntityType.ZOMBIE) {
            bot = at.getWorld().spawn(at, Zombie.class, z -> {
                z.setAdult();
                z.setShouldBurnInDay(false);
                z.setCanPickupItems(false);
                setup(z, name, kit, tournamentId);
            });
        } else {
            bot = at.getWorld().spawn(at, Mannequin.class, m -> {
                m.setProfile(ResolvableProfile.resolvableProfile().name(skin).build());
                m.setImmovable(false);
                m.setDescription(Component.text("Turnier-Bot", NamedTextColor.DARK_GRAY));
                setup(m, name, kit, tournamentId);
            });
        }
        bots.put(bot.getUniqueId(), bot);
        return bot;
    }

    private void setup(LivingEntity e, Component name, Kit kit, String tournamentId) {
        e.customName(name);
        e.setCustomNameVisible(true);
        e.setPersistent(false);
        e.setRemoveWhenFarAway(false);
        e.getPersistentDataContainer().set(BOT, PersistentDataType.STRING, tournamentId);
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
        }
        e.setHealth(health);
        EntityEquipment eq = e.getEquipment();
        if (eq != null && kit != null) {
            eq.setHelmet(copy(kit.helmet()));
            eq.setChestplate(copy(kit.chestplate()));
            eq.setLeggings(copy(kit.leggings()));
            eq.setBoots(copy(kit.boots()));
            eq.setItemInMainHand(kit.weapon());
            eq.setItemInOffHand(copy(kit.offhand()));
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setLeggingsDropChance(0);
            eq.setBootsDropChance(0);
            eq.setItemInMainHandDropChance(0);
            eq.setItemInOffHandDropChance(0);
        }
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    public boolean isBot(LivingEntity e) {
        return bots.containsKey(e.getUniqueId());
    }

    public Collection<LivingEntity> all() {
        return bots.values();
    }

    public void forget(UUID id) {
        bots.remove(id);
        attackCooldown.remove(id);
    }

    /** Moves every bot towards its closest opponent and attacks in melee range. */
    public void tick(Collection<LivingEntity> fighters) {
        for (LivingEntity bot : new ArrayList<>(bots.values())) {
            if (!bot.isValid() || bot.isDead()) {
                continue;
            }
            LivingEntity target = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity f : fighters) {
                if (f.equals(bot) || f.isDead() || !f.getWorld().equals(bot.getWorld())) {
                    continue;
                }
                double d = f.getLocation().distanceSquared(bot.getLocation());
                if (d < best) {
                    best = d;
                    target = f;
                }
            }
            if (target == null) {
                continue;
            }
            if (bot instanceof Mob mob) {
                if (mob.getTarget() != target) {
                    mob.setTarget(target);
                }
                continue;
            }
            steer(bot, target, Math.sqrt(best));
        }
    }

    private void steer(LivingEntity bot, LivingEntity target, double distance) {
        Location from = bot.getLocation();
        Vector to = target.getLocation().toVector().subtract(from.toVector());
        Location facing = from.clone().setDirection(to);
        bot.setRotation(facing.getYaw(), facing.getPitch());
        int cd = attackCooldown.getOrDefault(bot.getUniqueId(), 0);
        if (cd > 0) {
            attackCooldown.put(bot.getUniqueId(), cd - 1);
        }
        if (distance < 2.8) {
            if (cd <= 0) {
                target.damage(damage, bot);
                bot.swingMainHand();
                Vector kb = to.clone().setY(0);
                if (kb.lengthSquared() > 0.001) {
                    target.setVelocity(target.getVelocity().add(kb.normalize().multiply(0.35).setY(0.25)));
                }
                attackCooldown.put(bot.getUniqueId(), 5 + ThreadLocalRandom.current().nextInt(3));
            }
            if (distance > 1.6) {
                move(bot, to, 0.5);
            }
            return;
        }
        move(bot, to, 1.0);
    }

    private void move(LivingEntity bot, Vector direction, double factor) {
        Vector horizontal = direction.clone().setY(0);
        if (horizontal.lengthSquared() < 0.001) {
            return;
        }
        horizontal.normalize().multiply(speed * factor);
        Vector velocity = bot.getVelocity();
        double y = velocity.getY();
        Block ahead = bot.getLocation().add(horizontal.clone().normalize().multiply(0.8)).getBlock();
        if (bot.isOnGround() && !ahead.isPassable()) {
            y = 0.42;
        }
        bot.setVelocity(new Vector(horizontal.getX(), y, horizontal.getZ()));
    }

    public void removeAll() {
        for (LivingEntity bot : bots.values()) {
            if (bot.isValid()) {
                bot.remove();
            }
        }
        bots.clear();
        attackCooldown.clear();
    }
}
