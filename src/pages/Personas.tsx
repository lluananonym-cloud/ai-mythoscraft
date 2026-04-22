import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogDescription,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Plus, Trash2, Edit, Globe, User as UserIcon, Drama } from "lucide-react";
import { toast } from "sonner";
import { Link } from "react-router-dom";

type Persona = {
  id: string; user_id: string; name: string; description: string | null;
  system_prompt: string; avatar_emoji: string | null; is_public: boolean; use_count: number; created_at: string;
};

const EMPTY = { name: "", description: "", system_prompt: "", avatar_emoji: "🤖", is_public: false };

const Personas = () => {
  const { user } = useAuth();
  const [mine, setMine] = useState<Persona[]>([]);
  const [pub, setPub] = useState<Persona[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Persona | null>(null);
  const [form, setForm] = useState({ ...EMPTY });

  const load = async () => {
    if (!user) return;
    const [my, p] = await Promise.all([
      supabase.from("ai_personas").select("*").eq("user_id", user.id).order("created_at", { ascending: false }),
      supabase.from("ai_personas").select("*").eq("is_public", true).neq("user_id", user.id).order("use_count", { ascending: false }).limit(40),
    ]);
    if (my.data) setMine(my.data as any);
    if (p.data) setPub(p.data as any);
  };
  useEffect(() => { load(); }, [user]);

  const openNew = () => { setEditing(null); setForm({ ...EMPTY }); setOpen(true); };
  const openEdit = (p: Persona) => {
    setEditing(p);
    setForm({
      name: p.name, description: p.description || "", system_prompt: p.system_prompt,
      avatar_emoji: p.avatar_emoji || "🤖", is_public: p.is_public,
    });
    setOpen(true);
  };

  const save = async () => {
    if (!user || !form.name.trim() || !form.system_prompt.trim()) {
      toast.error("Name und System-Prompt erforderlich");
      return;
    }
    const payload = {
      name: form.name.trim().slice(0, 60),
      description: form.description.trim().slice(0, 200) || null,
      system_prompt: form.system_prompt.trim().slice(0, 4000),
      avatar_emoji: form.avatar_emoji.trim().slice(0, 8) || "🤖",
      is_public: form.is_public,
    };
    if (editing) {
      const { error } = await supabase.from("ai_personas").update(payload).eq("id", editing.id);
      if (error) { toast.error("Speichern fehlgeschlagen"); return; }
      toast.success("Persona aktualisiert");
    } else {
      const { error } = await supabase.from("ai_personas").insert({ ...payload, user_id: user.id });
      if (error) { toast.error("Erstellen fehlgeschlagen"); return; }
      toast.success("Persona erstellt");
    }
    setOpen(false);
    load();
  };

  const remove = async (id: string) => {
    if (!confirm("Persona wirklich löschen?")) return;
    await supabase.from("ai_personas").delete().eq("id", id);
    load();
  };

  const PersonaCard = ({ p, mine }: { p: Persona; mine: boolean }) => (
    <div className="glass rounded-2xl p-4 flex flex-col gap-2">
      <div className="flex items-start gap-3">
        <div className="text-3xl shrink-0">{p.avatar_emoji || "🤖"}</div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-display font-semibold truncate">{p.name}</span>
            {p.is_public && <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent/20 text-accent flex items-center gap-1"><Globe className="h-2.5 w-2.5" />public</span>}
          </div>
          {p.description && <p className="text-xs text-muted-foreground line-clamp-2 mt-0.5">{p.description}</p>}
        </div>
      </div>
      <p className="text-xs text-muted-foreground/80 line-clamp-2 italic">{p.system_prompt}</p>
      <div className="flex items-center gap-2 mt-1">
        <Link to={`/app?persona=${p.id}`} className="flex-1">
          <Button size="sm" variant="outline" className="w-full">Im Chat nutzen</Button>
        </Link>
        {mine && (
          <>
            <Button size="icon" variant="ghost" onClick={() => openEdit(p)}><Edit className="h-3.5 w-3.5" /></Button>
            <Button size="icon" variant="ghost" onClick={() => remove(p.id)} className="text-muted-foreground hover:text-destructive"><Trash2 className="h-3.5 w-3.5" /></Button>
          </>
        )}
      </div>
    </div>
  );

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-8 max-w-5xl">
        <div className="flex items-center justify-between mb-2 gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-primary flex items-center justify-center">
              <Drama className="h-5 w-5 text-primary-foreground" />
            </div>
            <h1 className="font-display text-2xl md:text-3xl font-bold">Personas</h1>
          </div>
          <Button onClick={openNew} className="bg-gradient-primary text-primary-foreground">
            <Plus className="h-4 w-4 mr-1.5" />Neue Persona
          </Button>
        </div>
        <p className="text-muted-foreground text-sm mb-6">
          Erstelle eigene AI-Charaktere — Deutsch-Lehrer, Roaster, Minecraft-Pro... Alles möglich.
        </p>

        <Tabs defaultValue="mine">
          <TabsList className="bg-secondary/50">
            <TabsTrigger value="mine"><UserIcon className="h-3.5 w-3.5 mr-1.5" />Meine ({mine.length})</TabsTrigger>
            <TabsTrigger value="public"><Globe className="h-3.5 w-3.5 mr-1.5" />Public ({pub.length})</TabsTrigger>
          </TabsList>
          <TabsContent value="mine" className="mt-5">
            {mine.length === 0 ? (
              <div className="glass rounded-2xl p-10 text-center">
                <Drama className="h-10 w-10 mx-auto mb-3 text-muted-foreground/50" />
                <p className="text-sm text-muted-foreground mb-4">Noch keine eigenen Personas.</p>
                <Button onClick={openNew} className="bg-gradient-primary text-primary-foreground">
                  <Plus className="h-4 w-4 mr-1.5" />Erste Persona erstellen
                </Button>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {mine.map(p => <PersonaCard key={p.id} p={p} mine={true} />)}
              </div>
            )}
          </TabsContent>
          <TabsContent value="public" className="mt-5">
            {pub.length === 0 ? (
              <div className="glass rounded-2xl p-10 text-center text-sm text-muted-foreground">Noch keine öffentlichen Personas.</div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {pub.map(p => <PersonaCard key={p.id} p={p} mine={false} />)}
              </div>
            )}
          </TabsContent>
        </Tabs>

        <Dialog open={open} onOpenChange={setOpen}>
          <DialogContent className="glass-strong max-w-lg">
            <DialogHeader>
              <DialogTitle>{editing ? "Persona bearbeiten" : "Neue Persona erstellen"}</DialogTitle>
              <DialogDescription>Definiere wie deine AI denken & sprechen soll.</DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <div className="grid grid-cols-[80px_1fr] gap-3">
                <div>
                  <Label>Emoji</Label>
                  <Input value={form.avatar_emoji} onChange={e => setForm(f => ({ ...f, avatar_emoji: e.target.value }))} className="mt-1.5 text-2xl text-center" maxLength={8} />
                </div>
                <div>
                  <Label>Name</Label>
                  <Input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="z.B. Roaster" className="mt-1.5" maxLength={60} />
                </div>
              </div>
              <div>
                <Label>Kurzbeschreibung (optional)</Label>
                <Input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="Macht Sprüche über alles" className="mt-1.5" maxLength={200} />
              </div>
              <div>
                <Label>System-Prompt *</Label>
                <Textarea
                  value={form.system_prompt}
                  onChange={e => setForm(f => ({ ...f, system_prompt: e.target.value }))}
                  placeholder="Du bist ein Roaster. Du machst freundliche aber bissige Sprüche über alles, was der User schreibt. Antworte auf Deutsch, kurz, mit viel Attitude."
                  className="mt-1.5 min-h-[140px] font-mono text-xs"
                  maxLength={4000}
                />
                <p className="text-xs text-muted-foreground mt-1">{form.system_prompt.length}/4000</p>
              </div>
              <div className="flex items-center gap-3">
                <Switch checked={form.is_public} onCheckedChange={v => setForm(f => ({ ...f, is_public: v }))} />
                <Label className="cursor-pointer" onClick={() => setForm(f => ({ ...f, is_public: !f.is_public }))}>Öffentlich (andere User können nutzen)</Label>
              </div>
            </div>
            <DialogFooter>
              <Button variant="ghost" onClick={() => setOpen(false)}>Abbrechen</Button>
              <Button onClick={save} className="bg-gradient-primary text-primary-foreground">
                {editing ? "Speichern" : "Erstellen"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </main>
    </div>
  );
};
export default Personas;
