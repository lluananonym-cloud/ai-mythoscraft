import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import LabsShell from "@/components/labs/LabsShell";
import AppPreview from "@/components/labs/AppPreview";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Globe2, RotateCcw, RotateCw, ZoomIn, ZoomOut, MessageSquare, Image as ImageIcon, AppWindow, Loader2 } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { listMyApps } from "@/lib/labs";

type Kind = "chat" | "image" | "app";
type Obj = { key: string; kind: Kind; title: string; chatId?: string; url?: string; html?: string };
const KIND_LABEL: Record<Kind, string> = { chat: "Chats", image: "Bilder", app: "Apps" };
const KIND_ICON = { chat: MessageSquare, image: ImageIcon, app: AppWindow };
const WORLD = 640; // Durchmesser der Insel in px (wird per Zoom an den Bildschirm angepasst)
const TILT = (50 * Math.PI) / 180;
const COS = Math.cos(TILT);
// Bäume und Steine als Deko, damit man das Drehen sieht.
const DECOR = [
  { x: 250, y: 40, emoji: "🌳" }, { x: -240, y: -90, emoji: "🌲" }, { x: 60, y: -265, emoji: "🌳" }, { x: -120, y: 250, emoji: "🌴" },
  { x: 180, y: 190, emoji: "🪨" }, { x: -280, y: 60, emoji: "🌷" }, { x: 210, y: -180, emoji: "🌲" },
];

/** Punkt auf der Insel (x, y) gedreht und schräg von oben projiziert. Hinten = kleiner und weiter unten im Stapel. */
function project(x: number, y: number, angleDeg: number) {
  const a = (angleDeg * Math.PI) / 180;
  const xr = x * Math.cos(a) - y * Math.sin(a);
  const yr = x * Math.sin(a) + y * Math.cos(a);
  return { x: xr, y: yr * COS, s: 0.85 + ((yr + WORLD / 2) / WORLD) * 0.3, z: Math.round(yr) + 1000 };
}

/** Steht aufrecht an seinem Punkt: unten mittig verankert, vorne liegt über hinten. */
function placeStyle(p: ReturnType<typeof project>, zoom: number): React.CSSProperties {
  return {
    left: `calc(50% + ${p.x * zoom}px)`, top: `calc(58% + ${p.y * zoom}px)`,
    transform: `translate(-50%, -100%) scale(${p.s * Math.min(1, Math.max(0.6, zoom * 1.1))})`, transformOrigin: "50% 100%", zIndex: p.z,
  };
}

