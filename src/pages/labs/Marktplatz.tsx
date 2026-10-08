import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import LabsShell from "@/components/labs/LabsShell";
import AppPreview from "@/components/labs/AppPreview";
import PublishDialog from "@/components/labs/PublishDialog";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Store, Heart, Download, Play, Loader2, Plus, Trash2, AppWindow, Image as ImageIcon, Hammer } from "lucide-react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { listMyApps, marketDelete, marketGet, marketLike, marketList, marketTake, saveMyApp, type MarketEntry } from "@/lib/labs";

type Filter = "all" | "app" | "image";
type Share = { type: "app" | "image"; title: string; html?: string; image_url?: string };

/** Nutzer teilen gebaute Apps und Bilder; andere übernehmen sie mit einem Klick und bauen weiter. */
export default function Marktplatz() {
  const nav = useNavigate();
  const [items, setItems] = useState<MarketEntry[] | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [sort, setSort] = useState<"new" | "top">("new");
  const [busyId, setBusyId] = useState("");
  const [preview, setPreview] = useState<{ title: string; html: string } | null>(null);
  const [image, setImage] = useState<MarketEntry | null>(null);
  const [picker, setPicker] = useState(false);
  const [share, setShare] = useState<Share | null>(null);

  const load = () => marketList().then(setItems).catch((e) => { setItems([]); toast.error((e as Error).message); });
  useEffect(() => { load(); }, []);

  const shown = useMemo(() => {
    const list = (items || []).filter((i) => filter === "all" || i.type === filter);
    return sort === "top" ? [...list].sort((a, b) => b.likes + b.copies - (a.likes + a.copies)) : list;
  }, [items, filter, sort]);

  const patch = (id: string, p: Partial<MarketEntry>) => setItems((x) => (x || []).map((i) => (i.id === id ? { ...i, ...p } : i)));

  const open = async (e: MarketEntry) => {
    if (e.type === "image") { setImage(e); return; }
    setBusyId(e.id);
    try {
      const item = await marketGet(e.id);
      if (!item?.html) throw new Error("App nicht gefunden");
      setPreview({ title: item.title, html: item.html });
    } catch (err) { toast.error((err as Error).message); }
    finally { setBusyId(""); }
  };

  const take = async (e: MarketEntry) => {
    setBusyId(e.id);
    try {
      const item = await marketTake(e.id);
      patch(e.id, { copies: e.copies + 1 });
      if (e.type === "image") {
        const a = document.createElement("a");
        a.href = e.image_url!; a.download = e.title + ".png"; a.target = "_blank"; a.rel = "noopener"; a.click();
        toast.success("Bild übernommen");
      } else {
        if (!item?.html) throw new Error("App nicht gefunden");
        const mine = saveMyApp({ title: item.title, html: item.html, source: "markt" });
        toast.success("Übernommen! Jetzt kannst du sie mit dem KI-Team weiterbauen.");
        nav(`/ki-team?app=${mine.id}`);
      }
    } catch (err) { toast.error((err as Error).message); }
    finally { setBusyId(""); }
  };

  const like = async (e: MarketEntry) => {
    patch(e.id, { liked: !e.liked, likes: e.likes + (e.liked ? -1 : 1) });
    try { const r = await marketLike(e.id); patch(e.id, { liked: r.liked, likes: r.likes }); }
    catch (err) { patch(e.id, { liked: e.liked, likes: e.likes }); toast.error((err as Error).message); }
  };

  const remove = async (e: MarketEntry) => {
    if (!confirm(`„${e.title}“ aus dem Marktplatz entfernen?`)) return;
    try { await marketDelete(e.id); setItems((x) => (x || []).filter((i) => i.id !== e.id)); }
    catch (err) { toast.error((err as Error).message); }
  };

  return (
    <LabsShell icon={Store} title="Mythos-Marktplatz" subtitle="Entdecke Apps und Bilder anderer Nutzer, übernimm sie mit einem Klick und bau weiter." wide>
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex rounded-xl bg-white/5 p-1">
          {([["all", "Alles"], ["app", "Apps"], ["image", "Bilder"]] as const).map(([k, l]) => (
            <button key={k} onClick={() => setFilter(k)} className={`h-9 px-3 rounded-lg text-sm ${filter === k ? "bg-white/15" : "text-muted-foreground"}`}>{l}</button>
          ))}
        </div>
        <div className="flex rounded-xl bg-white/5 p-1">
          {([["new", "Neu"], ["top", "Beliebt"]] as const).map(([k, l]) => (
            <button key={k} onClick={() => setSort(k)} className={`h-9 px-3 rounded-lg text-sm ${sort === k ? "bg-white/15" : "text-muted-foreground"}`}>{l}</button>
          ))}
        </div>
        <Button onClick={() => setPicker(true)} className="h-11 w-full sm:w-auto sm:ml-auto bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white">
          <Plus className="h-4 w-4 mr-2" />Etwas teilen
        </Button>
      </div>

      {items === null ? (
        <div className="flex justify-center py-12"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : shown.length === 0 ? (
        <div className="glass rounded-2xl p-8 text-center text-sm text-muted-foreground">Hier ist noch nichts. Teil als Erstes eine App oder ein Bild!</div>
      ) : (
        <div className="grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
          {shown.map((e) => (
            <article key={e.id} className="glass rounded-2xl overflow-hidden flex flex-col min-w-0">
              <button onClick={() => open(e)} className="relative block aspect-video w-full overflow-hidden bg-gradient-to-br from-fuchsia-500/20 to-violet-600/20" aria-label={`${e.title} ansehen`}>
                {e.type === "image" && e.image_url
                  ? <img src={e.image_url} alt={e.title} loading="lazy" className="h-full w-full object-cover" />
                  : <span className="flex h-full items-center justify-center"><AppWindow className="h-12 w-12 text-fuchsia-200/70" /></span>}
                <span className="absolute left-2 top-2 rounded-full bg-black/60 px-2 py-0.5 text-[11px] flex items-center gap-1">
                  {e.type === "app" ? <><AppWindow className="h-3 w-3" />App</> : <><ImageIcon className="h-3 w-3" />Bild</>}
                </span>
              </button>
              <div className="p-3 flex flex-col gap-2 flex-1">
                <div className="min-w-0">
                  <h3 className="font-medium truncate">{e.title}</h3>
                  <p className="text-[11px] text-muted-foreground truncate">von {e.author} · {e.copies}× übernommen</p>
                  {e.description && <p className="text-xs text-muted-foreground line-clamp-2 mt-1">{e.description}</p>}
                </div>
                <div className="flex items-center gap-1.5 mt-auto">
                  <Button size="sm" className="h-10 flex-1 min-w-0" onClick={() => take(e)} disabled={busyId === e.id}>
                    {busyId === e.id ? <Loader2 className="h-4 w-4 animate-spin" /> : e.type === "app" ? <><Hammer className="h-4 w-4 mr-1.5 shrink-0" /><span className="truncate">Übernehmen</span></> : <><Download className="h-4 w-4 mr-1.5 shrink-0" /><span className="truncate">Übernehmen</span></>}
                  </Button>
                  {e.type === "app" && (
                    <Button size="icon" variant="outline" className="h-10 w-10 shrink-0" onClick={() => open(e)} aria-label="Ausprobieren"><Play className="h-4 w-4" /></Button>
                  )}
                  <Button size="sm" variant="ghost" className={`h-10 shrink-0 px-2.5 ${e.liked ? "text-pink-400" : ""}`} onClick={() => like(e)} aria-label="Gefällt mir">
                    <Heart className={`h-4 w-4 mr-1 ${e.liked ? "fill-current" : ""}`} />{e.likes}
                  </Button>
                  {e.mine && (
                    <Button size="icon" variant="ghost" className="h-10 w-10 shrink-0 hover:text-destructive" onClick={() => remove(e)} aria-label="Entfernen"><Trash2 className="h-4 w-4" /></Button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      <AppPreview app={preview} onClose={() => setPreview(null)} canPublish={false} />

      <Dialog open={!!image} onOpenChange={(o) => !o && setImage(null)}>
        <DialogContent className="glass-strong w-[calc(100vw-2rem)] max-w-2xl rounded-2xl">
          <DialogHeader className="pr-6"><DialogTitle className="truncate">{image?.title}</DialogTitle><DialogDescription>von {image?.author}</DialogDescription></DialogHeader>
          {image?.image_url && <img src={image.image_url} alt={image.title} className="w-full max-h-[65vh] object-contain rounded-xl" />}
        </DialogContent>
      </Dialog>

      <SharePicker open={picker} onOpenChange={setPicker} onPick={(s) => { setPicker(false); setShare(s); }} />
      {share && <PublishDialog open={!!share} onOpenChange={(o) => !o && setShare(null)} item={share} onDone={load} />}
    </LabsShell>
  );
}

/** Auswahl, was geteilt werden soll: eigene Apps oder Bilder aus den eigenen Chats. */
function SharePicker({ open, onOpenChange, onPick }: { open: boolean; onOpenChange: (o: boolean) => void; onPick: (s: Share) => void }) {
  const [images, setImages] = useState<{ url: string; prompt: string }[] | null>(null);
  const apps = open ? listMyApps() : [];
  useEffect(() => {
    if (!open) return;
    setImages(null);
    supabase.from("messages").select("metadata").eq("role", "assistant").not("metadata->image", "is", null)
      .order("created_at", { ascending: false }).limit(30)
      .then(({ data }) => setImages((data || []).map((m) => (m.metadata as { image?: { url?: string; prompt?: string } } | null)?.image)
        .filter((i): i is { url: string; prompt: string } => typeof i?.url === "string" && i.url.startsWith("https://"))));
  }, [open]);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong w-[calc(100vw-2rem)] max-w-xl max-h-[85dvh] rounded-2xl flex flex-col">
        <DialogHeader className="pr-6"><DialogTitle>Was möchtest du teilen?</DialogTitle><DialogDescription>Deine Apps aus Foto zu App und KI-Team oder Bilder aus deinen Chats.</DialogDescription></DialogHeader>
        <div className="overflow-y-auto space-y-4 -mx-1 px-1">
          <div>
            <h3 className="text-sm font-medium mb-2">Apps</h3>
            {apps.length === 0 ? <p className="text-xs text-muted-foreground">Noch keine Apps. Bau eine mit „Foto zu App“ oder dem KI-Team.</p> : (
              <div className="space-y-1.5">
                {apps.map((a) => (
                  <button key={a.id} onClick={() => onPick({ type: "app", title: a.title, html: a.html })}
                    className="w-full flex items-center gap-2 rounded-xl bg-white/5 hover:bg-white/10 p-3 text-left min-h-[44px]">
                    <AppWindow className="h-4 w-4 shrink-0 text-fuchsia-300" /><span className="truncate">{a.title}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
          <div>
            <h3 className="text-sm font-medium mb-2">Bilder</h3>
            {images === null ? <Loader2 className="h-4 w-4 animate-spin" /> : images.length === 0 ? <p className="text-xs text-muted-foreground">Noch keine Bilder. Lass dir im Chat eins malen.</p> : (
              <div className="grid grid-cols-3 gap-2">
                {images.map((img, i) => (
                  <button key={i} onClick={() => onPick({ type: "image", title: img.prompt?.slice(0, 60) || "Mein Bild", image_url: img.url })}
                    className="aspect-square overflow-hidden rounded-xl bg-white/5" aria-label="Dieses Bild teilen">
                    <img src={img.url} alt="" loading="lazy" className="h-full w-full object-cover" />
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
