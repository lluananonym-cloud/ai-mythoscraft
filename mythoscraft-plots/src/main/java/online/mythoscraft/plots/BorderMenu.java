package online.mythoscraft.plots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** /plot border – the owner picks the slab (colour) of his plot's border. */
final class BorderMenu implements Listener {

    /** border choices, in menu order */
    static final List<Material> SLABS = List.of(
            Material.QUARTZ_SLAB, Material.SMOOTH_STONE_SLAB, Material.STONE_BRICK_SLAB, Material.DEEPSLATE_TILE_SLAB,
            Material.BLACKSTONE_SLAB, Material.POLISHED_TUFF_SLAB, Material.SANDSTONE_SLAB, Material.RED_SANDSTONE_SLAB,
            Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB, Material.DARK_OAK_SLAB,
            Material.CHERRY_SLAB, Material.MANGROVE_SLAB, Material.BAMBOO_MOSAIC_SLAB, Material.PALE_OAK_SLAB,
            Material.CRIMSON_SLAB, Material.WARPED_SLAB, Material.PRISMARINE_SLAB, Material.DARK_PRISMARINE_SLAB,
            Material.PURPUR_SLAB, Material.END_STONE_BRICK_SLAB, Material.MUD_BRICK_SLAB, Material.BRICK_SLAB,
            Material.NETHER_BRICK_SLAB, Material.RED_NETHER_BRICK_SLAB, Material.CUT_COPPER_SLAB, Material.OXIDIZED_CUT_COPPER_SLAB,
            Material.RESIN_BRICK_SLAB, Material.MOSSY_COBBLESTONE_SLAB, Material.DIORITE_SLAB, Material.POLISHED_GRANITE_SLAB);

    private static final class Holder implements InventoryHolder {
        final PlotId plot;
        Inventory inv;

        Holder(PlotId plot) {
            this.plot = plot;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final MythosPlots plugin;

    BorderMenu(MythosPlots plugin) {
        this.plugin = plugin;
    }

    static void open(MythosPlots plugin, Player p, Plot plot) {
        Holder h = new Holder(plot.id());
        Inventory inv = Bukkit.createInventory(h, 45, plugin.msg().get("border.title"));
        h.inv = inv;
        Material current = plot.border != null ? plot.border : plugin.defaultBorder(true);
        for (int i = 0; i < SLABS.size(); i++) {
            Material m = SLABS.get(i);
            ItemStack it = new ItemStack(m);
            boolean chosen = m == current;
            it.editMeta(meta -> {
                meta.displayName(Component.text(nice(m), chosen ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(plugin.msg().get(chosen ? "border.chosen" : "border.click").decoration(TextDecoration.ITALIC, false)));
                if (chosen) {
                    meta.setEnchantmentGlintOverride(true);
                }
            });
            inv.setItem(9 * (1 + i / 8) + i % 8, it);      // 4 rows of 8 under the reset button
        }
        ItemStack reset = new ItemStack(Material.BARRIER);
        reset.editMeta(meta -> meta.displayName(plugin.msg().get("border.reset").decoration(TextDecoration.ITALIC, false)));
        inv.setItem(4, reset);
        p.openInventory(inv);
    }

    private static String nice(Material m) {
        String s = m.name().replace("_SLAB", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String w : s.split(" ")) {
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        ItemStack it = e.getCurrentItem();
        Plot plot = plugin.store().get(h.plot);
        if (it == null || plot == null || (!plot.owner().equals(p.getUniqueId()) && !plugin.bypass(p))) {
            return;
        }
        if (it.getType() == Material.BARRIER) {
            plot.border = null;
        } else if (SLABS.contains(it.getType())) {
            plot.border = it.getType();
        } else {
            return;
        }
        plugin.store().save();
        plugin.updateBorder(plot.id(), true);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
        plugin.msg().send(p, "border.set", "block", nice(plot.border != null ? plot.border : plugin.defaultBorder(true)));
        p.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
