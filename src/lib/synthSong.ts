// Client-side WebAudio synth. Takes a MusicSpec (from music-gen edge fn) and
// renders a WAV Blob offline. No models, no memory issues, works everywhere.

export type MusicSpec = {
  title?: string;
  bpm?: number;
  key?: string;
  scale?: "major" | "minor";
  bars?: number;
  chords?: string[];
  melody?: number[];
  bass_pattern?: "root" | "walk" | "octave";
  drums?: "none" | "soft" | "beat" | "driving";
  lead?: "sine" | "square" | "saw" | "triangle" | "pluck";
  pad?: boolean;
};

const NOTE_TO_SEMI: Record<string, number> = {
  C: 0, "C#": 1, Db: 1, D: 2, "D#": 3, Eb: 3, E: 4, F: 5, "F#": 6, Gb: 6,
  G: 7, "G#": 8, Ab: 8, A: 9, "A#": 10, Bb: 10, B: 11,
};

const MAJ = [0, 2, 4, 5, 7, 9, 11];
const MIN = [0, 2, 3, 5, 7, 8, 10];

const ROMAN_DEG: Record<string, number> = {
  I: 0, ii: 1, iii: 2, III: 2, IV: 3, V: 4, vi: 5, VI: 5, VII: 6, vii: 6, "I7": 0,
};

function midi(freqRoot: number, semi: number) {
  return freqRoot * Math.pow(2, semi / 12);
}

function chordSemis(roman: string, scale: number[]) {
  const key = roman.replace(/7$/, "").replace(/dim|maj/, "");
  const deg = ROMAN_DEG[key] ?? 0;
  const root = scale[deg];
  const isMinor = /^[a-z]/.test(key);
  const third = isMinor ? root + 3 : root + 4;
  const fifth = root + 7;
  return [root, third, fifth];
}

function encodeWAV(left: Float32Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + left.length * 2);
  const view = new DataView(buffer);
  const w = (o: number, s: string) => { for (let i = 0; i < s.length; i++) view.setUint8(o + i, s.charCodeAt(i)); };
  w(0, "RIFF"); view.setUint32(4, 36 + left.length * 2, true); w(8, "WAVE");
  w(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true); view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  w(36, "data"); view.setUint32(40, left.length * 2, true);
  let off = 44;
  for (let i = 0; i < left.length; i++, off += 2) {
    const s = Math.max(-1, Math.min(1, left[i]));
    view.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([view], { type: "audio/wav" });
}

