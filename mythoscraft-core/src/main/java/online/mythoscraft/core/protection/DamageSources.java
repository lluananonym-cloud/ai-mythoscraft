package online.mythoscraft.core.protection;

import online.mythoscraft.core.util.Keys;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/** Resolves the player responsible for indirect damage (arrows, tridents, TNT, potions, pets, fangs, …). */
public final class DamageSources {

    private DamageSources() {
    }

    public static Player attacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Player p = resolve(event.getDamageSource().getCausingEntity());
        return p != null ? p : resolve(byEntity.getDamager());
    }

    public static Player resolve(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Player p) {
            return p;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter instanceof Player p ? p : resolve(shooter);
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != null && tnt.getSource() != entity) {
            return resolve(tnt.getSource());
        }
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) {
            return p;
        }
        if (entity instanceof EvokerFangs fangs && fangs.getOwner() instanceof Player p) {
            return p;
        }
        if (entity instanceof LightningStrike lightning) {
            return lightning.getCausingPlayer();
        }
        if (entity instanceof Tameable pet && pet.isTamed() && pet.getOwner() instanceof Player p) {
            return p;
        }
        return null;
    }

    /** Entities the region flag "entity-damage" protects: passive/neutral mobs, villagers, armor stands, frames, … */
    public static boolean isProtected(Entity entity) {
        if (entity instanceof Player || entity instanceof Enemy) {
            return false;
        }
        return entity instanceof LivingEntity || entity instanceof Hanging || entity instanceof EnderCrystal;
    }

    /** NPCs from plugins such as Citizens mark themselves with the "NPC" metadata. */
    public static boolean isNpc(Entity entity) {
        return entity.hasMetadata("NPC");
    }

    /** Entities spawned by MythosCraft itself (crates, holograms, event objects). */
    public static boolean isPluginEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.ENTITY, PersistentDataType.STRING);
    }
}
