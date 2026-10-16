package online.mythoscraft.core.cosmetic;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.util.Keys;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Little companions (snowman, penguin, ghost, dragon, bee) that sit on the player's right shoulder. Right-click the
 * companion item to call it, again to send it home. The choice is stored on the player and survives relogs.
 * The companion is an item display riding the player (so the client moves it without lag); it is taken off before
 * teleports to another world (see {@link #unmount}) and climbs back on the next tick.
 */
public final class CompanionService implements Listener {

    private static final NamespacedKey KEY = new NamespacedKey(Keys.NAMESPACE, "companion");
    private static final float SCALE = 0.42f;

    private final MythosCraft plugin;
    private final Map<UUID, ItemDisplay> shown = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();

    public CompanionService(MythosCraft plugin) {
        this.plugin = plugin;
        plugin.tasks().repeat(this, 20, 1, "companions", this::tick);
    }

    public static boolean isCompanion(CustomItem type) {
        return type != null && type.id().startsWith("companion_");
    }

    public String active(Player p) {
        return p.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    public void set(Player p, String id) {
        if (id == null) {
            p.getPersistentDataContainer().remove(KEY);
        } else {
            p.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        }
        despawn(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        CustomItem type = plugin.items().identify(event.getItem());
        if (!isCompanion(type)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().isSneaking()) {
            return;     // sneak + right-click on a block places it as a farming companion (CompanionFarmService)
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (type.id().equals(active(p))) {
            set(p, null);
            plugin.messages().actionBar(p, "companion.off");
        } else {
            set(p, type.id());
            plugin.messages().actionBar(p, "companion.on");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        despawn(event.getPlayer().getUniqueId());
    }

    private void despawn(UUID id) {
        ItemDisplay d = shown.remove(id);
        if (d != null) {
            lastYaw.remove(d.getUniqueId());
            if (d.isValid()) {
                d.remove();
            }
        }
    }

    private boolean hidden(Player p) {
        return p.isDead() || p.hasMetadata("vanish") || p.getGameMode() == GameMode.SPECTATOR || p.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    /** Right shoulder of the player, facing where the body faces. */
    public static Location shoulder(Location feet, float bodyYaw, boolean sneaking) {
        double yaw = Math.toRadians(bodyYaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);       // right-hand side
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);        // facing
        Location l = feet.clone().add(rx * 0.42 - fx * 0.04, sneaking ? 1.12 : 1.42, rz * 0.42 - fz * 0.04);
        l.setYaw(bodyYaw + 180);                               // the model's front (north) faces forward
        l.setPitch(0);
        return l;
    }

    private void tick() {
        shown.entrySet().removeIf(e -> {
            Player p = plugin.getServer().getPlayer(e.getKey());
            boolean gone = p == null || !e.getValue().isValid() || active(p) == null;
            if (gone) {
                lastYaw.remove(e.getValue().getUniqueId());
                if (e.getValue().isValid()) {
                    e.getValue().remove();
                }
            }
            return gone;
        });
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            String id = active(p);
            ItemDisplay d = shown.get(p.getUniqueId());
            if (id == null || hidden(p)) {
                if (d != null) {
                    despawn(p.getUniqueId());
                }
                continue;
            }
            if (d == null || !d.getWorld().equals(p.getWorld())) {
                despawn(p.getUniqueId());
                d = spawn(p.getLocation(), id);
                shown.put(p.getUniqueId(), d);
            }
            if (!p.getPassengers().contains(d)) {
                p.addPassenger(d);     // riding: the client moves it with the player – no lag behind
            }
            turn(d, p.getBodyYaw());
        }
    }

    /**
     * Turns the companion with the body. The entity itself never rotates (a rotation would snap and lag); instead
     * the display transformation is rotated and the client interpolates smoothly between two ticks.
     */
    private void turn(ItemDisplay d, float bodyYaw) {
        float yaw = bodyYaw + 180;                                   // model front (north) faces where the body faces
        Float last = lastYaw.get(d.getUniqueId());
        if (last != null && Math.abs(((yaw - last) % 360 + 540) % 360 - 180) < 0.8f) {
            return;
        }
        lastYaw.put(d.getUniqueId(), yaw);
        org.joml.Quaternionf rot = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-yaw));
        Vector3f offset = rot.transform(new Vector3f(0f, SCALE * 0.5f + 0.02f, 0f));   // right shoulder
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(new Transformation(offset, rot, new Vector3f(SCALE, SCALE, SCALE), new org.joml.Quaternionf()));
    }

    /** Takes the companion off before a teleport to another world (players with passengers cannot change worlds). */
    public static void unmount(Player p) {
        for (org.bukkit.entity.Entity e : p.getPassengers()) {
            if ("companion".equals(e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING))) {
                p.removePassenger(e);
                e.remove();
            }
        }
    }

    private ItemDisplay spawn(Location at, String id) {
        // same model lookup as the item itself (paper + custom model data string)
        ItemStack item = new online.mythoscraft.core.util.ItemBuilder(Material.PAPER).model(id).build();
        return at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setPersistent(false);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setViewRange(0.6f);
            e.setShadowRadius(0);
            e.setRotation(0, 0);
            e.setTransformation(new Transformation(new Vector3f(0f, SCALE * 0.5f + 0.02f, 0f), new AxisAngle4f(),
                    new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
            e.setBillboard(Display.Billboard.FIXED);
            e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "companion");
        });
    }

    public void shutdown() {
        shown.values().forEach(d -> {
            if (d.isValid()) {
                d.remove();
            }
        });
        shown.clear();
    }
}
