import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Crown, Sparkles, Loader2, Brain, Trash2, Plus, RefreshCcw, Wand2 } from "lucide-react";
import { toast } from "sonner";
import { Link } from "react-router-dom";

type Twin = {
  id: string;
  twin_name: string;
  avatar_emoji: string;
  style_summary: string | null;
  tone: string | null;
  vocabulary: string[];
  training_samples: string[];
  auto_reply_in_groups: boolean;
  is_trained: boolean;
  last_trained_at: string | null;
};

const EMOJIS = ["👤", "🧙", "🤖", "👾", "🦊", "🐉", "👻", "🎭", "🌟", "⚡", "🔥", "💀"];

export default function Twin() {
  const { user } = useAuth();
  const sub = useSubscription();
  const [twin, setTwin] = useState<Twin | null>(null);
  const [loading, setLoading] = useState(true);
  const [training, setTraining] = useState(false);
  const [newSample, setNewSample] = useState("");

  const load = async () => {
    if (!user) return;
    const { data } = await supabase.from("ai_twins").select("*").eq("user_id", user.id).maybeSingle();
    setTwin(data as any);
    setLoading(false);
  };
  useEffect(() => { load(); }, [user?.id]);

  const ensureTwin = async (): Promise<Twin> => {
    if (twin) return twin;
    const { data, error } = await supabase.from("ai_twins").insert({ user_id: user!.id }).select().single();
    if (error) throw error;
    setTwin(data as any);
    return data as any;
  };

  const update = async (patch: Partial<Twin>) => {
    if (!user) return;
    const t = await ensureTwin();
    const { data } = await supabase.from("ai_twins").update(patch).eq("id", t.id).select().single();
    if (data) setTwin(data as any);
  };

  const addSample = async () => {
    if (!newSample.trim()) return;
    const t = await ensureTwin();
    const samples = [...(t.training_samples || []), newSample.trim()].slice(-50);
    await update({ training_samples: samples });
    setNewSample("");
  };
  const removeSample = async (i: number) => {
    if (!twin) return;
    const samples = twin.training_samples.filter((_, idx) => idx !== i);
    await update({ training_samples: samples });
  };

  const train = async () => {
    setTraining(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      const res = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/functions/v1/twin-train`, {
        method: "POST",
        headers: {
          apikey: import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
          Authorization: `Bearer ${session?.access_token ?? ""}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ samples: twin?.training_samples || [] }),
      });
      const j = await res.json();
      if (!res.ok) {
        if (j.error === "not_enough_data") toast.error(j.message || "Zu wenig Daten");
        else if (j.error === "rate_limit") toast.error("Zu viele Anfragen — kurz warten");
        else if (j.error === "credits") toast.error("Keine AI-Credits mehr");
        else toast.error("Training fehlgeschlagen");
        return;
      }
      toast.success("Twin trainiert ✨");
      load();
    } catch (e: any) {
      toast.error(e?.message || "Fehler");
    } finally {
      setTraining(false);
    }
  };

  if (sub.loading || loading) return (
    <div className="min-h-screen"><TopNav /><div className="flex items-center justify-center py-32"><Loader2 className="h-8 w-8 animate-spin text-primary" /></div></div>
  );

  if (!sub.isPro) {
    return (
      <div className="min-h-screen">
        <TopNav />
        <main className="container max-w-2xl py-12 md:py-20">
          <div className="glass-strong rounded-3xl p-8 md:p-12 text-center">
            <div className="inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-primary mb-4 shadow-glow">
              <Crown className="h-8 w-8 text-primary-foreground" />
            </div>
            <h1 className="font-display text-3xl md:text-4xl font-bold mb-2">AI Twin</h1>
            <p className="text-muted-foreground mb-1">Dein digitaler Klon — trainiert auf <em>deinem</em> Schreibstil.</p>
            <p className="text-sm text-muted-foreground mb-6">Antwortet in deinem Namen, in deiner Tonalität, mit deinem Slang. <strong>Pro-Only.</strong></p>
            <Link to="/dashboard">
              <Button size="lg" className="bg-gradient-primary text-primary-foreground hover:opacity-90">
                <Crown className="h-4 w-4 mr-2" /> Auf Pro upgraden
              </Button>
            </Link>
            <Link to="/app" className="block mt-6 text-xs text-muted-foreground hover:text-foreground">← Zurück zum Chat</Link>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container max-w-3xl py-6 md:py-10 space-y-6">
        <div className="flex items-center gap-3">
          <div className="h-12 w-12 rounded-2xl bg-gradient-primary flex items-center justify-center shadow-glow">
            <Brain className="h-6 w-6 text-primary-foreground" />
          </div>
          <div>
            <h1 className="font-display text-2xl md:text-3xl font-bold">AI Twin</h1>
            <p className="text-sm text-muted-foreground">Dein digitaler Klon — trainiert auf deinem Stil.</p>
          </div>
          <span className="ml-auto inline-flex items-center gap-1 text-[10px] uppercase tracking-wider px-2 py-1 rounded-full bg-gradient-primary text-primary-foreground font-bold">
            <Crown className="h-3 w-3" /> Pro
          </span>
        </div>

        {/* Identity */}
        <section className="glass-strong rounded-3xl p-5 md:p-6 space-y-4">
          <h2 className="font-display text-lg font-semibold flex items-center gap-2"><Sparkles className="h-4 w-4 text-primary" />Identität</h2>
          <div className="grid grid-cols-[auto_1fr] gap-4 items-end">
            <div>
              <Label className="text-xs">Avatar</Label>
              <div className="flex flex-wrap gap-1.5 mt-1.5 max-w-[140px]">
                {EMOJIS.map(e => (
                  <button key={e} type="button" onClick={() => update({ avatar_emoji: e })}
                    className={`h-9 w-9 rounded-lg text-lg transition-all ${twin?.avatar_emoji === e ? "bg-primary/20 ring-2 ring-primary" : "bg-secondary/50 hover:bg-secondary"}`}>
                    {e}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <Label className="text-xs">Name</Label>
              <Input value={twin?.twin_name ?? "Mein Twin"} onChange={(e) => setTwin(t => t ? { ...t, twin_name: e.target.value } : t)}
                onBlur={(e) => update({ twin_name: e.target.value })}
                className="mt-1.5 bg-input/50" maxLength={40} />
            </div>
          </div>
          <div className="flex items-center justify-between p-3 rounded-xl bg-secondary/30">
            <div>
              <div className="text-sm font-medium">Auto-Antwort in Gruppen-Chats</div>
              <div className="text-xs text-muted-foreground">Twin antwortet automatisch wenn du @mentioned wirst</div>
            </div>
            <Switch checked={twin?.auto_reply_in_groups ?? false} onCheckedChange={(v) => update({ auto_reply_in_groups: v })} />
          </div>
        </section>

        {/* Training */}
        <section className="glass-strong rounded-3xl p-5 md:p-6 space-y-4">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <h2 className="font-display text-lg font-semibold flex items-center gap-2"><Wand2 className="h-4 w-4 text-primary" />Training</h2>
            <Button onClick={train} disabled={training} className="bg-gradient-primary text-primary-foreground">
              {training ? <Loader2 className="h-4 w-4 animate-spin mr-1.5" /> : <RefreshCcw className="h-4 w-4 mr-1.5" />}
              {twin?.is_trained ? "Neu trainieren" : "Twin trainieren"}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            Das Training analysiert deine letzten <strong>200 Chat-Nachrichten</strong> + manuelle Beispiele und erstellt ein Style-Profil.
            {twin?.last_trained_at && ` · Zuletzt: ${new Date(twin.last_trained_at).toLocaleString("de-DE")}`}
          </p>

          {twin?.is_trained && twin.style_summary && (
            <div className="rounded-xl bg-gradient-to-br from-primary/10 to-accent/10 border border-primary/20 p-4 space-y-3">
              <div>
                <div className="text-[10px] uppercase tracking-wider text-primary font-bold mb-1">Style</div>
                <p className="text-sm italic">"{twin.style_summary}"</p>
              </div>
              {twin.tone && (<div className="text-xs"><span className="text-muted-foreground">Tonalität: </span><span className="font-medium">{twin.tone}</span></div>)}
              {twin.vocabulary?.length > 0 && (
                <div>
                  <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5">Typische Wörter</div>
                  <div className="flex flex-wrap gap-1.5">
                    {twin.vocabulary.map((w, i) => (<span key={i} className="text-[11px] px-2 py-0.5 rounded-full bg-primary/20 text-primary-foreground">{w}</span>))}
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="space-y-2">
            <Label className="text-xs">Manuelle Beispiele (optional)</Label>
            <div className="flex gap-2">
              <Textarea value={newSample} onChange={(e) => setNewSample(e.target.value)} placeholder="Schreib eine typische Nachricht…" className="bg-input/50 min-h-[60px]" maxLength={500} />
              <Button onClick={addSample} disabled={!newSample.trim()} variant="outline" className="self-start"><Plus className="h-4 w-4" /></Button>
            </div>
            {twin && twin.training_samples.length > 0 && (
              <div className="space-y-1.5 mt-2">
                {twin.training_samples.map((s, i) => (
                  <div key={i} className="flex items-start gap-2 text-xs bg-secondary/30 rounded-lg p-2">
                    <span className="flex-1 italic">"{s}"</span>
                    <button onClick={() => removeSample(i)} className="text-muted-foreground hover:text-destructive shrink-0"><Trash2 className="h-3 w-3" /></button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>

        {twin?.is_trained && (
          <section className="glass-strong rounded-3xl p-5 md:p-6">
            <h2 className="font-display text-lg font-semibold mb-2">So nutzt du deinen Twin</h2>
            <ul className="text-sm text-muted-foreground space-y-1.5 list-disc list-inside">
              <li>Im Chat: <code className="text-primary bg-primary/10 px-1.5 py-0.5 rounded">/twin &lt;was sagen?&gt;</code> — Antwort in deinem Stil</li>
              <li>In Gruppen-Chats: Twin springt ein wenn du @mentioned wirst (wenn aktiviert)</li>
              <li>Style verbessert sich automatisch je mehr du chattest — einfach neu trainieren</li>
            </ul>
          </section>
        )}
      </main>
    </div>
  );
}
