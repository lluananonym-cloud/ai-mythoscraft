import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import Logo from "@/components/Logo";
import { useAuth } from "@/hooks/useAuth";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";

const Auth = () => {
  const nav = useNavigate();
  const { user } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);

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

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 py-[max(3rem,calc(env(safe-area-inset-top)+2rem))] pb-[max(3rem,calc(env(safe-area-inset-bottom)+2rem))]">
      <div className="mb-8"><Logo size="lg" /></div>
      <div className="glass-strong rounded-2xl p-8 w-full max-w-md animate-fade-in">
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
