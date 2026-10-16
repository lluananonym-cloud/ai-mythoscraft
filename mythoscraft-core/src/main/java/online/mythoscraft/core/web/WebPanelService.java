package online.mythoscraft.core.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.rank.MythosOwner;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Admin web panel served by the plugin itself. Login works through the server: the website shows a code, an allowed
 * player types {@code /website login <code>} in game and the browser is signed in (session cookie, HttpOnly,
 * SameSite=Strict). MinePro641 always has full access and decides who else may use it (/website allow).
 *
 * <p>Roles: view (live stats), edit (buttons/actions, player changes), admin (console + config files). Only the owner
 * manages access. Every Bukkit call runs on the main thread.</p>
 */
public final class WebPanelService {

    public enum Role { VIEW, EDIT, ADMIN, OWNER;

        boolean atLeast(Role r) {
            return ordinal() >= r.ordinal();
        }
    }

    /** player = website sign-in of a normal player (daily reward, applications), else team panel */
    private record Challenge(String code, long expires, String ip, UUID[] confirmed, String[] name, boolean player) {
    }

    private record Session(UUID uuid, String name, long expires) {
    }

    private static final String COOKIE = "mcpanel";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final MythosCraft plugin;
    private final File accessFile;
    private final File sessionFile;
    private HttpServer server;
    private ExecutorService executor;
    private boolean enabled;
    private int port = 8085;
    private String bind = "0.0.0.0";
    private String publicUrl = "";
    private RelayBridge relay;
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, int[]> rate = new ConcurrentHashMap<>();
    private final AtomicInteger liveStreams = new AtomicInteger();
    private final long started = System.currentTimeMillis();
    private final List<String> log = java.util.Collections.synchronizedList(new ArrayList<>());

