import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { LogoMark } from "@/components/Logo";
import MarkdownMessage from "@/components/MarkdownMessage";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { adMarkdown } from "@/lib/mythosAds";
import { toast } from "sonner";

type AdRow = { id: string; product: string; link: string; ad_text: string; budget: string; contact: string; status: string; weight: number; ai_note: string | null };

const STATUS: Record<string, string> = { pending: "Wartet auf Prüfung", approved: "Angenommen – läuft", rejected: "Abgelehnt", paused: "Pausiert" };

export default function WerbungVorschau() {
  const { id } = useParams();
  const { isAdmin, loading } = useAuth();
  const [ad, setAd] = useState<AdRow | null>(null);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  const call = async (action: string) => {
    const { data, error } = await supabase.functions.invoke("ad-review", { body: { action, id } });
    if (error || data?.error) throw new Error(data?.error || "Fehler");
    return data;
  };
  const load = () => call("get").then((d) => setAd(d.ad)).catch((e) => setErr(e.message));
  useEffect(() => { document.title = "Werbe-Vorschau – Mythos AI"; if (isAdmin) load(); }, [isAdmin, id]);

  const act = async (action: "approve" | "reject" | "pause") => {
    setBusy(true);
    try { const d = await call(action); toast.success(action === "approve" ? `Angenommen · Häufigkeit ${d.weight}/10` : action === "reject" ? "Abgelehnt" : "Pausiert"); await load(); }
    catch (e) { toast.error((e as Error).message); } finally { setBusy(false); }
  };

  if (!loading && !isAdmin) return <div className="min-h-screen grid place-items-center p-6 text-center text-muted-foreground">Die Vorschau ist nur für Admins sichtbar.</div>;

  return (
    <div className="min-h-screen flex flex-col items-center px-4 py-8 gap-5">
      <div className="w-full max-w-xl flex items-center gap-3">
        <Link to="/"><LogoMark size="sm" /></Link>
        <div><h1 className="font-display text-xl font-bold">Werbe-Vorschau</h1><p className="text-xs text-muted-foreground">So sieht die Werbung im Chat aus</p></div>
      </div>
      {err && <p className="text-destructive text-sm">{err}</p>}
      {ad && (
        <>
          <div className="w-full max-w-xl glass rounded-2xl p-4 flex flex-col gap-3">
            <div className="self-end rounded-2xl rounded-br-sm bg-primary text-primary-foreground px-3 py-2 text-sm">Wie komme ich auf den Server?</div>
            <div className="text-sm text-foreground">Verbinde dich mit <b>mythoscraft.online</b> in Minecraft Java – viel Spaß!</div>
            <div className="text-sm"><MarkdownMessage content={adMarkdown({ ...ad }, window.location.origin)} /></div>
          </div>
          <div className="w-full max-w-xl glass rounded-2xl p-4 text-sm space-y-1">
            <p><b>Status:</b> {STATUS[ad.status] || ad.status}{ad.status === "approved" && ` · Häufigkeit ${ad.weight}/10`}</p>
            <p><b>Budget (monatlich):</b> {ad.budget}</p>
            <p><b>Kontakt:</b> {ad.contact}</p>
            {ad.ai_note && <p className="text-muted-foreground"><b>KI-Analyse:</b> {ad.ai_note}</p>}
            <p className="text-xs text-muted-foreground pt-2">Die Bezahlung regelst du direkt mit dem Kunden. Nimm die Werbung erst an, wenn bezahlt wurde – die KI legt dann anhand des Budgets fest, wie oft sie erscheint.</p>
          </div>
          <div className="flex gap-2 flex-wrap justify-center">
            {ad.status !== "approved" && <Button disabled={busy} onClick={() => act("approve")}>Annehmen</Button>}
            {ad.status === "approved" && <Button variant="secondary" disabled={busy} onClick={() => act("pause")}>Pausieren</Button>}
            {ad.status !== "rejected" && <Button variant="outline" disabled={busy} onClick={() => act("reject")}>Ablehnen</Button>}
          </div>
        </>
      )}
    </div>
  );
}
