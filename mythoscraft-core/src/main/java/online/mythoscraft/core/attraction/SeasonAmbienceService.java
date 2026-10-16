package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Season mood on the whole map (not only the spawn): particles around every player, a season sky (Halloween dusk,
 * snowfall in winter) and fireworks for New Year's Eve / New Year – with a big show and countdown at midnight.
 */
public final class SeasonAmbienceService {

    private final MythosCraft plugin;
    private final Set<UUID> styled = new HashSet<>();
    private boolean enabled = true;
    private boolean sky = true;
    private int fireworkSeconds = 40;
    private long nextFirework;
    private int lastCountdown = -1;
    private long showDay = -1;

    private int giftMinMinutes = 20;
    private int giftMaxMinutes = 40;
    private int giftGemsMin = 1;
    private int giftGemsMax = 5;
    private long nextGift;

    /** The betting tables on the playground (offsets from the spawn centre, table top). */
    private static final int[][] TABLES = {{-100, 54}, {-84, 54}, {-100, 70}, {-84, 70}};

    public SeasonAmbienceService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.season-ambience");
        enabled = cfg.bool("enabled", true);
        sky = cfg.bool("season-sky", true);
        fireworkSeconds = cfg.integer("firework-every-seconds", 40, 5, 3600);
        showSeconds = cfg.integer("new-year-show-seconds", 180, 10, 3600);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 60, 6, "season ambience", this::particles);
        plugin.tasks().repeat(this, 80, 20, "season sky", this::skyAndFireworks);
        plugin.tasks().repeat(this, 100, 40, "new year's eve rockets", this::eveRockets);
        giftMinMinutes = cfg.integer("silvester-gifts.every-minutes.min", 20, 1, 600);
        giftMaxMinutes = Math.max(giftMinMinutes, cfg.integer("silvester-gifts.every-minutes.max", 40, 1, 600));
        giftGemsMin = cfg.integer("silvester-gifts.gems.min", 1, 0, 1000);
        giftGemsMax = Math.max(giftGemsMin, cfg.integer("silvester-gifts.gems.max", 5, 0, 1000));
        nextGift = 0;
        plugin.tasks().repeat(this, 120, 10, "table fireworks", this::tableFireworks);
        plugin.tasks().repeat(this, 200, 200, "silvester gifts", this::silvesterGifts);
    }

    private SeasonDecoService.Theme theme() {
        return plugin.attractions().deco().theme();
    }

    /** Silvester (Dec 31) and New Year's Day always get fireworks, whatever season runs. */
    private boolean newYearsEve() {
        MonthDay d = MonthDay.from(plugin.multipliers().today());
        return d.equals(MonthDay.of(12, 31)) || d.equals(MonthDay.of(1, 1));
    }

    private void particles() {
        if (!enabled) {
            return;
        }
        SeasonDecoService.Theme t = theme();
        boolean party = t == SeasonDecoService.Theme.NEWYEAR || newYearsEve();
        if (t == SeasonDecoService.Theme.NONE && !party) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.attractionStore().inside(p.getLocation(), 10)) {
                continue; // the spawn has its own (stronger) decoration particles
            }
            Location l = p.getLocation();
            switch (t) {
                case WINTER -> p.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 8, 0), 18, 12, 2, 12, 0.02);
                case SPRING -> p.spawnParticle(Particle.CHERRY_LEAVES, l.clone().add(0, 6, 0), 4, 10, 2, 10, 0);
                case SUMMER -> p.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, l.clone().add(0, 6, 0), 2, 10, 2, 10, 0);
                case AUTUMN -> p.spawnParticle(Particle.TINTED_LEAVES, l.clone().add(0, 7, 0), 6, 10, 2, 10, 0, Color.fromRGB(0xE07A1F));
                case VALENTINE -> {
                    if (rnd.nextInt(4) == 0) {
                        p.spawnParticle(Particle.HEART, l.clone().add(rnd.nextDouble(-8, 8), 2.5, rnd.nextDouble(-8, 8)), 1, 0.2, 0.2, 0.2, 0);
                    }
                }
                case STARS -> p.spawnParticle(Particle.END_ROD, l.clone().add(rnd.nextDouble(-16, 16), 18, rnd.nextDouble(-16, 16)), 1, 0, 0, 0, 0.08);
                case HALLOWEEN -> {
                    for (int i = 0; i < 2; i++) {
                        p.spawnParticle(Particle.WHITE_SMOKE, l.clone().add(rnd.nextDouble(-10, 10), 0.2, rnd.nextDouble(-10, 10)), 2, 0.8, 0.05, 0.8, 0.002);
                    }
                    if (rnd.nextInt(10) == 0) {
                        p.spawnParticle(Particle.SOUL, l.clone().add(rnd.nextDouble(-6, 6), 1.5, rnd.nextDouble(-6, 6)), 1, 0.2, 0.3, 0.2, 0.01);
                    }
                }
                default -> {
                }
            }
            // extra mood everywhere
            switch (t) {
                case STARS, NEWYEAR -> {
                    if (rnd.nextInt(14) == 0) {
                        shootingStar(p);
                    }
                }
                case WINTER -> {
                    if (rnd.nextInt(5) == 0) {
                        p.spawnParticle(Particle.WHITE_ASH, l.clone().add(0, 3, 0), 12, 8, 2, 8, 0);
                    }
                }
                case HALLOWEEN -> {
                    if (rnd.nextInt(90) == 0 && !p.getWorld().isDayTime()) {
                        SoundSpec.of("ambient.cave", 0.6f, 0.8f).play(p);
                    }
                    if (rnd.nextInt(6) == 0) {
                        p.spawnParticle(Particle.DUST, l.clone().add(rnd.nextDouble(-8, 8), rnd.nextDouble(0.5, 3), rnd.nextDouble(-8, 8)), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(rnd.nextBoolean() ? 0xFF7A1A : 0x7C3AED), 1.2f));
                    }
                }
                case SPRING, SUMMER -> {
                    if (rnd.nextInt(6) == 0) {
                        // fluttering "butterflies"
                        p.spawnParticle(Particle.DUST, l.clone().add(rnd.nextDouble(-6, 6), rnd.nextDouble(0.8, 2.5), rnd.nextDouble(-6, 6)), 2, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(new int[]{0xF9A8D4, 0xFDE047, 0x93C5FD, 0xFFFFFF}[rnd.nextInt(4)]), 0.8f));
                    }
                }
                case AUTUMN -> {
                    if (rnd.nextInt(4) == 0) {
                        p.spawnParticle(Particle.TINTED_LEAVES, l.clone().add(rnd.nextDouble(-6, 6), 4, rnd.nextDouble(-6, 6)), 3, 2, 1, 2, 0,
                                Color.fromRGB(rnd.nextBoolean() ? 0xB45309 : 0xFACC15));
                    }
                }
                case VALENTINE -> {
                    if (rnd.nextInt(5) == 0) {
                        p.spawnParticle(Particle.DUST, l.clone().add(rnd.nextDouble(-6, 6), rnd.nextDouble(1, 3), rnd.nextDouble(-6, 6)), 2, 0.2, 0.2, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(0xF472B6), 1f));
                    }
                }
                default -> {
                }
            }
            if (party && rnd.nextInt(3) == 0) {
                p.spawnParticle(Particle.FIREWORK, l.clone().add(rnd.nextDouble(-14, 14), 14, rnd.nextDouble(-14, 14)), 2, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private void skyAndFireworks() {
        if (!enabled) {
            return;
        }
        SeasonDecoService.Theme t = theme();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            applySky(p, t);
        }
        boolean party = t == SeasonDecoService.Theme.NEWYEAR || newYearsEve();
        if (!party) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= nextFirework) {
            nextFirework = now + fireworkSeconds * 1000L;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    fireworks(p.getLocation(), 2 + ThreadLocalRandom.current().nextInt(3));
                }
            }
        }
        midnightShow();
    }

    /** A shooting star streaking across the sky above the player. */
    private void shootingStar(Player p) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location from = p.getLocation().add(rnd.nextDouble(-25, 25), 28 + rnd.nextDouble(8), rnd.nextDouble(-25, 25));
        org.bukkit.util.Vector dir = new org.bukkit.util.Vector(rnd.nextDouble(-1, 1), -0.35, rnd.nextDouble(-1, 1)).normalize();
        for (int i = 0; i < 18; i++) {
            int step = i;
            plugin.tasks().later(1 + i / 3, "shooting star", () -> {
                if (p.isOnline()) {
                    Location at = from.clone().add(dir.clone().multiply(step * 1.4));
                    p.spawnParticle(Particle.END_ROD, at, 2, 0.05, 0.05, 0.05, 0);
                    p.spawnParticle(Particle.FIREWORK, at, 1, 0, 0, 0, 0);
                }
            });
        }
    }

    /** Halloween: everyone sees an eternal dusk; winter: snowfall (rain in warm biomes); otherwise the normal sky. */
    private void applySky(Player p, SeasonDecoService.Theme t) {
        UUID id = p.getUniqueId();
        if (silvesterTime()) {
            // New Year's Eve: always a clear night, so every rocket is seen
            p.setPlayerTime(18000, false);
            p.setPlayerWeather(WeatherType.CLEAR);
            styled.add(id);
            return;
        }
        if (sky && t == SeasonDecoService.Theme.HALLOWEEN) {
            p.setPlayerTime(13800, false);
            p.resetPlayerWeather();
            styled.add(id);
        } else if (sky && t == SeasonDecoService.Theme.WINTER) {
            p.resetPlayerTime();
            p.setPlayerWeather(WeatherType.DOWNFALL);
            styled.add(id);
        } else if (sky && t == SeasonDecoService.Theme.STARS) {
            p.setPlayerTime(18000, false);
            p.setPlayerWeather(WeatherType.CLEAR);
            styled.add(id);
        } else if (sky && moodTime(t) >= 0 && plugin.attractionStore().inside(p.getLocation(), 30)) {
            // at the spawn every season has its own light: spring morning, summer noon, golden autumn, pink valentine …
            p.setPlayerTime(moodTime(t), false);
            p.setPlayerWeather(WeatherType.CLEAR);
            styled.add(id);
        } else if (styled.remove(id)) {
            p.resetPlayerTime();
            p.resetPlayerWeather();
        }
    }

    /** Time of day that fits the season at the spawn (-1 = normal). */
    private static long moodTime(SeasonDecoService.Theme t) {
        return switch (t) {
            case SPRING -> 1500;
            case SUMMER -> 6000;
            case AUTUMN -> 12300;
            case VALENTINE -> 12650;
            case NEWYEAR -> 18000;
            default -> -1;
        };
    }

    /** Countdown in the last 10 seconds before midnight on Dec 31, then a big firework show for everyone. */
    private void midnightShow() {
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        LocalDate date = now.toLocalDate();
        if (date.getMonthValue() == 12 && date.getDayOfMonth() == 31 && now.getHour() == 23 && now.getMinute() == 59) {
            int left = 60 - now.getSecond();
            if (left <= 10 && left != lastCountdown) {
                lastCountdown = left;
                countdownTick(left);
            }
        }
        if (date.getMonthValue() == 1 && date.getDayOfMonth() == 1 && now.getHour() == 0 && now.getMinute() < 2
                && showDay != date.toEpochDay()) {
            showDay = date.toEpochDay();
            newYearShow(date.getYear());
        }
    }

    /** /season silvester: plays the 10 second countdown and the New Year show right now (test / private party). */
    public void testShow() {
        for (int i = 10; i >= 1; i--) {
            int left = i;
            plugin.tasks().later(1 + (10 - i) * 20L, "test countdown", () -> countdownTick(left));
        }
        int year = ZonedDateTime.now(plugin.multipliers().zone()).getYear() + 1;
        plugin.tasks().later(1 + 10 * 20L, "test new year show", () -> newYearShow(year));
    }

    private void countdownTick(int left) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.showTitle(plugin.messages().in(p, () -> net.kyori.adventure.title.Title.title(plugin.messages().get("seasons.countdown", "seconds", left),
                    plugin.messages().get("seasons.countdown-subtitle"),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(900),
                            java.time.Duration.ofMillis(100)))));
            SoundSpec.of("block.note_block.bell", 1f, left <= 3 ? 2f : 1.2f).play(p);
            SoundSpec.of("block.note_block.basedrum", 1f, 0.8f).play(p);
        }
        Location c = plugin.attractionStore().center();
        if (c != null && c.isChunkLoaded()) {
            // the obelisk at the spawn pulses with every second
            c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(0, 22, 0), 40 + (10 - left) * 15, 1.5, 6, 1.5, 0.05);
            c.getWorld().spawnParticle(Particle.FIREWORK, c.clone().add(0, 24, 0), 20, 2, 2, 2, 0.1);
        }
    }

    private void newYearShow(int year) {
        plugin.messages().broadcast("seasons.happy-new-year", "year", year);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.showTitle(plugin.messages().in(p, () -> net.kyori.adventure.title.Title.title(plugin.messages().get("seasons.new-year-title", "year", year),
                    plugin.messages().get("seasons.new-year-subtitle"))));
            SoundSpec.of("ui.toast.challenge_complete", 1f, 1f).play(p);
            SoundSpec.of("entity.firework_rocket.large_blast", 1f, 1f).play(p);
            for (int wave = 0; wave < 8; wave++) {
                plugin.tasks().later(1 + wave * 25L, "new year show", () -> {
                    if (p.isOnline() && !plugin.attractionStore().inside(p.getLocation(), 20)) {
                        fireworks(p.getLocation(), 6);
                    }
                });
            }
        }
        spawnShow(showSeconds);
    }

    private int showSeconds = 180;

    /**
     * The giant show at the spawn: for {@code seconds} rockets all over the island every half second, rings around the
     * obelisk every few seconds, rocket fountains along the paths and a huge grand finale in the last 15 seconds.
     */
    private void spawnShow(int seconds) {
        Location c = plugin.attractionStore().center();
        if (c == null || !c.isChunkLoaded()) {
            return;
        }
        int radius = Math.max(20, plugin.attractionStore().radius() - 8);
        int waves = seconds * 2;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int wave = 0; wave < waves; wave++) {
            int w = wave;
            boolean finale = wave >= waves - 30;
            plugin.tasks().later(1 + wave * 10L, "spawn new year show", () -> {
                int n = finale ? 45 : 10 + rnd.nextInt(10);
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble(Math.PI * 2);
                    double r = Math.sqrt(rnd.nextDouble()) * radius;
                    Location at = c.clone().add(Math.cos(a) * r, 1 + rnd.nextInt(6), Math.sin(a) * r);
                    if (at.isChunkLoaded()) {
                        fireworks(at, 1);
                    }
                }
                if (w % 8 == 0) {
                    // ring around the obelisk
                    int ring = 24;
                    double rr = 8 + (w / 8 % 4) * 6;
                    for (int i = 0; i < ring; i++) {
                        double a = Math.toRadians(i * 360.0 / ring + w * 7);
                        fireworks(c.clone().add(Math.cos(a) * rr, 3, Math.sin(a) * rr), 1);
                    }
                }
                if (w % 12 == 6) {
                    // fountains along the four paths
                    for (int d = 0; d < 4; d++) {
                        double a = Math.toRadians(d * 90);
                        for (int k = 20; k < radius; k += 12) {
                            fireworks(c.clone().add(Math.cos(a) * k, 2, Math.sin(a) * k), 1);
                        }
                    }
                }
                c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(0, 24, 0), finale ? 120 : 40, 3, 4, 3, 0.3);
                c.getWorld().spawnParticle(Particle.FIREWORK, c.clone().add(0, 22, 0), finale ? 80 : 25, 2, 2, 2, 0.4);
            });
        }
    }

    /** Dec 31 from 20:00: rockets keep flying above the spawn the whole evening. */
    private void eveRockets() {
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        if (now.getMonthValue() != 12 || now.getDayOfMonth() != 31 || now.getHour() < 20) {
            return;
        }
        Location c = plugin.attractionStore().center();
        if (c == null || !c.isChunkLoaded()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int radius = Math.max(20, plugin.attractionStore().radius() - 8);
        for (int i = 0; i < 8; i++) {
            double a = rnd.nextDouble(Math.PI * 2);
            double r = Math.sqrt(rnd.nextDouble()) * radius;
            Location at = c.clone().add(Math.cos(a) * r, 2, Math.sin(a) * r);
            if (at.isChunkLoaded()) {
                fireworks(at, 1);
            }
        }
    }

    /** Silvester: from Dec 31 until 3 a.m. on New Year's Day. */
    /** /season silvester aus: Silvester (clear night, fireworks, gifts) ended early until the next New Year's Eve. */
    private int silvesterOffYear = -1;

    public void endSilvester() {
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        silvesterOffYear = now.getMonthValue() == 1 ? now.getYear() - 1 : now.getYear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (styled.remove(p.getUniqueId())) {
                p.resetPlayerTime();
                p.resetPlayerWeather();
            }
        }
    }

    public void resumeSilvester() {
        silvesterOffYear = -1;
    }

    public boolean silvesterTime() {
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        int eveYear = now.getMonthValue() == 1 ? now.getYear() - 1 : now.getYear();
        if (eveYear == silvesterOffYear) {
            return false;
        }
        return now.getMonthValue() == 12 && now.getDayOfMonth() == 31 || now.getMonthValue() == 1 && now.getDayOfMonth() == 1 && now.getHour() < 3;
    }

    /** Silvester: little table fireworks (fountains, sparklers, crackling) on the tables of the playground. */
    private void tableFireworks() {
        if (!enabled || !silvesterTime()) {
            return;
        }
        Location c = plugin.attractionStore().center();
        if (c == null || !c.isChunkLoaded()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int[] t : TABLES) {
            Location top = c.clone().add(t[0] + 0.5, 3.1, t[1] + 0.5);
            if (!top.isChunkLoaded()) {
                continue;
            }
            World w = top.getWorld();
            switch (rnd.nextInt(4)) {
                case 0 -> { // fountain
                    w.spawnParticle(Particle.FIREWORK, top, 18, 0.05, 0.1, 0.05, 0.12);
                    w.spawnParticle(Particle.FLAME, top, 4, 0.05, 0.05, 0.05, 0.03);
                    w.playSound(top, Sound.BLOCK_FIRE_EXTINGUISH, 0.25f, 1.8f);
                }
                case 1 -> { // sparkler
                    w.spawnParticle(Particle.ELECTRIC_SPARK, top.clone().add(0, 0.3, 0), 14, 0.15, 0.2, 0.15, 0.05);
                    w.spawnParticle(Particle.WAX_ON, top.clone().add(0, 0.3, 0), 6, 0.2, 0.2, 0.2, 0.2);
                }
                case 2 -> { // confetti popper
                    for (int i = 0; i < 12; i++) {
                        w.spawnParticle(Particle.DUST, top.clone().add(rnd.nextDouble(-0.6, 0.6), rnd.nextDouble(0.3, 1.4), rnd.nextDouble(-0.6, 0.6)),
                                1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(rnd.nextInt(0xFFFFFF)), 0.9f));
                    }
                    w.playSound(top, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 0.35f, 1.6f);
                }
                default -> { // little crackers
                    w.spawnParticle(Particle.CRIT, top, 10, 0.3, 0.1, 0.3, 0.2);
                    w.playSound(top, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.3f, 1.9f);
                }
            }
        }
    }

    /** Silvester: every 20–40 minutes everybody online gets a few gems as a present. */
    private void silvesterGifts() {
        if (!enabled || !silvesterTime() || giftGemsMax <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextGift == 0) {
            nextGift = now + ThreadLocalRandom.current().nextLong(giftMinMinutes, giftMaxMinutes + 1L) * 60_000L;
            return;
        }
        if (now < nextGift) {
            return;
        }
        nextGift = now + ThreadLocalRandom.current().nextLong(giftMinMinutes, giftMaxMinutes + 1L) * 60_000L;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            online.mythoscraft.core.player.PlayerData data = plugin.playerData().get(p);
            if (data == null) {
                continue;
            }
            int gems = ThreadLocalRandom.current().nextInt(giftGemsMin, giftGemsMax + 1);
            plugin.economy().earn(data, online.mythoscraft.core.economy.Currency.GEMS, gems, false);
            plugin.messages().send(p, "seasons.silvester-gift", "gems", gems);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 2.2, 0), 25, 0.4, 0.3, 0.4, 0.08);
        }
    }

    /** Fireworks high above a player (tagged, they never hurt anybody). */
    static void fireworks(Location near, int count) {
        World w = near.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        for (int i = 0; i < count; i++) {
            Location at = count == 1 ? near.clone() : near.clone().add(rnd.nextDouble(-16, 16), 3, rnd.nextDouble(-16, 16));
            w.spawn(at, Firework.class, fw -> {
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().with(types[rnd.nextInt(types.length)])
                        .withColor(Color.fromRGB(rnd.nextInt(0xFFFFFF)), Color.fromRGB(rnd.nextInt(0xFFFFFF)))
                        .withFade(Color.WHITE).flicker(rnd.nextBoolean()).trail(rnd.nextBoolean()).build());
                meta.setPower(1 + rnd.nextInt(2));
                fw.setFireworkMeta(meta);
                fw.setPersistent(false);
                fw.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "season_firework");
            });
        }
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        for (UUID id : styled) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                p.resetPlayerTime();
                p.resetPlayerWeather();
            }
        }
        styled.clear();
    }
}
