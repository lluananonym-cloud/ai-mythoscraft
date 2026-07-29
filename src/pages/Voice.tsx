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

  // Orb canvas render
  useEffect(() => {
    const canvas = canvasRef.current; if (!canvas) return;
    const ctx = canvas.getContext("2d"); if (!ctx) return;
    let t = 0, raf = 0;
    const dpr = window.devicePixelRatio || 1;
    const resize = () => { canvas.width = canvas.clientWidth*dpr; canvas.height = canvas.clientHeight*dpr; };
    resize(); window.addEventListener("resize", resize);
    const draw = () => {
      t += 0.016;
      ctx.clearRect(0,0,canvas.width,canvas.height);
      const cx = canvas.width/2, cy = canvas.height/2;
      const base = Math.min(canvas.width, canvas.height) * 0.28;
      const isSpeaking = voice.status === "speaking";
      const reactive = isSpeaking ? (0.5 + 0.5*Math.sin(t*6)) : level;
      const r = base * (1 + reactive*0.35);
      // outer glow
      const grad = ctx.createRadialGradient(cx,cy,r*0.2,cx,cy,r*1.8);
      const colA = isSpeaking ? "hsla(280, 90%, 65%, 0.9)" : "hsla(200, 90%, 60%, 0.9)";
      const colB = isSpeaking ? "hsla(320, 90%, 55%, 0)" : "hsla(160, 90%, 50%, 0)";
      grad.addColorStop(0, colA); grad.addColorStop(1, colB);
      ctx.fillStyle = grad; ctx.beginPath(); ctx.arc(cx,cy,r*1.8,0,Math.PI*2); ctx.fill();
      // orb body — wobbling blob
      ctx.beginPath();
      const points = 64;
      for (let i = 0; i <= points; i++) {
        const a = (i/points)*Math.PI*2;
        const wob = 1 + 0.08*Math.sin(a*3 + t*2) + 0.06*Math.sin(a*5 - t*3) + reactive*0.15*Math.sin(a*7 + t*4);
        const rr = r*wob;
        const x = cx + Math.cos(a)*rr, y = cy + Math.sin(a)*rr;
        i === 0 ? ctx.moveTo(x,y) : ctx.lineTo(x,y);
      }
      ctx.closePath();
      const inner = ctx.createRadialGradient(cx-r*0.3,cy-r*0.3,r*0.1,cx,cy,r);
      inner.addColorStop(0, isSpeaking ? "hsla(300,100%,80%,1)" : "hsla(190,100%,75%,1)");
      inner.addColorStop(1, isSpeaking ? "hsla(280,80%,40%,1)" : "hsla(220,80%,30%,1)");
      ctx.fillStyle = inner; ctx.fill();
      raf = requestAnimationFrame(draw);
    };
    draw();
    return () => { cancelAnimationFrame(raf); window.removeEventListener("resize", resize); };
  }, [level, voice.status]);

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
