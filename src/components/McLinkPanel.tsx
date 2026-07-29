import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { Gamepad2, Link2Off } from "lucide-react";

type Linked = { id: string; mc_name: string; mc_uuid: string; linked_at: string };

export default function McLinkPanel() {
  const { user } = useAuth();
  const [linked, setLinked] = useState<Linked | null>(null);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);

  const load = async () => {
    if (!user) return;
    const { data } = await supabase.from("mc_players").select("id,mc_name,mc_uuid,linked_at")
      .eq("user_id", user.id).maybeSingle();
    setLinked((data as any) || null);
  };
  useEffect(() => { load(); }, [user]);

  const claim = async () => {
    if (!user || code.length !== 6) return toast.error("Der Code hat 6 Ziffern.");
    setBusy(true);
    // Find an unclaimed, unexpired code
    const { data: row } = await supabase.from("mc_link_codes")
      .select("id,mc_uuid,mc_name,expires_at,claimed_by")
      .eq("code", code).maybeSingle();
    if (!row) { setBusy(false); return toast.error("Code nicht gefunden."); }
    if (row.claimed_by) { setBusy(false); return toast.error("Code wurde schon eingelöst."); }
    if (new Date(row.expires_at) < new Date()) { setBusy(false); return toast.error("Code abgelaufen — /ai im Spiel für neuen Code."); }
    const { error: upErr } = await supabase.from("mc_link_codes").update({
      claimed_by: user.id, claimed_at: new Date().toISOString(),
    }).eq("id", row.id);
    if (upErr) { setBusy(false); return toast.error(upErr.message); }
    const { error: insErr } = await supabase.from("mc_players").insert({
      user_id: user.id, mc_uuid: row.mc_uuid, mc_name: row.mc_name,
    });
    setBusy(false);
    if (insErr) return toast.error(insErr.message);
    toast.success(`Verknüpft mit ${row.mc_name}!`);
    setCode("");
    load();
  };

  const unlink = async () => {
    if (!linked) return;
    if (!confirm(`Wirklich Verknüpfung zu ${linked.mc_name} entfernen?`)) return;
    await supabase.from("mc_players").delete().eq("id", linked.id);
    toast.success("Entkoppelt");
    load();
  };

  return (
    <div className="rounded-xl border border-white/10 bg-background/40 p-4 space-y-3">
      <div className="flex items-center gap-2">
        <Gamepad2 className="h-4 w-4 text-primary" />
        <div className="font-display font-semibold">Minecraft verknüpfen</div>
      </div>
      {linked ? (
        <div className="flex items-center justify-between gap-2">
          <div className="text-sm">
            Verknüpft mit <span className="font-semibold">{linked.mc_name}</span>
            <div className="text-xs text-muted-foreground">seit {new Date(linked.linked_at).toLocaleDateString()}</div>
          </div>
          <Button size="sm" variant="outline" onClick={unlink}>
            <Link2Off className="h-4 w-4 mr-1" /> Entkoppeln
          </Button>
        </div>
      ) : (
        <>
          <p className="text-xs text-muted-foreground">
            Schreib <code>/ai</code> auf einem Server mit installiertem MythosAI-Plugin. Du bekommst einen 6-stelligen Code — hier eingeben.
          </p>
          <div className="flex gap-2">
            <Label htmlFor="mc-code" className="sr-only">Code</Label>
            <Input id="mc-code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
              placeholder="123456" inputMode="numeric" maxLength={6} className="font-mono tracking-widest text-center" />
            <Button onClick={claim} disabled={busy || code.length !== 6}>Verknüpfen</Button>
          </div>
        </>
      )}
    </div>
  );
}
