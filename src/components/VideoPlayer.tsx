import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Play, Pause, Download, Loader2, Sparkles, RefreshCcw, Film } from "lucide-react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";

export type VideoRequest = {
  prompt: string;
  title?: string;
  duration?: number; // seconds, default 5
  motion?: "kenburns" | "parallax" | "zoom-out" | "pan-right";
};

type Status = "idle" | "fetching-image" | "rendering" | "ready" | "error";

// Render an AI-generated still into a short cinematic clip using
// Canvas + MediaRecorder. 100% client-side, free, no extra API.
export default function VideoPlayer({ request }: { request: VideoRequest }) {
  const [status, setStatus] = useState<Status>("idle");
  const [progress, setProgress] = useState(0);
  const [imgUrl, setImgUrl] = useState<string | null>(null);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [playing, setPlaying] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);

  useEffect(() => () => {
    if (videoUrl) URL.revokeObjectURL(videoUrl);
  }, [videoUrl]);

  const fetchImage = async (prompt: string): Promise<string> => {
    const { data, error } = await supabase.functions.invoke("image-gen", { body: { prompt } });
    if (error || !data?.url) throw new Error(error?.message || data?.error || "Bild fehlgeschlagen");
    return data.url as string;
  };

  const fetchKeyframes = async (): Promise<string[]> => {
    setStatus("fetching-image");
    setProgress(5);
    // Generate 3 keyframes with progressive prompts for real "video" feel (cinematic beats)
    const base = request.prompt;
    const prompts = [
      `${base}, wide establishing shot, cinematic, golden hour, film grain`,
      `${base}, medium shot, dynamic action moment, dramatic lighting`,
      `${base}, close-up detail, epic mood, shallow depth of field`,
    ];
    const urls: string[] = [];
    for (let i = 0; i < prompts.length; i++) {
      const url = await fetchImage(prompts[i]);
      urls.push(url);
      setImgUrl(url);
      setProgress(5 + Math.round(((i + 1) / prompts.length) * 35));
    }
    return urls;
  };


  const loadImg = (url: string) => new Promise<HTMLImageElement>((resolve, reject) => {
    const i = new Image();
    i.crossOrigin = "anonymous";
    i.onload = () => resolve(i);
    i.onerror = () => reject(new Error("Bild laden fehlgeschlagen"));
    i.src = url;
  });

  const renderVideo = async (urls: string[]): Promise<Blob> => {
    setStatus("rendering");
    const duration = Math.min(15, Math.max(4, request.duration ?? 8));
    const W = 1280, H = 720, FPS = 30;
    const imgs = await Promise.all(urls.map(loadImg));

    const canvas = canvasRef.current ?? document.createElement("canvas");
    canvas.width = W; canvas.height = H;
    const ctx = canvas.getContext("2d", { alpha: false })!;

    const candidates = [
      "video/mp4;codecs=avc1.42E01E",
      "video/webm;codecs=vp9",
      "video/webm;codecs=vp8",
      "video/webm",
    ];
    const mime = candidates.find(m => (window as any).MediaRecorder?.isTypeSupported?.(m)) || "video/webm";

    const stream = (canvas as any).captureStream(FPS) as MediaStream;
    const recorder = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 6_000_000 });
    const chunks: Blob[] = [];
    recorder.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
    const done = new Promise<Blob>((resolve) => {
      recorder.onstop = () => resolve(new Blob(chunks, { type: mime }));
    });
    recorder.start();

    const totalFrames = duration * FPS;
    const perScene = totalFrames / imgs.length;
    const ease = (t: number) => t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;

    const drawImg = (img: HTMLImageElement, sceneIdx: number, localT: number) => {
      const scale = Math.max(W / img.width, H / img.height);
      const baseW = img.width * scale, baseH = img.height * scale;
      // Alternate ken-burns direction per scene for variety
      const dir = sceneIdx % 2 === 0 ? 1 : -1;
      const e = ease(localT);
      const zoom = 1 + 0.15 * e;
      const dx = dir * -40 * e;
      const dy = -18 * e;
      const w = baseW * zoom, h = baseH * zoom;
      const x = (W - w) / 2 + dx, y = (H - h) / 2 + dy;
      ctx.drawImage(img, x, y, w, h);
    };

    return await new Promise<Blob>((resolve, reject) => {
      let frame = 0;
      const drawFrame = () => {
        const global = frame / (totalFrames - 1);
        const sceneF = frame / perScene;
        const sceneIdx = Math.min(imgs.length - 1, Math.floor(sceneF));
        const localT = sceneF - sceneIdx;

        ctx.fillStyle = "#000"; ctx.fillRect(0, 0, W, H);
        drawImg(imgs[sceneIdx], sceneIdx, localT);

        // Crossfade into next scene during last 25% of the current scene
        const fadeInto = 0.75;
        if (localT > fadeInto && sceneIdx < imgs.length - 1) {
          const blend = (localT - fadeInto) / (1 - fadeInto);
          ctx.globalAlpha = blend;
          drawImg(imgs[sceneIdx + 1], sceneIdx + 1, 0);
          ctx.globalAlpha = 1;
        }

        // Vignette
        const grd = ctx.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, H * 0.75);
        grd.addColorStop(0, "rgba(0,0,0,0)");
        grd.addColorStop(1, "rgba(0,0,0,0.55)");
        ctx.fillStyle = grd; ctx.fillRect(0, 0, W, H);

        // Global fade in/out
        const fade = Math.min(1, global / 0.06) * Math.min(1, (1 - global) / 0.06);
        if (fade < 1) {
          ctx.fillStyle = `rgba(0,0,0,${1 - fade})`;
          ctx.fillRect(0, 0, W, H);
        }

        setProgress(40 + Math.round((frame / totalFrames) * 55));
        frame++;
        if (frame >= totalFrames) {
          recorder.stop();
          done.then(resolve).catch(reject);
          return;
        }
        setTimeout(() => requestAnimationFrame(drawFrame), 1000 / FPS);
      };
      requestAnimationFrame(drawFrame);
    });
  };

  const generate = async () => {
    setErrorMsg(null);
    try {
      const urls = await fetchKeyframes();
      const blob = await renderVideo(urls);
      setVideoUrl(URL.createObjectURL(blob));
      setProgress(100);
      setStatus("ready");
    } catch (e: any) {
      console.error("VideoPlayer error", e);
      const msg = e?.message ?? "unbekannter Fehler";
      setErrorMsg(msg);
      setStatus("error");
      toast.error("Video fehlgeschlagen: " + msg);
    }
  };


  const toggle = () => {
    const v = videoRef.current; if (!v) return;
    if (playing) v.pause(); else v.play();
  };

  const download = () => {
    if (!videoUrl) return;
    const a = document.createElement("a");
    a.href = videoUrl;
    const ext = videoUrl.includes("mp4") ? "mp4" : "webm";
    a.download = `${(request.title || "ai-video").replace(/\s+/g, "-").toLowerCase()}.${ext}`;
    a.click();
  };

  return (
    <div className="my-3 rounded-2xl border border-white/10 bg-gradient-to-br from-accent/10 via-background/40 to-primary/10 p-4 backdrop-blur-md">
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="min-w-0">
          <div className="font-display text-base truncate flex items-center gap-1.5">
            <Film className="h-4 w-4 text-accent" /> {request.title || "AI Video"}
          </div>
          <div className="text-xs text-muted-foreground truncate">{request.prompt}</div>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {status === "ready" && (
            <>
              <Button size="icon" variant="ghost" onClick={download} className="h-9 w-9" title="Video herunterladen">
                <Download className="h-4 w-4" />
              </Button>
              <Button size="icon" onClick={toggle} className="h-10 w-10 bg-foreground text-background hover:bg-foreground/90">
                {playing ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 ml-0.5" />}
              </Button>
            </>
          )}
          {(status === "fetching-image" || status === "rendering") && (
            <Button size="icon" disabled className="h-10 w-10"><Loader2 className="h-5 w-5 animate-spin" /></Button>
          )}
          {status === "idle" && (
            <Button size="sm" onClick={generate} className="gap-1.5"><Sparkles className="h-4 w-4" /> Generieren</Button>
          )}
          {status === "error" && (
            <Button size="sm" variant="outline" onClick={generate} className="gap-1.5"><RefreshCcw className="h-4 w-4" /> Erneut</Button>
          )}
        </div>
      </div>

      {(status === "fetching-image" || status === "rendering") && (
        <div className="space-y-1.5">
          <div className="text-xs text-muted-foreground">
            {status === "fetching-image" ? "Erstelle Schlüsselbild mit KI…" : "Rendere Cinematic-Animation…"}
          </div>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-foreground/10">
            <div className="h-full bg-accent transition-all" style={{ width: `${progress}%` }} />
          </div>
        </div>
      )}

      {status === "idle" && (
        <p className="text-xs text-muted-foreground">
          Klick „Generieren" — die KI erstellt ein Schlüsselbild und animiert es zu einem 5-Sek-Clip. 100% kostenlos, läuft im Browser.
        </p>
      )}

      {status === "error" && errorMsg && (
        <p className="text-xs text-destructive">{errorMsg}</p>
      )}

      {imgUrl && status !== "ready" && (
        <img src={imgUrl} alt={request.prompt} className="mt-2 rounded-xl border border-white/10 max-w-full opacity-70" />
      )}

      {videoUrl && (
        <video
          ref={videoRef}
          src={videoUrl}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => setPlaying(false)}
          className="mt-2 w-full rounded-xl border border-white/10"
          playsInline
          controls
        />
      )}

      <canvas ref={canvasRef} className="hidden" />
    </div>
  );
}
