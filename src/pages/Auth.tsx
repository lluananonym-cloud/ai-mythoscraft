import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LogoMark } from "@/components/Logo";
import { useAuth } from "@/hooks/useAuth";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";

// Simple password strength helper (based on length only)
const getPasswordStrength = (pwd: string) => {
  if (pwd.length >= 12) return { label: "Stark", color: "text-green-500" };
  if (pwd.length >= 8) return { label: "Mittel", color: "text-yellow-500" };
  if (pwd.length > 0) return { label: "Schwach", color: "text-red-500" };
  return { label: "", color: "" };
};

const Auth = () => {
  const nav = useNavigate();
  const { user } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (user) nav("/app");
  }, [user, nav]);

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    setLoading(false);
    if (error) {
      toast.error(error.message);
    } else {
      toast.success("Willkommen zurück!");
      nav("/app");
    }
  };

  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const { error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        emailRedirectTo: window.location.origin + "/app",
        data: { display_name: name },
      },
    });
    setLoading(false);
    if (error) {
      toast.error(error.message);
    } else {
      toast.success("Account erstellt! Du wirst eingeloggt...");
      nav("/app");
    }
  };

  const pwdStrength = getPasswordStrength(password);

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 py-[max(3rem,calc(env(safe-area-inset-top)+2rem))] pb-[max(3rem,calc(env(safe-area-inset-bottom)+2rem))]">
      <div className="mb-6 flex flex-col items-center gap-3">
        <LogoMark size="lg" className="h-24 w-24 drop-shadow-[0_0_42px_hsl(var(--primary)/0.35)]" />
        <div className="font-display text-3xl font-bold tracking-tight">
          <span className="gradient-text">Mythos</span> AI
        </div>
      </div>
      <div className="glass-strong rounded-2xl p-8 w-full max-w-md animate-fade-in">
        <Tabs defaultValue="signin" className="w-full">
          <TabsList className="grid w-full grid-cols-2 mb-6 bg-secondary/50">
            <TabsTrigger value="signin">Login</TabsTrigger>
            <TabsTrigger value="signup">Registrieren</TabsTrigger>
          </TabsList>
          <TabsContent value="signin">
            <form onSubmit={handleSignIn} className="space-y-4">
              <div>
                <Label htmlFor="signin-email">Email</Label>
                <Input
                  id="signin-email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="bg-input/50 mt-1.5"
                />
              </div>
              <div>
                <Label htmlFor="signin-password">Passwort</Label>
                <Input
                  id="signin-password"
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="bg-input/50 mt-1.5"
                />
                <div className="mt-2 text-sm">
                  <Link to="/reset-password" className="hover:underline text-primary" aria-label="Passwort vergessen?">Passwort vergessen?</Link>
                </div>
              </div>
              <Button
                type="submit"
                disabled={loading}
                className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90 mt-2"
              >
                {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "Login"}
              </Button>
            </form>
          </TabsContent>
          <TabsContent value="signup">
            <form onSubmit={handleSignUp} className="space-y-4">
              <div>
                <Label htmlFor="signup-name">Name (optional)</Label>
                <Input
                  id="signup-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="bg-input/50 mt-1.5"
                  placeholder="Steve"
                />
              </div>
              <div>
                <Label htmlFor="signup-email">Email</Label>
                <Input
                  id="signup-email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="bg-input/50 mt-1.5"
                />
              </div>
              <div>
                <Label htmlFor="signup-password">Passwort</Label>
                <Input
                  id="signup-password"
                  type="password"
                  required
                  minLength={6}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="bg-input/50 mt-1.5"
                />
                {pwdStrength.label && (
                  <p className={`mt-1 text-sm ${pwdStrength.color}`}>Stärke: {pwdStrength.label}</p>
                )}
              </div>
              <Button
                type="submit"
                disabled={loading}
                className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90 mt-2"
              >
                {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "Account erstellen"}
              </Button>
            </form>
          </TabsContent>
        </Tabs>
        {/* Data‑Processing notice */}
        <p className="mt-4 text-xs text-muted-foreground">
          Durch die Registrierung stimmst du der Verarbeitung deiner Daten gemäß unserer <Link to="/datenschutz" className="hover:underline">Datenschutzerklärung</Link> und den <Link to="/nutzungsbedingungen" className="hover:underline">Nutzungsbedingungen</Link> zu.
        </p>
      </div>
      <Button
        variant="ghost"
        size="sm"
        className="mt-6 text-muted-foreground"
        onClick={() => nav("/")}
      >
        ← Zurück
      </Button>
      <footer className="mt-8 text-center text-sm text-muted-foreground">
        <nav className="flex flex-wrap justify-center gap-2 mt-2">
          <Link to="/impressum" className="hover:underline">Impressum</Link>
          <Link to="/datenschutz" className="hover:underline">Datenschutz</Link>
          <Link to="/nutzungsbedingungen" className="hover:underline">Nutzungsbedingungen</Link>
          <Link to="/cookie" className="hover:underline">Cookie‑Hinweis</Link>
          <Link to="/ki-regeln" className="hover:underline">KI‑Inhalts‑Regeln</Link>
        </nav>
      </footer>
    </div>
  );
};

export default Auth;
