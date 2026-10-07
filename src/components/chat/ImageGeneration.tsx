// Bild im Chat: Lade-Animation während der Generierung, Klick darauf startet das Mini-Spiel
// "Farbfänger" (fallende Farbtropfen mit der Palette auffangen, daraus entsteht ein eigenes
// Bild). Fertige Bilder werden mit einem Aufdeck-Effekt eingeblendet, Klick öffnet die Großansicht.
import { useCallback, useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Download, ExternalLink, Gamepad2, Sparkles, X } from "lucide-react";

const STYLE = `
@keyframes mg-spin { to { transform: rotate(360deg); } }
@keyframes mg-float { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(6%,-8%) scale(1.15); } }
@keyframes mg-float2 { 0%,100% { transform: translate(0,0) scale(1.1); } 50% { transform: translate(-8%,6%) scale(0.9); } }
@keyframes mg-twinkle { 0%,100% { opacity: 0; transform: scale(.4); } 50% { opacity: 1; transform: scale(1); } }
@keyframes mg-scan { 0% { transform: translateY(-100%); } 100% { transform: translateY(100%); } }
@keyframes mg-pulse { 0%,100% { opacity: .55; } 50% { opacity: 1; } }
.mg-reveal { clip-path: circle(0% at 50% 50%); filter: blur(22px) saturate(0); transform: scale(1.06); }
.mg-reveal.mg-in { clip-path: circle(75% at 50% 50%); filter: blur(0) saturate(1); transform: scale(1);
  transition: clip-path 1.1s cubic-bezier(.2,.8,.2,1), filter 1.4s ease-out, transform 1.4s ease-out; }
`;

const STARS = Array.from({ length: 14 }, (_, i) => ({
  left: `${(i * 37) % 100}%`, top: `${(i * 53 + 11) % 100}%`, delay: `${(i * 0.37) % 2.4}s`, size: 3 + (i % 3) * 2,
}));

export type GeneratedImage = { url: string; prompt: string; fallback?: boolean; reasons?: string[] };

/** Direkter Pollinations-Link: lädt der Browser selbst, wenn alle Server-Anbieter ausgefallen sind. */
export function browserImageUrl(prompt: string): string {
  const seed = Math.floor(Math.random() * 1e6);
  return `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt.slice(0, 800))}?model=flux&width=1024&height=1024&nologo=true&referrer=mythoscraft&seed=${seed}`;
}

type Props = {
  /** Gesetzt, solange der Server das Bild noch erzeugt. */
  pending?: { prompt: string };
  image?: GeneratedImage;
};

