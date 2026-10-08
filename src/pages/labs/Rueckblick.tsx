import { useEffect, useRef, useState } from "react";
import LabsShell from "@/components/labs/LabsShell";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { CalendarHeart, Loader2, Sparkles, Film, LayoutGrid, Pause, Play, ChevronLeft, ChevronRight, Share2 } from "lucide-react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { askAI, extractJson, makeImage } from "@/lib/labs";

type Panel = { caption: string; scene: string; url?: string };
type Recap = { title: string; date: string; panels: Panel[] };
const KEY = "mythos.labs.recap";

const SYSTEM = `Du machst aus dem Tag eines Nutzers einen kurzen, lustigen Comic mit genau 4 Bildern.
Antworte NUR mit JSON: {"title":"kurzer Titel","panels":[{"caption":"Sprechblase/Untertitel auf Deutsch, max. 90 Zeichen","scene":"englische Bildbeschreibung für einen Bildgenerator, ohne Text im Bild"}]}.
Halte dich an das, was wirklich passiert ist, und mach es freundlich und witzig.`;

/** Die KI macht aus dem Tag einen Comic oder ein kurzes Video (Diashow mit Animation). */
export default function Rueckblick() {
  const [notes, setNotes] = useState("");
  const [useChats, setUseChats] = useState(true);
  const [busy, setBusy] = useState("");
  const [recap, setRecap] = useState<Recap | null>(() => { try { return JSON.parse(localStorage.getItem(KEY) || "null"); } catch { return null; } });
  const [video, setVideo] = useState(false);

  const todayChats = async (): Promise<string> => {
    const start = new Date(); start.setHours(0, 0, 0, 0);
    const { data } = await supabase.from("messages").select("content").eq("role", "user")
      .gte("created_at", start.toISOString()).order("created_at", { ascending: true }).limit(60);
    return (data || []).map((m) => "- " + String(m.content).slice(0, 200)).join("\n");
  };

  const create = async () => {
    setBusy("Die KI liest deinen Tag …");
    try {
      const chats = useChats ? await todayChats() : "";
      if (!notes.trim() && !chats) throw new Error("Schreib kurz, was heute passiert ist (oder chatte erst ein bisschen mit Mythos).");
      const plan = extractJson<{ title?: string; panels?: Panel[] }>(await askAI(SYSTEM, [{
        role: "user",
        content: `Datum: ${new Date().toLocaleDateString("de-DE", { weekday: "long", day: "numeric", month: "long" })}\n` +
          (notes.trim() ? `Was der Nutzer erzählt:\n${notes.trim()}\n` : "") + (chats ? `Worüber er heute mit der KI geschrieben hat:\n${chats}` : ""),
      }]));
      const panels = (plan?.panels || []).filter((p) => p?.caption && p?.scene).slice(0, 4);
      if (panels.length < 2) throw new Error("Daraus konnte kein Comic entstehen. Bitte nochmal versuchen.");
      setBusy("Die Bilder werden gemalt …");
      const urls = await Promise.all(panels.map((p) => makeImage(`colorful comic book panel, cartoon style, bold outlines, ${p.scene}, no text, no speech bubbles`)));
      const r: Recap = { title: plan?.title || "Mein Tag", date: new Date().toISOString(), panels: panels.map((p, i) => ({ ...p, url: urls[i] })) };
      setRecap(r);
      try { localStorage.setItem(KEY, JSON.stringify(r)); } catch { /* egal */ }
    } catch (e) { toast.error((e as Error).message); }
    finally { setBusy(""); }
  };

  const share = async () => {
    if (!recap) return;
    const text = `📅 ${recap.title}\n\n` + recap.panels.map((p, i) => `${i + 1}. ${p.caption}`).join("\n");
    try {
      if (navigator.share) await navigator.share({ title: recap.title, text });
      else { await navigator.clipboard.writeText(text); toast.success("Text kopiert"); }
    } catch { /* abgebrochen */ }
  };

  return (
    <LabsShell icon={CalendarHeart} title="Tagesrückblick" subtitle="Die KI macht aus deinem Tag automatisch einen kurzen Comic oder ein Video.">
      <section className="glass-strong rounded-2xl p-4 md:p-5 space-y-3">
        <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1500} className="min-h-[100px] text-base"
          placeholder="Was war heute los? (optional, z. B. „Schule, danach Minecraft mit Ben, Pizza zum Abendessen“)" />
        <label className="flex items-center gap-3 text-sm cursor-pointer select-none min-h-[44px]">
          <input type="checkbox" checked={useChats} onChange={(e) => setUseChats(e.target.checked)} className="h-5 w-5 accent-fuchsia-500" />
          Meine heutigen Chats mit Mythos einbeziehen
        </label>
        <Button onClick={create} disabled={!!busy} className="w-full h-11 bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white">
          {busy ? <><Loader2 className="h-4 w-4 mr-2 animate-spin" />{busy}</> : <><Sparkles className="h-4 w-4 mr-2" />Rückblick erstellen</>}
        </Button>
      </section>

      {recap && (
        <section className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="font-display text-lg flex-1 min-w-0 truncate">{recap.title}</h2>
            <span className="text-xs text-muted-foreground">{new Date(recap.date).toLocaleDateString("de-DE")}</span>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:gap-3">
            {recap.panels.map((p, i) => (
              <figure key={i} className="glass rounded-xl overflow-hidden">
                <img src={p.url} alt={p.caption} loading="lazy" className="w-full aspect-square object-cover bg-white/5" />
                <figcaption className="p-2 text-[11px] sm:text-sm leading-snug">{p.caption}</figcaption>
              </figure>
            ))}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <Button className="h-11" onClick={() => setVideo(true)}><Film className="h-4 w-4 mr-2" />Als Video abspielen</Button>
            <Button className="h-11" variant="outline" onClick={share}><Share2 className="h-4 w-4 mr-2" />Teilen</Button>
          </div>
        </section>
      )}
      {recap && <RecapVideo recap={recap} open={video} onClose={() => setVideo(false)} />}
    </LabsShell>
  );
}

