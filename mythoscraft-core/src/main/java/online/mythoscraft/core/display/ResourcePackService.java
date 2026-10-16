package online.mythoscraft.core.display;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ships the bundled MythosCraft resource pack: either from the built-in mini web server (no external hosting
 * needed) or from a configured URL. Tracks which players actually loaded it, so badges/icons are only sent to
 * clients that can display them.
 */
public final class ResourcePackService implements Listener {

    private static final String BUNDLED = "resourcepack/MythosCraft-ResourcePack.zip";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("mythoscraft:resourcepack".getBytes(StandardCharsets.UTF_8));

    private final MythosCraft plugin;
    private final PackHttpServer http;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private byte[] pack;
    private String hash = "";
    /** Hash announced to clients – the bundled pack's, or the one of the external file. */
    private String sendHash = "";
    private String url;
    private boolean enabled;
    private boolean required;
    private boolean assumeLoaded;
    private int sendDelayTicks = 20;
    /** True when resource-pack.url still points to an older pack than the one bundled in this jar. */
    private boolean outdated;
    // self-host settings, kept to re-serve the pack when the season changes
    private String selfBind;
    private int selfPort;
    private String selfPublic;
    private String selfHost;
    private boolean selfHosted;
    /** Season sky (clouds, sun, moon) mixed into the pack; "none" outside a season. */
    private String seasonTheme = "none";

