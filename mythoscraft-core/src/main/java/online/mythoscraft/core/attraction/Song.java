package online.mythoscraft.core.attraction;

import org.bukkit.Sound;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A note block song: notes at server ticks. Built from the small text notation of the built-in songs or read from a
 * Note Block Studio (.nbs) file.
 */
public final class Song {

    public record Note(int tick, Sound sound, float pitch, float volume) {
    }

    private final String id;
    private final String title;
    private final boolean streamSafe;
    private final List<Note> notes;
    private final int length;

    public Song(String id, String title, boolean streamSafe, List<Note> notes) {
        this.id = id;
        this.title = title;
        this.streamSafe = streamSafe;
        List<Note> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparingInt(Note::tick));
        this.notes = List.copyOf(sorted);
        this.length = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1).tick() + 10;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public boolean streamSafe() {
        return streamSafe;
    }

    public List<Note> notes() {
        return notes;
    }

    public int length() {
        return length;
    }

    // ---- instruments -----------------------------------------------------------------------------------------

    /** Note block instruments with the MIDI number of their lowest note (key 0). */
    enum Instrument {
        HARP(Sound.BLOCK_NOTE_BLOCK_HARP, 54), BASS(Sound.BLOCK_NOTE_BLOCK_BASS, 30), BASEDRUM(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 54),
        SNARE(Sound.BLOCK_NOTE_BLOCK_SNARE, 54), HAT(Sound.BLOCK_NOTE_BLOCK_HAT, 54), GUITAR(Sound.BLOCK_NOTE_BLOCK_GUITAR, 42),
        FLUTE(Sound.BLOCK_NOTE_BLOCK_FLUTE, 66), BELL(Sound.BLOCK_NOTE_BLOCK_BELL, 78), CHIME(Sound.BLOCK_NOTE_BLOCK_CHIME, 78),
        XYLOPHONE(Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 78), IRON_XYLOPHONE(Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 54),
        COW_BELL(Sound.BLOCK_NOTE_BLOCK_COW_BELL, 66), DIDGERIDOO(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 30),
        BIT(Sound.BLOCK_NOTE_BLOCK_BIT, 54), BANJO(Sound.BLOCK_NOTE_BLOCK_BANJO, 54), PLING(Sound.BLOCK_NOTE_BLOCK_PLING, 54);

        final Sound sound;
        final int base;

        Instrument(Sound sound, int base) {
            this.sound = sound;
            this.base = base;
        }

        /** Pitch for a MIDI note, moved by octaves into the playable two-octave range. */
        float pitch(int midi) {
            int key = midi - base;
            while (key < 0) {
                key += 12;
            }
            while (key > 24) {
                key -= 12;
            }
            return (float) Math.pow(2, (key - 12) / 12.0);
        }
    }

    // ---- text notation ---------------------------------------------------------------------------------------

    /**
     * Builds a song from tracks in a small notation. Every track: {@code "INSTRUMENT volume: tokens"}; a token is
     * {@code NOTE:steps} (e.g. {@code E5:2}, {@code F#4:1}, {@code Bb3:3}), a chord {@code C4+E4+G4:2} or a rest
     * {@code R:2}; {@code |} bar lines are ignored. {@code ticksPerStep} sets the tempo.
     */
    public static Song compose(String id, String title, int ticksPerStep, String... tracks) {
        List<Note> notes = new ArrayList<>();
        for (String track : tracks) {
            int colon = track.indexOf(':');
            String[] head = track.substring(0, colon).trim().split("\\s+");
            Instrument inst = Instrument.valueOf(head[0].toUpperCase(Locale.ROOT));
            float volume = head.length > 1 ? Float.parseFloat(head[1]) : 1f;
            int step = 0;
            for (String token : track.substring(colon + 1).trim().split("\\s+")) {
                if (token.isEmpty() || token.equals("|")) {
                    continue;
                }
                int c = token.lastIndexOf(':');
                String what = c < 0 ? token : token.substring(0, c);
                int steps = c < 0 ? 1 : Integer.parseInt(token.substring(c + 1));
                if (!what.equalsIgnoreCase("R")) {
                    for (String n : what.split("\\+")) {
                        notes.add(new Note(step * ticksPerStep, inst.sound, inst.pitch(midi(n)), volume));
                    }
                }
                step += steps;
            }
        }
        return new Song(id, title, true, notes);
    }

    /** "C4" = 60, "F#3" = 54, "Bb2" … */
    static int midi(String name) {
        String n = name.trim();
        int letter = switch (Character.toUpperCase(n.charAt(0))) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> throw new IllegalArgumentException("Note " + name);
        };
        int i = 1;
        while (i < n.length() && (n.charAt(i) == '#' || n.charAt(i) == 'b')) {
            letter += n.charAt(i) == '#' ? 1 : -1;
            i++;
        }
        int octave = Integer.parseInt(n.substring(i));
        return (octave + 1) * 12 + letter;
    }

    // ---- converted audio ----------------------------------------------------------------------------------

    /**
     * Reads a song converted from an audio file: one note per line, {@code "<tick> <sound key> <pitch> <volume>"}
     * (e.g. {@code 40 entity.villager.ambient 1.122 0.80}); {@code #} starts a comment. Any game sound works.
     */
    public static Song readText(String id, String title, InputStream raw, boolean streamSafe) throws IOException {
        List<Note> notes = new ArrayList<>();
        String text = new String(raw.readAllBytes(), StandardCharsets.UTF_8);
        for (String line : text.split("\\R")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            String[] f = l.split("\\s+");
            if (f.length < 4) {
                continue;
            }
            Sound sound = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(f[1]));
            if (sound == null) {
                continue;
            }
            notes.add(new Note(Integer.parseInt(f[0]), sound, Float.parseFloat(f[2]), Float.parseFloat(f[3])));
        }
        return new Song(id, title, streamSafe, notes);
    }

    // ---- Note Block Studio ---------------------------------------------------------------------------------

    private static final Instrument[] NBS = {Instrument.HARP, Instrument.BASS, Instrument.BASEDRUM, Instrument.SNARE, Instrument.HAT,
            Instrument.GUITAR, Instrument.FLUTE, Instrument.BELL, Instrument.CHIME, Instrument.XYLOPHONE, Instrument.IRON_XYLOPHONE,
            Instrument.COW_BELL, Instrument.DIDGERIDOO, Instrument.BIT, Instrument.BANJO, Instrument.PLING};

    /** Reads an .nbs file (classic and new format, version 1–5). */
    public static Song readNbs(String id, InputStream raw, boolean streamSafe) throws IOException {
        LeInput in = new LeInput(raw);
        int first = in.u16();
        int version = 0;
        if (first == 0) {
            version = in.u8();
            in.u8();
            if (version >= 3) {
                in.u16();
            }
        }
        in.u16();
        String name = in.string();
        in.string();
        in.string();
        in.string();
        double tps = in.u16() / 100.0;
        in.u8();
        in.u8();
        in.u8();
        in.i32();
        in.i32();
        in.i32();
        in.i32();
        in.i32();
        in.string();
        if (version >= 4) {
            in.u8();
            in.u8();
            in.u16();
        }
        if (tps <= 0) {
            tps = 10;
        }
        List<Note> notes = new ArrayList<>();
        int tick = -1;
        while (true) {
            int jump = in.u16();
            if (jump == 0) {
                break;
            }
            tick += jump;
            while (true) {
                int layerJump = in.u16();
                if (layerJump == 0) {
                    break;
                }
                int instrument = in.u8();
                int key = in.u8();
                int velocity = 100;
                int fine = 0;
                if (version >= 4) {
                    velocity = in.u8();
                    in.u8();
                    fine = in.s16();
                }
                if (instrument >= NBS.length) {
                    continue;
                }
                double semis = key - 45 + fine / 100.0;
                while (semis < -12) {
                    semis += 12;
                }
                while (semis > 12) {
                    semis -= 12;
                }
                int serverTick = (int) Math.round(tick * 20 / tps);
                notes.add(new Note(serverTick, NBS[instrument].sound, (float) Math.pow(2, semis / 12.0), velocity / 100f));
            }
        }
        return new Song(id, name == null || name.isBlank() ? id : name, streamSafe, notes);
    }

    /** Little-endian reader for the .nbs format. */
    private static final class LeInput {
        private final DataInputStream in;

        LeInput(InputStream in) {
            this.in = new DataInputStream(in);
        }

        int u8() throws IOException {
            int b = in.read();
            if (b < 0) {
                throw new EOFException();
            }
            return b;
        }

        int u16() throws IOException {
            return u8() | (u8() << 8);
        }

        int s16() throws IOException {
            return (short) u16();
        }

        int i32() throws IOException {
            return u8() | (u8() << 8) | (u8() << 16) | (u8() << 24);
        }

        String string() throws IOException {
            int len = i32();
            if (len < 0 || len > 1_000_000) {
                throw new IOException("bad string length " + len);
            }
            byte[] b = in.readNBytes(len);
            return new String(b, StandardCharsets.UTF_8);
        }
    }
}
