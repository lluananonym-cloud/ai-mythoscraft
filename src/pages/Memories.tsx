import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Brain, Plus, Trash2, Sparkles } from "lucide-react";
import { toast } from "sonner";

type Memory = {
  id: string; content: string; category: string; source: string; created_at: string;
};

const CATEGORY_COLORS: Record<string, string> = {
  personal: "bg-blue-500/15 text-blue-300",
  preference: "bg-purple-500/15 text-purple-300",
  hobby: "bg-green-500/15 text-green-300",
  minecraft: "bg-emerald-500/15 text-emerald-300",
  work: "bg-orange-500/15 text-orange-300",
  general: "bg-secondary text-muted-foreground",
  other: "bg-secondary text-muted-foreground",
};

const Memories = () => {
  const { user } = useAuth();
  const [items, setItems] = useState<Memory[]>([]);
  const [content, setContent] = useState("");

  const load = async () => {
    const { data } = await supabase.from("user_memories").select("*").order("created_at", { ascending: false });
    if (data) setItems(data as any);
  };
  useEffect(() => { if (user) load(); }, [user]);

  const add = async () => {
    if (!content.trim() || !user) return;
    const { error } = await supabase.from("user_memories").insert({
      user_id: user.id, content: content.trim().slice(0, 280), category: "personal", source: "manual",
    });
    if (error) { toast.error("Speichern fehlgeschlagen"); return; }
    setContent("");
    toast.success("Memory gespeichert");
    load();
  };

  const remove = async (id: string) => {
    await supabase.from("user_memories").delete().eq("id", id);
    setItems(prev => prev.filter(m => m.id !== id));
  };

  const removeAll = async () => {
    if (!confirm("Wirklich ALLE Memories löschen? Die AI vergisst dann alles über dich.")) return;
    if (!user) return;
    await supabase.from("user_memories").delete().eq("user_id", user.id);
    setItems([]);
    toast.success("Alle Memories gelöscht");
  };

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-8 max-w-3xl">
        <div className="flex items-center gap-3 mb-2">
          <div className="h-10 w-10 rounded-xl bg-gradient-primary flex items-center justify-center">
            <Brain className="h-5 w-5 text-primary-foreground" />
          </div>
          <h1 className="font-display text-2xl md:text-3xl font-bold">Memories</h1>
        </div>
        <p className="text-muted-foreground text-sm mb-6">
          Was die AI sich über dich merkt. Sie zieht das automatisch aus deinen Nachrichten — du kannst hier alles sehen, ergänzen oder löschen.
        </p>

        <div className="glass-strong rounded-2xl p-4 mb-6 space-y-3">
          <Label className="flex items-center gap-1.5"><Plus className="h-3.5 w-3.5" /> Manuell hinzufügen</Label>
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="z.B. „Mein Lieblingsplugin ist EssentialsX“"
            className="bg-background/40 min-h-[60px]"
            maxLength={280}
          />
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs text-muted-foreground">{content.length}/280</span>
            <Button onClick={add} disabled={!content.trim()} className="bg-gradient-primary text-primary-foreground">
              <Sparkles className="h-3.5 w-3.5 mr-1.5" /> Merken
            </Button>
          </div>
        </div>

        <div className="flex items-center justify-between mb-3">
          <span className="text-sm text-muted-foreground">{items.length} Memories</span>
          {items.length > 0 && (
            <Button variant="ghost" size="sm" onClick={removeAll} className="text-destructive hover:text-destructive">
              <Trash2 className="h-3.5 w-3.5 mr-1.5" /> Alle löschen
            </Button>
          )}
        </div>

        {items.length === 0 ? (
          <div className="glass rounded-2xl p-10 text-center">
            <Brain className="h-10 w-10 mx-auto mb-3 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">Noch nichts gespeichert. Schreib einfach im Chat — die AI merkt sich automatisch wichtige Sachen.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {items.map(m => (
              <div key={m.id} className="glass rounded-xl p-3 flex items-start gap-3 group">
                <span className={`text-[10px] uppercase tracking-wider px-2 py-1 rounded shrink-0 ${CATEGORY_COLORS[m.category] || CATEGORY_COLORS.other}`}>
                  {m.category}
                </span>
                <p className="text-sm flex-1 break-words">{m.content}</p>
                <span className="text-[10px] text-muted-foreground shrink-0">
                  {m.source === "auto" ? "🤖" : "✋"}
                </span>
                <button
                  onClick={() => remove(m.id)}
                  className="opacity-0 group-hover:opacity-100 transition-opacity text-muted-foreground hover:text-destructive"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
};
export default Memories;
