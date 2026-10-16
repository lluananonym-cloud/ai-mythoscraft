package online.mythoscraft.core.teleport;

import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.protection.DamageSources;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Remembers recent PvP so teleports can't be used to escape a fight. */
public final class CombatTracker implements Listener {

    private final Map<UUID, Long> lastCombat = new HashMap<>();
    private long tagMillis = 10_000;

    public void reload(ConfigReader root) {
        tagMillis = root.integer("teleport.combat-tag-seconds", 10, 0, 300) * 1000L;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = DamageSources.attacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        lastCombat.put(victim.getUniqueId(), now);
        lastCombat.put(attacker.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastCombat.remove(event.getPlayer().getUniqueId());
    }

    /** Seconds until the combat tag runs out, 0 if not in combat. */
    public long remainingSeconds(Player player) {
        Long last = lastCombat.get(player.getUniqueId());
        if (last == null) {
            return 0;
        }
        long left = last + tagMillis - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }
}
