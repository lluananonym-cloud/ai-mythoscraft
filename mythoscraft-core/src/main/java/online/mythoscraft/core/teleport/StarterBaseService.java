package online.mythoscraft.core.teleport;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The first own place: after the first /rtp into the overworld the player gets a small protected base around the
 * landing spot (default 21×21, all heights) with a starter chest and a home "base". Only the owner and trusted players
 * may build, break or open containers there; explosions don't destroy it. /base – back there, /base here – move it
 * (cooldown), /base trust|untrust &lt;player&gt;, /base info, /base show.
 */
public final class StarterBaseService extends BaseCommand implements Listener {

    static final class Base {
        final UUID owner;
        String ownerName;
        String world;
        int x;
        int y;
        int z;
        final Set<UUID> trusted = new HashSet<>();
        long movedAt;

        Base(UUID owner, String ownerName, Location at) {
            this.owner = owner;
            this.ownerName = ownerName;
            set(at);
        }

        void set(Location at) {
            world = at.getWorld().getName();
            x = at.getBlockX();
            y = at.getBlockY();
            z = at.getBlockZ();
        }

        boolean contains(Location l, int radius) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && Math.abs(l.getBlockX() - x) <= radius && Math.abs(l.getBlockZ() - z) <= radius;
        }
    }

    private final YamlStore store;
    private final Map<UUID, Base> bases = new LinkedHashMap<>();
    private final Map<UUID, UUID> inside = new HashMap<>();
    private boolean enabled = true;
    private int radius = 10;
    private long moveCooldownHours = 24;
    private final List<ItemStack> chestItems = new ArrayList<>();

    public StarterBaseService(MythosCraft plugin) {
        super(plugin);
        store = new YamlStore(plugin, "data/bases.yml");
        load();
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("starter-base");
        enabled = cfg.bool("enabled", true);
        radius = cfg.integer("radius", 10, 3, 64);
        moveCooldownHours = cfg.integer("move-cooldown-hours", 24, 0, 24 * 30);
        chestItems.clear();
        for (String line : cfg.strings("chest")) {
            String[] parts = line.trim().split("\\s+");
            Material m = Material.matchMaterial(parts[0]);
            if (m == null || !m.isItem()) {
                cfg.warn("chest", "unknown item " + parts[0]);
                continue;
            }
            int n = 1;
            try {
                n = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            } catch (NumberFormatException ignored) {
                cfg.warn("chest", "bad amount " + line);
            }
            chestItems.add(new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), n))));
        }
    }

    // ------------------------------------------------------------------------------------------- lookup
    private Base at(Location l) {
        for (Base b : bases.values()) {
            if (b.contains(l, radius)) {
                return b;
            }
        }
        return null;
    }

    private boolean overlaps(Location l, UUID except) {
        for (Base b : bases.values()) {
            if (!b.owner.equals(except) && b.world.equals(l.getWorld().getName())
                    && Math.abs(l.getBlockX() - b.x) <= radius * 2 + 1 && Math.abs(l.getBlockZ() - b.z) <= radius * 2 + 1) {
                return true;
            }
        }
        return !plugin.regions().at(l).isEmpty() || plugin.attractionStore().inside(l, radius + 16);
    }

    private boolean mayEdit(Player p, Location l) {
        Base b = at(l);
        return b == null || b.owner.equals(p.getUniqueId()) || b.trusted.contains(p.getUniqueId()) || p.hasPermission("mythos.admin");
    }

    // ------------------------------------------------------------------------------------------- first rtp
    /** Called after every RTP: the first one into the overworld creates the base. */
    public void afterRtp(Player p, Location spot) {
        if (!enabled || bases.containsKey(p.getUniqueId()) || spot.getWorld() == null
                || spot.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        if (overlaps(spot, p.getUniqueId())) {
            return;                                             // next RTP tries again
        }
        Base b = new Base(p.getUniqueId(), p.getName(), spot);
        bases.put(b.owner, b);
        save();
        plugin.tasks().later(40, "starter base", () -> {
            if (!p.isOnline()) {
                return;
            }
            placeChest(p, spot);
            setHome(p);
            plugin.messages().sendRaw(p, "base.created", "size", radius * 2 + 1);
            SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.1f).play(p);
            show(p, b, 200);
        });
    }

    private void placeChest(Player p, Location spot) {
        if (chestItems.isEmpty()) {
            return;
        }
        Location front = spot.clone().add(spot.getDirection().setY(0).normalize().multiply(2));
        Block block = front.getBlock();
        for (int i = 0; i < 4 && !block.getType().isAir(); i++) {
            block = block.getRelative(BlockFace.UP);
        }
        if (!block.getType().isAir() || !block.getRelative(BlockFace.DOWN).getType().isSolid()) {
            chestItems.forEach(it -> online.mythoscraft.core.item.CustomItemManager.give(p, it.clone()));
            return;
        }
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            chest.customName(plugin.messages().get("base.chest-name"));
            chest.update();
            if (block.getState() instanceof Chest filled) {
                for (ItemStack it : chestItems) {
                    filled.getBlockInventory().addItem(it.clone());
                }
            }
        }
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1, 0.5), 20, 0.4, 0.3, 0.4, 0);
    }

    private void setHome(Player p) {
        var data = plugin.playerData().get(p);
        if (data != null && data.home("base") == null) {
            plugin.homes().setHome(p, data, "base");
        }
    }

    /** Particle border of a base for some ticks (only the viewer sees it). */
    private void show(Player p, Base b, int ticks) {
        World w = Bukkit.getWorld(b.world);
        if (w == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x5e, 0xf0, 0xa0), 1.3f);
        int[] t = {0};
        org.bukkit.scheduler.BukkitTask[] task = {null};
        task[0] = plugin.tasks().repeat(new Object(), 1, 10, "base border", () -> {
            t[0] += 10;
            if (!p.isOnline() || t[0] > ticks) {
                task[0].cancel();
                return;
            }
            double y = p.getLocation().getY() + 0.3;
            for (int i = -radius; i <= radius + 1; i += 1) {
                for (double[] pt : new double[][]{{b.x - radius, b.z + i}, {b.x + radius + 1, b.z + i}, {b.x + i, b.z - radius}, {b.x + i, b.z + radius + 1}}) {
                    p.spawnParticle(Particle.DUST, new Location(w, pt[0], y, pt[1]), 1, 0, 0.3, 0, 0, dust);
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------------- protection
    private void deny(Player p) {
        plugin.messages().actionBar(p, "base.protected");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!mayEdit(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!mayEdit(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!mayEdit(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!mayEdit(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        boolean guarded = b.getState() instanceof org.bukkit.inventory.InventoryHolder
                || org.bukkit.Tag.DOORS.isTagged(b.getType()) || org.bukkit.Tag.TRAPDOORS.isTagged(b.getType())
                || org.bukkit.Tag.FENCE_GATES.isTagged(b.getType()) || b.getType() == Material.LEVER
                || org.bukkit.Tag.BUTTONS.isTagged(b.getType());
        if (guarded && !mayEdit(e.getPlayer(), b.getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> at(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> at(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        Player p = e.getPlayer();
        Base b = at(e.getTo());
        UUID now = b == null ? null : b.owner;
        UUID before = inside.get(p.getUniqueId());
        if (java.util.Objects.equals(now, before)) {
            return;
        }
        if (now == null) {
            inside.remove(p.getUniqueId());
        } else {
            inside.put(p.getUniqueId(), now);
            plugin.messages().actionBar(p, b.owner.equals(p.getUniqueId()) ? "base.enter-own" : "base.enter", "player", b.ownerName);
        }
    }

    // ------------------------------------------------------------------------------------------- /base
    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        Base b = bases.get(p.getUniqueId());
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (b == null && !sub.equals("here")) {
            plugin.messages().send(p, "base.none");
            return;
        }
        switch (sub) {
            case "" -> {
                World w = Bukkit.getWorld(b.world);
                if (w == null) {
                    plugin.messages().send(p, "base.none");
                    return;
                }
                Location to = new Location(w, b.x + 0.5, b.y, b.z + 0.5);
                plugin.teleports().teleport(p, to, 3, true, plugin.messages().get("base.name"), null);
            }
            case "here" -> {
                Location l = p.getLocation();
                if (l.getWorld().getEnvironment() != World.Environment.NORMAL) {
                    plugin.messages().send(p, "base.overworld-only");
                    return;
                }
                long wait = b == null ? 0 : b.movedAt + moveCooldownHours * 3600_000L - System.currentTimeMillis();
                if (wait > 0 && !p.hasPermission("mythos.admin")) {
                    plugin.messages().send(p, "base.move-cooldown", "time", online.mythoscraft.core.util.Durations.format(java.time.Duration.ofMillis(wait)));
                    return;
                }
                if (overlaps(l, p.getUniqueId())) {
                    plugin.messages().send(p, "base.overlap");
                    return;
                }
                if (b == null) {
                    b = new Base(p.getUniqueId(), p.getName(), l);
                    bases.put(b.owner, b);
                } else {
                    b.set(l);
                }
                b.movedAt = System.currentTimeMillis();
                save();
                plugin.messages().send(p, "base.moved", "size", radius * 2 + 1);
                show(p, b, 160);
            }
            case "trust", "untrust" -> {
                if (args.length < 2) {
                    throw CommandFail.usage("/base " + sub + " <player>");
                }
                OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (o == null) {
                    plugin.messages().send(p, "general.player-not-found", "player", args[1]);
                    return;
                }
                if (sub.equals("trust")) {
                    b.trusted.add(o.getUniqueId());
                } else {
                    b.trusted.remove(o.getUniqueId());
                }
                save();
                plugin.messages().send(p, sub.equals("trust") ? "base.trusted" : "base.untrusted", "player", args[1]);
            }
            case "info" -> {
                List<String> names = new ArrayList<>();
                b.trusted.forEach(u -> names.add(String.valueOf(Bukkit.getOfflinePlayer(u).getName())));
                plugin.messages().send(p, "base.info", "x", b.x, "z", b.z, "world", b.world, "size", radius * 2 + 1,
                        "trusted", names.isEmpty() ? "-" : String.join(", ", names));
            }
            case "show" -> show(p, b, 200);
            default -> throw CommandFail.usage("/base [here|trust|untrust|info|show]");
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return List.of("here", "trust", "untrust", "info", "show");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            List<String> out = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            return out;
        }
        return List.of();
    }

    // ------------------------------------------------------------------------------------------- storage
    private void load() {
        var y = store.load(false);
        ConfigurationSection root = y.getConfigurationSection("bases");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(key);
            try {
                UUID owner = UUID.fromString(key);
                World w = Bukkit.getWorld(c.getString("world", "world"));
                Location at = new Location(w, c.getInt("x"), c.getInt("y"), c.getInt("z"));
                Base b = new Base(owner, c.getString("name", "?"), at.getWorld() == null ? new Location(Bukkit.getWorlds().get(0), 0, 0, 0) : at);
                b.world = c.getString("world", b.world);
                b.movedAt = c.getLong("moved", 0);
                for (String t : c.getStringList("trusted")) {
                    b.trusted.add(UUID.fromString(t));
                }
                bases.put(owner, b);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("bases.yml: " + key + " skipped – " + e.getMessage());
            }
        }
    }

    private void save() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        for (Base b : bases.values()) {
            String p = "bases." + b.owner;
            y.set(p + ".name", b.ownerName);
            y.set(p + ".world", b.world);
            y.set(p + ".x", b.x);
            y.set(p + ".y", b.y);
            y.set(p + ".z", b.z);
            y.set(p + ".moved", b.movedAt);
            List<String> t = new ArrayList<>();
            b.trusted.forEach(u -> t.add(u.toString()));
            y.set(p + ".trusted", t);
        }
        store.replace(y);
        store.saveAsync(plugin.tasks());
    }

    public void shutdown() {
        store.saveNow();
    }
}
