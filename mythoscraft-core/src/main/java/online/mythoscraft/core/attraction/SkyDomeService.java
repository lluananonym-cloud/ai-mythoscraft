package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Season sky above the spawn: huge glowing pictures high in the air around the island – northern lights in winter,
 * a giant moon with bats at Halloween, blossom clouds in spring, fireworks at New Year … They slowly drift and change
 * with the season. Together with the season pack (clouds, sun, moon) and the season time/weather.
 */
public final class SkyDomeService {

    private static final String TAG = "sky_dome";

    /** theme → pictures (item model ids in the pack) */
    private static final Map<String, List<String>> PICTURES = Map.of(
            "winter", List.of("sky_winter_aurora"),
            "halloween", List.of("sky_halloween_moon", "sky_halloween_mist"),
            "spring", List.of("sky_spring_blossom"),
            "summer", List.of("sky_summer_sun", "sky_summer_cloud"),
            "autumn", List.of("sky_autumn_leaves"),
            "newyear", List.of("sky_newyear_fireworks"),
            "valentine", List.of("sky_valentine_hearts"),
            "stars", List.of("sky_stars_galaxy"));

    private final MythosCraft plugin;
    private final List<ItemDisplay> displays = new ArrayList<>();
    private boolean enabled = true;
    private int count = 10;
    private double radius = 62;
    private double height = 52;
    private float scale = 46;
    private String shown = "";
    private int tick;

    public SkyDomeService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.sky-dome");
        enabled = cfg.bool("enabled", true);
        count = cfg.integer("pictures", 10, 1, 40);
        radius = cfg.decimal("radius", 62, 10, 90);
        height = cfg.decimal("height", 52, 10, 120);
        scale = (float) cfg.decimal("scale", 46, 4, 200);
        clear();
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 200, 10, "sky dome", this::tick);
    }

    private String theme() {
        return plugin.attractions().deco().theme().name().toLowerCase(Locale.ROOT);
    }

    private void tick() {
        Location c = plugin.attractionStore().center();
        if (!enabled || c == null || !c.isChunkLoaded()) {
            return;
        }
        String theme = theme();
        displays.removeIf(d -> !d.isValid());
        if (!theme.equals(shown) || displays.isEmpty() && PICTURES.containsKey(theme)) {
            clear();
            shown = theme;
            List<String> pics = PICTURES.get(theme);
            if (pics != null) {
                spawn(c, pics);
            }
        }
        // slow drift around the island
        tick++;
        for (int i = 0; i < displays.size(); i++) {
            ItemDisplay d = displays.get(i);
            double a = Math.PI * 2 * i / displays.size() + tick * 0.0025;
            double h = height + Math.sin(tick * 0.01 + i) * 3 + (i % 3) * 6;
            Location at = c.clone().add(Math.cos(a) * radius, h, Math.sin(a) * radius);
            at.setYaw((float) Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a))));
            d.teleport(at);
        }
    }

    private void spawn(Location c, List<String> pics) {
        World w = c.getWorld();
        for (int i = 0; i < count; i++) {
            String model = pics.get(i % pics.size());
            ItemStack item = new ItemStack(Material.PAPER);
            item.editMeta(m -> m.setItemModel(new NamespacedKey(Keys.NAMESPACE, model)));
            float s = scale * (model.endsWith("_moon") || model.endsWith("_sun") ? 0.8f : 1f);
            ItemDisplay d = w.spawn(c.clone().add(0, height, 0), ItemDisplay.class, e -> {
                e.setItemStack(item);
                e.setPersistent(false);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setViewRange(8f);
                e.setTeleportDuration(10);
                e.setBillboard(Display.Billboard.FIXED);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s),
                        new Quaternionf(new AxisAngle4f(0, 0, 1, 0))));
                e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
            });
            displays.add(d);
            // a single moon/sun is enough
            if ((model.endsWith("_moon") || model.endsWith("_sun")) && pics.size() > 1) {
                pics = pics.subList(1, pics.size());
            }
        }
    }

    public void clear() {
        displays.forEach(Entity::remove);
        displays.clear();
        shown = "";
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (TAG.equals(d.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING))) {
                    d.remove();
                }
            }
        }
    }
}
