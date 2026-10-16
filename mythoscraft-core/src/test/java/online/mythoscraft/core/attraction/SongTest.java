package online.mythoscraft.core.attraction;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongTest {

    @Test
    void noteNamesMapToMidi() {
        assertEquals(60, Song.midi("C4"));
        assertEquals(54, Song.midi("F#3"));
        assertEquals(46, Song.midi("Bb2"));
        assertEquals(69, Song.midi("A4"));
    }

    @Test
    void pitchStaysInTheNoteBlockRange() {
        for (Song.Instrument i : Song.Instrument.values()) {
            for (int midi = 20; midi < 110; midi++) {
                float p = i.pitch(midi);
                assertTrue(p >= 0.499f && p <= 2.001f, i + " " + midi + " -> " + p);
            }
        }
        assertEquals(1.0f, Song.Instrument.HARP.pitch(66), 1e-4);
    }

    @Test
    void everyBuiltInSongCompilesAndIsStreamSafe() {
        java.util.Map<String, Song> all = new java.util.LinkedHashMap<>(SongBook.all());
        all.putAll(GrooveBook.all());
        for (Song s : all.values()) {
            assertFalse(s.notes().isEmpty(), s.id());
            assertTrue(s.streamSafe(), s.id());
            assertTrue(s.length() > 200, s.id() + " is too short: " + s.length());
            for (int i = 1; i < s.notes().size(); i++) {
                assertTrue(s.notes().get(i).tick() >= s.notes().get(i - 1).tick(), "sorted " + s.id());
            }
        }
    }

    @Test
    void readsNoteBlockStudioFiles() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        le16(b, 0);
        b.write(5);
        b.write(16);
        le16(b, 10);
        le16(b, 1);
        str(b, "Test");
        str(b, "");
        str(b, "");
        str(b, "");
        le16(b, 1000);
        b.write(0);
        b.write(0);
        b.write(4);
        for (int i = 0; i < 5; i++) {
            le32(b, 0);
        }
        str(b, "");
        b.write(0);
        b.write(0);
        le16(b, 0);
        // tick 0: harp key 45, tick 4: bell key 57 at half volume
        le16(b, 1);
        le16(b, 1);
        b.write(0);
        b.write(45);
        b.write(100);
        b.write(100);
        le16(b, 0);
        le16(b, 0);
        le16(b, 4);
        le16(b, 1);
        b.write(7);
        b.write(57);
        b.write(50);
        b.write(100);
        le16(b, 0);
        le16(b, 0);
        le16(b, 0);
        Song s = Song.readNbs("test", new ByteArrayInputStream(b.toByteArray()), false);
        assertEquals("Test", s.title());
        assertEquals(2, s.notes().size());
        assertEquals(0, s.notes().get(0).tick());
        assertEquals(8, s.notes().get(1).tick());
        assertEquals(1.0f, s.notes().get(0).pitch(), 1e-4);
        assertEquals(2.0f, s.notes().get(1).pitch(), 1e-4);
        assertEquals(0.5f, s.notes().get(1).volume(), 1e-4);
        assertFalse(s.streamSafe());
    }

    @Test
    void ringPassUsesTheWholeMovement() {
        Vector ring = new Vector(0, 0, 0);
        assertTrue(RingService.segmentDistance(new Vector(-5, 1, 0), new Vector(5, 1, 0), ring) < 1.01);
        assertTrue(RingService.segmentDistance(new Vector(3, 0, 0), new Vector(6, 0, 0), ring) > 2.9);
    }

    private static void le16(ByteArrayOutputStream b, int v) {
        b.write(v & 0xFF);
        b.write((v >> 8) & 0xFF);
    }

    private static void le32(ByteArrayOutputStream b, int v) {
        le16(b, v & 0xFFFF);
        le16(b, (v >> 16) & 0xFFFF);
    }

    private static void str(ByteArrayOutputStream b, String s) {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        le32(b, d.length);
        b.write(d, 0, d.length);
    }
}
