package online.mythoscraft.core.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player side of the website (served by the plugin itself): players sign in with their Minecraft account
 * (/website login &lt;code&gt;), claim the daily website reward and apply as moderator. Staff read and answer the
 * applications in game with /applications.
 */
public final class PlayerSite extends BaseCommand implements Listener {

    static final String COOKIE = "mcplayer";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private record PSession(UUID uuid, String name, long expires) {
    }

    private final File sessionFile;
    private final File dailyFile;
    private final File appFile;
    private final Map<String, PSession> sessions = new ConcurrentHashMap<>();
    private YamlConfiguration daily;
    private YamlConfiguration apps;

    public PlayerSite(MythosCraft plugin) {
        super(plugin);
        sessionFile = new File(plugin.getDataFolder(), "data/web-player-sessions.yml");
        dailyFile = new File(plugin.getDataFolder(), "data/web-daily.yml");
        appFile = new File(plugin.getDataFolder(), "data/applications.yml");
        load();
    }

    private synchronized void load() {
        sessions.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(sessionFile);
        ConfigurationSection sec = y.getConfigurationSection("sessions");
        long now = System.currentTimeMillis();
        if (sec != null) {
            for (String h : sec.getKeys(false)) {
                long exp = sec.getLong(h + ".expires");
                if (exp > now) {
                    try {
                        sessions.put(h, new PSession(UUID.fromString(sec.getString(h + ".uuid")), sec.getString(h + ".name", "?"), exp));
                    } catch (IllegalArgumentException ignored) {
                        // broken entry
                    }
                }
            }
        }
        daily = YamlConfiguration.loadConfiguration(dailyFile);
        apps = YamlConfiguration.loadConfiguration(appFile);
    }

    private synchronized void save(YamlConfiguration y, File f) {
        try {
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning(f.getName() + " could not be saved: " + e.getMessage());
        }
    }

    private void saveSessions() {
        YamlConfiguration y = new YamlConfiguration();
        sessions.forEach((h, s) -> {
            y.set("sessions." + h + ".uuid", s.uuid().toString());
            y.set("sessions." + h + ".name", s.name());
            y.set("sessions." + h + ".expires", s.expires());
        });
        save(y, sessionFile);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A confirmed player login: new session, returns the Set-Cookie header. */
    String signIn(UUID uuid, String name) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        sessions.put(hash(token), new PSession(uuid, name, System.currentTimeMillis() + 30L * 24 * 3600_000));
        saveSessions();
        return COOKIE + "=" + token + "; Path=/; Max-Age=2592000; HttpOnly; SameSite=Lax";
    }

