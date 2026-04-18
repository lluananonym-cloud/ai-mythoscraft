import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogDescription,
} from "@/components/ui/dialog";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Plus, Copy, Check, Trash2, Ticket, Link as LinkIcon } from "lucide-react";
import { toast } from "sonner";

type BoostMode = "permanent" | "temporary" | "oneshot";
type BoostCode = {
  id: string; code: string; mode: BoostMode;
  daily_limit: number; duration_days: number | null; bonus_requests: number | null;
  max_uses: number; used_count: number; expires_at: string | null; note: string | null;
  created_at: string;
};

const randCode = (len = 10) => {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const arr = new Uint8Array(len);
  crypto.getRandomValues(arr);
  return Array.from(arr, b => chars[b % chars.length]).join("");
};

const BoostCodesAdmin = () => {
  const [codes, setCodes] = useState<BoostCode[]>([]);
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);

  // form
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
    if (finalCode.length < 4 || finalCode.length > 64) {
      toast.error("Code muss 4-64 Zeichen haben");
      return;
    }

    const payload: any = {
      code: finalCode,
      mode,
      max_uses: Math.max(0, Math.floor(maxUses)),
      note: note.trim() || null,
      expires_at: expiresInDays ? new Date(Date.now() + Number(expiresInDays) * 86400000).toISOString() : null,
    };
    if (mode === "permanent") payload.daily_limit = dailyLimit;
    if (mode === "temporary") { payload.daily_limit = dailyLimit; payload.duration_days = durationDays; }
    if (mode === "oneshot") { payload.daily_limit = 0; payload.bonus_requests = bonusRequests; }

    const { error } = await supabase.from("boost_codes").insert(payload);
    if (error) {
      toast.error(error.message.includes("duplicate") ? "Code existiert bereits" : "Fehler beim Erstellen");
      return;
    }
    toast.success("Code erstellt");
    reset();
    setOpen(false);
    load();
  };

  const del = async (id: string) => {
    if (!confirm("Code wirklich löschen?")) return;
    await supabase.from("boost_codes").delete().eq("id", id);
    load();
  };

  const copyCode = async (c: BoostCode, withLink = false) => {
    const link = `${window.location.origin}/redeem?code=${c.code}`;
    const text = withLink
      ? `🎁 **Mythos AI Boost Code**\nCode: \`${c.code}\`\nEinlösen: ${link}`
      : c.code;
    await navigator.clipboard.writeText(text);
    setCopied(c.id + (withLink ? "L" : "C"));
    setTimeout(() => setCopied(null), 1500);
    toast.success(withLink ? "Discord-Nachricht kopiert" : "Code kopiert");
  };

  const modeLabel = (m: BoostMode) =>
    m === "permanent" ? "Permanent" : m === "temporary" ? "Temporär" : "One-Shot Bonus";

  const codeDescription = (c: BoostCode) => {
    if (c.mode === "permanent") return `${c.daily_limit} Requests/Tag (permanent)`;
    if (c.mode === "temporary") return `${c.daily_limit}/Tag für ${c.duration_days} Tage`;
    return `+${c.bonus_requests} Bonus-Requests (einmalig)`;
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="text-sm text-muted-foreground">
          Generiere Codes für Discord-Booster, VIPs oder Aktionen. Teile Code oder Link.
        </p>
        <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) reset(); }}>
          <DialogTrigger asChild>
            <Button className="bg-gradient-primary text-primary-foreground hover:opacity-90">
              <Plus className="h-4 w-4 mr-1.5" /> Neuer Code
            </Button>
          </DialogTrigger>
          <DialogContent className="glass-strong max-w-md">
            <DialogHeader>
              <DialogTitle>Boost Code erstellen</DialogTitle>
              <DialogDescription>Erhöht das tägliche API-Limit für User die ihn einlösen.</DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <div>
                <Label>Modus</Label>
                <Select value={mode} onValueChange={(v) => setMode(v as BoostMode)}>
                  <SelectTrigger className="mt-1.5"><SelectValue /></SelectTrigger>
                  <SelectContent className="glass-strong">
                    <SelectItem value="permanent">Permanent (dauerhaft erhöhtes Tageslimit)</SelectItem>
                    <SelectItem value="temporary">Temporär (X Tage erhöht, dann zurück)</SelectItem>
                    <SelectItem value="oneshot">One-Shot Bonus (einmalige Extra-Requests)</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label>Code (leer lassen = automatisch)</Label>
                <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="z.B. DISCORDVIP" className="mt-1.5 font-mono uppercase" />
              </div>

              {(mode === "permanent" || mode === "temporary") && (
                <div>
                  <Label>Daily Limit (Requests/Tag)</Label>
                  <Input type="number" min={1} value={dailyLimit} onChange={(e) => setDailyLimit(parseInt(e.target.value) || 100)} className="mt-1.5" />
                </div>
              )}
              {mode === "temporary" && (
                <div>
                  <Label>Dauer (Tage)</Label>
                  <Input type="number" min={1} value={durationDays} onChange={(e) => setDurationDays(parseInt(e.target.value) || 7)} className="mt-1.5" />
                </div>
              )}
              {mode === "oneshot" && (
                <div>
                  <Label>Bonus-Requests (gesamt)</Label>
                  <Input type="number" min={1} value={bonusRequests} onChange={(e) => setBonusRequests(parseInt(e.target.value) || 500)} className="mt-1.5" />
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>Max. User</Label>
                  <Input type="number" min={0} value={maxUses} onChange={(e) => setMaxUses(parseInt(e.target.value) || 0)} className="mt-1.5" />
                  <p className="text-[10px] text-muted-foreground mt-1">0 = unbegrenzt</p>
                </div>
                <div>
                  <Label>Läuft ab in (Tagen)</Label>
                  <Input type="number" min={0} value={expiresInDays} onChange={(e) => setExpiresInDays(e.target.value ? parseInt(e.target.value) : "")} placeholder="∞" className="mt-1.5" />
                </div>
              </div>

              <div>
                <Label>Notiz (intern)</Label>
                <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="z.B. Discord Boost Aktion Nov 25" className="mt-1.5 min-h-[60px]" maxLength={500} />
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
          <div className="p-8 text-center text-sm text-muted-foreground">Noch keine Codes generiert.</div>
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
                <div className="text-xs text-muted-foreground">{codeDescription(c)}</div>
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
                <Button size="sm" variant="ghost" onClick={() => copyCode(c, true)} title="Discord-Nachricht (Code + Link)">
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
};

export default BoostCodesAdmin;
