package online.mythoscraft.core.attraction;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * First steps after the intro: a glowing particle trail leads a new player from goal to goal – starter kit, wheel of
 * fortune, a spawn game (dropper or parkour), the crates (short explanation on arrival) and finally the RTP portal into
 * the wilderness. A boss bar shows the current step, the action bar the distance. /guide resumes it, /guide skip
 * jumps to the next step, /guide stop ends it.
 */
public final class GuideService extends BaseCommand implements Listener {

    /** progress key: 0 = never started, 1..5 = current step, 99 = done */
    static final String STEP = "guide.step";
    static final int DONE = 99;

    enum Step {
        STARTER("starter"), WHEEL("wheel"), GAMES("games"), CRATES("crates"), RTP("rtp");

        final String key;

        Step(String key) {
            this.key = key;
        }
    }

    private static final Step[] STEPS = Step.values();

    private final class Run {
        final Player player;
        final BossBar bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        Step step;
        long startValue;
        /** tick the player reached the crates (explanation shown, next step follows a bit later) */
        int arrivedAt = -1;
        int tick;

        Run(Player player, Step step) {
            this.player = player;
            this.step = step;
        }
    }

    private final Map<UUID, Run> running = new HashMap<>();

    public GuideService(MythosCraft plugin) {
        super(plugin);
        plugin.tasks().repeat(this, 20, 4, "guide trail", this::tick);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("guide.enabled", true);
    }

    /** After the intro (watched or skipped): starts the guide once for every new player. */
    public void begin(Player p) {
        if (!enabled() || !p.isOnline() || !plugin.progress().loaded(p) || running.containsKey(p.getUniqueId())) {
            return;
        }
        long state = plugin.progress().get(p, STEP);
        if (state >= DONE) {
            return;
        }
        int index = (int) Math.max(1, Math.min(STEPS.length, state)) - 1;
        start(p, STEPS[index]);
    }

    /** Join with an unfinished guide: continue where the player left. */
    public void resume(Player p) {
        long state = plugin.progress().get(p, STEP);
        if (state > 0 && state < DONE) {
            plugin.tasks().later(60, "guide resume", () -> begin(p));
        }
    }

    private void start(Player p, Step step) {
        Run r = new Run(p, step);
        running.put(p.getUniqueId(), r);
        p.showBossBar(r.bar);
        if (plugin.newbies() != null && step == Step.STARTER) {
            plugin.messages().send(p, "newbie.guide-promise", "reward", plugin.newbies().describe());
        }
        enter(r, step);
    }

    private void enter(Run r, Step step) {
        Player p = r.player;
        r.step = step;
        r.arrivedAt = -1;
        r.startValue = switch (step) {
            case WHEEL -> plugin.progress().get(p, "wheel.spins") + plugin.progress().get(p, "wheel2.spins");
            default -> 0;
        };
        plugin.progress().set(p, STEP, step.ordinal() + 1);
        r.bar.name(plugin.messages().in(p, () -> plugin.messages().get("guide.bar",
                "step", step.ordinal() + 1, "total", STEPS.length, "goal", plugin.messages().get("guide." + step.key + ".goal"))));
        r.bar.progress((float) step.ordinal() / STEPS.length);
        plugin.messages().sendRaw(p, "guide." + step.key + ".chat");
        SoundSpec.of("block.note_block.chime", 0.9f, 1.5f).play(p);
        if (step == Step.STARTER && plugin.progress().get(p, "starter.kit-claimed") > 0) {
            next(r);
        }
    }

    private void next(Run r) {
        int i = r.step.ordinal() + 1;
        SoundSpec.of("entity.player.levelup", 0.7f, 1.6f).play(r.player);
        r.player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, r.player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
        if (i >= STEPS.length) {
            finish(r, true);
            return;
        }
        enter(r, STEPS[i]);
    }

    private void finish(Run r, boolean completed) {
        running.remove(r.player.getUniqueId());
        r.player.hideBossBar(r.bar);
        if (completed) {
            plugin.progress().set(r.player, STEP, DONE);
            plugin.messages().sendRaw(r.player, "guide.finish");
            if (plugin.newbies() != null) {
                plugin.newbies().guideFinished(r.player);
            }
        }
    }

    /** Where the trail of a step points to (null = no spawn build: the step is skipped). */
    private Location target(Step step) {
        AttractionStore s = plugin.attractionStore();
        return switch (step) {
            case STARTER -> s.first("mentor");
            case WHEEL -> s.first("wheel") == null || s.center() == null ? null : ground(s.first("wheel"), s.center());
            case GAMES -> s.first("dropper_lift") != null ? s.first("dropper_lift") : s.first("parkour_start");
            case CRATES -> {
                List<Location> crates = s.all("crate");
                yield crates.isEmpty() ? null : crates.get(0);
            }
            case RTP -> s.first("rtp_portal");
        };
    }

    /** The wheel hangs in the air – lead to the floor in front of it. */
    private static Location ground(Location wheel, Location center) {
        double yaw = Math.toRadians(wheel.getYaw());
        Location l = wheel.clone().add(-Math.sin(yaw) * 5, 0, Math.cos(yaw) * 5);
        l.setY(center.getY() + 1);
        return l;
    }

