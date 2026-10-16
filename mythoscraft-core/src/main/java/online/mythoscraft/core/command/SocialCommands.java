package online.mythoscraft.core.command;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.util.Cooldowns;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** Small player commands: /helpop, /daily, /stats, /top, /event. */
public final class SocialCommands extends BaseCommand {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Cooldowns cooldowns;

    public SocialCommands(MythosCraft plugin, Cooldowns cooldowns) {
        super(plugin);
        this.cooldowns = cooldowns;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        switch (label) {
            case "helpop" -> helpop(sender, args);
            case "daily" -> {
                permission(sender, Perms.DAILY);
                if (!plugin.daily().enabled()) {
                    throw new CommandFail("daily.disabled");
                }
                plugin.daily().claim(player(sender));
            }
            case "stats" -> stats(sender, args);
            case "top" -> top(plugin, sender, args);
            case "event" -> {
                if (args.length > 0 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("join"))) {
                    eventTeleport(player(sender));
                } else {
                    eventInfo(sender);
                }
            }
            default -> {
            }
        }
    }

    // ---- /helpop ------------------------------------------------------------------------------------------

    private void helpop(CommandSender sender, String[] args) throws CommandFail {
        permission(sender, Perms.HELPOP);
        if (args.length == 0) {
            throw CommandFail.usage("/helpop <Nachricht>");
        }
        String message = join(args, 0);
        if (message.length() > 256) {
            message = message.substring(0, 256);
        }
        if (sender instanceof Player p) {
            long left = cooldowns.remainingSeconds("helpop", p.getUniqueId());
            if (left > 0) {
                throw new CommandFail("helpop.cooldown", "seconds", left);
            }
            cooldowns.set("helpop", p.getUniqueId(), plugin.getConfig().getInt("helpop.cooldown-seconds", 30) * 1000L);
        }
        Component name = Component.text(sender.getName())
                .clickEvent(ClickEvent.suggestCommand("/tp " + sender.getName()))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("helpop.hover", "player", sender.getName())));
        Component line = plugin.messages().get("helpop.format", "player", name, "message", message);
        int receivers = 0;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(Perms.HELPOP_RECEIVE)) {
                staff.sendMessage(line);
                staff.playSound(Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, Sound.Source.MASTER, 1f, 1.2f));
                receivers++;
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
        String logLine = "[" + LocalDateTime.now(plugin.multipliers().zone()).format(LOG_TIME) + "] " + sender.getName() + ": " + message;
        Path file = plugin.getDataFolder().toPath().resolve("helpop.log");
        plugin.tasks().io("helpop log", () -> {
            try {
                Files.writeString(file, logLine + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not write helpop.log: " + e.getMessage());
            }
        });
        plugin.messages().send(sender, receivers > 0 ? "helpop.sent" : "helpop.sent-offline");
    }

    // ---- /stats -------------------------------------------------------------------------------------------

    private void stats(CommandSender sender, String[] args) throws CommandFail {
        permission(sender, Perms.STATS);
        if (args.length == 0) {
            Player player = player(sender);
            show(sender, data(player).snapshot(), plugin.playerData().get(player));
            return;
        }
        String name = args[0];
        plugin.playerData().lookup(name).whenComplete((snap, ex) -> plugin.tasks().sync(() -> {
            if (ex != null) {
                plugin.messages().send(sender, "general.error");
            } else if (snap.isEmpty()) {
                plugin.messages().send(sender, "general.player-unknown", "player", name);
            } else {
                show(sender, snap.get(), plugin.playerData().get(snap.get().uuid()));
            }
        }));
    }

    private void show(CommandSender to, PlayerData.Snapshot s, PlayerData live) {
        Rank rank = live != null ? plugin.ranks().rankOf(live) : plugin.ranks().byId(s.rankId()) == null
                ? plugin.ranks().defaultRank() : plugin.ranks().byId(s.rankId());
        long playtime = live != null ? live.livePlaytime(System.currentTimeMillis()) : s.playtime();
        plugin.messages().sendRaw(to, "stats.show", "player", s.name(), "rank", Text.parse(rank.display()),
                "coins", s.coins(), "gems", s.gems(), "kills", s.kills(), "deaths", s.deaths(),
                "kd", Numbers.formatDecimal(Math.round(s.kills() * 100.0 / Math.max(1, s.deaths())) / 100.0),
                "mobkills", s.mobKills(), "playtime", Durations.formatSeconds(playtime), "event_points", s.eventPoints(),
                "events_won", s.eventsWon(), "boss_damage", s.bossDamage(), "relics", s.relicsFound(),
                "crates", s.cratesOpened(), "streak", s.bestStreak(), "daily_streak", s.dailyStreak());
    }

    // ---- /top ---------------------------------------------------------------------------------------------

    public static void top(MythosCraft plugin, CommandSender sender, String[] args) throws CommandFail {
        permission(sender, Perms.TOP);
        if (args.length == 0) {
            throw CommandFail.usage("/mythos top <" + String.join("|", statIds()) + ">");
        }
        Stat stat = Stat.byId(args[0]);
        if (stat == null) {
            throw new CommandFail("stats.unknown", "stats", String.join(", ", statIds()));
        }
        plugin.leaderboards().top(stat, 10).whenComplete((list, ex) -> {
            if (ex != null) {
                plugin.messages().send(sender, "general.error");
                return;
            }
            plugin.messages().send(sender, "stats.top-header", "stat", plugin.messages().get("stats.names." + stat.id()));
            int place = 0;
            for (var e : list) {
                place++;
                plugin.messages().sendRaw(sender, place <= 3 ? "stats.top-line-podium" : "stats.top-line", "place", place,
                        "player", e.name(), "value", stat.format(e.value()));
            }
            if (list.isEmpty()) {
                plugin.messages().sendRaw(sender, "stats.top-empty");
            }
        });
    }

    public static List<String> statIds() {
        return Arrays.stream(Stat.values()).map(Stat::id).toList();
    }

    // ---- /event -------------------------------------------------------------------------------------------

    /** /event tp – the spawn button: beams the player to the place of the running event. */
    private void eventTeleport(Player player) {
        MythosEvent target = plugin.events().teleportTarget();
        if (target == null) {
            eventInfo(player);
            return;
        }
        Location at = target.venue();
        if (at == null) {
            plugin.messages().send(player, "events.tp-everywhere", "event", target.displayName());
            return;
        }
        if (plugin.arenas().isInArena(player)) {
            plugin.messages().send(player, "events.tp-in-arena");
            return;
        }
        plugin.teleports().teleport(player, at.clone().add(0, 0.2, 0), 3, true, target.displayName(), null);
    }

    private void eventInfo(CommandSender sender) {
        MythosEvent active = plugin.events().active();
        if (active == null) {
            long next = plugin.events().nextAutoMillis();
            plugin.messages().send(sender, next >= 0 ? "events.none-next" : "events.none",
                    "time", Durations.format(java.time.Duration.ofMillis(Math.max(0, next))));
            return;
        }
        plugin.messages().send(sender, "events.info-header", "event", active.displayName(), "status", active.statusLine());
        if (active.venue() != null) {
            plugin.messages().send(sender, "events.tp-hint");
        }
        for (Component line : active.infoLines(sender instanceof Player p ? p : null)) {
            sender.sendMessage(line);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return switch (label) {
            case "stats" -> onlineNames(sender);
            case "top" -> statIds();
            default -> List.of();
        };
    }
}
