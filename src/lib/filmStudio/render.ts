// Mythos Filmstudio: Zeitplan, Tonmischung und Bild für Bild zeichnen. Alles läuft im Browser:
// Bilder mit Kamerafahrt auf einem Canvas, Sprecher (Piper) und Musik (Synth) über Web Audio,
// Export als Video über MediaRecorder. Kein Server, keine Kosten.
import { loadVoice } from "@/lib/mythosVoice";
import { renderSong, type MusicSpec } from "@/lib/synthSong";
import type { Camera, Film, Scene } from "./script";
import { speechSeconds } from "./script";

export const W = 1280, H = 720, FPS = 30;
export const INTRO = 0.6;      // Sekunden Stille vor dem ersten Sprechertext
export const CROSSFADE = 0.7;  // Überblendung zwischen Szenen
export const CREDITS = 3.5;    // Abspann

export type Slot = { scene: Scene; index: number; start: number; end: number; voiceStart: number; voiceDur: number };
export type Timeline = { slots: Slot[]; scenesEnd: number; total: number };
export type Picture = CanvasImageSource & { width: number; height: number };

/** Zeitplan: jede Szene dauert mindestens so lange wie gewünscht und so lange, wie der Sprecher braucht. */
export function buildTimeline(scenes: Scene[], voiceDur: (s: Scene) => number | undefined): Timeline {
  let t = 0;
  const slots = scenes.map((scene, index) => {
    const lead = index === 0 ? INTRO : 0.35;
    const vd = scene.narration.trim() ? voiceDur(scene) ?? speechSeconds(scene.narration) : 0;
    const dur = Math.max(scene.duration, vd ? lead + vd + 0.8 : 0, 2);
    const slot: Slot = { scene, index, start: t, end: t + dur, voiceStart: t + lead, voiceDur: vd };
    t += dur;
    return slot;
  });
  return { slots, scenesEnd: t, total: t + CREDITS };
}

export function slotAt(tl: Timeline, t: number): Slot | undefined {
  return tl.slots.find((s) => t >= s.start && t < s.end) ?? (t >= tl.scenesEnd ? undefined : tl.slots[0]);
}

export const fmtTime = (sec: number) => `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, "0")}`;

// ---------- Bilder ----------
const pictures = new Map<string, Promise<Picture | null>>();

/** Bild so laden, dass es auf den Canvas darf (sonst ließe sich das Video nicht aufnehmen). */
export function loadPicture(src: string): Promise<Picture | null> {
  let p = pictures.get(src);
  if (!p) {
    p = (async () => {
      try {
        const r = await fetch(src);
        if (!r.ok) throw new Error(String(r.status));
        return await createImageBitmap(await r.blob());
      } catch {
        return new Promise<Picture | null>((resolve) => {
          const img = new Image();
          img.crossOrigin = "anonymous";
          img.onload = () => resolve(img);
          img.onerror = () => resolve(null);
          img.src = src;
        });
      }
    })();
    p.then((v) => { if (!v) pictures.delete(src); });
    pictures.set(src, p);
  }
  return p;
}

// ---------- Ton ----------
let decoder: AudioContext | null = null;
function decodeCtx() {
  if (!decoder) decoder = new AudioContext();
  return decoder;
}
export function decodeAudio(buf: ArrayBuffer): Promise<AudioBuffer> {
  return decodeCtx().decodeAudioData(buf.slice(0));
}

const voices = new Map<string, Promise<AudioBuffer | null>>();
const failedVoices = new Set<string>();
/** Sprechertext mit der gewählten Mythos-Stimme (Piper) vertonen. Ohne Piper-Stimme: null (dann nur Untertitel). */
export function narrate(text: string, voiceId: string): Promise<AudioBuffer | null> {
  const key = voiceId + "|" + text;
  let p = voices.get(key);
  if (!p) {
    p = (async () => {
      if (!text.trim() || failedVoices.has(voiceId)) return null;
      const v = loadVoice();
      if (!v) return null;
      const piper = await v.catch((e) => { failedVoices.add(voiceId); throw e; });
      return decodeAudio(await piper.predict(text));
    })().catch((e) => { console.warn("[film] Sprecher fehlgeschlagen", e); voices.delete(key); return null; });
    voices.set(key, p);
  }
  return p;
}

