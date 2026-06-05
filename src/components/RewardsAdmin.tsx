import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogDescription,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Plus, Copy, Check, Trash2, Link as LinkIcon, Gift, Sparkles, ChevronDown, ChevronRight,
  User as UserIcon, Search,
} from "lucide-react";
import { toast } from "sonner";

/* ============ TYPES ============ */
type BoostMode = "permanent" | "temporary" | "oneshot";
type BoostCode = {
  id: string; code: string; mode: BoostMode;
  daily_limit: number; duration_days: number | null; bonus_requests: number | null;
  max_uses: number; used_count: number; expires_at: string | null; note: string | null;
  created_at: string;
};
type UserRow = {
  user_id: string; email: string; display_name: string | null;
  mc_username: string | null; age: number | null; about: string | null;
  interests: string[] | null; favorite_block: string | null; playstyle: string | null;
  onboarded: boolean; created_at: string;
  tier: string; expires_at: string | null; source: string;
};

const randCode = (len = 10) => {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const arr = new Uint8Array(len);
  crypto.getRandomValues(arr);
  return Array.from(arr, b => chars[b % chars.length]).join("");
};
const tierBadge = (t: string) => t === "pro" ? "Pro" : t === "light" ? "Light" : "Free";
const tierColor = (t: string) => t === "pro" ? "text-fuchsia-400" : t === "light" ? "text-sky-400" : "text-muted-foreground";

/* ============ COMPONENT ============ */
export default function RewardsAdmin() {
  return (
    <Tabs defaultValue="users" className="w-full">
      <TabsList className="bg-secondary/50">
        <TabsTrigger value="users"><UserIcon className="h-3.5 w-3.5 mr-1.5" />Nutzer & Abos</TabsTrigger>
        <TabsTrigger value="boosts"><Gift className="h-3.5 w-3.5 mr-1.5" />Boost-Codes</TabsTrigger>
      </TabsList>
      <TabsContent value="users" className="mt-4"><UsersTab /></TabsContent>
      <TabsContent value="boosts" className="mt-4"><BoostsTab /></TabsContent>
    </Tabs>
  );
}

