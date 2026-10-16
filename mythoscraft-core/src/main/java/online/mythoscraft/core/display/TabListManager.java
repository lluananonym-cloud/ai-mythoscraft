package online.mythoscraft.core.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Tab list header/footer. With the resource pack the header shows the MythosCraft logo glyph. */
public final class TabListManager {

    private final MythosCraft plugin;
    private boolean enabled = true;
    private List<String> header = List.of();
    private List<String> footer = List.of();
    private List<String> headerEn = List.of();
    private List<String> footerEn = List.of();
    private int logoPadding = 3;

    public TabListManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("tablist");
        enabled = cfg.bool("enabled", true);
        header = cfg.strings("header");
        footer = cfg.strings("footer");
        headerEn = cfg.strings("header-en");
        footerEn = cfg.strings("footer-en");
        logoPadding = cfg.integer("logo-padding-lines", 2, 0, 10);
        plugin.tasks().cancel(this);
        if (enabled) {
            int interval = cfg.integer("update-interval-ticks", 100, 20, 1200);
            plugin.tasks().repeat(this, 20, interval, "tablist", this::updateAll);
        }
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p);
        }
    }

    public void update(Player player) {
        if (!enabled) {
            return;
        }
        Object[] ph = plugin.messages().in(player, () -> plugin.placeholders().of(player));
        boolean en = plugin.messages().lang(player).equals("en");
        List<String> header = en && !headerEn.isEmpty() ? headerEn : this.header;
        List<String> footer = en && !footerEn.isEmpty() ? footerEn : this.footer;
        List<Component> head = new ArrayList<>();
        if (plugin.display().hasPack(player)) {
            head.add(Glyphs.logo());
            for (int i = 0; i < logoPadding; i++) {
                head.add(Component.empty());
            }
        }
        for (String line : header) {
            head.add(plugin.messages().format(line, ph));
        }
        List<Component> foot = new ArrayList<>();
        for (String line : footer) {
            foot.add(plugin.messages().format(line, ph));
        }
        player.sendPlayerListHeaderAndFooter(Component.join(JoinConfiguration.newlines(), head),
                Component.join(JoinConfiguration.newlines(), foot));
    }
}
