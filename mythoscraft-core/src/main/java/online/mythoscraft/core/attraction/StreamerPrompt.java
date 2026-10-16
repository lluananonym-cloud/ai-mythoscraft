package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.gui.MenuItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Asked once on the first join: "Do you stream?" – streamers only hear stream-safe music and get a LIVE badge. */
public final class StreamerPrompt extends Menu {

    private final MythosCraft plugin;
    private boolean answered;

    public StreamerPrompt(MythosCraft plugin) {
        super(3, plugin.messages().get("stream.prompt-title"));
        this.plugin = plugin;
        getInventory().setItem(11, MenuItems.button(Material.RED_CONCRETE, plugin.messages().get("stream.prompt-yes"),
                plugin.messages().getList("stream.prompt-yes-lore")));
        getInventory().setItem(13, MenuItems.button(Material.BOOK, plugin.messages().get("stream.prompt-info"),
                plugin.messages().getList("stream.prompt-info-lore")));
        getInventory().setItem(15, MenuItems.button(Material.LIME_CONCRETE, plugin.messages().get("stream.prompt-no"),
                plugin.messages().getList("stream.prompt-no-lore")));
    }

    /** Opens the question a few seconds after the first join (only once per player). */
    public static void askLater(MythosCraft plugin, Player p) {
        plugin.tasks().later(80, "streamer prompt", () -> {
            if (p.isOnline() && plugin.progress().loaded(p) && !plugin.attractions().music().asked(p)) {
                new StreamerPrompt(plugin).open(p);
            }
        });
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot != 11 && slot != 15) {
            return;
        }
        answered = true;
        boolean on = slot == 11;
        plugin.attractions().music().setStreaming(player, on);
        player.closeInventory();
        plugin.messages().send(player, on ? "stream.enabled" : "stream.off-first");
    }

    @Override
    public void onClose(Player player) {
        if (!answered && !plugin.attractions().music().asked(player)) {
            plugin.attractions().music().setStreaming(player, false);
            plugin.messages().send(player, "stream.off-first");
        }
    }
}
