import { useEffect, useRef } from "react";

type Props = { level: number; status: "idle" | "listening" | "speaking"; className?: string };

/** Audio-reactive plasma orb for the live voice mode. */
export default function VoiceOrb({ level, status, className }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const levelRef = useRef(0);
  const smoothRef = useRef(0);
  const statusRef = useRef(status);
  levelRef.current = level;
  statusRef.current = status;

  useEffect(() => {
    const canvas = canvasRef.current; if (!canvas) return;
    const ctx = canvas.getContext("2d"); if (!ctx) return;
    let t = 0, raf = 0;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const resize = () => {
      canvas.width = Math.max(1, canvas.clientWidth * dpr);
      canvas.height = Math.max(1, canvas.clientHeight * dpr);
    };
    resize();
    window.addEventListener("resize", resize);

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
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.closePath();
      ctx.fillStyle = fill;
      ctx.fill();
    };

    const draw = () => {
      t += 0.016;
      const speaking = statusRef.current === "speaking";
      const listening = statusRef.current === "listening";
      const target = speaking
        ? 0.45 + 0.35 * Math.abs(Math.sin(t * 4.2))
        : listening ? levelRef.current * 1.6 : 0.06;
      smoothRef.current += (Math.min(target, 1) - smoothRef.current) * 0.12;
      const amp = smoothRef.current;

      const W = canvas.width, H = canvas.height;
      ctx.clearRect(0, 0, W, H);
      const cx = W / 2, cy = H / 2;
      const base = Math.min(W, H) * 0.24;
      const r = base * (1 + amp * 0.28);

      const hue = speaking ? 288 : listening ? 196 : 225;
      const hue2 = speaking ? 322 : listening ? 165 : 262;

      const halo = ctx.createRadialGradient(cx, cy, r * 0.3, cx, cy, r * 2.6);
      halo.addColorStop(0, `hsla(${hue}, 95%, 62%, ${0.30 + amp * 0.35})`);
      halo.addColorStop(0.55, `hsla(${hue2}, 90%, 55%, ${0.10 + amp * 0.16})`);
      halo.addColorStop(1, `hsla(${hue2}, 90%, 50%, 0)`);
      ctx.fillStyle = halo;
      ctx.beginPath(); ctx.arc(cx, cy, r * 2.6, 0, Math.PI * 2); ctx.fill();

      ctx.globalCompositeOperation = "lighter";
      blob(cx, cy, r * 1.32, 1.7, amp * 0.7, `hsla(${hue2}, 95%, 58%, ${0.10 + amp * 0.10})`);
      blob(cx, cy, r * 1.15, 3.1, amp * 0.85, `hsla(${hue}, 95%, 60%, ${0.14 + amp * 0.12})`);
      ctx.globalCompositeOperation = "source-over";

      const core = ctx.createRadialGradient(cx - r * 0.35, cy - r * 0.4, r * 0.08, cx, cy, r * 1.05);
      core.addColorStop(0, `hsla(${hue}, 100%, 92%, 1)`);
      core.addColorStop(0.42, `hsla(${hue}, 96%, 68%, 1)`);
      core.addColorStop(1, `hsla(${hue2}, 88%, 34%, 1)`);
      ctx.shadowColor = `hsla(${hue}, 95%, 60%, 0.75)`;
      ctx.shadowBlur = r * (0.5 + amp);
      blob(cx, cy, r, 0.4, amp, core);
      ctx.shadowBlur = 0;

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

  return <canvas ref={canvasRef} className={className ?? "absolute inset-0 w-full h-full"} />;
}
