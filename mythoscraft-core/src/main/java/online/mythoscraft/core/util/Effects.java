package online.mythoscraft.core.util;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Small reusable visual effects built from display entities and particles. */
public final class Effects {

    private Effects() {
    }

    /** An item that rises and spins for a moment, then disappears (e.g. a found gem or relic). */
    public static void floatingItem(Plugin plugin, Location at, ItemStack item, float scale) {
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
            d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "effect");
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(25);
            display.setTransformation(new Transformation(new Vector3f(0, 1.2f, 0),
                    new Quaternionf(new AxisAngle4f((float) Math.PI, 0, 1, 0)), new Vector3f(scale * 0.2f), new Quaternionf()));
        }, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.getWorld().spawnParticle(Particle.END_ROD, display.getLocation().add(0, 1.2, 0), 8, 0.15, 0.15, 0.15, 0.02);
                display.remove();
            }
        }, 28L);
    }

    /** A firework that explodes instantly and does no damage. */
    public static void burst(Location at, Color primary, Color fade, boolean large) {
        at.getWorld().spawn(at, Firework.class, fw -> {
            fw.setPersistent(false);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(large ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.BURST)
                    .withColor(primary).withFade(fade).withFlicker().build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "effect");
            fw.detonate();
        });
    }
}
