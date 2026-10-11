/**
 * Live-Anruf mit Mythos: wie ein Telefonat – zuhören, antworten, dazwischenreden.
 *
 * - Erkennung: Browser-Spracherkennung (Chrome/Edge). Wo es die nicht gibt (Firefox, Safari ohne Freigabe,
 *   Brave, Desktop-Apps) automatisch Whisper lokal im Browser (src/lib/mythosStt.worker.ts).
 * - Stimme: Piper/Browser-Stimme über `speaker.stream`, Satz für Satz, sobald die Antwort entsteht.
 * - Unterbrechen: Ein eigener Pegel-Detektor auf dem Mikrofon (mit Echo-Unterdrückung) merkt, wenn man
 *   Mythos ins Wort fällt; dann verstummt Mythos sofort und hört zu.
 * Alles läuft im Browser, es gibt keinen neuen Server-Teil.
 */
import { speaker, loadVoice } from "@/lib/mythosVoice";

export type CallPhase = "idle" | "connecting" | "listening" | "thinking" | "speaking" | "ended";
export type CallTurn = { role: "user" | "assistant"; content: string; cut?: boolean };
export type CallEngine = "browser" | "whisper";
export type CallState = {
  phase: CallPhase;
  muted: boolean;
  interim: string;
  turns: CallTurn[];
  engine: CallEngine | null;
  /** Ladefortschritt der lokalen Spracherkennung (nur beim ersten Mal), sonst null. */
  sttLoading: number | null;
  startedAt: number | null;
  error: string | null;
};

export type AskFn = (
  history: { role: "user" | "assistant"; content: string }[],
  text: string,
  signal: AbortSignal,
  onText: (full: string) => void,
) => Promise<string>;

type Opts = {
  lang?: string;
  ask: AskFn;
  onState: (s: CallState) => void;
  /** Frage + Antwort sind fertig (zum Speichern im Chat). */
  onExchange?: (user: string, assistant: string) => void;
  /** Bisheriger Chat als Kontext (wird nicht angezeigt). */
  context?: { role: "user" | "assistant"; content: string }[];
};

const RecognitionCtor = (): any =>
  typeof window === "undefined" ? null : (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition || null;

const HANGUP = /^(auflegen|leg auf|anruf beenden|beende den anruf|tschüss|tschau|ciao|bis dann|bis später)[.!]?$/i;

// ---------- Whisper (lokal) ----------
let whisper: Promise<Worker> | null = null;
let whisperId = 1;
const whisperPending = new Map<number, { res: (t: string) => void; rej: (e: Error) => void }>();
function loadWhisper(onProgress: (pct: number) => void): Promise<Worker> {
  if (whisper) return whisper;
  const start = (model: string, dtype: unknown, device: "webgpu" | "wasm") => new Promise<Worker>((res, rej) => {
    const w = new Worker(new URL("./mythosStt.worker.ts", import.meta.url), { type: "module" });
    const files: Record<string, number> = {};
    w.onmessage = (e) => {
      const m = e.data;
      if (m.type === "progress") {
        files[m.file] = m.pct;
        const v = Object.values(files);
        onProgress(Math.round(v.reduce((a, b) => a + b, 0) / v.length));
      } else if (m.type === "ready") res(w);
      else if (m.type === "text") { whisperPending.get(m.id)?.res(m.text); whisperPending.delete(m.id); }
      else if (m.type === "error") {
        if (m.id == null) { w.terminate(); rej(new Error(m.message)); }
        else { whisperPending.get(m.id)?.rej(new Error(m.message)); whisperPending.delete(m.id); }
      }
    };
    w.onerror = (e) => { w.terminate(); rej(new Error(e.message || "Spracherkennung konnte nicht starten")); };
    w.postMessage({ type: "init", model, dtype, device });
  });
  whisper = (async () => {
    // Grafikkarte (WebGPU): Whisper Small, genau und schnell. Sonst Whisper Base auf dem Prozessor.
    const gpu = (navigator as any).gpu;
    if (gpu) {
      try {
        if (await gpu.requestAdapter()) return await start("onnx-community/whisper-small", { encoder_model: "fp32", decoder_model_merged: "q4" }, "webgpu");
      } catch (e) { console.warn("[call] WebGPU nicht nutzbar, nehme Prozessor", e); }
    }
    return start("onnx-community/whisper-base", "q4", "wasm");
  })();
  whisper.catch(() => { whisper = null; });
  return whisper;
}
async function transcribe(pcm: Float32Array, onProgress: (pct: number) => void): Promise<string> {
  const w = await loadWhisper(onProgress);
  return new Promise((res, rej) => {
    const id = whisperId++;
    whisperPending.set(id, { res, rej });
    w.postMessage({ type: "transcribe", id, audio: pcm, language: "german" }, [pcm.buffer]);
  });
}

/** Whisper schreibt bei Geräuschen gern „[Musik]“, „(Lachen)“ oder „Untertitel von …“. */
function cleanTranscript(t: string) {
  const s = t.replace(/\[[^\]]*\]|\([^)]*\)|\*[^*]*\*/g, " ").replace(/\s+/g, " ").trim();
  if (/^(untertitel|copyright|vielen dank fürs zuschauen|danke fürs zuschauen)/i.test(s)) return "";
  return s;
}

