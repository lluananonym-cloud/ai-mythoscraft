// Mythos Filmstudio: Drehbuch und Szenenplan. Aus einer Idee wird per KI ein Film-Projekt
// mit Szenen (Sprechertext, Bild-Beschreibung, Kamerabewegung, Dauer) und einem Musik-Rezept.
import { askAI, extractJson } from "@/lib/labs";
import type { MusicSpec } from "@/lib/synthSong";

export type Camera = "zoom-in" | "zoom-out" | "pan-left" | "pan-right" | "pan-up" | "pan-down";
export const CAMERAS: { id: Camera; label: string }[] = [
  { id: "zoom-in", label: "Heranfahren" },
  { id: "zoom-out", label: "Wegfahren" },
  { id: "pan-left", label: "Schwenk nach links" },
  { id: "pan-right", label: "Schwenk nach rechts" },
  { id: "pan-up", label: "Schwenk nach oben" },
  { id: "pan-down", label: "Schwenk nach unten" },
];

export type Scene = {
  id: string;
  heading: string;
  narration: string;
  /** Bild-Beschreibung (Englisch, ohne Stil; der Stil des Films kommt beim Erzeugen dazu). */
  prompt: string;
  camera: Camera;
  /** Gewünschte Dauer in Sekunden; wird verlängert, wenn der Sprecher länger braucht. */
  duration: number;
  image?: string;
};

export type Film = {
  id: string;
  title: string;
  logline: string;
  idea: string;
  style: string;
  characters: string;
  music: MusicSpec;
  musicVolume: number;
  voiceVolume: number;
  subtitles: boolean;
  scenes: Scene[];
  created_at: string;
  updated_at: string;
};

export const LENGTHS = [
  { sec: 30, label: "30 Sek." },
  { sec: 60, label: "1 Min." },
  { sec: 120, label: "2 Min." },
  { sec: 180, label: "3 Min." },
];

export const STYLES = [
  { id: "kino", label: "Kino", prompt: "cinematic film still, anamorphic lens, dramatic lighting, rich color grade, highly detailed, 35mm film grain" },
  { id: "anime", label: "Anime", prompt: "anime film still, Studio Ghibli inspired, painterly backgrounds, vibrant colors, detailed" },
  { id: "pixar", label: "3D-Animation", prompt: "3D animated movie still, Pixar style, soft global illumination, expressive characters, detailed" },
  { id: "doku", label: "Doku", prompt: "documentary photograph, natural light, realistic, National Geographic style, sharp detail" },
  { id: "noir", label: "Film noir", prompt: "black and white film noir still, high contrast, hard shadows, moody, 1940s cinema" },
  { id: "comic", label: "Comic", prompt: "graphic novel panel, bold ink lines, flat vivid colors, dynamic composition" },
];

export const uid = () => Math.random().toString(36).slice(2, 10);

/** Anzahl Szenen für eine Filmlänge: etwa eine Szene alle 8 Sekunden, 3 bis 20. */
export function sceneCount(seconds: number): number {
  return Math.max(3, Math.min(20, Math.round(seconds / 8)));
}

/** Ungefähre Sprechdauer eines Textes (deutsch, ruhiges Erzähltempo). */
export function speechSeconds(text: string): number {
  const words = text.trim().split(/\s+/).filter(Boolean).length;
  return words ? words / 2.3 + 0.6 : 0;
}

const CAMERA_IDS = CAMERAS.map((c) => c.id);
const clamp = (n: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, n));

type RawScene = Partial<Record<keyof Scene, unknown>> & { image?: unknown; text?: unknown; title?: unknown };
type RawFilm = { title?: unknown; logline?: unknown; style?: unknown; characters?: unknown; music?: Partial<MusicSpec>; scenes?: RawScene[] };

const str = (v: unknown, max = 2000) => (typeof v === "string" ? v.trim().slice(0, max) : "");

