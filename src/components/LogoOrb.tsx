import { useEffect, useRef } from "react";

type Status = "idle" | "listening" | "speaking";
type Props = {
  status: Status;
  /** Aktuelle Lautstärke 0..1 (Mythos-Stimme beim Sprechen, Mikrofon beim Zuhören). */
  getLevel: () => number;
  className?: string;
};

/**
 * Sprach-Orb im Look des Mythoscraft-Logos: die Blüte pulsiert, leuchtet und sendet Wellen
 * zur echten Stimme aus. Beim Zuhören türkis, beim Sprechen violett, sonst ruhiges Atmen.
 */
export default function LogoOrb({ status, getLevel, className }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const statusRef = useRef(status);
  const levelRef = useRef(getLevel);
  statusRef.current = status;
  levelRef.current = getLevel;

  useEffect(() => {
    const canvas = canvasRef.current; if (!canvas) return;
    const ctx = canvas.getContext("2d"); if (!ctx) return;
    const img = new Image(); img.src = "/icon.png";
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const resize = () => { canvas.width = Math.max(1, canvas.clientWidth * dpr); canvas.height = Math.max(1, canvas.clientHeight * dpr); };
    resize();
    const ro = new ResizeObserver(resize); ro.observe(canvas);
    let raf = 0, t = 0, amp = 0, lastRing = 0;
    const rings: { r: number; a: number; hue: number }[] = [];

    const draw = () => {
      t += 1 / 60;
      const st = statusRef.current;
      const target = st === "idle" ? 0 : Math.min(1, levelRef.current());
      amp += (target - amp) * (target > amp ? 0.35 : 0.12);
      const W = canvas.width, H = canvas.height, cx = W / 2, cy = H / 2;
      const base = Math.min(W, H) * 0.3;
      const hue = st === "listening" ? 190 : st === "speaking" ? 278 : 255;
      ctx.clearRect(0, 0, W, H);

      // Leuchten hinter dem Logo
      const halo = ctx.createRadialGradient(cx, cy, base * 0.2, cx, cy, base * 2.3);
      halo.addColorStop(0, `hsla(${hue}, 95%, 62%, ${0.28 + amp * 0.45})`);
      halo.addColorStop(0.5, `hsla(${hue + 25}, 90%, 55%, ${0.08 + amp * 0.18})`);
      halo.addColorStop(1, `hsla(${hue + 25}, 90%, 50%, 0)`);
      ctx.fillStyle = halo; ctx.beginPath(); ctx.arc(cx, cy, base * 2.3, 0, Math.PI * 2); ctx.fill();

      // Wellen beim Sprechen / Zuhören
      if (st !== "idle" && amp > 0.3 && t - lastRing > 0.22) { rings.push({ r: base * 0.85, a: 0.25 + amp * 0.4, hue }); lastRing = t; }
      for (let i = rings.length - 1; i >= 0; i--) {
        const r = rings[i];
        r.r += (1.6 + amp * 3) * dpr; r.a *= 0.965;
        if (r.a < 0.01 || r.r > base * 2.6) { rings.splice(i, 1); continue; }
        ctx.beginPath(); ctx.arc(cx, cy, r.r, 0, Math.PI * 2);
        ctx.strokeStyle = `hsla(${r.hue}, 100%, 75%, ${r.a})`; ctx.lineWidth = Math.max(1, base * 0.012); ctx.stroke();
      }

      if (img.complete && img.naturalWidth) {
        const breathe = st === "idle" ? 0.025 * Math.sin(t * 1.6) : 0;
        const size = base * 2 * (1 + amp * 0.2 + breathe);
        const rot = t * 0.06 + amp * 0.06 * Math.sin(t * 9);
        ctx.save(); ctx.translate(cx, cy); ctx.rotate(rot);
        ctx.shadowColor = `hsla(${hue}, 100%, 65%, ${0.55 + amp * 0.4})`;
        ctx.shadowBlur = base * (0.18 + amp * 0.5);
        ctx.drawImage(img, -size / 2, -size / 2, size, size);
        ctx.shadowBlur = 0;
        // Schimmer: leicht verdrehte, hellere Kopie der Blüte, stärker je lauter
        if (amp > 0.02) {
          ctx.globalCompositeOperation = "lighter";
          ctx.globalAlpha = Math.min(0.55, amp * 0.6);
          ctx.rotate(0.14 * Math.sin(t * 3));
          const s2 = size * (1.05 + amp * 0.08);
          ctx.drawImage(img, -s2 / 2, -s2 / 2, s2, s2);
          ctx.globalAlpha = 1; ctx.globalCompositeOperation = "source-over";
        }
        ctx.restore();
      }
      raf = requestAnimationFrame(draw);
    };
    draw();
    return () => { cancelAnimationFrame(raf); ro.disconnect(); };
  }, []);

  return <canvas ref={canvasRef} className={className ?? "absolute inset-0 w-full h-full"} />;
}
