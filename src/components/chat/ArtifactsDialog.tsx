import { copyText } from "@/lib/copyText";
import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Code2, Copy, Download, ExternalLink, Image as ImageIcon, Loader2 } from "lucide-react";
import { toast } from "sonner";

type Props = { open: boolean; onOpenChange: (o: boolean) => void; onOpenChat: (id: string) => void };
type Artifact =
  | { kind: "code"; key: string; convId: string; lang: string; code: string; at: string }
  | { kind: "image"; key: string; convId: string; url: string; prompt: string; at: string };

const EXT: Record<string, string> = { javascript: "js", typescript: "ts", python: "py", html: "html", css: "css", json: "json", bash: "sh", shell: "sh", tsx: "tsx", jsx: "jsx", java: "java", markdown: "md", sql: "sql" };

/** Alles, was Mythos in deinen Chats erstellt hat: Code-Blöcke und Bilder. */
export default function ArtifactsDialog({ open, onOpenChange, onOpenChat }: Props) {
  const [items, setItems] = useState<Artifact[] | null>(null);
  const [filter, setFilter] = useState<"all" | "code" | "image">("all");

  useEffect(() => {
    if (!open) return;
    setItems(null);
    supabase.from("messages")
      .select("id,conversation_id,content,metadata,created_at")
      .eq("role", "assistant")
      .or("content.ilike.%```%,metadata->image.not.is.null")
      .order("created_at", { ascending: false })
      .limit(200)
      .then(({ data }) => {
        const out: Artifact[] = [];
        for (const m of (data || []) as any[]) {
          const img = m.metadata?.image;
          if (img?.url) out.push({ kind: "image", key: m.id + "i", convId: m.conversation_id, url: img.url, prompt: img.prompt || "Bild", at: m.created_at });
          const re = /```([\w+-]*)\n([\s\S]*?)```/g;
          let x: RegExpExecArray | null; let n = 0;
          while ((x = re.exec(m.content || ""))) {
            const code = x[2].trimEnd();
            if (code.split("\n").length < 3) continue; // kleine Schnipsel weglassen
            out.push({ kind: "code", key: `${m.id}c${n++}`, convId: m.conversation_id, lang: x[1] || "text", code, at: m.created_at });
          }
        }
        setItems(out);
      });
  }, [open]);

  const shown = (items || []).filter(i => filter === "all" || i.kind === filter);

  const download = (a: Extract<Artifact, { kind: "code" }>) => {
    const blob = new Blob([a.code], { type: "text/plain;charset=utf-8" });
    const el = document.createElement("a");
    el.href = URL.createObjectURL(blob); el.download = `artifact.${EXT[a.lang.toLowerCase()] || "txt"}`; el.click();
    setTimeout(() => URL.revokeObjectURL(el.href), 5000);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong max-w-3xl max-h-[85vh] flex flex-col">
        <DialogHeader><DialogTitle>Artifacts</DialogTitle></DialogHeader>
        <div className="flex gap-1.5">
          {(["all", "code", "image"] as const).map(f => (
            <Button key={f} size="sm" variant={filter === f ? "secondary" : "ghost"} className="h-7 text-xs" onClick={() => setFilter(f)}>
              {f === "all" ? "Alle" : f === "code" ? "Code" : "Bilder"}
            </Button>
          ))}
        </div>
        <div className="flex-1 overflow-y-auto min-h-0 grid sm:grid-cols-2 gap-2 pr-1">
          {items === null && <div className="col-span-full flex justify-center p-8"><Loader2 className="h-5 w-5 animate-spin" /></div>}
          {items && shown.length === 0 && (
            <div className="col-span-full text-center text-sm text-muted-foreground p-8">Noch nichts erstellt. Code und Bilder aus deinen Chats landen automatisch hier.</div>
          )}
          {shown.map(a => (
            <div key={a.key} className="rounded-xl border border-white/10 bg-white/[0.02] overflow-hidden flex flex-col">
              {a.kind === "image" ? (
                <img src={a.url} alt={a.prompt} className="h-40 w-full object-cover" loading="lazy" />
              ) : (
                <pre className="h-40 overflow-hidden text-[11px] leading-snug p-3 font-mono text-foreground/80">{a.code.slice(0, 1200)}</pre>
              )}
              <div className="flex items-center gap-1 border-t border-white/5 px-2 py-1.5">
                {a.kind === "image" ? <ImageIcon className="h-3.5 w-3.5 text-violet-300" /> : <Code2 className="h-3.5 w-3.5 text-violet-300" />}
                <span className="text-xs truncate flex-1">{a.kind === "image" ? a.prompt : a.lang}</span>
                {a.kind === "code" && (
                  <>
                    <Button variant="ghost" size="icon" className="h-7 w-7" title="Kopieren"
                      onClick={() => copyText(a.code).then((ok) => ok ? toast.success("Kopiert") : toast.error("Kopieren fehlgeschlagen"))}><Copy className="h-3.5 w-3.5" /></Button>
                    <Button variant="ghost" size="icon" className="h-7 w-7" title="Herunterladen" onClick={() => download(a)}><Download className="h-3.5 w-3.5" /></Button>
                  </>
                )}
                {a.kind === "image" && (
                  <Button variant="ghost" size="icon" className="h-7 w-7" title="Öffnen" onClick={() => window.open(a.url, "_blank", "noopener")}><Download className="h-3.5 w-3.5" /></Button>
                )}
                <Button variant="ghost" size="icon" className="h-7 w-7" title="Zum Chat" onClick={() => { onOpenChange(false); onOpenChat(a.convId); }}>
                  <ExternalLink className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
