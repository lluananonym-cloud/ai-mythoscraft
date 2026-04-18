import { useEffect, useState } from "react";
import { useNavigate, useSearchParams, Link } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Ticket, Sparkles, ArrowRight } from "lucide-react";
import { toast } from "sonner";

const Redeem = () => {
  const { user, loading } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [code, setCode] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<any>(null);

  useEffect(() => {
    const c = params.get("code");
    if (c) setCode(c.toUpperCase());
  }, [params]);

  useEffect(() => {
    if (!loading && !user) {
      nav(`/auth?next=/redeem?code=${encodeURIComponent(code || params.get("code") || "")}`);
    }
  }, [loading, user]);

  const redeem = async () => {
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;
    setSubmitting(true);
    const { data, error } = await supabase.functions.invoke("redeem-code", { body: { code: trimmed } });
    setSubmitting(false);
    if (error || data?.error) {
      toast.error(data?.error || "Code konnte nicht eingelöst werden");
      return;
    }
    setResult(data);
    toast.success("Code erfolgreich eingelöst!");
  };

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-10 md:py-16 max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex h-14 w-14 rounded-2xl bg-gradient-primary/20 border border-primary/30 items-center justify-center mb-4 glow-primary">
            <Ticket className="h-7 w-7 text-primary" />
          </div>
          <h1 className="font-display text-3xl font-bold mb-2">Boost Code einlösen</h1>
          <p className="text-sm text-muted-foreground">
            Codes erhältst du im offiziellen Mythoscraft Discord.
          </p>
        </div>

        {result ? (
          <div className="glass-strong rounded-2xl p-6 text-center space-y-4">
            <div className="inline-flex h-12 w-12 rounded-full bg-accent/20 items-center justify-center">
              <Sparkles className="h-6 w-6 text-accent" />
            </div>
            <h2 className="font-display text-xl font-bold">Boost aktiviert!</h2>
            <p className="text-sm text-muted-foreground">
              {result.mode === "permanent" && `Deine API-Keys haben jetzt dauerhaft ${result.daily_limit} Requests/Tag.`}
              {result.mode === "temporary" && `Deine Keys haben ${result.daily_limit}/Tag bis ${new Date(result.expires_at).toLocaleDateString()}.`}
              {result.mode === "oneshot" && `Du hast +${result.bonus_remaining} Bonus-Requests erhalten.`}
            </p>
            <Link to="/dashboard">
              <Button className="w-full bg-gradient-primary text-primary-foreground">
                Zum Dashboard <ArrowRight className="h-4 w-4 ml-1.5" />
              </Button>
            </Link>
          </div>
        ) : (
          <div className="glass-strong rounded-2xl p-6 space-y-4">
            <div>
              <Label>Code</Label>
              <Input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="DISCORDVIP"
                className="mt-1.5 font-mono text-center text-lg tracking-widest"
                maxLength={64}
                onKeyDown={(e) => e.key === "Enter" && redeem()}
              />
            </div>
            <Button
              onClick={redeem}
              disabled={!code.trim() || submitting}
              className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90"
            >
              {submitting ? "Einlösen..." : "Einlösen"}
            </Button>
            <a
              href="https://discord.gg/MewpPph3aw"
              target="_blank"
              rel="noopener noreferrer"
              className="block text-center text-xs text-muted-foreground hover:text-primary transition-colors"
            >
              Noch keinen Code? → Mythoscraft Discord beitreten
            </a>
          </div>
        )}
      </main>
    </div>
  );
};

export default Redeem;
