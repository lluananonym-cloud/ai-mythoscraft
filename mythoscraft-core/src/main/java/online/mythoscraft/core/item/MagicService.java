package online.mythoscraft.core.item;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mythos magic: the Mythos wand casts spells with mana (regenerates by itself). Right click = cast, sneak + right
 * click = next spell. Spells are learned from spell scrolls; mana potions refill mana. Spells only ever hurt monsters,
 * never players (no PvP).
 */
public final class MagicService implements Listener {

    public enum Spell {
        STAR_JUMP(20), ARCANE_BOLT(15), FROST_RING(30), HEALING_SPRING(35), FEATHER_LIFT(25), MYTHOS_SHIELD(40), CHAIN_LIGHTNING(45);

        final int mana;

        Spell(int mana) {
            this.mana = mana;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private static final Color ARCANE = Color.fromRGB(0xa855f7);

    private final MythosCraft plugin;
    private final Map<UUID, Double> mana = new HashMap<>();
    private final Map<UUID, Long> lastCast = new HashMap<>();
    private final Set<UUID> safeFall = new HashSet<>();
    private int maxMana = 100;
    private double regenPerSecond = 3;
    private double damageFactor = 1;

    public MagicService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("magic");
        maxMana = cfg.integer("max-mana", 100, 10, 10_000);
        regenPerSecond = cfg.decimal("mana-per-second", 3, 0, 1000);
        damageFactor = cfg.decimal("damage-factor", 1, 0.1, 10);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 10, "mana", this::regen);
    }

    // ---- knowledge & mana ---------------------------------------------------------------------------------

    public List<Spell> known(Player p) {
        long bits = plugin.progress().get(p, "magic.known");
        if (bits == 0) {
            bits = (1L << Spell.STAR_JUMP.ordinal()) | (1L << Spell.ARCANE_BOLT.ordinal());
            plugin.progress().set(p, "magic.known", bits);
        }
        List<Spell> out = new ArrayList<>();
        for (Spell s : Spell.values()) {
            if ((bits & (1L << s.ordinal())) != 0) {
                out.add(s);
            }
        }
        return out;
    }

    /** Spell by id ("arkanpfeil", "arcane-bolt", "ARCANE_BOLT" …). */
    public Spell spell(String id) {
        String n = id.toLowerCase(Locale.ROOT).replace('_', '-');
        for (Spell s : Spell.values()) {
            if (s.key().equals(n) || plugin.messages().raw("magic.spells." + s.key() + ".id").equalsIgnoreCase(n)) {
                return s;
            }
        }
        return null;
    }

    public List<String> spellIds() {
        List<String> out = new ArrayList<>();
        for (Spell s : Spell.values()) {
            String id = plugin.messages().raw("magic.spells." + s.key() + ".id");
            out.add(id.startsWith("<") ? s.key() : id);
        }
        return out;
    }

    /** Teaches or removes a spell (the two starter spells always stay). */
    public void setKnown(Player p, Spell s, boolean known) {
        long bits = plugin.progress().get(p, "magic.known");
        known(p);
        bits = plugin.progress().get(p, "magic.known");
        bits = known ? bits | (1L << s.ordinal()) : bits & ~(1L << s.ordinal());
        bits |= (1L << Spell.STAR_JUMP.ordinal()) | (1L << Spell.ARCANE_BOLT.ordinal());
        plugin.progress().set(p, "magic.known", bits);
    }

    private Spell selected(Player p) {
        List<Spell> known = known(p);
        long idx = plugin.progress().get(p, "magic.selected");
        for (Spell s : known) {
            if (s.ordinal() == idx) {
                return s;
            }
        }
        return known.get(0);
    }

    public double mana(Player p) {
        return mana.getOrDefault(p.getUniqueId(), (double) max(p));
    }

    /** Max mana incl. the rank bonus. */
    public int max(Player p) {
        return maxMana + plugin.rankPerks().extraMana(p);
    }

