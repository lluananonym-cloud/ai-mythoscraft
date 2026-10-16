package online.mythoscraft.core.attraction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in spawn music: public domain melodies (composers died more than 70 years ago) and original compositions
 * written for MythosCraft. Everything here is free to use and stream.
 */
public final class SongBook {

    private SongBook() {
    }

    public static Map<String, Song> all() {
        Map<String, Song> m = new LinkedHashMap<>();
        for (Song s : new Song[]{mythosTheme(), ode(), canon(), jingleBells(), silentNight(), winterGlow(), mountainKing(), toccata(),
                skeletonShuffle(), auldLangSyne(), danube()}) {
            m.put(s.id(), s);
        }
        return m;
    }

    private static String rep(String tokens, int times) {
        return (tokens + " ").repeat(times);
    }

    /** Original: calm arpeggios with a flute melody (A minor – F – C – G). */
    static Song mythosTheme() {
        String[][] arps = {
                {"A3", "C4", "E4", "A4", "C5", "A4", "E4", "C4"},
                {"F4", "A3", "C4", "F4", "A4", "F4", "C4", "A3"},
                {"G3", "C4", "E4", "G4", "C5", "G4", "E4", "C4"},
                {"G3", "B3", "D4", "G4", "B4", "G4", "D4", "B3"}};
        StringBuilder harp = new StringBuilder("HARP 0.55:");
        StringBuilder bass = new StringBuilder("BASS 0.8:");
        String[] roots = {"A2", "F2", "C3", "G2"};
        for (int round = 0; round < 4; round++) {
            for (int c = 0; c < 4; c++) {
                for (String n : arps[c]) {
                    harp.append(' ').append(n).append(":1");
                }
                bass.append(' ').append(roots[c]).append(":8");
            }
        }
        String flute = "FLUTE 0.8: R:32 E5:4 D5:2 C5:2 A4:6 R:2 F4:2 A4:2 C5:2 F5:2 E5:8 E5:4 G5:2 E5:2 D5:4 C5:4 B4:2 C5:2 D5:2 G5:2 G5:8"
                + " E5:4 D5:2 C5:2 A4:6 R:2 F4:2 A4:2 C5:2 F5:2 E5:4 D5:4 C5:4 B4:2 D5:2 C5:4 B4:2 G4:2 A4:8";
        return Song.compose("mythos_theme", "Mythos-Thema", 5, harp.toString(), bass.toString(), flute);
    }

    /** Beethoven – Ode an die Freude (1824). */
    static Song ode() {
        String a = "E4:2 E4:2 F4:2 G4:2 G4:2 F4:2 E4:2 D4:2 C4:2 C4:2 D4:2 E4:2 ";
        String melody = "HARP: " + a + "E4:3 D4:1 D4:4 " + a + "D4:3 C4:1 C4:4 "
                + "D4:2 D4:2 E4:2 C4:2 D4:2 E4:1 F4:1 E4:2 C4:2 D4:2 E4:1 F4:1 E4:2 D4:2 C4:2 D4:2 G3:4 "
                + a + "D4:3 C4:1 C4:4";
        String bass = "BASS 0.8: C3:4 G2:4 G2:8 C3:8 G2:8 C3:4 G2:4 G2:8 C3:8 G2:4 C3:4 G2:4 C3:4 G2:4 C3:4 G2:4 C3:4 C3:4 G2:4 "
                + "C3:4 G2:4 G2:8 C3:8 G2:4 C3:4";
        String bells = "BELL 0.35: R:32 R:32 E4:2 E4:2 F4:2 G4:2 G4:2 F4:2 E4:2 D4:2 C4:2 C4:2 D4:2 E4:2 D4:3 C4:1 C4:4";
        return Song.compose("ode", "Ode an die Freude (Beethoven)", 5, melody, bass, bells);
    }

    /** Pachelbel – Kanon in D (about 1700). */
    static Song canon() {
        String bassLine = "D3:4 A2:4 B2:4 F#2:4 G2:4 D2:4 G2:4 A2:4 ";
        String bass = "BASS 0.8: " + rep(bassLine, 4);
        String harp = "HARP 0.8: R:32 F#4:4 E4:4 D4:4 C#4:4 B3:4 A3:4 B3:4 C#4:4 "
                + "D4:2 F#4:2 A4:2 G4:2 F#4:2 D4:2 F#4:2 E4:2 D4:2 B3:2 D4:2 A4:2 G4:2 B4:2 A4:2 G4:2 "
                + "F#4:4 E4:4 D4:4 C#4:4 B3:4 A3:4 B3:4 C#4:8";
        String chime = "CHIME 0.3: R:64 F#4:2 A4:2 D4:2 F#4:2 A4:2 D4:2 G4:2 B4:2";
        return Song.compose("canon", "Kanon in D (Pachelbel)", 5, bass, harp, chime);
    }

