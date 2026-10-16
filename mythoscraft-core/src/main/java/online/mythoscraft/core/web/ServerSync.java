package online.mythoscraft.core.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One inventory for the survival server and the plot server. This server is the keeper:
 * <ol>
 *   <li>leaving here, the player's inventory, ender chest, XP, health and hunger are written down ("released"),
 *   together with his rank looks (badge, name, colours) and fly perks;</li>
 *   <li>the plot server takes them when he joins there ({@code /api/sync/pull}) – from then on the plot server
 *   holds the inventory (a session id proves it);</li>
 *   <li>leaving the plot server, it sends the inventory back ({@code /api/sync/push});</li>
 *   <li>joining here again, the returned inventory replaces the old one. If it has not arrived yet, the player waits
 *   a few seconds frozen; if it never comes, he keeps the inventory he had when he left (nothing is doubled).</li>
 * </ol>
 * Both servers share a key ({@code server-sync.key}).
 */
public final class ServerSync implements Listener {

    enum Mode { MAIN, RELEASED, PLOT, RETURNED }

    static final class State {
        Mode mode = Mode.MAIN;
        String session = "";
        JsonObject data;
        /** latest live save point from the plot server while he plays there */
        JsonObject checkpoint;
        String name = "?";
        long since = System.currentTimeMillis();
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MythosCraft plugin;
    private final File folder;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    /** rank looks + fly data collected early in the quit (later the player data may already be gone) */
    private final Map<UUID, JsonObject> leaving = new ConcurrentHashMap<>();

    public ServerSync(MythosCraft plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data/server-sync");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("server-sync.enabled", true);
    }

    private String generatedKey;

