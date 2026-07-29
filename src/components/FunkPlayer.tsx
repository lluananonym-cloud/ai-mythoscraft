import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Play, Pause, Download } from "lucide-react";

export type FunkPattern = {
  title?: string;
  bpm?: number;
  key?: string;
  bars?: number;
  bass?: number[];
  kick?: number[];
  snare?: number[];
  hihat?: number[];
  stab?: number[];
  vibe?: string;
};

const midiToFreq = (midi: number) => 440 * Math.pow(2, (midi - 69) / 12);

export default function FunkPlayer({ pattern }: { pattern: FunkPattern }) {
  const [playing, setPlaying] = useState(false);
  const ctxRef = useRef<AudioContext | null>(null);
  const stopRef = useRef<(() => void) | null>(null);

  useEffect(() => () => { stopRef.current?.(); }, []);

  const start = async () => {
    const AC: typeof AudioContext = (window as any).AudioContext || (window as any).webkitAudioContext;
    const ctx: AudioContext = ctxRef.current ?? new AC();
    ctxRef.current = ctx;
    if (ctx.state === "suspended") await ctx.resume();

    const bpm = pattern.bpm ?? 110;
    const stepDur = 60 / bpm / 4; // 16th notes
    const steps = 16;
    const bars = pattern.bars ?? 8;

    // Master bus with subtle compression
    const master = ctx.createGain();
    master.gain.value = 0.7;
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -12;
    comp.ratio.value = 4;
    master.connect(comp).connect(ctx.destination);

    const playKick = (t: number) => {
      const o = ctx.createOscillator(); const g = ctx.createGain();
      o.frequency.setValueAtTime(120, t);
      o.frequency.exponentialRampToValueAtTime(40, t + 0.12);
      g.gain.setValueAtTime(1.0, t);
      g.gain.exponentialRampToValueAtTime(0.001, t + 0.18);
      o.connect(g).connect(master); o.start(t); o.stop(t + 0.2);
    };
    const playSnare = (t: number) => {
      const buf = ctx.createBuffer(1, ctx.sampleRate * 0.2, ctx.sampleRate);
      const d = buf.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / d.length, 2);
      const n = ctx.createBufferSource(); n.buffer = buf;
      const hp = ctx.createBiquadFilter(); hp.type = "highpass"; hp.frequency.value = 1200;
      const g = ctx.createGain(); g.gain.value = 0.5;
      n.connect(hp).connect(g).connect(master); n.start(t);
      const o = ctx.createOscillator(); const og = ctx.createGain();
      o.frequency.value = 200; og.gain.setValueAtTime(0.3, t);
      og.gain.exponentialRampToValueAtTime(0.001, t + 0.08);
      o.connect(og).connect(master); o.start(t); o.stop(t + 0.1);
    };
    const playHat = (t: number) => {
      const buf = ctx.createBuffer(1, ctx.sampleRate * 0.05, ctx.sampleRate);
      const d = buf.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / d.length, 6);
      const n = ctx.createBufferSource(); n.buffer = buf;
      const hp = ctx.createBiquadFilter(); hp.type = "highpass"; hp.frequency.value = 7000;
      const g = ctx.createGain(); g.gain.value = 0.18;
      n.connect(hp).connect(g).connect(master); n.start(t);
    };
    const playBass = (midi: number, t: number) => {
      if (!midi) return;
      const o = ctx.createOscillator(); o.type = "sawtooth";
      o.frequency.value = midiToFreq(midi);
      const lp = ctx.createBiquadFilter(); lp.type = "lowpass";
      lp.frequency.setValueAtTime(800, t);
      lp.frequency.exponentialRampToValueAtTime(180, t + 0.2);
      lp.Q.value = 6;
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.5, t + 0.005);
      g.gain.exponentialRampToValueAtTime(0.001, t + stepDur * 0.9);
      o.connect(lp).connect(g).connect(master);
      o.start(t); o.stop(t + stepDur);
    };
    const playStab = (midi: number, t: number) => {
      if (!midi) return;
      const o1 = ctx.createOscillator(); o1.type = "square"; o1.frequency.value = midiToFreq(midi);
      const o2 = ctx.createOscillator(); o2.type = "sawtooth"; o2.frequency.value = midiToFreq(midi + 7);
      const lp = ctx.createBiquadFilter(); lp.type = "bandpass"; lp.frequency.value = 1500; lp.Q.value = 4;
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.18, t + 0.005);
      g.gain.exponentialRampToValueAtTime(0.001, t + 0.12);
      o1.connect(lp); o2.connect(lp); lp.connect(g).connect(master);
      o1.start(t); o2.start(t); o1.stop(t + 0.15); o2.stop(t + 0.15);
    };

    const startTime = ctx.currentTime + 0.1;
    let stopped = false;
    for (let bar = 0; bar < bars; bar++) {
      for (let s = 0; s < steps; s++) {
        const t = startTime + (bar * steps + s) * stepDur;
        if (pattern.kick?.[s]) playKick(t);
        if (pattern.snare?.[s]) playSnare(t);
        if (pattern.hihat?.[s]) playHat(t);
        if (pattern.bass?.[s]) playBass(pattern.bass[s], t);
        if (pattern.stab?.[s]) playStab(pattern.stab[s], t);
      }
    }

    const totalDur = bars * steps * stepDur + 0.5;
    const timeoutId = window.setTimeout(() => { if (!stopped) setPlaying(false); }, totalDur * 1000);

    stopRef.current = () => {
      stopped = true;
      clearTimeout(timeoutId);
      try { master.gain.setTargetAtTime(0, ctx.currentTime, 0.05); } catch {}
      setTimeout(() => { try { ctx.close(); } catch {}; ctxRef.current = null; }, 200);
    };
    setPlaying(true);
  };

  const stop = () => { stopRef.current?.(); stopRef.current = null; setPlaying(false); };

  const downloadJson = () => {
    const blob = new Blob([JSON.stringify(pattern, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${(pattern.title || "funk-groove").replace(/\s+/g, "-").toLowerCase()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="my-3 rounded-2xl border border-white/10 bg-gradient-to-br from-primary/10 via-background/40 to-accent/10 p-4 backdrop-blur-md">
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="min-w-0">
          <div className="font-display text-base truncate">🎷 {pattern.title || "Funk Groove"}</div>
          <div className="text-xs text-muted-foreground truncate">
            {pattern.bpm ?? "?"} BPM · {pattern.key ?? "C"} · {pattern.bars ?? 8} bars
            {pattern.vibe ? ` · ${pattern.vibe}` : ""}
          </div>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          <Button size="icon" variant="ghost" onClick={downloadJson} title="Pattern als JSON laden" className="h-9 w-9">
            <Download className="h-4 w-4" />
          </Button>
          <Button
            size="icon"
            onClick={playing ? stop : start}
            className="h-10 w-10 bg-foreground text-background hover:bg-foreground/90"
            title={playing ? "Stop" : "Play"}
          >
            {playing ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 ml-0.5" />}
          </Button>
        </div>
      </div>
      {/* Pattern visualizer */}
      <div className="grid grid-cols-16 gap-0.5">
        {Array.from({ length: 16 }).map((_, i) => {
          const active = !!(pattern.kick?.[i] || pattern.snare?.[i] || pattern.bass?.[i] || pattern.stab?.[i]);
          return (
            <div
              key={i}
              className={`h-1.5 rounded-full ${active ? "bg-primary" : "bg-foreground/10"} ${i % 4 === 0 ? "opacity-100" : "opacity-70"}`}
            />
          );
        })}
      </div>
    </div>
  );
}