    /** James Lord Pierpont – Jingle Bells (1857), chorus. */
    static Song jingleBells() {
        String a = "E4:2 E4:2 E4:4 E4:2 E4:2 E4:4 E4:2 G4:2 C4:3 D4:1 E4:8 F4:2 F4:2 F4:3 F4:1 F4:2 E4:2 E4:2 E4:1 E4:1 ";
        String melody = a + "E4:2 D4:2 D4:2 E4:2 D4:4 G4:4 " + a + "G4:2 G4:2 F4:2 D4:2 C4:8";
        String bass = "BASS 0.7: " + "C3:8 C3:8 C3:8 C3:8 F2:8 C3:8 G2:8 G2:8 C3:8 C3:8 C3:8 C3:8 F2:8 C3:8 G2:8 C3:8";
        String hat = "HAT 0.25: " + rep("C4:2", 64);
        return Song.compose("jingle_bells", "Jingle Bells", 4, "HARP: " + melody, "BELL 0.45: " + melody, bass, hat);
    }

    /** Franz Xaver Gruber – Stille Nacht (1818). */
    static Song silentNight() {
        String verse = "G4:3 A4:1 G4:2 E4:6 G4:3 A4:1 G4:2 E4:6 D5:4 D5:2 B4:6 C5:4 C5:2 G4:6 A4:4 A4:2 C5:3 B4:1 A4:2 G4:3 A4:1 G4:2 E4:6 "
                + "A4:4 A4:2 C5:3 B4:1 A4:2 G4:3 A4:1 G4:2 E4:6 D5:4 D5:2 F5:3 D5:1 B4:2 C5:6 E5:6 C5:2 G4:2 E4:2 G4:3 F4:1 D4:2 C4:12";
        String bass = "BASS 0.7: C3:12 C3:12 G2:12 C3:12 F2:12 C3:12 F2:12 C3:12 G2:12 C3:12 C3:6 G2:6 C3:12";
        String pad = "CHIME 0.25: R:12 C4+E4:12 G3+B3:12 C4+E4:12 F3+A3:12 C4+E4:12 F3+A3:12 C4+E4:12 G3+B3:12 C4+E4:12 R:12 C4+E4:12";
        return Song.compose("silent_night", "Stille Nacht (Gruber)", 6, "HARP: " + verse, bass, pad);
    }

    /** Original: "Winterglanz" – calm 3/4 waltz with chimes and bells. */
    static Song winterGlow() {
        String melody = "C5:4 F5:2 A5:4 G5:2 F5:2 E5:2 F5:2 C5:6 D5:4 Bb5:2 A5:4 G5:2 F5:2 G5:2 A5:2 G5:6 "
                + "C5:4 F5:2 A5:4 C6:2 Bb5:2 A5:2 G5:2 F5:2 D5:4 Bb4:2 D5:2 G5:2 F5:2 E5:2 G5:2 F5:6 R:6";
        String[] chords = {"F", "F", "F", "C", "Bb", "F", "C", "C", "F", "F", "Bb", "Bb", "G", "C", "F", "F"};
        StringBuilder harp = new StringBuilder("HARP 0.5:");
        StringBuilder bass = new StringBuilder("BASS 0.7:");
        for (String c : chords) {
            String[] n = switch (c) {
                case "F" -> new String[]{"F3", "C4", "A4"};
                case "C" -> new String[]{"C4", "G4", "E4"};
                case "Bb" -> new String[]{"Bb3", "F4", "D4"};
                default -> new String[]{"G3", "D4", "Bb4"};
            };
            harp.append(' ').append(n[0]).append(":2 ").append(n[1]).append(":2 ").append(n[2]).append(":2");
            bass.append(' ').append(c.equals("Bb") ? "Bb2" : c + "2").append(":6");
        }
        return Song.compose("winter_glow", "Winterglanz", 5, "CHIME 0.8: " + melody, "BELL 0.3: " + melody, harp.toString(), bass.toString());
    }

    /** Grieg – In der Halle des Bergkönigs (1875). */
    static Song mountainKing() {
        String phrase = "A3:1 B3:1 C4:1 D4:1 E4:1 C4:1 E4:2 D#4:1 B3:1 D#4:2 D4:1 Bb3:1 D4:2 "
                + "A3:1 B3:1 C4:1 D4:1 E4:1 C4:1 E4:1 A4:1 G4:1 E4:1 C4:1 E4:1 G4:4 ";
        String bassPhrase = "A2:2 E2:2 A2:2 E2:2 B2:2 F#2:2 Bb2:2 F2:2 A2:2 E2:2 A2:2 E2:2 E2:2 B1:2 E2:4 ";
        return Song.compose("mountain_king", "Halle des Bergkönigs (Grieg)", 3, "PLING 0.8: " + rep(phrase, 4),
                "BASS 0.9: " + rep(bassPhrase, 4), "SNARE 0.3: " + rep("R:2 C4:2", 32));
    }

    /** Bach – Toccata und Fuge d-Moll (opening). */
    static Song toccata() {
        String harp = "HARP: A4:1 G4:1 A4:6 R:2 G4:1 F4:1 E4:1 D4:1 C#4:4 D4:8 R:4 R:58 D4+F4+A4:12";
        String guitar = "GUITAR: R:30 A3:1 G3:1 A3:6 R:2 E3:2 F3:2 C#3:2 D3:8 R:4 R:30 D3+F3+A3:12";
        String low = "A2:1 G2:1 A2:6 R:2 G2:1 F2:1 E2:1 D2:1 C#2:4 D2:8 R:4 D2+A2:12";
        return Song.compose("toccata", "Toccata d-Moll (Bach)", 4, harp, guitar, "DIDGERIDOO: R:58 " + low, "BASS 0.8: R:58 " + low);
    }

