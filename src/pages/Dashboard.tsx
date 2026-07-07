import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import MinecraftSkin3D from "@/components/MinecraftSkin3D";
import MinecraftAvatar from "@/components/MinecraftAvatar";
import McLinkPanel from "@/components/McLinkPanel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogDescription,
} from "@/components/ui/dialog";
import { Plus, Key, Trash2, Copy, Check, BarChart3, Save, User as UserIcon, ShieldCheck, MessageSquare } from "lucide-react";
import { Switch } from "@/components/ui/switch";
import { isPersistentSessionEnabled, setPersistentSessionEnabled } from "@/lib/persistentSession";
import { toast } from "sonner";
import { Link } from "react-router-dom";

type ApiKey = {
  id: string; name: string; key_prefix: string; daily_limit: number;
  total_requests: number; last_used_at: string | null; revoked: boolean; created_at: string;
};

const Dashboard = () => {
  const { user, profile, refreshProfile } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get("tab") === "profile" ? "profile" : "keys";

  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [name, setName] = useState("");
  const [newKey, setNewKey] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [usage24h, setUsage24h] = useState(0);

  // Profile editing
  const [displayName, setDisplayName] = useState("");
  const [mcUsername, setMcUsername] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);
  const [stayLoggedIn, setStayLoggedIn] = useState(false);
  const [startInChat, setStartInChat] = useState(false);
  const [savingStartInChat, setSavingStartInChat] = useState(false);
  useEffect(() => { setStayLoggedIn(isPersistentSessionEnabled()); }, []);
  useEffect(() => { setStartInChat(!!profile?.start_in_chat); }, [profile?.start_in_chat]);

  const load = async () => {
    const { data } = await supabase.from("api_keys").select("*").order("created_at", { ascending: false });
    if (data) setKeys(data as any);
    const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
    const { count } = await supabase.from("api_usage").select("*", { count: "exact", head: true }).gte("created_at", since);
    setUsage24h(count || 0);
  };
  useEffect(() => { if (user) load(); }, [user]);

  useEffect(() => {
    setDisplayName(profile?.display_name || "");
    setMcUsername(profile?.mc_username || "");
  }, [profile]);

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

  const saveProfile = async () => {
    if (!user) return;
    setSavingProfile(true);
    const { error } = await supabase.from("profiles").update({
      display_name: displayName.trim() || null,
      mc_username: mcUsername.trim() || null,
    }).eq("user_id", user.id);
    setSavingProfile(false);
    if (error) {
      toast.error("Speichern fehlgeschlagen");
      return;
    }
    toast.success("Profil aktualisiert");
    await refreshProfile();
  };

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-6 md:py-8 max-w-5xl">
        <div className="mb-6 md:mb-8">
          <h1 className="font-display text-2xl md:text-3xl font-bold">Dashboard</h1>
          <p className="text-muted-foreground text-sm mt-1">Verwalte dein Profil & deine API-Keys</p>
        </div>

        <Tabs
          value={initialTab}
          onValueChange={(v) => setSearchParams(v === "profile" ? { tab: "profile" } : {})}
        >
          <TabsList className="bg-secondary/50">
            <TabsTrigger value="keys"><Key className="h-3.5 w-3.5 mr-1.5" />API Keys</TabsTrigger>
            <TabsTrigger value="profile"><UserIcon className="h-3.5 w-3.5 mr-1.5" />Profil</TabsTrigger>
          </TabsList>

          {/* ========== KEYS TAB ========== */}
          <TabsContent value="keys" className="mt-6 space-y-6">
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <p className="text-sm text-muted-foreground">Claude-kompatible Keys (sk-ant-mythos-…) für MythosBrowse & Co.</p>
              <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) setNewKey(null); }}>
                <DialogTrigger asChild>
                  <Button className="bg-gradient-primary text-primary-foreground hover:opacity-90">
                    <Plus className="h-4 w-4 mr-1.5" />Neuer Key
                  </Button>
                </DialogTrigger>
                <DialogContent className="glass-strong max-w-md">
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
                      <div>
                        <Label>Name</Label>
                        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="z.B. MythosBrowse" className="mt-1.5" />
                      </div>
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
            <div className="grid grid-cols-2 md:grid-cols-3 gap-3 md:gap-4">
              <div className="glass rounded-2xl p-4 md:p-5">
                <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2"><Key className="h-3.5 w-3.5" />Aktive Keys</div>
                <div className="font-display text-2xl md:text-3xl font-bold">{keys.filter(k => !k.revoked).length}</div>
              </div>
              <div className="glass rounded-2xl p-4 md:p-5">
                <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2"><BarChart3 className="h-3.5 w-3.5" />Requests (24h)</div>
                <div className="font-display text-2xl md:text-3xl font-bold">{usage24h}</div>
              </div>
              <div className="glass rounded-2xl p-4 md:p-5 col-span-2 md:col-span-1">
                <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2">Total Requests</div>
                <div className="font-display text-2xl md:text-3xl font-bold">
                  {keys.reduce((s, k) => s + Number(k.total_requests || 0), 0)}
                </div>
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
                        <div className="flex items-center gap-2 mb-1 flex-wrap">
                          <span className="font-medium text-sm">{k.name}</span>
                          {k.revoked && <span className="text-xs px-2 py-0.5 rounded bg-destructive/20 text-destructive">Widerrufen</span>}
                        </div>
                        <div className="font-mono text-xs text-muted-foreground break-all">{k.key_prefix}…</div>
                        <div className="text-xs text-muted-foreground mt-1">
                          {Number(k.total_requests)} Requests · Limit: {k.daily_limit}/Tag
                          {k.last_used_at && ` · Zuletzt: ${new Date(k.last_used_at).toLocaleDateString()}`}
                        </div>
                      </div>
                      {!k.revoked && (
                        <Button size="icon" variant="ghost" onClick={() => revoke(k.id)} className="text-muted-foreground hover:text-destructive shrink-0">
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </TabsContent>

          {/* ========== PROFILE TAB ========== */}
          <TabsContent value="profile" className="mt-6">
            <div className="grid grid-cols-1 md:grid-cols-[1fr_280px] gap-6">
              {/* Form */}
              <div className="glass-strong rounded-2xl p-5 md:p-6 space-y-4">
                <div className="flex items-center gap-3 mb-2">
                  <MinecraftAvatar
                    username={profile?.mc_username}
                    fallback={profile?.display_name || user?.email}
                    size={48}
                  />
                  <div className="min-w-0">
                    <div className="font-display font-semibold truncate">{profile?.display_name || user?.email}</div>
                    <div className="text-xs text-muted-foreground truncate">{user?.email}</div>
                  </div>
                </div>

                <div>
                  <Label>Anzeigename</Label>
                  <Input
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="Steve"
                    className="mt-1.5"
                  />
                </div>

                <div>
                  <Label>Minecraft-Username</Label>
                  <Input
                    value={mcUsername}
                    onChange={(e) => setMcUsername(e.target.value)}
                    placeholder="z.B. Notch"
                    className="mt-1.5"
                  />
                  <p className="text-xs text-muted-foreground mt-1.5">
                    Dein Minecraft-Skin wird als Profilbild + 3D-Modell verwendet (Java-Account oder mit Cape per mc-heads.net).
                  </p>
                </div>

                <McLinkPanel />


                <Button
                  onClick={saveProfile}
                  disabled={savingProfile}
                  className="bg-gradient-primary text-primary-foreground hover:opacity-90"
                >
                  <Save className="h-4 w-4 mr-1.5" />
                  {savingProfile ? "Speichern..." : "Speichern"}
                </Button>
              </div>

              <div className="glass-strong rounded-2xl p-5 md:p-6 md:col-span-2 space-y-5">
                <div className="flex items-start gap-3">
                  <ShieldCheck className="h-5 w-5 mt-0.5 text-primary shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-3">
                      <div className="font-display font-semibold">Dauerhaft angemeldet bleiben</div>
                      <Switch
                        checked={stayLoggedIn}
                        onCheckedChange={async (v) => {
                          setStayLoggedIn(v);
                          await setPersistentSessionEnabled(v);
                          toast.success(v ? "Du bleibst auf diesem Gerät angemeldet." : "Auto-Anmeldung deaktiviert.");
                        }}
                      />
                    </div>
                    <p className="text-xs text-muted-foreground mt-1.5">
                      Aktivieren, falls du in der installierten App (PWA) immer wieder ausgeloggt wirst.
                      Deine Sitzung wird zusätzlich sicher im Gerät gespeichert (IndexedDB) und
                      übersteht so iOS-/Browser-Cache-Bereinigungen. Gilt nur für dieses Gerät.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 pt-4 border-t border-border/50">
                  <MessageSquare className="h-5 w-5 mt-0.5 text-primary shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-3">
                      <div className="font-display font-semibold">App startet direkt im Chat</div>
                      <Switch
                        checked={startInChat}
                        disabled={savingStartInChat}
                        onCheckedChange={async (v) => {
                          if (!user) return;
                          setStartInChat(v);
                          setSavingStartInChat(true);
                          const { error } = await supabase
                            .from("profiles")
                            .update({ start_in_chat: v })
                            .eq("user_id", user.id);
                          setSavingStartInChat(false);
                          if (error) {
                            setStartInChat(!v);
                            toast.error("Konnte nicht gespeichert werden.");
                            return;
                          }
                          await refreshProfile();
                          toast.success(v ? "Beim Öffnen geht's direkt in den Chat." : "Du landest beim Öffnen auf der Startseite.");
                        }}
                      />
                    </div>
                    <p className="text-xs text-muted-foreground mt-1.5">
                      Wenn aktiv, wirst du beim Öffnen der App/PWA automatisch in den Chat geleitet — wie bei ChatGPT.
                    </p>
                  </div>
                </div>
              </div>

              {/* 3D Skin */}
              <div className="flex flex-col items-center gap-3">
                <div className="text-xs text-muted-foreground uppercase tracking-wider">3D Skin Preview</div>
                {mcUsername ? (
                  <MinecraftSkin3D username={mcUsername} width={240} height={320} />
                ) : (
                  <div className="glass rounded-2xl flex items-center justify-center text-xs text-muted-foreground p-6 text-center" style={{ width: 240, height: 320 }}>
                    Trage deinen<br />Minecraft-Username ein,<br />um deinen Skin zu sehen
                  </div>
                )}
              </div>
            </div>
          </TabsContent>
        </Tabs>
      </main>
    </div>
  );
};
export default Dashboard;
