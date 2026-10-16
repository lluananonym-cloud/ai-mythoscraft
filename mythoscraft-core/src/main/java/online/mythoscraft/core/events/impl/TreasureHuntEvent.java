package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.EventState;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.events.SurfaceFinder;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * All-day treasure hunt: every few minutes a glowing treasure chest appears somewhere in the wilderness. The rough
 * position is announced (rounded), a light beam shows the way when you get close. Whoever grabs it first scores a
 * point and gets a random treasure. Runs in the background next to the normal events.
 */
public final class TreasureHuntEvent extends MythosEvent {

    private static final String TAG = "event_treasure";

    private final List<Item> chests = new ArrayList<>();
    private int everyMinutes = 20;
    private double minRadius = 400;
    private double maxRadius = 2500;
    private int maxOpen = 3;
    private List<Reward> treasures = new ArrayList<>();
    private int timer;

    public TreasureHuntEvent(MythosCraft plugin) {
        super(plugin, "treasurehunt");
    }

    @Override
    protected int defaultDuration() {
        return 12 * 3600;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        everyMinutes = cfg.integer("chest-every-minutes", 20, 1, 600);
        minRadius = cfg.decimal("radius.min", 400, 20, 100_000);
        maxRadius = Math.max(minRadius + 50, cfg.decimal("radius.max", 2500, 50, 100_000));
        maxOpen = cfg.integer("max-chests-at-once", 3, 1, 20);
        treasures = new ArrayList<>();
        for (String s : cfg.strings("treasures")) {
            try {
                treasures.add(Reward.parse(s));
            } catch (IllegalArgumentException e) {
                cfg.warn("treasures", e.getMessage());
            }
        }
        if (treasures.isEmpty()) {
            treasures.add(Reward.parse("coins 1000"));
        }
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.treasures";
    }

    @Override
    protected void onStart() {
        timer = 5;
    }

    @Override
    protected void onTick() {
        chests.removeIf(i -> !i.isValid());
        for (Item chest : chests) {
            if (chest.getLocation().isChunkLoaded()) {
                Location l = chest.getLocation();
                for (int y = 0; y < 40; y += 3) {
                    l.getWorld().spawnParticle(Particle.END_ROD, l.clone().add(0, y, 0), 1, 0.05, 0.3, 0.05, 0, null, true);
                }
                l.getWorld().spawnParticle(Particle.WAX_ON, l.clone().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3, 0);
            }
        }
        if (--timer > 0 || chests.size() >= maxOpen) {
            return;
        }
        timer = everyMinutes * 60;
        hide();
    }

    private void hide() {
        Location base = plugin.spawn().spawn();
        World w = base != null ? base.getWorld() : Bukkit.getWorlds().get(0);
        double cx = base != null ? base.getX() : w.getSpawnLocation().getX();
        double cz = base != null ? base.getZ() : w.getSpawnLocation().getZ();
        SurfaceFinder.find(plugin, w, cx, cz, minRadius, maxRadius, 20,
                b -> plugin.regions().top(b.getLocation()) == null && plugin.arenas().arenaAt(b.getLocation()) == null)
                .thenAccept(loc -> {
                    if (loc == null || state() != EventState.RUNNING) {
                        return;
                    }
                    ItemStack stack = new ItemBuilder(Material.CHEST).glint(true).build();
                    stack.editPersistentDataContainer(pdc -> pdc.set(Keys.key(TAG), PersistentDataType.STRING, session));
                    Item item = loc.getWorld().dropItem(loc.clone().add(0, 0.2, 0), stack, drop -> {
                        drop.setPersistent(true);
                        drop.setGlowing(true);
                        drop.setUnlimitedLifetime(true);
                        drop.setCanMobPickup(false);
                        drop.setGravity(false);
                        drop.setVelocity(new org.bukkit.util.Vector());
                    });
                    chests.add(item);
                    int rx = (int) (Math.round(loc.getX() / 50.0) * 50);
                    int rz = (int) (Math.round(loc.getZ() / 50.0) * 50);
                    plugin.messages().broadcast("events.treasurehunt.hidden", "x", rx, "z", rz, "world", loc.getWorld().getName());
                });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        String tag = event.getItem().getItemStack().getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        event.setCancelled(true);
        if (!tag.equals(session)) {
            event.getItem().remove(); // left over from an earlier day
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !eligible(player)) {
            return;
        }
        Location at = event.getItem().getLocation();
        event.getItem().remove();
        chests.remove(event.getItem());
        addScore(player, 1);
        Reward r = treasures.get(ThreadLocalRandom.current().nextInt(treasures.size()));
        r.give(plugin, player, 1.0);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.add(0, 0.6, 0), 60, 0.4, 0.6, 0.4, 0.3);
        SoundSpec.of("ui.toast.challenge_complete", 1f, 1.2f).play(player);
        plugin.messages().broadcast("events.treasurehunt.found", "player", player.getName(), "reward", r.describe(plugin, 1.0));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (event.getItem().getItemStack().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void cleanup() {
        for (Item i : chests) {
            if (i.isValid()) {
                i.remove();
            }
        }
        chests.clear();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(Item.class)) {
                if (((Item) e).getItemStack().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }
    }
}
