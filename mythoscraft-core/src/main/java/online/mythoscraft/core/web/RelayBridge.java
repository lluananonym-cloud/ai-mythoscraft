package online.mythoscraft.core.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.rank.MythosOwner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Outgoing connection to the Netlify relay (for hosts without extra ports, e.g. MineKeep): once per second the
 * server uploads its live data and fetches the waiting jobs of the website (buttons, console, files, access).
 */
final class RelayBridge {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final MythosCraft plugin;
    private final WebPanelService panel;
    private final String url;
    private final String secret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ConcurrentLinkedQueue<JsonObject> results = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, String>> confirmed = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService loop;
    private int cycle;
    private boolean warned;
    private volatile boolean connected;

    RelayBridge(MythosCraft plugin, WebPanelService panel, String url, String secret) {
        this.plugin = plugin;
        this.panel = panel;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.secret = secret;
    }

    void start() {
        loop = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MythosCraft-PanelRelay");
            t.setDaemon(true);
            return t;
        });
        loop.scheduleWithFixedDelay(this::cycle, 2, 1, TimeUnit.SECONDS);
    }

    void stop() {
        if (loop != null) {
            loop.shutdownNow();
            loop = null;
        }
    }

    boolean connected() {
        return connected;
    }

    /** /website login <code> in relay mode: the code is checked by the website on the next cycle. */
    void confirm(Player p, String code) {
        confirmed.add(Map.of("code", code, "uuid", p.getUniqueId().toString(), "name", p.getName()));
    }

    private Map<String, String> accessMap() {
        Map<String, String> m = new LinkedHashMap<>();
        var sec = panel.accessConfig().getConfigurationSection("users");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                String role = sec.getString(id + ".role", "view");
                m.put(id, role.equalsIgnoreCase("owner") ? "admin" : role.toLowerCase(java.util.Locale.ROOT));
            }
        }
        m.put(MythosOwner.ONLINE_UUID.toString(), "owner");
        if (!Bukkit.getOnlineMode()) {
            m.put(MythosOwner.OFFLINE_UUID.toString(), "owner");
        }
        return m;
    }

    private void cycle() {
        try {
            cycle++;
            JsonObject body = new JsonObject();
            if (cycle % 2 == 1) {
                body.add("snapshot", GSON.toJsonTree(panel.sync(panel::snapshot)));
            }
            if (cycle % 15 == 1) {
                body.add("public", GSON.toJsonTree(panel.sync(() -> plugin.wiki().snapshot())));
            }
            if (cycle % 30 == 1) {
                body.add("actions", GSON.toJsonTree(panel.sync(() -> WebActions.definitions(plugin))));
                body.add("log", GSON.toJsonTree(panel.logLines()));
            }
            body.add("access", GSON.toJsonTree(accessMap()));
            JsonArray res = new JsonArray();
            JsonObject r;
            while ((r = results.poll()) != null) {
                res.add(r);
            }
            body.add("results", res);
            JsonArray conf = new JsonArray();
            Map<String, String> c;
            while ((c = confirmed.poll()) != null) {
                conf.add(GSON.toJsonTree(c));
            }
            body.add("confirmed", conf);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/api/bridge"))
                    .timeout(Duration.ofSeconds(9))
                    .header("Content-Type", "application/json")
                    .header("X-Relay-Secret", secret)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                if (!warned) {
                    warned = true;
                    plugin.getLogger().warning("Web panel relay " + url + " answered " + resp.statusCode()
                            + (resp.statusCode() == 403 ? " – PANEL_SECRET at Netlify does not match web-panel.relay.secret" : ""));
                }
                connected = false;
                return;
            }
            if (!connected) {
                plugin.getLogger().info("Web panel relay connected: " + url);
            }
            connected = true;
            warned = false;
            JsonObject answer = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (answer.has("rewards") && answer.get("rewards").isJsonArray()) {
                // daily rewards claimed on the website
                for (JsonElement e : answer.getAsJsonArray("rewards")) {
                    JsonObject rw = e.getAsJsonObject();
                    try {
                        plugin.webDaily().claimed(java.util.UUID.fromString(str(rw, "uuid")), str(rw, "name"),
                                rw.has("streak") ? rw.get("streak").getAsInt() : 1);
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Web daily reward skipped: " + ex.getMessage());
                    }
                }
            }
            for (JsonElement e : answer.getAsJsonArray("jobs")) {
                JsonObject job = e.getAsJsonObject();
                results.add(handle(job));
            }
            if (!results.isEmpty()) {
                cycle(); // answer right away, the website is waiting
            }
        } catch (Exception e) {
            connected = false;
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Web panel relay not reachable (" + url + "): " + e.getMessage());
            }
        }
    }

    private JsonObject handle(JsonObject job) {
        String id = job.get("id").getAsString();
        String type = job.get("type").getAsString();
        String by = job.has("by") ? job.get("by").getAsString() : "?";
        JsonObject p = job.has("payload") && job.get("payload").isJsonObject() ? job.getAsJsonObject("payload") : new JsonObject();
        Map<String, Object> out = new HashMap<>();
        try {
            switch (type) {
                case "player" -> out.putAll(panel.sync(() -> panel.player(str(p, "name"))));
                case "action" -> {
                    Map<String, String> params = new HashMap<>();
                    if (p.has("params") && p.get("params").isJsonObject()) {
                        p.getAsJsonObject("params").entrySet().forEach(en -> params.put(en.getKey(), en.getValue().getAsString()));
                    }
                    String role = str(p, "role");
                    panel.sync(() -> WebActions.definitions(plugin));
                    String command = WebActions.build(str(p, "id"), params, role.equals("admin") || role.equals("owner"));
                    panel.audit(by, "Aktion " + str(p, "id") + ": /" + command);
                    out.put("command", command);
                    out.put("output", panel.sync(() -> panel.run(command)));
                }
                case "command" -> {
                    String cmd = str(p, "command").trim();
                    if (cmd.startsWith("/")) {
                        cmd = cmd.substring(1);
                    }
                    if (cmd.isEmpty()) {
                        throw new IllegalArgumentException("Leerer Befehl");
                    }
                    String fc = cmd;
                    panel.audit(by, "Konsole: /" + fc);
                    out.put("output", panel.sync(() -> panel.run(fc)));
                }
                case "config_get" -> out.putAll(panel.readFile(str(p, "file")));
                case "config_save" -> {
                    out.putAll(panel.writeFile(str(p, "file"), str(p, "text"), by));
                    if (Boolean.TRUE.equals(out.get("ok"))) {
                        out.put("output", panel.sync(() -> panel.run("mythos reload")));
                    }
                }
                case "access_list" -> out.putAll(panel.accessList());
                case "access_set" -> out.put("result", panel.sync(() -> panel.setAccess(str(p, "name"), str(p, "role"), by)));
                default -> out.put("error", "unknown job");
            }
        } catch (IllegalArgumentException e) {
            out.put("error", e.getMessage());
        } catch (Exception e) {
            out.put("error", "Fehler: " + e.getMessage());
        }
        JsonObject res = GSON.toJsonTree(out).getAsJsonObject();
        res.addProperty("id", id);
        return res;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    static List<String> none() {
        return new ArrayList<>();
    }
}