    private boolean reached(Run r, Location target) {
        Player p = r.player;
        return switch (r.step) {
            case STARTER -> plugin.progress().get(p, "starter.kit-claimed") > 0;
            case WHEEL -> plugin.progress().get(p, "wheel.spins") + plugin.progress().get(p, "wheel2.spins") > r.startValue;
            case GAMES -> near(p, target, 3.5) || near(p, plugin.attractionStore().first("dropper_start"), 6);
            case CRATES -> near(p, target, 5);
            // through the portal (or /rtp) – the player has left the spawn
            case RTP -> !plugin.attractionStore().inside(p.getLocation(), 8);
        };
    }

    private static boolean near(Player p, Location l, double range) {
        return l != null && l.getWorld().equals(p.getWorld()) && l.distanceSquared(p.getLocation()) <= range * range;
    }

    private void tick() {
        for (Run r : List.copyOf(running.values())) {
            Player p = r.player;
            if (!p.isOnline()) {
                running.remove(p.getUniqueId());
                continue;
            }
            r.tick++;
            Location target = target(r.step);
            if (target == null && r.step != Step.RTP) {
                next(r);
                continue;
            }
            if (r.step == Step.CRATES && r.arrivedAt >= 0) {
                if (r.tick - r.arrivedAt >= 45) {          // ~9 s to read the crate explanation
                    next(r);
                }
                continue;
            }
            if (reached(r, target)) {
                if (r.step == Step.CRATES) {
                    r.arrivedAt = r.tick;
                    plugin.messages().sendRaw(p, "guide.crates.explain");
                    SoundSpec.of("block.chest.open", 0.8f, 1.2f).play(p);
                } else {
                    next(r);
                }
                continue;
            }
            if (target == null || !target.getWorld().equals(p.getWorld())) {
                if (r.tick % 5 == 0) {
                    plugin.messages().actionBar(p, "guide." + r.step.key + ".bar", "distance", "?");
                }
                continue;
            }
            trail(r, target);
            if (r.tick % 5 == 0) {
                plugin.messages().actionBar(p, "guide." + r.step.key + ".bar",
                        "distance", (int) Math.round(target.distance(p.getLocation())));
            }
        }
    }

    /** Glowing dotted line from the player to the goal with a light pulse running along it, plus a beacon at the goal. */
    private void trail(Run r, Location target) {
        Player p = r.player;
        Location from = p.getLocation().add(0, 0.35, 0);
        Location to = new Location(target.getWorld(), target.getBlockX() + 0.5, target.getBlockY() + 0.6, target.getBlockZ() + 0.5);
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        if (dist > 0.5) {
            dir.multiply(1 / dist);
            double len = Math.min(dist, 28);
            Particle.DustTransition dust = new Particle.DustTransition(Color.fromRGB(0xfd, 0xe0, 0x47), Color.fromRGB(0xa8, 0x55, 0xf7), 1.4f);
            for (double d = 1.2; d < len; d += 0.6) {
                Location pt = from.clone().add(dir.clone().multiply(d));
                pt.add(0, Math.sin((d - r.tick * 0.6) * 0.8) * 0.12, 0);
                p.spawnParticle(Particle.DUST_COLOR_TRANSITION, pt, 1, 0, 0, 0, 0, dust);
            }
            // a bright pulse travels along the trail towards the goal
            double pulse = 1.2 + (r.tick * 1.1) % Math.max(1.5, len - 1.2);
            Location head = from.clone().add(dir.clone().multiply(pulse));
            p.spawnParticle(Particle.END_ROD, head, 2, 0.05, 0.05, 0.05, 0.01);
            p.spawnParticle(Particle.GLOW, head, 1, 0.1, 0.1, 0.1, 0);
        }
        // beacon at the goal: a column with a turning spiral
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(0xfa, 0xcc, 0x15), 1.8f);
        for (int y = 0; y < 8; y++) {
            p.spawnParticle(Particle.DUST, to.clone().add(0, y * 0.75, 0), 1, 0.05, 0.1, 0.05, 0, gold);
        }
        for (int i = 0; i < 3; i++) {
            double a = r.tick * 0.35 + i * (Math.PI * 2 / 3);
            p.spawnParticle(Particle.END_ROD, to.clone().add(Math.cos(a) * 1.1, (r.tick % 20) * 0.25, Math.sin(a) * 1.1), 1, 0, 0, 0, 0);
        }
        if (r.tick % 10 == 0) {
            p.spawnParticle(Particle.FIREWORK, to.clone().add(0, 6, 0), 6, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Run r = running.remove(event.getPlayer().getUniqueId());
        if (r != null) {
            event.getPlayer().hideBossBar(r.bar);
        }
    }

    // ------------------------------------------------------------------ /guide [skip|stop]
    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        Run r = running.get(p.getUniqueId());
        String a = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        switch (a) {
            case "skip", "next" -> {
                if (r != null) {
                    next(r);
                }
            }
            case "stop" -> {
                if (r != null) {
                    finish(r, false);
                    plugin.progress().set(p, STEP, DONE);
                    plugin.messages().send(p, "guide.stopped");
                    if (plugin.newbies() != null) {
                        plugin.newbies().guideSkipped(p);
                    }
                }
            }
            default -> {
                if (r == null) {
                    if (plugin.progress().get(p, STEP) >= DONE) {
                        plugin.progress().set(p, STEP, 0);
                    }
                    begin(p);
                }
            }
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 ? List.of("skip", "stop") : List.of();
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        for (Run r : List.copyOf(running.values())) {
            r.player.hideBossBar(r.bar);
        }
        running.clear();
    }
}
