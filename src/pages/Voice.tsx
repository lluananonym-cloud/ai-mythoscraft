import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { useVoiceMode } from "@/hooks/useVoiceMode";
import { useSubscription } from "@/hooks/useSubscription";
import Paywall from "@/components/Paywall";
import { LogoMark } from "@/components/Logo";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Mic, MicOff, X } from "lucide-react";
import { toast } from "sonner";

/**
 * Live voice chat — full-screen animated audio-reactive orb.
 * Pro-gated. Uses browser Web Speech API + Lovable AI chat function for replies.
 */
export default function Voice() {
  const { user } = useAuth();
  const nav = useNavigate();
  const { isPro, loading } = useSubscription();
  const [showPaywall, setShowPaywall] = useState(false);
  const [level, setLevel] = useState(0);
  const [aiText, setAiText] = useState("");
  const [history, setHistory] = useState<{ role: "user"|"assistant"; content: string }[]>([]);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const rafRef = useRef<number | null>(null);
  const historyRef = useRef(history);
  historyRef.current = history;

  const askAI = async (text: string) => {
    setHistory(h => [...h, { role: "user", content: text }]);
    setAiText("");
    try {
      const url = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/chat`;
      const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;
      const resp = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}`, apikey: key },
        body: JSON.stringify({
          userId: user?.id,
          mode: "support",
          messages: [...historyRef.current, { role: "user", content: text }],
        }),
      });
      if (!resp.ok || !resp.body) {
        const t = await resp.text().catch(()=> "");
        console.error("[voice] chat error", resp.status, t);
        toast.error("AI nicht erreichbar");
        return;
      }
      const reader = resp.body.getReader();
      const dec = new TextDecoder();
      let buf = "", full = "";
      while (true) {
        const { done, value } = await reader.read(); if (done) break;
        buf += dec.decode(value, { stream: true });
        let i;
        while ((i = buf.indexOf("\n")) !== -1) {
          const line = buf.slice(0,i).replace(/\r$/,""); buf = buf.slice(i+1);
          if (!line.startsWith("data: ")) continue;
          const j = line.slice(6).trim(); if (j === "[DONE]") break;
          try { const p = JSON.parse(j); const c = p.choices?.[0]?.delta?.content; if (c) { full += c; setAiText(full); } } catch {}
        }
      }
      if (full) { setHistory(h => [...h, { role: "assistant", content: full }]); voice.speak(full); }
      else toast.error("Keine Antwort erhalten");
    } catch (e) {
      console.error("[voice] connection error", e);
      toast.error("Verbindungsfehler");
    }
  };

  const voice = useVoiceMode({ lang: "de-DE", onTranscript: askAI });

  // Mic audio analyser → drive orb
  useEffect(() => {
    if (!isPro) return;
    let cancelled = false;
    (async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        if (cancelled) { stream.getTracks().forEach(t => t.stop()); return; }
        streamRef.current = stream;
        const ctx = new (window.AudioContext || (window as any).webkitAudioContext)();
        audioCtxRef.current = ctx;
        const src = ctx.createMediaStreamSource(stream);
        const an = ctx.createAnalyser(); an.fftSize = 256;
        src.connect(an); analyserRef.current = an;
        const data = new Uint8Array(an.frequencyBinCount);
        const loop = () => {
          an.getByteFrequencyData(data);
          let sum = 0; for (let i = 0; i < data.length; i++) sum += data[i];
          setLevel(sum / data.length / 255);
          rafRef.current = requestAnimationFrame(loop);
        };
        loop();
      } catch { toast.error("Mikrofon-Zugriff verweigert"); }
    })();
    return () => {
      cancelled = true;
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      streamRef.current?.getTracks().forEach(t => t.stop());
      audioCtxRef.current?.close().catch(()=>{});
    };
  }, [isPro]);

  // Orb canvas render — layered, audio-reactive plasma blob
  const levelRef = useRef(0);
  const smoothRef = useRef(0);
  levelRef.current = level;
  const statusRef = useRef(voice.status);
  statusRef.current = voice.status;

  useEffect(() => {
    const canvas = canvasRef.current; if (!canvas) return;
    const ctx = canvas.getContext("2d"); if (!ctx) return;
    let t = 0, raf = 0;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const resize = () => { canvas.width = canvas.clientWidth * dpr; canvas.height = canvas.clientHeight * dpr; };
    resize(); window.addEventListener("resize", resize);

    const blob = (cx: number, cy: number, r: number, seed: number, amp: number, fill: string | CanvasGradient) => {
      ctx.beginPath();
      const points = 96;
      for (let i = 0; i <= points; i++) {
        const a = (i / points) * Math.PI * 2;
        const wob =
          1 +
          0.055 * Math.sin(a * 3 + t * 1.3 + seed) +
          0.04 * Math.sin(a * 5 - t * 1.9 + seed * 2) +
          0.03 * Math.sin(a * 8 + t * 2.6 + seed * 3) +
          amp * 0.22 * Math.sin(a * 4 + t * 5 + seed);
        const rr = r * wob;
        const x = cx + Math.cos(a) * rr, y = cy + Math.sin(a) * rr;
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      }
      ctx.closePath();
      ctx.fillStyle = fill;
      ctx.fill();
    };

    const draw = () => {
      t += 0.016;
      const speaking = statusRef.current === "speaking";
      const listening = statusRef.current === "listening";
      // smooth the amplitude so the orb breathes instead of flickering
      const target = speaking ? 0.45 + 0.35 * Math.abs(Math.sin(t * 4.2)) : listening ? levelRef.current * 1.6 : 0.06;
      smoothRef.current += (Math.min(target, 1) - smoothRef.current) * 0.12;
      const amp = smoothRef.current;

      const W = canvas.width, H = canvas.height;
      ctx.clearRect(0, 0, W, H);
      const cx = W / 2, cy = H / 2;
      const base = Math.min(W, H) * 0.24;
      const r = base * (1 + amp * 0.28);

      const hue = speaking ? 288 : listening ? 196 : 225;
      const hue2 = speaking ? 322 : listening ? 165 : 262;

      // ambient halo
      const halo = ctx.createRadialGradient(cx, cy, r * 0.3, cx, cy, r * 2.6);
      halo.addColorStop(0, `hsla(${hue}, 95%, 62%, ${0.30 + amp * 0.35})`);
      halo.addColorStop(0.55, `hsla(${hue2}, 90%, 55%, ${0.10 + amp * 0.16})`);
      halo.addColorStop(1, `hsla(${hue2}, 90%, 50%, 0)`);
      ctx.fillStyle = halo;
      ctx.beginPath(); ctx.arc(cx, cy, r * 2.6, 0, Math.PI * 2); ctx.fill();

      // soft outer shells
      ctx.globalCompositeOperation = "lighter";
      blob(cx, cy, r * 1.32, 1.7, amp * 0.7, `hsla(${hue2}, 95%, 58%, ${0.10 + amp * 0.10})`);
      blob(cx, cy, r * 1.15, 3.1, amp * 0.85, `hsla(${hue}, 95%, 60%, ${0.14 + amp * 0.12})`);
      ctx.globalCompositeOperation = "source-over";

      // core body
      const core = ctx.createRadialGradient(cx - r * 0.35, cy - r * 0.4, r * 0.08, cx, cy, r * 1.05);
      core.addColorStop(0, `hsla(${hue}, 100%, 92%, 1)`);
      core.addColorStop(0.42, `hsla(${hue}, 96%, 68%, 1)`);
      core.addColorStop(1, `hsla(${hue2}, 88%, 34%, 1)`);
      ctx.shadowColor = `hsla(${hue}, 95%, 60%, 0.75)`;
      ctx.shadowBlur = r * (0.5 + amp);
      blob(cx, cy, r, 0.4, amp, core);
      ctx.shadowBlur = 0;

      // inner swirl highlights
      ctx.globalCompositeOperation = "screen";
      for (let k = 0; k < 3; k++) {
        const ox = Math.cos(t * (0.7 + k * 0.35) + k) * r * 0.22;
        const oy = Math.sin(t * (0.9 + k * 0.3) + k) * r * 0.22;
        const g = ctx.createRadialGradient(cx + ox, cy + oy, 0, cx + ox, cy + oy, r * 0.55);
        g.addColorStop(0, `hsla(${hue + k * 18}, 100%, 85%, ${0.20 + amp * 0.22})`);
        g.addColorStop(1, "hsla(0,0%,0%,0)");
        ctx.fillStyle = g;
        ctx.beginPath(); ctx.arc(cx + ox, cy + oy, r * 0.55, 0, Math.PI * 2); ctx.fill();
      }
      ctx.globalCompositeOperation = "source-over";

      // reactive ring
      ctx.beginPath();
      ctx.arc(cx, cy, r * (1.5 + amp * 0.25), 0, Math.PI * 2);
      ctx.strokeStyle = `hsla(${hue}, 100%, 78%, ${0.10 + amp * 0.28})`;
      ctx.lineWidth = Math.max(1, r * 0.012);
      ctx.stroke();

      raf = requestAnimationFrame(draw);
    };
    draw();
    return () => { cancelAnimationFrame(raf); window.removeEventListener("resize", resize); };
  }, []);

  useEffect(() => {
    if (!loading && !isPro) setShowPaywall(true);
  }, [loading, isPro]);

  const exit = () => {
    voice.stopListening(); voice.stopSpeaking();
    nav("/app");
  };

  return (
    <div className="fixed inset-0 bg-black flex flex-col">
      <div className="flex items-center justify-between p-4" style={{ paddingTop: "max(1rem, env(safe-area-inset-top))" }}>
        <Link to="/app" onClick={() => { voice.stopListening(); voice.stopSpeaking(); }}>
          <ArrowLeft className="h-6 w-6 text-white/80" />
        </Link>
        <LogoMark size="sm" className="opacity-90" />
        <button onClick={exit}><X className="h-6 w-6 text-white/80" /></button>
      </div>

      <div className="flex-1 relative">
        <canvas ref={canvasRef} className="absolute inset-0 w-full h-full" />
        <div className="absolute bottom-32 left-0 right-0 px-6 text-center">
          <p className="text-white/70 text-sm min-h-[2rem]">
            {voice.status === "listening" && (voice.interim || "Ich höre zu …")}
            {voice.status === "speaking" && (aiText.slice(-160) || "…")}
            {voice.status === "idle" && (isPro ? "Tippe das Mikrofon zum Sprechen" : "Pro erforderlich")}
          </p>
        </div>
      </div>

      <div className="p-8 flex justify-center" style={{ paddingBottom: "max(2rem, env(safe-area-inset-bottom))" }}>
        {voice.status === "listening" ? (
          <Button size="lg" variant="destructive" className="h-16 w-16 rounded-full" onClick={() => voice.stopListening()}>
            <MicOff className="h-7 w-7" />
          </Button>
        ) : (
          <Button size="lg" className="h-16 w-16 rounded-full bg-gradient-primary text-primary-foreground"
            disabled={!isPro || !voice.supported}
            onClick={() => { setAiText(""); voice.startLive(); }}>
            <Mic className="h-7 w-7" />
          </Button>
        )}
      </div>

      <Paywall open={showPaywall} onOpenChange={(o) => { setShowPaywall(o); if (!o && !isPro) nav("/app"); }} reason="Live-Sprachchat ist eine Pro-Funktion." />
    </div>
  );
}