    private void regen() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            double m = mana(p);
            if (m < max(p)) {
                mana.put(p.getUniqueId(), Math.min(max(p), m + regenPerSecond / 2));
            }
            if (plugin.items().is(p.getInventory().getItemInMainHand(), CustomItem.MYTHOS_WAND) && plugin.progress().loaded(p)) {
                showBar(p);
            }
        }
    }

    private void showBar(Player p) {
        int full = Math.min(10, (int) Math.round(mana(p) / max(p) * 10));
        String bar = "<#a855f7>" + "▮".repeat(full) + "<dark_gray>" + "▮".repeat(10 - full);
        Spell s = selected(p);
        plugin.messages().actionBar(p, "magic.bar", "bar", online.mythoscraft.core.util.Text.parse(bar), "mana", (int) mana(p), "max", max(p),
                "spell", plugin.messages().get("magic.spells." + s.key() + ".name"), "cost", s.mana);
    }

    // ---- items --------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        CustomItem type = plugin.items().identify(item);
        if (type == null) {
            return;
        }
        Player p = event.getPlayer();
        switch (type) {
            case MYTHOS_WAND -> {
                event.setCancelled(true);
                if (!plugin.progress().loaded(p)) {
                    return;
                }
                if (p.isSneaking()) {
                    cycle(p);
                } else {
                    cast(p, selected(p));
                }
            }
            case SPELL_SCROLL -> {
                event.setCancelled(true);
                List<Spell> unknown = new ArrayList<>(List.of(Spell.values()));
                unknown.removeAll(known(p));
                if (unknown.isEmpty()) {
                    plugin.messages().send(p, "magic.know-all");
                    return;
                }
                AbilitySupport.consumeOne(item);
                Spell s = unknown.get(ThreadLocalRandom.current().nextInt(unknown.size()));
                plugin.progress().set(p, "magic.known", plugin.progress().get(p, "magic.known") | (1L << s.ordinal()));
                plugin.progress().set(p, "magic.selected", s.ordinal());
                plugin.messages().send(p, "magic.learned", "spell", plugin.messages().get("magic.spells." + s.key() + ".name"),
                        "desc", plugin.messages().get("magic.spells." + s.key() + ".desc"));
                p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 80, 0.5, 0.8, 0.5, 1);
                SoundSpec.of("block.enchantment_table.use", 1f, 1.2f).play(p);
            }
            case MANA_ELIXIR -> {
                event.setCancelled(true);
                AbilitySupport.consumeOne(item);
                mana.put(p.getUniqueId(), Math.min(max(p), mana(p) + max(p) * 0.6));
                SoundSpec.of("entity.generic.drink", 1f, 1.3f).play(p);
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0, new Particle.DustOptions(ARCANE, 1.2f));
                showBar(p);
            }
            default -> {
            }
        }
    }

    private void cycle(Player p) {
        List<Spell> known = known(p);
        int i = known.indexOf(selected(p));
        Spell next = known.get((i + 1) % known.size());
        plugin.progress().set(p, "magic.selected", next.ordinal());
        SoundSpec.of("block.note_block.chime", 0.8f, 1.2f + 0.1f * (i % 5)).play(p);
        showBar(p);
    }

    public void cast(Player p, Spell s) {
        long now = System.currentTimeMillis();
        if (now - lastCast.getOrDefault(p.getUniqueId(), 0L) < 600) {
            return;
        }
        if (plugin.arenas().isInArena(p) || plugin.tournaments().isParticipant(p)) {
            plugin.messages().actionBar(p, "magic.not-here");
            return;
        }
        if (mana(p) < s.mana) {
            plugin.messages().actionBar(p, "magic.no-mana", "cost", s.mana);
            SoundSpec.of("block.fire.extinguish", 0.6f, 1.6f).play(p);
            return;
        }
        boolean done = switch (s) {
            case STAR_JUMP -> starJump(p);
            case ARCANE_BOLT -> arcaneBolt(p);
            case FROST_RING -> frostRing(p);
            case HEALING_SPRING -> healingSpring(p);
            case FEATHER_LIFT -> featherLift(p);
            case MYTHOS_SHIELD -> shield(p);
            case CHAIN_LIGHTNING -> chainLightning(p);
        };
        if (done) {
            lastCast.put(p.getUniqueId(), now);
            mana.put(p.getUniqueId(), mana(p) - s.mana);
            showBar(p);
        }
    }

    private static boolean hittable(Entity e) {
        return e instanceof Enemy && e instanceof LivingEntity le && !le.isDead() && !(e instanceof Player);
    }

    private void hurt(Player caster, LivingEntity target, double amount) {
        target.damage(amount * damageFactor, caster);
    }

    // ---- spells -------------------------------------------------------------------------------------------

    /** Teleports up to 10 blocks in the looking direction, leaving a trail of stars. */
    private boolean starJump(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        // aimed at a creature or player: swap places with it
        RayTraceResult aim = p.getWorld().rayTrace(eye, dir, 16, FluidCollisionMode.NEVER, true, 0.4,
                e -> e != p && e instanceof LivingEntity && !(e instanceof org.bukkit.entity.ArmorStand)
                        && !e.getPersistentDataContainer().has(online.mythoscraft.core.util.Keys.ENTITY, org.bukkit.persistence.PersistentDataType.STRING)
                        && !(e instanceof Player other && (other.getGameMode() == org.bukkit.GameMode.SPECTATOR || plugin.arenas().isInArena(other))));
        if (aim != null && aim.getHitEntity() != null) {
            return swap(p, (LivingEntity) aim.getHitEntity());
        }
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, dir, 10, FluidCollisionMode.NEVER, true);
        double dist = hit == null ? 10 : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 1.2);
        if (dist < 1.5) {
            plugin.messages().actionBar(p, "tools.no-target");
            return false;
        }
        Location to = p.getLocation().add(dir.clone().multiply(dist));
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        if (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable()) {
            to.add(0, 1, 0);
            if (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable()) {
                plugin.messages().actionBar(p, "tools.no-target");
                return false;
            }
        }
        Location from = p.getLocation().add(0, 1, 0);
        for (double d = 0; d < dist; d += 0.6) {
            Location l = from.clone().add(dir.clone().multiply(d));
            p.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0.05, 0.05, 0.05, 0);
        }
        p.teleport(to);
        p.setFallDistance(0);
        safeFall.add(p.getUniqueId());
        plugin.tasks().later(60, "star jump safe", () -> safeFall.remove(p.getUniqueId()));
        p.getWorld().spawnParticle(Particle.FIREWORK, to.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        SoundSpec.of("entity.illusioner.mirror_move", 1f, 1.5f).playAt(to);
        return true;
    }

    /** Star jump on a target: both swap places (not at the spawn – except for admins). */
    private boolean swap(Player p, LivingEntity other) {
        boolean admin = p.hasPermission("mythos.admin");
        if (!admin && (plugin.attractionStore().inside(p.getLocation(), 0) || plugin.attractionStore().inside(other.getLocation(), 0))) {
            plugin.messages().actionBar(p, "magic.swap-not-at-spawn");
            return false;
        }
        Location a = p.getLocation();
        Location b = other.getLocation();
        Location toP = b.clone();
        toP.setYaw(a.getYaw());
        toP.setPitch(a.getPitch());
        Location toO = a.clone();
        toO.setYaw(b.getYaw());
        toO.setPitch(b.getPitch());
        for (double t = 0; t <= 1; t += 0.05) {
            Location l = a.clone().add(0, 1, 0).add(b.clone().subtract(a).toVector().multiply(t));
            p.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0.05, 0.05, 0.05, 0);
        }
        p.teleport(toP);
        other.teleport(toO);
        p.setFallDistance(0);
        other.setFallDistance(0);
        safeFall.add(p.getUniqueId());
        plugin.tasks().later(60, "star jump safe", () -> safeFall.remove(p.getUniqueId()));
        p.getWorld().spawnParticle(Particle.FIREWORK, toP.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        p.getWorld().spawnParticle(Particle.FIREWORK, toO.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        SoundSpec.of("entity.illusioner.mirror_move", 1f, 1.2f).playAt(toP);
        SoundSpec.of("entity.illusioner.mirror_move", 1f, 1.2f).playAt(toO);
        if (other instanceof Player op) {
            plugin.messages().actionBar(op, "magic.swapped-by", "player", p.getName());
        }
        return true;
    }

    /** A homing arcane missile – flies 30 blocks, curves towards the next monster. */
    private boolean arcaneBolt(Player p) {
        Location pos = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.8));
        Vector[] vel = {p.getEyeLocation().getDirection().multiply(0.9)};
        World w = p.getWorld();
        SoundSpec.of("entity.evoker.cast_spell", 1f, 1.6f).playAt(pos);
        for (int i = 0; i < 34; i++) {
            final int step = i;
            plugin.tasks().later(i, "arcane bolt", () -> {
                if (vel[0] == null) {
                    return;
                }
                LivingEntity near = null;
                double best = 4 * 4;
                for (Entity e : w.getNearbyEntities(pos, 4, 4, 4)) {
                    if (hittable(e) && e.getLocation().add(0, 1, 0).distanceSquared(pos) < best) {
                        best = e.getLocation().add(0, 1, 0).distanceSquared(pos);
                        near = (LivingEntity) e;
                    }
                }
                if (near != null) {
                    Vector want = near.getLocation().add(0, near.getHeight() / 2, 0).toVector().subtract(pos.toVector()).normalize().multiply(0.9);
                    vel[0] = vel[0].multiply(0.6).add(want.multiply(0.4));
                }
                pos.add(vel[0]);
                w.spawnParticle(Particle.DUST, pos, 3, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(ARCANE, 1.3f));
                if (step % 2 == 0) {
                    w.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0);
                }
                if (!pos.getBlock().isPassable()) {
                    w.spawnParticle(Particle.WITCH, pos, 10, 0.2, 0.2, 0.2, 0);
                    vel[0] = null;
                    return;
                }
                for (Entity e : w.getNearbyEntities(pos, 0.7, 0.9, 0.7)) {
                    if (hittable(e)) {
                        hurt(p, (LivingEntity) e, 7);
                        w.spawnParticle(Particle.DUST, pos, 25, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(ARCANE, 1.6f));
                        SoundSpec.of("entity.illusioner.cast_spell", 1f, 1.8f).playAt(pos);
                        vel[0] = null;
                        return;
                    }
                }
            });
        }
        return true;
    }

    /** Ice ring: monsters around slow down, freeze and take damage. */
    private boolean frostRing(Player p) {
        Location c = p.getLocation();
        for (int ring = 1; ring <= 6; ring++) {
            int r = ring;
            plugin.tasks().later(ring * 2L, "frost ring", () -> {
                for (int a = 0; a < 360; a += 10) {
                    double rad = Math.toRadians(a);
                    c.getWorld().spawnParticle(Particle.SNOWFLAKE, c.clone().add(Math.cos(rad) * r, 0.3, Math.sin(rad) * r), 1, 0, 0, 0, 0);
                }
            });
        }
        SoundSpec.of("block.glass.break", 1f, 0.7f).playAt(c);
        for (Entity e : p.getNearbyEntities(6, 3, 6)) {
            if (hittable(e)) {
                LivingEntity le = (LivingEntity) e;
                hurt(p, le, 4);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3));
                le.setFreezeTicks(Math.max(le.getFreezeTicks(), 200));
            }
        }
        return true;
    }

    /** A spring of light: heals you and the players around you over 5 seconds. */
    private boolean healingSpring(Player p) {
        Location c = p.getLocation();
        for (Player other : p.getWorld().getPlayers()) {
            if (other.getLocation().distanceSquared(c) <= 36) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
            }
        }
        for (int i = 0; i < 5; i++) {
            plugin.tasks().later(i * 20L, "healing spring", () -> {
                c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 0.3, 0), 30, 3, 0.2, 3, 0);
                c.getWorld().spawnParticle(Particle.HEART, c.clone().add(0, 1.5, 0), 3, 1.5, 0.5, 1.5, 0);
            });
        }
        SoundSpec.of("block.beacon.power_select", 1f, 1.7f).playAt(c);
        return true;
    }

    /** Lifts you up gently, then lets you float down like a feather. */
    private boolean featherLift(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 30, 0.4, 0.1, 0.4, 0.05);
        SoundSpec.of("entity.phantom.flap", 1f, 1.4f).playAt(p.getLocation());
        return true;
    }

    /** A shimmering shield: extra hearts and less damage for a while. */
    private boolean shield(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 0));
        Location c = p.getLocation().add(0, 1, 0);
        for (int i = 0; i < 40; i++) {
            double a = Math.toRadians(i * 9);
            double y = Math.sin(a * 2) * 0.8;
            p.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 1.2, y, Math.sin(a) * 1.2), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(0xfde047), 1.1f));
        }
        SoundSpec.of("item.armor.equip_netherite", 1f, 1.4f).play(p);
        return true;
    }

    /** Lightning that jumps from monster to monster (up to 4). */
    private boolean chainLightning(Player p) {
        List<LivingEntity> chain = new ArrayList<>();
        Location from = p.getEyeLocation();
        LivingEntity current = null;
        double range = 12;
        for (int i = 0; i < 4; i++) {
            LivingEntity next = null;
            double best = range * range;
            Location origin = current == null ? p.getLocation() : current.getLocation();
            for (Entity e : origin.getWorld().getNearbyEntities(origin, range, 6, range)) {
                if (hittable(e) && !chain.contains(e) && e.getLocation().distanceSquared(origin) < best) {
                    best = e.getLocation().distanceSquared(origin);
                    next = (LivingEntity) e;
                }
            }
            if (next == null) {
                break;
            }
            chain.add(next);
            current = next;
            range = 7;
        }
        if (chain.isEmpty()) {
            plugin.messages().actionBar(p, "magic.no-monster");
            return false;
        }
        Location last = from;
        for (int i = 0; i < chain.size(); i++) {
            LivingEntity e = chain.get(i);
            Location to = e.getLocation().add(0, e.getHeight() / 2, 0);
            Location a = last.clone();
            plugin.tasks().later(i * 3L, "chain lightning", () -> {
                Vector d = to.toVector().subtract(a.toVector());
                double len = d.length();
                d.normalize();
                for (double t = 0; t < len; t += 0.4) {
                    Location l = a.clone().add(d.clone().multiply(t)).add(ThreadLocalRandom.current().nextDouble(-0.15, 0.15), 0, 0);
                    l.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0, 0, 0, 0);
                }
                if (e.isValid()) {
                    hurt(p, e, 6);
                    e.getWorld().spawnParticle(Particle.FIREWORK, to, 12, 0.2, 0.3, 0.2, 0.05);
                }
                SoundSpec.of("entity.lightning_bolt.impact", 0.8f, 1.6f).playAt(to);
            });
            last = to;
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && event.getCause() == EntityDamageEvent.DamageCause.FALL && safeFall.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mana.remove(event.getPlayer().getUniqueId());
        lastCast.remove(event.getPlayer().getUniqueId());
        safeFall.remove(event.getPlayer().getUniqueId());
    }
}
