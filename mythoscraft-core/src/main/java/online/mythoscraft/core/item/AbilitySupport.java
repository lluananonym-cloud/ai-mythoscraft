package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.protection.DamageSources;
import online.mythoscraft.core.protection.Flag;
import online.mythoscraft.core.util.Cooldowns;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Shared rules for item abilities: cooldowns with a visible overlay and the same hurt rules as melee combat. */
public final class AbilitySupport {

    private final MythosCraft plugin;
    private final Cooldowns cooldowns;

    public AbilitySupport(MythosCraft plugin, Cooldowns cooldowns) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
    }

    /** True if the ability is still cooling down (and tells the player); otherwise starts the cooldown. */
    public boolean onCooldown(Player player, ItemStack item, String key, double seconds) {
        long left = cooldowns.remainingSeconds(key, player.getUniqueId());
        if (left > 0) {
            plugin.messages().actionBar(player, "tools.cooldown", "seconds", left);
            return true;
        }
        long millis = Math.round(seconds * 1000);
        cooldowns.set(key, player.getUniqueId(), millis);
        if (seconds > 0 && item != null && !item.isEmpty()) {
            player.setCooldown(item, (int) Math.round(seconds * 20));
        }
        return false;
    }

    /** Ability damage obeys PvP, region and NPC rules exactly like a sword hit. */
    public boolean mayHurt(Player attacker, Entity target) {
        if (!(target instanceof LivingEntity) || target.equals(attacker) || target instanceof ArmorStand
                || DamageSources.isPluginEntity(target) || DamageSources.isNpc(target) || target.isDead()) {
            return false;
        }
        if (target instanceof Player victim) {
            return plugin.protection().pvpAllowed(attacker, victim);
        }
        return !DamageSources.isProtected(target) || plugin.protection().allows(attacker, target.getLocation(), Flag.ENTITY_DAMAGE);
    }

    /** Clears every ability cooldown of the player (mana potion). */
    public void resetCooldowns(Player player) {
        cooldowns.clear(player.getUniqueId());
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && !s.isEmpty()) {
                player.setCooldown(s, 0);
            }
        }
    }

    /** Removes one item from the stack the player holds in the given hand. */
    public static void consumeOne(ItemStack item) {
        item.setAmount(item.getAmount() - 1);
    }
}