    public ResourcePackService(MythosCraft plugin) {
        this.plugin = plugin;
        this.http = new PackHttpServer(plugin.getLogger());
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("resource-pack");
        enabled = cfg.bool("enabled", true);
        required = cfg.bool("required", false);
        assumeLoaded = cfg.bool("assume-loaded", false);
        sendDelayTicks = cfg.integer("send-delay-ticks", 20, 0, 400);
        http.stop();
        url = null;
        sendHash = "";
        outdated = false;
        selfHosted = false;
        if (!enabled) {
            return;
        }
        if (!loadBundledPack()) {
            enabled = false;
            return;
        }
        String external = cfg.string("url", "").trim();
        String configuredPublic = cfg.string("self-host.public-url", "").trim();
        if (!external.isEmpty() && !configuredPublic.isEmpty()
                && !externalHash(cfg.string("sha1", "").trim(), external).equalsIgnoreCase(hash)) {
            // the external link still serves an older pack, but a self-host address exists: serve the current pack
            // ourselves – after a plugin update nobody has to upload anything or edit the config
            plugin.getLogger().info("resource-pack.url points to an older pack – serving the bundled pack via " + configuredPublic);
            external = "";
        }
        if (!external.isEmpty()) {
            url = external;
            sendHash = externalHash(cfg.string("sha1", "").trim(), external);
            outdated = !sendHash.equalsIgnoreCase(hash);
            if (outdated) {
                plugin.getLogger().warning("The pack at resource-pack.url (SHA-1 " + sendHash + ") is not the pack bundled in this "
                        + "plugin version (SHA-1 " + hash + "). Players get the old pack – upload plugins/MythosCraft/"
                        + "MythosCraft-ResourcePack.zip (e.g. to mc-packs.net) and put the new link into resource-pack.url.");
            }
            plugin.getLogger().info("Resource pack: external URL " + external + " (SHA-1 " + sendHash + ")");
            return;
        }
        if (cfg.bool("self-host.enabled", true) || !configuredPublic.isEmpty()) {
            String bind = cfg.string("self-host.bind", "0.0.0.0");
            int port = cfg.integer("self-host.port", 8765, 1, 65535);
            int publicPort = portOf(configuredPublic);
            if (publicPort > 0) {
                // e.g. https://server.minekeep.dev:2053 – listen on the same port the host forwards
                port = publicPort;
            }
            String host = cfg.string("self-host.public-host", "").trim();
            if (host.isEmpty()) {
                host = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
                cfg.warn("self-host.public-host", "nicht gesetzt – verwende '" + host + "' (Spieler von außen brauchen die öffentliche Adresse)");
            }
            sendHash = hash;
            String path = "/mythoscraft-" + hash.substring(0, 12) + ".zip";
            // Hosts like MineKeep put HTTPS in front of the port (https://<server>.minekeep.dev:2053) – then the public
            // address differs from what the plugin listens on.
            String publicUrl = cfg.string("self-host.public-url", "").trim();
            while (publicUrl.endsWith("/")) {
                publicUrl = publicUrl.substring(0, publicUrl.length() - 1);
            }
            selfBind = bind;
            selfPort = port;
            selfPublic = publicUrl;
            selfHost = host;
            try {
                seasonTheme = currentTheme();
                serve(seasonTheme);
                selfHosted = true;
                plugin.tasks().cancel(this);
                plugin.tasks().repeat(this, 1200, 1200, "season pack", this::checkSeason);
                plugin.getLogger().info("Resource pack served at " + url + " (season sky: " + seasonTheme + ")");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not start the resource pack web server on port " + port + ": " + e.getMessage()
                        + " – set resource-pack.url or another port.");
            }
        }
    }

    private String currentTheme() {
        try {
            return plugin.attractions().deco().theme().name().toLowerCase(java.util.Locale.ROOT);
        } catch (RuntimeException e) {
            return "none";
        }
    }

    /** The bundled pack plus the season sky files of {@code theme}, served under a path with its hash. */
    private void serve(String theme) throws IOException {
        byte[] data = withSeason(pack, theme);
        String h;
        try {
            h = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        String path = "/mythoscraft-" + h.substring(0, 12) + ".zip";
        http.start(selfBind, selfPort, path, data);
        sendHash = h;
        url = selfPublic.isEmpty() ? "http://" + selfHost + ":" + selfPort + path : selfPublic + path;
    }

    private byte[] withSeason(byte[] base, String theme) throws IOException {
        if (theme.equals("none")) {
            return base;
        }
        byte[] season;
        try (InputStream in = plugin.getResource("resourcepack/seasons/" + theme + ".zip")) {
            if (in == null) {
                return base;
            }
            season = in.readAllBytes();
        }
        java.util.Map<String, byte[]> files = new java.util.TreeMap<>();
        for (byte[] zip : new byte[][]{base, season}) {
            boolean isSeason = zip == season;
            try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
                java.util.zip.ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory() || isSeason && e.getName().equals("pack.mcmeta")) {
                        continue;
                    }
                    files.put(e.getName(), zin.readAllBytes());
                }
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(base.length + season.length);
        try (java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(out)) {
            for (java.util.Map.Entry<String, byte[]> f : files.entrySet()) {
                java.util.zip.ZipEntry e = new java.util.zip.ZipEntry(f.getKey());
                e.setTime(0); // stable hash for the same content
                zout.putNextEntry(e);
                zout.write(f.getValue());
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** Season changed → serve the matching sky and send it to everybody online (replaces the old pack). */
    private void checkSeason() {
        if (!enabled || !selfHosted) {
            return;
        }
        String theme = currentTheme();
        if (theme.equals(seasonTheme)) {
            return;
        }
        String before = seasonTheme;
        seasonTheme = theme;
        plugin.tasks().io("season pack", () -> {
            try {
                serve(theme);
                plugin.getLogger().info("Season sky changed " + before + " → " + theme + " – sending the new pack");
                plugin.tasks().sync(() -> plugin.getServer().getOnlinePlayers().forEach(this::send));
            } catch (IOException e) {
                plugin.getLogger().warning("Season pack: " + e.getMessage());
            }
        });
    }

    public String seasonTheme() {
        return seasonTheme;
    }

    private boolean loadBundledPack() {
        try (InputStream in = plugin.getResource(BUNDLED)) {
            if (in == null) {
                plugin.getLogger().warning("Bundled resource pack is missing from the jar.");
                return false;
            }
            pack = in.readAllBytes();
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            File copy = new File(plugin.getDataFolder(), "MythosCraft-ResourcePack.zip");
            if (!copy.exists() || copy.length() != pack.length) {
                byte[] data = pack;
                plugin.tasks().io("export resource pack", () -> {
                    try {
                        Files.write(copy.toPath(), data);
                    } catch (IOException e) {
                        plugin.getLogger().warning("Could not export resource pack: " + e.getMessage());
                    }
                });
            }
            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Could not read the bundled resource pack: " + e.getMessage());
            return false;
        }
    }

    private static int portOf(String url) {
        try {
            return url.isEmpty() ? -1 : URI.create(url).getPort();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /**
     * Hash of an externally hosted pack: explicitly configured, or read from the link itself (mc-packs.net links
     * contain the SHA-1), otherwise assumed to be the bundled pack.
     */
    private String externalHash(String configured, String link) {
        if (configured.matches("[0-9a-fA-F]{40}")) {
            return configured.toLowerCase();
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9a-fA-F]{40})").matcher(link);
        return m.find() ? m.group(1).toLowerCase() : hash;
    }

    /** Sends the pack shortly after join (the client is ready then). */
    public void send(Player player) {
        if (!enabled || url == null) {
            return;
        }
        plugin.tasks().later(Math.max(1, sendDelayTicks), "send resource pack", () -> {
            if (!player.isOnline()) {
                return;
            }
            if (outdated && player.hasPermission("mythos.admin")) {
                plugin.messages().send(player, "resourcepack.outdated", "hash", hash);
            }
            try {
                player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                        .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), sendHash))
                        .prompt(plugin.messages().get("resourcepack.prompt"))
                        .required(required)
                        .replace(false)
                        .build());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid resource pack URL '" + url + "': " + e.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                if (loaded.add(player.getUniqueId())) {
                    plugin.display().packChanged(player);
                }
            }
            case DECLINED -> {
                loaded.remove(player.getUniqueId());
                plugin.messages().send(player, "resourcepack.declined");
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                loaded.remove(player.getUniqueId());
                plugin.messages().send(player, "resourcepack.failed");
                plugin.getLogger().warning("Resource pack failed for " + player.getName() + ": " + event.getStatus());
            }
            case DISCARDED -> {
                if (loaded.remove(player.getUniqueId())) {
                    plugin.display().packChanged(player);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    public boolean hasPack(Player player) {
        return assumeLoaded || loaded.contains(player.getUniqueId());
    }

    public boolean hasPack(UUID player) {
        return assumeLoaded || loaded.contains(player);
    }

    public boolean enabled() {
        return enabled;
    }

    public String url() {
        return url;
    }

    public String hash() {
        return hash;
    }

    public int loadedCount() {
        return loaded.size();
    }

    public void shutdown() {
        http.stop();
    }
}
