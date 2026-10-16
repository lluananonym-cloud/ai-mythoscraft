package online.mythoscraft.core.teleport;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /plot – to the plot server. MineKeep moves players between its servers with its own proxy command
 * {@code /mk join <server>}, which only works when the player's client sends it. So /plot opens a small window with a
 * "Join" button (and posts a clickable line in chat) – one click sends the command from the player's game.
 */
public final class PlotTransferCommand extends BaseCommand {

    /** who used /plot recently – their quit is shown as "went to the plot world" */
    private static final java.util.Map<java.util.UUID, Long> HOPS = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean hopped(Player p) {
        Long at = HOPS.remove(p.getUniqueId());
        return at != null && System.currentTimeMillis() - at < 120_000;
    }

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
        String command = cfg.getString("plot-server.join-command", "mk join mythoscraftsmpplot").trim();
        String server = cfg.getString("plot-server.proxy-server", "mythoscraftsmpplot").trim();
        String cmd = command.startsWith("/") ? command : "/" + command;
        plugin.messages().send(p, "plot-server.sending");
        HOPS.put(p.getUniqueId(), System.currentTimeMillis());
        // 1) straight away through the proxy (BungeeCord "Connect" message – understood by Velocity/Bungee based proxies)
        connect(plugin, p, server);
        // 2) still here after 2 s: the proxy did not move him – the button lets his client send /mk join itself
        plugin.tasks().later(40, "plot join fallback", () -> {
            if (p.isOnline()) {
                offer(plugin, p, cmd);
            }
        });
    }

    /** Asks the proxy to move the player to {@code server} (plugin message on the BungeeCord channel). */
    public static void connect(MythosCraft plugin, Player p, String server) {
        try {
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            plugin.getLogger().info("Asked the proxy to move " + p.getName() + " to " + server);
        } catch (Exception e) {
            plugin.getLogger().warning("Proxy connect to " + server + " failed: " + e.getMessage());
        }
    }

    /** Window + clickable chat line that let the player's client send the MineKeep join command. */
    public static void offer(MythosCraft plugin, Player p, String command) {
        var m = plugin.messages();
        SoundSpec.of("block.portal.trigger", 0.3f, 1.8f).play(p);
        m.sendRaw(p, "plot-server.click", "command", command);
        try {
            ClickEvent click = ClickEvent.runCommand(command);
            ActionButton join = ActionButton.create(m.get("plot-server.button"), m.get("plot-server.button-hover", "command", command), 200,
                    DialogAction.staticAction(click));
            ActionButton stay = ActionButton.create(m.get("plot-server.cancel"), Component.empty(), 120, null);
            Dialog dialog = Dialog.create(f -> f.empty()
                    .base(DialogBase.builder(m.get("plot-server.title"))
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(m.get("plot-server.dialog-text"), 260)))
                            .build())
                    .type(DialogType.confirmation(join, stay)));
            p.showDialog(dialog);
        } catch (RuntimeException | LinkageError e) {
            // older client / API: the clickable chat line is enough
            plugin.getLogger().fine("Plot dialog not shown: " + e);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return List.of();
    }
}
