package online.mythoscraft.core.rank;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The MYTHOS rank belongs to MinePro641 and only he may hand it out. The check uses his Mojang UUID, so a renamed
 * or impersonating account cannot pass it. On an offline-mode server (without proxy forwarding) the name-derived
 * offline UUID is accepted as well – names are spoofable there, which is logged as a warning on startup.
 */
public final class MythosOwner {

    public static final String NAME = "MinePro641";
    public static final UUID ONLINE_UUID = UUID.fromString("9f799033-c0f0-4d2a-aa6f-b68ce9207af4");
    public static final UUID OFFLINE_UUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));

    private MythosOwner() {
    }

    public static boolean is(UUID uuid) {
        if (ONLINE_UUID.equals(uuid)) {
            return true;
        }
        return OFFLINE_UUID.equals(uuid) && !Bukkit.getOnlineMode();
    }

    public static boolean is(CommandSender sender) {
        return sender instanceof Player p && is(p.getUniqueId());
    }
}
