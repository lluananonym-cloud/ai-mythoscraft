import { useRef, useState } from "react";
import LabsShell from "@/components/labs/LabsShell";
import AppPreview from "@/components/labs/AppPreview";
import MyApps from "@/components/labs/MyApps";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Camera, ImagePlus, Loader2, Sparkles, X } from "lucide-react";
import { toast } from "sonner";
import { APP_RULES, askAI, extractHtml, saveMyApp, shrinkImage, type MyApp } from "@/lib/labs";

const SYSTEM = `Du bist ein Profi-Webentwickler. Der Nutzer schickt ein Foto einer Skizze (Papier, Whiteboard, Screenshot).
Baue daraus eine echte, klickbare Web-App: übernimm Aufbau, Texte und Buttons aus der Skizze und gib allem sinnvolle Funktionen.
${APP_RULES}`;

/** Skizze fotografieren → fertige, klickbare App. */
export default function FotoApp() {
  const camRef = useRef<HTMLInputElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [photo, setPhoto] = useState<string | null>(null);
  const [hint, setHint] = useState("");
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<MyApp | null>(null);
  const [refresh, setRefresh] = useState(0);

  const pick = async (f?: File | null) => {
    if (!f) return;
    try { setPhoto(await shrinkImage(f)); } catch (e) { toast.error((e as Error).message); }
  };

  const build = async () => {
    if (!photo) return;
    setBusy(true);
    try {
      const text = await askAI(SYSTEM, [{
        role: "user",
        content: [
          { type: "text", text: "Baue diese Skizze als App." + (hint.trim() ? " Zusatz: " + hint.trim() : "") },
          { type: "image_url", image_url: { url: photo } },
        ],
      }], "google/gemini-3.1-pro-preview");
      const html = extractHtml(text);
      if (!html) throw new Error("Die KI hat keine App geliefert. Versuch es mit einem deutlicheren Foto.");
      const title = html.match(/<title>([^<]{1,60})<\/title>/i)?.[1]?.trim() || "Foto-App";
      const app = saveMyApp({ title, html, source: "foto" });
      setPreview(app); setRefresh((n) => n + 1);
      toast.success("Deine App ist fertig ✨");
    } catch (e) { toast.error((e as Error).message); }
    finally { setBusy(false); }
  };

  return (
    <LabsShell icon={Camera} title="Foto zu App" subtitle="Fotografier eine Skizze auf Papier und bekomm daraus eine fertige, klickbare App.">
      <section className="glass-strong rounded-2xl p-4 md:p-5 space-y-4">
        <input ref={camRef} type="file" accept="image/*" capture="environment" className="hidden" onChange={(e) => { pick(e.target.files?.[0]); e.target.value = ""; }} />
        <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={(e) => { pick(e.target.files?.[0]); e.target.value = ""; }} />
        {photo ? (
          <div className="relative">
            <img src={photo} alt="Deine Skizze" className="w-full max-h-[50vh] object-contain rounded-xl bg-black/30" />
            <button onClick={() => setPhoto(null)} aria-label="Foto entfernen"
              className="absolute top-2 right-2 flex h-10 w-10 items-center justify-center rounded-full bg-black/60 hover:bg-black/80">
              <X className="h-5 w-5" />
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <button onClick={() => camRef.current?.click()} className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-white/20 hover:bg-white/5 p-6 min-h-[130px]">
              <Camera className="h-8 w-8 text-fuchsia-300" /><span className="font-medium">Foto aufnehmen</span>
              <span className="text-xs text-muted-foreground">Kamera öffnen</span>
            </button>
            <button onClick={() => fileRef.current?.click()} className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-white/20 hover:bg-white/5 p-6 min-h-[130px]">
              <ImagePlus className="h-8 w-8 text-fuchsia-300" /><span className="font-medium">Bild auswählen</span>
              <span className="text-xs text-muted-foreground">Aus Galerie oder Ordner</span>
            </button>
          </div>
        )}
        <Input value={hint} onChange={(e) => setHint(e.target.value)} maxLength={300} className="h-11"
          placeholder="Optional: Was soll die App können?" />
        <Button onClick={build} disabled={busy || !photo} className="w-full h-11 bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white">
          {busy ? <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Die KI baut deine App …</> : <><Sparkles className="h-4 w-4 mr-2" />App bauen</>}
        </Button>
      </section>
      <MyApps source="foto" refresh={refresh} onOpen={setPreview} />
      <AppPreview app={preview} onClose={() => setPreview(null)} />
    </LabsShell>
  );
}
