import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { CheckCircle2, Loader2, MonitorSmartphone } from "lucide-react";

const CliAuth = () => {
  const { user, loading } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const code = (params.get("code") || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 16);
  const [state, setState] = useState<"idle" | "busy" | "done">("idle");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && !user) nav(`/auth?next=${encodeURIComponent(`/cli-auth?code=${code}`)}`, { replace: true });
  }, [loading, user, nav, code]);

  const approve = async () => {
    setState("busy"); setError("");
    const { data, error: e } = await supabase.functions.invoke("cli-auth", { body: { action: "approve", code } });
    if (e || data?.error) {
      let msg = data?.error as string | undefined;
      const ctx = (e as { context?: Response } | null)?.context;
      if (!msg && ctx) msg = (await ctx.json().catch(() => null))?.error;
      setError(msg || "Bestätigung fehlgeschlagen.");
      setState("idle");
      return;
    }
    setState("done");
  };

  if (loading || !user) {
    return <div className="min-h-screen flex items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary" /></div>;
  }

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-10 md:py-16 max-w-md">
        <div className="glass-strong rounded-2xl p-6 text-center space-y-5">
          <div className="inline-flex h-14 w-14 rounded-2xl bg-white/5 border border-white/10 items-center justify-center">
            {state === "done" ? <CheckCircle2 className="h-7 w-7 text-green-500" /> : <MonitorSmartphone className="h-7 w-7" />}
          </div>
          {state === "done" ? (
            <>
              <h1 className="font-display text-2xl font-bold">Angemeldet!</h1>
              <p className="text-sm text-muted-foreground">Mythos Code ist jetzt mit deinem Konto verbunden. Du kannst dieses Fenster schließen und zurück zur App bzw. zum Terminal wechseln.</p>
            </>
          ) : !code ? (
            <>
              <h1 className="font-display text-2xl font-bold">Kein Code</h1>
              <p className="text-sm text-muted-foreground">Starte die Anmeldung in der Mythos Code App („Mit MythosAI anmelden“) oder im Terminal mit <code className="font-mono">mythos login</code>.</p>
            </>
          ) : (
            <>
              <h1 className="font-display text-2xl font-bold">Mythos Code anmelden</h1>
              <p className="text-sm text-muted-foreground">
                Angemeldet als <span className="text-foreground">{user.email}</span>. Prüfe, dass der Code mit dem in der App übereinstimmt:
              </p>
              <div className="font-mono text-2xl tracking-[0.3em] py-2">{code}</div>
              <Button onClick={approve} disabled={state === "busy"} className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90">
                {state === "busy" ? <Loader2 className="h-4 w-4 animate-spin mr-1.5" /> : null}
                Authentifizieren
              </Button>
              {error && <p className="text-sm text-destructive">{error}</p>}
            </>
          )}
        </div>
      </main>
    </div>
  );
};

export default CliAuth;
