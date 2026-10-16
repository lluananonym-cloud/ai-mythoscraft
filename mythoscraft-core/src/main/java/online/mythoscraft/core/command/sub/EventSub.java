package online.mythoscraft.core.command.sub;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.events.EventManager;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** /mythos event list|start|stop|info|reload|spawnboss */
public final class EventSub implements SubCommand {

    private final MythosCraft plugin;

    public EventSub(MythosCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "event";
    }

    @Override
    public String permission() {
        return Perms.EVENTS_ADMIN;
    }

    @Override
    public boolean allowed(CommandSender sender) {
        return sender.hasPermission(Perms.EVENTS_ADMIN) || sender.hasPermission(Perms.EVENTS_START)
                || sender.hasPermission(Perms.EVENTS_STOP) || sender.hasPermission(Perms.EVENTS_RELOAD)
                || sender.hasPermission(Perms.EVENTS_SPAWNBOSS);
    }

    @Override
    public void execute(CommandSender sender, String[] args) throws CommandFail {
        String action = args.length == 0 ? "info" : args[0].toLowerCase();
        EventManager events = plugin.events();
        switch (action) {
            case "list" -> {
                plugin.messages().send(sender, "events.list-header");
                for (MythosEvent e : events.all()) {
                    long cd = e.cooldownRemainingMillis();
                    plugin.messages().sendRaw(sender, "events.list-line", "id", e.id(), "event", e.displayName(),
                            "state", plugin.messages().get(e.enabled() ? "events.enabled" : "events.disabled"),
                            "cooldown", cd > 0 ? Durations.format(Duration.ofMillis(cd)) : "-");
                }
            }
            case "start" -> {
                require(sender, Perms.EVENTS_START);
                if (args.length < 2) {
                    throw CommandFail.usage("/mythos event start <Event> [Countdown-Sekunden]");
                }
                Integer countdown = args.length > 2 ? Numbers.parseInt(args[2]) : null;
                EventManager.StartResult result = events.start(args[1], true, countdown);
                switch (result) {
                    case STARTED -> plugin.messages().send(sender, "events.admin-started", "event", events.get(args[1]).displayName());
                    case UNKNOWN -> throw new CommandFail("events.unknown", "events", ids());
                    case ALREADY_RUNNING -> throw new CommandFail("events.already-running");
                    default -> throw new CommandFail("events.cannot-start", "reason", result.name());
                }
            }
            case "stop" -> {
                require(sender, Perms.EVENTS_STOP);
                if (!events.stop()) {
                    throw new CommandFail("events.not-running");
                }
                plugin.messages().send(sender, "events.admin-stopped");
            }
            case "reload" -> {
                require(sender, Perms.EVENTS_RELOAD);
                plugin.reloadConfig();
                plugin.events().reload(plugin.rootConfig());
                plugin.messages().send(sender, "events.reloaded");
            }
            case "spawnboss" -> {
                require(sender, Perms.EVENTS_SPAWNBOSS);
                if (!(sender instanceof Player p)) {
                    throw new CommandFail("general.players-only");
                }
                EventManager.StartResult r = events.spawnBoss(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(6)));
                if (r == EventManager.StartResult.ALREADY_RUNNING) {
                    throw new CommandFail("events.already-running");
                }
                plugin.messages().send(sender, "events.boss-spawned");
            }
            default -> {
                MythosEvent active = events.active();
                if (active == null) {
                    long next = events.nextAutoMillis();
                    plugin.messages().send(sender, next >= 0 ? "events.none-next" : "events.none",
                            "time", Durations.format(Duration.ofMillis(Math.max(0, next))));
                    return;
                }
                plugin.messages().send(sender, "events.info-header", "event", active.displayName(), "status", active.statusLine());
                for (Component line : active.infoLines(sender instanceof Player p ? p : null)) {
                    sender.sendMessage(line);
                }
            }
        }
    }

    private void require(CommandSender sender, String perm) throws CommandFail {
        if (!sender.hasPermission(perm) && !sender.hasPermission(Perms.EVENTS_ADMIN)) {
            throw new CommandFail("general.no-permission");
        }
    }

    private String ids() {
        List<String> ids = new ArrayList<>();
        plugin.events().all().forEach(e -> ids.add(e.id()));
        return String.join(", ", ids);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list", "info"));
            if (sender.hasPermission(Perms.EVENTS_START) || sender.hasPermission(Perms.EVENTS_ADMIN)) {
                options.add("start");
            }
            if (sender.hasPermission(Perms.EVENTS_STOP) || sender.hasPermission(Perms.EVENTS_ADMIN)) {
                options.add("stop");
            }
            if (sender.hasPermission(Perms.EVENTS_RELOAD) || sender.hasPermission(Perms.EVENTS_ADMIN)) {
                options.add("reload");
            }
            if (sender.hasPermission(Perms.EVENTS_SPAWNBOSS) || sender.hasPermission(Perms.EVENTS_ADMIN)) {
                options.add("spawnboss");
            }
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> ids = new ArrayList<>();
            plugin.events().all().forEach(e -> ids.add(e.id()));
            return ids;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return List.of("0", "10", "30", "60");
        }
        return List.of();
    }
}
