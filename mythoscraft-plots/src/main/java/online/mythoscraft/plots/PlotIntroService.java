package online.mythoscraft.plots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small Citybuild camera intro. In local mode it only runs after the selector explicitly chooses plots. */
final class PlotIntroService implements Listener {
    private static final NamespacedKey NEXT_WORLD = new NamespacedKey("mythoscraft", "intro-next-world");
    private static final NamespacedKey INTRO_ENABLED = new NamespacedKey("mythoscraft", "intro-enabled");
    private static final NamespacedKey SEEN = new NamespacedKey("mythosplots", "citybuild-intro-seen");
    private static final int SCENE_TICKS = 60;

    private record Scene(String title, String subtitle, String chat, Location target, double radius, double height) {}

    private static final class Run {
        final Player player;
        final Location back;
        final GameMode mode;
        final List<Scene> scenes;
        ItemDisplay camera;
        BukkitTask task;
        int scene;
        int tick;

        Run(Player player, List<Scene> scenes) {
            this.player = player;
            this.back = player.getLocation().clone();
            this.mode = player.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : player.getGameMode();
            this.scenes = scenes;
        }
    }

    private final MythosPlots plugin;
    private final Map<UUID, Run> running = new HashMap<>();

    PlotIntroService(MythosPlots plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.local()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isPlotWorld(player.getWorld())) {
            return;
        }
        boolean selected = "plots".equals(player.getPersistentDataContainer().get(NEXT_WORLD, PersistentDataType.STRING));
        boolean firstVisit = !seen(player) && !player.hasPlayedBefore();
        if (selected || firstVisit) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> beginIfEnabled(player), 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!plugin.local() || !plugin.isPlotWorld(event.getPlayer().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        if (!"plots".equals(player.getPersistentDataContainer().get(NEXT_WORLD, PersistentDataType.STRING))) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> beginIfEnabled(player), 12L);
    }

    private boolean seen(Player player) {
        Byte value = player.getPersistentDataContainer().get(SEEN, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    private boolean introEnabled(Player player) {
        Byte value = player.getPersistentDataContainer().get(INTRO_ENABLED, PersistentDataType.BYTE);
        return value == null || value != 0; // on by default
    }

    private void beginIfEnabled(Player player) {
        if (!player.isOnline() || !plugin.isPlotWorld(player.getWorld()) || running.containsKey(player.getUniqueId())) {
            return;
        }
        player.getPersistentDataContainer().remove(NEXT_WORLD);
        if (!introEnabled(player)) {
            player.getPersistentDataContainer().set(SEEN, PersistentDataType.BYTE, (byte) 1);
            return;
        }
        start(player);
    }

    private List<Scene> scenes(Player player) {
        World world = plugin.world();
        Location spawn = plugin.spawn();
        List<Scene> result = new ArrayList<>();
        result.add(new Scene("Willkommen im Citybuild", "Dein Platz. Deine Ideen. Deine Welt.",
                "Willkommen im Citybuild von MythosCraft. Hier baust du auf deinem eigenen Grundstück.",
                spawn.clone().add(0, 2, 0), 9, 5));
        PlotId first = new PlotId(0, 0);
        Location freePlot = new Location(world, plugin.grid().centerX(first) + .5, PlotGrid.SURFACE + 2,
                plugin.grid().centerZ(first) + .5);
        result.add(new Scene("Grundstücke", "Claimen und losbauen",
                "Mit /plot auto findest du automatisch ein freies Grundstück. Mit /plot claim claimst du das Grundstück unter dir.",
                freePlot, 28, 16));
        List<Plot> own = plugin.store().of(player.getUniqueId());
        if (!own.isEmpty()) {
            Location home = plugin.entrance(own.get(0).id());
            result.add(new Scene("Dein eigenes Grundstück", "Gestalte es nach deinen Regeln",
                    "Mit /plot home kommst du jederzeit zu deinem Grundstück zurück.", home, 10, 6));
        } else {
            result.add(new Scene("Dein nächster Schritt", "Finde dein Grundstück",
                    "Starte mit /plot auto oder gehe zu einem freien Grundstück und nutze /plot claim.",
                    freePlot, 16, 9));
        }
        return result;
    }

    private void start(Player player) {
        List<Scene> scenes = scenes(player);
        if (scenes.isEmpty() || scenes.get(0).target().getWorld() == null) {
            return;
        }
        Run run = new Run(player, scenes);
        running.put(player.getUniqueId(), run);
        player.setGameMode(GameMode.SPECTATOR);
        run.camera = player.getWorld().spawn(cameraAt(scenes.get(0), 0), ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setTeleportDuration(3);
        });
        player.teleport(run.camera.getLocation());
        player.setSpectatorTarget(run.camera);
        showScene(run);
        run.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.isPlotWorld(player.getWorld()) || run.camera == null || !run.camera.isValid()) {
                    finish(run);
                    return;
                }
                Scene scene = run.scenes.get(run.scene);
                run.camera.teleport(cameraAt(scene, run.tick / (double) SCENE_TICKS));
                if (player.getSpectatorTarget() != run.camera) {
                    player.teleport(run.camera.getLocation());
                    player.setSpectatorTarget(run.camera);
                }
                if (run.tick % 20 == 0) {
                    player.sendActionBar(Component.text("Schleichen = Intro überspringen", NamedTextColor.GRAY));
                }
                if (++run.tick >= SCENE_TICKS) {
                    run.tick = 0;
                    if (++run.scene >= run.scenes.size()) {
                        finish(run);
                        return;
                    }
                    showScene(run);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void showScene(Run run) {
        Scene scene = run.scenes.get(run.scene);
        run.player.showTitle(Title.title(Component.text(scene.title(), NamedTextColor.LIGHT_PURPLE),
                Component.text(scene.subtitle(), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1900), Duration.ofMillis(350))));
        run.player.sendMessage(Component.text(scene.chat(), NamedTextColor.GRAY));
    }

    private static Location cameraAt(Scene scene, double progress) {
        double t = Math.max(0, Math.min(1, progress));
        double eased = t * t * (3 - 2 * t);
        double angle = Math.PI * .2 + eased * Math.PI * .65;
        Location target = scene.target();
        Location camera = target.clone().add(Math.cos(angle) * scene.radius(), scene.height(), Math.sin(angle) * scene.radius());
        Vector delta = target.toVector().subtract(camera.toVector());
        camera.setDirection(delta);
        return camera;
    }

    private void finish(Run run) {
        running.remove(run.player.getUniqueId());
        if (run.task != null) {
            run.task.cancel();
        }
        Player player = run.player;
        if (player.isOnline()) {
            player.setSpectatorTarget(null);
            player.setGameMode(run.mode);
            if (plugin.isPlotWorld(run.back.getWorld())) {
                player.teleport(run.back);
            } else {
                player.teleport(plugin.spawn());
            }
            player.sendMessage(Component.text("Viel Spaß beim Bauen auf MythosCraft!", NamedTextColor.GREEN));
        }
        if (run.camera != null) {
            run.camera.remove();
        }
        player.getPersistentDataContainer().set(SEEN, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Run run = running.get(event.getPlayer().getUniqueId());
        if (run != null && event.isSneaking() && (run.scene > 0 || run.tick > 8)) {
            event.setCancelled(true);
            finish(run);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Run run = running.get(event.getPlayer().getUniqueId());
        if (run != null) {
            finish(run);
        }
    }

    void shutdown() {
        for (Run run : new ArrayList<>(running.values())) {
            finish(run);
        }
    }
}
