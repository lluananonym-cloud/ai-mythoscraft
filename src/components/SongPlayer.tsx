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
  createdAt?: number;
};

/** Songlänge in Sekunden aus dem Wunsch lesen ("2 Minuten", "90 sek"), immer 60–180, Standard 120. */
export function songSeconds(prompt: string): number {
  const min = prompt.match(/(\d+(?:[.,]\d+)?)\s*(?:min|minute)/i);
  const sec = prompt.match(/(\d+)\s*(?:s\b|sek|sec)/i);
  const s = min ? parseFloat(min[1].replace(",", ".")) * 60 : sec ? parseInt(sec[1], 10) : 120;
  return Math.min(180, Math.max(60, Math.round(s)));
}

const MP3_BYTES_PER_SEC = 128000 / 8;

/** MP3-Stream abspielen, während er noch lädt (MediaSource). Liefert am Ende die komplette Datei. */
async function playStream(
  body: ReadableStream<Uint8Array>,
  seconds: number,
  onUrl: (url: string) => void,
  onProgress: (receivedSec: number) => void,
): Promise<Blob> {
  const chunks: Uint8Array[] = [];
  const reader = body.getReader();
  const canStream = typeof MediaSource !== "undefined" && MediaSource.isTypeSupported("audio/mpeg");
  let sb: SourceBuffer | null = null;
  let ms: MediaSource | null = null;
  const queue: Uint8Array[] = [];
  const pump = () => {
    if (sb && !sb.updating && queue.length) {
      try { sb.appendBuffer(queue.shift()!); } catch { sb = null; }
    }
  };
  if (canStream) {
    ms = new MediaSource();
    onUrl(URL.createObjectURL(ms));
    await new Promise<void>((res) => ms!.addEventListener("sourceopen", () => res(), { once: true }));
    sb = ms.addSourceBuffer("audio/mpeg");
    sb.addEventListener("updateend", pump);
  }
  let received = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    received += value.byteLength;
    onProgress(Math.min(seconds, received / MP3_BYTES_PER_SEC));
    if (sb) { queue.push(value); pump(); }
  }
  if (ms && sb) {
    while (queue.length || sb.updating) { await new Promise((r) => setTimeout(r, 50)); pump(); }
    try { ms.endOfStream(); } catch { /* bereits beendet */ }
  }
  return new Blob(chunks, { type: "audio/mpeg" });
}

