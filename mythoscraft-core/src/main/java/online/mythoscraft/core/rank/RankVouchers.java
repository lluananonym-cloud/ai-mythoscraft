package online.mythoscraft.core.rank;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Durations;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Rank vouchers ("Rang-Gutscheine"): a tradeable item that grants a rank for a time (or forever) on right-click.
 * Never downgrades and never replaces a permanent rank with a temporary one; MYTHOS and staff ranks can't be
 * put on a voucher.
 */
public final class RankVouchers implements Listener {

    public static final NamespacedKey RANK = Keys.key("voucher_rank");
    public static final NamespacedKey DURATION = Keys.key("voucher_duration");

    private final MythosCraft plugin;

    public RankVouchers(MythosCraft plugin) {
        this.plugin = plugin;
    }

    /** @param duration e.g. "30d", or "perm" */
    public ItemStack create(String rankId, String duration, int amount) {
        Rank rank = plugin.ranks().byId(rankId);
        if (rank == null || rank.restricted()) {
            return null;
        }
        String time = durationText(duration);
        return new ItemBuilder(Material.PAPER, amount)
                .name(plugin.messages().get("vouchers.name", "rank", Text.parse(rank.display()), "duration", time))
                .loreComponents(plugin.messages().getList("vouchers.lore", "rank", Text.parse(rank.display()), "duration", time))
                .model("rank_voucher")
                .glint(true)
                .rarity(ItemRarity.EPIC)
                .maxStack(16)
                .tag(RANK, rank.id())
                .tag(DURATION, duration.toLowerCase(Locale.ROOT))
                .build();
    }

    public String durationText(String duration) {
        if (duration == null || duration.equalsIgnoreCase("perm")) {
            return Text.plain(plugin.messages().get("vouchers.forever"));
        }
        return Durations.parse(duration).map(Durations::format).orElse(duration);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.isEmpty()) {
            return;
        }
        String rankId = item.getPersistentDataContainer().get(RANK, PersistentDataType.STRING);
        if (rankId == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        redeem(p, item, rankId, item.getPersistentDataContainer().getOrDefault(DURATION, PersistentDataType.STRING, "perm"));
    }

    private void redeem(Player p, ItemStack item, String rankId, String duration) {
        Rank target = plugin.ranks().byId(rankId);
        PlayerData data = plugin.playerData().get(p);
        if (target == null || target.restricted() || data == null) {
            plugin.messages().send(p, "vouchers.invalid");
            return;
        }
        Rank current = plugin.ranks().rankOf(data);
        boolean permanentVoucher = duration.equalsIgnoreCase("perm");
        Optional<Duration> length = permanentVoucher ? Optional.empty() : Durations.parse(duration);
        if (!permanentVoucher && length.isEmpty()) {
            plugin.messages().send(p, "vouchers.invalid");
            return;
        }
        boolean currentPermanent = current != plugin.ranks().defaultRank() && data.rankExpires() <= 0;
        long now = System.currentTimeMillis();
        long expires;
        if (current.restricted() || current.weight() > target.weight()) {
            plugin.messages().send(p, "vouchers.lower", "rank", Text.parse(current.display()));
            return;
        }
        if (current == target) {
            if (currentPermanent) {
                plugin.messages().send(p, "vouchers.already-permanent", "rank", Text.parse(current.display()));
                return;
            }
            expires = permanentVoucher ? 0 : Math.max(now, data.rankExpires()) + length.get().toMillis();
        } else {
            if (currentPermanent && !permanentVoucher) {
                plugin.messages().send(p, "vouchers.would-replace", "rank", Text.parse(current.display()),
                        "new", Text.parse(target.display()));
                return;
            }
            expires = permanentVoucher ? 0 : now + length.get().toMillis();
        }
        item.setAmount(item.getAmount() - 1);
        data.rank(target.id(), expires);
        plugin.playerData().save(data);
        plugin.ranks().applyPermissions(p);
        plugin.display().refresh(p);
        String left = expires == 0 ? durationText("perm") : Durations.format(Duration.ofMillis(expires - now));
        plugin.messages().send(p, "vouchers.redeemed", "rank", Text.parse(target.display()), "duration", left);
        plugin.messages().broadcast("vouchers.broadcast", "player", p.getName(), "rank", Text.parse(target.display()));
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(0xF59E0B), 1.4f));
        SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.2f).play(p);
        plugin.getLogger().info("[Rank] " + p.getName() + " redeemed a voucher: " + target.id() + " (" + duration + ")");
    }
}
