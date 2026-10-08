import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Loader2, Store } from "lucide-react";
import { toast } from "sonner";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { marketPublish } from "@/lib/labs";

type Item = { type: "app" | "image"; title: string; html?: string; image_url?: string; prompt?: string };
type Props = { open: boolean; onOpenChange: (o: boolean) => void; item: Item; onDone?: () => void };

/** App oder Bild im Mythos-Marktplatz veröffentlichen. */
export default function PublishDialog({ open, onOpenChange, item, onDone }: Props) {
  const { profile, user } = useAuth();
  const nav = useNavigate();
  const [title, setTitle] = useState(item.title);
  const [desc, setDesc] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (open) { setTitle(item.title); setDesc(""); } }, [open, item.title]);

  const publish = async () => {
    setBusy(true);
    try {
      await marketPublish({
        ...item, title: title.trim() || item.title, description: desc.trim(),
        author: profile?.mc_username || profile?.display_name || user?.email?.split("@")[0],
      });
      toast.success("Im Marktplatz veröffentlicht 🎉", { action: { label: "Ansehen", onClick: () => nav("/marktplatz") } });
      onOpenChange(false);
      onDone?.();
    } catch (e) {
      toast.error((e as Error).message);
    } finally { setBusy(false); }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong w-[calc(100vw-2rem)] max-w-md rounded-2xl">
        <DialogHeader className="pr-6">
          <DialogTitle>Im Marktplatz teilen</DialogTitle>
          <DialogDescription>Andere können {item.type === "app" ? "deine App" : "dein Bild"} ansehen und mit einem Klick übernehmen.</DialogDescription>
        </DialogHeader>
        {item.type === "image" && item.image_url && <img src={item.image_url} alt="" className="w-full max-h-48 object-cover rounded-xl" />}
        <div className="space-y-3">
          <div><Label>Titel</Label><Input value={title} maxLength={80} onChange={(e) => setTitle(e.target.value)} className="mt-1.5 h-11" /></div>
          <div><Label>Beschreibung (optional)</Label><Textarea value={desc} maxLength={300} onChange={(e) => setDesc(e.target.value)} className="mt-1.5" placeholder="Was kann es?" /></div>
        </div>
        <Button onClick={publish} disabled={busy || !title.trim()} className="h-11 w-full bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white">
          {busy ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Store className="h-4 w-4 mr-2" />}Veröffentlichen
        </Button>
      </DialogContent>
    </Dialog>
  );
}
