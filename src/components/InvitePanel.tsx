import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import { inviteLink } from "@/lib/invite";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Check, Copy, Gift, Loader2, Users } from "lucide-react";
import { toast } from "sonner";

const InvitePanel = () => {
  const { user } = useAuth();
  const { isPro, loading: subLoading } = useSubscription();
  const [code, setCode] = useState<string | null>(null);
  const [count, setCount] = useState(0);
  const [bonusDays, setBonusDays] = useState(0);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!user || !isPro) return;
    (async () => {
      const { data, error } = await (supabase.rpc as any)("get_invite_code");
      if (error) { toast.error("Einladungslink konnte nicht geladen werden"); return; }
      setCode(data as string);
      const { data: refs } = await (supabase.from as any)("referrals")
        .select("bonus_days").eq("inviter_id", user.id);
      const list = (refs ?? []) as { bonus_days: number }[];
      setCount(list.length);
      setBonusDays(list.reduce((s, r) => s + (r.bonus_days || 0), 0));
    })();
  }, [user?.id, isPro]);

  const link = code ? inviteLink(code) : "";

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      toast.success("Link kopiert");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Kopieren nicht möglich");
    }
  };

  if (subLoading) {
    return <div className="glass-strong rounded-2xl p-8 flex justify-center"><Loader2 className="h-5 w-5 animate-spin" /></div>;
  }

  if (!isPro) {
    return (
      <div className="glass-strong rounded-2xl p-6 md:p-8 text-center space-y-3">
        <Gift className="h-8 w-8 mx-auto text-primary" />
        <h2 className="font-display text-xl font-semibold">Freunde einladen</h2>
        <p className="text-sm text-muted-foreground">
          Mit Pro kannst du Freunde einladen. Sie bekommen 7 Tage Light, du bekommst 7 Tage Pro gratis pro Freund.
        </p>
        <Link to="/app"><Button className="bg-gradient-primary text-primary-foreground hover:opacity-90">Pro holen</Button></Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="glass-strong rounded-2xl p-6 md:p-8 space-y-4">
        <div className="flex items-center gap-2">
          <Gift className="h-5 w-5 text-primary" />
          <h2 className="font-display text-xl font-semibold">Freunde einladen</h2>
        </div>
        <p className="text-sm text-muted-foreground">
          Teile deinen Link. Wer sich darüber neu registriert, bekommt <b>7 Tage Light</b> gratis.
          Du bekommst dafür <b>7 Tage Pro</b> gratis dazu (bis zu 10 Freunde in 30 Tagen).
        </p>
        <div className="flex gap-2">
          <Input readOnly value={link || "Lädt…"} className="font-mono text-xs bg-input/50" onFocus={(e) => e.target.select()} />
          <Button onClick={copy} disabled={!code} className="bg-gradient-primary text-primary-foreground hover:opacity-90 shrink-0">
            {copied ? <Check className="h-4 w-4 mr-1.5" /> : <Copy className="h-4 w-4 mr-1.5" />}
            Kopieren
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 md:gap-4">
        <div className="glass rounded-2xl p-4 md:p-5">
          <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2"><Users className="h-3.5 w-3.5" />Eingeladen</div>
          <div className="font-display text-2xl md:text-3xl font-bold">{count}</div>
        </div>
        <div className="glass rounded-2xl p-4 md:p-5">
          <div className="flex items-center gap-2 text-muted-foreground text-xs mb-2"><Gift className="h-3.5 w-3.5" />Bonus verdient</div>
          <div className="font-display text-2xl md:text-3xl font-bold">{bonusDays} Tage Pro</div>
        </div>
      </div>
    </div>
  );
};

export default InvitePanel;
