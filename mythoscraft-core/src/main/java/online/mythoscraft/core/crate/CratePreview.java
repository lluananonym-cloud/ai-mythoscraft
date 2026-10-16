package online.mythoscraft.core.crate;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.display.Glyphs;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Read-only overview of all rewards of a crate with their chances. */
public final class CratePreview extends Menu {

    public CratePreview(MythosCraft plugin, CrateManager manager, CrateType type, Player viewer) {
        super(6, manager.guiTitle(Glyphs.GUI_CRATE_PREVIEW, plugin.messages().get("crates.preview-title",
                "crate", Text.parse(type.name())), viewer));
        boolean pack = plugin.display().hasPack(viewer);
        int slot = pack ? 10 : 0;
        online.mythoscraft.core.util.WeightedRandom<CrateType.CrateReward> table = manager.table(type);
        List<CrateType.CrateReward> items = table.items();
        for (int i = 0; i < items.size() && slot < 54; i++) {
            CrateType.CrateReward reward = items.get(i);
            ItemStack icon = manager.rewardIcon(reward);
            double chance = table.chance(i);
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.messages().get("crates.preview-rarity", "rarity", plugin.messages().get("crates.rarity." + reward.rarity().name().toLowerCase())));
            lore.add(plugin.messages().get("crates.preview-chance", "chance", Numbers.formatDecimal(Math.round(chance * 100) / 100.0)));
            icon.editMeta(meta -> meta.lore(lore.stream().map(Text::noItalic).toList()));
            getInventory().setItem(slot, icon);
            slot++;
            if (pack && slot % 9 == 8) {
                slot += 2;
            }
        }
    }
}