export default function SongPlayer({ request }: { request: SongRequest }) {
  const [status, setStatus] = useState<"idle" | "loading" | "generating" | "ready" | "error">("idle");
  const [progress, setProgress] = useState(0);
  const [progressMsg, setProgressMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  // Warum statt echtem Gesang nur die Synth-Version kam (sonst wirkt es, als wäre nichts passiert).
  const [notice, setNotice] = useState<string[] | null>(null);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [spec, setSpec] = useState<MusicSpec | null>(null);
  const [playing, setPlaying] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => () => { if (audioUrl) URL.revokeObjectURL(audioUrl); }, [audioUrl]);
  useEffect(() => () => { if (fileUrl) URL.revokeObjectURL(fileUrl); }, [fileUrl]);

  const seconds = Math.min(180, Math.max(60, Math.round(request.duration ?? 120)));

  // Echter Song mit Gesang (ElevenLabs Music über die Edge Function), spielt schon während er entsteht.
  // false = kein Musik-Schlüssel hinterlegt, dann alter Browser-Synth.
  const generateReal = async (): Promise<boolean> => {
    const { data: { session } } = await supabase.auth.getSession();
    // Ohne Streaming (Lyria) liefert der Server erst am Ende: Fortschritt bis 90 % schätzen.
    const steps = ["Text wird geschrieben…", "Melodie entsteht…", "Gesang wird aufgenommen…", "Song wird gemischt…"];
    const t0 = Date.now();
    const tick = setInterval(() => {
      const sec = (Date.now() - t0) / 1000;
      setProgress(Math.min(90, Math.round(5 + 85 * (1 - Math.exp(-sec / 30)))));
      setProgressMsg(steps[Math.min(steps.length - 1, Math.floor(sec / 12))]);
    }, 500);
    try {
      return await requestReal(session?.access_token, () => clearInterval(tick));
    } finally {
      clearInterval(tick);
    }
  };

  const requestReal = async (token: string | undefined, stopEstimate: () => void): Promise<boolean> => {
    const r = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/functions/v1/music-gen`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
        Authorization: `Bearer ${token ?? import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}`,
      },
      body: JSON.stringify({ prompt: request.prompt, duration: seconds, mode: "stream" }),
    });
    if ((r.headers.get("Content-Type") || "").includes("json")) {
      const j = await r.json().catch(() => ({}));
      if (j?.error === "no_music_key") {
        console.warn("[song] kein Musikmodell erreichbar", j.details);
        setNotice(Array.isArray(j.reasons) && j.reasons.length ? j.reasons : ["Kein Musikmodell mit Gesang erreichbar."]);
        return false;
      }
      // Alte Server-Version (kennt den Song-Modus noch nicht) schickt nur ein Synth-Rezept zurück.
      if (j?.spec) {
        setNotice(["Die Server-Funktion für Songs ist noch die alte Version. In Lovable müssen die Edge Functions neu bereitgestellt werden."]);
        return false;
      }
      if (!j?.audio) {
        console.warn("[song] unerwartete Antwort", r.status, j);
        setNotice([j?.error ? String(j.error) : "Der Musik-Server hat keine Audiodaten geschickt."]);
        return false;
      }
      const bin = atob(j.audio);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      const url = URL.createObjectURL(new Blob([bytes], { type: j.mime || "audio/mpeg" }));
      setAudioUrl(url);
      setFileUrl(url);
      setProgress(100);
      setStatus("ready");
      setTimeout(() => audioRef.current?.play().catch(() => {}), 50);
      return true;
    }
    if (!r.ok || !r.body) throw new Error(`Fehler ${r.status}`);
    stopEstimate();
    const total = Number(r.headers.get("X-Song-Seconds")) || seconds;
    setStatus("generating");
    setProgressMsg("Song entsteht…");
    let started = false;
    const blob = await playStream(
      r.body,
      total,
      (url) => setAudioUrl(url),
      (sec) => {
        setProgress(Math.max(8, Math.round((sec / total) * 100)));
        setProgressMsg(`Song entsteht… ${Math.floor(sec)} von ${total} Sekunden`);
        if (!started && sec >= 4 && audioRef.current) { started = true; audioRef.current.play().catch(() => {}); }
      },
    );
    const url = URL.createObjectURL(blob);
    setFileUrl(url);
    if (typeof MediaSource === "undefined" || !MediaSource.isTypeSupported("audio/mpeg")) setAudioUrl(url);
    setStatus("ready");
    return true;
  };

  const generate = async () => {
    setErrorMsg(null);
    setNotice(null);
    setStatus("loading");
    setProgress(5);
    setProgressMsg("Song wird gestartet…");
    try {
      if (await generateReal()) return;
      setProgressMsg("KI komponiert Song-Struktur…");
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
      const url = URL.createObjectURL(wav);
      setAudioUrl(url);
      setFileUrl(url);
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

  // Frisch angefragte Songs starten sofort, alte Chat-Verläufe erst auf Klick (kostet Guthaben).
  const autoStarted = useRef(false);
  useEffect(() => {
    if (autoStarted.current || !request.createdAt || Date.now() - request.createdAt > 60_000) return;
    autoStarted.current = true;
    generate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggle = () => {
    const a = audioRef.current; if (!a) return;
    if (playing) a.pause(); else a.play();
  };

  const download = () => {
    if (!fileUrl) return;
    const a = document.createElement("a");
    a.href = fileUrl;
    a.download = `${(spec?.title || request.title || "ai-song").replace(/\s+/g, "-").toLowerCase()}.${spec ? "wav" : "mp3"}`;
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
            <Button size="icon" variant="ghost" onClick={download} className="h-9 w-9" title="Herunterladen">
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
          Klick „Generieren" für einen echten Song mit Gesang ({Math.round(seconds / 6) / 10} Minuten).
        </p>
      )}

      {notice && status !== "loading" && (
        <div className="mb-2 rounded-lg border border-amber-400/25 bg-amber-500/10 px-3 py-2 text-xs text-amber-100/90">
          Echter Gesang ging gerade nicht, deshalb kommt eine Instrumental-Version.
          <ul className="mt-1 list-disc pl-4 text-amber-100/70">{notice.map((n) => <li key={n}>{n}</li>)}</ul>
        </div>
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