/** Diashow mit Ken-Burns-Zoom und Untertiteln, wie ein kurzes Video. */
function RecapVideo({ recap, open, onClose }: { recap: Recap; open: boolean; onClose: () => void }) {
  const [i, setI] = useState(0);
  const [playing, setPlaying] = useState(true);
  const timer = useRef<number>();
  useEffect(() => { if (open) { setI(0); setPlaying(true); } }, [open]);
  useEffect(() => {
    if (!open || !playing) return;
    timer.current = window.setTimeout(() => {
      if (i < recap.panels.length - 1) setI(i + 1); else setPlaying(false);
    }, 3500);
    return () => window.clearTimeout(timer.current);
  }, [open, playing, i, recap.panels.length]);
  const p = recap.panels[i];
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="p-0 gap-0 overflow-hidden bg-black border-0 w-screen max-w-none h-[100dvh] rounded-none sm:w-[90vw] sm:max-w-xl sm:h-auto sm:aspect-[9/14] sm:rounded-2xl flex flex-col">
        <DialogTitle className="sr-only">{recap.title}</DialogTitle>
        <DialogDescription className="sr-only">Tagesrückblick als Video</DialogDescription>
        <div className="flex gap-1 p-3 pr-12 pt-[max(env(safe-area-inset-top),0.75rem)]">
          {recap.panels.map((_, k) => (
            <span key={k} className="h-1 flex-1 rounded-full bg-white/25 overflow-hidden">
              <span key={`${i}-${playing}`} className={`block h-full bg-white ${k < i ? "w-full" : k === i && playing ? "animate-labs-progress" : k === i ? "w-full" : "w-0"}`} />
            </span>
          ))}
        </div>
        <div className="relative flex-1 min-h-0 overflow-hidden">
          <img key={i} src={p?.url} alt="" className="absolute inset-0 h-full w-full object-cover animate-kenburns" />
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 to-transparent p-5 pb-6">
            <p key={i} className="text-white text-lg sm:text-xl font-display leading-snug animate-fade-in">{p?.caption}</p>
          </div>
        </div>
        <div className="flex items-center justify-center gap-3 p-3 pb-[max(env(safe-area-inset-bottom),0.75rem)]">
          <Button size="icon" variant="ghost" className="h-11 w-11 text-white" disabled={i === 0} onClick={() => setI(i - 1)} aria-label="Zurück"><ChevronLeft className="h-6 w-6" /></Button>
          <Button size="icon" variant="ghost" className="h-11 w-11 text-white" aria-label={playing ? "Pause" : "Abspielen"}
            onClick={() => { if (!playing && i === recap.panels.length - 1) setI(0); setPlaying(!playing); }}>
            {playing ? <Pause className="h-6 w-6" /> : <Play className="h-6 w-6" />}
          </Button>
          <Button size="icon" variant="ghost" className="h-11 w-11 text-white" disabled={i === recap.panels.length - 1} onClick={() => setI(i + 1)} aria-label="Weiter"><ChevronRight className="h-6 w-6" /></Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
