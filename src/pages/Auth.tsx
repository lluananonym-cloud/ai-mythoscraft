import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { lovable } from "@/integrations/lovable";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LogoMark } from "@/components/Logo";
import { useAuth } from "@/hooks/useAuth";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";

const GoogleIcon = () => (
  <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden>
    <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.4-1.7 4.1-5.5 4.1-3.3 0-6-2.7-6-6.1s2.7-6.1 6-6.1c1.9 0 3.1.8 3.8 1.5l2.6-2.5C16.7 3.4 14.6 2.5 12 2.5 6.8 2.5 2.6 6.7 2.6 12s4.2 9.5 9.4 9.5c5.4 0 9-3.8 9-9.2 0-.6-.06-1.1-.15-1.6H12z"/>
  </svg>
);

const Auth = () => {
  const nav = useNavigate();
  const { user } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);

  useEffect(() => { if (user) nav("/app"); }, [user, nav]);

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    setLoading(false);
    if (error) toast.error(error.message);
    else { toast.success("Willkommen zurück!"); nav("/app"); }
  };

  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const { error } = await supabase.auth.signUp({
      email, password,
      options: { emailRedirectTo: window.location.origin + "/app", data: { display_name: name } }
    });
    setLoading(false);
    if (error) toast.error(error.message);
    else { toast.success("Account erstellt! Du wirst eingeloggt..."); nav("/app"); }
  };

  const handleGoogle = async () => {
    setGoogleLoading(true);
    try {
      const result = await lovable.auth.signInWithOAuth("google", { redirect_uri: window.location.origin + "/app" });
      if (result.error) { toast.error("Google-Login fehlgeschlagen"); setGoogleLoading(false); return; }
      if (result.redirected) return;
      nav("/app");
    } catch {
      toast.error("Google-Login fehlgeschlagen");
      setGoogleLoading(false);
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
        <Button
          type="button"
          variant="outline"
          onClick={handleGoogle}
          disabled={googleLoading}
          className="w-full h-11 bg-white text-black hover:bg-white/90 border-white/20 mb-4 font-medium"
        >
          {googleLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : (<><GoogleIcon /><span className="ml-2">Weiter mit Google</span></>)}
        </Button>
        <div className="flex items-center gap-3 my-4">
          <div className="h-px bg-border flex-1" />
          <span className="text-[10px] uppercase tracking-wider text-muted-foreground">oder Email</span>
          <div className="h-px bg-border flex-1" />
        </div>
        <Tabs defaultValue="signin" className="w-full">
          <TabsList className="grid w-full grid-cols-2 mb-6 bg-secondary/50">
            <TabsTrigger value="signin">Login</TabsTrigger>
            <TabsTrigger value="signup">Registrieren</TabsTrigger>
          </TabsList>
          <TabsContent value="signin">
            <form onSubmit={handleSignIn} className="space-y-4">
              <div><Label>Email</Label><Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="bg-input/50 mt-1.5" /></div>
              <div><Label>Passwort</Label><Input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} className="bg-input/50 mt-1.5" /></div>
              <Button type="submit" disabled={loading} className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90 mt-2">
                {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "Login"}
              </Button>
            </form>
          </TabsContent>
          <TabsContent value="signup">
            <form onSubmit={handleSignUp} className="space-y-4">
              <div><Label>Name (optional)</Label><Input value={name} onChange={(e) => setName(e.target.value)} className="bg-input/50 mt-1.5" placeholder="Steve" /></div>
              <div><Label>Email</Label><Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="bg-input/50 mt-1.5" /></div>
              <div><Label>Passwort</Label><Input type="password" required minLength={6} value={password} onChange={(e) => setPassword(e.target.value)} className="bg-input/50 mt-1.5" /></div>
              <Button type="submit" disabled={loading} className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90 mt-2">
                {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "Account erstellen"}
              </Button>
            </form>
          </TabsContent>
        </Tabs>
      </div>
      <Button variant="ghost" size="sm" className="mt-6 text-muted-foreground" onClick={() => nav("/")}>← Zurück</Button>
    </div>
  );
};
export default Auth;