export default function ImageGeneration({ pending, image }: Props) {
  const [playing, setPlaying] = useState(false);
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");

  // Bild vorladen: die Animation bleibt stehen, bis es wirklich da ist (Browser-Backup kann dauern).
  useEffect(() => {
    if (!image?.url) return;
    setStatus("loading");
    const img = new Image();
    let alive = true;
    img.onload = () => alive && setStatus("ready");
    img.onerror = () => alive && setStatus("error");
    img.src = image.url;
    return () => { alive = false; };
  }, [image?.url]);

  const busy = !!pending || (!!image && status === "loading");
  const ready = !!image && status === "ready";

  return (
    <div className="not-prose mt-2 w-full max-w-md">
      <style>{STYLE}</style>
      {playing && (busy || ready) ? (
        <div className="relative">
          <PaintCatcher onExit={() => setPlaying(false)} />
          {ready && (
            <button
              onClick={() => setPlaying(false)}
              className="absolute left-1/2 top-3 -translate-x-1/2 rounded-full bg-emerald-500 px-4 py-1.5 text-xs font-semibold text-white shadow-lg"
              style={{ animation: "mg-pulse 1.2s ease-in-out infinite" }}
            >
              ✨ Dein Bild ist fertig, ansehen
            </button>
          )}
        </div>
      ) : ready ? (
        <RevealImage image={image!} onOpen={() => setOpen(true)} />
      ) : busy ? (
        <PendingCard prompt={pending?.prompt ?? image!.prompt} onPlay={() => setPlaying(true)} />
      ) : image && status === "error" ? (
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">
          ❌ Bild-Generierung fehlgeschlagen. Alle Anbieter sind gerade nicht erreichbar, versuch es gleich nochmal.
          {!!image.reasons?.length && (
            <ul className="mt-2 list-disc pl-5 text-xs text-red-200/70">
              {image.reasons.map((r) => <li key={r}>{r}</li>)}
            </ul>
          )}
        </div>
      ) : null}
      {ready && (
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogContent className="max-w-4xl border-white/10 bg-black/90 p-2">
            <DialogTitle className="sr-only">{image!.prompt}</DialogTitle>
            <img src={image!.url} alt={image!.prompt} className="max-h-[80vh] w-full rounded-lg object-contain" />
            <div className="flex flex-wrap items-center justify-between gap-2 px-2 pb-1 text-xs text-muted-foreground">
              <span className="line-clamp-2 flex-1">{image!.prompt}</span>
              <div className="flex gap-1">
                <a href={image!.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 rounded-md px-2 py-1 hover:bg-white/10">
                  <ExternalLink className="h-3.5 w-3.5" /> Öffnen
                </a>
                <button onClick={() => download(image!.url)} className="inline-flex items-center gap-1 rounded-md px-2 py-1 hover:bg-white/10">
                  <Download className="h-3.5 w-3.5" /> Speichern
                </button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}

async function download(url: string) {
  try {
    const blob = await (await fetch(url)).blob();
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `mythos-bild-${Date.now()}.${blob.type.includes("jpeg") ? "jpg" : "png"}`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 10_000);
  } catch { window.open(url, "_blank"); }
}

function PendingCard({ prompt, onPlay }: { prompt: string; onPlay: () => void }) {
  return (
    <button
      onClick={onPlay}
      className="group relative block aspect-square w-full overflow-hidden rounded-2xl border border-white/10 bg-[#0b0718] text-left"
      title="Klick zum Spielen"
    >
      {/* Wabernde Farbwolken, wie Farbe, die sich zu einem Bild sortiert */}
      <div className="absolute -inset-1/4 opacity-80 blur-3xl" style={{ background: "conic-gradient(from 0deg, #7c3aed, #ec4899, #f59e0b, #22d3ee, #7c3aed)", animation: "mg-spin 9s linear infinite" }} />
      <div className="absolute left-[10%] top-[15%] h-1/2 w-1/2 rounded-full bg-fuchsia-500/50 blur-2xl" style={{ animation: "mg-float 5s ease-in-out infinite" }} />
      <div className="absolute bottom-[10%] right-[10%] h-1/2 w-1/2 rounded-full bg-cyan-400/40 blur-2xl" style={{ animation: "mg-float2 6s ease-in-out infinite" }} />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_30%,rgba(5,3,15,.75)_100%)]" />
      <div className="pointer-events-none absolute inset-x-0 h-1/3 bg-gradient-to-b from-transparent via-white/10 to-transparent" style={{ animation: "mg-scan 2.4s linear infinite" }} />
      {STARS.map((s, i) => (
        <span key={i} className="absolute rounded-full bg-white" style={{ left: s.left, top: s.top, width: s.size, height: s.size, animation: `mg-twinkle 2.4s ease-in-out ${s.delay} infinite` }} />
      ))}
      <div className="absolute inset-x-0 bottom-0 p-4">
        <div className="flex items-center gap-2 text-sm font-medium text-white">
          <Sparkles className="h-4 w-4" style={{ animation: "mg-pulse 1.4s ease-in-out infinite" }} />
          Mythos malt dein Bild…
        </div>
        <p className="mt-1 line-clamp-2 text-xs text-white/70">{prompt}</p>
        <div className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-[11px] font-medium text-white backdrop-blur transition group-hover:bg-white/25">
          <Gamepad2 className="h-3.5 w-3.5" /> Klick zum Spielen: Farbfänger
        </div>
      </div>
    </button>
  );
}

function RevealImage({ image, onOpen }: { image: { url: string; prompt: string }; onOpen: () => void }) {
  const [loaded, setLoaded] = useState(false);
  return (
    <button onClick={onOpen} className="relative block w-full overflow-hidden rounded-2xl border border-white/10 bg-[#0b0718]" title="Groß ansehen">
      {!loaded && <div className="aspect-square w-full animate-pulse bg-gradient-to-br from-violet-900/40 to-cyan-900/30" />}
      <img
        src={image.url}
        alt={image.prompt}
        onLoad={() => requestAnimationFrame(() => setLoaded(true))}
        className={`mg-reveal ${loaded ? "mg-in" : "absolute inset-0 opacity-0"} h-auto w-full`}
      />
    </button>
  );
}

// ---------------- Mini-Spiel "Farbfänger" ----------------

type Drop = { x: number; y: number; vy: number; r: number; hue: number; bad: boolean };

function PaintCatcher({ onExit }: { onExit: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [score, setScore] = useState(0);
  const [best, setBest] = useState(() => { try { return Number(localStorage.getItem("mythos_paint_best") || 0); } catch { return 0; } });
  const [lives, setLives] = useState(3);
  const [over, setOver] = useState(false);
  const [round, setRound] = useState(0);
  const restart = useCallback(() => { setScore(0); setLives(3); setOver(false); setRound((r) => r + 1); }, []);

  useEffect(() => {
    const cv = canvasRef.current;
    if (!cv) return;
    const ctx = cv.getContext("2d")!;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const size = cv.clientWidth;
    cv.width = size * dpr; cv.height = size * dpr;
    ctx.scale(dpr, dpr);
    const W = size, H = size;
    const art = document.createElement("canvas"); // Hier entsteht aus gefangenen Farben ein eigenes Bild.
    art.width = W * dpr; art.height = H * dpr;
    const actx = art.getContext("2d")!; actx.scale(dpr, dpr);

    let px = W / 2, target = W / 2, hue = 280;
    const pw = W * 0.24;
    let drops: Drop[] = [], sparks: { x: number; y: number; vx: number; vy: number; life: number; hue: number }[] = [];
    let t = 0, spawn = 0, pts = 0, hp = 3, dead = false, raf = 0, combo = 0;
    const keys = new Set<string>();

    const setTarget = (clientX: number) => { const r = cv.getBoundingClientRect(); target = Math.max(pw / 2, Math.min(W - pw / 2, clientX - r.left)); };
    const onMove = (e: PointerEvent) => setTarget(e.clientX);
    const onKey = (e: KeyboardEvent) => { if (["ArrowLeft", "ArrowRight", "a", "d"].includes(e.key)) { e.preventDefault(); if (e.type === "keydown") keys.add(e.key); else keys.delete(e.key); } };
    cv.addEventListener("pointermove", onMove);
    cv.addEventListener("pointerdown", onMove);
    window.addEventListener("keydown", onKey);
    window.addEventListener("keyup", onKey);

    const splat = (x: number, y: number, r: number, h: number) => {
      actx.globalAlpha = 0.55;
      actx.fillStyle = `hsl(${h} 85% 60%)`;
      for (let i = 0; i < 6; i++) {
        actx.beginPath();
        actx.arc(x + (Math.random() - 0.5) * r * 2.4, y + (Math.random() - 0.5) * r * 2.4, r * (0.3 + Math.random() * 0.8), 0, Math.PI * 2);
        actx.fill();
      }
      actx.globalAlpha = 1;
    };

    const loop = () => {
      t++;
      const speed = 1 + pts / 25;
      if (keys.has("ArrowLeft") || keys.has("a")) target = Math.max(pw / 2, target - 7);
      if (keys.has("ArrowRight") || keys.has("d")) target = Math.min(W - pw / 2, target + 7);
      px += (target - px) * 0.25;
      if (!dead && --spawn <= 0) {
        spawn = Math.max(14, 46 - pts) + Math.random() * 20;
        const bad = pts > 4 && Math.random() < 0.18; // Graue Kleckse: nicht fangen, die verschmieren die Palette.
        drops.push({ x: 20 + Math.random() * (W - 40), y: -20, vy: (1.6 + Math.random() * 1.4) * speed, r: 9 + Math.random() * 6, hue: Math.floor(Math.random() * 360), bad });
      }

      // Hintergrund: dunkel + das wachsende Farbbild
      ctx.fillStyle = "#0b0718"; ctx.fillRect(0, 0, W, H);
      ctx.drawImage(art, 0, 0, W, H);
      ctx.fillStyle = "rgba(11,7,24,.35)"; ctx.fillRect(0, 0, W, H);

      const py = H - 26;
      for (const d of drops) {
        d.y += d.vy;
        const caught = d.y + d.r > py - 6 && d.y < py + 10 && Math.abs(d.x - px) < pw / 2 + d.r * 0.5;
        if (caught && !dead) {
          d.y = H * 3;
          if (d.bad) { hp--; combo = 0; setLives(hp); splat(d.x, py - 30, 26, 0); if (hp <= 0) dead = true; }
          else {
            combo++; pts += combo >= 5 ? 2 : 1; setScore(pts); hue = d.hue;
            splat(Math.random() * W, Math.random() * (H - 60), 14 + Math.random() * 18, d.hue);
            for (let i = 0; i < 12; i++) sparks.push({ x: d.x, y: py - 6, vx: (Math.random() - 0.5) * 6, vy: -Math.random() * 5, life: 30, hue: d.hue });
          }
        } else if (d.y - d.r > H && d.y < H * 2) {
          d.y = H * 3;
          if (!d.bad && !dead) { combo = 0; }
        }
        if (d.y < H + 30) {
          ctx.beginPath();
          // Tropfenform
          ctx.moveTo(d.x, d.y - d.r * 1.8);
          ctx.quadraticCurveTo(d.x + d.r, d.y - d.r * 0.2, d.x, d.y + d.r);
          ctx.quadraticCurveTo(d.x - d.r, d.y - d.r * 0.2, d.x, d.y - d.r * 1.8);
          ctx.fillStyle = d.bad ? "#4b5563" : `hsl(${d.hue} 90% 62%)`;
          ctx.shadowColor = d.bad ? "transparent" : `hsl(${d.hue} 90% 60%)`; ctx.shadowBlur = 14;
          ctx.fill(); ctx.shadowBlur = 0;
        }
      }
      drops = drops.filter((d) => d.y < H * 2);

      for (const s of sparks) { s.x += s.vx; s.y += s.vy; s.vy += 0.25; s.life--; ctx.fillStyle = `hsla(${s.hue} 90% 65% / ${s.life / 30})`; ctx.fillRect(s.x, s.y, 3, 3); }
      sparks = sparks.filter((s) => s.life > 0);

      // Palette
      ctx.save();
      ctx.translate(px, py);
      ctx.fillStyle = "#e9d5ff";
      ctx.beginPath(); ctx.ellipse(0, 0, pw / 2, 11, 0, 0, Math.PI * 2); ctx.fill();
      [-0.3, -0.1, 0.1, 0.3].forEach((o, i) => { ctx.fillStyle = `hsl(${(hue + i * 40) % 360} 85% 58%)`; ctx.beginPath(); ctx.arc(o * pw, -2, 4.5, 0, Math.PI * 2); ctx.fill(); });
      ctx.restore();

      if (combo >= 5 && !dead) { ctx.fillStyle = `hsl(${hue} 90% 70%)`; ctx.font = "bold 13px system-ui"; ctx.textAlign = "center"; ctx.fillText(`Combo x${combo}  (doppelte Punkte)`, W / 2, 24); }
      if (dead) {
        setOver(true);
        setBest((b) => { const n = Math.max(b, pts); try { localStorage.setItem("mythos_paint_best", String(n)); } catch { /* egal */ } return n; });
        return; // Das gemalte Bild bleibt stehen.
      }
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => {
      cancelAnimationFrame(raf);
      cv.removeEventListener("pointermove", onMove);
      cv.removeEventListener("pointerdown", onMove);
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("keyup", onKey);
    };
  }, [round]);

  return (
    <div className="relative aspect-square w-full touch-none select-none overflow-hidden rounded-2xl border border-white/10">
      <canvas ref={canvasRef} className="h-full w-full cursor-none" />
      <div className="pointer-events-none absolute left-3 top-3 text-xs font-semibold text-white/90">
        🎨 {score} <span className="text-white/50">· Best {Math.max(best, score)}</span>
      </div>
      <div className="pointer-events-none absolute right-10 top-3 text-xs">{"❤️".repeat(Math.max(lives, 0))}</div>
      <button onClick={onExit} className="absolute right-2 top-2 rounded-full bg-black/40 p-1 text-white/80 hover:bg-black/60" title="Spiel beenden">
        <X className="h-3.5 w-3.5" />
      </button>
      {score === 0 && !over && (
        <div className="pointer-events-none absolute inset-x-0 bottom-14 text-center text-[11px] text-white/70">
          Fang die bunten Tropfen mit der Palette. Graue Kleckse meiden!
        </div>
      )}
      {over && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/40 text-white backdrop-blur-[2px]">
          <div className="text-lg font-bold">Dein Farbbild: {score} Punkte</div>
          <button onClick={restart} className="rounded-full bg-white/90 px-4 py-1.5 text-xs font-semibold text-black hover:bg-white">Nochmal</button>
        </div>
      )}
    </div>
  );
}
