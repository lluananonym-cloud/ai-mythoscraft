import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogDescription } from "@/components/ui/dialog";
import { Plus, Key, Trash2, Copy, Check, BarChart3 } from "lucide-react";
import { toast } from "sonner";
import { Link } from "react-router-dom";

type ApiKey = {
  id: string; name: string; key_prefix: string; daily_limit: number;
  total_requests: number; last_used_at: string | null; revoked: boolean; created_at: string;
};

const Dashboard = () => {
  const { user } = useAuth();
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [name, setName] = useState("");
  const [newKey, setNewKey] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [usage24h, setUsage24h] = useState(0);

  const load = async () => {
    const { data } = await supabase.from("api_keys").select("*").order("created_at", { ascending: false });
    if (data) setKeys(data as any);
    const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
    const { count } = await supabase.from("api_usage").select("*", { count: "exact", head: true }).gte("created_at", since);
    setUsage24h(count || 0);
  };
  useEffect(() => { if (user) load(); }, [user]);

  const create = async () => {
    if (!name.trim()) return;
    const { data, error } = await supabase.functions.invoke("create-api-key", { body: { name: name.trim() } });
    if (error || !data?.key) { toast.error("Fehler beim Erstellen"); return; }
    setNewKey(data.key);
    setName("");
    load();
  };

  const revoke = async (id: string) => {
    if (!confirm("Diesen Key wirklich widerrufen?")) return;
    await supabase.from("api_keys").update({ revoked: true }).eq("id", id);
    load();
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
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="font-display text-3xl font-bold">API Dashboard</h1>
            <p className="text-muted-foreground text-sm mt-1">Verwalte deine Mythos AI API-Keys (Claude-kompatibel)</p>
          </div>
          <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) setNewKey(null); }}>
            <DialogTrigger asChild>
              <Button className="bg-gradient-primary text-primary-foreground hover:opacity-90"><Plus className="h-4 w-4 mr-1.5" />Neuer Key</Button>
            </DialogTrigger>
            <DialogContent className="glass-strong">
              <DialogHeader>
                <DialogTitle>{newKey ? "Key erstellt!" : "Neuen API-Key erstellen"}</DialogTitle>
                <DialogDescription>
                  {newKey ? "Speichere ihn jetzt — er wird nur einmal angezeigt." : "Gib dem Key einen Namen, z. B. 'MythosBrowse'."}
                </DialogDescription>
              </DialogHeader>
              {newKey ? (
                <div className="space-y-3">
                  <div className="glass rounded-lg p-3 font-mono text-xs break-all">{newKey}</div>
                  <Button onClick={() => copy(newKey)} className="w-full" variant="outline">
                    {copied ? <Check className="h-4 w-4 mr-1.5" /> : <Copy className="h-4 w-4 mr-1.5" />}
                    Kopieren
                  </Button>
                </div>
              ) : (
                <div className="space-y-3">
                  <div><Label>Name</Label><Input value={name} onChange={(e) => setName(e.target.value)} placeholder="z.B. MythosBrowse" className="mt-1.5" /></div>
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

        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          <div className="glass rounded-2xl p-5">
            <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2"><Key className="h-3.5 w-3.5" />Aktive Keys</div>
            <div className="font-display text-3xl font-bold">{keys.filter(k => !k.revoked).length}</div>
          </div>
          <div className="glass rounded-2xl p-5">
            <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2"><BarChart3 className="h-3.5 w-3.5" />Requests (24h)</div>
            <div className="font-display text-3xl font-bold">{usage24h}</div>
          </div>
          <div className="glass rounded-2xl p-5">
            <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2">Total Requests</div>
            <div className="font-display text-3xl font-bold">{keys.reduce((s, k) => s + Number(k.total_requests || 0), 0)}</div>
          </div>
        </div>

        {/* Keys list */}
        <div className="glass-strong rounded-2xl overflow-hidden">
          <div className="p-4 border-b border-border/50 flex items-center justify-between">
            <h2 className="font-display font-semibold">Deine API Keys</h2>
            <Link to="/docs"><Button variant="ghost" size="sm">API Docs →</Button></Link>
          </div>
          {keys.length === 0 ? (
            <div className="p-12 text-center text-muted-foreground text-sm">Noch keine Keys. Erstelle deinen ersten!</div>
          ) : (
            <div className="divide-y divide-border/50">
              {keys.map(k => (
                <div key={k.id} className="p-4 flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-sm">{k.name}</span>
                      {k.revoked && <span className="text-xs px-2 py-0.5 rounded bg-destructive/20 text-destructive">Widerrufen</span>}
                    </div>
                    <div className="font-mono text-xs text-muted-foreground">{k.key_prefix}…</div>
                    <div className="text-xs text-muted-foreground mt-1">
                      {Number(k.total_requests)} Requests · Limit: {k.daily_limit}/Tag
                      {k.last_used_at && ` · Zuletzt: ${new Date(k.last_used_at).toLocaleDateString()}`}
                    </div>
                  </div>
                  {!k.revoked && (
                    <Button size="icon" variant="ghost" onClick={() => revoke(k.id)} className="text-muted-foreground hover:text-destructive">
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
};
export default Dashboard;