export class LiveCall {
  private o: Opts;
  private lang: string;
  private s: CallState = { phase: "idle", muted: false, interim: "", turns: [], engine: null, sttLoading: null, startedAt: null, error: null };
  private stream: MediaStream | null = null;
  private ctx: AudioContext | null = null;
  private proc: ScriptProcessorNode | null = null;
  private analyser: AnalyserNode | null = null;
  private rec: any = null;
  private recWanted = false;
  private recFails = 0;
  private abort: AbortController | null = null;
  private req = 0;
  private micLevel = 0;
  // Pegel-Detektor
  private noise = 0.008;
  private echo = 0.02;
  private loudMs = 0;
  private quietMs = 0;
  private speechOn = false;
  private segment: Float32Array[] = [];
  private preroll: Float32Array[] = [];
  private segBusy = false;
  private speakMs = 0;
  /** Dazwischenreden erkennen (bei Lautsprechern ohne Echo-Unterdrückung abschaltbar). */
  bargeIn = true;

  constructor(o: Opts) { this.o = o; this.lang = o.lang ?? "de-DE"; }

  get state() { return this.s; }
  private set(p: Partial<CallState>) { this.s = { ...this.s, ...p }; this.o.onState(this.s); }

  /** Lautstärke 0..1 für den Orb: Mythos beim Sprechen, sonst das Mikrofon. */
  level() { return this.s.phase === "speaking" ? speaker.level() : this.s.muted ? 0 : Math.min(1, this.micLevel * 9); }

