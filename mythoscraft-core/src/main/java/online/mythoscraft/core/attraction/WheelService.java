package online.mythoscraft.core.attraction;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.WeightedRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wheel of fortune: twelve coloured segments (item displays) on a vertical circle. Right-click spins (once a day free,
 * afterwards for coins), left-click lists the prizes. The prize is picked by weight first, the wheel then eases out
 * exactly onto that segment.
 */
public final class WheelService {

    static final String TAG = "wheel";

    record Segment(Reward reward, double weight, Material block) {
    }

    private final MythosCraft plugin;
    private final List<Segment> segments = new ArrayList<>();
    private final List<ItemDisplay> parts = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private Location center;
    private double angle;
    private BukkitTask spin;
    /** Prize of the running spin – paid out even if the wheel is rebuilt mid-spin. */
    private Runnable pending;
    private long price = 2500;
    private Currency currency = Currency.COINS;
    private double radius = 3.4;

    public WheelService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.wheel");
        price = cfg.longValue("price", 2500, 0, 1_000_000_000);
        Currency c = Currency.byId(cfg.string("currency", "coins"));
        currency = c == null ? Currency.COINS : c;
        radius = cfg.decimal("radius", 5, 1.5, 10);
        segments.clear();
        List<?> list = cfg.raw().getList("segments");
        if (list != null) {
            int index = 0;
            for (Object o : list) {
                index++;
                if (!(o instanceof Map<?, ?> map)) {
                    continue;
                }
                try {
                    Reward reward = Reward.parse(String.valueOf(map.get("reward")));
                    double weight = map.get("weight") == null ? 1 : Double.parseDouble(String.valueOf(map.get("weight")));
                    Material block = map.get("block") == null ? Material.WHITE_CONCRETE
                            : Material.matchMaterial(String.valueOf(map.get("block")));
                    segments.add(new Segment(reward, weight, block == null || !block.isBlock() ? Material.WHITE_CONCRETE : block));
                } catch (IllegalArgumentException e) {
                    cfg.warn("segments", "Feld " + index + ": " + e.getMessage());
                }
            }
        }
    }

    public void rebuild() {
        clear();
        center = plugin.attractionStore().first("wheel");
        if (center != null) {
            center = center.clone();
            center.setPitch(0);
        }
        if (center == null || segments.isEmpty() || !center.isChunkLoaded()) {
            return;
        }
        // enough room for every field (about 1.5 blocks per field on the rim)
        radius = Math.max(radius, segments.size() * 1.5 / (2 * Math.PI));
        for (Segment s : segments) {
            ItemDisplay d = center.getWorld().spawn(center, ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemStack(new ItemStack(s.block()));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.95f, 0.95f, 0.35f), new AxisAngle4f()));
                e.setTeleportDuration(2);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setRotation(center.getYaw(), 0);
                e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
            });
            parts.add(d);
            spawned.add(d);
        }
        spawned.add(center.getWorld().spawn(center, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(new ItemStack(Material.GOLD_BLOCK));
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.3f, 1.3f, 0.5f), new AxisAngle4f()));
            e.setRotation(center.getYaw(), 0);
            e.setBrightness(new Display.Brightness(15, 15));
            e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        }));
        Location face = center.clone().add(new Vector(-Math.sin(Math.toRadians(center.getYaw())), 0, Math.cos(Math.toRadians(center.getYaw())))
                .multiply(0.32));
        spawned.add(center.getWorld().spawn(face, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(new online.mythoscraft.core.util.ItemBuilder(Material.PAPER).model("wheel_hub").build());
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.8f, 1.8f, 1.8f), new AxisAngle4f()));
            e.setRotation(center.getYaw() + 180, 0);
            e.setBrightness(new Display.Brightness(15, 15));
            e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        }));
        Location pointer = center.clone().add(0, radius + 1.0, 0);
        spawned.add(center.getWorld().spawn(pointer, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(new online.mythoscraft.core.util.ItemBuilder(Material.PAPER).model("wheel_pointer").build());
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f()));
            e.setRotation(center.getYaw(), 0);
            e.setBrightness(new Display.Brightness(15, 15));
            e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        }));
        spawned.add(center.getWorld().spawn(center.clone().add(0, radius + 2.6, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0x70, 0x1b, 0x10, 0x22));
            t.text(plugin.messages().get("attractions.wheel.hologram", "price", price,
                    "currency", plugin.messages().get("economy.names." + currency.id())));
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        }));
        Interaction hit = center.getWorld().spawn(center.clone().add(0, -radius - 0.6, 0), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth((float) (radius * 2 + 1));
            i.setInteractionHeight((float) (radius * 2 + 1.2));
            i.setResponsive(true);
            i.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
        });
        spawned.add(hit);
        layout();
    }

    private void layout() {
        if (center == null || parts.isEmpty()) {
            return;
        }
        double yaw = Math.toRadians(center.getYaw());
        Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        int n = parts.size();
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(angle + i * 360.0 / n);
            Location l = center.clone().add(side.clone().multiply(Math.cos(a) * radius)).add(0, Math.sin(a) * radius, 0);
            l.setYaw(center.getYaw());
            ItemDisplay d = parts.get(i);
            if (d.isValid()) {
                d.teleport(l);
            }
        }
    }

    public boolean isPart(Entity e) {
        return TAG.equals(e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING));
    }

    public void showPrizes(Player p) {
        plugin.messages().send(p, "attractions.wheel.prizes-header");
        WeightedRandom<Segment> table = new WeightedRandom<>(segments, Segment::weight);
        for (int i = 0; i < segments.size(); i++) {
            plugin.messages().sendRaw(p, "attractions.wheel.prize-line", "reward", segments.get(i).reward().describe(plugin, 1.0),
                    "chance", String.format(java.util.Locale.GERMANY, "%.1f", table.chance(i) * 100));
        }
    }

    public void use(Player p) {
        if (spin != null) {
            plugin.messages().actionBar(p, "attractions.wheel.busy");
            return;
        }
        if (parts.isEmpty()) {
            return;
        }
        long today = plugin.multipliers().today().toEpochDay();
        long usedToday = plugin.progress().get(p, "wheel.day") == today ? Math.max(1, plugin.progress().get(p, "wheel.free-used")) : 0;
        if (plugin.progress().get(p, "wheel.day") == today && plugin.progress().get(p, "wheel.free-used") == 0) {
            usedToday = 1; // spun before the extra rank spins existed
        }
        boolean free = usedToday < 1 + plugin.rankPerks().extraSpins(p);
        if (!free && !plugin.attractions().charge(p, currency, price)) {
            return;
        }
        if (free) {
            plugin.progress().set(p, "wheel.day", today);
            plugin.progress().set(p, "wheel.free-used", usedToday + 1);
        }
        plugin.progress().add(p, "wheel.spins", 1);
        int n = segments.size();
        int win = pickIndex();
        // segment i sits at angle + i*step; it must end at the top (90°)
        double step = 360.0 / n;
        double target = 90 - win * step;
        double start = angle;
        double delta = ((target - start) % 360 + 360) % 360 + 360 * (4 + ThreadLocalRandom.current().nextInt(2));
        int duration = 110;
        plugin.messages().send(p, free ? "attractions.wheel.spin-free" : "attractions.wheel.spin-paid", "price", price,
                "currency", plugin.messages().get("economy.names." + currency.id()));
        Segment prize = segments.get(win);
        pending = () -> finish(p, prize);
        int[] tick = {0};
        int[] lastSegment = {-1};
        spin = plugin.tasks().repeat(this, 1, 1, "wheel spin", () -> {
            tick[0]++;
            double t = Math.min(1, tick[0] / (double) duration);
            double eased = 1 - Math.pow(1 - t, 3);
            angle = start + delta * eased;
            layout();
            int seg = (int) Math.floor(((angle % 360) + 360) % 360 / step);
            if (seg != lastSegment[0]) {
                lastSegment[0] = seg;
                SoundSpec.of("block.note_block.hat", 0.6f, 1.4f).playAt(center);
            }
            if (t >= 1) {
                spin.cancel();
                spin = null;
                pending = null;
                finish(p, prize);
            }
        });
    }

    private int pickIndex() {
        double total = 0;
        for (Segment s : segments) {
            total += s.weight();
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < segments.size(); i++) {
            r -= segments.get(i).weight();
            if (r < 0) {
                return i;
            }
        }
        return segments.size() - 1;
    }

    private void finish(Player p, Segment s) {
        if (center != null) {
            Location top = center.clone().add(0, radius, 0);
            center.getWorld().spawnParticle(Particle.FIREWORK, top, 40, 0.5, 0.5, 0.5, 0.15);
            SoundSpec.of("entity.player.levelup", 1f, 1.2f).playAt(center);
        }
        if (!p.isOnline()) {
            return;
        }
        s.reward().give(plugin, p, 1.0);
        Component prize = s.reward().describe(plugin, 1.0);
        plugin.messages().send(p, "attractions.wheel.won", "reward", prize);
        if (s.weight() <= 2) {
            plugin.messages().broadcast("attractions.wheel.jackpot", "player", p.getName(), "reward", prize);
        }
    }

    public void clear() {
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
        if (pending != null) {
            Runnable r = pending;
            pending = null;
            r.run();
        }
        spawned.forEach(e -> {
            if (e.isValid()) {
                e.remove();
            }
        });
        spawned.clear();
        parts.clear();
    }

    public boolean present() {
        return !parts.isEmpty() && parts.get(0).isValid();
    }

    public void shutdown() {
        clear();
    }
}
