package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.progress.ProgressMath;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Elytra ring course: the plate at the foot of the tower brings you up and lends you an elytra, then fly through all
 * rings in order. Every ring gives a little boost. Best time, top list and a daily reward like the parkour.
 */
public final class RingService implements Listener {

    private static final String LOAN = "loan_elytra";

    private static final class Run {
        long started;
        int next;
        long lastRing;
        boolean airborne;
    }

    private final MythosCraft plugin;
    private final Map<UUID, Run> runs = new HashMap<>();

    public boolean inRun(Player p) {
        return runs.containsKey(p.getUniqueId());
    }
    private long dailyGems = 3;
    private long dailyCoins = 500;
    private double ringRadius = 3.3;
    private double boost = 1.25;

    public RingService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.rings");
        dailyGems = cfg.longValue("daily-gems", 3, 0, 100_000);
        dailyCoins = cfg.longValue("daily-coins", 500, 0, 100_000_000);
        ringRadius = cfg.decimal("ring-radius", 3.3, 1, 10);
        boost = cfg.decimal("boost", 1.25, 1, 3);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 10, "ring glow", this::glow);
    }

    public boolean exists() {
        return plugin.attractionStore().first("elytra_start") != null && !plugin.attractionStore().all("ring").isEmpty();
    }

    private void glow() {
        for (Map.Entry<UUID, Run> e : runs.entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            List<Location> rings = plugin.attractionStore().all("ring");
            if (p == null || e.getValue().next >= rings.size()) {
                continue;
            }
            Location r = rings.get(e.getValue().next);
            p.spawnParticle(Particle.END_ROD, r, 25, 1.6, 1.6, 1.6, 0.01);
        }
    }

    ItemStack loanElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        elytra.editMeta(meta -> {
            meta.displayName(Text.item(plugin.messages().raw("attractions.rings.elytra-name")));
            meta.lore(plugin.messages().rawList("attractions.rings.elytra-lore").stream().map(Text::item).toList());
            meta.setUnbreakable(true);
            org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("loan_elytra"));
            meta.setCustomModelDataComponent(cmd);
            meta.getPersistentDataContainer().set(Keys.key(LOAN), PersistentDataType.BYTE, (byte) 1);
        });
        return elytra;
    }

    static boolean isLoan(ItemStack item) {
        return item != null && item.getType() == Material.ELYTRA && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.key(LOAN), PersistentDataType.BYTE);
    }

    /** Pressure plate at the tower: teleport up and lend the elytra. */
    @EventHandler
    public void onPlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }
        Location lift = plugin.attractionStore().first("elytra_lift");
        Location top = plugin.attractionStore().first("elytra_start");
        Block b = event.getClickedBlock();
        if (lift == null || top == null || !b.getWorld().equals(lift.getWorld()) || b.getX() != lift.getBlockX()
                || b.getY() != lift.getBlockY() || b.getZ() != lift.getBlockZ()) {
            return;
        }
        Player p = event.getPlayer();
        if (runs.containsKey(p.getUniqueId())) {
            return;
        }
        ItemStack chest = p.getInventory().getChestplate();
        if (chest != null && !chest.isEmpty() && !isLoan(chest)) {
            if (p.getInventory().firstEmpty() < 0) {
                plugin.messages().actionBar(p, "attractions.rings.inventory-full");
                return;
            }
            p.getInventory().addItem(chest);
        }
        p.getInventory().setChestplate(loanElytra());
        Run run = new Run();
        runs.put(p.getUniqueId(), run);
        Location up = top.clone();
        p.teleport(up, PlayerTeleportEvent.TeleportCause.PLUGIN);
        SoundSpec.of("entity.enderman.teleport", 0.8f, 1.4f).play(p);
        plugin.messages().send(p, "attractions.rings.ready", "rings", plugin.attractionStore().all("ring").size());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Run run = runs.get(p.getUniqueId());
        if (run == null) {
            return;
        }
        if (!plugin.attractionStore().inside(event.getTo(), 30)) {
            end(p, "attractions.rings.cancelled");
            return;
        }
        if (p.isGliding()) {
            if (!run.airborne) {
                run.airborne = true;
                run.started = System.currentTimeMillis();
                plugin.messages().actionBar(p, "attractions.rings.go");
            }
        } else if (run.airborne && ((org.bukkit.entity.Entity) p).isOnGround()) {
            end(p, "attractions.rings.landed");
            return;
        }
        if (!run.airborne) {
            Location top = plugin.attractionStore().first("elytra_start");
            if (top != null && ((org.bukkit.entity.Entity) p).isOnGround() && p.getLocation().getY() < top.getY() - 4) {
                end(p, "attractions.rings.landed");
            }
            return;
        }
        List<Location> rings = plugin.attractionStore().all("ring");
        if (run.next >= rings.size()) {
            return;
        }
        Location ring = rings.get(run.next);
        if (!ring.getWorld().equals(p.getWorld())) {
            return;
        }
        double d = segmentDistance(event.getFrom().toVector(), event.getTo().toVector(), ring.toVector());
        if (d > ringRadius) {
            return;
        }
        run.next++;
        run.lastRing = System.currentTimeMillis();
        Vector v = p.getVelocity();
        Vector dir = p.getLocation().getDirection();
        p.setVelocity(v.multiply(boost).add(dir.multiply(0.25)).add(new Vector(0, 0.15, 0)));
        p.spawnParticle(Particle.FIREWORK, ring, 30, 1.2, 1.2, 1.2, 0.05);
        if (run.next >= rings.size()) {
            finish(p, run);
        } else {
            SoundSpec.of("entity.experience_orb.pickup", 0.9f, 0.6f + run.next * 0.08f).play(p);
            plugin.messages().actionBar(p, "attractions.rings.passed", "number", run.next, "total", rings.size(),
                    "time", ProgressMath.time(System.currentTimeMillis() - run.started));
        }
    }

    static double segmentDistance(Vector a, Vector b, Vector c) {
        Vector ab = b.clone().subtract(a);
        double len = ab.lengthSquared();
        if (len < 1e-6) {
            return a.distance(c);
        }
        double t = Math.max(0, Math.min(1, c.clone().subtract(a).dot(ab) / len));
        return a.clone().add(ab.multiply(t)).distance(c);
    }

    private void finish(Player p, Run run) {
        long time = System.currentTimeMillis() - run.started;
        long best = plugin.progress().get(p, "rings.best");
        boolean record = best <= 0 || time < best;
        if (record) {
            plugin.progress().set(p, "rings.best", time);
        }
        plugin.progress().add(p, "rings.finishes", 1);
        plugin.messages().send(p, record ? "attractions.rings.finished-record" : "attractions.rings.finished",
                "time", ProgressMath.time(time), "best", ProgressMath.time(record ? time : best));
        ActivityRewards.pay(plugin, p, "rings", dailyGems, dailyCoins);
        SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.2f).play(p);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 60, 0.6, 0.6, 0.6, 0.3);
        // the elytra is taken back after landing
        run.next = Integer.MAX_VALUE / 2;
    }

    /** Ends the run (landing, leaving, quitting) and takes the loaned elytra back. */
    private void end(Player p, String messageKey) {
        Run run = runs.remove(p.getUniqueId());
        takeBack(p);
        if (run != null && run.next < Integer.MAX_VALUE / 2 && messageKey != null) {
            plugin.messages().actionBar(p, messageKey);
        }
    }

    private static void takeBack(Player p) {
        if (isLoan(p.getInventory().getChestplate())) {
            p.getInventory().setChestplate(null);
        }
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isLoan(contents[i])) {
                p.getInventory().setItem(i, null);
            }
        }
        if (isLoan(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(RingService::isLoan);
        runs.remove(event.getEntity().getUniqueId());
        takeBack(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLoan(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (isLoan(event.getCurrentItem()) || isLoan(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    /** Safety net: loaned elytras outside a run disappear (e.g. after a crash). */
    public void sweep() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!runs.containsKey(p.getUniqueId())) {
                takeBack(p);
            }
        }
    }

    /** /ringe: own best time and the top 5. */
    public void info(Player p) {
        if (!exists()) {
            plugin.messages().send(p, "attractions.rings.none");
            return;
        }
        long best = plugin.progress().get(p, "rings.best");
        plugin.messages().send(p, "attractions.rings.info", "best", best > 0 ? ProgressMath.time(best) : "-",
                "finishes", plugin.progress().get(p, "rings.finishes"));
        plugin.progress().lowest("rings.best", 5).thenAccept(top -> plugin.tasks().sync(() -> {
            int place = 0;
            for (Map.Entry<String, Long> e : top) {
                place++;
                plugin.messages().sendRaw(p, "parkour.top-line", "place", place, "player", e.getKey(), "time", ProgressMath.time(e.getValue()));
            }
        }));
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        for (UUID id : List.copyOf(runs.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                takeBack(p);
            }
        }
        runs.clear();
    }
}
