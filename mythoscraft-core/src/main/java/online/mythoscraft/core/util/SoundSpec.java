package online.mythoscraft.core.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;

import java.util.Locale;

/**
 * A configurable sound: "entity.player.levelup 1.0 1.2" (key, volume, pitch). An empty string or "none" disables it.
 */
public record SoundSpec(Sound sound) {

    public static final SoundSpec NONE = new SoundSpec(null);

    public static SoundSpec parse(String spec) {
        if (spec == null) {
            return NONE;
        }
        String s = spec.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) {
            return NONE;
        }
        String[] parts = s.split("\\s+");
        float volume = 1f;
        float pitch = 1f;
        try {
            if (parts.length > 1) {
                volume = Float.parseFloat(parts[1]);
            }
            if (parts.length > 2) {
                pitch = Float.parseFloat(parts[2]);
            }
            Key key = Key.key(parts[0].toLowerCase(Locale.ROOT));
            return new SoundSpec(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
        } catch (InvalidKeyException | NumberFormatException e) {
            return null;
        }
    }

    public static SoundSpec of(String key, float volume, float pitch) {
        return new SoundSpec(Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch));
    }

    public boolean enabled() {
        return sound != null;
    }

    public void play(Audience audience) {
        if (sound != null) {
            audience.playSound(sound, Sound.Emitter.self());
        }
    }

    public void play(Audience audience, float pitch) {
        if (sound != null) {
            audience.playSound(Sound.sound(sound.name(), sound.source(), sound.volume(), pitch), Sound.Emitter.self());
        }
    }

    public void playAt(Location loc) {
        if (sound != null && loc.getWorld() != null) {
            loc.getWorld().playSound(sound, loc.getX(), loc.getY(), loc.getZ());
        }
    }
}
