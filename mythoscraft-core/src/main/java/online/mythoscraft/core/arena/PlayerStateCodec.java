package online.mythoscraft.core.arena;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Serialises inventory, XP and food of a player into bytes (Paper item serialisation, version-safe). */
public final class PlayerStateCodec {

    private static final int VERSION = 1;

    private PlayerStateCodec() {
    }

    public static byte[] encode(Player player) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(player.getLevel());
            out.writeFloat(player.getExp());
            out.writeInt(player.getFoodLevel());
            out.writeFloat(player.getSaturation());
            byte[] items = ItemStack.serializeItemsAsBytes(player.getInventory().getContents());
            out.writeInt(items.length);
            out.write(items);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialise inventory of " + player.getName(), e);
        }
    }

    public static void restore(Player player, byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unknown player state version " + version);
            }
            int level = in.readInt();
            float exp = in.readFloat();
            int food = in.readInt();
            float saturation = in.readFloat();
            byte[] items = new byte[in.readInt()];
            in.readFully(items);
            ItemStack[] contents = ItemStack.deserializeItemsFromBytes(items);
            player.getInventory().clear();
            player.getInventory().setContents(contents);
            player.setLevel(level);
            player.setExp(Math.max(0f, Math.min(1f, exp)));
            player.setFoodLevel(food);
            player.setSaturation(saturation);
        }
    }
}