/* ============ USERS TAB ============ */
function UsersTab() {
  const { user } = useAuth();
  const [rows, setRows] = useState<UserRow[]>([]);
  const [search, setSearch] = useState("");
  const [expanded, setExpanded] = useState<string | null>(null);
  const [giftFor, setGiftFor] = useState<UserRow | null>(null);
  const [days, setDays] = useState(30);
  const [tier, setTier] = useState<"light" | "pro">("pro");
  const [note, setNote] = useState("");

  const load = async () => {
    const [{ data: profs }, { data: subs }] = await Promise.all([
      supabase.from("profiles").select("user_id,email,display_name,mc_username,age,about,interests,favorite_block,playstyle,onboarded,created_at"),
      supabase.from("subscriptions").select("user_id,tier,expires_at,source"),
    ]);
    if (profs) {
      setRows((profs as any[]).map(p => {
        const s = subs?.find((x: any) => x.user_id === p.user_id);
        return { ...p, tier: s?.tier ?? "free", expires_at: s?.expires_at ?? null, source: s?.source ?? "free" };
      }));
    }
  };
  useEffect(() => { load(); }, []);

  const gift = async () => {
    if (!giftFor || !user) return;
    const expires_at = days > 0 ? new Date(Date.now() + days * 86400_000).toISOString() : null;
    const { error } = await supabase.from("subscriptions").upsert({
      user_id: giftFor.user_id, tier, source: "gift",
      expires_at, granted_by: user.id, note: note || `Geschenk: ${days} Tage ${tier}`,
    }, { onConflict: "user_id" });
    if (error) return toast.error(error.message);
    toast.success(`${giftFor.display_name || giftFor.email}: ${days || "∞"} Tage ${tierBadge(tier)}`);
    setGiftFor(null); setNote(""); load();
  };
  const revoke = async (uid: string) => {
    if (!confirm("Abo entziehen?")) return;
    await supabase.from("subscriptions").upsert({ user_id: uid, tier: "free", source: "free", expires_at: null }, { onConflict: "user_id" });
    load();
  };

  const filtered = rows.filter(r => {
    const q = search.toLowerCase().trim();
    if (!q) return true;
    return (r.email?.toLowerCase().includes(q) || r.display_name?.toLowerCase().includes(q) || r.mc_username?.toLowerCase().includes(q));
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Suche nach Email, Name, MC-Username…" className="pl-9 bg-input/50" />
        </div>
        <span className="text-xs text-muted-foreground whitespace-nowrap">{filtered.length} / {rows.length}</span>
      </div>

      <div className="glass-strong rounded-2xl divide-y divide-border/50">
        {filtered.map(r => {
          const active = (r.tier === "pro" || r.tier === "light") && (!r.expires_at || new Date(r.expires_at) > new Date());
          const isOpen = expanded === r.user_id;
          return (
            <div key={r.user_id}>
              <button onClick={() => setExpanded(isOpen ? null : r.user_id)} className="w-full p-4 flex items-center justify-between gap-3 hover:bg-foreground/5 text-left">
                <div className="min-w-0 flex-1 flex items-center gap-2">
                  {isOpen ? <ChevronDown className="h-4 w-4 text-muted-foreground shrink-0" /> : <ChevronRight className="h-4 w-4 text-muted-foreground shrink-0" />}
                  <div className="min-w-0">
                    <div className="font-medium text-sm truncate flex items-center gap-2">
                      {r.display_name || r.email}
                      {!r.onboarded && <span className="text-[9px] px-1.5 py-0.5 rounded bg-muted text-muted-foreground">No Onboarding</span>}
                    </div>
                    <div className="text-xs text-muted-foreground truncate">
                      {r.email} · <span className={tierColor(r.tier)}>{tierBadge(r.tier)}{active && r.expires_at ? ` bis ${new Date(r.expires_at).toLocaleDateString("de-DE")}` : active ? " ∞" : ""}</span>
                    </div>
                  </div>
                </div>
                <div className="flex gap-2 shrink-0" onClick={(e) => e.stopPropagation()}>
                  {active && <Button size="sm" variant="outline" onClick={() => revoke(r.user_id)}>Entziehen</Button>}
                  <Button size="sm" onClick={() => { setGiftFor(r); setDays(30); setTier("pro"); }} className="bg-gradient-primary text-primary-foreground">
                    <Gift className="h-3.5 w-3.5 mr-1.5" />Verschenken
                  </Button>
                </div>
              </button>
              {isOpen && (
                <div className="px-4 pb-4 pl-12 grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-2 text-xs">
                  <Detail label="MC-Username" v={r.mc_username} />
                  <Detail label="Alter" v={r.age?.toString()} />
                  <Detail label="Playstyle" v={r.playstyle} />
                  <Detail label="Lieblings-Block" v={r.favorite_block} />
                  <Detail label="Interessen" v={r.interests?.join(", ")} />
                  <Detail label="Account seit" v={r.created_at ? new Date(r.created_at).toLocaleDateString("de-DE") : null} />
                  {r.about && <div className="md:col-span-2"><div className="text-muted-foreground mb-0.5">Über</div><div className="italic">"{r.about}"</div></div>}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <Dialog open={!!giftFor} onOpenChange={(o) => !o && setGiftFor(null)}>
        <DialogContent className="glass-strong max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-primary" />Abo verschenken</DialogTitle>
          </DialogHeader>
          {giftFor && (
            <div className="space-y-3">
              <p className="text-sm">An <strong>{giftFor.display_name || giftFor.email}</strong></p>
              <div><Label>Plan</Label>
                <Select value={tier} onValueChange={(v) => setTier(v as any)}>
                  <SelectTrigger className="mt-1.5"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="light">Light – Bilder + 150 Chats/Tag</SelectItem>
                    <SelectItem value="pro">Pro – Alles inkl. Voice / Musik / Video / Twin</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div><Label>Dauer (Tage, 0 = unbefristet)</Label>
                <Input type="number" min={0} value={days} onChange={(e) => setDays(parseInt(e.target.value) || 0)} className="mt-1.5" />
              </div>
              <div><Label>Notiz (optional)</Label>
                <Input value={note} onChange={(e) => setNote(e.target.value)} className="mt-1.5" placeholder="z.B. Beta-Tester" />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button onClick={gift} className="bg-gradient-primary text-primary-foreground">Verschenken</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Detail({ label, v }: { label: string; v?: string | null }) {
  if (!v) return null;
  return (<div><div className="text-muted-foreground">{label}</div><div className="font-medium truncate">{v}</div></div>);
}

/* ============ BOOSTS TAB (extracted from BoostCodesAdmin) ============ */
function BoostsTab() {
  const [codes, setCodes] = useState<BoostCode[]>([]);
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);
  const [mode, setMode] = useState<BoostMode>("permanent");
  const [code, setCode] = useState("");
  const [dailyLimit, setDailyLimit] = useState(1000);
  const [durationDays, setDurationDays] = useState(7);
  const [bonusRequests, setBonusRequests] = useState(500);
  const [maxUses, setMaxUses] = useState(1);
  const [expiresInDays, setExpiresInDays] = useState<number | "">("");
  const [note, setNote] = useState("");

  const load = async () => {
    const { data } = await supabase.from("boost_codes").select("*").order("created_at", { ascending: false });
    if (data) setCodes(data as any);
  };
  useEffect(() => { load(); }, []);

  const reset = () => {
    setCode(""); setMode("permanent"); setDailyLimit(1000); setDurationDays(7);
    setBonusRequests(500); setMaxUses(1); setExpiresInDays(""); setNote("");
  };

  const create = async () => {
    const finalCode = (code.trim() || randCode()).toUpperCase().replace(/\s+/g, "");
    if (finalCode.length < 4 || finalCode.length > 64) return toast.error("Code muss 4-64 Zeichen haben");
    const payload: any = {
      code: finalCode, mode, max_uses: Math.max(0, Math.floor(maxUses)),
      note: note.trim() || null,
      expires_at: expiresInDays ? new Date(Date.now() + Number(expiresInDays) * 86400000).toISOString() : null,
    };
    if (mode === "permanent") payload.daily_limit = dailyLimit;
    if (mode === "temporary") { payload.daily_limit = dailyLimit; payload.duration_days = durationDays; }
    if (mode === "oneshot") { payload.daily_limit = 0; payload.bonus_requests = bonusRequests; }
    const { error } = await supabase.from("boost_codes").insert(payload);
    if (error) return toast.error(error.message.includes("duplicate") ? "Code existiert bereits" : "Fehler");
    toast.success("Code erstellt"); reset(); setOpen(false); load();
  };

  const del = async (id: string) => {
    if (!confirm("Code löschen?")) return;
    await supabase.from("boost_codes").delete().eq("id", id); load();
  };

  const copyCode = async (c: BoostCode, withLink = false) => {
    const link = `${window.location.origin}/redeem?code=${c.code}`;
    const text = withLink ? `🎁 **Mythos AI Boost Code**\nCode: \`${c.code}\`\nEinlösen: ${link}` : c.code;
    await navigator.clipboard.writeText(text);
    setCopied(c.id + (withLink ? "L" : "C"));
    setTimeout(() => setCopied(null), 1500);
    toast.success(withLink ? "Discord-Nachricht kopiert" : "Code kopiert");
  };

  const modeLabel = (m: BoostMode) => m === "permanent" ? "Permanent" : m === "temporary" ? "Temporär" : "One-Shot";
  const desc = (c: BoostCode) =>
    c.mode === "permanent" ? `${c.daily_limit} Req/Tag (permanent)` :
    c.mode === "temporary" ? `${c.daily_limit}/Tag für ${c.duration_days} Tage` :
    `+${c.bonus_requests} Bonus-Requests`;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="text-sm text-muted-foreground">Generiere Codes für API-Limit-Boosts. Pro-Abos werden im Tab „Nutzer & Abos" verschenkt.</p>
        <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) reset(); }}>
          <DialogTrigger asChild>
            <Button className="bg-gradient-primary text-primary-foreground"><Plus className="h-4 w-4 mr-1.5" />Neuer Code</Button>
          </DialogTrigger>
          <DialogContent className="glass-strong max-w-md">
            <DialogHeader>
              <DialogTitle>Boost Code erstellen</DialogTitle>
              <DialogDescription>Erhöht das tägliche API-Limit beim Einlösen.</DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <div><Label>Modus</Label>
                <Select value={mode} onValueChange={(v) => setMode(v as BoostMode)}>
                  <SelectTrigger className="mt-1.5"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="permanent">Permanent</SelectItem>
                    <SelectItem value="temporary">Temporär (X Tage)</SelectItem>
                    <SelectItem value="oneshot">One-Shot Bonus</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div><Label>Code (leer = automatisch)</Label>
                <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="DISCORDVIP" className="mt-1.5 font-mono uppercase" />
              </div>
              {(mode === "permanent" || mode === "temporary") && (
                <div><Label>Daily Limit</Label>
                  <Input type="number" min={1} value={dailyLimit} onChange={(e) => setDailyLimit(parseInt(e.target.value) || 100)} className="mt-1.5" />
                </div>
              )}
              {mode === "temporary" && (
                <div><Label>Dauer (Tage)</Label>
                  <Input type="number" min={1} value={durationDays} onChange={(e) => setDurationDays(parseInt(e.target.value) || 7)} className="mt-1.5" />
                </div>
              )}
              {mode === "oneshot" && (
                <div><Label>Bonus-Requests</Label>
                  <Input type="number" min={1} value={bonusRequests} onChange={(e) => setBonusRequests(parseInt(e.target.value) || 500)} className="mt-1.5" />
                </div>
              )}
              <div className="grid grid-cols-2 gap-3">
                <div><Label>Max. User</Label>
                  <Input type="number" min={0} value={maxUses} onChange={(e) => setMaxUses(parseInt(e.target.value) || 0)} className="mt-1.5" />
                  <p className="text-[10px] text-muted-foreground mt-1">0 = ∞</p>
                </div>
                <div><Label>Läuft ab (Tage)</Label>
                  <Input type="number" min={0} value={expiresInDays} onChange={(e) => setExpiresInDays(e.target.value ? parseInt(e.target.value) : "")} placeholder="∞" className="mt-1.5" />
                </div>
              </div>
              <div><Label>Notiz</Label>
                <Textarea value={note} onChange={(e) => setNote(e.target.value)} className="mt-1.5 min-h-[60px]" maxLength={500} />
              </div>
            </div>
            <DialogFooter>
              <Button onClick={create} className="bg-gradient-primary text-primary-foreground">Erstellen</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      <div className="glass-strong rounded-2xl divide-y divide-border/50">
        {codes.length === 0 ? (
          <div className="p-8 text-center text-sm text-muted-foreground">Noch keine Codes.</div>
        ) : codes.map(c => {
          const expired = c.expires_at && new Date(c.expires_at) < new Date();
          const exhausted = c.max_uses > 0 && c.used_count >= c.max_uses;
          return (
            <div key={c.id} className="p-4 flex items-start justify-between gap-3 flex-wrap">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                  <span className="font-mono text-sm font-semibold tracking-wider">{c.code}</span>
                  <span className="text-[10px] px-2 py-0.5 rounded bg-primary/20 text-primary">{modeLabel(c.mode)}</span>
                  {expired && <span className="text-[10px] px-2 py-0.5 rounded bg-destructive/20 text-destructive">Abgelaufen</span>}
                  {exhausted && <span className="text-[10px] px-2 py-0.5 rounded bg-muted text-muted-foreground">Aufgebraucht</span>}
                </div>
                <div className="text-xs text-muted-foreground">{desc(c)}</div>
                <div className="text-xs text-muted-foreground mt-0.5">
                  {c.used_count} / {c.max_uses === 0 ? "∞" : c.max_uses} eingelöst
                  {c.expires_at && ` · läuft ab ${new Date(c.expires_at).toLocaleDateString()}`}
                </div>
                {c.note && <div className="text-xs italic text-muted-foreground mt-1">"{c.note}"</div>}
              </div>
              <div className="flex gap-1 shrink-0">
                <Button size="sm" variant="ghost" onClick={() => copyCode(c, false)}>
                  {copied === c.id + "C" ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                </Button>
                <Button size="sm" variant="ghost" onClick={() => copyCode(c, true)} title="Discord-Nachricht">
                  {copied === c.id + "L" ? <Check className="h-4 w-4" /> : <LinkIcon className="h-4 w-4" />}
                </Button>
                <Button size="sm" variant="ghost" onClick={() => del(c.id)} className="hover:text-destructive">
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
