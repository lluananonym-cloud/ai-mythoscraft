package online.mythoscraft.core.attraction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Songs with groove: every track gets a drum machine (kick, snare, hi-hat), a bass line and chords under the melody.
 * Original compositions for MythosCraft plus public domain melodies (Joplin, Offenbach, Beethoven, Gounod,
 * Leontovych, folk songs) – all free to use and stream.
 */
public final class GrooveBook {

    private GrooveBook() {
    }

    public static Map<String, Song> all() {
        Map<String, Song> m = new LinkedHashMap<>();
        for (Song s : new Song[]{spawnGroove(), neonNights(), sunsetDrive(), entertainer(), canCan(), furElise(), ghostTrain(),
                marionette(), carolOfTheBells(), tannenbaum(), snowflakeFunk(), springGroove(), beachParty(), loveSong(),
                stardust(), newYearRush()}) {
            m.put(s.id(), s);
        }
        return m;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /**
     * Drum machine: patterns of 16 steps per bar ("x" = hit, "." = rest), repeated {@code bars} times. Returns three
     * tracks (kick, snare, hat).
     */
    static String[] drums(String kick, String snare, String hat, int bars, float volume) {
        return new String[]{pattern("BASEDRUM " + volume, kick, bars), pattern("SNARE " + (volume * 0.7f), snare, bars),
                pattern("HAT " + (volume * 0.45f), hat, bars)};
    }

    static String pattern(String head, String pattern, int bars) {
        StringBuilder sb = new StringBuilder(head).append(':');
        for (int b = 0; b < bars; b++) {
            for (char c : pattern.toCharArray()) {
                sb.append(c == 'x' ? " C4:1" : " R:1");
            }
        }
        return sb.toString();
    }

    /** Bass line: one root note per bar, played as a groove pattern ("x" = root, "o" = octave up, "f" = fifth). */
    static String bass(String instrument, String[] roots, String groove, int repeat) {
        StringBuilder sb = new StringBuilder(instrument).append(':');
        for (int r = 0; r < repeat; r++) {
            for (String root : roots) {
                int midi = Song.midi(root);
                for (char c : groove.toCharArray()) {
                    switch (c) {
                        case 'x' -> sb.append(' ').append(name(midi)).append(":1");
                        case 'o' -> sb.append(' ').append(name(midi + 12)).append(":1");
                        case 'f' -> sb.append(' ').append(name(midi + 7)).append(":1");
                        default -> sb.append(" R:1");
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Chord stabs: one chord per bar on the given rhythm. Chords like "C4+E4+G4". */
    static String chords(String instrument, String[] chords, String rhythm, int repeat) {
        StringBuilder sb = new StringBuilder(instrument).append(':');
        for (int r = 0; r < repeat; r++) {
            for (String chord : chords) {
                for (char c : rhythm.toCharArray()) {
                    sb.append(c == 'x' ? " " + chord + ":1" : " R:1");
                }
            }
        }
        return sb.toString();
    }

    static String name(int midi) {
        String[] n = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return n[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1);
    }

    static String rep(String tokens, int times) {
        return (tokens + " ").repeat(times);
    }

    static Song song(String id, String title, int ticks, List<String> tracks) {
        return Song.compose(id, title, ticks, tracks.toArray(new String[0]));
    }

    static List<String> with(String[] drums, String... tracks) {
        List<String> l = new ArrayList<>(List.of(tracks));
        l.addAll(List.of(drums));
        return l;
    }

    static final String KICK_POP = "x.......x.x.....";
    static final String SNARE_POP = "....x.......x...";
    static final String HAT_8 = "x.x.x.x.x.x.x.x.";
    static final String HAT_16 = "xxxxxxxxxxxxxxxx";
    static final String KICK_4 = "x...x...x...x...";

    // ---- originals ---------------------------------------------------------------------------------------------

    /** Original: lo-fi spawn groove (Am – F – C – G), laid-back hip hop beat. */
    static Song spawnGroove() {
        String[] roots = {"A2", "F2", "C3", "G2"};
        String[] ch = {"A3+C4+E4", "F3+A3+C4", "C4+E4+G4", "G3+B3+D4"};
        String melody = "FLUTE 0.7: R:64 E5:3 D5:1 C5:4 A4:4 R:4 C5:2 D5:2 E5:4 G5:4 R:4 E5:3 D5:1 C5:2 D5:2 E5:4 D5:2 C5:2 B4:4 G4:4 R:8 "
                + "A5:4 G5:2 E5:2 D5:4 C5:4 E5:2 G5:2 A5:4 G5:8 E5:2 D5:2 C5:4 D5:4 E5:4 C5:2 B4:2 A4:8";
        return song("spawn_groove", "Spawn Groove", 3, with(drums("x......xx.x.....", "....x.......x..x", "x.x.x.x.x.x.x.xx", 12, 0.7f),
                bass("BASS 0.9", roots, "x..x..o.x...f...", 3), chords("PLING 0.35", ch, "..x...x...x.x...", 3), melody));
    }

    /** Original: synthwave with an 8-bit arpeggio and four-on-the-floor. */
    static Song neonNights() {
        String[] roots = {"D3", "Bb2", "F2", "C3"};
        StringBuilder arp = new StringBuilder("BIT 0.5:");
        String[][] notes = {{"D4", "F4", "A4", "D5"}, {"Bb3", "D4", "F4", "Bb4"}, {"F3", "A3", "C4", "F4"}, {"C4", "E4", "G4", "C5"}};
        for (int r = 0; r < 3; r++) {
            for (String[] n : notes) {
                for (int i = 0; i < 16; i++) {
                    arp.append(' ').append(n[i % 4]).append(":1");
                }
            }
        }
        String lead = "IRON_XYLOPHONE 0.7: R:64 A5:6 G5:2 F5:4 E5:4 D5:6 F5:2 A5:8 Bb5:6 A5:2 G5:4 F5:4 E5:4 G5:4 C6:8 "
                + "D6:4 C6:4 A5:4 F5:4 G5:6 A5:2 Bb5:8 A5:4 G5:4 F5:4 E5:4 D5:16";
        return song("neon_nights", "Neon Nights", 2, with(drums(KICK_4, SNARE_POP, HAT_16, 12, 0.75f),
                bass("BASS 1.0", roots, "x.xox.xox.xox.xo", 3), arp.toString(), lead));
    }

    /** Original: relaxed sunset drive in F major. */
    static Song sunsetDrive() {
        String[] roots = {"F2", "D2", "Bb1", "C2"};
        String[] ch = {"F3+A3+C4", "D3+F3+A3", "Bb3+D4+F4", "C4+E4+G4"};
        String melody = "GUITAR 0.8: R:32 A4:4 C5:4 D5:2 C5:2 A4:4 F4:4 G4:4 A4:8 D5:4 C5:4 A4:2 G4:2 F4:4 G4:4 A4:2 G4:2 E4:8 "
                + "F5:4 E5:4 D5:4 C5:4 D5:4 C5:2 A4:2 F4:8 G4:4 A4:4 C5:4 D5:4 C5:16";
        return song("sunset_drive", "Sunset Drive", 3, with(drums(KICK_POP, SNARE_POP, HAT_8, 12, 0.6f),
                bass("BASS 0.9", roots, "x...x.x...x.f.x.", 3), chords("HARP 0.35", ch, "x..x..x.x..x..x.", 3), melody));
    }

    /** Original: spooky ghost train with a stomping beat. */
    static Song ghostTrain() {
        String[] roots = {"E2", "C2", "A1", "B1"};
        String[] ch = {"E4+G4+B4", "C4+E4+G4", "A3+C4+E4", "B3+D#4+F#4"};
        String melody = "XYLOPHONE 0.8: R:32 E5:2 G5:2 B5:2 G5:2 A5:4 G5:4 E5:2 G5:2 C6:2 B5:2 A5:8 C6:2 B5:2 A5:2 G5:2 E5:4 C5:4 "
                + "B4:2 D#5:2 F#5:2 A5:2 B5:8 E5:2 E5:2 G5:2 E5:2 B5:4 A5:4 G5:2 F#5:2 E5:2 D#5:2 E5:8";
        return song("ghost_train", "Geisterbahn", 3, with(drums("x..x..x.x..x..x.", "....x.......x...", HAT_8, 12, 0.7f),
                bass("BASS 1.0", roots, "x.x.o.x.x.x.o.f.", 3), chords("BIT 0.3", ch, "x...x...x...x...", 3), melody,
                "DIDGERIDOO 0.5: " + rep("E2:16 C2:16 A1:16 B1:16", 3)));
    }

    /** Original: funky snowflake groove with bells for the winter. */
    static Song snowflakeFunk() {
        String[] roots = {"G2", "E2", "C2", "D2"};
        String[] ch = {"G4+B4+D5", "E4+G4+B4", "C4+E4+G4", "D4+F#4+A4"};
        String melody = "BELL 0.6: R:32 B5:2 D6:2 B5:2 G5:2 A5:4 B5:4 G5:2 E5:2 G5:2 B5:2 A5:8 E5:2 G5:2 C6:2 B5:2 A5:2 G5:2 E5:4 "
                + "D5:2 F#5:2 A5:2 C6:2 B5:8 G5:2 B5:2 D6:4 B5:4 G5:2 E5:2 C5:2 E5:2 G5:4 A5:4 D5:2 F#5:2 G5:8";
        return song("snowflake_funk", "Schneeflocken-Funk", 3, with(drums("x..x......x.....", "....x.......x...", "x.xxx.xxx.xxx.xx", 12, 0.65f),
                bass("BASS 1.0", roots, "x..xo..xx.x.o..f", 3), chords("CHIME 0.3", ch, "..x..x....x..x..", 3), melody,
                pattern("HAT 0.2", "..x...x...x...x.", 12)));
    }

    /** Original: spring groove with banjo. */
    static Song springGroove() {
        String[] roots = {"C3", "A2", "F2", "G2"};
        String[] ch = {"C4+E4+G4", "A3+C4+E4", "F3+A3+C4", "G3+B3+D4"};
        String melody = "FLUTE 0.7: R:32 G5:2 E5:2 C5:2 E5:2 G5:4 A5:4 G5:2 E5:2 C5:4 D5:8 E5:2 F5:2 A5:2 C6:2 B5:4 A5:4 G5:4 F5:4 E5:8 "
                + "G5:2 E5:2 C5:2 E5:2 G5:4 C6:4 B5:2 A5:2 G5:4 E5:8 F5:4 A5:4 G5:4 D5:4 C5:16";
        return song("spring_groove", "Frühlingsgroove", 3, with(drums(KICK_POP, SNARE_POP, HAT_8, 12, 0.55f),
                bass("BASS 0.8", roots, "x...f...x...f.o.", 3), chords("BANJO 0.45", ch, "x.xx.xx.x.xx.xx.", 3), melody));
    }

    /** Original: summer beach party, reggaeton-like beat. */
    static Song beachParty() {
        String[] roots = {"A2", "D3", "E2", "A2"};
        String[] ch = {"A3+C#4+E4", "D4+F#4+A4", "E4+G#4+B4", "A3+C#4+E4"};
        String melody = "PLING 0.7: R:32 E5:3 C#5:3 A4:2 B4:2 C#5:2 E5:4 F#5:3 E5:3 D5:2 C#5:4 B4:4 E5:3 G#5:3 B5:2 A5:4 G#5:4 "
                + "A5:3 E5:3 C#5:2 A4:8 C#6:3 B5:3 A5:2 F#5:4 A5:4 B5:3 A5:3 G#5:2 E5:8 E5:3 C#5:3 A4:2 B4:2 C#5:2 A4:8";
        return song("beach_party", "Strandparty", 3, with(drums("x..x..x.x..x..x.", "...x..x....x..x.", HAT_8, 12, 0.65f),
                bass("BASS 1.0", roots, "x..x..x.x..x..o.", 3), chords("GUITAR 0.4", ch, "..x..x....x..x..", 3), melody));
    }

    /** Original: love ballad for Valentine's day. */
    static Song loveSong() {
        String[] roots = {"F2", "D2", "Bb1", "C2"};
        String[] ch = {"F3+A3+C4", "D3+F3+A3", "Bb3+D4+F4", "C4+E4+G4"};
        String melody = "BELL 0.6: R:32 C5:4 F5:4 A5:6 G5:2 F5:4 D5:4 F5:8 D5:4 Bb4:4 D5:6 F5:2 E5:4 C5:4 G5:8 "
                + "A5:4 C6:4 Bb5:4 A5:4 G5:4 F5:4 D5:8 Bb4:4 D5:4 G5:4 F5:2 E5:2 F5:16";
        return song("love_song", "Liebeslied", 4, with(drums("x.......x.......", "....x.......x...", "x...x...x...x...", 12, 0.45f),
                bass("BASS 0.8", roots, "x.......f.......", 3), chords("HARP 0.4", ch, "x.x.x.x.x.x.x.x.", 3), melody));
    }

    /** Original: starry dream pop for the shooting stars. */
    static Song stardust() {
        String[] roots = {"E2", "C#2", "A1", "B1"};
        String[] ch = {"E4+G#4+B4", "C#4+E4+G#4", "A3+C#4+E4", "B3+D#4+F#4"};
        String melody = "CHIME 0.7: R:32 B5:4 G#5:2 E5:2 F#5:4 G#5:4 E5:8 C#5:4 E5:4 G#5:4 B5:4 A5:4 G#5:4 F#5:8 "
                + "E6:4 D#6:2 B5:2 C#6:4 B5:4 G#5:8 A5:4 G#5:4 F#5:4 D#5:4 E5:16";
        return song("stardust", "Sternenstaub", 3, with(drums(KICK_POP, SNARE_POP, HAT_16, 12, 0.5f),
                bass("BASS 0.8", roots, "x.....x...x.....", 3), chords("IRON_XYLOPHONE 0.3", ch, "x..x..x..x..x.x.", 3), melody));
    }

    /** Original: New Year's rush – fast, euphoric, with a build-up. */
    static Song newYearRush() {
        String[] roots = {"C3", "G2", "A2", "F2"};
        String[] ch = {"C4+E4+G4", "G3+B3+D4", "A3+C4+E4", "F3+A3+C4"};
        String melody = "PLING 0.8: R:64 G5:2 G5:2 E5:2 G5:2 A5:4 G5:4 D5:2 D5:2 B4:2 D5:2 G5:8 E5:2 E5:2 C5:2 E5:2 A5:4 G5:4 "
                + "F5:2 A5:2 C6:2 A5:2 G5:8 C6:4 B5:4 A5:4 G5:4 D5:4 G5:4 B5:8 C6:4 E6:4 D6:4 C6:4 A5:4 F5:4 G5:4 C6:4";
        return song("new_year_rush", "Neujahrs-Rausch", 2, with(drums(KICK_4, "....x.......x.xx", HAT_16, 12, 0.8f),
                bass("BASS 1.0", roots, "xoxoxoxoxoxoxoxo", 3), chords("BIT 0.35", ch, "..x...x...x...x.", 3), melody));
    }

    // ---- public domain with groove -------------------------------------------------------------------------

    /** Scott Joplin – The Entertainer (1902). */
    static Song entertainer() {
        String a = "D4:1 D#4:1 E4:1 C5:2 E4:1 C5:2 E4:1 C5:6 C5:1 D5:1 D#5:1 E5:1 C5:1 D5:1 E5:2 B4:1 D5:2 C5:6 ";
        String b = "D4:1 D#4:1 E4:1 C5:2 E4:1 C5:2 E4:1 C5:6 R:1 A4:1 G4:1 F#4:1 A4:1 C5:1 E5:2 D5:1 C5:1 A4:1 D5:6 R:1 ";
        String melody = "HARP: " + a + b + a + "C5:1 D5:1 E5:1 C5:1 D5:1 E5:2 C5:1 D5:1 C5:1 E5:1 C5:1 D5:1 E5:2 C5:1 D5:1 C5:1 E5:1 C5:1 D5:1 E5:2 B4:1 D5:2 C5:8";
        String[] roots = {"C2", "C2", "G1", "C2", "C2", "C2", "D2", "G1"};
        return song("entertainer", "The Entertainer (Joplin)", 3, with(drums("x...x...x...x...", "..x...x...x...x.", HAT_8, 8, 0.45f),
                melody, bass("BASS 0.9", roots, "x...o...f...o...", 1), chords("GUITAR 0.3", new String[]{"C4+E4+G4", "C4+E4+G4", "B3+D4+F4",
                        "C4+E4+G4", "C4+E4+G4", "C4+E4+G4", "D4+F#4+A4", "B3+D4+F4"}, "..x...x...x...x.", 1)));
    }

    /** Jacques Offenbach – Galop infernal / Can-Can (1858). */
    static Song canCan() {
        String phrase = "C4:2 D4:1 F4:1 E4:1 D4:1 G4:2 G4:2 G4:1 A4:1 E4:1 F4:1 D4:2 D4:2 D4:1 F4:1 E4:1 D4:1 C4:1 C5:1 B4:1 A4:1 "
                + "G4:1 F4:1 E4:1 D4:1 ";
        String melody = "HARP: " + rep(phrase, 4);
        String[] roots = {"C3", "G2", "G2", "C3"};
        return song("can_can", "Can-Can (Offenbach)", 2, with(drums("x...x...x...x...", "..x...x...x...x.", HAT_8, 12, 0.6f),
                melody, bass("BASS 1.0", roots, "x.o.x.o.x.o.x.o.", 3), "PLING 0.3: " + rep(phrase, 4)));
    }

    /** Ludwig van Beethoven – Für Elise (1810), with a soft beat. */
    static Song furElise() {
        String a = "E5:1 D#5:1 E5:1 D#5:1 E5:1 B4:1 D5:1 C5:1 A4:3 C4:1 E4:1 A4:1 B4:3 E4:1 G#4:1 B4:1 C5:3 E4:1 ";
        String b = "E5:1 D#5:1 E5:1 D#5:1 E5:1 B4:1 D5:1 C5:1 A4:3 C4:1 E4:1 A4:1 B4:3 E4:1 C5:1 B4:1 A4:6 ";
        String melody = "HARP: " + a + b + a + b;
        return song("fur_elise", "Für Elise (Beethoven)", 3, with(drums("x.......x.......", "....x.......x...", "x.x.x.x.x.x.x.x.", 6, 0.35f),
                melody, "BASS 0.7: " + rep("A2:6 E2:6 A2:6 E2:6", 2) + " A2:6 E2:6 A2:6 E2:6"));
    }

    /** Charles Gounod – Funeral March of a Marionette (1872). */
    static Song marionette() {
        String melody = "XYLOPHONE: R:2 A4:1 D5:2 D5:1 D5:2 E5:1 F5:2 F5:1 F5:2 G5:1 A5:2 Bb5:1 A5:2 G5:1 F5:2 E5:1 D5:3 "
                + "R:1 A4:1 C#5:2 C#5:1 C#5:2 D5:1 E5:2 E5:1 E5:2 F5:1 G5:2 A5:1 G5:2 F5:1 E5:2 D5:1 C#5:3 "
                + "R:1 A4:1 D5:2 D5:1 D5:2 E5:1 F5:2 F5:1 F5:2 G5:1 A5:2 D6:1 C6:2 Bb5:1 A5:2 G5:1 F5:2 E5:1 D5:6";
        String[] roots = {"D2", "D2", "A1", "A1", "D2", "D2"};
        return song("marionette", "Marionetten-Trauermarsch (Gounod)", 4, with(drums("x.....x.....x...", "...x.....x......", "x.x.x.x.x.x.x.x.", 6, 0.45f),
                melody, bass("BASS 0.9", roots, "x..f..x..f..x...", 1)));
    }

    /** Mykola Leontovych – Carol of the Bells (Shchedryk, 1914). */
    static Song carolOfTheBells() {
        String ostinato = "Bb4:2 A4:1 Bb4:1 G4:2 ";
        String melody = "BELL 0.7: " + rep(ostinato, 8) + rep("F5:2 Eb5:1 F5:1 D5:2 ", 4) + rep("Bb5:2 A5:1 Bb5:1 G5:2 ", 4)
                + rep(ostinato, 4) + "D5:6 C5:6 Bb4:6 A4:6";
        String harp = "HARP 0.6: " + rep(ostinato, 28);
        return song("carol_bells", "Carol of the Bells (Leontovych)", 4, with(drums("x.....x.....x...", "......x.......x.", "x.x.x.x.x.x.x.x.", 10, 0.4f),
                melody, harp, "BASS 0.8: " + rep("G2:6 F2:6 Eb2:6 D2:6", 7)));
    }

    /** German folk song – O Tannenbaum (melody 16th century, text 1824). */
    static Song tannenbaum() {
        String verse = "D4:2 G4:3 G4:1 G4:4 A4:2 B4:3 B4:1 B4:4 B4:2 A4:2 B4:2 C5:4 F#4:4 A4:2 G4:6 "
                + "D5:2 D5:2 B4:2 E5:6 D5:2 D5:2 C5:2 C5:6 C5:2 C5:2 A4:2 D5:6 C5:2 C5:2 B4:2 B4:6 "
                + "D4:2 G4:3 G4:1 G4:4 A4:2 B4:3 B4:1 B4:4 B4:2 A4:2 B4:2 C5:4 F#4:4 A4:2 G4:6";
        return song("tannenbaum", "O Tannenbaum", 3, with(drums("x.......x.......", "....x.......x...", "x.x.x.x.x.x.x.x.", 9, 0.4f),
                "CHIME 0.8: " + verse, "HARP 0.5: " + verse, "BASS 0.8: " + rep("G2:8 D2:8", 9)));
    }
}
