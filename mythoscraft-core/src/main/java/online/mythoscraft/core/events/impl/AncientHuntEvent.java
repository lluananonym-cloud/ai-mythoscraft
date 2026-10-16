package online.mythoscraft.core.events.impl;

import net.kyori.adventure.text.format.NamedTextColor;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.MultiplierType;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.events.SurfaceFinder;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Effects;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ancient relics are hidden at random surface spots around a center. They glow, send a light beam into the sky
 * and players near one get warmer/colder hints in the action bar; the Relikt-Kompass points to the nearest one.
 * Each find gives points and coins; the event ends when all relics are found or the time is up.
 */
public final class AncientHuntEvent extends MythosEvent {

    private String worldName = "";
    private String center = "spawn";
    private int relicCount = 8;
    private double minRadius = 40;
    private double maxRadius = 250;
    private int pointsPerRelic = 10;
    private long coinsPerRelic = 100;
    private double hintRadius = 60;
    private double collectRadius = 2.2;
    private boolean beam = true;
    private SoundSpec findSound = SoundSpec.NONE;

    private final List<Relic> relics = new ArrayList<>();
    private final Map<UUID, Location> compassTargets = new HashMap<>();
    private int placedTotal;
    private int pending;
    private int foundTotal;
    private int animationTick;

    private static final class Relic {
        final ItemDisplay display;
        final Location location;
        final int chunkX;
        final int chunkZ;
        float angle;

        Relic(ItemDisplay display, Location location) {
            this.display = display;
            this.location = location;
            this.chunkX = location.getBlockX() >> 4;
            this.chunkZ = location.getBlockZ() >> 4;
        }
    }

    public AncientHuntEvent(MythosCraft plugin) {
        super(plugin, "ancienthunt");
    }

    @Override
    protected int defaultDuration() {
        return 600;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        worldName = cfg.string("world", "");
        center = cfg.string("center", "spawn");
        relicCount = cfg.integer("relics", 8, 1, 100);
        minRadius = cfg.decimal("radius.min", 40, 0, 10_000);
        maxRadius = Math.max(minRadius + 1, cfg.decimal("radius.max", 250, 1, 20_000));
        pointsPerRelic = cfg.integer("points-per-relic", 10, 0, 100_000);
        coinsPerRelic = cfg.longValue("coins-per-relic", 100, 0, 1_000_000_000L);
        hintRadius = cfg.decimal("hint-radius", 60, 0, 1000);
        collectRadius = cfg.decimal("collect-radius", 2.2, 0.5, 10);
        beam = cfg.bool("beam", true);
        SoundSpec s = SoundSpec.parse(cfg.string("sounds.find", "entity.player.levelup 1 0.8"));
        findSound = s == null ? SoundSpec.NONE : s;
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.relics";
    }

    @Override
    protected String formatScore(double score) {
        return String.valueOf(Math.round(score / Math.max(1, pointsPerRelic)));
    }

    @Override
    protected void onStart() {
        relics.clear();
        placedTotal = 0;
        foundTotal = 0;
        World world = worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
        Location base = plugin.spawn().spawn();
        if (world == null) {
            world = base != null ? base.getWorld() : Bukkit.getWorlds().get(0);
        }
        double cx;
        double cz;
        if (center.equalsIgnoreCase("spawn") && base != null && base.getWorld().equals(world)) {
            cx = base.getX();
            cz = base.getZ();
        } else {
            String[] parts = center.split(",");
            try {
                cx = Double.parseDouble(parts[0].trim());
                cz = Double.parseDouble(parts[1].trim());
            } catch (RuntimeException e) {
                cx = world.getSpawnLocation().getX();
                cz = world.getSpawnLocation().getZ();
            }
        }
        if (venue != null) {
            world = venue.getWorld();
            cx = venue.getX();
            cz = venue.getZ();
        }
        pending = relicCount;
        World w = world;
        for (int i = 0; i < relicCount; i++) {
            SurfaceFinder.find(plugin, w, cx, cz, minRadius, maxRadius, 12, b -> plugin.regions().top(b.getLocation()) == null)
                    .thenAccept(loc -> {
                        pending--;
                        if (loc != null && state() == online.mythoscraft.core.events.EventState.RUNNING) {
                            placeRelic(loc);
                        }
                        if (pending == 0 && placedTotal == 0 && state() == online.mythoscraft.core.events.EventState.RUNNING) {
                            plugin.getLogger().warning("AncientHunt: no valid relic location found – stopping");
                            finish(true);
                        }
                    });
        }
    }

