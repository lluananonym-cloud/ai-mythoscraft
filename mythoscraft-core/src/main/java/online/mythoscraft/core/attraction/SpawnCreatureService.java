package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Own little creatures that live on the spawn meadows – no vanilla mobs, every one is built from display blocks and
 * animated by the plugin: the hopping Moosling, the fluttering Glimmerling, the floating Star Jelly and the walking
 * Lantern Shroom. They change their look with the season. Right-click = pet them (hearts, a little sound, a few coins
 * now and then).
 */
public final class SpawnCreatureService implements Listener {

    private static final String TAG = "spawn_creature";

    enum Role { BODY, WING_L, WING_R, TENTACLE, EAR, EYE, GLOW, TAIL, ARM_L, ARM_R, FIN }

    /** One display block of a creature: material, local offset (centre bottom) and size. */
    record Part(String material, float x, float y, float z, float sx, float sy, float sz, Role role, boolean glow) {
    }

    enum Kind {
        MOOSLING(0.09, 0, 1.1f, 0.9f),
        GLIMMERLING(0.12, 1.4, 0.9f, 0.8f),
        STAR_JELLY(0.05, 3.2, 1.2f, 1.6f),
        LANTERN_SHROOM(0.07, 0, 0.9f, 1.4f),
        CRYSTAL_GOLEM(0.05, 0, 1.2f, 2.0f),
        SPARK_FOX(0.17, 0, 0.9f, 0.8f),
        CLOUD_WHALE(0, 0, 0, 0);

        final double speed;
        final double hover;
        final float width;
        final float height;

        Kind(double speed, double hover, float width, float height) {
            this.speed = speed;
            this.hover = hover;
            this.width = width;
            this.height = height;
        }
    }

    private final class Creature {
        final Kind kind;
        final List<BlockDisplay> parts = new ArrayList<>();
        final List<Part> design = new ArrayList<>();
        Interaction hitbox;
        Location pos;
        Location target;
        float yaw;
        int age;
        int pause;

        Creature(Kind kind) {
            this.kind = kind;
        }
    }

    private final MythosCraft plugin;
    private final List<Creature> creatures = new ArrayList<>();
    private final Map<UUID, Long> petCooldown = new HashMap<>();
    private boolean enabled = true;
    private int perKind = 3;
    private long petCoins = 10;
    private SeasonDecoService.Theme builtFor;

