import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LogoMark } from "@/components/Logo";
import { toast } from "sonner";
import { Loader2, MailCheck } from "lucide-react";

// Der Link aus der Reset-Mail führt hierher zurück (#...type=recovery).
// Ohne diesen Link: Mail anfordern. Mit Link: neues Passwort setzen.
const isRecoveryLink = () =>
  /type=recovery/.test(window.location.hash) || /type=recovery/.test(window.location.search);

const ResetPassword = () => {
  const nav = useNavigate();
  const [recovery, setRecovery] = useState(isRecoveryLink);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  useEffect(() => {
    const { data: sub } = supabase.auth.onAuthStateChange((event) => {
      if (event === "PASSWORD_RECOVERY") setRecovery(true);
    });
    return () => sub.subscription.unsubscribe();
  }, []);

  const requestMail = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const { error } = await supabase.auth.resetPasswordForEmail(email.trim(), {
      redirectTo: window.location.origin + "/reset-password",
    });
    setLoading(false);
    if (error) toast.error(error.message);
    else setSent(true);
  };

  const setNewPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== password2) {
      toast.error("Die Passwörter stimmen nicht überein.");
      return;
    }
    setLoading(true);
    const { error } = await supabase.auth.updateUser({ password });
    setLoading(false);
    if (error) {
      toast.error(error.message);
    } else {
      toast.success("Passwort geändert! Du bist eingeloggt.");
      nav("/app");
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 py-[max(3rem,calc(env(safe-area-inset-top)+2rem))] pb-[max(3rem,calc(env(safe-area-inset-bottom)+2rem))]">
      <div className="mb-6 flex flex-col items-center gap-3">
        <LogoMark size="lg" className="h-24 w-24 drop-shadow-[0_0_42px_hsl(var(--primary)/0.35)]" />
        <div className="font-display text-3xl font-bold tracking-tight">
          <span className="gradient-text">Mythos</span> AI
        </div>
      </div>
      <div className="glass-strong rounded-2xl p-8 w-full max-w-md animate-fade-in">
        {recovery ? (
          <form onSubmit={setNewPassword} className="space-y-4">
            <h1 className="text-xl font-semibold">Neues Passwort setzen</h1>
            <div>
              <Label htmlFor="new-password">Neues Passwort</Label>
              <Input
                id="new-password"
                type="password"
                required
                minLength={6}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="bg-input/50 mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="new-password2">Passwort wiederholen</Label>
              <Input
                id="new-password2"
                type="password"
                required
                minLength={6}
                autoComplete="new-password"
                value={password2}
                onChange={(e) => setPassword2(e.target.value)}
                className="bg-input/50 mt-1.5"
              />
            </div>
            <Button
              type="submit"
              disabled={loading}
              className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90 mt-2"
            >
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "Passwort speichern"}
            </Button>
          </form>
        ) : sent ? (
          <div className="text-center space-y-3">
            <MailCheck className="h-10 w-10 mx-auto text-primary" />
            <h1 className="text-xl font-semibold">Mail ist unterwegs</h1>
            <p className="text-sm text-muted-foreground">
              Wenn es für <b>{email}</b> einen Account gibt, bekommst du gleich einen Link zum Zurücksetzen. Schau auch im Spam-Ordner nach.
            </p>
            <Button variant="outline" className="w-full" onClick={() => setSent(false)}>
              Andere Email eingeben
            </Button>
          </div>
        ) : (
          <form onSubmit={requestMail} className="space-y-4">
            <h1 className="text-xl font-semibold">Passwort vergessen?</h1>
            <p className="text-sm text-muted-foreground">
              Gib deine Email ein. Wir schicken dir einen Link, mit dem du ein neues Passwort setzen kannst.
            </p>
            <div>
              <Label htmlFor="reset-email">Email</Label>
              <Input
                id="reset-email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="bg-input/50 mt-1.5"
              />
            </div>
            <Button
              type="submit"
              disabled={loading}
              className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90 mt-2"
            >
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "Link schicken"}
            </Button>
          </form>
        )}
        <div className="mt-4 text-sm text-center">
          <Link to="/auth" className="hover:underline text-primary">Zurück zum Login</Link>
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