/** Ruhige Filmmusik-Melodie: alle halbe Takte ein Akkordton, mit Pausen. */
function filmMelody(spec: MusicSpec, seed: number): number[] {
  const DEG: Record<string, number> = { i: 0, I: 0, ii: 1, II: 1, iii: 2, III: 2, iv: 3, IV: 3, v: 4, V: 4, vi: 5, VI: 5, vii: 6, VII: 6 };
  const chords = spec.chords?.length ? spec.chords : ["i", "VI", "III", "VII"];
  const out: number[] = [];
  let r = seed || 1;
  const rnd = () => { r = (r * 16807) % 2147483647; return r / 2147483647; };
  for (let bar = 0; bar < (spec.bars ?? 16); bar++) {
    const root = DEG[chords[bar % chords.length].replace(/7|dim|maj/g, "")] ?? 0;
    for (let step = 0; step < 16; step++) {
      const on = step === 0 || (step === 8 && rnd() > 0.35) || (step === 12 && rnd() > 0.8);
      out.push(on ? root + 1 + [0, 2, 4, 7][Math.floor(rnd() * 4)] : 0);
    }
  }
  return out;
}

const songs = new Map<string, Promise<AudioBuffer>>();
/** Musik aus dem Rezept des Films erzeugen (einmal pro Rezept). */
export function makeMusic(spec: MusicSpec, seed = 7): Promise<AudioBuffer> {
  const key = JSON.stringify(spec) + seed;
  let p = songs.get(key);
  if (!p) {
    p = renderSong({ ...spec, bars: 16, melody: filmMelody(spec, seed) }).then((b) => b.arrayBuffer()).then(decodeAudio);
    p.catch(() => songs.delete(key));
    songs.set(key, p);
  }
  return p;
}

/** Fertige Tonspur: Musik (in Schleife, leiser unter dem Sprecher) + Sprecher. */
export async function mixSoundtrack(film: Film, tl: Timeline, narration: Map<string, AudioBuffer>, music: AudioBuffer | null): Promise<AudioBuffer> {
  const sr = 44100;
  const ctx = new OfflineAudioContext(2, Math.ceil(tl.total * sr), sr);
  if (music && film.musicVolume > 0) {
    const src = ctx.createBufferSource();
    src.buffer = music; src.loop = true;
    const g = ctx.createGain();
    const full = film.musicVolume * 0.7, low = full * 0.35;
    g.gain.setValueAtTime(0, 0);
    g.gain.linearRampToValueAtTime(full, 1.5);
    for (const s of tl.slots) {
      if (!s.voiceDur || !narration.get(s.scene.id)) continue;
      g.gain.setTargetAtTime(low, Math.max(0, s.voiceStart - 0.3), 0.15);
      g.gain.setTargetAtTime(full, s.voiceStart + s.voiceDur, 0.4);
    }
    g.gain.setTargetAtTime(full, tl.scenesEnd - 2, 0.3);
    g.gain.setValueAtTime(full, tl.total - 2.5);
    g.gain.linearRampToValueAtTime(0, tl.total);
    src.connect(g).connect(ctx.destination);
    src.start(0);
  }
  for (const s of tl.slots) {
    const buf = narration.get(s.scene.id);
    if (!buf) continue;
    const src = ctx.createBufferSource();
    src.buffer = buf;
    const g = ctx.createGain();
    g.gain.value = film.voiceVolume;
    src.connect(g).connect(ctx.destination);
    src.start(s.voiceStart);
  }
  return ctx.startRendering();
}

// ---------- Bild zeichnen ----------
const ease = (x: number) => 0.5 - Math.cos(Math.PI * Math.max(0, Math.min(1, x))) / 2;

function drawPicture(ctx: CanvasRenderingContext2D, pic: Picture | null | undefined, cam: Camera, p: number, alpha: number, label: string) {
  ctx.save();
  ctx.globalAlpha = alpha;
  if (!pic) {
    const g = ctx.createLinearGradient(0, 0, W, H);
    g.addColorStop(0, "#1e1b4b"); g.addColorStop(1, "#4a044e");
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = "rgba(255,255,255,0.75)"; ctx.font = "600 34px system-ui, sans-serif"; ctx.textAlign = "center";
    ctx.fillText(label, W / 2, H / 2);
    ctx.restore();
    return;
  }
  const e = ease(p);
  let zoom = 1.18, fx = 0, fy = 0;
  if (cam === "zoom-in") zoom = 1.04 + 0.22 * e;
  else if (cam === "zoom-out") zoom = 1.26 - 0.22 * e;
  else if (cam === "pan-left") fx = 1 - 2 * e;
  else if (cam === "pan-right") fx = -1 + 2 * e;
  else if (cam === "pan-up") fy = 1 - 2 * e;
  else fy = -1 + 2 * e;
  const base = Math.max(W / pic.width, H / pic.height);
  const w = pic.width * base * zoom, h = pic.height * base * zoom;
  const x = (W - w) / 2 + fx * (w - W) / 2 * 0.9;
  const y = (H - h) / 2 + fy * (h - H) / 2 * 0.9;
  ctx.drawImage(pic, x, y, w, h);
  ctx.restore();
}

