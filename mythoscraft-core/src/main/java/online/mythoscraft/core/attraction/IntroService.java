package online.mythoscraft.core.attraction;

import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Introduction for new players – starts once the resource pack is loaded (or a bit after the first join without a
 * pack): short camera flights over the spawn highlights while mentor Lia explains them in chat (in the player's
 * language, otherwise English), then what to do first with the starter kit. /intro replays it, /intro skip ends it.
 */
public final class IntroService extends BaseCommand implements Listener {

    private static final String DONE = "intro.done";
    private static final int SCENE_TICKS = 70;   // 3.5 s per shot

    /** one camera shot: orbit around {@code target} */
    private record Scene(String key, Location target, double radius, double height) {
    }

    private final class Run {
        final Player player;
        final Location back;
        final GameMode mode;
        final List<Scene> scenes;
        org.bukkit.entity.ItemDisplay cam;
        BukkitTask task;
        int scene;
        int tick;

        Run(Player p, List<Scene> scenes) {
            this.player = p;
            this.back = p.getLocation().clone();
            this.mode = p.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : p.getGameMode();
            this.scenes = scenes;
        }
    }

    private final Map<UUID, Run> running = new HashMap<>();
    /** players on their very first join – only they get the intro automatically */
    private final java.util.Set<UUID> newcomers = new java.util.HashSet<>();

    public IntroService(MythosCraft plugin) {
        super(plugin);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("intro.enabled", true);
    }

    // ------------------------------------------------------------------ triggers
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPack(PlayerResourcePackStatusEvent event) {
        Player p = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    plugin.tasks().later(40, "intro", () -> maybeStart(p));
            default -> {
            }
        }
    }

    /** Called once the player data is ready: fallback when no resource pack is sent at all. */
    public void firstJoin(Player p) {
        newcomers.add(p.getUniqueId());
        plugin.tasks().later(20 * 25, "intro fallback", () -> maybeStart(p));
    }

    private void maybeStart(Player p) {
        if (!enabled() || !p.isOnline() || !newcomers.contains(p.getUniqueId()) || running.containsKey(p.getUniqueId())
                || !plugin.progress().loaded(p)
                || plugin.progress().get(p, DONE) > 0 || plugin.arenas().isInArena(p)) {
            return;
        }
        start(p);
    }

    // ------------------------------------------------------------------ the show
    private List<Scene> scenes() {
        List<Scene> list = new ArrayList<>();
        Location center = plugin.attractionStore().center();
        if (center == null) {
            return list;
        }
        list.add(new Scene("welcome", center.clone().add(0, 4, 0), 30, 20));
        org.bukkit.Location crate = null;
        for (var c : plugin.crates().placed()) {
            if (c.location() != null && plugin.attractionStore().inside(c.location(), 0)) {
                crate = c.location().clone().add(0.5, 1, 0.5);
                break;
            }
        }
        if (crate != null) {
            list.add(new Scene("crates", crate, 6, 3.5));
        }
        Location wheel = plugin.attractionStore().first("wheel");
        if (wheel != null) {
            list.add(new Scene("wheel", wheel, 9, 1.5));
        }
        List<Location> statues = plugin.attractionStore().all("statue");
        if (!statues.isEmpty()) {
            list.add(new Scene("leaderboards", statues.get(0).clone().add(0, 2, 0), 8, 3));
        }
        Location parkour = plugin.attractionStore().first("parkour_start");
        if (parkour != null) {
            list.add(new Scene("games", parkour.clone().add(0, 2, 0), 10, 6));
        }
        list.add(new Scene("start", center.clone().add(0, 2, 0), 14, 7));
        return list;
    }

    public void start(Player p) {
        List<Scene> scenes = scenes();
        if (scenes.isEmpty()) {
            plugin.progress().set(p, DONE, 1);
            return;
        }
        Run r = new Run(p, scenes);
        running.put(p.getUniqueId(), r);
        p.setGameMode(GameMode.SPECTATOR);
        // the camera rides an (empty) item display: its moves are interpolated by the client → smooth flight
        r.cam = p.getWorld().spawn(camPos(scenes.get(0), 0), org.bukkit.entity.ItemDisplay.class, a -> {
            a.setPersistent(false);
            a.setTeleportDuration(3);
            a.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "intro_cam");
        });
        p.teleport(r.cam.getLocation());
        p.setSpectatorTarget(r.cam);
        r.task = plugin.tasks().repeat(this, 1, 1, "intro " + p.getName(), () -> step(r));
        sceneStart(r);
    }

    /** camera position on the orbit at progress t (0..1), looking at the target */
    private static Location camPos(Scene s, double t) {
        double e = t * t * (3 - 2 * t);                       // ease in/out
        double a = Math.PI * 0.25 + e * Math.PI * 0.35;
        Location target = s.target();
        Location l = target.clone().add(Math.cos(a) * s.radius(), s.height(), Math.sin(a) * s.radius());
        double dx = target.getX() - l.getX(), dy = target.getY() - l.getY(), dz = target.getZ() - l.getZ();
        l.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        l.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        return l;
    }

    private void sceneStart(Run r) {
        Scene s = r.scenes.get(r.scene);
        Player p = r.player;
        p.showTitle(Title.title(plugin.messages().get("intro." + s.key() + ".title"), plugin.messages().get("intro." + s.key() + ".subtitle"),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2600), Duration.ofMillis(600))));
        plugin.messages().sendRaw(p, "intro." + s.key() + ".lia");
        SoundSpec.of("block.amethyst_block.chime", 0.8f, 1.3f).play(p);
    }

    private void step(Run r) {
        Player p = r.player;
        if (!p.isOnline() || r.cam == null || !r.cam.isValid()) {
            finish(r, false);
            return;
        }
        Scene s = r.scenes.get(r.scene);
        double t = r.tick / (double) SCENE_TICKS;
        Location at = camPos(s, t);
        r.cam.teleport(at);
        if (p.getSpectatorTarget() != r.cam) {
            p.teleport(at);
            p.setSpectatorTarget(r.cam);
        }
        if (++r.tick >= SCENE_TICKS) {
            r.tick = 0;
            if (++r.scene >= r.scenes.size()) {
                finish(r, true);
                return;
            }
            sceneStart(r);
        }
    }

    private void finish(Run r, boolean completed) {
        running.remove(r.player.getUniqueId());
        if (r.task != null) {
            r.task.cancel();
        }
        Player p = r.player;
        if (p.isOnline()) {
            p.setSpectatorTarget(null);
            p.setGameMode(r.mode);
            p.teleport(r.back, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        if (r.cam != null) {
            r.cam.remove();
        }
        newcomers.remove(p.getUniqueId());
        plugin.progress().set(p, DONE, 1);
        if (p.isOnline()) {
            plugin.messages().sendRaw(p, "intro.finish");
            SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.2f).play(p);
            // the glowing trail takes over: starter kit → wheel → games → crates → RTP
            plugin.tasks().later(60, "guide after intro", () -> plugin.guide().begin(p));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Run r = running.get(event.getPlayer().getUniqueId());
        if (r != null) {
            finish(r, false);
        }
    }

    // ------------------------------------------------------------------ /intro [skip]
    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        Run r = running.get(p.getUniqueId());
        if (args.length > 0 && args[0].equalsIgnoreCase("skip")) {
            if (r != null) {
                finish(r, false);
            }
            return;
        }
        if (r == null) {
            start(p);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 ? List.of("skip") : List.of();
    }

    public void shutdown() {
        for (Run r : new ArrayList<>(running.values())) {
            finish(r, false);
        }
    }
}
