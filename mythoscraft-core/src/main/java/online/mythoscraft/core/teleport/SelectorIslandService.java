package online.mythoscraft.core.teleport;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.util.LocationCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A separate, protected first-join selector. It does not modify the SMP spawn structure or its /spawn build routine.
 * /island place confirm builds the enclosed platform wherever the admin is standing.
 */
public final class SelectorIslandService extends BaseCommand implements Listener {
    private static final int SIZE = 25;
    private static final int MID = SIZE / 2;
    private static final NamespacedKey NPC_KEY = new NamespacedKey("mythoscraft", "world-selector-npc");
    private static final NamespacedKey SELECTOR_PENDING = new NamespacedKey("mythoscraft", "selector-pending");
    private static final NamespacedKey NEXT_WORLD = new NamespacedKey("mythoscraft", "intro-next-world");

    private final Map<UUID, Long> pending = new HashMap<>();
    private final Map<String, PlayerProfile> profiles = new HashMap<>();
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    public SelectorIslandService(MythosCraft plugin) {
        super(plugin);
        restoreNpcs();
    }

    public Location location() {
        if (!plugin.getConfig().getBoolean("selector-island.enabled", true)) {
            return null;
        }
        return LocationCodec.read(plugin.getConfig().getConfigurationSection("selector-island.location"));
    }

    public boolean isConfigured() {
        return location() != null;
    }

