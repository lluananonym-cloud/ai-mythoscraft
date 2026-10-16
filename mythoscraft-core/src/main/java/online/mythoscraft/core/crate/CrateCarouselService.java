package online.mythoscraft.core.crate;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Crate carousel at the spawn: only a few pedestals. The standard crates always stand, the other pedestals swap
 * through all remaining crates every few minutes (crate sinks, the next one rises with particles). Event crates
 * (Halloween, Frostfest, Silvester) stand on their own pedestal only while their event runs – otherwise never.
 */
public final class CrateCarouselService {

    private final MythosCraft plugin;
    private boolean enabled = true;
    private int swapSeconds = 300;
    private List<String> fixed = List.of("common", "vip", "elite", "mythos");
    private final Map<String, String> eventCrates = new LinkedHashMap<>();
    private List<String> never = List.of();
    private final List<String> queue = new ArrayList<>();
    private int nextSlot;
    private long nextSwap;

    public CrateCarouselService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("crate-carousel");
        enabled = cfg.bool("enabled", true);
        swapSeconds = cfg.integer("swap-seconds", 300, 20, 86_400);
        fixed = lower(cfg.strings("fixed"));
        if (fixed.isEmpty() && !cfg.has("fixed")) {
            fixed = List.of("common", "vip", "elite", "mythos");
        }
        eventCrates.clear();
        ConfigReader ev = cfg.sub("event-crates");
        for (String crate : ev.keys()) {
            eventCrates.put(crate.toLowerCase(Locale.ROOT), ev.string(crate, "").toLowerCase(Locale.ROOT));
        }
        if (eventCrates.isEmpty() && !cfg.has("event-crates")) {
            eventCrates.put("halloween", "halloween");
            eventCrates.put("frostfest", "frostfest");
            eventCrates.put("silvester", "neujahr");
        }
        never = lower(cfg.strings("never"));
        queue.clear();
        removeSigns();
        nextSwap = System.currentTimeMillis() + swapSeconds * 1000L;
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 100, 20, "crate carousel", this::tick);
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        in.forEach(s -> out.add(s.trim().toLowerCase(Locale.ROOT)));
        return out;
    }

    public boolean enabled() {
        return enabled && !spots().isEmpty();
    }

    /** Carousel pedestals: first the fixed ones, then the rotating ones. */
    private List<Location> spots() {
        return plugin.attractionStore().all("crate");
    }

    private Location eventSpot() {
        return plugin.attractionStore().first("crate_event");
    }

    /** Is this an event crate, and is its event running right now? */
    public boolean isEventCrate(String crate) {
        return eventCrates.containsKey(crate);
    }

    public boolean eventActive(String crate) {
        String season = eventCrates.get(crate);
        if (season == null) {
            return false;
        }
        if (crate.equals("silvester") && plugin.attractions().ambience().silvesterTime()) {
            return true;
        }
        online.mythoscraft.core.item.SeasonService.Season s = plugin.seasons().active();
        return s != null && s.id().equalsIgnoreCase(season);
    }

    /** The event crate that should stand now, or null. */
    private String currentEventCrate() {
        for (String crate : eventCrates.keySet()) {
            if (plugin.crates().type(crate) != null && eventActive(crate)) {
                return crate;
            }
        }
        return null;
    }

    /** Crates that take turns on the rotating pedestals. */
    private List<String> rotating() {
        List<String> out = new ArrayList<>();
        for (CrateType t : plugin.crates().types()) {
            String id = t.id().toLowerCase(Locale.ROOT);
            if (!fixed.contains(id) && !eventCrates.containsKey(id) && !never.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private String shownAt(Location spot) {
        PlacedCrate c = plugin.crates().nearest(spot.clone().add(0.5, 0.5, 0.5), 0.8);
        return c == null ? null : c.type().toLowerCase(Locale.ROOT);
    }

    private void show(Location spot, String crate, boolean animate) {
        PlacedCrate old = plugin.crates().nearest(spot.clone().add(0.5, 0.5, 0.5), 0.8);
        if (old != null && crate != null && old.type().equalsIgnoreCase(crate)) {
            return;
        }
        if (old != null) {
            plugin.crates().remove(old);
        }
        if (animate && spot.isChunkLoaded()) {
            Location c = spot.clone().add(0.5, 0.8, 0.5);
            c.getWorld().spawnParticle(Particle.PORTAL, c, 60, 0.4, 0.6, 0.4, 0.4);
            c.getWorld().spawnParticle(Particle.END_ROD, c, 20, 0.2, 1.2, 0.2, 0.05);
            SoundSpec.of("block.beacon.power_select", 1f, 1.4f).playAt(c);
        }
        if (crate != null) {
            CrateType t = plugin.crates().type(crate);
            if (t != null) {
                float yaw = spot.getYaw();
                plugin.crates().place(t, spot, yaw);
            }
        }
    }

    /** Puts the right crates on every pedestal (after a spawn build or a reload). */
    public void arrange() {
        if (!enabled) {
            return;
        }
        List<Location> spots = spots();
        List<String> pool = rotating();
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            Location spot = spots.get(i);
            if (i < fixed.size()) {
                show(spot, fixed.get(i), false);
                continue;
            }
            String now = shownAt(spot);
            if (now == null || !pool.contains(now) || shown.contains(now)) {
                now = null;
                for (String c : pool) {
                    if (!shown.contains(c) && !onAnySpot(c, spots)) {
                        now = c;
                        break;
                    }
                }
                show(spot, now, false);
            }
            if (now != null) {
                shown.add(now);
            }
        }
        Location ev = eventSpot();
        if (ev != null) {
            show(ev, currentEventCrate(), false);
        }
        // event crates never stand anywhere else on the spawn
        for (PlacedCrate c : new ArrayList<>(plugin.crates().placed())) {
            String id = c.type().toLowerCase(Locale.ROOT);
            if (eventCrates.containsKey(id) && plugin.attractionStore().inside(c.location(), 0)
                    && (ev == null || c.location().distanceSquared(ev) > 1 || !eventActive(id))) {
                plugin.crates().remove(c);
            }
        }
    }

    private boolean onAnySpot(String crate, List<Location> spots) {
        for (Location s : spots) {
            if (crate.equals(shownAt(s))) {
                return true;
            }
        }
        return false;
    }

    private String lastEvent = "";

    private void tick() {
        if (!enabled()) {
            return;
        }
        String ev = currentEventCrate();
        String evKey = ev == null ? "" : ev;
        if (!evKey.equals(lastEvent)) {
            lastEvent = evKey;
            Location spot = eventSpot();
            if (spot != null) {
                show(spot, ev, true);
            }
        }
        if (System.currentTimeMillis() >= nextSwap) {
            nextSwap = System.currentTimeMillis() + swapSeconds * 1000L;
            swap();
        }
        updateSigns();
    }

    /** Crates currently standing on the carousel pedestals. */
    private List<String> shownAll() {
        List<String> shown = new ArrayList<>();
        for (Location s : spots()) {
            String c = shownAt(s);
            if (c != null) {
                shown.add(c);
            }
        }
        return shown;
    }

    /** The next {@code n} crates in the order they will come (without changing the queue). */
    private List<String> projected(int n) {
        List<String> shown = shownAll();
        List<String> q = new ArrayList<>(queue);
        q.removeIf(shown::contains);
        List<String> out = new ArrayList<>();
        List<String> pool = rotating();
        int guard = 0;
        while (out.size() < n && guard++ < 200) {
            if (q.isEmpty()) {
                for (String c : pool) {
                    if (!shown.contains(c) && !out.contains(c)) {
                        q.add(c);
                    }
                }
                if (q.isEmpty()) {
                    break;
                }
            }
            out.add(q.remove(0));
        }
        return out;
    }

    /** Next crate on the next rotating pedestal. */
    void swap() {
        List<Location> spots = spots();
        int rotatingSlots = spots.size() - fixed.size();
        if (rotatingSlots <= 0) {
            return;
        }
        List<String> shown = shownAll();
        queue.removeIf(shown::contains);
        if (queue.isEmpty()) {
            for (String c : rotating()) {
                if (!shown.contains(c)) {
                    queue.add(c);
                }
            }
            Collections.shuffle(queue);
        }
        if (queue.isEmpty()) {
            return; // every crate is already on display
        }
        String next = queue.remove(0);
        nextSlot = nextSlot % rotatingSlots;
        Location spot = spots.get(fixed.size() + nextSlot);
        nextSlot++;
        show(spot, next, true);
        CrateType t = plugin.crates().type(next);
        if (t != null) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (plugin.attractionStore().inside(p.getLocation(), 0)) {
                    plugin.messages().actionBar(p, "crates.carousel.new", "crate", Text.parse(t.name()));
                }
            }
        }
    }

    /** Position of rotating pedestal {@code slot} in the swap order (0 = next). */
    private int order(int slot, int rotatingSlots) {
        int n = Math.max(1, rotatingSlots);
        return Math.floorMod(slot - nextSlot % n, n);
    }

    private long secondsUntilSlot(int slot, int rotatingSlots) {
        return secondsToSwap() + (long) order(slot, rotatingSlots) * swapSeconds;
    }

    private static String clock(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ---- countdown signs above the rotating pedestals ------------------------------------------------------

    private final List<org.bukkit.entity.TextDisplay> signs = new ArrayList<>();

    private void updateSigns() {
        List<Location> spots = spots();
        int rotatingSlots = spots.size() - fixed.size();
        signs.removeIf(d -> !d.isValid());
        if (rotatingSlots <= 0) {
            return;
        }
        List<String> next = projected(rotatingSlots);
        for (int i = 0; i < rotatingSlots; i++) {
            Location spot = spots.get(fixed.size() + i).clone().add(0.5, 3.4, 0.5);
            if (!spot.isChunkLoaded()) {
                continue;
            }
            // look in the WORLD (not only in our list): keep exactly one sign per spot, remove every extra copy
            org.bukkit.entity.TextDisplay sign = null;
            for (org.bukkit.entity.Entity e : spot.getWorld().getNearbyEntities(spot, 0.8, 0.8, 0.8)) {
                if (e instanceof org.bukkit.entity.TextDisplay d && "carousel_sign".equals(d.getPersistentDataContainer()
                        .get(online.mythoscraft.core.util.Keys.ENTITY, org.bukkit.persistence.PersistentDataType.STRING))) {
                    if (sign == null) {
                        sign = d;
                        if (!signs.contains(d)) {
                            signs.add(d);
                        }
                    } else {
                        signs.remove(d);
                        d.remove();
                    }
                }
            }
            if (sign == null) {
                sign = spot.getWorld().spawn(spot, org.bukkit.entity.TextDisplay.class, d -> {
                    d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    d.setPersistent(false);
                    d.setBackgroundColor(org.bukkit.Color.fromARGB(110, 20, 10, 30));
                    d.getPersistentDataContainer().set(online.mythoscraft.core.util.Keys.ENTITY,
                            org.bukkit.persistence.PersistentDataType.STRING, "carousel_sign");
                });
                signs.add(sign);
            }
            int o = order(i, rotatingSlots);
            String upcoming = o < next.size() ? next.get(o) : null;
            CrateType t = upcoming == null ? null : plugin.crates().type(upcoming);
            sign.text(plugin.messages().get("crates.carousel.sign", "time", clock(secondsUntilSlot(i, rotatingSlots)),
                    "next", t == null ? net.kyori.adventure.text.Component.text("-") : Text.parse(t.name())));
        }
    }

    public void removeSigns() {
        signs.forEach(org.bukkit.entity.Entity::remove);
        signs.clear();
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.TextDisplay d : w.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)) {
                if ("carousel_sign".equals(d.getPersistentDataContainer().get(online.mythoscraft.core.util.Keys.ENTITY,
                        org.bukkit.persistence.PersistentDataType.STRING))) {
                    d.remove();
                }
            }
        }
    }

    public long secondsToSwap() {
        return Math.max(0, (nextSwap - System.currentTimeMillis()) / 1000);
    }

    /** Rough minutes until a crate is back on the spawn, -1 if never (event crate outside its event). */
    public long minutesUntil(String crate) {
        crate = crate.toLowerCase(Locale.ROOT);
        if (eventCrates.containsKey(crate)) {
            return eventActive(crate) ? 0 : -1;
        }
        for (Location s : spots()) {
            if (crate.equals(shownAt(s))) {
                return 0;
            }
        }
        if (never.contains(crate)) {
            return -1;
        }
        int pos = projected(rotating().size()).indexOf(crate);
        if (pos < 0) {
            return -1;
        }
        return Math.max(1, (secondsToSwap() + (long) pos * swapSeconds + 59) / 60);
    }

    /** Hint when a player holds a key of a crate that is not on the spawn right now. */
    public void hint(Player player, String crate) {
        if (!enabled() || crate == null) {
            return;
        }
        long m = minutesUntil(crate);
        CrateType t = plugin.crates().type(crate);
        if (t == null || m == 0) {
            return;
        }
        if (m < 0) {
            plugin.messages().send(player, "crates.carousel.event-only", "crate", Text.parse(t.name()));
        } else {
            plugin.messages().send(player, "crates.carousel.comes-back", "crate", Text.parse(t.name()), "minutes", m);
        }
    }

    /** /kisten – every pedestal: which crate stands there, which one replaces it and when. */
    public void info(CommandSender sender) {
        List<Location> spots = spots();
        int rotatingSlots = Math.max(0, spots.size() - fixed.size());
        plugin.messages().send(sender, "crates.carousel.header", "minutes", Math.max(1, swapSeconds / 60));
        List<String> next = projected(rotatingSlots);
        for (int i = 0; i < spots.size(); i++) {
            String now = shownAt(spots.get(i));
            CrateType cur = now == null ? null : plugin.crates().type(now);
            net.kyori.adventure.text.Component curName = cur == null ? plugin.messages().get("crates.carousel.empty") : Text.parse(cur.name());
            if (i < fixed.size()) {
                plugin.messages().sendRaw(sender, "crates.carousel.fixed-line", "crate", curName);
                continue;
            }
            int slot = i - fixed.size();
            int o = order(slot, rotatingSlots);
            String up = o < next.size() ? next.get(o) : null;
            CrateType upT = up == null ? null : plugin.crates().type(up);
            plugin.messages().sendRaw(sender, "crates.carousel.slot-line", "crate", curName,
                    "next", upT == null ? net.kyori.adventure.text.Component.text("-") : Text.parse(upT.name()),
                    "time", clock(secondsUntilSlot(slot, rotatingSlots)));
        }
        String ev = currentEventCrate();
        plugin.messages().sendRaw(sender, "crates.carousel.event-line", "event", ev == null ? plugin.messages().get("crates.carousel.no-event")
                : Text.parse(plugin.crates().type(ev).name()));
        List<String> later = projected(rotating().size());
        if (later.size() > rotatingSlots) {
            List<String> names = new ArrayList<>();
            for (String c : later.subList(rotatingSlots, later.size())) {
                CrateType t = plugin.crates().type(c);
                if (t != null) {
                    names.add(t.name());
                }
            }
            plugin.messages().sendRaw(sender, "crates.carousel.later", "crates", Text.parse(String.join("<gray>, ", names)));
        }
    }
}
