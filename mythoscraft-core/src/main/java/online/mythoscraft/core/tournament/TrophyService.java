package online.mythoscraft.core.tournament;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.util.Keys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tournament trophies: the item remembers its winner and how often that player has won this tournament tier.
 * Right-click a block to put the trophy on top of it (a display with a floating sign: tier, winner, number of wins);
 * the owner (or an admin) picks it up again with sneak + right-click.
 */
public final class TrophyService implements Listener {

    public static final String TAG = "trophy";
    private final NamespacedKey ownerKey, ownerNameKey, winsKey, placedKey, placerKey;
    private final MythosCraft plugin;

    public TrophyService(MythosCraft plugin) {
        this.plugin = plugin;
        ownerKey = new NamespacedKey(Keys.NAMESPACE, "trophy_owner");
        ownerNameKey = new NamespacedKey(Keys.NAMESPACE, "trophy_owner_name");
        winsKey = new NamespacedKey(Keys.NAMESPACE, "trophy_wins");
        placedKey = new NamespacedKey(Keys.NAMESPACE, "trophy_id");
        placerKey = new NamespacedKey(Keys.NAMESPACE, "trophy_placer");
    }

    static TournamentTier tierOf(CustomItem type) {
        for (TournamentTier t : TournamentTier.values()) {
            if (t.trophy() == type) {
                return t;
            }
        }
        return null;
    }

    /** The trophy for a win; {@code countWin} adds the win to the player's statistics (not for test tournaments). */
    public ItemStack award(Player winner, TournamentTier tier, boolean countWin) {
        String key = "tournament.wins." + tier.id();
        long wins = countWin ? plugin.progress().add(winner, key, 1) : Math.max(1, plugin.progress().get(winner, key));
        return create(tier, winner.getUniqueId(), winner.getName(), wins);
    }

    public ItemStack create(TournamentTier tier, UUID owner, String ownerName, long wins) {
        ItemStack item = plugin.items().create(tier.trophy());
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
            pdc.set(ownerNameKey, PersistentDataType.STRING, ownerName);
            pdc.set(winsKey, PersistentDataType.LONG, wins);
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(plugin.messages().get("trophy.lore-owner", "player", ownerName));
            lore.add(plugin.messages().get("trophy.lore-wins", "count", wins));
            lore.add(plugin.messages().get("trophy.lore-place"));
            meta.lore(lore);
        });
        return item;
    }

    // ------------------------------------------------------------------ placing
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getBlockFace() != BlockFace.UP) {
            return;
        }
        ItemStack hand = event.getItem();
        TournamentTier tier = tierOf(plugin.items().identify(hand));
        boolean totem = plugin.items().is(hand, online.mythoscraft.core.item.CustomItem.MINEPRO_TOTEM);
        if (tier == null && !totem) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        Block on = event.getClickedBlock().getRelative(BlockFace.UP);
        if (!on.getType().isAir() || !on.getWorld().getNearbyEntities(on.getLocation().add(0.5, 0.5, 0.5), 0.45, 0.5, 0.45,
                e -> TAG.equals(tag(e))).isEmpty()) {
            plugin.messages().actionBar(p, "trophy.no-space");
            return;
        }
        // same rules as placing a block there (protection, claims, other plugins) – checked with a plain block, because
        // custom items themselves may never be placed as blocks
        BlockPlaceEvent check = new BlockPlaceEvent(on, on.getState(), event.getClickedBlock(), new ItemStack(org.bukkit.Material.STONE), p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(check);
        if (check.isCancelled() || !check.canBuild()) {
            plugin.messages().actionBar(p, "trophy.no-build");
            return;
        }
        place(on.getLocation().add(0.5, 0, 0.5), p, hand.asOne());
        hand.subtract(1);
        plugin.messages().actionBar(p, totem ? "trophy.totem-placed" : "trophy.placed");
    }

    private void place(Location base, Player placer, ItemStack item) {
        PersistentDataContainer src = item.getItemMeta().getPersistentDataContainer();
        String ownerName = src.getOrDefault(ownerNameKey, PersistentDataType.STRING, placer.getName());
        long wins = src.getOrDefault(winsKey, PersistentDataType.LONG, 1L);
        TournamentTier tier = tierOf(plugin.items().identify(item));
        String id = UUID.randomUUID().toString();
        float scale = tier == null ? 1.1f : 0.9f;      // the MinePro totem stands a bit bigger
        Location at = base.clone().add(0, scale * 0.5, 0);
        at.setYaw(placer.getLocation().getYaw());      // the star plate faces the player
        at.setPitch(0);
        base.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            d.setPersistent(true);
            mark(d.getPersistentDataContainer(), id, placer);
        });
        Component text = tier == null
                ? plugin.messages().get("trophy.totem-sign", "player", placer.getName())
                : plugin.messages().get("trophy.sign-title", "tier", plugin.tournaments().tierName(tier))
                .append(Component.newline()).append(plugin.messages().get("trophy.sign-owner", "player", ownerName))
                .append(Component.newline()).append(plugin.messages().get("trophy.sign-wins", "count", wins));
        base.getWorld().spawn(base.clone().add(0, scale + 0.45, 0), TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0x60, 0x10, 0x0c, 0x18));
            t.setPersistent(true);
            mark(t.getPersistentDataContainer(), id, placer);
        });
        base.getWorld().spawn(base, Interaction.class, i -> {
            i.setInteractionWidth(0.7f);
            i.setInteractionHeight(scale);
            i.setResponsive(true);
            i.setPersistent(true);
            mark(i.getPersistentDataContainer(), id, placer);
            // the item itself is kept on the hitbox so it comes back unchanged
            i.getPersistentDataContainer().set(new NamespacedKey(Keys.NAMESPACE, "trophy_item"), PersistentDataType.BYTE_ARRAY,
                    item.serializeAsBytes());
        });
    }

    private void mark(PersistentDataContainer pdc, String id, Player placer) {
        pdc.set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        pdc.set(placedKey, PersistentDataType.STRING, id);
        pdc.set(placerKey, PersistentDataType.STRING, placer.getUniqueId().toString());
    }

    private static String tag(Entity e) {
        return e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ picking up
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (event.getHand() != EquipmentSlot.HAND || !(e instanceof Interaction) || !TAG.equals(tag(e))) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        String placer = e.getPersistentDataContainer().get(placerKey, PersistentDataType.STRING);
        if (!p.isSneaking()) {
            plugin.messages().actionBar(p, "trophy.hint");
            return;
        }
        if (!p.getUniqueId().toString().equals(placer) && !p.hasPermission("mythos.admin")) {
            plugin.messages().actionBar(p, "trophy.not-yours");
            return;
        }
        byte[] raw = e.getPersistentDataContainer().get(new NamespacedKey(Keys.NAMESPACE, "trophy_item"), PersistentDataType.BYTE_ARRAY);
        String id = e.getPersistentDataContainer().get(placedKey, PersistentDataType.STRING);
        for (Entity n : e.getWorld().getNearbyEntities(e.getLocation(), 1.5, 2.5, 1.5)) {
            if (id != null && id.equals(n.getPersistentDataContainer().get(placedKey, PersistentDataType.STRING))) {
                n.remove();
            }
        }
        if (raw != null) {
            CustomItemManager.give(p, ItemStack.deserializeBytes(raw));
        }
        plugin.messages().actionBar(p, "trophy.picked");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHit(EntityDamageByEntityEvent event) {
        if (TAG.equals(tag(event.getEntity()))) {
            event.setCancelled(true);
        }
    }
}