export async function renderSong(spec: MusicSpec, onProgress?: (p: number, m: string) => void, vocalBytes?: ArrayBuffer | null): Promise<Blob> {
  const bpm = Math.max(60, Math.min(180, spec.bpm ?? 100));
  const bars = Math.max(4, Math.min(16, spec.bars ?? 8));
  const key = spec.key ?? "C";
  const rootMidi = 48 + (NOTE_TO_SEMI[key] ?? 0); // C3 base
  const rootFreq = 440 * Math.pow(2, (rootMidi - 69) / 12);
  const scaleArr = (spec.scale === "minor" ? MIN : MAJ);
  const beatSec = 60 / bpm;
  const barSec = beatSec * 4;
  const totalSec = bars * barSec;
  const sr = 44100;
  const totalSamples = Math.ceil(totalSec * sr);

  onProgress?.(10, "Setup Audio-Engine…");
  const OfflineCtx: any = (window as any).OfflineAudioContext || (window as any).webkitOfflineAudioContext;
  const ctx = new OfflineCtx(1, totalSamples, sr);

  const master = ctx.createGain();
  master.gain.value = 0.85;
  const comp = ctx.createDynamicsCompressor();
  comp.threshold.value = -14; comp.ratio.value = 4;
  master.connect(comp); comp.connect(ctx.destination);

  const chords = (spec.chords && spec.chords.length ? spec.chords : ["I", "V", "vi", "IV"])
    .slice(0, bars);
  while (chords.length < bars) chords.push(chords[chords.length % chords.length]);

  const leadWave = (spec.lead ?? "triangle") as OscillatorType;

  function playNote(freq: number, start: number, dur: number, gain: number, type: OscillatorType = "sine", detune = 0) {
    const osc = ctx.createOscillator();
    osc.type = type;
    osc.frequency.value = freq;
    osc.detune.value = detune;
    const g = ctx.createGain();
    const a = 0.01, d = 0.08, r = Math.min(0.3, dur * 0.5);
    g.gain.setValueAtTime(0, start);
    g.gain.linearRampToValueAtTime(gain, start + a);
    g.gain.linearRampToValueAtTime(gain * 0.7, start + a + d);
    g.gain.setValueAtTime(gain * 0.7, start + Math.max(a + d, dur - r));
    g.gain.linearRampToValueAtTime(0, start + dur);
    osc.connect(g); g.connect(master);
    osc.start(start); osc.stop(start + dur + 0.02);
  }

  function playKick(t: number) {
    const osc = ctx.createOscillator();
    const g = ctx.createGain();
    osc.frequency.setValueAtTime(120, t);
    osc.frequency.exponentialRampToValueAtTime(40, t + 0.15);
    g.gain.setValueAtTime(0.9, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + 0.18);
    osc.connect(g); g.connect(master);
    osc.start(t); osc.stop(t + 0.2);
  }
  function playSnare(t: number) {
    const bufSize = sr * 0.2;
    const buf = ctx.createBuffer(1, bufSize, sr);
    const d = buf.getChannelData(0);
    for (let i = 0; i < bufSize; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / bufSize, 2);
    const src = ctx.createBufferSource(); src.buffer = buf;
    const g = ctx.createGain(); g.gain.value = 0.35;
    src.connect(g); g.connect(master);
    src.start(t);
  }
  function playHat(t: number, open = false) {
    const bufSize = sr * (open ? 0.12 : 0.04);
    const buf = ctx.createBuffer(1, bufSize, sr);
    const d = buf.getChannelData(0);
    for (let i = 0; i < bufSize; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / bufSize, 3);
    const src = ctx.createBufferSource(); src.buffer = buf;
    const hp = ctx.createBiquadFilter(); hp.type = "highpass"; hp.frequency.value = 6000;
    const g = ctx.createGain(); g.gain.value = 0.18;
    src.connect(hp); hp.connect(g); g.connect(master);
    src.start(t);
  }

  onProgress?.(30, "Komponiere Akkorde…");
  // Chords + bass
  for (let b = 0; b < bars; b++) {
    const barStart = b * barSec;
    const semis = chordSemis(chords[b] || "I", scaleArr);
    // Pad (long chord)
    if (spec.pad !== false) {
      semis.forEach((s, i) => {
        playNote(midi(rootFreq, s + 12), barStart, barSec, 0.06, "sawtooth", i * 4);
      });
    }
    // Bass
    const bassPat = spec.bass_pattern ?? "root";
    for (let beat = 0; beat < 4; beat++) {
      const t = barStart + beat * beatSec;
      let bs = semis[0] - 12;
      if (bassPat === "walk") bs = semis[beat % semis.length] - 12;
      if (bassPat === "octave") bs = (beat % 2 === 0 ? semis[0] - 12 : semis[0]);
      playNote(midi(rootFreq, bs), t, beatSec * 0.9, 0.22, "triangle");
    }
  }

  onProgress?.(55, "Schreibe Melodie…");
  const melody = spec.melody ?? [];
  const stepsPerBar = 16;
  const stepSec = barSec / stepsPerBar;
  for (let i = 0; i < bars * stepsPerBar; i++) {
    const deg = melody[i] ?? (i % 4 === 0 ? ((i / 4) % 7) + 1 : 0);
    if (!deg || deg <= 0) continue;
    const semi = scaleArr[(deg - 1) % 7] + 12 + (deg > 7 ? 12 : 0);
    playNote(midi(rootFreq, semi), i * stepSec, stepSec * 1.2, 0.14, leadWave);
  }

  onProgress?.(75, "Legt Drums drüber…");
  const drums = spec.drums ?? "beat";
  if (drums !== "none") {
    for (let b = 0; b < bars; b++) {
      for (let beat = 0; beat < 4; beat++) {
        const t = b * barSec + beat * beatSec;
        if (drums === "soft") {
          if (beat === 0 || beat === 2) playKick(t);
          if (beat === 1 || beat === 3) playSnare(t);
        } else if (drums === "beat") {
          if (beat === 0 || beat === 2) playKick(t);
          if (beat === 1 || beat === 3) playSnare(t);
          playHat(t); playHat(t + beatSec / 2);
        } else {
          playKick(t);
          if (beat === 1 || beat === 3) playSnare(t);
          for (let s = 0; s < 4; s++) playHat(t + s * beatSec / 4);
        }
      }
    }
  }

  // --- Mix in AI-generated vocals ---
  if (vocalBytes && vocalBytes.byteLength > 0) {
    onProgress?.(80, "Mixe Gesang…");
    try {
      // Decode using a temporary online context (OfflineAudioContext can't decodeAudioData cross-sr reliably in all browsers, but ctx.decodeAudioData works fine)
      const decoded: AudioBuffer = await new Promise((resolve, reject) => {
        // decodeAudioData accepts callback or Promise form; use callback for wider compat
        (ctx as any).decodeAudioData(vocalBytes.slice(0), resolve, reject);
      });
      const vSrc = ctx.createBufferSource();
      vSrc.buffer = decoded;
      // Slight playback rate tweak to fit song length if vocals are longer
      const targetDur = Math.min(decoded.duration, totalSec - 0.1);
      if (decoded.duration > totalSec) vSrc.playbackRate.value = decoded.duration / totalSec;
      const vGain = ctx.createGain(); vGain.gain.value = 1.4;
      // Fade in/out
      vGain.gain.setValueAtTime(0, 0);
      vGain.gain.linearRampToValueAtTime(1.4, 0.3);
      vGain.gain.setValueAtTime(1.4, Math.max(0.3, targetDur - 0.4));
      vGain.gain.linearRampToValueAtTime(0, targetDur);
      vSrc.connect(vGain); vGain.connect(master);
      // Duck instrumental a touch by lowering master while vocal plays — simple: leave as-is, vocal gain > 1
      vSrc.start(0.2);
    } catch (e) {
      console.warn("vocal decode failed", e);
    }
  }

  onProgress?.(88, "Rendere Audio…");
  const rendered = await ctx.startRendering();
  const ch = rendered.getChannelData(0);
  onProgress?.(98, "Verpacke WAV…");
  return encodeWAV(ch, sr);
}
