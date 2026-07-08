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
    setProgress(3);
    const base = request.prompt;
    // 6 cinematic beats — each a distinct camera / composition so the "video" reads as real footage
    const prompts = [
      `${base}, ultra wide establishing shot, cinematic anamorphic, dawn light, atmospheric haze, film grain, 35mm`,
      `${base}, tracking shot mid-distance, dynamic motion, shallow depth of field, volumetric light`,
      `${base}, low angle hero shot, dramatic rim lighting, epic scale, cinematic color grade`,
      `${base}, over-the-shoulder perspective, tension, motion blur background, teal-orange grade`,
      `${base}, close-up detail, macro focus, expressive mood, bokeh, sharp texture`,
      `${base}, final wide reveal, golden hour, sweeping vista, cinematic finale`,
    ];
    const urls: string[] = [];
    for (let i = 0; i < prompts.length; i++) {
      const url = await fetchImage(prompts[i]);
      urls.push(url);
      setImgUrl(url);
      setProgress(3 + Math.round(((i + 1) / prompts.length) * 45));
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
    const duration = Math.min(15, Math.max(6, request.duration ?? 10));
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
    const recorder = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 8_000_000 });
    const chunks: Blob[] = [];
    recorder.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
    const done = new Promise<Blob>((resolve) => {
      recorder.onstop = () => resolve(new Blob(chunks, { type: mime }));
    });
    recorder.start();

    const totalFrames = duration * FPS;
    const perScene = totalFrames / imgs.length;
    const ease = (t: number) => t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    // 6 distinct camera moves — each scene gets a different real "camera motion"
    const moves = [
      { zoomFrom: 1.0, zoomTo: 1.18, dx: -60, dy: 0, rot: 0 },      // slow push-in wide
      { zoomFrom: 1.15, zoomTo: 1.0, dx: 80, dy: -20, rot: 0.5 },   // pull-back track right
      { zoomFrom: 1.25, zoomTo: 1.05, dx: 0, dy: 40, rot: -0.3 },   // crane-down hero
      { zoomFrom: 1.0, zoomTo: 1.3, dx: -50, dy: 30, rot: 0 },      // push-in OTS
      { zoomFrom: 1.4, zoomTo: 1.1, dx: 30, dy: -30, rot: 0 },      // macro reveal
      { zoomFrom: 1.0, zoomTo: 1.2, dx: 60, dy: 0, rot: 0 },        // final sweep
    ];

    const drawImg = (img: HTMLImageElement, sceneIdx: number, localT: number, alpha = 1) => {
      const m = moves[sceneIdx % moves.length];
      const e = ease(localT);
      const zoom = m.zoomFrom + (m.zoomTo - m.zoomFrom) * e;
      const dx = m.dx * e;
      const dy = m.dy * e;
      const rot = (m.rot * Math.PI / 180) * e;
      const baseScale = Math.max(W / img.width, H / img.height);
      const w = img.width * baseScale * zoom, h = img.height * baseScale * zoom;
      ctx.save();
      ctx.globalAlpha = alpha;
      ctx.translate(W / 2 + dx, H / 2 + dy);
      ctx.rotate(rot);
      ctx.drawImage(img, -w / 2, -h / 2, w, h);
      ctx.restore();
    };

    return await new Promise<Blob>((resolve, reject) => {
      let frame = 0;
      const drawFrame = () => {
        const global = frame / (totalFrames - 1);
        const sceneF = frame / perScene;
        const sceneIdx = Math.min(imgs.length - 1, Math.floor(sceneF));
        const localT = sceneF - sceneIdx;

        ctx.fillStyle = "#000"; ctx.fillRect(0, 0, W, H);
        drawImg(imgs[sceneIdx], sceneIdx, localT, 1);

        // Long crossfade (last 35%) into next scene for smoother morph
        const fadeInto = 0.65;
        if (localT > fadeInto && sceneIdx < imgs.length - 1) {
          const blend = ease((localT - fadeInto) / (1 - fadeInto));
          drawImg(imgs[sceneIdx + 1], sceneIdx + 1, 0, blend);
        }

        // Film grain
        if (frame % 2 === 0) {
          ctx.globalAlpha = 0.04;
          ctx.fillStyle = Math.random() > 0.5 ? "#fff" : "#000";
          for (let g = 0; g < 40; g++) {
            ctx.fillRect(Math.random() * W, Math.random() * H, 2, 2);
          }
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
