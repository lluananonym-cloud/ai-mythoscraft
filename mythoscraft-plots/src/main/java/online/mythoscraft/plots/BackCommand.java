package online.mythoscraft.plots;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /smp, /mythos, /hub – back to the main server. First the proxy is asked directly (BungeeCord "Connect" message);
 * if the player is still here after 2 s, a window + clickable chat line let his client send MineKeep's
 * {@code /mk join mythoscraftsmp} itself.
 */
final class BackCommand implements CommandExecutor {

    private final MythosPlots plugin;

    BackCommand(MythosPlots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.local()) {
            plugin.toSurvival(p);
            return true;
        }
        plugin.msg().send(p, "transfer.sending");
        plugin.backToMain(p);
        return true;
    }

    /** window with a "join" button that runs the command from the player's client */
    static void offer(MythosPlots plugin, Player p, String cmd) {
        Msg m = plugin.msg();
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.3f, 1.8f);
        m.plain(p, "transfer.click", "command", cmd);
        try {
            ActionButton join = ActionButton.create(m.get("transfer.button"), m.get("transfer.button-hover", "command", cmd), 200,
                    DialogAction.staticAction(ClickEvent.runCommand(cmd)));
            ActionButton stay = ActionButton.create(m.get("transfer.cancel"), Component.empty(), 120, null);
            Dialog dialog = Dialog.create(f -> f.empty()
                    .base(DialogBase.builder(m.get("transfer.title"))
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(m.get("transfer.dialog-text"), 260)))
                            .build())
                    .type(DialogType.confirmation(join, stay)));
            p.showDialog(dialog);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().fine("Back dialog not shown: " + e);
        }
    }
}