    public WebPanelService(MythosCraft plugin) {
        this.plugin = plugin;
        this.accessFile = new File(plugin.getDataFolder(), "data/web-access.yml");
        this.sessionFile = new File(plugin.getDataFolder(), "data/web-sessions.yml");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("web-panel");
        boolean wasEnabled = enabled;
        int oldPort = port;
        enabled = cfg.bool("enabled", true);
        port = cfg.integer("port", 8085, 1, 65535);
        bind = cfg.string("bind", "0.0.0.0");
        publicUrl = cfg.string("public-url", "").trim();
        loadSessions();
        // relay mode (host without extra ports): the server connects OUT to the Netlify site
        if (relay != null) {
            relay.stop();
            relay = null;
        }
        String relayUrl = cfg.string("relay.url", "").trim();
        String secret = cfg.string("relay.secret", "").trim();
        if (enabled && !relayUrl.isEmpty()) {
            if (secret.length() < 24) {
                secret = random(30);
                plugin.getConfig().set("web-panel.relay.secret", secret);
                plugin.saveConfig();
                plugin.getLogger().warning("Web panel relay: a new secret was created – copy it to Netlify (PANEL_SECRET): /website relay");
            }
            relay = new RelayBridge(plugin, this, relayUrl, secret);
            relay.start();
            if (publicUrl.isEmpty()) {
                publicUrl = relayUrl;
            }
        }
        if (!enabled) {
            stop();
            return;
        }
        if (server != null && wasEnabled && oldPort == port) {
            return;
        }
        stopServer();
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 32);
            executor = Executors.newFixedThreadPool(10, r -> {
                Thread t = new Thread(r, "MythosCraft-WebPanel");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            plugin.getLogger().info("Web panel running on port " + port + " – sign in with /website login <code>");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Web panel could not start on port " + port + ": " + e.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (relay != null) {
            relay.stop();
            relay = null;
        }
        stopServer();
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public String url() {
        if (!publicUrl.isEmpty()) {
            return publicUrl;
        }
        String host = Bukkit.getIp() == null || Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
        return "http://" + host + ":" + port;
    }

    // ---- access -------------------------------------------------------------------------------------------

    private YamlConfiguration access() {
        return YamlConfiguration.loadConfiguration(accessFile);
    }

    public Role role(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (MythosOwner.is(uuid)) {
            return Role.OWNER;
        }
        String r = access().getString("users." + uuid + ".role");
        if (r == null) {
            return null;
        }
        try {
            Role role = Role.valueOf(r.toUpperCase(Locale.ROOT));
            return role == Role.OWNER ? Role.ADMIN : role;
        } catch (IllegalArgumentException e) {
            return Role.VIEW;
        }
    }

    public void allow(UUID uuid, String name, Role role) {
        YamlConfiguration y = access();
        y.set("users." + uuid + ".name", name);
        y.set("users." + uuid + ".role", role.name().toLowerCase(Locale.ROOT));
        save(y, accessFile);
    }

    public boolean deny(UUID uuid) {
        YamlConfiguration y = access();
        if (!y.contains("users." + uuid)) {
            return false;
        }
        y.set("users." + uuid, null);
        save(y, accessFile);
        sessions.entrySet().removeIf(e -> e.getValue().uuid().equals(uuid));
        saveSessions();
        return true;
    }

    private static void save(YamlConfiguration y, File f) {
        try {
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (IOException ignored) {
            // logged by caller paths where it matters
        }
    }

    // ---- login --------------------------------------------------------------------------------------------

    /** /website login <code> – confirms the browser challenge for this player. */
    public String confirm(Player p, String code) {
        String c = code.trim().toUpperCase(Locale.ROOT).replace("MC-", "");
        if (relay != null) {
            // every player may sign in on the website (daily rewards); the panel itself still checks the access role
            relay.confirm(p, c);
            audit(p.getName(), "Login-Code an die Website geschickt");
            return "sent";
        }
        long now = System.currentTimeMillis();
        challenges.values().removeIf(ch -> ch.expires() < now);
        for (Challenge ch : challenges.values()) {
            if (ch.code().equals(c)) {
                if (!ch.player() && role(p.getUniqueId()) == null) {
                    return "denied";
                }
                ch.confirmed()[0] = p.getUniqueId();
                ch.name()[0] = p.getName();
                audit(p.getName(), "login bestätigt (" + ch.ip() + ")");
                return "ok";
            }
        }
        return "unknown";
    }

    private static String random(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void loadSessions() {
        sessions.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(sessionFile);
        var sec = y.getConfigurationSection("sessions");
        if (sec == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String h : sec.getKeys(false)) {
            long exp = sec.getLong(h + ".expires");
            if (exp > now) {
                try {
                    sessions.put(h, new Session(UUID.fromString(sec.getString(h + ".uuid")), sec.getString(h + ".name", "?"), exp));
                } catch (IllegalArgumentException ignored) {
                    // broken entry
                }
            }
        }
    }

    private void saveSessions() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            y.set("sessions." + e.getKey() + ".uuid", e.getValue().uuid().toString());
            y.set("sessions." + e.getKey() + ".name", e.getValue().name());
            y.set("sessions." + e.getKey() + ".expires", e.getValue().expires());
        }
        save(y, sessionFile);
    }

    private Session session(HttpExchange ex) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String part : cookies.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(COOKIE)) {
                Session s = sessions.get(hash(kv[1]));
                if (s != null && s.expires() > System.currentTimeMillis() && role(s.uuid()) != null) {
                    return s;
                }
            }
        }
        return null;
    }

    private boolean limited(HttpExchange ex, int perMinute) {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        long minute = System.currentTimeMillis() / 60_000;
        int[] r = rate.computeIfAbsent(ip, k -> new int[]{(int) minute, 0});
        synchronized (r) {
            if (r[0] != (int) minute) {
                r[0] = (int) minute;
                r[1] = 0;
            }
            return ++r[1] > perMinute;
        }
    }

    List<String> logLines() {
        return new ArrayList<>(log);
    }

    YamlConfiguration accessConfig() {
        return access();
    }

    boolean relayConnected() {
        return relay != null && relay.connected();
    }

    boolean relayMode() {
        return relay != null;
    }

    public boolean relayStatus() {
        return relayConnected();
    }

    public String secretForOwner() {
        return relaySecret();
    }

    String relaySecret() {
        return plugin.getConfig().getString("web-panel.relay.secret", "");
    }

    void audit(String who, String what) {
        String line = java.time.LocalDateTime.now().withNano(0) + " " + who + ": " + what;
        log.add(line);
        while (log.size() > 200) {
            log.remove(0);
        }
        plugin.getLogger().info("[WebPanel] " + who + ": " + what);
    }

    // ---- http ---------------------------------------------------------------------------------------------

    private void handle(HttpExchange ex) {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("X-Frame-Options", "DENY");
            ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            if (!path.startsWith("/api/")) {
                staticFile(ex, path);
                return;
            }
            boolean post = "POST".equalsIgnoreCase(ex.getRequestMethod());
            // simple CSRF protection: our page always sends this header, other sites cannot without CORS
            if (post && !"1".equals(ex.getRequestHeaders().getFirst("X-Panel"))) {
                json(ex, 403, Map.of("error", "csrf"));
                return;
            }
            switch (path) {
                case "/api/public" -> {
                    // wiki website: public data, no login
                    ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    json(ex, 200, sync(() -> plugin.wiki().snapshot()));
                }
                case "/api/public/plotperks" -> {
                    // plot server: rank + how many plots this player may claim (no secrets – just the rank)
                    if (limited(ex, 240)) {
                        json(ex, 429, Map.of("error", "rate"));
                        return;
                    }
                    json(ex, 200, plotPerks(query(ex).getOrDefault("uuid", "")));
                }
                case "/api/login/start" -> loginStart(ex);
                case "/api/apply/decide" -> {
                    if (limited(ex, 30)) {
                        json(ex, 429, Map.of("error", "rate"));
                        return;
                    }
                    plugin.playerSite().decideLink(ex, this);
                }
                case "/api/apply", "/api/player/me", "/api/player/claim", "/api/player/logout" -> {
                    if (limited(ex, 120)) {
                        json(ex, 429, Map.of("error", "rate"));
                        return;
                    }
                    plugin.playerSite().handle(ex, path, post, this);
                }
                case "/api/login/poll" -> loginPoll(ex);
                case "/api/logout" -> {
                    Session s = session(ex);
                    String cookie = ex.getRequestHeaders().getFirst("Cookie");
                    if (s != null && cookie != null) {
                        sessions.entrySet().removeIf(e -> e.getValue() == s);
                        saveSessions();
                    }
                    ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
                    json(ex, 200, Map.of("ok", true));
                }
                default -> authed(ex, path, post);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Web panel request failed", e);
            try {
                json(ex, 500, Map.of("error", "server", "message", String.valueOf(e.getMessage())));
            } catch (Exception ignored) {
                // response already started
            }
        }
    }

    private void loginStart(HttpExchange ex) throws IOException {
        if (limited(ex, 12)) {
            json(ex, 429, Map.of("error", "rate"));
            return;
        }
        long now = System.currentTimeMillis();
        challenges.values().removeIf(ch -> ch.expires() < now);
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        String id = random(24);
        boolean player = "player".equals(query(ex).get("kind"));
        challenges.put(id, new Challenge(code.toString(), now + 180_000, ex.getRemoteAddress().getAddress().getHostAddress(),
                new UUID[1], new String[1], player));
        json(ex, 200, Map.of("id", id, "code", code.toString(), "expires", 180));
    }

    private void loginPoll(HttpExchange ex) throws IOException {
        if (limited(ex, 120)) {
            json(ex, 429, Map.of("error", "rate"));
            return;
        }
        String id = query(ex).getOrDefault("id", "");
        Challenge ch = challenges.get(id);
        if (ch == null || ch.expires() < System.currentTimeMillis()) {
            json(ex, 200, Map.of("state", "expired"));
            return;
        }
        if (ch.confirmed()[0] == null) {
            json(ex, 200, Map.of("state", "waiting"));
            return;
        }
        challenges.remove(id);
        if (ch.player()) {
            ex.getResponseHeaders().add("Set-Cookie", plugin.playerSite().signIn(ch.confirmed()[0], ch.name()[0]));
            json(ex, 200, Map.of("state", "ok", "name", ch.name()[0], "uuid", ch.confirmed()[0].toString()));
            return;
        }
        if (role(ch.confirmed()[0]) == null) {
            json(ex, 200, Map.of("state", "denied"));
            return;
        }
        String token = random(32);
        sessions.put(hash(token), new Session(ch.confirmed()[0], ch.name()[0], System.currentTimeMillis() + 7L * 24 * 3600_000));
        saveSessions();
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + token + "; Path=/; Max-Age=604800; HttpOnly; SameSite=Strict");
        json(ex, 200, Map.of("state", "ok", "name", ch.name()[0]));
    }

    private void authed(HttpExchange ex, String path, boolean post) throws Exception {
        Session s = session(ex);
        if (s == null) {
            json(ex, 401, Map.of("error", "login"));
            return;
        }
        Role role = role(s.uuid());
        if (limited(ex, 600)) {
            json(ex, 429, Map.of("error", "rate"));
            return;
        }
        switch (path) {
            case "/api/me" -> json(ex, 200, Map.of("name", s.name(), "uuid", s.uuid().toString(), "role", role.name().toLowerCase(Locale.ROOT),
                    "owner", role == Role.OWNER, "url", url()));
            case "/api/stats" -> json(ex, 200, sync(this::snapshot));
            case "/api/live" -> live(ex, s);
            case "/api/player" -> json(ex, 200, sync(() -> player(query(ex).getOrDefault("name", ""))));
            case "/api/actions" -> json(ex, 200, sync(() -> WebActions.definitions(plugin)));
            case "/api/log" -> json(ex, 200, Map.of("log", new ArrayList<>(log)));
            case "/api/action" -> {
                if (!post || !role.atLeast(Role.EDIT)) {
                    json(ex, 403, Map.of("error", "role"));
                    return;
                }
                JsonObject body = body(ex);
                sync(() -> WebActions.definitions(plugin));
                String id = body.has("id") ? body.get("id").getAsString() : "";
                Map<String, String> params = new HashMap<>();
                if (body.has("params") && body.get("params").isJsonObject()) {
                    body.getAsJsonObject("params").entrySet().forEach(e -> params.put(e.getKey(), e.getValue().getAsString()));
                }
                String command;
                try {
                    command = WebActions.build(id, params, role.atLeast(Role.ADMIN));
                } catch (IllegalArgumentException e) {
                    json(ex, 400, Map.of("error", e.getMessage()));
                    return;
                }
                audit(s.name(), "Aktion " + id + ": /" + command);
                json(ex, 200, Map.of("command", command, "output", sync(() -> run(command, s))));
            }
            case "/api/command" -> {
                if (!post || !role.atLeast(Role.ADMIN)) {
                    json(ex, 403, Map.of("error", "role"));
                    return;
                }
                String command = body(ex).get("command").getAsString().trim();
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                if (command.isEmpty() || command.length() > 500) {
                    json(ex, 400, Map.of("error", "command"));
                    return;
                }
                String cmd = command;
                audit(s.name(), "Konsole: /" + cmd);
                json(ex, 200, Map.of("output", sync(() -> run(cmd, s))));
            }
            case "/api/config" -> config(ex, s, role, post);
            case "/api/access" -> accessApi(ex, s, role, post);
            default -> json(ex, 404, Map.of("error", "unknown"));
        }
    }

    /** Runs a command as the console and returns everything it answered. */
    List<String> run(String command) {
        return run(command, null);
    }

    private List<String> run(String command, Session s) {
        List<String> out = new ArrayList<>();
        try {
            org.bukkit.command.CommandSender sender;
            try {
                // console rights, but every answer comes back to the website
                sender = Bukkit.createCommandSender(c -> out.add(PlainTextComponentSerializer.plainText().serialize(c)));
            } catch (RuntimeException unsupported) {
                sender = Bukkit.getConsoleSender();
            }
            boolean ok = Bukkit.dispatchCommand(sender, command);
            if (!ok) {
                out.add("Unbekannter Befehl.");
            }
        } catch (RuntimeException e) {
            out.add("Fehler: " + e.getMessage());
        }
        if (out.isEmpty()) {
            out.add("✓ ausgeführt");
        }
        return out;
    }

    private void live(HttpExchange ex, Session s) throws IOException {
        if (liveStreams.incrementAndGet() > 8) {
            liveStreams.decrementAndGet();
            json(ex, 429, Map.of("error", "streams"));
            return;
        }
        try {
            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, 0);
            OutputStream out = ex.getResponseBody();
            for (int i = 0; i < 1800; i++) { // max. 1 hour, the page reconnects
                if (session(ex) == null || server == null) {
                    break;
                }
                Object snap = sync(this::snapshot);
                out.write(("data: " + GSON.toJson(snap) + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            // browser closed the page
        } finally {
            liveStreams.decrementAndGet();
        }
    }

    // ---- data ---------------------------------------------------------------------------------------------

    private static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Values the server may not offer (other server software / test servers) never break the panel. */
    private static <T> T safe(java.util.function.Supplier<T> s, T fallback) {
        try {
            return s.get();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tps", safe(() -> Math.min(20, Math.round(Bukkit.getTPS()[0] * 100) / 100.0), 20.0));
        m.put("mspt", safe(() -> Math.round(Bukkit.getAverageTickTime() * 10) / 10.0, 0.0));
        Runtime rt = Runtime.getRuntime();
        m.put("ramUsed", (rt.totalMemory() - rt.freeMemory()) / 1_048_576);
        m.put("ramMax", rt.maxMemory() / 1_048_576);
        m.put("uptime", (System.currentTimeMillis() - started) / 1000);
        m.put("online", Bukkit.getOnlinePlayers().size());
        m.put("max", Bukkit.getMaxPlayers());
        List<Map<String, Object>> players = new ArrayList<>();
        long coins = 0;
        long gems = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            var data = plugin.playerData().get(p);
            pm.put("name", p.getName());
            pm.put("uuid", p.getUniqueId().toString());
            pm.put("rank", data == null ? "" : plugin.ranks().rankOf(data).id());
            long c = data == null ? 0 : online.mythoscraft.core.economy.Currency.COINS.get(data);
            long g = data == null ? 0 : online.mythoscraft.core.economy.Currency.GEMS.get(data);
            coins += c;
            gems += g;
            pm.put("coins", c);
            pm.put("gems", g);
            pm.put("ping", safe(p::getPing, 0));
            pm.put("world", p.getWorld().getName());
            pm.put("x", p.getLocation().getBlockX());
            pm.put("y", p.getLocation().getBlockY());
            pm.put("z", p.getLocation().getBlockZ());
            pm.put("health", Math.round(p.getHealth()));
            pm.put("gamemode", p.getGameMode().name().toLowerCase(Locale.ROOT));
            players.add(pm);
        }
        m.put("players", players);
        m.put("coinsOnline", coins);
        m.put("gemsOnline", gems);
        var active = plugin.events().active();
        m.put("event", active == null ? null : Map.of("id", active.id(), "name", plain(active.displayName()),
                "seconds", active.secondsLeft(), "state", active.state().name()));
        var bg = plugin.events().background();
        m.put("background", bg == null ? null : Map.of("id", bg.id(), "name", plain(bg.displayName()), "seconds", bg.secondsLeft()));
        m.put("nextEvent", plugin.events().nextAutoMillis() / 1000);
        var season = plugin.seasons().active();
        m.put("season", season == null ? null : season.id());
        m.put("giveaway", plugin.giveaways().running());
        List<Map<String, Object>> clans = new ArrayList<>();
        for (Map.Entry<String, Long> e : plugin.clanWar().ranking()) {
            if (clans.size() >= 5) {
                break;
            }
            for (var c : plugin.clans().all()) {
                if (c.id().equals(e.getKey())) {
                    clans.add(Map.of("name", c.name(), "tag", c.tag(), "points", e.getValue()));
                }
            }
        }
        m.put("clanWar", clans);
        m.put("worlds", Bukkit.getWorlds().size());
        m.put("entities", safe(() -> Bukkit.getWorlds().stream().mapToInt(w -> w.getEntityCount()).sum(), 0));
        m.put("chunks", safe(() -> Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum(), 0));
        m.put("time", System.currentTimeMillis());
        return m;
    }

    Map<String, Object> player(String name) {
        Player p = Bukkit.getPlayerExact(name);
        Map<String, Object> m = new LinkedHashMap<>();
        if (p == null) {
            m.put("error", "offline");
            return m;
        }
        var data = plugin.playerData().get(p);
        m.put("name", p.getName());
        m.put("uuid", p.getUniqueId().toString());
        m.put("rank", data == null ? "" : plugin.ranks().rankOf(data).id());
        m.put("coins", data == null ? 0 : online.mythoscraft.core.economy.Currency.COINS.get(data));
        m.put("gems", data == null ? 0 : online.mythoscraft.core.economy.Currency.GEMS.get(data));
        m.put("homes", data == null ? 0 : data.homes().size());
        m.put("health", Math.round(p.getHealth()));
        m.put("food", p.getFoodLevel());
        m.put("level", p.getLevel());
        m.put("world", p.getWorld().getName());
        m.put("location", p.getLocation().getBlockX() + " " + p.getLocation().getBlockY() + " " + p.getLocation().getBlockZ());
        Map<String, Object> jobs = new LinkedHashMap<>();
        for (online.mythoscraft.core.progress.Job job : online.mythoscraft.core.progress.Job.values()) {
            jobs.put(job.id(), plugin.progress().loaded(p) ? plugin.jobs().level(p, job) : 0);
        }
        m.put("jobs", jobs);
        List<String> inv = new ArrayList<>();
        for (var item : p.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                inv.add(item.getAmount() + "× " + item.getType().name().toLowerCase(Locale.ROOT));
            }
        }
        m.put("inventory", inv);
        return m;
    }

    // ---- config files ------------------------------------------------------------------------------------

    private static final List<String> FILES = List.of("config.yml", "messages.yml", "messages_en.yml", "crates.yml");

    private void config(HttpExchange ex, Session s, Role role, boolean post) throws Exception {
        if (!role.atLeast(Role.ADMIN)) {
            json(ex, 403, Map.of("error", "role"));
            return;
        }
        String name = post ? body(ex).get("file").getAsString() : query(ex).getOrDefault("file", "config.yml");
        if (!FILES.contains(name)) {
            json(ex, 400, Map.of("error", "file"));
            return;
        }
        File f = new File(plugin.getDataFolder(), name);
        if (!post) {
            json(ex, 200, Map.of("file", name, "files", FILES, "text", f.exists() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : ""));
            return;
        }
        String text = lastBody.get().get("text").getAsString();
        YamlConfiguration test = new YamlConfiguration();
        try {
            test.loadFromString(text);
        } catch (Exception e) {
            json(ex, 400, Map.of("error", "yaml", "message", e.getMessage()));
            return;
        }
        File backup = new File(plugin.getDataFolder(), "backups/web-" + System.currentTimeMillis() + "-" + name);
        backup.getParentFile().mkdirs();
        if (f.exists()) {
            Files.copy(f.toPath(), backup.toPath());
        }
        Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
        audit(s.name(), "Datei gespeichert: " + name);
        json(ex, 200, Map.of("ok", true, "output", sync(() -> run("mythos reload", s))));
    }

    Map<String, Object> readFile(String name) throws IOException {
        if (!FILES.contains(name)) {
            throw new IllegalArgumentException("Datei nicht erlaubt");
        }
        File f = new File(plugin.getDataFolder(), name);
        return Map.of("file", name, "files", FILES, "text", f.exists() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : "");
    }

    Map<String, Object> writeFile(String name, String text, String by) throws IOException {
        if (!FILES.contains(name)) {
            throw new IllegalArgumentException("Datei nicht erlaubt");
        }
        try {
            new YamlConfiguration().loadFromString(text);
        } catch (Exception e) {
            return Map.of("error", "yaml", "message", String.valueOf(e.getMessage()));
        }
        File f = new File(plugin.getDataFolder(), name);
        File backup = new File(plugin.getDataFolder(), "backups/web-" + System.currentTimeMillis() + "-" + name);
        backup.getParentFile().mkdirs();
        if (f.exists()) {
            Files.copy(f.toPath(), backup.toPath());
        }
        Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
        audit(by, "Datei gespeichert: " + name);
        return Map.of("ok", true);
    }

    Map<String, Object> accessList() {
        List<Map<String, String>> users = new ArrayList<>();
        var sec = access().getConfigurationSection("users");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                users.add(Map.of("uuid", id, "name", sec.getString(id + ".name", "?"), "role", sec.getString(id + ".role", "view")));
            }
        }
        return Map.of("users", users);
    }

    String setAccess(String name, String r, String by) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            return "name";
        }
        @SuppressWarnings("deprecation")
        var off = Bukkit.getOfflinePlayer(name);
        audit(by, "Zugriff " + name + " → " + r);
        if (r.equals("remove")) {
            return deny(off.getUniqueId()) ? "ok" : "unknown";
        }
        try {
            Role role = Role.valueOf(r.toUpperCase(Locale.ROOT));
            allow(off.getUniqueId(), name, role == Role.OWNER ? Role.ADMIN : role);
        } catch (IllegalArgumentException e) {
            return "role";
        }
        return "ok";
    }

    private void accessApi(HttpExchange ex, Session s, Role role, boolean post) throws Exception {
        if (role != Role.OWNER) {
            json(ex, 403, Map.of("error", "owner"));
            return;
        }
        if (post) {
            JsonObject b = body(ex);
            String name = b.get("name").getAsString();
            String r = b.get("role").getAsString();
            Object result = sync(() -> {
                @SuppressWarnings("deprecation")
                var off = Bukkit.getOfflinePlayer(name);
                if (r.equals("remove")) {
                    return deny(off.getUniqueId()) ? "ok" : "unknown";
                }
                try {
                    allow(off.getUniqueId(), name, Role.valueOf(r.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return "role";
                }
                return "ok";
            });
            audit(s.name(), "Zugriff " + name + " → " + r);
            json(ex, 200, Map.of("result", result));
            return;
        }
        List<Map<String, String>> users = new ArrayList<>();
        var sec = access().getConfigurationSection("users");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                users.add(Map.of("uuid", id, "name", sec.getString(id + ".name", "?"), "role", sec.getString(id + ".role", "view")));
            }
        }
        json(ex, 200, Map.of("users", users));
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private final ThreadLocal<JsonObject> lastBody = new ThreadLocal<>();

    private JsonObject body(HttpExchange ex) throws IOException {
        JsonObject cached = lastBody.get();
        if (cached != null && ex.getAttribute("bodyRead") != null) {
            return cached;
        }
        byte[] raw = ex.getRequestBody().readNBytes(2_000_000);
        JsonObject o;
        try {
            o = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            o = new JsonObject();
        }
        lastBody.set(o);
        ex.setAttribute("bodyRead", Boolean.TRUE);
        return o;
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) {
            return m;
        }
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            m.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }

    private Map<String, Object> plotPerks(String id) throws Exception {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Map.of("error", "uuid");
        }
        online.mythoscraft.core.player.PlayerData.Snapshot snap = plugin.playerData().loadSnapshot(uuid).get(8, TimeUnit.SECONDS);
        return sync(() -> {
            var ranks = plugin.ranks();
            online.mythoscraft.core.rank.Rank r = snap == null ? null : ranks.byId(snap.rankId());
            boolean expired = snap != null && snap.rankExpires() > 0 && snap.rankExpires() < System.currentTimeMillis();
            if (online.mythoscraft.core.rank.MythosOwner.is(uuid) && (r == null || expired)) {
                r = ranks.mythos();
            } else if (r == null || expired) {
                r = ranks.defaultRank();
            }
            int plots = plugin.rankPerks().plotsFor(r.id());
            online.mythoscraft.core.rank.Rank mod = ranks.byId("MOD");
            if (mod != null && r.weight() >= mod.weight()) {
                plots = Math.max(plots, plugin.getConfig().getInt("plot-server.staff-plots", 5));
            }
            boolean staff = mod != null && r.weight() >= mod.weight();
            var gson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rank", r.id());
            out.put("display", r.display());
            out.put("plots", plots);
            // looks of the rank for the plot server (chat, tab list, name tag) – as JSON text components
            out.put("prefix", gson.serialize(plugin.display().prefix(r, staff, true)));
            out.put("text-prefix", gson.serialize(plugin.display().prefix(r, staff, false)));
            out.put("name-color", r.teamColor().toString());
            out.put("chat-color", r.chatColor() == null ? "#AAAAAA" : r.chatColor().asHexString());
            out.put("weight", r.weight());
            return out;
        });
    }

    <T> T sync(java.util.concurrent.Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return task.call();
        }
        java.util.concurrent.CompletableFuture<T> f = new java.util.concurrent.CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                f.complete(task.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    private static void json(HttpExchange ex, int status, Object body) throws IOException {
        byte[] b = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    private void staticFile(HttpExchange ex, String path) throws IOException {
        // the public wiki is the start page, the team panel lives under /panel/
        if (path.isEmpty() || path.equals("/") || path.equals("/wiki") || path.equals("/panel")) {
            String to = path.equals("/panel") ? "/panel/" : "/wiki/";
            ex.getResponseHeaders().set("Location", to);
            ex.sendResponseHeaders(302, -1);
            return;
        }
        if (path.endsWith("/")) {
            path = path + "index.html";
        }
        if (path.contains("..") || !path.matches("/[a-zA-Z0-9_./-]+")) {
            ex.sendResponseHeaders(404, -1);
            return;
        }
        byte[] data;
        try (InputStream in = plugin.getResource("web" + path)) {
            if (in == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            data = buf.toByteArray();
        }
        String type = path.endsWith(".html") ? "text/html; charset=utf-8" : path.endsWith(".js") ? "text/javascript; charset=utf-8"
                : path.endsWith(".css") ? "text/css; charset=utf-8" : path.endsWith(".png") ? "image/png"
                : path.endsWith(".svg") ? "image/svg+xml" : path.endsWith(".json") ? "application/json; charset=utf-8"
                : path.endsWith(".ico") ? "image/x-icon" : "application/octet-stream";
        ex.getResponseHeaders().set("Content-Type", type);
        // textures and 3D models change only with a new plugin version
        ex.getResponseHeaders().set("Cache-Control", path.startsWith("/wiki/tex/") || path.startsWith("/wiki/models/")
                ? "public, max-age=3600" : "no-cache");
        if (path.endsWith(".html")) {
            ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self' https://unpkg.com; "
                    + "img-src 'self' data: https://mc-heads.net https://crafatar.com https://textures.minecraft.net; "
                    + "connect-src 'self' https://mc-heads.net https://crafatar.com; style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; "
                    + "font-src https://fonts.gstatic.com; frame-ancestors 'none'");
        }
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