    /** First-join destination. Returning players keep the existing SMP join behaviour. */
    public boolean handleJoin(Player player, boolean firstJoin) {
        Location at = location();
        if (!firstJoin || at == null || !plugin.getConfig().getBoolean("spawn.teleport-on-first-join", true)) {
            return false;
        }
        player.getPersistentDataContainer().set(SELECTOR_PENDING, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().remove(NEXT_WORLD);
        player.teleportAsync(at);
        return true;
    }

    public static boolean waitingForChoice(Player player) {
        var pdc = player.getPersistentDataContainer();
        Byte pending = pdc.get(SELECTOR_PENDING, PersistentDataType.BYTE);
        String next = pdc.get(NEXT_WORLD, PersistentDataType.STRING);
        return (pending != null && pending != 0) || (next != null && !next.isBlank());
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player player = player(sender);
        permission(sender, "mythos.spawn.set");
        if (args.length == 0 || !args[0].equalsIgnoreCase("place")) {
            throw CommandFail.usage("/island place [confirm]");
        }

        boolean confirm = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        Long until = pending.get(player.getUniqueId());
        boolean confirmationValid = until != null && until >= System.currentTimeMillis();
        if (confirm && (confirmationValid || !isConfigured())) {
            pending.remove(player.getUniqueId());
            build(player);
            return;
        }
        pending.put(player.getUniqueId(), System.currentTimeMillis() + 60_000L);
        player.sendMessage(Component.text("Selector-Insel bauen? Der bestehende SMP-Spawn bleibt unverändert. ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Bestätige mit /island place confirm", NamedTextColor.YELLOW)));
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return List.of("place");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return List.of("confirm");
        }
        return List.of();
    }

    private void build(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            player.sendMessage(Component.text("Die Welt konnte nicht ermittelt werden.", NamedTextColor.RED));
            return;
        }

        // Build a compact opaque-walled platform. The floor is solid, the high walls hide the drop and prevent falling.
        Location origin = at.getBlock().getLocation().add(0, -1, 0);
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int wx = ox + x;
                int wz = oz + z;
                Material floor = Material.POLISHED_BLACKSTONE_BRICKS;
                if (x == 1 || x == SIZE - 2 || z == 1 || z == SIZE - 2) {
                    floor = Material.QUARTZ_BLOCK;
                }
                if ((x == MID || z == MID) && x > 2 && x < SIZE - 3 && z > 2 && z < SIZE - 3) {
                    floor = Material.PURPUR_BLOCK;
                }
                if ((x == MID - 5 || x == MID + 5) && Math.abs(z - MID) <= 2) {
                    floor = Material.AMETHYST_BLOCK;
                }
                world.getBlockAt(wx, oy, wz).setType(floor, false);

                // Clear only the build volume above the floor, so the spawn and NPCs cannot be trapped in trees.
                for (int y = oy + 1; y <= oy + 4; y++) {
                    if (world.getBlockAt(wx, y, wz).getType() != Material.AIR) {
                        world.getBlockAt(wx, y, wz).setType(Material.AIR, false);
                    }
                }

                boolean edge = x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1;
                if (edge) {
                    for (int h = 1; h <= 3; h++) {
                        world.getBlockAt(wx, oy + h, wz).setType(
                                h == 3 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS, false);
                    }
                }
                if ((x == 1 || x == SIZE - 2) && (z == 1 || z == SIZE - 2)) {
                    world.getBlockAt(wx, oy + 1, wz).setType(Material.AMETHYST_BLOCK, false);
                    world.getBlockAt(wx, oy + 2, wz).setType(Material.QUARTZ_PILLAR, false);
                }
            }
        }

        // Small mythic details and lighting, entirely inside the island footprint.
        for (int[] pos : new int[][]{{2,2},{SIZE-3,2},{2,SIZE-3},{SIZE-3,SIZE-3}}) {
            world.getBlockAt(ox + pos[0], oy, oz + pos[1]).setType(Material.SEA_LANTERN, false);
        }
        world.getBlockAt(ox + MID, oy, oz + MID).setType(Material.AMETHYST_BLOCK, false);

        Location spawn = new Location(world, ox + MID + 0.5, oy + 1, oz + MID + 0.5, 180, 0);
        var section = plugin.getConfig().getConfigurationSection("selector-island.location");
        if (section == null) {
            section = plugin.getConfig().createSection("selector-island.location");
        }
        LocationCodec.write(section, spawn);
        plugin.getConfig().set("selector-island.enabled", true);
        plugin.saveConfig();
        restoreNpcs();
        player.teleport(spawn);
        player.sendMessage(Component.text("Selector-Insel gebaut. Der SMP-Spawn wurde nicht verändert.", NamedTextColor.GREEN));
    }

    private void restoreNpcs() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getPersistentDataContainer().has(NPC_KEY, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        Location center = location();
        if (center == null || center.getWorld() == null) {
            return;
        }
        Location smp = center.clone().add(-5, 0, 0);
        smp.setDirection(center.toVector().subtract(smp.toVector()).setY(0));
        Location citybuild = center.clone().add(5, 0, 0);
        citybuild.setDirection(center.toVector().subtract(citybuild.toVector()).setY(0));
        spawnNpc(smp, "smp", "MinePro641");
        spawnNpc(citybuild, "plots", "WestDesert68101");
    }

    private void spawnNpc(Location at, String role, String skinName) {
        if (at.getWorld() == null) {
            return;
        }
        at.getChunk().load();
        Mannequin mannequin = at.getWorld().spawn(at, Mannequin.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setGravity(false);
            entity.setAI(false);
            entity.setImmovable(true);
            entity.setCollidable(false);
            entity.setRotation(at.getYaw(), 0);
            entity.customName(role.equals("smp")
                    ? Component.text("✦ MYTHOSCRAFT • SMP ✦", NamedTextColor.GOLD)
                    : Component.text("✦ CITYBUILD • PLOTS ✦", NamedTextColor.LIGHT_PURPLE));
            entity.setCustomNameVisible(true);
            entity.setDescription(role.equals("smp")
                    ? Component.text("Klicke, um auf dem Survival-SMP zu spielen", NamedTextColor.GRAY)
                    : Component.text("Klicke, um in den Citybuild zu wechseln", NamedTextColor.GRAY));
            entity.setPose(Pose.STANDING);
            entity.getEquipment().setItem(EquipmentSlot.HAND,
                    new ItemStack(role.equals("smp") ? Material.NETHERITE_SWORD : Material.GRASS_BLOCK));
            entity.getEquipment().setItem(EquipmentSlot.CHEST,
                    new ItemStack(role.equals("smp") ? Material.NETHERITE_CHESTPLATE : Material.IRON_CHESTPLATE));
            entity.getPersistentDataContainer().set(NPC_KEY, PersistentDataType.STRING, role);
        });
        skin(mannequin, skinName);
    }

    private void skin(Mannequin mannequin, String skinName) {
        PlayerProfile known = profiles.get(skinName);
        if (known != null) {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(known));
            return;
        }
        PlayerProfile profile = Bukkit.createProfile(skinName);
        plugin.tasks().io("selector NPC skin " + skinName, () -> {
            boolean success = profile.complete(true);
            plugin.tasks().sync(() -> {
                if (success) {
                    profiles.put(skinName, profile);
                }
                if (mannequin.isValid()) {
                    mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcClick(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        String role = entity.getPersistentDataContainer().get(NPC_KEY, PersistentDataType.STRING);
        if (role == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = clickCooldown.get(player.getUniqueId());
        if (last != null && now - last < 1_200L) {
            return;
        }
        clickCooldown.put(player.getUniqueId(), now);
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0, 2, 0), 8, .25, .25, .25, 0);
        player.getPersistentDataContainer().remove(SELECTOR_PENDING);
        if (role.equals("smp")) {
            player.getPersistentDataContainer().remove(NEXT_WORLD);
            plugin.intro().selectedPlots(player); // cancel the first-join auto trigger until the SMP teleport completes
            Location spawn = plugin.spawn().spawn();
            if (spawn == null) {
                player.sendMessage(Component.text("Der SMP-Spawn ist noch nicht gesetzt.", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Willkommen auf dem MythosCraft SMP!", NamedTextColor.GOLD));
            player.teleportAsync(spawn).thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            plugin.intro().selectedSmp(player);
                        }
                    });
                }
            });
        } else if (role.equals("plots")) {
            player.getPersistentDataContainer().set(NEXT_WORLD, PersistentDataType.STRING, "plots");
            plugin.intro().selectedPlots(player);
            player.sendMessage(Component.text("Citybuild wird betreten …", NamedTextColor.LIGHT_PURPLE));
            player.performCommand("farmwelt");
        }
    }

    private boolean inside(Location location) {
        Location center = location();
        return center != null && center.getWorld() != null && location.getWorld() != null
                && center.getWorld().equals(location.getWorld())
                && Math.abs(location.getBlockX() - center.getBlockX()) <= MID
                && Math.abs(location.getBlockZ() - center.getBlockZ()) <= MID;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inside(event.getBlock().getLocation()) && !event.getPlayer().hasPermission("mythos.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inside(event.getBlock().getLocation()) && !event.getPlayer().hasPermission("mythos.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> inside(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> inside(block.getLocation()));
    }

    public void shutdown() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getPersistentDataContainer().has(NPC_KEY, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }
}
