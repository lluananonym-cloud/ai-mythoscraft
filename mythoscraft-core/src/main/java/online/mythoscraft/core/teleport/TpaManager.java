package online.mythoscraft.core.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.protection.Region;
import online.mythoscraft.core.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * /tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel. Each player has at most one outgoing request; every target keeps
 * a bounded list of incoming requests. Requests expire and are dropped when either side leaves.
 */
public final class TpaManager implements Listener {

    private final MythosCraft plugin;
    private final Cooldowns cooldowns;
    private final Map<UUID, Request> outgoing = new HashMap<>();
    private final Map<UUID, Deque<Request>> incoming = new HashMap<>();
    private int timeoutSeconds = 60;
    private int cooldownSeconds = 10;
    private int maxIncoming = 5;
    private int delay = 3;
    private boolean cancelOnMove = true;
    private boolean denyIntoArenas = true;
    private boolean denyIntoPvp;

    private record Request(UUID from, UUID to, boolean here, long expires) {
    }

    public TpaManager(MythosCraft plugin, Cooldowns cooldowns) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("teleport.tpa");
        timeoutSeconds = cfg.integer("timeout", 60, 5, 600);
        cooldownSeconds = cfg.integer("cooldown", 10, 0, 3600);
        maxIncoming = cfg.integer("max-incoming", 5, 1, 50);
        delay = cfg.integer("teleport-delay", 3, 0, 120);
        cancelOnMove = cfg.bool("cancel-on-move", true);
        denyIntoArenas = cfg.bool("deny-into-arenas", true);
        denyIntoPvp = cfg.bool("deny-into-pvp-areas", false);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 20, "tpa expiry", this::expire);
    }

    public void request(Player from, Player to, boolean here) {
        if (from.equals(to)) {
            plugin.messages().send(from, "tpa.self");
            return;
        }
        long cd = cooldowns.remainingSeconds("tpa", from.getUniqueId());
        if (cd > 0) {
            plugin.messages().send(from, "teleport.cooldown", "seconds", cd);
            return;
        }
        Deque<Request> queue = incoming.computeIfAbsent(to.getUniqueId(), u -> new ArrayDeque<>());
        if (queue.stream().anyMatch(r -> r.from.equals(from.getUniqueId()))) {
            plugin.messages().send(from, "tpa.already-pending", "player", to.getName());
            return;
        }
        if (queue.size() >= maxIncoming) {
            plugin.messages().send(from, "tpa.target-full", "player", to.getName());
            return;
        }
        Request previous = outgoing.remove(from.getUniqueId());
        if (previous != null) {
            removeIncoming(previous);
        }
        Request req = new Request(from.getUniqueId(), to.getUniqueId(), here, System.currentTimeMillis() + timeoutSeconds * 1000L);
        outgoing.put(from.getUniqueId(), req);
        queue.addLast(req);
        cooldowns.set("tpa", from.getUniqueId(), cooldownSeconds * 1000L);

        plugin.messages().send(from, here ? "tpa.sent-here" : "tpa.sent", "player", to.getName(), "seconds", timeoutSeconds);
        plugin.messages().send(to, here ? "tpa.received-here" : "tpa.received", "player", from.getName(), "seconds", timeoutSeconds);
        Component accept = plugin.messages().get("tpa.button-accept")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + from.getName()))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("tpa.hover-accept")));
        Component deny = plugin.messages().get("tpa.button-deny")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + from.getName()))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("tpa.hover-deny")));
        to.sendMessage(plugin.messages().prefix().append(accept).append(Component.space()).append(deny));
        to.playSound(net.kyori.adventure.sound.Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, net.kyori.adventure.sound.Sound.Source.MASTER, 0.8f, 1.6f));
    }

    public void accept(Player target, String fromName) {
        Request req = find(target, fromName);
        if (req == null) {
            plugin.messages().send(target, fromName == null ? "tpa.none" : "tpa.none-from", "player", fromName == null ? "" : fromName);
            return;
        }
        Player from = Bukkit.getPlayer(req.from);
        removeRequest(req);
        if (from == null) {
            plugin.messages().send(target, "tpa.offline");
            return;
        }
        Player mover = req.here ? target : from;
        Player destination = req.here ? from : target;
        String problem = checkTeleport(mover, destination.getLocation());
        if (problem != null) {
            plugin.messages().send(mover, problem);
            plugin.messages().send(target, "tpa.failed", "player", mover.getName());
            return;
        }
        plugin.messages().send(target, "tpa.accepted-target", "player", from.getName());
        plugin.messages().send(from, "tpa.accepted-sender", "player", target.getName());
        Location dest = destination.getLocation();
        plugin.teleports().teleport(mover, dest, delay, cancelOnMove, Component.text(destination.getName()), null);
    }

    public void deny(Player target, String fromName) {
        Request req = find(target, fromName);
        if (req == null) {
            plugin.messages().send(target, fromName == null ? "tpa.none" : "tpa.none-from", "player", fromName == null ? "" : fromName);
            return;
        }
        removeRequest(req);
        Player from = Bukkit.getPlayer(req.from);
        plugin.messages().send(target, "tpa.denied-target", "player", from == null ? "?" : from.getName());
        if (from != null) {
            plugin.messages().send(from, "tpa.denied-sender", "player", target.getName());
        }
    }

    public void cancel(Player from) {
        Request req = outgoing.remove(from.getUniqueId());
        if (req == null) {
            plugin.messages().send(from, "tpa.no-outgoing");
            return;
        }
        removeIncoming(req);
        plugin.messages().send(from, "tpa.cancelled");
        Player to = Bukkit.getPlayer(req.to);
        if (to != null) {
            plugin.messages().send(to, "tpa.cancelled-target", "player", from.getName());
        }
    }

    /** Null if the teleport is fine, otherwise the message key explaining why not. */
    private String checkTeleport(Player mover, Location destination) {
        if (plugin.arenas().isInArena(mover)) {
            return "arena.leave-first";
        }
        long combat = plugin.combat().remainingSeconds(mover);
        if (combat > 0) {
            return "tpa.in-combat";
        }
        if (denyIntoArenas && plugin.arenas().arenaAt(destination) != null) {
            return "tpa.into-arena";
        }
        if (denyIntoPvp) {
            Region region = plugin.regions().top(destination);
            if (region != null && Boolean.TRUE.equals(region.flag(online.mythoscraft.core.protection.Flag.PVP))) {
                return "tpa.into-pvp";
            }
        }
        return null;
    }

    public java.util.List<String> incomingNames(Player target) {
        Deque<Request> queue = incoming.get(target.getUniqueId());
        if (queue == null) {
            return java.util.List.of();
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Request r : queue) {
            Player p = Bukkit.getPlayer(r.from);
            if (p != null) {
                names.add(p.getName());
            }
        }
        return names;
    }

    private Request find(Player target, String fromName) {
        Deque<Request> queue = incoming.get(target.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        if (fromName == null) {
            return queue.peekLast();
        }
        for (Request r : queue) {
            Player p = Bukkit.getPlayer(r.from);
            if (p != null && p.getName().equalsIgnoreCase(fromName)) {
                return r;
            }
        }
        return null;
    }

    private void removeRequest(Request req) {
        outgoing.remove(req.from, req);
        removeIncoming(req);
    }

    private void removeIncoming(Request req) {
        Deque<Request> queue = incoming.get(req.to);
        if (queue != null) {
            queue.remove(req);
            if (queue.isEmpty()) {
                incoming.remove(req.to);
            }
        }
    }

    private void expire() {
        if (outgoing.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Request> it = outgoing.values().iterator();
        while (it.hasNext()) {
            Request r = it.next();
            if (r.expires > now) {
                continue;
            }
            it.remove();
            removeIncoming(r);
            Player from = Bukkit.getPlayer(r.from);
            Player to = Bukkit.getPlayer(r.to);
            if (from != null) {
                plugin.messages().send(from, "tpa.expired-sender", "player", to == null ? "?" : to.getName());
            }
            if (to != null) {
                plugin.messages().send(to, "tpa.expired-target", "player", from == null ? "?" : from.getName());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Request out = outgoing.remove(id);
        if (out != null) {
            removeIncoming(out);
            Player to = Bukkit.getPlayer(out.to);
            if (to != null) {
                plugin.messages().send(to, "tpa.left", "player", event.getPlayer().getName());
            }
        }
        Deque<Request> in = incoming.remove(id);
        if (in != null) {
            for (Request r : in) {
                outgoing.remove(r.from, r);
                Player from = Bukkit.getPlayer(r.from);
                if (from != null) {
                    plugin.messages().send(from, "tpa.left", "player", event.getPlayer().getName());
                }
            }
        }
    }
}