/** Welt-Modus: deine Chats, Bilder und Apps stehen als anklickbare Objekte auf einer kleinen 3D-Insel. */
export default function Welt() {
  const { user } = useAuth();
  const nav = useNavigate();
  const [objs, setObjs] = useState<Obj[] | null>(null);
  const [show, setShow] = useState<Record<Kind, boolean>>({ chat: true, image: true, app: true });
  const [angle, setAngle] = useState(-20);
  const [zoom, setZoom] = useState(1);
  const [picked, setPicked] = useState<Obj | null>(null);
  const [app, setApp] = useState<{ title: string; html: string } | null>(null);
  const sceneRef = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; moved: number } | null>(null);

  // Startzoom passend zur Bildschirmbreite, damit die Insel aufs Handy passt.
  useEffect(() => {
    const w = sceneRef.current?.clientWidth || window.innerWidth;
    setZoom(Math.max(0.45, Math.min(1, (w - 16) / (WORLD * 0.95))));
  }, []);

  useEffect(() => {
    if (!user) return;
    (async () => {
      const [convs, imgs, games] = await Promise.all([
        supabase.from("conversations").select("id,title").eq("user_id", user.id).order("updated_at", { ascending: false }).limit(8),
        supabase.from("messages").select("id,metadata").eq("role", "assistant").not("metadata->image", "is", null).order("created_at", { ascending: false }).limit(6),
        supabase.from("pro_games").select("id,title,html").eq("user_id", user.id).order("created_at", { ascending: false }).limit(4),
      ]);
      const out: Obj[] = [];
      for (const c of convs.data || []) out.push({ key: "c" + c.id, kind: "chat", title: c.title || "Chat", chatId: c.id });
      for (const m of imgs.data || []) {
        const img = (m.metadata as { image?: { url?: string; prompt?: string } } | null)?.image;
        if (typeof img?.url === "string") out.push({ key: "i" + m.id, kind: "image", title: img.prompt || "Bild", url: img.url });
      }
      for (const a of listMyApps().slice(0, 6)) out.push({ key: "a" + a.id, kind: "app", title: a.title, html: a.html });
      for (const g of games.data || []) out.push({ key: "g" + g.id, kind: "app", title: g.title, html: g.html });
      setObjs(out);
    })().catch(() => setObjs([]));
  }, [user]);

  // Objekte auf Ringe um die Mitte verteilen (feste Plätze, damit nichts übereinander steht).
  const placed = useMemo(() => {
    const list = (objs || []).filter((o) => show[o.kind]);
    const out: (Obj & { x: number; y: number })[] = [];
    let ring = 0, idx = 0;
    while (idx < list.length) {
      const r = 115 + ring * 95;
      const slots = Math.max(5, Math.floor((2 * Math.PI * r) / 125)); // genug Abstand, damit sich nichts überdeckt
      for (let s = 0; s < slots && idx < list.length; s++, idx++) {
        const a = (s / slots) * Math.PI * 2 + ring * 0.5;
        out.push({ ...list[idx], x: Math.cos(a) * r, y: Math.sin(a) * r });
      }
      ring++;
      if (ring > 2) break; // mehr passt nicht auf die Insel
    }
    return out;
  }, [objs, show]);

  const counts = useMemo(() => {
    const c: Record<Kind, number> = { chat: 0, image: 0, app: 0 };
    for (const o of objs || []) c[o.kind]++;
    return c;
  }, [objs]);

  const onDown = (e: React.PointerEvent) => { drag.current = { x: e.clientX, moved: 0 }; };
  const onMove = (e: React.PointerEvent) => {
    if (!drag.current) return;
    const dx = e.clientX - drag.current.x;
    drag.current.x = e.clientX; drag.current.moved += Math.abs(dx);
    setAngle((a) => a + dx * 0.4);
  };
  const onUp = () => { setTimeout(() => { drag.current = null; }, 0); };
  const click = (o: Obj) => { if (drag.current && drag.current.moved > 6) return; setPicked(o); };

  const openPicked = () => {
    if (!picked) return;
    if (picked.kind === "chat") nav(`/app/c/${picked.chatId}`);
    else if (picked.kind === "app" && picked.html) { setApp({ title: picked.title, html: picked.html }); setPicked(null); }
  };

  return (
    <LabsShell icon={Globe2} title="Welt-Modus" subtitle="Deine Chats, Bilder und Apps stehen als Objekte auf deiner eigenen Insel. Zum Drehen ziehen, zum Öffnen antippen." wide>
      <div className="flex flex-wrap gap-2">
        {(Object.keys(KIND_LABEL) as Kind[]).map((k) => {
          const Icon = KIND_ICON[k];
          return (
            <button key={k} onClick={() => setShow((s) => ({ ...s, [k]: !s[k] }))}
              className={`flex items-center gap-1.5 h-10 rounded-full px-3.5 text-sm transition-colors ${show[k] ? "bg-white/15" : "bg-white/5 text-muted-foreground line-through"}`}>
              <Icon className="h-4 w-4" />{KIND_LABEL[k]} <span className="text-xs opacity-70">{counts[k]}</span>
            </button>
          );
        })}
      </div>

      <div ref={sceneRef}
        className="relative isolate w-full h-[58dvh] min-h-[340px] max-h-[640px] overflow-hidden rounded-3xl bg-gradient-to-b from-sky-900/60 via-indigo-950/60 to-background select-none cursor-grab active:cursor-grabbing"
        style={{ touchAction: "pan-y" }}
        onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerLeave={onUp} onPointerCancel={onUp}>
        {objs === null && <div className="absolute inset-0 flex items-center justify-center z-[2000]"><Loader2 className="h-6 w-6 animate-spin" /></div>}
        {/* Wasser und Insel (Ellipse = schräg von oben gesehen) */}
        <div className="absolute left-1/2 top-[58%] -translate-x-1/2 -translate-y-1/2 rounded-[50%]"
          style={{ width: (WORLD + 120) * zoom, height: (WORLD + 120) * zoom * COS, background: "radial-gradient(closest-side, rgba(56,189,248,0.35) 80%, transparent)" }} />
        <div className="absolute left-1/2 top-[58%] -translate-x-1/2 -translate-y-1/2 rounded-[50%] shadow-[0_18px_0_#854d0e,0_0_60px_rgba(16,185,129,0.25)]"
          style={{ width: WORLD * zoom, height: WORLD * zoom * COS, background: "radial-gradient(closest-side, #4ade80 0%, #22c55e 50%, #15803d 92%, #ca8a04 93%)" }} />
        {DECOR.map((d, i) => (
          <div key={"d" + i} className="absolute pointer-events-none text-3xl" style={placeStyle(project(d.x, d.y, angle), zoom)}>{d.emoji}</div>
        ))}
        {placed.map((o, i) => (
          <div key={o.key} className="absolute" style={placeStyle(project(o.x, o.y, angle), zoom)}>
            <button onClick={() => click(o)} className="group flex flex-col items-center gap-1 focus:outline-none" style={{ animation: `world-bob ${3 + (i % 4) * 0.4}s ease-in-out infinite` }} aria-label={o.title}>
              {o.kind === "image" ? (
                <span className="block h-14 w-14 rounded-lg border-4 border-amber-200 bg-amber-100 shadow-xl overflow-hidden group-hover:scale-110 transition-transform">
                  <img src={o.url} alt="" loading="lazy" draggable={false} className="h-full w-full object-cover" />
                </span>
              ) : o.kind === "chat" ? (
                <span className="text-4xl drop-shadow-lg group-hover:scale-110 transition-transform">🏠</span>
              ) : (
                <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-fuchsia-500 to-violet-600 shadow-[0_0_24px_rgba(217,70,239,0.6)] group-hover:scale-110 transition-transform">
                  <AppWindow className="h-6 w-6 text-white" />
                </span>
              )}
              <span className="max-w-[88px] truncate rounded-full bg-black/65 px-2 py-0.5 text-[10px] text-white">{o.title}</span>
            </button>
          </div>
        ))}
        {/* Mitte: Mythos-Kristall */}
        <div className="absolute pointer-events-none text-5xl drop-shadow-[0_0_18px_rgba(217,70,239,0.8)]"
          style={{ left: "50%", top: "58%", transform: `translate(-50%, -100%) scale(${Math.max(0.7, zoom)})`, transformOrigin: "50% 100%", zIndex: 1000, animation: "world-bob 3s ease-in-out infinite" }}>🔮</div>
        {objs && objs.length === 0 && (
          <div className="absolute inset-x-4 bottom-4 z-[2000] rounded-xl bg-black/60 p-3 text-center text-sm">Deine Insel ist noch leer. Chatte mit Mythos, lass Bilder malen oder bau Apps, dann tauchen sie hier auf.</div>
        )}
      </div>

      {/* Steuerung unter der Welt, damit nichts über den Objekten liegt */}
      <div className="grid grid-cols-4 gap-2 max-w-sm mx-auto">
        <Button variant="outline" className="h-11" onClick={() => setAngle((a) => a - 45)} aria-label="Nach links drehen"><RotateCcw className="h-5 w-5" /></Button>
        <Button variant="outline" className="h-11" onClick={() => setAngle((a) => a + 45)} aria-label="Nach rechts drehen"><RotateCw className="h-5 w-5" /></Button>
        <Button variant="outline" className="h-11" onClick={() => setZoom((z) => Math.max(0.4, z - 0.15))} aria-label="Verkleinern"><ZoomOut className="h-5 w-5" /></Button>
        <Button variant="outline" className="h-11" onClick={() => setZoom((z) => Math.min(1.6, z + 0.15))} aria-label="Vergrößern"><ZoomIn className="h-5 w-5" /></Button>
      </div>

      <Dialog open={!!picked} onOpenChange={(o) => !o && setPicked(null)}>
        <DialogContent className="glass-strong w-[calc(100vw-2rem)] max-w-md rounded-2xl">
          <DialogHeader className="pr-6">
            <DialogTitle className="truncate">{picked?.title}</DialogTitle>
            <DialogDescription>{picked ? { chat: "Chat", image: "Bild", app: "App" }[picked.kind] : ""}</DialogDescription>
          </DialogHeader>
          {picked?.kind === "image" && picked.url && <img src={picked.url} alt={picked.title} className="w-full max-h-[60vh] object-contain rounded-xl" />}
          {picked?.kind !== "image" && (
            <Button className="h-11 w-full bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white" onClick={openPicked}>
              {picked?.kind === "chat" ? "Chat öffnen" : "App starten"}
            </Button>
          )}
        </DialogContent>
      </Dialog>
      <AppPreview app={app} onClose={() => setApp(null)} />
    </LabsShell>
  );
}
