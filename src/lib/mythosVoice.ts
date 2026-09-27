/**
 * Mythos-Stimme: natürlich klingende Sprachausgabe mit Piper (Open Source, MIT, rhasspy/piper),
 * läuft komplett lokal im Browser (WebAssembly + ONNX). Die Stimme wird beim ersten Mal
 * geladen (~60 MB) und danach im Browser gespeichert. Bis dahin spricht die Browser-Stimme.
 *
 * Wiedergabe über Web Audio mit Analyser → der Logo-Orb bewegt sich zur echten Stimme.
 */

export type VoiceOption = { id: string; label: string };
export const VOICES: VoiceOption[] = [
  { id: "de_DE-thorsten-medium", label: "Thorsten – natürlich (empfohlen)" },
  { id: "de_DE-thorsten_emotional-medium", label: "Thorsten – lebendig" },
  { id: "de_DE-kerstin-low", label: "Kerstin – weiblich" },
  { id: "de_DE-ramona-low", label: "Ramona – weiblich" },
  { id: "browser", label: "Browser-Stimme (ohne Download)" },
];
const DEFAULT_VOICE = VOICES[0].id;
const VOICE_KEY = "mythos.voice";
// Muss zur installierten onnxruntime-web-Version passen (package.json).
const ORT_WASM = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.30.0/dist/";

export type LoadState = { status: "idle" | "loading" | "ready" | "error"; pct: number };

export function getVoice(): string {
  try { return localStorage.getItem(VOICE_KEY) || DEFAULT_VOICE; } catch { return DEFAULT_VOICE; }
}
export function setVoice(id: string) {
  try { localStorage.setItem(VOICE_KEY, id); } catch { /* egal */ }
  loaded = null; loadedVoice = "";
  emit({ status: "idle", pct: 0 });
}

/** Datei aus dem Browser-Cache (oder einmal aus dem Netz) als Blob-URL. */
const blobUrls = new Map<string, Promise<string>>();
function cachedBlobUrl(url: string, type?: string): Promise<string> {
  let p = blobUrls.get(url);
  if (!p) {
    p = (async () => {
      let res: Response | undefined;
      let cache: Cache | undefined;
      try { cache = await caches.open("mythos-voice"); res = await cache.match(url); } catch { /* ohne Cache */ }
      if (!res) {
        res = await fetch(url);
        if (!res.ok) throw new Error("Download fehlgeschlagen: " + url);
        try { await cache?.put(url, res.clone()); } catch { /* egal */ }
      }
      const blob = await res.blob();
      return URL.createObjectURL(type ? new Blob([blob], { type }) : blob);
    })();
    p.catch(() => blobUrls.delete(url));
    blobUrls.set(url, p);
  }
  return p;
}

// ---------- Laden der Piper-Stimme (einmal pro Seite, in einem Web Worker) ----------
export type PiperVoice = { predict: (text: string) => Promise<ArrayBuffer> };
let loaded: Promise<PiperVoice> | null = null;
let loadedVoice = "";
let worker: Worker | null = null;
let state: LoadState = { status: "idle", pct: 0 };
const subs = new Set<(s: LoadState) => void>();
function emit(s: LoadState) { state = s; subs.forEach((f) => f(s)); }
export function onLoadState(fn: (s: LoadState) => void) { subs.add(fn); fn(state); return () => { subs.delete(fn); }; }

// Standard-Adressen der Piper-Aussprache-Engine (siehe @mintplex-labs/piper-tts-web fixtures).
const PIPER_WASM_BASE = "https://cdn.jsdelivr.net/npm/@diffusionstudio/piper-wasm@1.0.0/build/piper_phonemize";