function normMusic(m: Partial<MusicSpec> | undefined): MusicSpec {
  const pick = <T,>(v: unknown, ok: readonly T[], d: T): T => (ok.includes(v as T) ? (v as T) : d);
  return {
    title: str(m?.title, 80) || undefined,
    bpm: clamp(Number(m?.bpm) || 84, 60, 160),
    key: pick(m?.key, ["C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B"], "D"),
    scale: pick(m?.scale, ["major", "minor"] as const, "minor"),
    bars: 16,
    chords: Array.isArray(m?.chords) && m!.chords!.length ? m!.chords!.slice(0, 8).map(String) : ["i", "VI", "III", "VII"],
    bass_pattern: pick(m?.bass_pattern, ["root", "walk", "octave"] as const, "root"),
    drums: pick(m?.drums, ["none", "soft", "beat", "driving"] as const, "soft"),
    lead: pick(m?.lead, ["sine", "square", "saw", "triangle", "pluck"] as const, "triangle"),
    pad: m?.pad !== false,
  };
}

/** KI-Antwort in ein sauberes Film-Projekt verwandeln (fehlende Felder auffüllen, Grenzen einhalten). */
export function normalizeFilm(raw: RawFilm | null, idea: string, seconds: number, stylePrompt: string): Film {
  if (!raw || !Array.isArray(raw.scenes) || !raw.scenes.length) throw new Error("Das Drehbuch kam unvollständig an. Bitte nochmal versuchen.");
  const per = seconds / Math.max(1, raw.scenes.length);
  const scenes: Scene[] = raw.scenes.slice(0, 24).map((s, i) => {
    const narration = str(s.narration ?? s.text, 600);
    const prompt = str(s.prompt ?? s.image, 600) || narration || idea;
    return {
      id: uid(),
      heading: str(s.heading ?? s.title, 120) || `Szene ${i + 1}`,
      narration,
      prompt,
      camera: CAMERA_IDS.includes(s.camera as Camera) ? (s.camera as Camera) : CAMERA_IDS[i % CAMERA_IDS.length],
      duration: clamp(Math.round(Number(s.duration) || per), 3, 30),
    };
  });
  const now = new Date().toISOString();
  return {
    id: uid(),
    title: str(raw.title, 120) || "Mein Film",
    logline: str(raw.logline, 400),
    idea,
    style: str(raw.style, 400) ? `${stylePrompt}, ${str(raw.style, 400)}` : stylePrompt,
    characters: str(raw.characters, 600),
    music: normMusic(raw.music),
    musicVolume: 0.5,
    voiceVolume: 1,
    subtitles: true,
    scenes,
    created_at: now,
    updated_at: now,
  };
}

const SYSTEM = `Du bist Drehbuchautor, Regisseur und Komponist des Mythos Filmstudios. Aus einer Idee planst du einen kurzen Film, der aus Standbildern mit Kamerafahrt, einer Erzählerstimme und Filmmusik besteht.
Antworte NUR mit JSON in genau diesem Format:
{
  "title": "Filmtitel (deutsch)",
  "logline": "ein Satz, worum es geht",
  "style": "kurze englische Beschreibung der Bildsprache (Licht, Farben, Epoche)",
  "characters": "englische Beschreibung der Hauptfiguren mit Aussehen und Kleidung, damit sie in jedem Bild gleich aussehen (leer, wenn es keine gibt)",
  "music": { "bpm": 60-140, "key": "C|D|E|F|G|A|B (optional mit #/b)", "scale": "major|minor", "chords": ["i","VI","III","VII"], "drums": "none|soft|beat|driving", "lead": "sine|square|saw|triangle|pluck", "bass_pattern": "root|walk|octave", "pad": true },
  "scenes": [
    { "heading": "kurzer Szenentitel", "narration": "Sprechertext auf Deutsch, 1-3 Sätze, bildhaft und packend", "prompt": "englische Bildbeschreibung dieser Einstellung: Motiv, Ort, Perspektive, Stimmung; Hauptfiguren immer mit gleichem Aussehen beschreiben", "camera": "zoom-in|zoom-out|pan-left|pan-right|pan-up|pan-down", "duration": Sekunden }
  ]
}
Regeln: Der Film hat einen klaren Anfang, Höhepunkt und Schluss. Wechsle Einstellungen ab (Totale, Halbnah, Nahaufnahme). Die Summe der Sprechertexte passt zur Filmlänge (etwa 2 Wörter pro Sekunde). Keine Texte oder Schrift in den Bildern.`;

