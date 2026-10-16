package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.core.Perms;
import online.mythoscraft.core.util.Keys;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Game mode for creators the owner picks: /creatormode add|remove|list &lt;player&gt; (admin), the creators switch
 * with /gm &lt;c|s|sp&gt;. Creative has its own inventory (the survival one is stored and comes back), nothing can be
 * dropped, no containers opened, no players hurt – so no creative item ever reaches the normal game. Building and
 * breaking at the spawn stay forbidden.
 */
public final class CreatorModeService extends BaseCommand implements Listener {

    private static final NamespacedKey SAVED = new NamespacedKey(Keys.NAMESPACE, "creator_survival_inv");
    private final Set<UUID> allowed = new LinkedHashSet<>();
    private final File file;

    public CreatorModeService(MythosCraft plugin) {
        super(plugin);
        file = new File(plugin.getDataFolder(), "data/creator-mode.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("allowed")) {
            try {
                allowed.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // broken entry
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed", allowed.stream().map(UUID::toString).toList());
        try {
            file.getParentFile().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("creator-mode.yml: " + e.getMessage());
        }
    }

    public boolean allowed(Player p) {
        return allowed.contains(p.getUniqueId());
    }

    /** a creator in creative mode (admins are not restricted) */
    private boolean restricted(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && allowed(p) && !p.hasPermission(Perms.ADMIN);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        if (label.equals("creatormode")) {
            permission(sender, Perms.ADMIN);
            String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
            if (sub.equals("list")) {
                List<String> names = new ArrayList<>();
                for (UUID id : allowed) {
                    OfflinePlayer o = plugin.getServer().getOfflinePlayer(id);
                    names.add(o.getName() == null ? id.toString() : o.getName());
                }
                plugin.messages().send(sender, "creator-mode.list", "players", names.isEmpty() ? "-" : String.join(", ", names));
                return;
            }
            if (args.length < 2 || !(sub.equals("add") || sub.equals("remove"))) {
                throw CommandFail.usage("/creatormode <add|remove|list> [Spieler]");
            }
            OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
            if (sub.equals("add")) {
                allowed.add(target.getUniqueId());
                plugin.messages().send(sender, "creator-mode.added", "player", args[1]);
            } else {
                allowed.remove(target.getUniqueId());
                Player online = target.getPlayer();
                if (online != null && online.getGameMode() != GameMode.SURVIVAL) {
                    switchTo(online, GameMode.SURVIVAL);
                }
                plugin.messages().send(sender, "creator-mode.removed", "player", args[1]);
            }
            save();
            return;
        }
        // /gm <c|s|sp|a>
        Player p = player(sender);
        if (!allowed(p) && !p.hasPermission(Perms.ADMIN)) {
            throw new CommandFail("creator-mode.not-allowed");
        }
        if (args.length < 1) {
            throw CommandFail.usage("/gm <c|s|sp>");
        }
        GameMode mode = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "c", "1", "creative", "kreativ" -> GameMode.CREATIVE;
            case "s", "0", "survival", "überleben" -> GameMode.SURVIVAL;
            case "sp", "3", "spectator", "zuschauer" -> GameMode.SPECTATOR;
            case "a", "2", "adventure" -> GameMode.ADVENTURE;
            default -> throw CommandFail.usage("/gm <c|s|sp>");
        };
        if (mode == GameMode.ADVENTURE && !p.hasPermission(Perms.ADMIN)) {
            mode = GameMode.SURVIVAL;
        }
        if (plugin.arenas().isInArena(p) || plugin.tournaments().isParticipant(p)) {
            throw new CommandFail("creator-mode.blocked-here");
        }
        switchTo(p, mode);
        plugin.messages().send(p, "creator-mode.switched", "mode", mode.name().toLowerCase(Locale.ROOT));
    }

    /** Survival inventory is stored while in creative/spectator and restored when going back. */
    private void switchTo(Player p, GameMode mode) {
        boolean wasSurvival = p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        boolean toSurvival = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
        if (wasSurvival && !toSurvival && !p.hasPermission(Perms.ADMIN)) {
            ItemStack[] contents = p.getInventory().getContents();
            p.getPersistentDataContainer().set(SAVED, PersistentDataType.BYTE_ARRAY,
                    ItemStack.serializeItemsAsBytes(Arrays.asList(contents)));
            p.getInventory().clear();
        }
        p.setGameMode(mode);
        if (toSurvival) {
            restore(p);
        }
    }

    private void restore(Player p) {
        byte[] raw = p.getPersistentDataContainer().get(SAVED, PersistentDataType.BYTE_ARRAY);
        if (raw == null) {
            return;
        }
        p.getInventory().clear();          // creative items never stay
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(raw);
        p.getInventory().setContents(Arrays.copyOf(items, p.getInventory().getSize()));
        p.getPersistentDataContainer().remove(SAVED);
    }

    // ------------------------------------------------------------------ safety
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (allowed(p) && !p.hasPermission(Perms.ADMIN) && p.getGameMode() != GameMode.SURVIVAL) {
            switchTo(p, GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // safety net (crash while in creative): survival inventory back, creative gone
        Player p = event.getPlayer();
        if (p.getPersistentDataContainer().has(SAVED, PersistentDataType.BYTE_ARRAY)) {
            p.setGameMode(GameMode.SURVIVAL);
            restore(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (restricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && restricted(p)
                && event.getInventory().getType() != InventoryType.CRAFTING && event.getInventory().getType() != InventoryType.CREATIVE
                && event.getInventory().getType() != InventoryType.PLAYER && event.getInventory().getType() != InventoryType.WORKBENCH) {
            event.setCancelled(true);
            plugin.messages().actionBar(p, "creator-mode.no-containers");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && restricted(p) && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    private boolean atSpawn(org.bukkit.block.Block b) {
        return plugin.attractionStore().inside(b.getLocation(), 2);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (allowed(p) && !p.hasPermission(Perms.ADMIN) && p.getGameMode() == GameMode.CREATIVE && atSpawn(event.getBlock())) {
            event.setCancelled(true);
            plugin.messages().actionBar(p, "creator-mode.no-spawn");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (allowed(p) && !p.hasPermission(Perms.ADMIN) && p.getGameMode() == GameMode.CREATIVE) {
            if (atSpawn(event.getBlock())) {
                event.setCancelled(true);
                plugin.messages().actionBar(p, "creator-mode.no-spawn");
                return;
            }
            mark(event.getBlock(), true);
        }
    }

    // blocks placed in creative never drop anything when they are broken later (no creative items into the economy)
    private static final NamespacedKey CREATIVE_BLOCKS = new NamespacedKey(Keys.NAMESPACE, "creative_blocks");

    private static long pack(org.bukkit.block.Block b) {
        return ((long) (b.getX() & 15) << 40) | ((long) (b.getZ() & 15) << 32) | (b.getY() & 0xffffffffL);
    }

    private void mark(org.bukkit.block.Block b, boolean on) {
        var pdc = b.getChunk().getPersistentDataContainer();
        long[] cur = pdc.getOrDefault(CREATIVE_BLOCKS, PersistentDataType.LONG_ARRAY, new long[0]);
        long key = pack(b);
        java.util.LinkedHashSet<Long> set = new java.util.LinkedHashSet<>();
        for (long v : cur) {
            set.add(v);
        }
        if (on ? !set.add(key) : !set.remove(key)) {
            return;
        }
        pdc.set(CREATIVE_BLOCKS, PersistentDataType.LONG_ARRAY, set.stream().mapToLong(Long::longValue).toArray());
    }

    private boolean marked(org.bukkit.block.Block b) {
        long[] cur = b.getChunk().getPersistentDataContainer().get(CREATIVE_BLOCKS, PersistentDataType.LONG_ARRAY);
        if (cur == null) {
            return false;
        }
        long key = pack(b);
        for (long v : cur) {
            if (v == key) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakCreativeBlock(BlockBreakEvent event) {
        if (marked(event.getBlock())) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            mark(event.getBlock(), false);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (label.equals("creatormode")) {
            if (!sender.hasPermission(Perms.ADMIN)) {
                return List.of();
            }
            return args.length == 1 ? List.of("add", "remove", "list") : args.length == 2 ? onlineNames(sender) : List.of();
        }
        return args.length == 1 ? List.of("c", "s", "sp") : List.of();
    }
}
