package online.mythoscraft.ai;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class MythosAIPlugin extends JavaPlugin implements Listener {
    public static final String PREFIX = ChatColor.AQUA + "[MythosAI] " + ChatColor.RESET;
    private HttpClient http;
    private String website;
    private String apiKey;
    private String chatTrigger;
    private boolean enableEvents;
    private boolean enableIngameChat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCfg();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MythosAI aktiv -> " + website);
        // Health-check
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            JSONObject r = call(new JSONObject().put("action", "ping"));
            if (r != null) getLogger().info("Verbindung OK: " + r.optJSONObject("server"));
            else getLogger().warning("Konnte Website nicht erreichen. Prüfe website/api_key in config.yml.");
        });
    }

    private void loadCfg() {
        reloadConfig();
        this.website = getConfig().getString("website", "https://ai-mythoscraft.lovable.app").replaceAll("/+$", "");
        this.apiKey = getConfig().getString("api_key", "191306");
        this.chatTrigger = getConfig().getString("chat_trigger", "!ai");
        this.enableEvents = getConfig().getBoolean("enable_events", true);
        this.enableIngameChat = getConfig().getBoolean("enable_ingame_chat", true);
    }

    /** POST JSON to /functions/v1/mc-bridge. Blocking; call from async task. */
    public JSONObject call(JSONObject body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(website + "/functions/v1/mc-bridge"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return new JSONObject(resp.body());
            getLogger().warning("mc-bridge " + resp.statusCode() + ": " + resp.body());
        } catch (Exception e) {
            getLogger().warning("mc-bridge error: " + e.getMessage());
        }
        return null;
    }

    private void sendAiLine(Player p, String txt) {
        if (p == null || !p.isOnline() || txt == null || txt.isEmpty()) return;
        p.sendMessage(PREFIX + ChatColor.GRAY + "» " + ChatColor.WHITE + txt);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("mythos")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(PREFIX + "Befehle: /mythos reload, /mythos status, /ai <text>, /ai link, /ai unlink, /ai stats");
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                loadCfg();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Config neu geladen.");
                return true;
            }
            if (args[0].equalsIgnoreCase("status")) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    JSONObject r = call(new JSONObject().put("action", "ping"));
                    sender.sendMessage(PREFIX + (r != null ? ChatColor.GREEN + "Online: " + r.optJSONObject("server") : ChatColor.RED + "Offline"));
                });
                return true;
            }
            return true;
        }
        if (name.equals("ai")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
            if (args.length == 0) { p.sendMessage(PREFIX + "Nutze /ai <deine Frage>"); return true; }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("link")) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    JSONObject r = call(new JSONObject()
                            .put("action", "__link_status__")
                            .put("player", p.getName())
                            .put("uuid", p.getUniqueId().toString()));
                    if (r == null) { sendAiLine(p, "Website nicht erreichbar."); return; }
                    if (r.optBoolean("linked")) sendAiLine(p, "Bereits verknüpft als " + r.optString("mc_name"));
                    else {
                        JSONObject c = call(new JSONObject().put("action", "__get_code__")
                                .put("player", p.getName()).put("uuid", p.getUniqueId().toString()));
                        if (c != null && c.has("code")) sendAiLine(p, ChatColor.YELLOW + "Verifizierungs-Code: " + ChatColor.WHITE + c.getString("code") +
                                ChatColor.GRAY + "  (auf " + website + "/dashboard eingeben)");
                    }
                });
                return true;
            }
            if (sub.equals("unlink")) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    JSONObject r = call(new JSONObject().put("action", "__unlink__")
                            .put("player", p.getName()).put("uuid", p.getUniqueId().toString()));
                    sendAiLine(p, r != null ? r.optString("reply", "Entkoppelt.") : "Fehler beim Entkoppeln.");
                });
                return true;
            }
            if (sub.equals("stats")) {
                p.sendMessage(PREFIX + ChatColor.GRAY + "Deine Stats: " + ChatColor.AQUA
                        + website + "/plugin?mc=" + p.getUniqueId());
                return true;
            }
            String msg = String.join(" ", args);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                JSONObject r = call(new JSONObject().put("action", "ai")
                        .put("player", p.getName())
                        .put("uuid", p.getUniqueId().toString())
                        .put("message", msg));
                if (r == null) { sendAiLine(p, ChatColor.RED + "AI gerade nicht erreichbar."); return; }
                sendAiLine(p, r.optString("reply", "..."));
            });
            return true;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!enableEvents) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
            p.sendMessage(PREFIX + ChatColor.YELLOW + "Tipp: schreib /ai um mit Mythos AI zu chatten.");
            JSONObject r = call(new JSONObject().put("action", "event")
                    .put("type", "join").put("player", p.getName())
                    .put("uuid", p.getUniqueId().toString()));
            if (r != null && !r.optString("reply", "").isEmpty()) sendAiLine(p, r.getString("reply"));
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!enableEvents) return;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> call(new JSONObject()
                .put("action", "event").put("type", "leave").put("player", e.getPlayer().getName())));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!enableEvents) return;
        Player p = e.getEntity();
        String cause = e.getDeathMessage() != null ? e.getDeathMessage() : "died";
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            JSONObject r = call(new JSONObject().put("action", "event")
                    .put("type", "death").put("player", p.getName()).put("content", cause));
            if (r != null && !r.optString("reply", "").isEmpty())
                Bukkit.getScheduler().runTask(this, () -> Bukkit.broadcastMessage(PREFIX + ChatColor.GRAY + r.getString("reply")));
        });
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!enableIngameChat) return;
        String m = e.getMessage();
        if (m == null || !m.toLowerCase(Locale.ROOT).startsWith(chatTrigger.toLowerCase(Locale.ROOT))) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            JSONObject r = call(new JSONObject().put("action", "chat")
                    .put("player", p.getName()).put("uuid", p.getUniqueId().toString()).put("message", m));
            if (r != null && !r.optString("reply", "").isEmpty())
                Bukkit.getScheduler().runTask(this, () -> Bukkit.broadcastMessage(PREFIX + ChatColor.GRAY + "» " + ChatColor.WHITE + r.getString("reply")));
        });
    }
}