/** Drehbuch und Szenenplan aus einer Idee schreiben lassen. */
export async function writeScript(idea: string, seconds: number, stylePrompt: string): Promise<Film> {
  const n = sceneCount(seconds);
  const user = `Idee: ${idea}\nFilmlänge: etwa ${seconds} Sekunden\nAnzahl Szenen: ${n} (je etwa ${Math.round(seconds / n)} Sekunden)\nBildstil: ${stylePrompt}`;
  let lastErr: Error | null = null;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const text = await askAI(SYSTEM, [{ role: "user", content: user }]);
      return normalizeFilm(extractJson<RawFilm>(text), idea, seconds, stylePrompt);
    } catch (e) {
      lastErr = e as Error;
      if (/anmelden|zu viele|Guthaben/i.test(lastErr.message)) break;
    }
  }
  throw lastErr ?? new Error("Drehbuch konnte nicht geschrieben werden");
}

/** Eine neue Szene, die an die vorhandenen anschließt. */
export async function writeNextScene(film: Film, wish: string): Promise<Scene> {
  const story = film.scenes.map((s, i) => `${i + 1}. ${s.heading}: ${s.narration}`).join("\n");
  const text = await askAI(
    `Du schreibst eine weitere Szene für einen Kurzfilm. Antworte NUR mit JSON: {"heading":"...","narration":"Sprechertext deutsch, 1-3 Sätze","prompt":"englische Bildbeschreibung","camera":"zoom-in|zoom-out|pan-left|pan-right|pan-up|pan-down","duration":Sekunden}`,
    [{ role: "user", content: `Film: ${film.title}\nIdee: ${film.idea}\nFiguren: ${film.characters || "-"}\nBisher:\n${story}\n\nWunsch für die neue Szene: ${wish || "die Geschichte sinnvoll weitererzählen"}` }],
  );
  const s = extractJson<RawScene>(text);
  if (!s) throw new Error("Neue Szene konnte nicht geschrieben werden");
  return normalizeFilm({ scenes: [s] }, film.idea, 8, film.style).scenes[0];
}

/** Vollständiger Bild-Prompt einer Szene: Einstellung + Figuren + Stil des Films. */
export function imagePrompt(film: Pick<Film, "style" | "characters">, scene: Pick<Scene, "prompt">): string {
  return [scene.prompt, film.characters, film.style, "16:9 widescreen, no text, no letters, no watermark"].filter(Boolean).join(", ");
}

// ---------- Speichern im Browser ----------
const KEY = "mythos.filmstudio.films";

export function listFilms(): Film[] {
  try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; }
}

/** Speichert den Film (neueste zuerst). Gibt false zurück, wenn der Speicher voll ist. */
export function saveFilm(film: Film): boolean {
  const others = listFilms().filter((f) => f.id !== film.id);
  const all = [{ ...film, updated_at: new Date().toISOString() }, ...others].slice(0, 30);
  for (let n = all.length; n > 0; n--) {
    try { localStorage.setItem(KEY, JSON.stringify(all.slice(0, n))); return true; } catch { /* älteste Filme weglassen */ }
  }
  return false;
}

export function deleteFilm(id: string) {
  try { localStorage.setItem(KEY, JSON.stringify(listFilms().filter((f) => f.id !== id))); } catch { /* egal */ }
}