    private PSession session(HttpExchange ex) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String part : cookies.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(COOKIE)) {
                PSession s = sessions.get(hash(kv[1]));
                if (s != null && s.expires() > System.currentTimeMillis()) {
                    return s;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------- http
    /** /api/player/* and /api/apply */
    void handle(HttpExchange ex, String path, boolean post, WebPanelService web) throws Exception {
        if (path.equals("/api/player/logout")) {
            String cookies = ex.getRequestHeaders().getFirst("Cookie");
            if (cookies != null) {
                for (String part : cookies.split(";")) {
                    String[] kv = part.trim().split("=", 2);
                    if (kv.length == 2 && kv[0].equals(COOKIE)) {
                        sessions.remove(hash(kv[1]));
                    }
                }
                saveSessions();
            }
            ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
            json(ex, 200, Map.of("ok", true));
            return;
        }
        PSession me = session(ex);
        if (path.equals("/api/apply") && !post) {
            json(ex, 200, web.sync(() -> applyInfo(me)));
            return;
        }
        if (me == null) {
            json(ex, 401, Map.of("error", "login"));
            return;
        }
        switch (path) {
            case "/api/player/me" -> json(ex, 200, Map.of("name", me.name(), "uuid", me.uuid().toString(), "daily", web.sync(() -> dailyState(me.uuid()))));
            case "/api/player/claim" -> {
                if (!post) {
                    json(ex, 405, Map.of("error", "method"));
                    return;
                }
                Map<String, Object> result = web.sync(() -> claim(me));
                json(ex, result.containsKey("error") ? 409 : 200, result);
            }
            case "/api/apply" -> {
                JsonObject body = readBody(ex);
                Map<String, Object> result = web.sync(() -> submit(me, body));
                json(ex, result.containsKey("error") ? 400 : 200, result);
            }
            default -> json(ex, 404, Map.of("error", "unknown"));
        }
    }

    // ------------------------------------------------------------------------------------------- daily
    private Map<String, Object> dailyState(UUID uuid) {
        String key = uuid.toString();
        String last = daily.getString(key + ".last");
        int streak = daily.getInt(key + ".streak", 0);
        LocalDate today = plugin.multipliers().today();
        boolean canClaim = !today.toString().equals(last);
        int next = !canClaim ? streak : (today.minusDays(1).toString().equals(last) ? streak + 1 : 1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("last", last);
        m.put("streak", streak);
        m.put("total", daily.getInt(key + ".total", 0));
        m.put("today", today.toString());
        m.put("canClaim", canClaim && plugin.webDaily() != null && plugin.webDaily().enabled());
        m.put("nextStreak", next);
        m.put("resetIn", plugin.multipliers().untilMidnight().toMillis());
        return m;
    }

    private Map<String, Object> claim(PSession me) {
        Map<String, Object> st = dailyState(me.uuid());
        if (!Boolean.TRUE.equals(st.get("canClaim"))) {
            return Map.of("error", "already", "daily", st);
        }
        int streak = (int) st.get("nextStreak");
        String key = me.uuid().toString();
        daily.set(key + ".last", st.get("today"));
        daily.set(key + ".streak", streak);
        daily.set(key + ".total", daily.getInt(key + ".total", 0) + 1);
        save(daily, dailyFile);
        plugin.webDaily().claimed(me.uuid(), me.name(), streak);
        return Map.of("ok", true, "streak", streak, "daily", dailyState(me.uuid()));
    }

    // ------------------------------------------------------------------------------------------- applications
    private boolean appsOpen() {
        return plugin.getConfig().getBoolean("mod-applications.enabled", true);
    }

    private List<String> questions() {
        return plugin.getConfig().getStringList("mod-applications.questions");
    }

    /** latest application of a player (id) or null */
    private String latestOf(UUID uuid) {
        ConfigurationSection root = apps.getConfigurationSection("applications");
        if (root == null) {
            return null;
        }
        String found = null;
        long newest = -1;
        for (String id : root.getKeys(false)) {
            if (uuid.toString().equals(root.getString(id + ".uuid")) && root.getLong(id + ".created") > newest) {
                newest = root.getLong(id + ".created");
                found = id;
            }
        }
        return found;
    }

    private Map<String, Object> applyInfo(PSession me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", appsOpen());
        m.put("questions", questions());
        m.put("minHours", plugin.getConfig().getDouble("mod-applications.min-playtime-hours", 5));
        if (me != null) {
            String id = latestOf(me.uuid());
            if (id != null) {
                ConfigurationSection a = apps.getConfigurationSection("applications." + id);
                Map<String, Object> mine = new LinkedHashMap<>();
                mine.put("id", id);
                mine.put("status", a.getString("status", "open"));
                mine.put("created", a.getLong("created"));
                mine.put("note", a.getString("note", ""));
                m.put("mine", mine);
            }
        }
        return m;
    }

    private Map<String, Object> submit(PSession me, JsonObject body) {
        if (!appsOpen()) {
            return Map.of("error", "closed");
        }
        String last = latestOf(me.uuid());
        if (last != null) {
            ConfigurationSection a = apps.getConfigurationSection("applications." + last);
            String status = a.getString("status", "open");
            long days = plugin.getConfig().getLong("mod-applications.cooldown-days", 14);
            if (status.equals("open")) {
                return Map.of("error", "pending");
            }
            if (System.currentTimeMillis() - a.getLong("decided", 0) < days * 86_400_000L) {
                return Map.of("error", "cooldown", "days", days);
            }
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(me.uuid());
        double hours = off.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
        double min = plugin.getConfig().getDouble("mod-applications.min-playtime-hours", 5);
        if (hours < min) {
            return Map.of("error", "playtime", "hours", Math.round(hours * 10) / 10.0, "min", min);
        }
        List<String> qs = questions();
        JsonArray answers = body.has("answers") && body.get("answers").isJsonArray() ? body.getAsJsonArray("answers") : new JsonArray();
        List<String> clean = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) {
            String a = i < answers.size() && !answers.get(i).isJsonNull() ? answers.get(i).getAsString().trim() : "";
            if (a.isEmpty()) {
                return Map.of("error", "empty", "question", i + 1);
            }
            clean.add(a.length() > 1500 ? a.substring(0, 1500) : a);
        }
        int next = apps.getInt("next-id", 1);
        apps.set("next-id", next + 1);
        String p = "applications." + next;
        apps.set(p + ".uuid", me.uuid().toString());
        apps.set(p + ".name", me.name());
        apps.set(p + ".created", System.currentTimeMillis());
        apps.set(p + ".playtime-hours", Math.round(hours * 10) / 10.0);
        apps.set(p + ".questions", qs);
        apps.set(p + ".answers", clean);
        apps.set(p + ".status", "open");
        byte[] k = new byte[18];
        RANDOM.nextBytes(k);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(k);
        apps.set(p + ".key", key);
        save(apps, appFile);
        mail(next, me.name(), Math.round(hours * 10) / 10.0, qs, clean, key);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mythos.applications")) {
                plugin.messages().send(staff, "applications.new", "player", me.name(), "id", next);
                SoundSpec.of("block.note_block.pling", 0.7f, 1.4f).play(staff);
            }
        }
        plugin.getLogger().info("New moderator application #" + next + " from " + me.name());
        return Map.of("ok", true, "id", next);
    }

    // ------------------------------------------------------------------------------------------- /applications (staff)
    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        if (!sender.hasPermission("mythos.applications")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        ConfigurationSection root = apps.getConfigurationSection("applications");
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list", "all" -> {
                boolean all = sub.equals("all");
                plugin.messages().send(sender, "applications.header");
                int n = 0;
                if (root != null) {
                    for (String id : root.getKeys(false)) {
                        String status = root.getString(id + ".status", "open");
                        if (all || status.equals("open")) {
                            plugin.messages().sendRaw(sender, "applications.line", "id", id, "player", root.getString(id + ".name", "?"),
                                    "status", status, "hours", root.getDouble(id + ".playtime-hours"));
                            n++;
                        }
                    }
                }
                if (n == 0) {
                    plugin.messages().sendRaw(sender, "applications.none");
                }
            }
            case "view", "read" -> {
                ConfigurationSection a = app(args);
                List<String> qs = a.getStringList("questions");
                List<String> as = a.getStringList("answers");
                plugin.messages().sendRaw(sender, "applications.view-head", "id", a.getName(), "player", a.getString("name", "?"),
                        "status", a.getString("status", "open"), "hours", a.getDouble("playtime-hours"));
                for (int i = 0; i < qs.size(); i++) {
                    plugin.messages().sendRaw(sender, "applications.view-qa", "n", i + 1, "question", qs.get(i),
                            "answer", i < as.size() ? as.get(i) : "-");
                }
                plugin.messages().sendRaw(sender, "applications.view-actions", "id", a.getName());
            }
            case "accept", "deny" -> {
                ConfigurationSection a = app(args);
                String note = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                decide(a, sub.equals("accept"), note, sender.getName());
                plugin.messages().send(sender, "applications.decided", "id", a.getName(), "player", a.getString("name", "?"),
                        "status", a.getString("status"));
            }
            default -> throw CommandFail.usage("/applications [list|all|view <id>|accept <id> [note]|deny <id> [note]]");
        }
    }

    /** accept / deny – an accepted moderator gets the configured command (default: rank set {player} MOD) */
    private void decide(ConfigurationSection a, boolean accept, String note, String by) {
        a.set("status", accept ? "accepted" : "denied");
        a.set("note", note);
        a.set("decided", System.currentTimeMillis());
        a.set("decided-by", by);
        a.set("notified", false);
        a.set("key", null);
        save(apps, appFile);
        String name = a.getString("name", "?");
        if (accept) {
            // the owner accepted (in game or with the secret link from the e-mail): the rank is set directly
            String rankId = plugin.getConfig().getString("mod-applications.accept-rank", "MOD").trim();
            var rank = plugin.ranks().byId(rankId);
            UUID uuid = UUID.fromString(a.getString("uuid"));
            if (rank != null) {
                plugin.playerData().modify(uuid, d -> d.rank(rank.id(), 0)).whenComplete((ok, ex) -> plugin.tasks().sync(() -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        plugin.ranks().applyPermissions(online);
                        plugin.display().refresh(online);
                    }
                    plugin.getLogger().info("Application #" + a.getName() + " accepted by " + by + " – " + name + " is now " + rank.id()
                            + (ex != null || !Boolean.TRUE.equals(ok) ? " (could not be saved!)" : ""));
                }));
            } else {
                plugin.getLogger().warning("mod-applications.accept-rank: unknown rank " + rankId);
            }
            String cmd = plugin.getConfig().getString("mod-applications.accept-command", "").replace("{player}", name).trim();
            if (!cmd.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        }
        Player applicant = Bukkit.getPlayer(UUID.fromString(a.getString("uuid")));
        if (applicant != null) {
            notifyApplicant(applicant, a);
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mythos.applications")) {
                plugin.messages().send(staff, "applications.decided", "id", a.getName(), "player", name, "status", a.getString("status"));
            }
        }
    }

    /** Link from the e-mail: /api/apply/decide?id=…&action=accept|deny&key=… – a small result page. */
    void decideLink(HttpExchange ex, WebPanelService web) throws Exception {
        Map<String, String> q = new java.util.HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String part : raw.split("&")) {
                String[] kv = part.split("=", 2);
                q.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
        String result = web.sync(() -> {
            ConfigurationSection a = apps.getConfigurationSection("applications." + q.getOrDefault("id", "x"));
            if (a == null) {
                return "Application not found.";
            }
            String key = a.getString("key");
            if (key == null || !java.security.MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
                    q.getOrDefault("key", "").getBytes(StandardCharsets.UTF_8))) {
                return "This link was already used or is invalid. Status: " + a.getString("status", "open");
            }
            boolean accept = "accept".equals(q.get("action"));
            decide(a, accept, "", "e-mail link");
            return accept ? "✔ " + a.getString("name") + " is now a moderator on MythosCraft." : "✖ The application of " + a.getString("name") + " was declined.";
        });
        String html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>MythosCraft</title><body style='margin:0;min-height:100vh;display:grid;place-items:center;background:#0b0814;color:#ece8ff;"
                + "font-family:system-ui,sans-serif'><div style='max-width:520px;padding:32px;border:1px solid #ffffff22;border-radius:18px;"
                + "background:#ffffff0a;text-align:center'><h2 style='margin-top:0'>MythosCraft – moderator application</h2><p style='font-size:18px'>"
                + result.replace("&", "&amp;").replace("<", "&lt;") + "</p></div></body>";
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    /** The application by e-mail through FormSubmit (formsubmit.co) – with accept / deny links. */
    private void mail(int id, String name, double hours, List<String> qs, List<String> answers, String key) {
        String to = plugin.getConfig().getString("mod-applications.email", "").trim();
        if (to.isEmpty()) {
            return;
        }
        String base = plugin.getConfig().getString("web-panel.public-url", "").trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String link = base + "/api/apply/decide?id=" + id + "&key=" + key + "&action=";
        JsonObject body = new JsonObject();
        body.addProperty("_subject", "MythosCraft: moderator application #" + id + " from " + name);
        body.addProperty("_template", "table");
        body.addProperty("_captcha", "false");
        body.addProperty("Player", name);
        body.addProperty("Play time", hours + " h");
        for (int i = 0; i < qs.size(); i++) {
            body.addProperty((i + 1) + ". " + qs.get(i), i < answers.size() ? answers.get(i) : "");
        }
        body.addProperty("✔ ACCEPT (click = rank MOD on the server)", link + "accept");
        body.addProperty("✖ DENY", link + "deny");
        String origin = base.isEmpty() ? "https://mythoscraft.online" : base;
        plugin.tasks().io("application mail", () -> {
            try {
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://formsubmit.co/ajax/" + to))
                        .timeout(java.time.Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Origin", origin)
                        .header("Referer", origin + "/wiki/")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();
                java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient().send(req,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2 || resp.body().contains("\"success\":\"false\"")) {
                    plugin.getLogger().warning("Application mail (FormSubmit) answered " + resp.statusCode() + ": " + resp.body());
                } else {
                    plugin.getLogger().info("Application #" + id + " sent to " + to + " (FormSubmit: " + resp.body() + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Application mail could not be sent: " + e.getMessage());
            }
        });
    }

    private ConfigurationSection app(String[] args) throws CommandFail {
        ConfigurationSection a = args.length > 1 ? apps.getConfigurationSection("applications." + args[1]) : null;
        if (a == null) {
            throw CommandFail.usage("/applications " + args[0] + " <id>");
        }
        return a;
    }

    private void notifyApplicant(Player p, ConfigurationSection a) {
        boolean ok = "accepted".equals(a.getString("status"));
        plugin.messages().sendRaw(p, ok ? "applications.you-accepted" : "applications.you-denied", "note",
                a.getString("note", "").isEmpty() ? "-" : a.getString("note"));
        SoundSpec.of(ok ? "ui.toast.challenge_complete" : "block.note_block.bass", 0.8f, 1.1f).play(p);
        a.set("notified", true);
        save(apps, appFile);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.tasks().later(100, "application notice", () -> {
            if (!p.isOnline()) {
                return;
            }
            String id = latestOf(p.getUniqueId());
            if (id != null) {
                ConfigurationSection a = apps.getConfigurationSection("applications." + id);
                if (!"open".equals(a.getString("status", "open")) && !a.getBoolean("notified")) {
                    notifyApplicant(p, a);
                }
            }
            if (p.hasPermission("mythos.applications")) {
                ConfigurationSection root = apps.getConfigurationSection("applications");
                long open = root == null ? 0 : root.getKeys(false).stream().filter(k -> "open".equals(root.getString(k + ".status"))).count();
                if (open > 0) {
                    plugin.messages().send(p, "applications.open-count", "count", open);
                }
            }
        });
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return List.of("list", "all", "view", "accept", "deny");
        }
        if (args.length == 2 && apps.getConfigurationSection("applications") != null) {
            return new ArrayList<>(apps.getConfigurationSection("applications").getKeys(false));
        }
        return List.of();
    }

    // ------------------------------------------------------------------------------------------- helpers
    private static JsonObject readBody(HttpExchange ex) throws IOException {
        byte[] raw = ex.getRequestBody().readNBytes(64_000);
        try {
            JsonElement e = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
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
}
