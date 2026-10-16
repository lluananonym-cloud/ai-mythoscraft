package online.mythoscraft.core.display;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /discord – the clickable invite link (discord.invite in config.yml). */
public final class DiscordCommand extends BaseCommand {

    public DiscordCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String link = plugin.getConfig().getString("discord.invite", "").trim();
        if (link.isEmpty()) {
            plugin.messages().send(sender, "discord.none");
            return;
        }
        plugin.messages().sendRaw(sender, "discord.link", "link", link);
        if (sender instanceof Player p) {
            SoundSpec.of("entity.experience_orb.pickup", 0.6f, 1.4f).play(p);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return List.of();
    }
}
