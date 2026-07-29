import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Play, Pause, Download, Loader2, Sparkles, RefreshCcw } from "lucide-react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { renderSong, type MusicSpec } from "@/lib/synthSong";

export type SongRequest = {
  prompt: string;
  title?: string;
  duration?: number;
};

export default function SongPlayer({ request }: { request: SongRequest }) {
  const [status, setStatus] = useState<"idle" | "loading" | "generating" | "ready" | "error">("idle");
  const [progress, setProgress] = useState(0);
  const [progressMsg, setProgressMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [spec, setSpec] = useState<MusicSpec | null>(null);
  const [playing, setPlaying] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => () => { if (audioUrl) URL.revokeObjectURL(audioUrl); }, [audioUrl]);

  const generate = async () => {
    setErrorMsg(null);
    setStatus("loading");
    setProgress(5);
    setProgressMsg("KI komponiert Song-Struktur…");
    try {
      const { data, error } = await supabase.functions.invoke("music-gen", {
        body: { prompt: request.prompt },
      });
      if (error) throw error;
      if (!data?.spec) throw new Error("Keine Song-Daten erhalten");
      const s: MusicSpec = { ...data.spec, title: data.spec.title || request.title };
      setSpec(s);
      setStatus("generating");
      let vocalBuf: ArrayBuffer | null = null;
      if (data.vocal) {
        try {
          const bin = atob(data.vocal);
          const bytes = new Uint8Array(bin.length);
          for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
          vocalBuf = bytes.buffer;
        } catch {}
      }
      const wav = await renderSong(s, (p, m) => { setProgress(p); setProgressMsg(m); }, vocalBuf);
      setAudioUrl(URL.createObjectURL(wav));
      setStatus("ready");
    } catch (e: any) {
      const raw = e?.message ?? "unbekannter Fehler";
      let friendly = raw;
      if (/rate|429/i.test(raw)) friendly = "Zu viele Anfragen — kurz warten & erneut.";
      else if (/402|credit/i.test(raw)) friendly = "AI-Guthaben aufgebraucht — bitte Admin kontaktieren.";
      setErrorMsg(friendly);
      toast.error("Musik fehlgeschlagen: " + friendly);
      setStatus("error");
    }
  };

  const toggle = () => {
    const a = audioRef.current; if (!a) return;
    if (playing) a.pause(); else a.play();
  };

  const download = () => {
    if (!audioUrl) return;
    const a = document.createElement("a");
    a.href = audioUrl;
    a.download = `${(spec?.title || request.title || "ai-song").replace(/\s+/g, "-").toLowerCase()}.wav`;
    a.click();
  };

  return (
    <div className="my-3 rounded-2xl border border-white/10 bg-gradient-to-br from-primary/10 via-background/40 to-accent/10 p-4 backdrop-blur-md">
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="min-w-0">
          <div className="font-display text-base truncate">🎵 {spec?.title || request.title || "AI Song"}</div>
          <div className="text-xs text-muted-foreground truncate">{request.prompt}</div>
          {spec && (
            <div className="text-[10px] text-muted-foreground mt-0.5">
              {spec.bpm} BPM · {spec.key} {spec.scale} · {spec.bars} Takte
            </div>
          )}
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {status === "ready" && (
            <Button size="icon" variant="ghost" onClick={download} className="h-9 w-9" title="WAV">
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
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-foreground/10">
            <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
          </div>
        </div>
      )}

      {status === "idle" && (
        <p className="text-xs text-muted-foreground">
          Klick „Generieren" — die KI komponiert Akkorde, Melodie & Drums, dein Browser rendert daraus einen echten Track (~2-5s, kein Download nötig).
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