    /**
     * Shared key with the plot server: server-sync.key in the config, otherwise one made up once and kept in
     * data/server-sync/key.txt. The owner hands it over with /mythos synckey (copy) → /plot admin key … over there.
     */
    public synchronized String key() {
        String set = plugin.getConfig().getString("server-sync.key", "").trim();
        if (!set.isEmpty()) {
            return set;
        }
        if (generatedKey == null) {
            File f = new File(folder, "key.txt");
            try {
                if (f.isFile()) {
                    generatedKey = Files.readString(f.toPath(), StandardCharsets.UTF_8).trim();
                }
                if (generatedKey == null || generatedKey.length() < 16) {
                    generatedKey = HexFormat.of().formatHex(token()) + HexFormat.of().formatHex(token());
                    Files.createDirectories(folder.toPath());
                    Files.writeString(f.toPath(), generatedKey, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("server-sync: key file – " + e.getMessage());
                return "";
            }
        }
        return generatedKey;
    }

    /** waiting for the inventory from the plot server? */
    public boolean busy(Player p) {
        return waiting.contains(p.getUniqueId());
    }

    // ------------------------------------------------------------------------------------------- state files
    private State state(UUID id) {
        return states.computeIfAbsent(id, u -> {
            State s = new State();
            File f = new File(folder, u + ".json");
            if (f.isFile()) {
                try {
                    JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                    s.mode = Mode.valueOf(o.get("mode").getAsString());
                    s.session = o.has("session") ? o.get("session").getAsString() : "";
                    s.since = o.has("since") ? o.get("since").getAsLong() : 0;
                    s.data = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : null;
                    s.checkpoint = o.has("checkpoint") && o.get("checkpoint").isJsonObject() ? o.getAsJsonObject("checkpoint") : null;
                    s.name = o.has("name") ? o.get("name").getAsString() : "?";
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().warning("server-sync: " + f.getName() + " unreadable – " + e.getMessage());
                }
            }
            return s;
        });
    }

    private void save(UUID id, State s) {
        save(id, s, false);
    }

    private void save(UUID id, State s, boolean now) {
        JsonObject o = new JsonObject();
        o.addProperty("mode", s.mode.name());
        o.addProperty("session", s.session);
        o.addProperty("since", s.since);
        o.addProperty("name", s.name);
        if (s.data != null) {
            o.add("data", s.data);
        }
        if (s.checkpoint != null) {
            o.add("checkpoint", s.checkpoint);
        }
        String text = o.toString();
        Runnable write = () -> {
            try {
                Files.createDirectories(folder.toPath());
                File tmp = new File(folder, id + ".json.tmp");
                Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), new File(folder, id + ".json").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().warning("server-sync: could not save " + id + " – " + e.getMessage());
            }
        };
        if (now) {
            write.run();
        } else {
            plugin.tasks().io("server sync save", write);
        }
    }

    // ------------------------------------------------------------------------------------------- snapshot / apply
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

    /** what the plot server shows and allows for this player */
    private JsonObject looks(Player p) {
        Rank rank = plugin.ranks().rankOf(p);
        boolean staff = p.hasPermission(Perms.TEAM_BADGE);
        var data = plugin.playerData().get(p);
        JsonObject o = rankLooks(rank, staff, data == null ? 0 : data.coins(), data == null ? 0 : data.gems());
        o.addProperty("name", GsonComponentSerializer.gson().serialize(Component.text(p.getName(), rank.teamColor()).append(plugin.display().suffix(p))));
        o.addProperty("fly-until", plugin.progress().loaded(p) ? plugin.progress().get(p, "flybooster.until") : 0);
        return o;
    }

    /** rank part of the looks (works offline too) + coins, gems and the scoreboard icons */
    private JsonObject rankLooks(Rank rank, boolean staff, long coins, long gems) {
        GsonComponentSerializer gson = GsonComponentSerializer.gson();
        JsonObject o = new JsonObject();
        o.addProperty("rank", rank.id());
        o.addProperty("prefix", gson.serialize(plugin.display().prefix(rank, staff, true)));
        o.addProperty("text-prefix", gson.serialize(plugin.display().prefix(rank, staff, false)));
        o.addProperty("rank-display", gson.serialize(online.mythoscraft.core.util.Text.parse(rank.display())));
        o.addProperty("name-color", rank.teamColor().toString());
        o.addProperty("chat-color", rank.chatColor() == null ? "#AAAAAA" : rank.chatColor().asHexString());
        o.addProperty("weight", rank.weight());
        o.addProperty("plots", plotLimit(rank));
        o.addProperty("fly", plugin.rankPerks().of(rank).baseFly());
        o.addProperty("staff", staff);
        o.addProperty("coins", coins);
        o.addProperty("gems", gems);
        JsonObject icons = new JsonObject();
        for (String id : java.util.List.of("coin", "gem", "rank", "time", "key", "event")) {
            JsonObject i = new JsonObject();
            i.addProperty("pack", gson.serialize(online.mythoscraft.core.display.Glyphs.icon(id, true)));
            i.addProperty("text", gson.serialize(online.mythoscraft.core.display.Glyphs.icon(id, false)));
            icons.add(id, i);
        }
        o.add("icons", icons);
        return o;
    }

    /** /api/sync/info – fresh rank, coins and gems for a player who is on the plot server */
    private JsonObject info(UUID id) throws Exception {
        online.mythoscraft.core.player.PlayerData.Snapshot snap =
                plugin.playerData().loadSnapshot(id).get(8, java.util.concurrent.TimeUnit.SECONDS);
        return plugin.webPanel().sync(() -> {
            var ranks = plugin.ranks();
            Rank r = snap == null ? null : ranks.byId(snap.rankId());
            boolean expired = snap != null && snap.rankExpires() > 0 && snap.rankExpires() < System.currentTimeMillis();
            if (online.mythoscraft.core.rank.MythosOwner.is(id) && (r == null || expired)) {
                r = ranks.mythos();
            } else if (r == null || expired) {
                r = ranks.defaultRank();
            }
            Rank mod = ranks.byId("MOD");
            boolean staff = mod != null && r.weight() >= mod.weight();
            JsonObject o = rankLooks(r, staff, snap == null ? 0 : snap.coins(), snap == null ? 0 : snap.gems());
            State s = state(id);
            if (s.data != null && s.data.has("looks") && s.data.getAsJsonObject("looks").has("name")) {
                o.add("name", s.data.getAsJsonObject("looks").get("name"));
            }
            o.addProperty("status", "ok");
            return o;
        });
    }

    /** plots on the plot server: rank-perks.&lt;rank&gt;.plots, team members at least plot-server.staff-plots */
    public int plotLimit(Rank r) {
        int plots = plugin.rankPerks().plotsFor(r.id());
        Rank mod = plugin.ranks().byId("MOD");
        if (mod != null && r.weight() >= mod.weight()) {
            plots = Math.max(plots, plugin.getConfig().getInt("plot-server.staff-plots", 5));
        }
        return plots;
    }

    private JsonObject snapshot(Player p, JsonObject looks) {
        JsonObject o = new JsonObject();
        o.addProperty("inventory", items(p.getInventory().getContents()));
        o.addProperty("ender", items(p.getEnderChest().getContents()));
        o.addProperty("level", p.getLevel());
        o.addProperty("exp", p.getExp());
        o.addProperty("health", p.getHealth());
        o.addProperty("food", p.getFoodLevel());
        o.addProperty("saturation", p.getSaturation());
        o.add("looks", looks == null ? looks(p) : looks);
        o.addProperty("time", System.currentTimeMillis());
        return o;
    }

    private void apply(Player p, JsonObject d) {
        try {
            p.getInventory().setContents(items(str(d, "inventory"), p.getInventory().getSize()));
            p.getEnderChest().setContents(items(str(d, "ender"), p.getEnderChest().getSize()));
            p.setLevel(d.has("level") ? d.get("level").getAsInt() : p.getLevel());
            p.setExp(d.has("exp") ? Math.max(0, Math.min(0.9999f, d.get("exp").getAsFloat())) : p.getExp());
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            double health = d.has("health") ? d.get("health").getAsDouble() : p.getHealth();
            p.setHealth(Math.max(1, Math.min(max == null ? 20 : max.getValue(), health)));
            p.setFoodLevel(d.has("food") ? d.get("food").getAsInt() : p.getFoodLevel());
            p.setSaturation(d.has("saturation") ? d.get("saturation").getAsFloat() : p.getSaturation());
            p.updateInventory();
            // a Fly Booster used on the plot server counts here too
            long flyUntil = d.has("fly-until") ? d.get("fly-until").getAsLong() : 0;
            if (flyUntil > 0) {
                setFlyUntil(p, flyUntil, 0);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("server-sync: inventory of " + p.getName() + " could not be applied – " + e);
        }
    }

    private void setFlyUntil(Player p, long until, int tries) {
        if (!p.isOnline()) {
            return;
        }
        if (plugin.progress().loaded(p)) {
            if (until > plugin.progress().get(p, "flybooster.until")) {
                plugin.progress().set(p, "flybooster.until", until);
            }
        } else if (tries < 20) {
            plugin.tasks().later(10, "fly sync", () -> setFlyUntil(p, until, tries + 1));
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    // ------------------------------------------------------------------------------------------- join / quit
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled()) {
            return;
        }
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        State s = state(id);
        if (s.mode == Mode.RETURNED && s.data != null) {
            apply(p, s.data);
            back(p, id, s);
            plugin.messages().actionBar(p, "server-sync.loaded");
        } else if (s.mode == Mode.PLOT) {
            // the plot server still holds the inventory – wait for it
            waiting.add(id);
            plugin.messages().send(p, "server-sync.waiting");
            long until = System.currentTimeMillis() + plugin.getConfig().getInt("server-sync.wait-seconds", 20) * 1000L;
            org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
            task[0] = plugin.tasks().repeat(this, 5, 5, "server sync wait", new Runnable() {
                boolean done;

                @Override
                public void run() {
                    if (done) {
                        if (task[0] != null) {
                            task[0].cancel();
                        }
                        return;
                    }
                    if (!p.isOnline()) {
                        done = true;
                        waiting.remove(id);
                        return;
                    }
                    if (s.mode == Mode.RETURNED && s.data != null) {
                        done = true;
                        waiting.remove(id);
                        apply(p, s.data);
                        back(p, id, s);
                        plugin.messages().actionBar(p, "server-sync.loaded");
                    } else if (System.currentTimeMillis() > until) {
                        done = true;
                        waiting.remove(id);
                        if (s.checkpoint != null) {
                            // plot server gone (crash …): the last live save point from there
                            apply(p, s.checkpoint);
                            plugin.messages().send(p, "server-sync.checkpoint");
                            plugin.getLogger().warning("server-sync: plot server did not answer for " + p.getName()
                                    + " – loaded the last live save point from there.");
                        } else {
                            plugin.messages().send(p, "server-sync.timeout");
                            plugin.getLogger().warning("server-sync: the plot server did not send " + p.getName()
                                    + "'s inventory back – he keeps the inventory he had before going there.");
                        }
                        back(p, id, s);
                    }
                }
            });
        } else {
            back(p, id, s);
        }
    }

    private void back(Player p, UUID id, State s) {
        s.mode = Mode.MAIN;
        s.session = "";
        s.name = p.getName();
        s.data = null;
        s.checkpoint = null;
        s.since = System.currentTimeMillis();
        save(id, s);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitEarly(PlayerQuitEvent e) {
        if (enabled()) {
            try {
                leaving.put(e.getPlayer().getUniqueId(), looks(e.getPlayer()));
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("server-sync looks: " + ex);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        JsonObject looks = leaving.remove(id);
        if (!enabled()) {
            return;
        }
        if (waiting.remove(id)) {
            return;                     // left while waiting: the plot server still holds the real inventory
        }
        State s = state(id);
        if (s.mode != Mode.MAIN) {
            return;
        }
        s.mode = Mode.RELEASED;
        s.session = "";
        s.name = p.getName();
        s.data = snapshot(p, looks);
        s.since = System.currentTimeMillis();
        save(id, s);
        backup(id, s.data, "left-survival");
    }

    /** shutdown: everybody online is released, so the plot server may take them */
    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!enabled() || waiting.contains(id)) {
                continue;
            }
            State s = state(id);
            if (s.mode == Mode.MAIN) {
                s.mode = Mode.RELEASED;
                s.data = snapshot(p, null);
                s.since = System.currentTimeMillis();
                save(id, s, true);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- while waiting
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (busy(e.getPlayer()) && e.hasChangedPosition()) {
            e.setTo(e.getFrom().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler(ignoreCancelled = true)
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
        if (busy(e.getPlayer())) {
            e.setCancelled(true);
            plugin.messages().send(e.getPlayer(), "server-sync.waiting");
        }
    }

    // ------------------------------------------------------------------------------------------- HTTP (plot server)
    private boolean authorised(HttpExchange ex) {
        String want = key();
        String got = ex.getRequestHeaders().getFirst("X-Sync-Key");
        return !want.isEmpty() && got != null
                && MessageDigest.isEqual(want.getBytes(StandardCharsets.UTF_8), got.trim().getBytes(StandardCharsets.UTF_8));
    }

    void handle(HttpExchange ex, String path, WebPanelService web) throws Exception {
        if (!enabled()) {
            WebPanelService.json(ex, 503, Map.of("status", "disabled"));
            return;
        }
        if (!authorised(ex)) {
            WebPanelService.json(ex, 403, Map.of("status", "key"));
            return;
        }
        JsonObject body = web.body(ex);
        UUID id;
        try {
            id = UUID.fromString(body.get("uuid").getAsString());
        } catch (RuntimeException e) {
            WebPanelService.json(ex, 400, Map.of("status", "uuid"));
            return;
        }
        if (path.endsWith("/info")) {
            WebPanelService.json(ex, 200, info(id));
        } else if (path.endsWith("/pull")) {
            Map<String, Object> r = web.sync(() -> pull(id));
            plugin.getLogger().info("server-sync: plot server takes the inventory of " + state(id).name + " → " + r.get("status"));
            WebPanelService.json(ex, 200, r);
        } else {
            String session = body.has("session") ? body.get("session").getAsString() : "";
            JsonObject data = body.has("data") && body.get("data").isJsonObject() ? body.getAsJsonObject("data") : null;
            boolean live = path.endsWith("/checkpoint");
            Map<String, Object> r = web.sync(() -> live ? checkpoint(id, session, data) : push(id, session, data));
            if (!live) {
                plugin.getLogger().info("server-sync: plot server sends back the inventory of " + state(id).name + " → " + r.get("status"));
            }
            WebPanelService.json(ex, 200, r);
        }
    }

    private Map<String, Object> pull(UUID id) {
        State s = state(id);
        if (Bukkit.getPlayer(id) != null) {
            return Map.of("status", "wait");          // still connected here (server switch in progress)
        }
        if (s.data == null || s.mode == Mode.MAIN) {
            return Map.of("status", "none");          // never left this server since the sync exists
        }
        s.mode = Mode.PLOT;
        s.session = HexFormat.of().formatHex(token());
        s.since = System.currentTimeMillis();
        save(id, s);
        return Map.of("status", "ok", "session", s.session, "data", s.data);
    }

    private static byte[] token() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return b;
    }

    private Map<String, Object> push(UUID id, String session, JsonObject data) {
        State s = state(id);
        if (s.mode != Mode.PLOT || s.session.isEmpty() || !s.session.equals(session) || data == null) {
            return Map.of("status", "rejected");
        }
        JsonObject looks = s.data != null && s.data.has("looks") ? s.data.getAsJsonObject("looks") : null;
        if (looks != null && !data.has("looks")) {
            data.add("looks", looks);
        }
        s.mode = Mode.RETURNED;
        s.session = "";
        s.data = data;
        s.checkpoint = null;
        s.since = System.currentTimeMillis();
        save(id, s);
        backup(id, data, "back-from-plot");
        Player online = Bukkit.getPlayer(id);
        if (online != null && !waiting.contains(id)) {
            // he is already here and not waiting (e.g. the push came late after a restart): take it now
            apply(online, data);
            back(online, id, s);
        }
        return Map.of("status", "ok");
    }

    /** live save point every minute while he plays on the plot server */
    private Map<String, Object> checkpoint(UUID id, String session, JsonObject data) {
        State s = state(id);
        if (s.mode != Mode.PLOT || s.session.isEmpty() || !s.session.equals(session) || data == null) {
            return Map.of("status", "rejected");
        }
        s.checkpoint = data;
        save(id, s);
        long last = lastBackup.getOrDefault(id, 0L);
        if (System.currentTimeMillis() - last > 10 * 60_000L) {
            backup(id, data, "plot-live");
        }
        return Map.of("status", "ok");
    }

    // ------------------------------------------------------------------------------------------- backups
    private final Map<UUID, Long> lastBackup = new ConcurrentHashMap<>();

    private File backupDir(UUID id) {
        return new File(folder, "backups/" + id);
    }

    private void backup(UUID id, JsonObject data, String reason) {
        if (data == null) {
            return;
        }
        lastBackup.put(id, System.currentTimeMillis());
        String text = data.toString();
        int keep = Math.max(1, plugin.getConfig().getInt("server-sync.backups-per-player", 30));
        plugin.tasks().io("server sync backup", () -> {
            try {
                File dir = backupDir(id);
                Files.createDirectories(dir.toPath());
                Files.writeString(new File(dir, System.currentTimeMillis() + "-" + reason + ".json").toPath(), text, StandardCharsets.UTF_8);
                File[] all = dir.listFiles((d, n) -> n.endsWith(".json"));
                if (all != null && all.length > keep) {
                    java.util.Arrays.sort(all, java.util.Comparator.comparing(File::getName));
                    for (int i = 0; i < all.length - keep; i++) {
                        all[i].delete();
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("server-sync: backup of " + id + " failed – " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------------------------------- admin page /inventory
    /** everybody the sync knows: name, where the inventory is right now, since when */
    java.util.List<Map<String, Object>> list() {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        File[] files = folder.listFiles((d, n) -> n.endsWith(".json") && n.length() > 30);
        if (files != null) {
            for (File f : files) {
                try {
                    UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 5));
                    State s = state(id);
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("uuid", id.toString());
                    m.put("name", Bukkit.getPlayer(id) != null ? Bukkit.getPlayer(id).getName() : s.name);
                    m.put("where", where(id, s));
                    m.put("since", s.since);
                    out.add(m);
                } catch (RuntimeException ignored) {
                    // other file
                }
            }
        }
        out.sort(java.util.Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase(java.util.Locale.ROOT)));
        return out;
    }

    private String where(UUID id, State s) {
        if (Bukkit.getPlayer(id) != null) {
            return busy(Bukkit.getPlayer(id)) ? "arriving" : "survival";
        }
        return switch (s.mode) {
            case PLOT -> "plot";
            case RETURNED -> "on the way back";
            default -> "offline";
        };
    }

    private static java.util.List<Map<String, Object>> describe(String data, int size, MythosCraft plugin) {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        ItemStack[] items = items(data, size);
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null) {
                continue;
            }
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("slot", i);
            m.put("type", it.getType().getKey().getKey());
            m.put("amount", it.getAmount());
            var custom = plugin.items().identify(it);
            m.put("custom", custom == null ? "" : custom.id());
            m.put("name", it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName())
                    : "");
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> view(JsonObject d) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (d == null) {
            return m;
        }
        m.put("inventory", describe(str(d, "inventory"), 41, plugin));
        m.put("ender", describe(str(d, "ender"), 27, plugin));
        m.put("level", d.has("level") ? d.get("level").getAsInt() : 0);
        m.put("health", d.has("health") ? d.get("health").getAsDouble() : 0);
        m.put("food", d.has("food") ? d.get("food").getAsInt() : 0);
        m.put("time", d.has("time") ? d.get("time").getAsLong() : 0);
        return m;
    }

    /** one player: the inventory right now (live from the game, the live save point or the stored one) + backups */
    Map<String, Object> inspect(UUID id) {
        State s = state(id);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Player online = Bukkit.getPlayer(id);
        out.put("uuid", id.toString());
        out.put("name", online != null ? online.getName() : s.name);
        out.put("where", where(id, s));
        JsonObject now = online != null ? snapshot(online, new JsonObject()) : s.mode == Mode.PLOT && s.checkpoint != null ? s.checkpoint : s.data;
        out.put("source", online != null ? "live (survival)" : s.mode == Mode.PLOT && s.checkpoint != null ? "live save point (plot)" : "stored");
        out.put("current", view(now));
        java.util.List<Map<String, Object>> backups = new java.util.ArrayList<>();
        File[] all = backupDir(id).listFiles((d, n) -> n.endsWith(".json"));
        if (all != null) {
            java.util.Arrays.sort(all, java.util.Comparator.comparing(File::getName).reversed());
            for (File f : all) {
                String n = f.getName().substring(0, f.getName().length() - 5);
                int dash = n.indexOf('-');
                Map<String, Object> b = new java.util.LinkedHashMap<>();
                b.put("id", n);
                b.put("time", dash > 0 ? Long.parseLong(n.substring(0, dash)) : 0);
                b.put("reason", dash > 0 ? n.substring(dash + 1) : "");
                backups.add(b);
            }
        }
        out.put("backups", backups);
        return out;
    }

    Map<String, Object> backupView(UUID id, String backupId) throws IOException {
        if (!backupId.matches("[0-9]+-[a-z-]+")) {
            return Map.of("error", "id");
        }
        File f = new File(backupDir(id), backupId + ".json");
        if (!f.isFile()) {
            return Map.of("error", "missing");
        }
        JsonObject d = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
        return view(d);
    }

    /** admin: put a backup back. Online here → right away; offline → on his next join here. */
    Map<String, Object> restore(UUID id, String backupId, String by) throws IOException {
        if (!backupId.matches("[0-9]+-[a-z-]+")) {
            return Map.of("error", "id");
        }
        File f = new File(backupDir(id), backupId + ".json");
        if (!f.isFile()) {
            return Map.of("error", "missing");
        }
        JsonObject d = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
        State s = state(id);
        if (s.mode == Mode.PLOT) {
            return Map.of("error", "on the plot server right now – restore when he is back or offline");
        }
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            if (busy(online)) {
                return Map.of("error", "just arriving – try again in a moment");
            }
            backup(id, snapshot(online, new JsonObject()), "before-restore");
            apply(online, d);
            plugin.getLogger().info("server-sync: " + by + " restored backup " + backupId + " for " + online.getName());
            return Map.of("ok", true, "applied", "now");
        }
        if (s.data != null) {
            backup(id, s.data, "before-restore");
        }
        d.remove("fly-until");
        s.mode = Mode.RETURNED;
        s.session = "";
        s.data = d;
        s.checkpoint = null;
        s.since = System.currentTimeMillis();
        save(id, s);
        plugin.getLogger().info("server-sync: " + by + " restored backup " + backupId + " for " + s.name + " (on next join)");
        return Map.of("ok", true, "applied", "next join");
    }
}