    /** Original: "Skelett-Shuffle" – bony xylophone swing for Halloween. */
    static Song skeletonShuffle() {
        String melody = "D5:2 R:1 D5:1 F5:2 D5:2 A5:2 G5:2 F5:2 E5:2 D5:2 R:1 D5:1 F5:2 A5:2 C#5:4 R:4 "
                + "D5:2 R:1 D5:1 F5:2 D5:2 Bb5:2 A5:2 G5:2 F5:2 E5:2 G5:2 C#5:2 E5:2 D5:6 R:2 ";
        String bass = "D2:2 A2:2 D2:2 A2:2 D2:2 A2:2 D2:2 A2:2 D2:2 A2:2 D2:2 A2:2 A1:2 E2:2 A1:2 C#2:2 "
                + "D2:2 A2:2 D2:2 A2:2 Bb1:2 F2:2 Bb1:2 F2:2 A1:2 E2:2 A1:2 C#2:2 D2:2 A2:2 D2:4 ";
        return Song.compose("skeleton_shuffle", "Skelett-Shuffle", 3, "XYLOPHONE: " + rep(melody, 3), "BASS 0.9: " + rep(bass, 3),
                "SNARE 0.35: " + rep("R:2 C4:2", 48), "BASEDRUM 0.5: " + rep("C4:4", 48), "BIT 0.25: R:64 " + rep("D4:1 R:3", 16));
    }

    /** Traditional (Robert Burns, 1788) – Auld Lang Syne, the New Year's song. */
    static Song auldLangSyne() {
        String melody = "C4:2 F4:3 F4:1 F4:2 A4:2 G4:3 F4:1 G4:2 A4:2 F4:3 F4:1 A4:2 C5:2 D5:6 "
                + "D5:2 C5:3 A4:1 A4:2 F4:2 G4:3 F4:1 G4:2 A4:2 F4:3 D4:1 D4:2 C4:2 F4:6 "
                + "D5:2 C5:3 A4:1 A4:2 F4:2 G4:3 F4:1 G4:2 D5:2 C5:3 A4:1 A4:2 C5:2 D5:6 "
                + "F5:2 C5:3 A4:1 A4:2 F4:2 G4:3 F4:1 G4:2 A4:2 F4:3 D4:1 D4:2 C4:2 F4:8";
        String bass = "BASS 0.75: R:2 " + rep("F2:8 C3:8 F2:8 Bb2:8 F2:8 C3:8 D3:4 C3:4 F2:8", 2);
        String pad = "CHIME 0.25: R:2 " + rep("F3+A3:8 E3+G3:8 F3+A3:8 F3+Bb3:8 F3+A3:8 E3+G3:8 D3+F3:4 E3+G3:4 F3+A3:8", 2);
        return Song.compose("auld_lang_syne", "Auld Lang Syne", 5, "HARP: " + melody, "FLUTE 0.5: " + melody, bass, pad);
    }

    /** Johann Strauss II – An der schönen blauen Donau (1866), the waltz of every New Year's concert. */
    static Song danube() {
        String a = "D4:2 D4:2 F#4:2 A4:4 A4:2 R:2 A5:2 A5:2 R:2 F#5:2 F#5:2 R:2 ";
        String b = "D4:2 D4:2 F#4:2 A4:4 A4:2 R:2 A5:2 A5:2 R:2 G5:2 G5:2 R:2 ";
        String c = "C#4:2 C#4:2 E4:2 B4:4 B4:2 R:2 B5:2 B5:2 R:2 G5:2 G5:2 R:2 ";
        String d = "C#4:2 C#4:2 E4:2 B4:4 B4:2 R:2 B5:2 B5:2 R:2 F#5:2 F#5:2 R:2 ";
        String melody = a + b + c + d + "D4:2 D4:2 F#4:2 A4:2 D5:4 R:2 D6:2 D6:2 R:2 A5:2 A5:2 R:2 "
                + "D4:2 D4:2 F#4:2 A4:2 D5:4 R:2 D6:2 D6:2 R:2 B5:2 B5:2 R:2 E4:2 E4:2 G4:2 B4:6 G5:6 F#5:6 D5:2 F#5:2 E5:2 A4:4 D5:8";
        String bass = "BASS 0.8: " + rep("D2:2 F#3+A3:2 F#3+A3:2", 8) + rep("A1:2 E3+G3:2 E3+G3:2", 8)
                + rep("D2:2 F#3+A3:2 F#3+A3:2", 8) + rep("A1:2 E3+G3:2 E3+G3:2", 4) + "D2:12";
        return Song.compose("danube", "Donauwalzer (Strauss)", 4, "HARP: " + melody, "BELL 0.35: " + melody, bass);
    }
}
