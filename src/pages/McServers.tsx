import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogDescription,
} from "@/components/ui/dialog";
import { Plus, Trash2, Copy, Check, Server, Activity, MessageCircle, Skull, UserPlus } from "lucide-react";
import { toast } from "sonner";
import { Link } from "react-router-dom";

type McServer = {
  id: string; name: string; key_prefix: string; ingame_chat_enabled: boolean;
  events_enabled: boolean; greet_on_join: boolean; comment_on_death: boolean; chat_trigger: string;
  ai_persona_id: string | null; last_seen_at: string | null; total_events: number; revoked: boolean; created_at: string;
};

type Persona = { id: string; name: string; avatar_emoji: string | null };

const McServers = () => {
  const { user } = useAuth();
  const [servers, setServers] = useState<McServer[]>([]);
  const [personas, setPersonas] = useState<Persona[]>([]);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [newKey, setNewKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const load = async () => {
    const { data } = await supabase.from("mc_servers").select("*").order("created_at", { ascending: false });
    if (data) setServers(data as any);
    const { data: p } = await supabase.from("ai_personas").select("id,name,avatar_emoji").order("name");
    if (p) setPersonas(p as any);
  };
  useEffect(() => { if (user) load(); }, [user]);

  const create = async () => {
    if (!name.trim()) return;
    const { data, error } = await supabase.functions.invoke("create-mc-key", { body: { name: name.trim() } });
    if (error || !data?.key) { toast.error("Fehler beim Erstellen"); return; }
    setNewKey(data.key);
    setName("");
    load();
  };

  const remove = async (id: string) => {
    if (!confirm("Server wirklich löschen? Plugin verliert die Verbindung.")) return;
    await supabase.from("mc_servers").delete().eq("id", id);
    load();
  };

  const update = async (id: string, patch: Partial<McServer>) => {
    const { error } = await supabase.from("mc_servers").update(patch).eq("id", id);
    if (error) toast.error("Update fehlgeschlagen");
    else load();
  };

  const copy = async (text: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    toast.success("Kopiert!");
  };

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-8 max-w-5xl">
        <div className="flex items-center justify-between mb-2 gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-primary flex items-center justify-center">
              <Server className="h-5 w-5 text-primary-foreground" />
            </div>
            <h1 className="font-display text-2xl md:text-3xl font-bold">Minecraft-Server</h1>
          </div>
          <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) setNewKey(null); }}>
            <DialogTrigger asChild>
              <Button className="bg-gradient-primary text-primary-foreground"><Plus className="h-4 w-4 mr-1.5" />Server hinzufügen</Button>
            </DialogTrigger>
            <DialogContent className="glass-strong max-w-md">
              <DialogHeader>
                <DialogTitle>{newKey ? "Plugin-Key erstellt!" : "Neuer Server"}</DialogTitle>
                <DialogDescription>
                  {newKey ? "Kopiere ihn JETZT — er wird nur einmal angezeigt." : "Verbinde deinen Minecraft-Server mit Mythos AI. Du bekommst einen Key, den du im Plugin einträgst."}
                </DialogDescription>
              </DialogHeader>
              {newKey ? (
                <div className="space-y-3">
                  <div className="glass rounded-lg p-3 font-mono text-xs break-all">{newKey}</div>
                  <Button onClick={() => copy(newKey)} className="w-full" variant="outline">
                    {copied ? <Check className="h-4 w-4 mr-1.5" /> : <Copy className="h-4 w-4 mr-1.5" />}
                    Kopieren
                  </Button>
                  <p className="text-xs text-muted-foreground">
                    👉 Trag diesen Key in der <code className="text-accent">config.yml</code> deines Plugins ein. Anleitung: <Link to="/docs#minecraft-plugin" className="text-accent hover:underline">/docs</Link>
                  </p>
                </div>
              ) : (
                <div>
                  <Label>Server-Name</Label>
                  <Input value={name} onChange={e => setName(e.target.value)} placeholder="z.B. mythoscraft.online" className="mt-1.5" />
                </div>
              )}
              <DialogFooter>
                {newKey ? (
                  <Button onClick={() => { setOpen(false); setNewKey(null); }} className="bg-gradient-primary text-primary-foreground">Fertig</Button>
                ) : (
                  <Button onClick={create} disabled={!name.trim()} className="bg-gradient-primary text-primary-foreground">Erstellen</Button>
                )}
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
        <p className="text-muted-foreground text-sm mb-6">
          Verbinde deinen MC-Server mit Mythos AI. Sie kann Spieler begrüßen, Tode kommentieren und im Chat auf <code className="text-accent">!ai</code> antworten.
        </p>

        {servers.length === 0 ? (
          <div className="glass rounded-2xl p-10 text-center">
            <Server className="h-10 w-10 mx-auto mb-3 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground mb-4">Noch kein Server verbunden. <Link to="/docs#minecraft-plugin" className="text-accent hover:underline">Anleitung lesen</Link></p>
          </div>
        ) : (
          <div className="space-y-3">
            {servers.map(s => (
              <div key={s.id} className="glass-strong rounded-2xl p-5">
                <div className="flex items-start justify-between gap-3 mb-4 flex-wrap">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-display font-semibold">{s.name}</span>
                      {s.last_seen_at && (Date.now() - new Date(s.last_seen_at).getTime()) < 5 * 60 * 1000
                        ? <span className="text-[10px] px-2 py-0.5 rounded bg-green-500/20 text-green-300 flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" />online</span>
                        : <span className="text-[10px] px-2 py-0.5 rounded bg-secondary text-muted-foreground">offline</span>}
                    </div>
                    <code className="text-xs text-muted-foreground">{s.key_prefix}…</code>
                    <div className="text-xs text-muted-foreground mt-1">
                      <Activity className="h-3 w-3 inline mr-1" />{s.total_events} Events · zuletzt {s.last_seen_at ? new Date(s.last_seen_at).toLocaleString() : "nie"}
                    </div>
                  </div>
                  <Button size="icon" variant="ghost" onClick={() => remove(s.id)} className="text-muted-foreground hover:text-destructive">
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="flex items-center justify-between glass rounded-lg p-3">
                    <div className="flex items-center gap-2 text-sm"><MessageCircle className="h-4 w-4 text-foreground/70" />Ingame-Chat (<code className="text-accent text-xs">{s.chat_trigger}</code>)</div>
                    <Switch checked={s.ingame_chat_enabled} onCheckedChange={v => update(s.id, { ingame_chat_enabled: v })} />
                  </div>
                  <div className="flex items-center justify-between glass rounded-lg p-3">
                    <div className="flex items-center gap-2 text-sm"><UserPlus className="h-4 w-4 text-foreground/70" />Spieler begrüßen</div>
                    <Switch checked={s.greet_on_join} onCheckedChange={v => update(s.id, { greet_on_join: v })} />
                  </div>
                  <div className="flex items-center justify-between glass rounded-lg p-3">
                    <div className="flex items-center gap-2 text-sm"><Skull className="h-4 w-4 text-foreground/70" />Tod kommentieren</div>
                    <Switch checked={s.comment_on_death} onCheckedChange={v => update(s.id, { comment_on_death: v })} />
                  </div>
                  <div className="flex items-center justify-between glass rounded-lg p-3">
                    <div className="flex items-center gap-2 text-sm"><Activity className="h-4 w-4 text-foreground/70" />Events tracken</div>
                    <Switch checked={s.events_enabled} onCheckedChange={v => update(s.id, { events_enabled: v })} />
                  </div>
                </div>

                <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <Label className="text-xs">Chat-Trigger</Label>
                    <Input
                      value={s.chat_trigger}
                      onChange={e => setServers(prev => prev.map(x => x.id === s.id ? { ...x, chat_trigger: e.target.value } : x))}
                      onBlur={() => update(s.id, { chat_trigger: s.chat_trigger })}
                      className="mt-1 h-9"
                      maxLength={10}
                    />
                  </div>
                  <div>
                    <Label className="text-xs">Persona (optional)</Label>
                    <select
                      value={s.ai_persona_id || ""}
                      onChange={e => update(s.id, { ai_persona_id: e.target.value || null })}
                      className="mt-1 h-9 w-full rounded-md bg-background/40 border border-border px-2 text-sm"
                    >
                      <option value="">Standard</option>
                      {personas.map(p => <option key={p.id} value={p.id}>{p.avatar_emoji} {p.name}</option>)}
                    </select>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
};
export default McServers;
