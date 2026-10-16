package online.mythoscraft.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * /pronounce &lt;text&gt; (aliases /announce, /ankuendigung, /bc): announcement for everybody – framed chat message,
 * big title on screen and a sound. "-c" = chat only. MiniMessage (&lt;gold&gt;) and &amp;-colour codes work.
 */
public final class AnnounceCommand extends BaseCommand {

    public static final String PERMISSION = "mythos.announce";

    public AnnounceCommand(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        permission(sender, PERMISSION);
        boolean chatOnly = args.length > 0 && args[0].equalsIgnoreCase("-c");
        String[] words = chatOnly ? Arrays.copyOfRange(args, 1, args.length) : args;
        if (words.length == 0) {
            throw new CommandFail("announce.usage");
        }
        String raw = String.join(" ", words);
        Component text = raw.indexOf('&') >= 0 && raw.indexOf('<') < 0
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
                : Text.parse(raw);
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.messages().in(p, () -> {
                plugin.messages().sendRaw(p, "announce.chat", "text", text, "sender", sender.getName());
                if (!chatOnly) {
                    p.showTitle(Title.title(plugin.messages().get("announce.title"), text,
                            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(4500), Duration.ofMillis(800))));
                }
            });
            SoundSpec.of("block.note_block.bell", 0.9f, 1.2f).play(p);
        }
        plugin.getLogger().info("[Announcement by " + sender.getName() + "] " + raw);
        plugin.messages().send(sender, "announce.sent");
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 && sender.hasPermission(PERMISSION) ? List.of("-c") : List.of();
    }
}
