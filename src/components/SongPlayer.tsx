import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Play, Pause, Download, Loader2, Sparkles, RefreshCcw } from "lucide-react";
import { toast } from "sonner";

export type SongRequest = {
  prompt: string;
  title?: string;
  duration?: number; // seconds, max ~15
};

// Encode Float32 PCM to a 16-bit WAV blob
function encodeWAV(samples: Float32Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const writeStr = (off: number, s: string) => {
    for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i));
  };
  writeStr(0, "RIFF");
  view.setUint32(4, 36 + samples.length * 2, true);
  writeStr(8, "WAVE");
  writeStr(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeStr(36, "data");
  view.setUint32(40, samples.length * 2, true);
  let off = 44;
  for (let i = 0; i < samples.length; i++, off += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([view], { type: "audio/wav" });
}

// Globally deduped — see src/lib/transformersLoader.ts
import { loadPipeline } from "@/lib/transformersLoader";
async function getMusicGen(onProgress: (p: number, msg: string) => void, dtype: "fp32" | "q8" = "fp32") {
  return loadPipeline({
    task: "text-to-audio",
    model: "Xenova/musicgen-small",
    dtype,
    onProgress: (p) => onProgress(p.pct, p.msg || `Lade Musik-Modell… ${p.pct}%`),
  });
}

// Detect low-memory devices (mobile, old browsers)
function isLowMemory() {
  const mem = (navigator as any).deviceMemory;
  if (typeof mem === "number" && mem < 4) return true;
  return /Mobi|Android|iPhone|iPad/i.test(navigator.userAgent) && !(navigator as any).deviceMemory;
}

export default function SongPlayer({ request }: { request: SongRequest }) {
  const [status, setStatus] = useState<"idle" | "loading" | "generating" | "ready" | "error">("idle");
  const [progress, setProgress] = useState(0);
  const [progressMsg, setProgressMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [playing, setPlaying] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => () => { if (audioUrl) URL.revokeObjectURL(audioUrl); }, [audioUrl]);

  const runGenerate = async (dtype: "fp32" | "q8") => {
    setStatus("loading");
    setProgress(0);
    setProgressMsg(`Lade KI-Modell (${dtype === "q8" ? "~150MB quantisiert" : "~300MB"}, einmalig & offline-fähig)…`);
    const pipe = await getMusicGen((p, m) => { setProgress(p); setProgressMsg(m); }, dtype);
    setStatus("generating");
    setProgressMsg("Komponiere Song… (15-60s)");
    const seconds = Math.min(10, Math.max(4, request.duration ?? 7));
    const max_new_tokens = Math.round(seconds * 50);
    const out: any = await Promise.race([
      pipe(request.prompt, { do_sample: true, guidance_scale: 3, max_new_tokens }),
      new Promise((_, rej) => setTimeout(() => rej(new Error("Timeout")), 180_000)),
    ]);
    const sampleRate = out.sampling_rate ?? 32000;
    const samples = out.audio as Float32Array;
    if (!samples || !samples.length) throw new Error("Leere Audio-Antwort vom Modell");
    return encodeWAV(samples, sampleRate);
  };

  const generate = async () => {
    setErrorMsg(null);
    // fp32 ist deutlich stabiler; auf Low-Mem direkt q8
    const order: ("fp32" | "q8")[] = isLowMemory() ? ["q8", "fp32"] : ["fp32", "q8"];
    let lastErr: any = null;
    for (const dtype of order) {
      try {
        const wav = await runGenerate(dtype);
        setAudioUrl(URL.createObjectURL(wav));
        setStatus("ready");
        return;
      } catch (e: any) {
        console.error(`MusicGen (${dtype}) error:`, e);
        lastErr = e;
        if (dtype === "fp32" && /memory|allocation|wasm|RangeError/i.test(e?.message ?? "")) {
          setProgressMsg("Zu wenig RAM für fp32 — wechsle auf quantisiertes Modell…");
          continue;
        }
        break;
      }
    }
    const raw = lastErr?.message ?? "unbekannter Fehler";
    let friendly = raw;
    if (/out of memory|allocation|wasm|RangeError/i.test(raw)) friendly = "Zu wenig Arbeitsspeicher — schließe andere Tabs & lade die Seite neu.";
    else if (/network|fetch|load|404|403/i.test(raw)) friendly = "Modell konnte nicht geladen werden — Internet prüfen.";
    else if (/timeout/i.test(raw)) friendly = "Zu langsam — kürzere Beschreibung versuchen.";
    setErrorMsg(friendly);
    toast.error("Musik fehlgeschlagen: " + friendly);
    setStatus("error");
  };


  const toggle = () => {
    const a = audioRef.current; if (!a) return;
    if (playing) { a.pause(); } else { a.play(); }
  };

  const download = () => {
    if (!audioUrl) return;
    const a = document.createElement("a");
    a.href = audioUrl;
    a.download = `${(request.title || "ai-song").replace(/\s+/g, "-").toLowerCase()}.wav`;
    a.click();
  };

  return (
    <div className="my-3 rounded-2xl border border-white/10 bg-gradient-to-br from-primary/10 via-background/40 to-accent/10 p-4 backdrop-blur-md">
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="min-w-0">
          <div className="font-display text-base truncate">🎵 {request.title || "AI Song"}</div>
          <div className="text-xs text-muted-foreground truncate">{request.prompt}</div>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {status === "ready" && (
            <Button size="icon" variant="ghost" onClick={download} className="h-9 w-9" title="WAV herunterladen">
              <Download className="h-4 w-4" />
            </Button>
          )}
          {status === "ready" ? (
            <Button size="icon" onClick={toggle} className="h-10 w-10 bg-foreground text-background hover:bg-foreground/90">
              {playing ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 ml-0.5" />}
            </Button>
          ) : status === "loading" || status === "generating" ? (
            <Button size="icon" disabled className="h-10 w-10">
              <Loader2 className="h-5 w-5 animate-spin" />
            </Button>
          ) : status === "error" ? (
            <Button size="sm" variant="outline" onClick={generate} className="gap-1.5">
              <RefreshCcw className="h-4 w-4" /> Erneut
            </Button>
          ) : (
            <Button size="sm" onClick={generate} className="gap-1.5">
              <Sparkles className="h-4 w-4" /> Generieren
            </Button>
          )}
        </div>
      </div>

      {(status === "loading" || status === "generating") && (
        <div className="space-y-1.5">
          <div className="text-xs text-muted-foreground">{progressMsg}</div>
          {status === "loading" && (
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-foreground/10">
              <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
            </div>
          )}
        </div>
      )}

      {status === "idle" && (
        <p className="text-xs text-muted-foreground">
          Klick „Generieren" — der erste Song lädt das KI-Modell (~150MB, einmalig & offline-fähig). Folgende Songs sind schnell.
          {isLowMemory() && <span className="block mt-1 text-amber-400">⚠ Auf deinem Gerät kann's eng werden — schließe andere Tabs.</span>}
        </p>
      )}

      {status === "error" && errorMsg && (
        <p className="text-xs text-destructive">{errorMsg}</p>
      )}


      {audioUrl && (
        <audio
          ref={audioRef}
          src={audioUrl}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => setPlaying(false)}
          className="mt-2 w-full"
          controls
        />
      )}
    </div>
  );
}
