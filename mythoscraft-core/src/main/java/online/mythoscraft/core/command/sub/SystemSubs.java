package online.mythoscraft.core.command.sub;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.command.MythosCommand;
import online.mythoscraft.core.command.SubCommand;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.player.Stat;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/** help, status, reload, bypass and top. */
public final class SystemSubs {

    private final MythosCraft plugin;
    private final MythosCommand root;

    public SystemSubs(MythosCraft plugin, MythosCommand root) {
        this.plugin = plugin;
        this.root = root;
    }

    public SubCommand help() {
        return new SubCommand() {
            public String name() {
                return "help";
            }

            public String permission() {
                return null;
            }

            public void execute(CommandSender sender, String[] args) {
                plugin.messages().send(sender, "help.header", "version", plugin.getPluginMeta().getVersion());
                for (SubCommand s : root.visible(sender)) {
                    plugin.messages().sendRaw(sender, s.helpKey());
                }
                plugin.messages().sendRaw(sender, "help.player-commands");
            }
        };
    }

    public SubCommand status() {
        return new SubCommand() {
            public String name() {
                return "status";
            }

            public String permission() {
                return Perms.STATUS;
            }

            public void execute(CommandSender sender, String[] args) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                long max = rt.maxMemory() / 1024 / 1024;
                MythosEvent active = plugin.events().active();
                plugin.messages().send(sender, "status.header");
                plugin.messages().sendRaw(sender, "status.body",
                        "version", plugin.getPluginMeta().getVersion(),
                        "server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion(),
                        "tps", Numbers.formatDecimal(Math.round(Bukkit.getTPS()[0] * 100) / 100.0),
                        "database", plugin.database().describe(),
                        "db_state", plugin.messages().get(plugin.database().isReady() ? "status.ok" : "status.error"),
                        "db_pending", plugin.database().pending(),
                        "db_failures", plugin.database().failures(),
                        "regions", plugin.regions().regions().size(),
                        "arenas", plugin.arenas().arenas().size(),
                        "crates", plugin.crates().placed().size(),
                        "holograms", plugin.holograms().all().size(),
                        "players", plugin.playerData().cached().size(),
                        "event", active == null ? plugin.messages().get("events.status-none") : active.statusLine(),
                        "tasks", plugin.tasks().activeCount(),
                        "memory", used + " / " + max + " MB",
                        "pack", plugin.resourcePack().enabled() ? plugin.resourcePack().loadedCount() + " Spieler" : "aus",
                        "warnings", plugin.configProblems().list().size());
                if (plugin.database().lastError() != null) {
                    plugin.messages().sendRaw(sender, "status.last-error", "error", plugin.database().lastError());
                }
                for (String warning : plugin.configProblems().list().stream().limit(5).toList()) {
                    plugin.messages().sendRaw(sender, "status.warning", "warning", warning);
                }
            }
        };
    }

    public SubCommand reload() {
        return new SubCommand() {
            public String name() {
                return "reload";
            }

            public String permission() {
                return Perms.RELOAD;
            }

            public void execute(CommandSender sender, String[] args) {
                long start = System.currentTimeMillis();
                plugin.reloadAll();
                plugin.messages().send(sender, "general.reloaded", "ms", System.currentTimeMillis() - start,
                        "warnings", plugin.configProblems().list().size());
            }
        };
    }

    public SubCommand bypass() {
        return new SubCommand() {
            public String name() {
                return "bypass";
            }

            public String permission() {
                return Perms.BYPASS;
            }

            public void execute(CommandSender sender, String[] args) throws CommandFail {
                if (!(sender instanceof Player player)) {
                    throw new CommandFail("general.players-only");
                }
                boolean on = plugin.protection().toggleBypass(player);
                plugin.messages().send(sender, on ? "protection.bypass-on" : "protection.bypass-off");
            }
        };
    }

    public SubCommand top() {
        return new SubCommand() {
            public String name() {
                return "top";
            }

            public String permission() {
                return Perms.TOP;
            }

            public void execute(CommandSender sender, String[] args) throws CommandFail {
                online.mythoscraft.core.command.SocialCommands.top(plugin, sender, args);
            }

            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? Arrays.stream(Stat.values()).map(Stat::id).toList() : List.of();
            }
        };
    }
}
