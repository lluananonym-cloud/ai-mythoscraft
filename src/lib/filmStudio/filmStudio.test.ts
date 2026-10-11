import { describe, expect, it } from "vitest";
import { imagePrompt, normalizeFilm, sceneCount } from "./script";
import { buildTimeline, CREDITS, INTRO, slotAt, subtitleChunks } from "./render";

describe("Filmstudio-Drehbuch", () => {
  it("füllt fehlende Felder auf und hält Grenzen ein", () => {
    const f = normalizeFilm({
      title: "Feuerberg",
      music: { bpm: 999, key: "H" as never, drums: "laut" as never },
      scenes: [
        { heading: "Anfang", narration: "Die Insel schläft.", prompt: "island at dawn", camera: "zoom-in", duration: 100 },
        { text: "Der Berg grollt.", image: "volcano smoke", camera: "wackeln" },
      ],
    }, "Vulkan", 60, "cinematic");
    expect(f.title).toBe("Feuerberg");
    expect(f.scenes).toHaveLength(2);
    expect(f.scenes[0].duration).toBe(30);
    expect(f.scenes[1].narration).toBe("Der Berg grollt.");
    expect(f.scenes[1].prompt).toBe("volcano smoke");
    expect(f.scenes[1].heading).toBe("Szene 2");
    expect(["zoom-in", "zoom-out", "pan-left", "pan-right", "pan-up", "pan-down"]).toContain(f.scenes[1].camera);
    expect(f.music.bpm).toBe(160);
    expect(f.music.key).toBe("D");
    expect(f.music.drums).toBe("soft");
    expect(imagePrompt(f, f.scenes[0])).toContain("island at dawn");
    expect(imagePrompt(f, f.scenes[0])).toContain("cinematic");
  });

  it("lehnt leere Drehbücher ab", () => {
    expect(() => normalizeFilm(null, "x", 30, "s")).toThrow();
    expect(() => normalizeFilm({ scenes: [] }, "x", 30, "s")).toThrow();
  });

  it("plant passend viele Szenen", () => {
    expect(sceneCount(30)).toBe(4);
    expect(sceneCount(10)).toBe(3);
    expect(sceneCount(600)).toBe(20);
  });
});

describe("Filmstudio-Schnitt", () => {
  const scenes = normalizeFilm({ scenes: [
    { narration: "Kurz.", duration: 6 },
    { narration: "", duration: 4 },
  ] }, "x", 10, "s").scenes;

  it("verlängert Szenen, wenn der Sprecher länger braucht", () => {
    const tl = buildTimeline(scenes, (s) => (s.narration ? 9 : undefined));
    expect(tl.slots[0].end).toBeCloseTo(INTRO + 9 + 0.8);
    expect(tl.slots[1].start).toBeCloseTo(tl.slots[0].end);
    expect(tl.slots[1].end - tl.slots[1].start).toBe(4);
    expect(tl.slots[1].voiceDur).toBe(0);
    expect(tl.total).toBeCloseTo(tl.scenesEnd + CREDITS);
    expect(slotAt(tl, tl.slots[1].start + 0.1)?.index).toBe(1);
    expect(slotAt(tl, tl.scenesEnd + 1)).toBeUndefined();
  });

  it("teilt Untertitel in kurze Häppchen", () => {
    const long = "Das ist ein sehr langer Satz, der weit über die erlaubte Länge eines einzelnen Untertitels hinausgeht und deshalb geteilt werden muss. Kurz!";
    const chunks = subtitleChunks(long, 60);
    expect(chunks.length).toBeGreaterThan(2);
    expect(chunks.every((c) => c.length <= 60)).toBe(true);
    expect(chunks.join(" ").replace(/\s+/g, " ")).toBe(long);
  });
});