    private void placeRelic(Location loc) {
        Location at = loc.clone().add(0, 0.6, 0);
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(plugin.items().createUnique(CustomItem.ANCIENT_RELIC));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0x7C3AED));
            d.setViewRange(2.0f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.1f), new Quaternionf()));
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "relic:" + session);
        });
        at.getWorld().addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, plugin);
        relics.add(new Relic(display, at));
        placedTotal++;
    }

    @Override
    protected void onTick() {
        animationTick++;
        for (Relic relic : new ArrayList<>(relics)) {
            if (!relic.display.isValid()) {
                removeRelic(relic);
                continue;
            }
            relic.angle += (float) (Math.PI / 2);
            relic.display.setInterpolationDelay(0);
            relic.display.setInterpolationDuration(20);
            float bob = (animationTick % 2 == 0) ? 0.15f : -0.05f;
            relic.display.setTransformation(new Transformation(new Vector3f(0, bob, 0),
                    new Quaternionf(new AxisAngle4f(relic.angle, 0, 1, 0)), new Vector3f(1.1f), new Quaternionf()));
            World w = relic.location.getWorld();
            if (beam) {
                for (double y = 1; y < 40; y += 1.5) {
                    w.spawnParticle(Particle.END_ROD, relic.location.clone().add(0, y, 0), 1, 0.05, 0.2, 0.05, 0, null, true);
                }
            }
            w.spawnParticle(Particle.WITCH, relic.location, 6, 0.4, 0.4, 0.4, 0.01);
        }
        for (Player p : participants()) {
            Relic nearest = nearest(p.getLocation());
            if (nearest == null) {
                continue;
            }
            double dist = p.getLocation().distance(nearest.location);
            if (dist <= collectRadius) {
                collect(p, nearest);
                continue;
            }
            if (dist <= hintRadius) {
                NamedTextColor color = dist < 10 ? NamedTextColor.RED : dist < 25 ? NamedTextColor.GOLD : dist < 45 ? NamedTextColor.YELLOW : NamedTextColor.AQUA;
                plugin.messages().actionBar(p, "events.ancienthunt.hint",
                        "distance", net.kyori.adventure.text.Component.text(Math.round(dist) + "m", color),
                        "left", relics.size());
            }
            updateCompass(p, nearest.location);
        }
    }

    private void updateCompass(Player player, Location target) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        ItemStack compass = plugin.items().is(main, CustomItem.RELIC_COMPASS) ? main
                : plugin.items().is(off, CustomItem.RELIC_COMPASS) ? off : null;
        if (compass == null) {
            return;
        }
        Location last = compassTargets.get(player.getUniqueId());
        if (last != null && last.equals(target)) {
            return;
        }
        compassTargets.put(player.getUniqueId(), target);
        compass.editMeta(CompassMeta.class, meta -> {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
        });
    }

    private Relic nearest(Location loc) {
        Relic best = null;
        double bestDist = Double.MAX_VALUE;
        for (Relic r : relics) {
            if (!r.location.getWorld().equals(loc.getWorld())) {
                continue;
            }
            double d = r.location.distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    private void collect(Player player, Relic relic) {
        removeRelic(relic);
        foundTotal++;
        addScore(player, pointsPerRelic);
        PlayerData data = plugin.playerData().get(player);
        long coins = 0;
        if (data != null) {
            data.addRelic();
            coins = plugin.economy().earn(data, Currency.COINS,
                    Math.round(coinsPerRelic * plugin.multipliers().global(MultiplierType.EVENTS)), false);
        }
        Effects.floatingItem(plugin, relic.location, plugin.items().createUnique(CustomItem.ANCIENT_RELIC), 1.2f);
        Effects.burst(relic.location.clone().add(0, 1, 0), Color.fromRGB(0x7C3AED), Color.fromRGB(0x22D3EE), false);
        findSound.play(player);
        plugin.messages().broadcast("events.ancienthunt.found", "player", player.getName(), "left", relics.size(),
                "total", placedTotal);
        plugin.messages().send(player, "events.ancienthunt.reward", "coins", coins);
    }

    private void removeRelic(Relic relic) {
        relics.remove(relic);
        if (relic.display.isValid()) {
            relic.display.remove();
        }
        boolean chunkStillUsed = relics.stream().anyMatch(r -> r.chunkX == relic.chunkX && r.chunkZ == relic.chunkZ
                && r.location.getWorld().equals(relic.location.getWorld()));
        if (!chunkStillUsed) {
            relic.location.getWorld().removePluginChunkTicket(relic.chunkX, relic.chunkZ, plugin);
        }
    }

    @Override
    protected boolean isFinished() {
        return pending == 0 && placedTotal > 0 && relics.isEmpty();
    }

    @Override
    protected net.kyori.adventure.text.Component bossBarText() {
        return plugin.messages().get("events.ancienthunt.bossbar", "event", displayName(),
                "found", foundTotal, "total", Math.max(placedTotal, relicCount),
                "time", online.mythoscraft.core.util.Durations.clock(secondsLeft()));
    }

    @Override
    protected void cleanup() {
        for (Relic r : new ArrayList<>(relics)) {
            removeRelic(r);
        }
        relics.clear();
        compassTargets.clear();
    }
}