/** Piper-Stimme laden (oder die laufende Ladung zurückgeben). */
export function loadVoice(): Promise<PiperVoice> | null {
  const voiceId = getVoice();
  if (voiceId === "browser") return null;
  if (loaded && loadedVoice === voiceId) return loaded;
  loadedVoice = voiceId;
  worker?.terminate();
  emit({ status: "loading", pct: 0 });
  loaded = (async () => {
    // Die Aussprache-Engine (9 MB) würde sonst bei jedem Satz neu geladen → einmal holen, im Speicher halten.
    const [piperData, piperWasm] = await Promise.all([cachedBlobUrl(PIPER_WASM_BASE + ".data"), cachedBlobUrl(PIPER_WASM_BASE + ".wasm", "application/wasm")]);
    const w = new Worker(new URL("./mythosVoice.worker.ts", import.meta.url), { type: "module" });
    worker = w;
    const pending = new Map<number, { res: (b: ArrayBuffer) => void; rej: (e: Error) => void }>();
    let nextId = 1;
    await new Promise<void>((res, rej) => {
      w.onmessage = (e) => {
        const m = e.data;
        if (m.type === "progress") emit({ status: "loading", pct: m.pct });
        else if (m.type === "ready") res();
        else if (m.type === "audio") { pending.get(m.id)?.res(m.wav); pending.delete(m.id); }
        else if (m.type === "error") {
          if (m.id == null) rej(new Error(m.message));
          else { pending.get(m.id)?.rej(new Error(m.message)); pending.delete(m.id); }
        }
      };
      w.onerror = (e) => rej(new Error(e.message || "Worker-Fehler"));
      w.postMessage({ type: "init", voiceId, wasmPaths: { onnxWasm: ORT_WASM, piperData, piperWasm } });
    });
    emit({ status: "ready", pct: 100 });
    return {
      predict: (text: string) => new Promise<ArrayBuffer>((res, rej) => {
        const id = nextId++;
        pending.set(id, { res, rej });
        w.postMessage({ type: "predict", id, text });
      }),
    };
  })().catch((e) => {
    console.error("[voice] Piper konnte nicht geladen werden", e);
    loaded = null;
    emit({ status: "error", pct: 0 });
    throw e;
  });
  return loaded;
}

