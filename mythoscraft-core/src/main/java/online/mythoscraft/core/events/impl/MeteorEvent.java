package online.mythoscraft.core.events.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.events.EventState;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.events.SurfaceFinder;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.reward.RewardTable;
import online.mythoscraft.core.util.Effects;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meteors are announced, fall visibly from the sky and explode on impact. The explosion goes through the normal
 * explosion events, so protected regions are never damaged. Each impact leaves a glowing meteor core that the
 * first players to reach it can claim for rewards.
 */
public final class MeteorEvent extends MythosEvent {

    private static final Particle.DustOptions WARNING = new Particle.DustOptions(Color.fromRGB(0xFF4D1A), 1.6f);

    private int meteorCount = 3;
    private int firstDelay = 15;
    private int interval = 70;
    private int warningSeconds = 15;
    private double minDistance = 25;
    private double maxDistance = 70;
    private float power = 3.0f;
    private boolean breakBlocks = true;
    private boolean fire;
    private int claimsPerCore = 3;
    private int coreLifetime = 120;
    private List<Reward> coreRewards = List.of();
    private List<Reward> rareRewards = List.of();
    private double rareChance = 0.1;
    private int firstClaimPoints = 10;
    private int claimPoints = 5;
    private SoundSpec warningSound = SoundSpec.NONE;

    private int launched;
    private int pendingTargets;
    private int nextMeteorIn;
    private final List<Incoming> incoming = new ArrayList<>();
    private final List<Core> cores = new ArrayList<>();
    private final List<BukkitRunnable> running = new ArrayList<>();

    private static final class Incoming {
        final Location target;
        int secondsLeft;

        Incoming(Location target, int secondsLeft) {
            this.target = target;
            this.secondsLeft = secondsLeft;
        }
    }

    private static final class Core {
        final ItemDisplay display;
        final Interaction hitbox;
        final TextDisplay label;
        final Location location;
        final Set<UUID> claimed = new HashSet<>();
        int claimsLeft;
        int lifetime;

        Core(ItemDisplay display, Interaction hitbox, TextDisplay label, Location location, int claims, int lifetime) {
            this.display = display;
            this.hitbox = hitbox;
            this.label = label;
            this.location = location;
            this.claimsLeft = claims;
            this.lifetime = lifetime;
        }
    }

    public MeteorEvent(MythosCraft plugin) {
        super(plugin, "meteor");
    }

    @Override
    protected int defaultDuration() {
        return 300;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        meteorCount = cfg.integer("meteors", 3, 1, 50);
        firstDelay = cfg.integer("first-delay-seconds", 15, 0, 600);
        interval = cfg.integer("interval-seconds", 70, 5, 3600);
        warningSeconds = cfg.integer("warning-seconds", 15, 3, 120);
        minDistance = cfg.decimal("distance.min", 25, 0, 1000);
        maxDistance = Math.max(minDistance + 1, cfg.decimal("distance.max", 70, 1, 2000));
        power = (float) cfg.decimal("explosion-power", 3.0, 0, 10);
        breakBlocks = cfg.bool("break-blocks", true);
        fire = cfg.bool("fire", false);
        claimsPerCore = cfg.integer("core.claims", 3, 1, 100);
        coreLifetime = cfg.integer("core.lifetime-seconds", 120, 10, 3600);
        coreRewards = RewardTable.parseList(cfg, "core.rewards");
        rareRewards = RewardTable.parseList(cfg, "core.rare-rewards");
        rareChance = cfg.decimal("core.rare-chance", 0.1, 0, 1);
        firstClaimPoints = cfg.integer("points.first-claim", 10, 0, 100_000);
        claimPoints = cfg.integer("points.claim", 5, 0, 100_000);
        SoundSpec s = SoundSpec.parse(cfg.string("sounds.warning", "block.beacon.activate 1 0.6"));
        warningSound = s == null ? SoundSpec.NONE : s;
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.points";
    }

    @Override
    protected void onStart() {
        launched = 0;
        pendingTargets = 0;
        nextMeteorIn = firstDelay;
    }

    @Override
    protected void onTick() {
        if (launched < meteorCount && --nextMeteorIn <= 0) {
            launched++;
            nextMeteorIn = interval;
            pickTarget();
        }
        for (Incoming in : new ArrayList<>(incoming)) {
            in.secondsLeft--;
            telegraph(in);
            if (in.secondsLeft <= 3 && in.secondsLeft + 1 > 3) {
                fall(in.target);
            }
            if (in.secondsLeft <= 0) {
                incoming.remove(in);
            }
        }
        for (Core core : new ArrayList<>(cores)) {
            if (--core.lifetime <= 0 || !core.display.isValid()) {
                removeCore(core, true);
            } else {
                core.location.getWorld().spawnParticle(Particle.FLAME, core.location.clone().add(0, 0.6, 0), 4, 0.3, 0.3, 0.3, 0.01);
                core.location.getWorld().spawnParticle(Particle.LAVA, core.location, 1, 0.3, 0.1, 0.3, 0);
            }
        }
    }

