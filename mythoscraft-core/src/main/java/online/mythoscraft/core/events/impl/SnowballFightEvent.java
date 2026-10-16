package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Snowball fight (Frostfest): every participant regularly gets event snowballs. A hit on another player scores
 * (no damage, just a little knockback and snow). The event snowballs disappear when it ends.
 */
public final class SnowballFightEvent extends MythosEvent {

    private static final String TAG = "event_snowball";

    private int refillSeconds = 4;
    private int maxBalls = 16;
    private int refillTimer;

    public SnowballFightEvent(MythosCraft plugin) {
        super(plugin, "snowballfight");
    }

    @Override
    protected int defaultDuration() {
        return 180;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        refillSeconds = cfg.integer("refill-seconds", 4, 1, 60);
        maxBalls = cfg.integer("max-snowballs", 16, 1, 64);
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.hits";
    }

    private ItemStack ball(int amount) {
        ItemStack s = new ItemBuilder(Material.SNOWBALL, amount).name(plugin.messages().get("events.snowballfight.item")).glint(true).build();
        s.editPersistentDataContainer(pdc -> pdc.set(Keys.key(TAG), PersistentDataType.STRING, "1"));
        return s;
    }

    private int count(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && s.getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    @Override
    protected void onStart() {
        refillTimer = 0;
        for (Player p : participants()) {
            p.getInventory().addItem(ball(maxBalls));
        }
    }

    @Override
    protected void onTick() {
        if (++refillTimer < refillSeconds) {
            return;
        }
        refillTimer = 0;
        for (Player p : participants()) {
            int have = count(p);
            if (have < maxBalls) {
                p.getInventory().addItem(ball(Math.min(4, maxBalls - have)));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball ball && ball.getShooter() instanceof Player
                && ball.getItem().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
            ball.getPersistentDataContainer().set(Keys.key(TAG), PersistentDataType.STRING, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball) || !(ball.getShooter() instanceof Player shooter)
                || !session.equals(ball.getPersistentDataContainer().get(Keys.key(TAG), PersistentDataType.STRING))) {
            return;
        }
        if (!(event.getHitEntity() instanceof Player target) || target.equals(shooter) || !eligible(target) || !eligible(shooter)) {
            return;
        }
        Vector push = target.getLocation().toVector().subtract(shooter.getLocation().toVector()).setY(0);
        if (push.lengthSquared() > 0.01) {
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.3).setY(0.2)));
        }
        target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 20));
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1.4, 0), 15, 0.3, 0.3, 0.3, 0.05);
        addScore(shooter, 1);
        SoundSpec.of("entity.experience_orb.pickup", 0.6f, 1.8f).play(shooter);
        plugin.messages().actionBar(shooter, "events.snowballfight.hit", "player", target.getName(),
                "total", Math.round(score(shooter.getUniqueId())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getItemDrop().getItemStack().getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void cleanup() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = p.getInventory().getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null && contents[i].getPersistentDataContainer().has(Keys.key(TAG), PersistentDataType.STRING)) {
                    contents[i] = null;
                    changed = true;
                }
            }
            if (changed) {
                p.getInventory().setContents(contents);
            }
        }
    }
}
