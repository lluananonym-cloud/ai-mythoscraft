package online.mythoscraft.plots;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same inventory as on the survival server. On join the inventory, ender chest, XP, health and hunger come from the
 * main server ({@code /api/sync/pull}); while that happens the player stands still with an empty inventory. On quit
 * everything goes back ({@code /api/sync/push}). Rank, coins and gems are refreshed every half minute
 * ({@code /api/sync/info}). Flying on the own plot works with the rank perk (base-fly) or a Fly Booster.
 */
final class SyncClient implements Listener {

    private static final NamespacedKey ITEM = new NamespacedKey("mythoscraft", "item");

    private final MythosPlots plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    /** session id per player – only a player with a session sends his inventory back */
    private final Map<UUID, String> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> flyUntil = new ConcurrentHashMap<>();
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();
    /** survival inventory put aside while a team member is in creative/spectator – that one goes back, not the creative one */
    private final Map<UUID, JsonObject> stash = new ConcurrentHashMap<>();
    private volatile String lastStatus = "nothing yet";
    private volatile String workingBase;

    SyncClient(MythosPlots plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 600, 600);
        Bukkit.getScheduler().runTaskTimer(plugin, this::flyTick, 40, 10);
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("sync.enabled", true);
    }

    private String key() {
        return plugin.getConfig().getString("sync.key", "").trim();
    }

    String status() {
        return lastStatus + (workingBase != null ? " (via " + workingBase + ")" : "");
    }

    boolean busy(Player p) {
        return loading.contains(p.getUniqueId());
    }

    /** the configured address first, the plain http variant as fallback */
    private List<String> bases() {
        List<String> out = new ArrayList<>();
        if (workingBase != null) {
            out.add(workingBase);
        }
        String base = plugin.getConfig().getString("main-server.api-url", "").trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.isEmpty() && !out.contains(base)) {
            out.add(base);
        }
        for (String extra : plugin.getConfig().getStringList("main-server.api-fallbacks")) {
            String e = extra.trim();
            while (e.endsWith("/")) {
                e = e.substring(0, e.length() - 1);
            }
            if (!e.isEmpty() && !out.contains(e)) {
                out.add(e);
            }
        }
        if (base.startsWith("https://")) {
            String plain = "http://" + base.substring(8);
            if (!out.contains(plain)) {
                out.add(plain);
            }
        }
        return out;
    }

    /** POST to the main server; tries the addresses one after the other */
    private CompletableFuture<JsonObject> post(String path, JsonObject body) {
        return post(path, body, bases(), 0);
    }

    private CompletableFuture<JsonObject> post(String path, JsonObject body, List<String> bases, int i) {
        if (i >= bases.size()) {
            return CompletableFuture.failedFuture(new IllegalStateException("main server not reachable – " + lastStatus));
        }
        String base = bases.get(i);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json").header("X-Panel", "1").header("X-Sync-Key", key())
                    .header("User-Agent", "MythosPlots").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        } catch (IllegalArgumentException e) {
            lastStatus = "bad address " + base;
            return post(path, body, bases, i + 1);
        }
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).handle((res, err) -> {
            if (err != null) {
                lastStatus = base + ": " + err.getClass().getSimpleName() + " " + err.getMessage();
                return null;
            }
            if (res.statusCode() == 403) {
                lastStatus = "wrong key – run /mythos synckey on the survival server and paste the command here";
                JsonObject o = new JsonObject();
                o.addProperty("status", "key");
                return o;
            }
            try {
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                workingBase = base;
                lastStatus = "ok (" + res.statusCode() + ")";
                return o;
            } catch (RuntimeException e) {
                lastStatus = base + ": answer " + res.statusCode() + " is no JSON";
                return null;
            }
        }).thenCompose(o -> o != null ? CompletableFuture.completedFuture(o) : post(path, body, bases, i + 1));
    }

    // ------------------------------------------------------------------------------------------- item data
    private static String items(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? ItemStack.empty() : items[i];
        }
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(copy));
    }

    private static ItemStack[] items(String data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.isEmpty()) {
            return out;
        }
        ItemStack[] read = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data));
        for (int i = 0; i < Math.min(size, read.length); i++) {
            out[i] = read[i] == null || read[i].isEmpty() ? null : read[i];
        }
        return out;
    }

    private JsonObject snapshot(Player p) {
        JsonObject o = new JsonObject();
        JsonObject kept = stash.get(p.getUniqueId());
        o.addProperty("inventory", kept != null ? kept.get("inventory").getAsString() : items(p.getInventory().getContents()));
        o.addProperty("ender", kept != null ? kept.get("ender").getAsString() : items(p.getEnderChest().getContents()));
        o.addProperty("level", kept != null ? kept.get("level").getAsInt() : p.getLevel());
        o.addProperty("exp", kept != null ? kept.get("exp").getAsFloat() : p.getExp());
        o.addProperty("health", p.getHealth());
        o.addProperty("food", p.getFoodLevel());
        o.addProperty("saturation", p.getSaturation());
        o.addProperty("fly-until", flyUntil.getOrDefault(p.getUniqueId(), 0L));
        o.addProperty("time", System.currentTimeMillis());
        return o;
    }

    private void apply(Player p, JsonObject d) {
        p.getInventory().setContents(items(d.has("inventory") ? d.get("inventory").getAsString() : null, p.getInventory().getSize()));
        p.getEnderChest().setContents(items(d.has("ender") ? d.get("ender").getAsString() : null, p.getEnderChest().getSize()));
        p.setLevel(d.has("level") ? d.get("level").getAsInt() : 0);
        p.setExp(d.has("exp") ? Math.max(0, Math.min(0.9999f, d.get("exp").getAsFloat())) : 0);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        double health = d.has("health") ? d.get("health").getAsDouble() : 20;
        p.setHealth(Math.max(1, Math.min(max == null ? 20 : max.getValue(), health)));
        p.setFoodLevel(d.has("food") ? d.get("food").getAsInt() : 20);
        p.setSaturation(d.has("saturation") ? d.get("saturation").getAsFloat() : 5);
        p.updateInventory();
        if (d.has("looks") && d.get("looks").isJsonObject()) {
            JsonObject looks = d.getAsJsonObject("looks");
            plugin.ranks().accept(p.getUniqueId(), looks);
            if (looks.has("fly-until")) {
                flyUntil.merge(p.getUniqueId(), looks.get("fly-until").getAsLong(), Math::max);
            }
        }
    }

    private void clear(Player p) {
        p.getInventory().clear();
        p.getEnderChest().clear();
        p.setLevel(0);
        p.setExp(0);
    }

    // ------------------------------------------------------------------------------------------- join / quit
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!enabled()) {
            return;
        }
        // what this server still has stored is old – the real inventory comes from the survival server
        clear(p);
        if (key().isEmpty()) {
            lastStatus = "no key – run /mythos synckey on the survival server";
            plugin.getLogger().warning("Inventory sync: no key set. Run /mythos synckey on the survival server and paste "
                    + "the command here (as OP), or set sync.key in config.yml.");
            if (plugin.bypass(p)) {
                plugin.msg().send(p, "sync.no-key");
            } else {
                plugin.msg().send(p, "sync.failed");
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.backToMain(p), 60);
            }
            return;
        }
        loading.add(p.getUniqueId());
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 30, 0, true, false, false));
        plugin.msg().bar(p, "sync.loading");
        pull(p, 0);
    }

    private void pull(Player p, int attempt) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", p.getUniqueId().toString());
        post("/api/sync/pull", body).whenComplete((o, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                loading.remove(p.getUniqueId());
                return;
            }
            String status = err != null || o == null ? "error" : o.has("status") ? o.get("status").getAsString() : "error";
            switch (status) {
                case "ok" -> {
                    sessions.put(p.getUniqueId(), o.get("session").getAsString());
                    try {
                        apply(p, o.getAsJsonObject("data"));
                    } catch (RuntimeException ex) {
                        plugin.getLogger().warning("Inventory of " + p.getName() + " could not be read: " + ex);
                    }
                    done(p);
                    plugin.msg().bar(p, "sync.loaded");
                }
                case "wait" -> {
                    // still connected to the survival server (server switch in progress)
                    if (attempt < 20) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> pull(p, attempt + 1), 15);
                    } else {
                        fail(p, "sync.failed");
                    }
                }
                case "none" -> fail(p, "sync.join-main-first");
                default -> {
                    if (attempt < 3 && !"key".equals(status)) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> pull(p, attempt + 1), 30);
                    } else {
                        plugin.getLogger().warning("Inventory sync for " + p.getName() + " failed: " + status());
                        fail(p, plugin.bypass(p) && "key".equals(status) ? "sync.no-key" : "sync.failed");
                    }
                }
            }
        }));
    }

    private void done(Player p) {
        loading.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    /** without his inventory nobody plays here: back to the survival server (team members may stay) */
    private void fail(Player p, String key) {
        done(p);
        plugin.msg().send(p, key);
        if (!plugin.bypass(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    plugin.backToMain(p);
                }
            }, 60);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        loading.remove(id);
        flying.remove(id);
        String session = sessions.remove(id);
        if (session == null) {
            flyUntil.remove(id);
            stash.remove(id);
            return;
        }
        JsonObject body = body(p, session);
        flyUntil.remove(id);
        stash.remove(id);
        push(p.getName(), body, 0);
    }

    private JsonObject body(Player p, String session) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", p.getUniqueId().toString());
        body.addProperty("session", session);
        body.add("data", snapshot(p));
        return body;
    }

    private void push(String name, JsonObject body, int attempt) {
        post("/api/sync/push", body).whenComplete((o, err) -> {
            String status = err != null || o == null ? "error" : o.has("status") ? o.get("status").getAsString() : "error";
            if ("ok".equals(status)) {
                return;
            }
            if ("rejected".equals(status)) {
                plugin.getLogger().warning("Inventory of " + name + " was not taken back by the survival server (it gave up waiting).");
                return;
            }
            if (attempt < 4 && plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> push(name, body, attempt + 1), 20L * (attempt + 1));
            } else {
                plugin.getLogger().warning("Inventory of " + name + " could not be sent back: " + status());
            }
        });
    }

    /** server stops: everybody's inventory goes back right now (waiting for the answer) */
    void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String session = sessions.remove(p.getUniqueId());
            if (session == null) {
                continue;
            }
            try {
                post("/api/sync/push", body(p, session)).get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ex) {
                plugin.getLogger().warning("Inventory of " + p.getName() + " could not be sent back on shutdown: " + ex);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- rank, coins, gems
    private void refreshAll() {
        if (!enabled() || key().isEmpty()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!sessions.containsKey(p.getUniqueId())) {
                continue;
            }
            UUID id = p.getUniqueId();
            JsonObject body = new JsonObject();
            body.addProperty("uuid", id.toString());
            post("/api/sync/info", body).thenAccept(o -> {
                if (o != null && "ok".equals(o.has("status") ? o.get("status").getAsString() : "")) {
                    plugin.ranks().accept(id, o);
                }
            });
        }
    }

    // ------------------------------------------------------------------------------------------- flying on the own plot
    private void flyTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
                continue;
            }
            RankLimits.Info i = plugin.ranks().info(p.getUniqueId());
            boolean booster = flyUntil.getOrDefault(p.getUniqueId(), 0L) > now;
            Plot plot = plugin.store().get(plugin.plotAt(p.getLocation()));
            boolean ownPlot = plot != null && plot.mayBuild(p.getUniqueId());
            boolean may = ownPlot && (booster || (i != null && i.fly())) || plugin.bypass(p) && p.getAllowFlight();
            if (may && !p.getAllowFlight()) {
                p.setAllowFlight(true);
                flying.add(p.getUniqueId());
                plugin.msg().bar(p, "fly.on");
            } else if (!may && flying.remove(p.getUniqueId())) {
                boolean inAir = !((org.bukkit.entity.Entity) p).isOnGround();
                p.setFlying(false);
                p.setAllowFlight(false);
                p.setFallDistance(0);
                if (inAir) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 8, 0, true, false, true));
                }
                plugin.msg().bar(p, "fly.off");
            }
        }
    }

    /** a Fly Booster used here: 30 more minutes, counted on the survival server too */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()
                || !"fly_booster".equals(item.getItemMeta().getPersistentDataContainer().get(ITEM, PersistentDataType.STRING))) {
            return;
        }
        e.setCancelled(true);
        if (!sessions.containsKey(e.getPlayer().getUniqueId())) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
        long start = Math.max(System.currentTimeMillis(), flyUntil.getOrDefault(e.getPlayer().getUniqueId(), 0L));
        long until = start + 30 * 60_000L;
        flyUntil.put(e.getPlayer().getUniqueId(), until);
        plugin.msg().send(e.getPlayer(), "fly.booster", "minutes", (until - System.currentTimeMillis() + 59_999) / 60_000);
        e.getPlayer().playSound(e.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------------------------------- creative for the team
    private static boolean survivalLike(GameMode m) {
        return m == GameMode.SURVIVAL || m == GameMode.ADVENTURE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(org.bukkit.event.player.PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        boolean from = survivalLike(p.getGameMode());
        boolean to = survivalLike(e.getNewGameMode());
        if (from && !to && !busy(p)) {
            // into creative/spectator: the survival things are put aside, creative starts empty
            JsonObject kept = new JsonObject();
            kept.addProperty("inventory", items(p.getInventory().getContents()));
            kept.addProperty("ender", items(p.getEnderChest().getContents()));
            kept.addProperty("level", p.getLevel());
            kept.addProperty("exp", p.getExp());
            stash.put(p.getUniqueId(), kept);
            p.getInventory().clear();
            p.getEnderChest().clear();
            plugin.msg().send(p, "gamemode.stashed");
        } else if (!from && to) {
            JsonObject kept = stash.remove(p.getUniqueId());
            if (kept != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) {
                        return;
                    }
                    p.getInventory().setContents(items(kept.get("inventory").getAsString(), p.getInventory().getSize()));
                    p.getEnderChest().setContents(items(kept.get("ender").getAsString(), p.getEnderChest().getSize()));
                    p.setLevel(kept.get("level").getAsInt());
                    p.setExp(kept.get("exp").getAsFloat());
                    plugin.msg().send(p, "gamemode.restored");
                });
            }
        }
    }

    // ------------------------------------------------------------------------------------------- while loading
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (busy(e.getPlayer()) && e.hasChangedPosition()) {
            e.setTo(e.getFrom().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (busy(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && busy(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (busy(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && busy(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && busy(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (busy(e.getPlayer()) && !msg.startsWith("/smp") && !msg.startsWith("/mk ") && !msg.startsWith("/plot admin key")) {
            e.setCancelled(true);
            plugin.msg().bar(e.getPlayer(), "sync.loading");
        }
    }
}