    private void pickTarget() {
        List<Player> players = participants();
        if (players.isEmpty()) {
            return;
        }
        Player anchor = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        World world = venue != null ? venue.getWorld() : anchor.getWorld();
        double ax = venue != null ? venue.getX() : anchor.getX();
        double az = venue != null ? venue.getZ() : anchor.getZ();
        pendingTargets++;
        SurfaceFinder.find(plugin, world, ax, az, venue != null ? 0 : minDistance, venue != null ? 45 : maxDistance, 15,
                b -> plugin.protection().query(b, Flag.EXPLOSIONS) && plugin.arenas().arenaAt(b.getLocation()) == null)
                .thenAccept(loc -> {
                    pendingTargets--;
                    if (loc == null || state() != EventState.RUNNING) {
                        return;
                    }
                    incoming.add(new Incoming(loc, warningSeconds));
                    plugin.messages().broadcast("events.meteor.warning", "x", loc.getBlockX(), "z", loc.getBlockZ(),
                            "seconds", warningSeconds, "world", world.getName());
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(loc) < 150 * 150) {
                            warningSound.play(p);
                        }
                    }
                });
    }

    private void telegraph(Incoming in) {
        World w = in.target.getWorld();
        double r = 2.5 + (warningSeconds - in.secondsLeft) * 0.2;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            w.spawnParticle(Particle.DUST, in.target.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0, 0, 0, WARNING, true);
        }
        for (double y = 0; y < 30; y += 2) {
            w.spawnParticle(Particle.DUST, in.target.clone().add(0, y, 0), 1, 0, 0, 0, 0, WARNING, true);
        }
    }

    /** Animates the meteor from the sky to the target over three seconds, then impacts. */
    private void fall(Location target) {
        World world = target.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location start = target.clone().add(rnd.nextDouble(-30, 30), 70, rnd.nextDouble(-30, 30));
        ItemDisplay rock = world.spawn(start, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(plugin.items().createUnique(CustomItem.METEOR_ROCK));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(4f);
            d.setTeleportDuration(2);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(3.5f), new Quaternionf()));
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "meteor:" + session);
        });
        SoundSpec.of("entity.ender_dragon.flap", 3f, 0.5f).playAt(target);
        BukkitRunnable task = new BukkitRunnable() {
            final int steps = 60;
            int step;

            @Override
            public void run() {
                if (!rock.isValid()) {
                    cancel();
                    return;
                }
                step++;
                double t = step / (double) steps;
                Vector pos = start.toVector().add(target.toVector().subtract(start.toVector()).multiply(t * t));
                Location loc = pos.toLocation(world);
                rock.teleport(loc);
                if (step % 5 == 0) {
                    rock.setInterpolationDelay(0);
                    rock.setInterpolationDuration(5);
                    rock.setTransformation(new Transformation(new Vector3f(),
                            new Quaternionf(new AxisAngle4f(step * 0.35f, 1, 0.4f, 0.2f).normalize()), new Vector3f(3.5f), new Quaternionf()));
                }
                world.spawnParticle(Particle.FLAME, loc, 25, 0.6, 0.6, 0.6, 0.05, null, true);
                world.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.5, 0.5, 0.5, 0.02, null, true);
                world.spawnParticle(Particle.LAVA, loc, 3, 0.4, 0.4, 0.4, 0, null, true);
                if (step >= steps) {
                    cancel();
                    rock.remove();
                    impact(target);
                }
            }
        };
        running.add(task);
        task.runTaskTimer(plugin, 1, 1);
    }

    private void impact(Location target) {
        if (state() != EventState.RUNNING) {
            return;
        }
        World world = target.getWorld();
        world.createExplosion(target, power, fire, breakBlocks);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 2, 1, 0.5, 1, 0, null, true);
        world.spawnParticle(Particle.LAVA, target, 60, 2, 1, 2, 0, null, true);
        world.spawnParticle(Particle.LARGE_SMOKE, target, 80, 2.5, 1.5, 2.5, 0.05, null, true);
        SoundSpec.of("entity.generic.explode", 4f, 0.6f).playAt(target);
        java.util.function.Supplier<Title> title = () -> Title.title(plugin.messages().get("events.meteor.impact-title"), plugin.messages().get("events.meteor.impact-subtitle"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(400)));
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(target) < 40 * 40) {
                p.showTitle(plugin.messages().in(p, title));
            }
        }
        plugin.messages().broadcast("events.meteor.impact", "x", target.getBlockX(), "z", target.getBlockZ());
        plugin.tasks().later(10, "meteor core", () -> {
            if (state() == EventState.RUNNING) {
                spawnCore(target);
            }
        });
    }

    private void spawnCore(Location impact) {
        World world = impact.getWorld();
        int y = world.getHighestBlockYAt(impact.getBlockX(), impact.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Location loc = new Location(world, impact.getBlockX() + 0.5, y + 1.0, impact.getBlockZ() + 0.5);
        String tag = "meteorcore:" + session;
        ItemDisplay display = world.spawn(loc.clone().add(0, 0.6, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(plugin.items().createUnique(CustomItem.METEOR_CORE));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0xFF7A1A));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.4f), new Quaternionf()));
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        Interaction hitbox = world.spawn(loc, Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.3f);
            i.setInteractionHeight(1.4f);
            i.setResponsive(true);
            i.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        TextDisplay label = world.spawn(loc.clone().add(0, 1.9, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        world.addPluginChunkTicket(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, plugin);
        Core core = new Core(display, hitbox, label, loc, claimsPerCore, coreLifetime);
        updateLabel(core);
        cores.add(core);
    }

    private void updateLabel(Core core) {
        core.label.text(plugin.messages().get("events.meteor.core-label", "left", core.claimsLeft));
    }

    @EventHandler
    public void onClaim(PlayerInteractEntityEvent event) {
        Core core = null;
        for (Core c : cores) {
            if (c.hitbox.equals(event.getRightClicked())) {
                core = c;
                break;
            }
        }
        if (core == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !eligible(player)) {
            return;
        }
        if (!core.claimed.add(player.getUniqueId())) {
            plugin.messages().actionBar(player, "events.meteor.already-claimed");
            return;
        }
        boolean first = core.claimed.size() == 1;
        core.claimsLeft--;
        addScore(player, first ? firstClaimPoints : claimPoints);
        double multiplier = plugin.multipliers().global(MultiplierType.EVENTS);
        Component text = Component.empty();
        for (Reward r : coreRewards) {
            r.give(plugin, player, multiplier);
            text = text.append(Component.text(" ")).append(r.describe(plugin, multiplier));
        }
        if (!rareRewards.isEmpty() && ThreadLocalRandom.current().nextDouble() < rareChance) {
            Reward rare = rareRewards.get(ThreadLocalRandom.current().nextInt(rareRewards.size()));
            rare.give(plugin, player, 1.0);
            plugin.messages().broadcast("events.meteor.rare", "player", player.getName(), "reward", rare.describe(plugin, 1.0));
        }
        plugin.messages().send(player, first ? "events.meteor.claimed-first" : "events.meteor.claimed", "rewards", text);
        Effects.burst(core.location.clone().add(0, 1, 0), Color.ORANGE, Color.YELLOW, first);
        SoundSpec.of("block.amethyst_block.resonate", 1f, 1.2f).play(player);
        if (core.claimsLeft <= 0) {
            removeCore(core, false);
        } else {
            updateLabel(core);
        }
    }

    private void removeCore(Core core, boolean expired) {
        cores.remove(core);
        if (core.display.isValid()) {
            core.display.getWorld().spawnParticle(Particle.LARGE_SMOKE, core.location, 20, 0.3, 0.3, 0.3, 0.02);
            core.display.remove();
        }
        if (core.hitbox.isValid()) {
            core.hitbox.remove();
        }
        if (core.label.isValid()) {
            core.label.remove();
        }
        core.location.getWorld().removePluginChunkTicket(core.location.getBlockX() >> 4, core.location.getBlockZ() >> 4, plugin);
        if (expired && core.claimsLeft > 0 && state() == EventState.RUNNING) {
            plugin.messages().broadcast("events.meteor.core-expired");
        }
    }

    @Override
    protected boolean isFinished() {
        return launched >= meteorCount && pendingTargets == 0 && incoming.isEmpty() && cores.isEmpty() && running.stream().allMatch(BukkitRunnable::isCancelled);
    }

    @Override
    protected Component bossBarText() {
        return plugin.messages().get("events.meteor.bossbar", "event", displayName(), "launched", launched,
                "total", meteorCount, "time", online.mythoscraft.core.util.Durations.clock(secondsLeft()));
    }

    @Override
    protected void cleanup() {
        for (BukkitRunnable r : running) {
            if (!r.isCancelled()) {
                r.cancel();
            }
        }
        running.clear();
        incoming.clear();
        for (Core c : new ArrayList<>(cores)) {
            removeCore(c, false);
        }
        for (World w : Bukkit.getWorlds()) {
            w.getEntitiesByClass(ItemDisplay.class).stream()
                    .filter(e -> ("meteor:" + session).equals(e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING)))
                    .forEach(org.bukkit.entity.Entity::remove);
        }
    }
}