  /** Muss aus einem Klick heraus aufgerufen werden (Mikrofon-Freigabe, Audio entsperren). */
  async start() {
    if (this.s.phase !== "idle" && this.s.phase !== "ended") return;
    this.set({ phase: "connecting", turns: [], interim: "", error: null, muted: false, startedAt: null });
    speaker.unlock();
    loadVoice()?.catch(() => {});
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true, channelCount: 1 },
      });
    } catch {
      this.set({ phase: "ended", error: "Mythos braucht dein Mikrofon. Bitte erlaube den Zugriff und ruf nochmal an." });
      return;
    }
    const Ctx = window.AudioContext || (window as any).webkitAudioContext;
    const ctx: AudioContext = new Ctx();
    this.ctx = ctx;
    if (ctx.state === "suspended") ctx.resume().catch(() => {});
    const src = ctx.createMediaStreamSource(this.stream);
    const proc = ctx.createScriptProcessor(2048, 1, 1);
    const ratio = ctx.sampleRate / 16000;
    proc.onaudioprocess = (e) => this.onFrame(e.inputBuffer.getChannelData(0), ratio, (2048 / ctx.sampleRate) * 1000);
    // Stumm verbunden, damit der Prozessor läuft, ohne dass man sich selbst hört.
    const mute = ctx.createGain(); mute.gain.value = 0;
    src.connect(proc); proc.connect(mute); mute.connect(ctx.destination);
    this.proc = proc;

    const engine: CallEngine = RecognitionCtor() ? "browser" : "whisper";
    this.set({ engine, phase: "listening", startedAt: Date.now() });
    if (engine === "browser") this.startRecognition();
    else this.warmWhisper();
  }

  hangUp() {
    if (this.s.phase === "idle" || this.s.phase === "ended") return;
    this.req++;
    this.abort?.abort(); this.abort = null;
    speaker.stop();
    this.stopRecognition();
    try { this.proc?.disconnect(); } catch { /* egal */ }
    this.stream?.getTracks().forEach((t) => t.stop());
    this.ctx?.close().catch(() => {});
    this.stream = null; this.ctx = null; this.proc = null; this.analyser = null;
    this.segment = []; this.speechOn = false;
    this.set({ phase: "ended", interim: "" });
  }

  setMuted(muted: boolean) {
    if (this.s.phase === "idle" || this.s.phase === "ended") return;
    this.stream?.getAudioTracks().forEach((t) => { t.enabled = !muted; });
    this.set({ muted, interim: "" });
    this.segment = []; this.speechOn = false;
    if (muted) this.stopRecognition();
    else if (this.s.phase !== "speaking") this.startRecognition();
  }

  /** Mythos ins Wort fallen (Knopf oder Orb). */
  interrupt() {
    if (this.s.phase === "speaking" || this.s.phase === "thinking") this.cutReply();
  }

  // ---------- Mikrofon-Pegel, Unterbrechen und Whisper-Abschnitte ----------
  private onFrame(d: Float32Array, ratio: number, ms: number) {
    let sum = 0;
    for (let i = 0; i < d.length; i++) sum += d[i] * d[i];
    const rms = Math.sqrt(sum / d.length);
    this.micLevel = rms;
    const ph = this.s.phase;
    if (this.s.muted || ph === "ended" || ph === "idle" || ph === "connecting") return;

    // 16 kHz für Whisper (einfaches Mitteln reicht für Sprache).
    const n = Math.floor(d.length / ratio);
    const pcm = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const a = Math.floor(i * ratio), b = Math.min(d.length, Math.floor((i + 1) * ratio));
      let s = 0; for (let j = a; j < b; j++) s += d[j];
      pcm[i] = s / Math.max(1, b - a);
    }

    if (ph === "speaking") {
      // Während Mythos spricht: Restecho lernen, nur deutlich lautere, anhaltende Sprache zählt.
      this.keepPreroll(pcm);
      if (!this.bargeIn) return;
      if (this.speakMs < 600) { this.speakMs += ms; this.echo = Math.max(this.echo, rms * 0.8); return; }
      const thr = Math.max(0.045, this.echo * 2.6, this.noise * 4);
      if (rms > thr) this.loudMs += ms; else { this.loudMs = Math.max(0, this.loudMs - ms / 2); this.echo = this.echo * 0.97 + rms * 0.03; }
      if (this.loudMs >= 320) {
        this.loudMs = 0;
        this.cutReply();
        if (this.s.engine === "whisper") { this.speechOn = true; this.quietMs = 0; this.segment = [...this.preroll]; }
      }
      return;
    }

    const thr = Math.max(0.014, this.noise * 2.8);
    const loud = rms > thr;
    if (!loud && !this.speechOn) this.noise = this.noise * 0.98 + rms * 0.02;
    if (this.s.engine !== "whisper") return;

    if (!this.speechOn) {
      this.keepPreroll(pcm);
      if (loud) { this.loudMs += ms; if (this.loudMs >= 120) { this.speechOn = true; this.quietMs = 0; this.segment = [...this.preroll]; this.set({ interim: "…" }); } }
      else this.loudMs = 0;
      return;
    }
    this.segment.push(pcm);
    this.quietMs = loud ? 0 : this.quietMs + ms;
    const len = this.segment.reduce((a, c) => a + c.length, 0);
    if (this.quietMs > 850 || len > 16000 * 25) {
      const seg = this.segment; this.segment = []; this.speechOn = false; this.loudMs = 0;
      if (len > 16000 * 0.45) this.finishSegment(seg, len);
      else this.set({ interim: "" });
    }
  }

  private keepPreroll(pcm: Float32Array) {
    this.preroll.push(pcm);
    let len = this.preroll.reduce((a, c) => a + c.length, 0);
    while (len > 16000 * 0.4 && this.preroll.length > 1) len -= this.preroll.shift()!.length;
  }

  private warmWhisper() {
    loadWhisper((pct) => this.set({ sttLoading: pct < 100 ? pct : null }))
      .then(() => this.set({ sttLoading: null }))
      .catch((e) => this.set({ sttLoading: null, error: "Spracherkennung konnte nicht geladen werden: " + e.message }));
  }

  private async finishSegment(chunks: Float32Array[], len: number) {
    const pcm = new Float32Array(len);
    let o = 0; for (const c of chunks) { pcm.set(c, o); o += c.length; }
    // Mehrere Abschnitte nacheinander erkennen, nicht gleichzeitig.
    while (this.segBusy) await new Promise((r) => setTimeout(r, 50));
    this.segBusy = true;
    this.set({ interim: "…" });
    try {
      const text = cleanTranscript(await transcribe(pcm, (pct) => this.set({ sttLoading: pct < 100 ? pct : null })));
      this.set({ interim: "", sttLoading: null });
      if (text) this.heard(text);
    } catch (e: any) {
      this.set({ interim: "", error: "Spracherkennung fehlgeschlagen: " + (e?.message || e) });
    } finally { this.segBusy = false; }
  }

  // ---------- Browser-Spracherkennung ----------
  private startRecognition() {
    if (this.s.engine !== "browser" || this.s.muted) return;
    this.recWanted = true;
    if (!this.rec) {
      const Ctor = RecognitionCtor();
      const rec = new Ctor();
      rec.lang = this.lang; rec.continuous = true; rec.interimResults = true;
      rec.onresult = (e: any) => {
        let interim = "", final = "";
        for (let i = e.resultIndex; i < e.results.length; i++) {
          const t = e.results[i][0].transcript;
          if (e.results[i].isFinal) final += t; else interim += t;
        }
        this.recFails = 0;
        if (this.s.phase === "speaking") return; // eigenes Echo ignorieren
        this.set({ interim: final.trim() ? "" : interim });
        if (final.trim()) this.heard(final.trim());
      };
      rec.onerror = (e: any) => {
        const err = e?.error;
        if (err === "no-speech" || err === "aborted") return;
        this.recFails++;
        // Browser hat die Erkennung, aber sie geht nicht (z. B. Brave, Firmen-Richtlinie) → Whisper lokal.
        if (/^(network|not-allowed|service-not-allowed|language-not-supported)$/.test(err) || this.recFails >= 4) this.switchToWhisper();
      };
      rec.onend = () => {
        if (!this.recWanted || this.s.engine !== "browser") return;
        window.setTimeout(() => { if (this.recWanted) try { rec.start(); } catch { /* läuft schon */ } }, 150 + this.recFails * 400);
      };
      this.rec = rec;
    }
    try { this.rec.start(); } catch { /* läuft schon */ }
  }

  private stopRecognition() {
    this.recWanted = false;
    try { this.rec?.abort(); } catch { /* läuft nicht */ }
  }

  private switchToWhisper() {
    if (this.s.engine === "whisper") return;
    this.stopRecognition(); this.rec = null;
    this.set({ engine: "whisper", interim: "" });
    this.warmWhisper();
  }

  // ---------- Gespräch ----------
  /** Antwort abbrechen (Mythos verstummt), Gesagtes bleibt im Verlauf. */
  private cutReply() {
    this.req++;
    this.abort?.abort(); this.abort = null;
    speaker.stop();
    const turns = [...this.s.turns];
    const last = turns[turns.length - 1];
    if (last?.role === "assistant") turns[turns.length - 1] = { ...last, cut: true };
    this.set({ phase: "listening", turns });
    this.startRecognition();
  }

  private heard(text: string) {
    const ph = this.s.phase;
    if (ph === "ended" || ph === "idle" || this.s.muted) return;
    if (HANGUP.test(text.trim())) { this.hangUp(); return; }
    if (ph === "thinking" || ph === "speaking") this.cutReply();
    this.reply(text);
  }

  private async reply(text: string) {
    const my = ++this.req;
    const history = this.s.turns.filter((t) => t.content).map((t) => ({ role: t.role, content: t.cut ? t.content + " …(unterbrochen)" : t.content }));
    const context = [...(this.o.context ?? []), ...history].slice(-24);
    this.set({ phase: "thinking", interim: "", turns: [...this.s.turns, { role: "user", content: text }, { role: "assistant", content: "" }] });
    const ac = new AbortController(); this.abort = ac;
    const out = speaker.stream({
      lang: this.lang,
      onStart: () => { if (my === this.req) { this.stopRecognition(); this.echo = 0.02; this.loudMs = 0; this.speakMs = 0; this.set({ phase: "speaking" }); } },
      onEnd: () => { if (my === this.req) { this.set({ phase: "listening" }); this.startRecognition(); } },
    });
    let sent = 0, latest = "";
    const setText = (full: string) => {
      latest = full;
      if (my !== this.req) return;
      out.push(full.slice(sent)); sent = full.length;
      const turns = [...this.s.turns]; turns[turns.length - 1] = { role: "assistant", content: full };
      this.set({ turns });
    };
    try {
      const full = await this.o.ask(context, text, ac.signal, setText);
      if (my !== this.req) { if (full) this.o.onExchange?.(text, full + " …"); return; }
      setText(full);
      out.end();
      if (!full.trim()) { this.set({ phase: "listening", error: "Keine Antwort erhalten – sag es nochmal." }); this.startRecognition(); return; }
      this.o.onExchange?.(text, full);
    } catch (e: any) {
      if (my !== this.req) { if (latest) this.o.onExchange?.(text, latest + " …"); return; }
      out.end();
      if (e?.name === "AbortError") return;
      this.set({ phase: "listening", error: e?.message || "Verbindungsfehler", turns: this.s.turns.filter((t, i, a) => !(i === a.length - 1 && t.role === "assistant" && !t.content)) });
      this.startRecognition();
    } finally { if (this.abort === ac) this.abort = null; }
  }

  clearError() { if (this.s.error) this.set({ error: null }); }
}