// ---------- Text vorbereiten ----------
export function cleanForSpeech(text: string): string {
  return text
    .replace(/```[\s\S]*?```/g, " (Code siehe Chat) ")
    .replace(/<tool>[\s\S]*?<\/tool>/g, " ")
    .replace(/!\[.*?\]\(.*?\)/g, " ")
    .replace(/\[(.*?)\]\(.*?\)/g, "$1")
    .replace(/https?:\/\/\S+/g, " Link ")
    .replace(/[#*_`>|~]/g, "")
    .replace(/^\s*[-•]\s+/gm, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 3000);
}
/** In Sätze bzw. kurze Stücke teilen, damit die erste Antwort schnell hörbar ist. */
export function splitSentences(text: string, max = 220): string[] {
  const out: string[] = [];
  let cur = "";
  for (const part of text.split(/(?<=[.!?…:;])\s+/)) {
    if (cur && (cur + " " + part).length > max) { out.push(cur); cur = part; }
    else cur = cur ? cur + " " + part : part;
    while (cur.length > max * 1.5) { const cut = cur.lastIndexOf(" ", max) > 40 ? cur.lastIndexOf(" ", max) : max; out.push(cur.slice(0, cut)); cur = cur.slice(cut).trim(); }
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

// ---------- Browser-Stimme (Rückfall) ----------
function pickBrowserVoice(lang: string): SpeechSynthesisVoice | null {
  if (typeof window === "undefined" || !("speechSynthesis" in window)) return null;
  const voices = window.speechSynthesis.getVoices();
  const short = lang.slice(0, 2).toLowerCase();
  const pool = voices.filter((v) => v.lang?.toLowerCase().startsWith(short));
  for (const n of ["natural", "neural", "online", "google deutsch", "google", "premium", "enhanced", "anna", "katja", "conrad"]) {
    const hit = pool.find((v) => v.name.toLowerCase().includes(n));
    if (hit) return hit;
  }
  return pool.find((v) => !v.localService) ?? pool[0] ?? null;
}

// ---------- Sprecher ----------
type SpeakOpts = { lang?: string; onStart?: () => void; onEnd?: () => void };

/**
 * Spricht Text mit Piper (Satz für Satz, der nächste Satz wird schon berechnet während der
 * aktuelle läuft). `level()` liefert die aktuelle Lautstärke 0..1 für Animationen.
 */
export class MythosSpeaker {
  private ctx: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private source: AudioBufferSourceNode | null = null;
  private data = new Uint8Array(256);
  private token = 0;
  private fakeUntil = 0;

  private audio() {
    if (!this.ctx) {
      const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new Ctx();
      this.analyser = this.ctx.createAnalyser();
      this.analyser.fftSize = 512;
      this.analyser.smoothingTimeConstant = 0.6;
      this.analyser.connect(this.ctx.destination);
      this.data = new Uint8Array(this.analyser.fftSize);
    }
    if (this.ctx.state === "suspended") this.ctx.resume().catch(() => {});
    return this.ctx;
  }

  /** Muss einmal in einem Klick-Handler aufgerufen werden (Autoplay-Regeln der Browser). */
  unlock() { try { this.audio(); } catch { /* egal */ } }

  /** Lautstärke der Stimme 0..1 (bei der Browser-Stimme angenähert). */
  level(): number {
    if (this.analyser && this.source) {
      this.analyser.getByteTimeDomainData(this.data);
      let sum = 0;
      for (let i = 0; i < this.data.length; i++) { const v = (this.data[i] - 128) / 128; sum += v * v; }
      return Math.min(1, Math.sqrt(sum / this.data.length) * 3.2);
    }
    if (Date.now() < this.fakeUntil) { const t = Date.now() / 1000; return 0.35 + 0.3 * Math.abs(Math.sin(t * 7.3)) * Math.abs(Math.sin(t * 2.1 + 1)); }
    return 0;
  }

  stop() {
    this.token++;
    try { this.source?.stop(); } catch { /* schon aus */ }
    this.source = null; this.fakeUntil = 0;
    try { window.speechSynthesis?.cancel(); } catch { /* egal */ }
  }

  async speak(raw: string, opts: SpeakOpts = {}) {
    this.stop();
    const my = ++this.token;
    const text = cleanForSpeech(raw);
    if (!text) { opts.onEnd?.(); return; }
    const sentences = splitSentences(text);
    const session = loadVoice();
    // Piper noch nicht fertig geladen (erstes Mal) → Browser-Stimme, Piper lädt im Hintergrund weiter.
    const ready = session && state.status === "ready" ? await session.catch(() => null) : null;
    if (my !== this.token) return;    if (!ready) return this.speakBrowser(sentences, opts, my);

    // Satz-Pipeline: nächster Satz wird berechnet, während der aktuelle läuft.
    const ctx = this.audio();
    const synth = (s: string) => ready.predict(s).then((ab) => ctx.decodeAudioData(ab)).catch(() => null);
    let next = synth(sentences[0]);
    let started = false;
    for (let i = 0; i < sentences.length; i++) {
      const buf = await next;
      if (my !== this.token) return;
      if (i + 1 < sentences.length) next = synth(sentences[i + 1]);
      if (!buf) continue;
      if (!started) { started = true; opts.onStart?.(); }
      await new Promise<void>((res) => {
        const src = ctx.createBufferSource();
        src.buffer = buf;
        src.connect(this.analyser as AnalyserNode);
        src.onended = () => res();
        this.source = src;
        src.start();
      });
      if (my !== this.token) return;
    }
    this.source = null;
    if (my === this.token) opts.onEnd?.();
  }

  private speakBrowser(sentences: string[], opts: SpeakOpts, my: number) {
    if (!("speechSynthesis" in window)) { opts.onEnd?.(); return; }
    const lang = opts.lang || "de-DE";
    const v = pickBrowserVoice(lang);
    sentences.forEach((s, i) => {
      const u = new SpeechSynthesisUtterance(s);
      u.lang = lang; u.rate = 1.02; u.pitch = 1.0;
      if (v) u.voice = v;
      u.onstart = () => { if (my !== this.token) return; this.fakeUntil = Date.now() + 60000; if (i === 0) opts.onStart?.(); };
      const done = () => { if (i === sentences.length - 1 && my === this.token) { this.fakeUntil = 0; opts.onEnd?.(); } };
      u.onend = done; u.onerror = done;
      window.speechSynthesis.speak(u);
    });
  }
}

/** Gemeinsamer Sprecher für die ganze Seite. */
export const speaker = new MythosSpeaker();
