package online.mythoscraft.core.item;

import io.papermc.paper.entity.TeleportFlag;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rideable witch broom. The player sits on the broom (invisible marker seat + item display of the broom model)
 * and steers with the view direction: W accelerate, S brake, Space climb, Ctrl turbo, Shift land.
 * Tricks: left click = barrel roll, double Space = loop, Ctrl + Space = corkscrew. Tricks in a row build a combo.
 */
public final class BroomRide implements Listener {

    private enum Trick { NONE, ROLL, LOOP, SPIN }

    private final class Ride {
        final Player player;
        final ArmorStand seat;
        final ItemDisplay broom;
        final long started = System.currentTimeMillis();
        Vector velocity = new Vector();
        Input input;
        long lastJumpTick = -100;
        long tick;
        Trick trick = Trick.NONE;
        int trickTick;
        int combo;
        long lastTrickTick = -1000;
        int points;
        boolean wasSprinting;

        Ride(Player player, ArmorStand seat, ItemDisplay broom) {
            this.player = player;
            this.seat = seat;
            this.broom = broom;
        }
    }

    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(0x9333EA), 1.1f);
    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(0xFF7A1A), 1.1f);

    private final MythosCraft plugin;
    private final AbilitySupport support;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private double speed = 0.65;
    private double turbo = 1.05;
    private int maxSeconds = 120;
    private double seatOffset = 0.62;
    private double cooldown = 5;

    public BroomRide(MythosCraft plugin, AbilitySupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("items.witch-broom");
        speed = cfg.decimal("speed", 0.65, 0.1, 3);
        turbo = cfg.decimal("turbo-speed", 1.05, 0.1, 4);
        maxSeconds = cfg.integer("max-flight-seconds", 120, 5, 3600);
        seatOffset = cfg.decimal("seat-offset", 0.62, -2, 3);
        cooldown = cfg.decimal("cooldown-seconds", 5, 0, 600);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 1, 1, "broom flight", this::tick);
    }

    public boolean riding(Player p) {
        return rides.containsKey(p.getUniqueId());
    }

    /** Right-click with the broom: mount, or land when already flying. */
    public void toggle(Player p, ItemStack item) {
        if (riding(p)) {
            land(p, true);
            return;
        }
        if (p.isInsideVehicle() || p.isGliding() || plugin.arenas().isInArena(p) || plugin.tournaments().isParticipant(p)) {
            plugin.messages().actionBar(p, "abilities.broom-blocked");
            return;
        }
        if (plugin.combat().remainingSeconds(p) > 0) {
            plugin.messages().actionBar(p, "abilities.broom-combat");
            return;
        }
        if (support.onCooldown(p, item, "witch_broom", cooldown)) {
            return;
        }
        Location at = p.getLocation().add(0, 0.6, 0);
        at.setPitch(0);
        World w = p.getWorld();
        ArmorStand seat = w.spawn(at, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setPersistent(false);
            s.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "broom_seat");
        });
        ItemDisplay broom = w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemBuilder(Material.BLAZE_ROD).model("witch_broom_ride").build());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setBrightness(new Display.Brightness(15, 15));
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "broom_display");
        });
        seat.addPassenger(p);
        seat.addPassenger(broom);
        Ride ride = new Ride(p, seat, broom);
        ride.velocity = p.getLocation().getDirection().setY(0.3).multiply(0.3);
        rides.put(p.getUniqueId(), ride);
        orient(ride, 0, 0, 0);
        w.spawnParticle(Particle.WITCH, at, 40, 0.5, 0.3, 0.5, 0.1);
        SoundSpec.of("entity.phantom.flap", 1f, 1.2f).playAt(at);
        plugin.messages().send(p, "abilities.broom-help");
    }

    private void tick() {
        if (rides.isEmpty()) {
            return;
        }
        for (Ride ride : rides.values().toArray(new Ride[0])) {
            step(ride);
        }
    }

    private void step(Ride r) {
        Player p = r.player;
        if (!p.isOnline() || !r.seat.isValid() || p.getVehicle() != r.seat) {
            land(p, false);
            return;
        }
        r.tick++;
        if ((System.currentTimeMillis() - r.started) / 1000 >= maxSeconds) {
            plugin.messages().actionBar(p, "abilities.broom-tired");
            land(p, true);
            return;
        }
        Input in = p.getCurrentInput();
        Vector look = p.getLocation().getDirection();
        double max = in.isSprint() ? turbo : speed;
        Vector wanted;
        if (in.isForward()) {
            wanted = look.clone().multiply(max);
        } else if (in.isBackward()) {
            wanted = r.velocity.clone().multiply(0.6);
        } else {
            wanted = r.velocity.clone().multiply(0.94);
        }
        if (in.isLeft() || in.isRight()) {
            Vector side = new Vector(-look.getZ(), 0, look.getX()).normalize().multiply(in.isLeft() ? -0.25 : 0.25);
            wanted.add(side);
        }
        if (in.isJump()) {
            wanted.setY(Math.max(wanted.getY(), 0.35));
        }
        r.velocity = r.velocity.multiply(0.75).add(wanted.multiply(0.25));
        if (r.velocity.lengthSquared() < 0.01 && !in.isJump()) {
            r.velocity.setY(r.velocity.getY() - 0.02);
        }
        if (r.trick == Trick.LOOP) {
            double phase = r.trickTick / 16.0 * Math.PI * 2;
            r.velocity.setY(r.velocity.getY() + Math.cos(phase) * 0.12);
        }
        Location from = r.seat.getLocation();
        Location to = from.clone().add(r.velocity);
        World w = from.getWorld();
        if (to.getY() > w.getMaxHeight() - 3) {
            to.setY(w.getMaxHeight() - 3);
            r.velocity.setY(0);
        }
        if (blocked(to) || blocked(to.clone().add(0, 1.8, 0))) {
            if (r.velocity.getY() < -0.05 && blocked(from.clone().add(0, -0.3, 0))) {
                land(p, true);
                return;
            }
            r.velocity.multiply(-0.2);
            to = from.clone();
        }
        if (!w.getWorldBorder().isInside(to)) {
            r.velocity.multiply(-0.5);
            to = from.clone();
        }
        to.setYaw(0);
        to.setPitch(0);
        r.seat.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        p.setFallDistance(0);

        float roll = 0;
        float pitchExtra = 0;
        float yawExtra = 0;
        if (r.trick != Trick.NONE) {
            r.trickTick++;
            int len = r.trick == Trick.LOOP ? 16 : 12;
            float progress = Math.min(1f, r.trickTick / (float) len);
            switch (r.trick) {
                case ROLL -> roll = progress * 360f;
                case LOOP -> pitchExtra = -progress * 360f;
                case SPIN -> yawExtra = progress * 360f;
                default -> {
                }
            }
            w.spawnParticle(Particle.FIREWORK, to.clone().add(0, 0.6, 0), 3, 0.3, 0.3, 0.3, 0.02);
            if (r.trickTick >= len) {
                finishTrick(r);
            }
        }
        orient(r, roll, pitchExtra, yawExtra);
        if (r.velocity.lengthSquared() > 0.04) {
            Location tail = to.clone().add(0, seatOffset, 0).subtract(look.clone().multiply(1.1));
            w.spawnParticle(Particle.DUST, tail, 2, 0.08, 0.08, 0.08, 0, r.tick % 2 == 0 ? PURPLE : ORANGE);
            if (r.tick % 3 == 0) {
                w.spawnParticle(Particle.WITCH, tail, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        if (in.isSprint() && !r.wasSprinting && r.trick == Trick.NONE) {
            SoundSpec.of("entity.breeze.shoot", 0.6f, 1.4f).playAt(to);
        }
        r.wasSprinting = in.isSprint();
        if (r.tick % 20 == 0 && r.trick == Trick.NONE) {
            long left = maxSeconds - (System.currentTimeMillis() - r.started) / 1000;
            plugin.messages().actionBar(p, "abilities.broom-status", "speed", Math.round(r.velocity.length() * 20 * 3.6),
                    "time", left, "points", r.points);
        }
    }

    private static boolean blocked(Location l) {
        Block b = l.getBlock();
        return !b.isPassable() || b.isLiquid() && b.getType() == Material.LAVA;
    }

    /** Lays the broom along the flight direction (model is built along +Z) with optional trick rotations. */
    private void orient(Ride r, float roll, float pitchExtra, float yawExtra) {
        Location eye = r.player.getLocation();
        float yaw = eye.getYaw() + yawExtra;
        float pitch = Math.max(-45, Math.min(45, eye.getPitch() * 0.6f)) + pitchExtra;
        Quaternionf q = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll));
        r.broom.setInterpolationDelay(0);
        r.broom.setTransformation(new Transformation(new Vector3f(0, (float) seatOffset, 0), q, new Vector3f(1.1f, 1.1f, 1.1f),
                new Quaternionf(new AxisAngle4f())));
    }

    private void startTrick(Ride r, Trick trick) {
        if (r.trick != Trick.NONE) {
            return;
        }
        r.trick = trick;
        r.trickTick = 0;
        String sound = switch (trick) {
            case ROLL -> "entity.player.attack.sweep";
            case LOOP -> "entity.firework_rocket.launch";
            default -> "entity.breeze.wind_burst";
        };
        SoundSpec.of(sound, 0.8f, 1.3f).playAt(r.seat.getLocation());
        if (trick == Trick.SPIN) {
            r.velocity.add(r.player.getLocation().getDirection().multiply(0.5));
        }
    }

    private void finishTrick(Ride r) {
        Trick done = r.trick;
        r.trick = Trick.NONE;
        r.combo = r.tick - r.lastTrickTick <= 50 ? r.combo + 1 : 1;
        r.lastTrickTick = r.tick;
        int base = switch (done) {
            case ROLL -> 10;
            case LOOP -> 25;
            default -> 15;
        };
        int gained = base * r.combo;
        r.points += gained;
        plugin.messages().actionBar(r.player, "abilities.broom-trick", "trick", plugin.messages().get("abilities.broom-tricks." + done.name().toLowerCase()),
                "points", gained, "combo", r.combo, "total", r.points);
        Location at = r.seat.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 10 + 5 * Math.min(r.combo, 6), 0.4, 0.4, 0.4, 0.25);
        SoundSpec.of("entity.experience_orb.pickup", 0.7f, 0.8f + 0.1f * Math.min(r.combo, 8)).play(r.player);
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Ride r = rides.get(event.getPlayer().getUniqueId());
        if (r == null) {
            return;
        }
        Input now = event.getInput();
        Input before = r.input;
        r.input = now;
        boolean jumpPressed = now.isJump() && (before == null || !before.isJump());
        if (!jumpPressed) {
            return;
        }
        if (now.isSprint()) {
            startTrick(r, Trick.SPIN);
        } else if (r.tick - r.lastJumpTick <= 7) {
            startTrick(r, Trick.LOOP);
        }
        r.lastJumpTick = r.tick;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            Ride r = rides.get(event.getPlayer().getUniqueId());
            if (r != null) {
                startTrick(r, Trick.ROLL);
            }
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p && rides.containsKey(p.getUniqueId()) && event.getDismounted() == rides.get(p.getUniqueId()).seat) {
            plugin.tasks().later(1, "broom dismount", () -> land(p, true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        Ride r = rides.get(event.getPlayer().getUniqueId());
        if (r != null && (!event.getTo().getWorld().equals(r.seat.getWorld())
                || event.getTo().distanceSquared(r.seat.getLocation()) > 36)) {
            land(event.getPlayer(), false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && rides.containsKey(p.getUniqueId())
                && event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        land(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        land(event.getPlayer(), false);
    }

    /** Ends the ride: removes seat and display, protects the player from fall damage. */
    public void land(Player p, boolean gently) {
        Ride r = rides.remove(p.getUniqueId());
        if (r == null) {
            return;
        }
        if (p.getVehicle() == r.seat) {
            r.seat.removePassenger(p);
        }
        for (Entity e : new Entity[]{r.broom, r.seat}) {
            if (e.isValid()) {
                e.remove();
            }
        }
        p.setFallDistance(0);
        if (gently && p.isOnline()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false));
            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 0.5, 0), 25, 0.4, 0.2, 0.4, 0.05);
            SoundSpec.of("entity.phantom.flap", 0.8f, 0.8f).playAt(p.getLocation());
            if (r.points > 0) {
                plugin.messages().send(p, "abilities.broom-score", "points", r.points);
            }
        }
    }

    public void shutdown() {
        for (Ride r : rides.values().toArray(new Ride[0])) {
            land(r.player, true);
        }
    }
}