function wrap(ctx: CanvasRenderingContext2D, text: string, max: number): string[] {
  const lines: string[] = [];
  let line = "";
  for (const word of text.split(/\s+/)) {
    const next = line ? line + " " + word : word;
    if (ctx.measureText(next).width > max && line) { lines.push(line); line = word; } else line = next;
  }
  if (line) lines.push(line);
  return lines;
}

/** Sprechertext in Untertitel-Häppchen (höchstens etwa zwei Zeilen). */
export function subtitleChunks(text: string, max = 90): string[] {
  const out: string[] = [];
  for (const sentence of text.replace(/\s+/g, " ").trim().match(/[^.!?…]+[.!?…]*["“”»«]?\s*/g) ?? []) {
    let rest = sentence.trim();
    while (rest.length > max) {
      let cut = rest.lastIndexOf(" ", max);
      const comma = rest.lastIndexOf(", ", max);
      if (comma > max * 0.5) cut = comma + 1;
      if (cut <= 0) cut = max;
      out.push(rest.slice(0, cut).trim());
      rest = rest.slice(cut).trim();
    }
    if (rest) out.push(rest);
  }
  return out;
}

function drawSubtitle(ctx: CanvasRenderingContext2D, slot: Slot, t: number) {
  const chunks = subtitleChunks(slot.scene.narration);
  if (!chunks.length || !slot.voiceDur) return;
  const p = (t - slot.voiceStart) / slot.voiceDur;
  if (p < 0 || p > 1.05) return;
  const total = chunks.reduce((n, c) => n + c.length, 0);
  let acc = 0, text = chunks[chunks.length - 1];
  for (const c of chunks) { acc += c.length; if (p * total <= acc) { text = c; break; } }
  ctx.save();
  ctx.font = "600 30px system-ui, sans-serif";
  ctx.textAlign = "center";
  const lines = wrap(ctx, text, W - 240);
  const lh = 40, boxH = lines.length * lh + 22, y0 = H - 44 - boxH;
  const bw = Math.max(...lines.map((l) => ctx.measureText(l).width)) + 44;
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.beginPath();
  if (ctx.roundRect) ctx.roundRect((W - bw) / 2, y0, bw, boxH, 12); else ctx.rect((W - bw) / 2, y0, bw, boxH);
  ctx.fill();
  ctx.fillStyle = "#fff";
  lines.forEach((l, i) => ctx.fillText(l, W / 2, y0 + 40 + i * lh - 4));
  ctx.restore();
}

function vignette(ctx: CanvasRenderingContext2D) {
  const g = ctx.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, W * 0.75);
  g.addColorStop(0, "rgba(0,0,0,0)"); g.addColorStop(1, "rgba(0,0,0,0.45)");
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
}

export type FrameData = { film: Film; tl: Timeline; pics: Map<string, Picture | null> };

/** Ein Bild des Films zum Zeitpunkt t (Sekunden) zeichnen. */
export function drawFrame(ctx: CanvasRenderingContext2D, d: FrameData, t: number) {
  const { film, tl, pics } = d;
  ctx.fillStyle = "#000"; ctx.fillRect(0, 0, W, H);
  if (t >= tl.scenesEnd || !tl.slots.length) {
    const a = Math.min(1, (t - tl.scenesEnd) / 0.8, (tl.total - t) / 0.6);
    ctx.save(); ctx.globalAlpha = Math.max(0, a); ctx.fillStyle = "#fff"; ctx.textAlign = "center";
    ctx.font = "700 54px Georgia, serif"; ctx.fillText(film.title, W / 2, H / 2 - 10);
    ctx.font = "400 24px system-ui, sans-serif"; ctx.fillStyle = "rgba(255,255,255,0.7)";
    ctx.fillText("Ein Film aus dem Mythos Filmstudio", W / 2, H / 2 + 40);
    ctx.restore();
    return;
  }
  const slot = slotAt(tl, t)!;
  const dur = slot.end - slot.start;
  drawPicture(ctx, pics.get(slot.scene.id), slot.scene.camera, (t - slot.start) / dur, 1, slot.scene.heading);
  const next = tl.slots[slot.index + 1];
  if (next && t > slot.end - CROSSFADE) {
    const a = (t - (slot.end - CROSSFADE)) / CROSSFADE;
    drawPicture(ctx, pics.get(next.scene.id), next.scene.camera, 0, a, next.scene.heading);
  }
  vignette(ctx);
  // Ein- und Ausblenden
  const fade = Math.max(1 - t / 1, 1 - (tl.scenesEnd - t) / 1, 0);
  if (fade > 0) { ctx.fillStyle = `rgba(0,0,0,${fade})`; ctx.fillRect(0, 0, W, H); }
  // Titel am Anfang
  if (t < 5) {
    const a = Math.min(1, (t - 0.4) / 0.8, (5 - t) / 0.8);
    if (a > 0) {
      ctx.save(); ctx.globalAlpha = a; ctx.textAlign = "center"; ctx.shadowColor = "rgba(0,0,0,0.8)"; ctx.shadowBlur = 18;
      ctx.fillStyle = "#fff"; ctx.font = "700 64px Georgia, serif";
      wrap(ctx, film.title, W - 200).forEach((l, i, all) => ctx.fillText(l, W / 2, H / 2 - (all.length - 1) * 36 + i * 72 - 40));
      if (film.logline) {
        ctx.font = "italic 400 26px Georgia, serif"; ctx.fillStyle = "rgba(255,255,255,0.85)";
        wrap(ctx, film.logline, W - 320).slice(0, 2).forEach((l, i) => ctx.fillText(l, W / 2, H / 2 + 40 + i * 34));
      }
      ctx.restore();
    }
  }
  if (film.subtitles) drawSubtitle(ctx, slot, t);
}

// ---------- Video aufnehmen ----------
export function videoMime(): string {
  const c = [
    "video/mp4;codecs=avc1.42E01E,mp4a.40.2",
    "video/webm;codecs=vp9,opus",
    "video/webm;codecs=vp8,opus",
    "video/webm",
  ];
  return c.find((m) => typeof MediaRecorder !== "undefined" && MediaRecorder.isTypeSupported(m)) || "video/webm";
}

/** Film in Echtzeit als Video aufnehmen (Bild + Tonspur). */
export async function recordFilm(d: FrameData, soundtrack: AudioBuffer, onProgress: (t: number) => void, signal: { stop: boolean }): Promise<Blob> {
  if (typeof MediaRecorder === "undefined") throw new Error("Dieser Browser kann keine Videos aufnehmen. Bitte Chrome, Edge oder Firefox nutzen.");
  const canvas = document.createElement("canvas");
  canvas.width = W; canvas.height = H;
  const ctx = canvas.getContext("2d", { alpha: false })!;
  drawFrame(ctx, d, 0);
  const ac = new AudioContext();
  await ac.resume();
  const dest = ac.createMediaStreamDestination();
  const src = ac.createBufferSource();
  src.buffer = soundtrack;
  src.connect(dest);
  const stream = new MediaStream([...canvas.captureStream(FPS).getVideoTracks(), ...dest.stream.getAudioTracks()]);
  const mime = videoMime();
  const rec = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 8_000_000, audioBitsPerSecond: 192_000 });
  const chunks: Blob[] = [];
  rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
  const done = new Promise<Blob>((res) => { rec.onstop = () => res(new Blob(chunks, { type: mime.split(";")[0] })); });
  rec.start(1000);
  const t0 = ac.currentTime + 0.05;
  src.start(t0);
  await new Promise<void>((res) => {
    const timer = setInterval(() => {
      const t = ac.currentTime - t0;
      if (t >= d.tl.total || signal.stop) { clearInterval(timer); res(); return; }
      drawFrame(ctx, d, Math.max(0, t));
      onProgress(Math.max(0, t));
    }, 1000 / FPS);
  });
  rec.stop();
  src.stop();
  const blob = await done;
  void ac.close();
  stream.getTracks().forEach((tr) => tr.stop());
  if (signal.stop) throw new Error("abgebrochen");
  return blob;
}
