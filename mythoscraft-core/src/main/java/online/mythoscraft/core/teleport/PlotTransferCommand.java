package online.mythoscraft.core.teleport;

import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * /plot – beams the player to the plot server (own server with the MythosPlots plugin) through Minecraft's transfer
 * packet. The plot server needs {@code accepts-transfers=true} in its server.properties; /smp there brings the player
 * back. Host and port: {@code plot-server} in config.yml.
 */
public final class PlotTransferCommand extends BaseCommand {

    public PlotTransferCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("plot-server.enabled", true)) {
            plugin.messages().send(p, "plot-server.disabled");
            return;
        }
        String host = cfg.getString("plot-server.host", "mythoscraftsmpplot.minekeep.gg");
        int port = cfg.getInt("plot-server.port", 25565);
        plugin.messages().send(p, "plot-server.sending");
        p.showTitle(Title.title(plugin.messages().get("plot-server.title"), plugin.messages().get("plot-server.subtitle"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(300))));
        SoundSpec.of("block.portal.travel", 0.4f, 1.6f).play(p);
        plugin.tasks().later(30, "plot transfer", () -> {
            if (!p.isOnline()) {
                return;
            }
            plugin.getLogger().info("Transferring " + p.getName() + " to the plot server " + host + ":" + port);
            try {
                p.transfer(host, port);
            } catch (RuntimeException e) {
                plugin.messages().send(p, "plot-server.failed", "host", host, "port", port);
                plugin.getLogger().warning("Transfer to plot server failed: " + e);
                return;
            }
            // still here after a few seconds: the client or the host did not follow the transfer
            plugin.tasks().later(100, "plot transfer check", () -> {
                if (p.isOnline()) {
                    plugin.getLogger().warning(p.getName() + " is still online 5 s after the transfer to " + host + ":" + port
                            + " – check accepts-transfers=true on the plot server and that the address is reachable.");
                    plugin.messages().send(p, "plot-server.stuck", "host", host, "port", port);
                }
            });
        });
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return List.of();
    }
}
