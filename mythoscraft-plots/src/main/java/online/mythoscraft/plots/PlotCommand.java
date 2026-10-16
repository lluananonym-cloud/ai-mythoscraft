package online.mythoscraft.plots;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /plot – claim, visit and manage plots. */
final class PlotCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("auto", "claim", "home", "list", "visit", "info", "add", "remove",
            "deny", "undeny", "border", "unclaim", "clear", "spawn", "help");

    private final MythosPlots plugin;

    PlotCommand(MythosPlots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Msg m = plugin.msg();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "auto", "a" -> auto(p);
            case "claim", "c" -> claim(p);
            case "home", "h" -> home(p, args);
            case "list" -> list(p);
            case "visit", "v", "tp" -> visit(p, args);
            case "info", "i" -> info(p);
            case "add", "trust" -> member(p, args, true);
            case "remove", "untrust" -> member(p, args, false);
            case "deny" -> deny(p, args, true);
            case "undeny" -> deny(p, args, false);
            case "sethome", "setentrance" -> plugin.msg().send(p, "entrance-fixed");
            case "border", "color", "colour", "rand" -> {
                Plot plot = own(p);
                if (plot != null) {
                    BorderMenu.open(plugin, p, plot);
                }
            }
            case "unclaim", "delete" -> unclaim(p, args);
            case "clear", "reset" -> clear(p, args);
            case "spawn" -> p.teleport(plugin.spawn());
            case "admin" -> admin(p, args);
            default -> m.plain(p, "help");
        }
        return true;
    }

    private Plot own(Player p) {
        PlotId id = plugin.plotAt(p.getLocation());
        Plot plot = plugin.store().get(id);
        if (plot == null || (!plot.owner().equals(p.getUniqueId()) && !plugin.bypass(p))) {
            plugin.msg().send(p, "not-your-plot");
            return null;
        }
        return plot;
    }

    private boolean limitOk(Player p) {
        int max = plugin.maxPlots(p);
        if (plugin.store().of(p.getUniqueId()).size() >= max) {
            plugin.msg().send(p, "limit", "max", max);
            return false;
        }
        return true;
    }

    private void claimed(Player p, PlotId id) {
        plugin.store().claim(id, p.getUniqueId(), p.getName());
        plugin.updateBorder(id, true);
        plugin.msg().send(p, "claimed", "id", id);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
    }

    private void auto(Player p) {
        if (!limitOk(p)) {
            return;
        }
        // spiral around the centre: the nearest free plot
        for (int r = 0; r < 400; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != r) {
                        continue;
                    }
                    PlotId id = new PlotId(x, z);
                    if (plugin.store().get(id) == null) {
                        p.teleport(plugin.entrance(id));
                        claimed(p, id);
                        return;
                    }
                }
            }
        }
        plugin.msg().send(p, "no-free-plot");
    }

    private void claim(Player p) {
        PlotId id = plugin.plotAt(p.getLocation());
        if (id == null) {
            plugin.msg().send(p, "not-on-plot");
            return;
        }
        if (plugin.store().get(id) != null) {
            plugin.msg().send(p, "already-claimed", "owner", plugin.store().get(id).ownerName());
            return;
        }
        if (limitOk(p)) {
            claimed(p, id);
        }
    }

    private void home(Player p, String[] args) {
        List<Plot> own = plugin.store().of(p.getUniqueId());
        if (own.isEmpty()) {
            plugin.msg().send(p, "no-plot");
            return;
        }
        int n = 1;
        try {
            n = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        } catch (NumberFormatException ignored) {
            // first plot
        }
        Plot plot = own.get(Math.max(0, Math.min(own.size() - 1, n - 1)));
        p.teleport(plugin.entrance(plot.id()));
        plugin.msg().send(p, "teleported", "id", plot.id());
    }

    private void list(Player p) {
        List<Plot> own = plugin.store().of(p.getUniqueId());
        if (own.isEmpty()) {
            plugin.msg().send(p, "no-plot");
            return;
        }
        plugin.msg().send(p, "list-header", "count", own.size(), "max", plugin.maxPlots(p) == Integer.MAX_VALUE ? "∞" : plugin.maxPlots(p));
        for (int i = 0; i < own.size(); i++) {
            plugin.msg().plain(p, "list-line", "n", i + 1, "id", own.get(i).id());
        }
    }

    private void visit(Player p, String[] args) {
        if (args.length < 2) {
            plugin.msg().send(p, "usage", "usage", "/plot visit <player> [n]");
            return;
        }
        OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(args[1]);
        List<Plot> plots = o == null ? List.of() : plugin.store().of(o.getUniqueId());
        if (plots.isEmpty()) {
            plugin.msg().send(p, "player-no-plot", "player", args[1]);
            return;
        }
        int n = 1;
        try {
            n = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        } catch (NumberFormatException ignored) {
            // first plot
        }
        Plot plot = plots.get(Math.max(0, Math.min(plots.size() - 1, n - 1)));
        if (plot.denied.contains(p.getUniqueId()) && !plugin.bypass(p)) {
            plugin.msg().send(p, "you-are-denied");
            return;
        }
        p.teleport(plugin.entrance(plot.id()));
        plugin.msg().send(p, "visiting", "player", plot.ownerName(), "id", plot.id());
    }

    private void info(Player p) {
        PlotId id = plugin.plotAt(p.getLocation());
        if (id == null) {
            plugin.msg().send(p, "not-on-plot");
            return;
        }
        Plot plot = plugin.store().get(id);
        if (plot == null) {
            plugin.msg().send(p, "info-free", "id", id);
            return;
        }
        plugin.msg().plain(p, "info", "id", id, "owner", plot.ownerName(), "trusted", names(plot.trusted),
                "denied", names(plot.denied), "date", new SimpleDateFormat("yyyy-MM-dd").format(new Date(plot.claimedAt)));
    }

    private static String names(java.util.Set<UUID> ids) {
        if (ids.isEmpty()) {
            return "-";
        }
        List<String> out = new ArrayList<>();
        ids.forEach(u -> out.add(String.valueOf(Bukkit.getOfflinePlayer(u).getName())));
        return String.join(", ", out);
    }

    private OfflinePlayer target(Player p, String[] args, String usage) {
        if (args.length < 2) {
            plugin.msg().send(p, "usage", "usage", usage);
            return null;
        }
        OfflinePlayer o = Bukkit.getPlayerExact(args[1]);
        if (o == null) {
            o = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (o == null) {
            plugin.msg().send(p, "unknown-player", "player", args[1]);
        }
        return o;
    }

    private void member(Player p, String[] args, boolean add) {
        Plot plot = own(p);
        OfflinePlayer o = plot == null ? null : target(p, args, "/plot " + (add ? "add" : "remove") + " <player>");
        if (o == null) {
            return;
        }
        if (add) {
            plot.trusted.add(o.getUniqueId());
        } else {
            plot.trusted.remove(o.getUniqueId());
        }
        plugin.store().save();
        plugin.msg().send(p, add ? "trusted" : "untrusted", "player", args[1]);
    }

    private void deny(Player p, String[] args, boolean deny) {
        Plot plot = own(p);
        OfflinePlayer o = plot == null ? null : target(p, args, "/plot " + (deny ? "deny" : "undeny") + " <player>");
        if (o == null) {
            return;
        }
        if (deny) {
            plot.denied.add(o.getUniqueId());
            plot.trusted.remove(o.getUniqueId());
            Player online = o.getPlayer();
            if (online != null && plot.id().equals(plugin.plotAt(online.getLocation())) && !plugin.bypass(online)) {
                online.teleport(plugin.spawn());
                plugin.msg().send(online, "you-are-denied");
            }
        } else {
            plot.denied.remove(o.getUniqueId());
        }
        plugin.store().save();
        plugin.msg().send(p, deny ? "denied" : "undenied", "player", args[1]);
    }

    private void unclaim(Player p, String[] args) {
        Plot plot = own(p);
        if (plot == null) {
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            plugin.msg().send(p, "unclaim-confirm");
            return;
        }
        PlotId id = plot.id();
        plugin.store().remove(id);
        plugin.updateBorder(id, false);
        plugin.msg().send(p, "unclaimed", "id", id);
        plugin.reset(id, null);
    }

    private void clear(Player p, String[] args) {
        Plot plot = own(p);
        if (plot == null) {
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            plugin.msg().send(p, "clear-confirm");
            return;
        }
        plugin.msg().send(p, "clearing");
        plugin.reset(plot.id(), () -> {
            if (p.isOnline()) {
                plugin.msg().send(p, "cleared");
            }
        });
    }

    private void admin(Player p, String[] args) {
        if (!plugin.bypass(p)) {
            plugin.msg().send(p, "no-permission");
            return;
        }
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (what) {
            case "unclaim" -> {
                PlotId id = plugin.plotAt(p.getLocation());
                if (id == null || plugin.store().get(id) == null) {
                    plugin.msg().send(p, "not-on-plot");
                    return;
                }
                plugin.store().remove(id);
                plugin.updateBorder(id, false);
                plugin.reset(id, null);
                plugin.msg().send(p, "unclaimed", "id", id);
            }
            case "tp" -> {
                try {
                    p.teleport(plugin.entrance(PlotId.parse(args[2])));
                } catch (RuntimeException e) {
                    plugin.msg().send(p, "usage", "usage", "/plot admin tp <x;z>");
                }
            }
            default -> plugin.msg().send(p, "usage", "usage", "/plot admin <unclaim|tp <x;z>>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            if (sender.hasPermission("mythosplots.admin") && "admin".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("admin");
            }
            return out;
        }
        if (args.length == 2) {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (List.of("visit", "v", "tp", "add", "trust", "remove", "untrust", "deny", "undeny").contains(s)) {
                List<String> out = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
                return out;
            }
            if (s.equals("unclaim") || s.equals("clear")) {
                return List.of("confirm");
            }
            if (s.equals("admin")) {
                return List.of("unclaim", "tp");
            }
        }
        return List.of();
    }
}