    public SpawnCreatureService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.creatures");
        enabled = cfg.bool("enabled", true);
        perKind = cfg.integer("per-kind", 3, 0, 12);
        petCoins = cfg.longValue("pet-coins", 10, 0, 100_000);
        despawnAll();
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 160, 3, "spawn creatures", this::tick);
    }

    // ---- designs ------------------------------------------------------------------------------------------

    private static String themed(SeasonDecoService.Theme t, String normal, String winter, String autumn, String halloween, String spring) {
        return switch (t) {
            case WINTER, NEWYEAR -> winter;
            case AUTUMN -> autumn;
            case HALLOWEEN -> halloween;
            case SPRING, VALENTINE -> spring;
            default -> normal;
        };
    }

    static List<Part> design(Kind kind, SeasonDecoService.Theme t) {
        List<Part> p = new ArrayList<>();
        switch (kind) {
            case MOOSLING -> {
                String body = themed(t, "moss_block", "snow_block", "orange_wool", "jack_o_lantern", "flowering_azalea_leaves[persistent=true]");
                p.add(new Part(body, 0, 0, 0, 0.62f, 0.5f, 0.62f, Role.BODY, false));
                p.add(new Part("azalea_leaves[persistent=true]", -0.24f, 0.46f, 0, 0.12f, 0.26f, 0.2f, Role.EAR, false));
                p.add(new Part("azalea_leaves[persistent=true]", 0.24f, 0.46f, 0, 0.12f, 0.26f, 0.2f, Role.EAR, false));
                p.add(new Part("white_concrete", -0.14f, 0.24f, 0.31f, 0.13f, 0.13f, 0.02f, Role.EYE, false));
                p.add(new Part("white_concrete", 0.14f, 0.24f, 0.31f, 0.13f, 0.13f, 0.02f, Role.EYE, false));
                p.add(new Part("black_concrete", -0.14f, 0.25f, 0.325f, 0.07f, 0.08f, 0.02f, Role.EYE, false));
                p.add(new Part("black_concrete", 0.14f, 0.25f, 0.325f, 0.07f, 0.08f, 0.02f, Role.EYE, false));
                p.add(new Part(themed(t, "red_mushroom_block", "red_wool", "brown_mushroom_block", "orange_concrete", "pink_wool"),
                        0.08f, 0.5f, -0.05f, 0.14f, 0.1f, 0.14f, Role.BODY, false));
            }
            case GLIMMERLING -> {
                String body = themed(t, "amethyst_block", "light_blue_stained_glass", "orange_stained_glass", "purple_stained_glass", "pink_stained_glass");
                p.add(new Part(body, 0, 0, 0, 0.4f, 0.4f, 0.4f, Role.BODY, false));
                p.add(new Part("sea_lantern", 0, 0.12f, 0, 0.18f, 0.18f, 0.18f, Role.GLOW, true));
                p.add(new Part("white_stained_glass", -0.36f, 0.16f, -0.04f, 0.34f, 0.02f, 0.26f, Role.WING_L, false));
                p.add(new Part("white_stained_glass", 0.36f, 0.16f, -0.04f, 0.34f, 0.02f, 0.26f, Role.WING_R, false));
                p.add(new Part("black_concrete", -0.09f, 0.22f, 0.205f, 0.06f, 0.07f, 0.02f, Role.EYE, false));
                p.add(new Part("black_concrete", 0.09f, 0.22f, 0.205f, 0.06f, 0.07f, 0.02f, Role.EYE, false));
            }
            case STAR_JELLY -> {
                String bell = themed(t, "light_blue_stained_glass", "white_stained_glass", "orange_stained_glass", "lime_stained_glass", "magenta_stained_glass");
                p.add(new Part(bell, 0, 0.55f, 0, 0.95f, 0.5f, 0.95f, Role.BODY, false));
                p.add(new Part(bell, 0, 1.05f, 0, 0.6f, 0.18f, 0.6f, Role.BODY, false));
                p.add(new Part("sea_lantern", 0, 0.68f, 0, 0.4f, 0.3f, 0.4f, Role.GLOW, true));
                float[][] t4 = {{-0.28f, -0.28f}, {0.28f, -0.28f}, {-0.28f, 0.28f}, {0.28f, 0.28f}, {0, 0}};
                for (float[] o : t4) {
                    p.add(new Part("end_rod[facing=up]", o[0], -0.35f, o[1], 0.5f, 0.9f, 0.5f, Role.TENTACLE, true));
                }
            }
            case CRYSTAL_GOLEM -> {
                String stone = themed(t, "calcite", "packed_ice", "smooth_sandstone", "polished_blackstone", "white_terracotta");
                String crystal = themed(t, "amethyst_block", "blue_ice", "orange_stained_glass", "purple_stained_glass", "pink_stained_glass");
                p.add(new Part(stone, -0.2f, 0, 0, 0.26f, 0.7f, 0.3f, Role.BODY, false));
                p.add(new Part(stone, 0.2f, 0, 0, 0.26f, 0.7f, 0.3f, Role.BODY, false));
                p.add(new Part(stone, 0, 0.7f, 0, 0.9f, 0.75f, 0.55f, Role.BODY, false));
                p.add(new Part(crystal, 0, 0.85f, 0.2f, 0.36f, 0.36f, 0.2f, Role.GLOW, true));
                p.add(new Part(stone, 0, 1.45f, 0, 0.5f, 0.42f, 0.45f, Role.BODY, false));
                p.add(new Part("sea_lantern", -0.11f, 1.62f, 0.225f, 0.1f, 0.06f, 0.02f, Role.GLOW, true));
                p.add(new Part("sea_lantern", 0.11f, 1.62f, 0.225f, 0.1f, 0.06f, 0.02f, Role.GLOW, true));
                p.add(new Part(crystal, -0.12f, 1.87f, 0, 0.12f, 0.3f, 0.12f, Role.BODY, false));
                p.add(new Part(crystal, 0.1f, 1.87f, -0.05f, 0.1f, 0.22f, 0.1f, Role.BODY, false));
                p.add(new Part(stone, -0.58f, 0.6f, 0, 0.24f, 0.8f, 0.26f, Role.ARM_L, false));
                p.add(new Part(stone, 0.58f, 0.6f, 0, 0.24f, 0.8f, 0.26f, Role.ARM_R, false));
            }
            case SPARK_FOX -> {
                String fur = themed(t, "orange_concrete", "white_concrete", "red_concrete", "black_concrete", "pink_concrete");
                p.add(new Part(fur, 0, 0.2f, 0, 0.36f, 0.3f, 0.7f, Role.BODY, false));
                for (float[] leg : new float[][]{{-0.12f, 0.24f}, {0.12f, 0.24f}, {-0.12f, -0.24f}, {0.12f, -0.24f}}) {
                    p.add(new Part("black_concrete", leg[0], 0, leg[1], 0.08f, 0.2f, 0.08f, Role.BODY, false));
                }
                p.add(new Part(fur, 0, 0.36f, 0.4f, 0.34f, 0.3f, 0.3f, Role.BODY, false));
                p.add(new Part("white_concrete", 0, 0.37f, 0.58f, 0.16f, 0.12f, 0.12f, Role.BODY, false));
                p.add(new Part("black_concrete", 0, 0.45f, 0.64f, 0.06f, 0.05f, 0.02f, Role.EYE, false));
                p.add(new Part(fur, -0.11f, 0.66f, 0.42f, 0.08f, 0.14f, 0.06f, Role.EAR, false));
                p.add(new Part(fur, 0.11f, 0.66f, 0.42f, 0.08f, 0.14f, 0.06f, Role.EAR, false));
                p.add(new Part("black_concrete", -0.08f, 0.48f, 0.555f, 0.05f, 0.05f, 0.02f, Role.EYE, false));
                p.add(new Part("black_concrete", 0.08f, 0.48f, 0.555f, 0.05f, 0.05f, 0.02f, Role.EYE, false));
                p.add(new Part(fur, 0, 0.3f, -0.55f, 0.2f, 0.2f, 0.4f, Role.TAIL, false));
                p.add(new Part("shroomlight", 0, 0.32f, -0.8f, 0.16f, 0.16f, 0.14f, Role.TAIL, true));
            }
            case CLOUD_WHALE -> {
                String skin = themed(t, "light_blue_wool", "white_wool", "orange_wool", "purple_wool", "pink_wool");
                p.add(new Part(skin, 0, 0, 0, 3.2f, 2.2f, 7.5f, Role.BODY, false));
                p.add(new Part("white_wool", 0, -0.15f, 0.5f, 2.8f, 0.5f, 6.2f, Role.BODY, false));
                p.add(new Part(skin, 0, 0.3f, -4.4f, 1.6f, 1.2f, 1.8f, Role.TAIL, false));
                p.add(new Part(skin, 0, 0.6f, -5.6f, 3.6f, 0.3f, 1.2f, Role.TAIL, false));
                p.add(new Part(skin, -2.2f, 0.4f, 1.0f, 1.6f, 0.25f, 1.2f, Role.FIN, false));
                p.add(new Part(skin, 2.2f, 0.4f, 1.0f, 1.6f, 0.25f, 1.2f, Role.FIN, false));
                p.add(new Part("black_concrete", -1.62f, 1.1f, 2.6f, 0.05f, 0.35f, 0.35f, Role.EYE, false));
                p.add(new Part("black_concrete", 1.62f, 1.1f, 2.6f, 0.05f, 0.35f, 0.35f, Role.EYE, false));
                p.add(new Part("white_stained_glass", 0, 2.2f, 1.6f, 0.4f, 0.5f, 0.4f, Role.GLOW, false));
            }
            case LANTERN_SHROOM -> {
                String cap = themed(t, "red_mushroom_block", "white_concrete", "brown_mushroom_block", "orange_concrete", "pink_concrete");
                p.add(new Part("mushroom_stem", 0, 0, 0, 0.32f, 0.62f, 0.32f, Role.BODY, false));
                p.add(new Part(cap, 0, 0.6f, 0, 0.86f, 0.28f, 0.86f, Role.BODY, false));
                p.add(new Part(cap, 0, 0.86f, 0, 0.52f, 0.16f, 0.52f, Role.BODY, false));
                p.add(new Part("shroomlight", 0, 0.5f, 0, 0.5f, 0.1f, 0.5f, Role.GLOW, true));
                p.add(new Part("black_concrete", -0.07f, 0.36f, 0.165f, 0.05f, 0.07f, 0.02f, Role.EYE, false));
                p.add(new Part("black_concrete", 0.07f, 0.36f, 0.165f, 0.05f, 0.07f, 0.02f, Role.EYE, false));
            }
        }
        return p;
    }

    // ---- life ---------------------------------------------------------------------------------------------

    private List<Location> meadow() {
        return plugin.attractionStore().all("ground");
    }

    private void spawnAll() {
        List<Location> spots = meadow();
        if (spots.isEmpty()) {
            return;
        }
        SeasonDecoService.Theme theme = plugin.attractions().deco().theme();
        builtFor = theme;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Kind kind : Kind.values()) {
            int n = kind == Kind.CLOUD_WHALE ? 1 : kind == Kind.STAR_JELLY || kind == Kind.LANTERN_SHROOM || kind == Kind.CRYSTAL_GOLEM
                    ? Math.max(1, perKind - 1) : perKind;
            for (int i = 0; i < n; i++) {
                Location at = spots.get(rnd.nextInt(spots.size())).clone().add(0.5, 0, 0.5);
                if (!at.isChunkLoaded()) {
                    continue;
                }
                Creature c = new Creature(kind);
                c.pos = at;
                c.yaw = rnd.nextFloat() * 360;
                c.age = rnd.nextInt(100);
                c.design.addAll(design(kind, theme));
                World w = at.getWorld();
                Location base = body(c);
                for (Part part : c.design) {
                    BlockData data = Bukkit.createBlockData(part.material().contains(":") ? part.material() : "minecraft:" + part.material());
                    BlockDisplay d = w.spawn(base, BlockDisplay.class, e -> {
                        e.setBlock(data);
                        e.setPersistent(false);
                        e.setTeleportDuration(3);
                        e.setInterpolationDuration(6);
                        e.setViewRange(0.6f);
                        if (part.glow()) {
                            e.setBrightness(new Display.Brightness(15, 15));
                        }
                        e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG);
                    });
                    d.setTransformation(transform(part, c, 0));
                    c.parts.add(d);
                }
                if (kind == Kind.CLOUD_WHALE) {
                    for (BlockDisplay d : c.parts) {
                        d.setViewRange(4f);
                        d.setTeleportDuration(6);
                    }
                    creatures.add(c);
                    continue;
                }
                c.hitbox = w.spawn(base, Interaction.class, e -> {
                    e.setInteractionWidth(kind.width);
                    e.setInteractionHeight(kind.height);
                    e.setPersistent(false);
                    e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, TAG + ":" + kind.name());
                });
                creatures.add(c);
            }
        }
    }

    /** Where the body is right now (incl. hovering / hopping). */
    private Location body(Creature c) {
        double y = c.kind.hover;
        switch (c.kind) {
            case MOOSLING -> y += Math.max(0, Math.sin(c.age * 0.45)) * 0.45;
            case GLIMMERLING -> y += Math.sin(c.age * 0.15) * 0.25;
            case STAR_JELLY -> y += Math.sin(c.age * 0.08) * 0.5;
            default -> {
            }
        }
        Location l = c.pos.clone().add(0, y, 0);
        l.setYaw(c.yaw);
        l.setPitch(0);
        return l;
    }

    private static Transformation transform(Part p, Creature c, int age) {
        float sx = p.sx();
        float sy = p.sy();
        float sz = p.sz();
        Quaternionf rot = new Quaternionf();
        float ox = p.x();
        float oy = p.y();
        float oz = p.z();
        switch (p.role()) {
            case WING_L, WING_R -> {
                float flap = (float) Math.sin(age * 1.3) * 0.7f;
                rot = new Quaternionf(new AxisAngle4f(p.role() == Role.WING_L ? flap : -flap, 0, 0, 1));
            }
            case TENTACLE -> {
                float sway = (float) Math.sin(age * 0.2 + ox * 5 + oz * 3) * 0.25f;
                rot = new Quaternionf(new AxisAngle4f(sway, 1, 0, 0.4f));
            }
            case EAR -> rot = new Quaternionf(new AxisAngle4f((float) Math.sin(age * 0.3) * 0.2f * (ox < 0 ? 1 : -1), 0, 0, 1));
            case TAIL -> rot = new Quaternionf(new AxisAngle4f((float) Math.sin(age * (c.kind == Kind.CLOUD_WHALE ? 0.08 : 0.6)) * 0.35f,
                    c.kind == Kind.CLOUD_WHALE ? 1 : 0, c.kind == Kind.CLOUD_WHALE ? 0 : 1, 0));
            case ARM_L, ARM_R -> {
                float swing = (float) Math.sin(age * 0.35) * 0.45f * (p.role() == Role.ARM_L ? 1 : -1);
                rot = new Quaternionf(new AxisAngle4f(swing, 1, 0, 0));
                oy -= 0; // arms hang from the shoulder: rotate around the top
                Vector3f top = new Vector3f(0, sy, 0);
                Vector3f moved = new Vector3f(top).rotate(rot);
                ox += 0;
                oy += top.y - moved.y;
                oz += top.z - moved.z;
            }
            case FIN -> rot = new Quaternionf(new AxisAngle4f((float) Math.sin(age * 0.07) * 0.35f * (ox < 0 ? 1 : -1), 0, 0, 1));
            case BODY -> {
                if (c.kind == Kind.MOOSLING && p.sy() > 0.3f) {
                    // squash when landing, stretch while jumping
                    float s = (float) Math.sin(age * 0.45);
                    float squash = s < 0 ? 1 + s * 0.15f : 1 + s * 0.08f;
                    sy *= squash;
                    sx /= Math.sqrt(squash);
                    sz /= Math.sqrt(squash);
                }
            }
            default -> {
            }
        }
        // block displays are anchored at their corner: shift so (x, y, z) is the centre bottom
        Vector3f translation = new Vector3f(ox, oy, oz).add(new Vector3f(-sx / 2, 0, -sz / 2).rotate(rot));
        return new Transformation(translation, rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private void tick() {
        if (!enabled || perKind <= 0) {
            if (!creatures.isEmpty()) {
                despawnAll();
            }
            return;
        }
        Location center = plugin.attractionStore().center();
        if (center == null || !center.isChunkLoaded()) {
            return;
        }
        if (builtFor != null && builtFor != plugin.attractions().deco().theme()) {
            despawnAll(); // new season – new look
        }
        creatures.removeIf(c -> c.parts.isEmpty() || !c.parts.get(0).isValid());
        if (creatures.isEmpty()) {
            spawnAll();
            return;
        }
        List<Location> spots = meadow();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Creature c : creatures) {
            c.age++;
            if (c.kind == Kind.CLOUD_WHALE) {
                double a = c.age * 0.0045;
                double radius = Math.min(80, plugin.attractionStore().radius() * 0.6);
                c.pos = center.clone().add(Math.cos(a) * radius, 30 + Math.sin(c.age * 0.02) * 2, Math.sin(a) * radius);
                c.yaw = (float) Math.toDegrees(Math.atan2(Math.sin(a), Math.cos(a))) + 0f;
                Location wb = c.pos.clone();
                wb.setYaw(c.yaw);
                for (int i = 0; i < c.parts.size(); i++) {
                    c.parts.get(i).teleport(wb);
                    c.parts.get(i).setInterpolationDelay(0);
                    c.parts.get(i).setTransformation(transform(c.design.get(i), c, c.age));
                }
                if (c.age % 40 == 0) {
                    // blowing a little fountain
                    wb.getWorld().spawnParticle(Particle.SPLASH, wb.clone().add(0, 2.6, 0), 40, 0.2, 0.6, 0.2, 0.1, null, true);
                    wb.getWorld().spawnParticle(Particle.CLOUD, wb.clone().add(0, 2.6, 0), 6, 0.3, 0.3, 0.3, 0.02, null, true);
                }
                continue;
            }
            if (!c.pos.isChunkLoaded()) {
                continue;
            }
            if (c.pause > 0) {
                c.pause--;
            } else {
                if (c.target == null || c.target.distanceSquared(c.pos) < 0.5) {
                    if (c.target != null && rnd.nextInt(3) == 0) {
                        c.pause = 10 + rnd.nextInt(30);
                    }
                    c.target = pickTarget(spots, c.pos, rnd);
                }
                if (c.target != null) {
                    double dx = c.target.getX() - c.pos.getX();
                    double dz = c.target.getZ() - c.pos.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    double step = Math.min(len, c.kind.speed * 3);
                    float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float diff = ((want - c.yaw + 540) % 360) - 180;
                    c.yaw += Math.max(-25, Math.min(25, diff));
                    if (len > 0.01 && (c.kind != Kind.MOOSLING || Math.sin(c.age * 0.45) > 0)) {
                        c.pos.add(dx / len * step, 0, dz / len * step);
                        c.pos.setY(c.target.getY());
                    }
                }
            }
            Location b = body(c);
            for (int i = 0; i < c.parts.size(); i++) {
                BlockDisplay d = c.parts.get(i);
                d.teleport(b);
                Part part = c.design.get(i);
                if (part.role() != Role.EYE && part.role() != Role.GLOW || c.kind == Kind.MOOSLING) {
                    d.setInterpolationDelay(0);
                    d.setTransformation(transform(part, c, c.age));
                }
            }
            c.hitbox.teleport(c.pos.clone().add(0, c.kind.hover, 0));
            if (c.kind == Kind.GLIMMERLING && c.age % 4 == 0) {
                b.getWorld().spawnParticle(Particle.END_ROD, b.clone().add(0, 0.2, 0), 1, 0.15, 0.1, 0.15, 0.005);
            } else if (c.kind == Kind.STAR_JELLY && c.age % 6 == 0) {
                b.getWorld().spawnParticle(Particle.GLOW, b.clone().add(0, 0.4, 0), 1, 0.3, 0.2, 0.3, 0);
            } else if (c.kind == Kind.SPARK_FOX && c.age % 2 == 0) {
                Location tail = b.clone().add(new org.bukkit.util.Vector(0, 0.4, -0.85).rotateAroundY(-Math.toRadians(c.yaw)));
                b.getWorld().spawnParticle(Particle.FLAME, tail, 1, 0.05, 0.05, 0.05, 0.005);
            } else if (c.kind == Kind.CRYSTAL_GOLEM && c.age % 8 == 0) {
                b.getWorld().spawnParticle(Particle.END_ROD, b.clone().add(0, 1.9, 0), 1, 0.15, 0.1, 0.15, 0.003);
            } else if (c.kind == Kind.LANTERN_SHROOM && c.age % 10 == 0) {
                b.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, b.clone().add(0, 0.9, 0), 1, 0.3, 0.1, 0.3, 0);
            }
        }
    }

    private static Location pickTarget(List<Location> spots, Location from, ThreadLocalRandom rnd) {
        if (spots.isEmpty()) {
            return null;
        }
        for (int i = 0; i < 8; i++) {
            Location s = spots.get(rnd.nextInt(spots.size()));
            if (s.getWorld().equals(from.getWorld()) && s.distanceSquared(from) < 22 * 22) {
                return s.clone().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    @EventHandler
    public void onPet(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
        if (tag == null || !tag.startsWith(TAG + ":")) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        Kind kind = Kind.valueOf(tag.substring(TAG.length() + 1));
        Location at = e.getLocation().add(0, kind.height, 0);
        at.getWorld().spawnParticle(Particle.HEART, at, 4, 0.3, 0.2, 0.3, 0);
        String sound = switch (kind) {
            case MOOSLING -> "entity.slime.squish_small";
            case GLIMMERLING -> "block.amethyst_block.chime";
            case STAR_JELLY -> "entity.allay.ambient_without_item";
            case LANTERN_SHROOM -> "entity.mooshroom.convert";
            case CRYSTAL_GOLEM -> "block.amethyst_cluster.hit";
            case SPARK_FOX -> "entity.fox.ambient";
            case CLOUD_WHALE -> "entity.dolphin.ambient";
        };
        SoundSpec.of(sound, 1f, 1.4f).playAt(at);
        plugin.messages().actionBar(p, "attractions.creatures.pet", "creature",
                plugin.messages().get("attractions.creatures.names." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        long now = System.currentTimeMillis();
        if (petCoins > 0 && petCooldown.getOrDefault(p.getUniqueId(), 0L) < now) {
            petCooldown.put(p.getUniqueId(), now + 5 * 60_000L);
            PlayerData data = plugin.playerData().get(p);
            if (data != null) {
                plugin.economy().earn(data, Currency.COINS, petCoins, false);
            }
        }
    }

    public int count() {
        return creatures.size();
    }

    public void despawnAll() {
        for (Creature c : creatures) {
            c.parts.forEach(Entity::remove);
            if (c.hitbox != null) {
                c.hitbox.remove();
            }
        }
        creatures.clear();
        builtFor = null;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
                if (tag != null && tag.startsWith(TAG)) {
                    e.remove();
                }
            }
        }
    }
}
