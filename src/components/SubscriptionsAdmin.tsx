import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Gift, Sparkles } from "lucide-react";
import { toast } from "sonner";

type Row = { user_id: string; email: string; display_name: string | null; tier: string; expires_at: string | null; source: string };

const tierBadge = (t: string) => t === "pro" ? "Pro" : t === "light" ? "Light" : "Free";

export default function SubscriptionsAdmin() {
  const { user } = useAuth();
  const [rows, setRows] = useState<Row[]>([]);
  const [target, setTarget] = useState<Row | null>(null);
  const [days, setDays] = useState(30);
  const [tier, setTier] = useState<"light"|"pro">("pro");
  const [note, setNote] = useState("");

  const load = async () => {
    const { data: profs } = await supabase.from("profiles").select("user_id,email,display_name");
    const { data: subs } = await supabase.from("subscriptions").select("user_id,tier,expires_at,source");
    if (profs) {
      setRows(profs.map((p: any) => {
        const s = subs?.find((x: any) => x.user_id === p.user_id);
        return { ...p, tier: s?.tier ?? "free", expires_at: s?.expires_at ?? null, source: s?.source ?? "free" };
      }));
    }
  };
  useEffect(() => { load(); }, []);

  const gift = async () => {
    if (!target || !user) return;
    const expires_at = days > 0 ? new Date(Date.now() + days*86400_000).toISOString() : null;
    const { error } = await supabase.from("subscriptions").upsert({
      user_id: target.user_id, tier, source: "gift",
      expires_at, granted_by: user.id, note: note || `Geschenk: ${days} Tage ${tier}`,
    }, { onConflict: "user_id" });
    if (error) { toast.error(error.message); return; }
    toast.success(`${target.display_name || target.email} hat ${days} Tage ${tierBadge(tier)} erhalten`);
    setTarget(null); setNote(""); load();
  };

  const revoke = async (uid: string) => {
    if (!confirm("Abo entziehen?")) return;
    await supabase.from("subscriptions").upsert({ user_id: uid, tier: "free", source: "free", expires_at: null }, { onConflict: "user_id" });
    load();
  };

  return (
    <div>
      <p className="text-sm text-muted-foreground mb-3">Light- / Pro-Status für Nutzer setzen oder verschenken.</p>
      <div className="glass-strong rounded-2xl divide-y divide-border/50">
        {rows.map(r => {
          const active = (r.tier === "pro" || r.tier === "light") && (!r.expires_at || new Date(r.expires_at) > new Date());
          const color = r.tier === "pro" ? "text-fuchsia-400" : r.tier === "light" ? "text-sky-400" : "text-muted-foreground";
          return (
            <div key={r.user_id} className="p-4 flex items-center justify-between gap-3">
              <div className="min-w-0 flex-1">
                <div className="font-medium text-sm truncate">{r.display_name || r.email}</div>
                <div className="text-xs text-muted-foreground truncate">
                  {r.email} · <span className={color}>{tierBadge(r.tier)}{active && r.expires_at ? ` bis ${new Date(r.expires_at).toLocaleDateString("de-DE")}` : active ? " (unbefristet)" : ""}</span>
                  {r.source && r.source !== "free" && ` · ${r.source}`}
                </div>
              </div>
              <div className="flex gap-2">
                {active && <Button size="sm" variant="outline" onClick={() => revoke(r.user_id)}>Entziehen</Button>}
                <Button size="sm" onClick={() => { setTarget(r); setDays(30); setTier("pro"); }} className="bg-gradient-primary text-primary-foreground">
                  <Gift className="h-3.5 w-3.5 mr-1.5" />Verschenken
                </Button>
              </div>
            </div>
          );
        })}
      </div>

      <Dialog open={!!target} onOpenChange={(o) => !o && setTarget(null)}>
        <DialogContent className="glass-strong max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-primary" />Abo verschenken</DialogTitle>
          </DialogHeader>
          {target && (
            <div className="space-y-3">
              <p className="text-sm">An <strong>{target.display_name || target.email}</strong></p>
              <div><Label>Plan</Label>
                <Select value={tier} onValueChange={(v) => setTier(v as any)}>
                  <SelectTrigger className="mt-1.5"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="light">Light – Bilder + 150 Chats/Tag</SelectItem>
                    <SelectItem value="pro">Pro – Alles inkl. Voice/Musik/Video</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div><Label>Dauer (Tage, 0 = unbefristet)</Label>
                <Input type="number" min={0} value={days} onChange={(e) => setDays(parseInt(e.target.value)||0)} className="mt-1.5" />
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
