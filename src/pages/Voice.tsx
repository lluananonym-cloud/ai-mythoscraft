import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { useVoiceMode } from "@/hooks/useVoiceMode";
import { useSubscription } from "@/hooks/useSubscription";
import Paywall from "@/components/Paywall";
import { LogoMark } from "@/components/Logo";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Mic, MicOff, X, Square } from "lucide-react";
import VoiceOrb from "@/components/VoiceOrb";
import { DEFAULT_MYTHOS_ID } from "@/lib/mythosModels";
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
          mythos: (typeof window !== "undefined" && localStorage.getItem("mythos.model")) || DEFAULT_MYTHOS_ID,
          voice: true,
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
        <VoiceOrb level={level} status={voice.status} />
        <div className="absolute bottom-32 left-0 right-0 px-6 text-center">
          <p className="text-white/70 text-sm min-h-[2rem]">
            {voice.status === "listening" && (voice.interim || "Ich höre zu … sprich einfach los")}
            {voice.status === "speaking" && (aiText.slice(-160) || "…")}
            {voice.status === "idle" && (isPro ? (aiText ? aiText.slice(-160) : "Tippe das Mikrofon zum Sprechen") : "Pro erforderlich")}
          </p>
        </div>
      </div>

      <div className="p-8 flex justify-center" style={{ paddingBottom: "max(2rem, env(safe-area-inset-bottom))" }}>
        {voice.status === "speaking" ? (
          <Button size="lg" variant="secondary" className="h-16 w-16 rounded-full" onClick={() => voice.stopSpeaking()} aria-label="Sprechen stoppen">
            <Square className="h-6 w-6" />
          </Button>
        ) : voice.status === "listening" ? (
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
