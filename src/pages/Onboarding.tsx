import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Loader2, Sparkles, ArrowRight, ArrowLeft, Check } from "lucide-react";
import { toast } from "sonner";
import { LogoMark } from "@/components/Logo";

const INTEREST_OPTIONS = [
  "Survival", "Creative", "PvP", "Redstone", "Building", "Mini-Games",
  "Skyblock", "Modded", "Adventure", "Speedrun", "Coding/Plugins", "Streaming",
];

const PLAYSTYLES = [
  { v: "casual", l: "Chill & Casual", e: "🌿" },
  { v: "competitive", l: "Sweat & Competitive", e: "⚔️" },
  { v: "social", l: "Social Butterfly", e: "🎉" },
  { v: "explorer", l: "Explorer & Builder", e: "🗺️" },
];

export default function Onboarding() {
  const { user, profile, refreshProfile } = useAuth();
  const nav = useNavigate();
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);

  const [name, setName] = useState(profile?.display_name ?? "");
  const [mcUsername, setMcUsername] = useState(profile?.mc_username ?? "");
  const [age, setAge] = useState<number | "">("");
  const [interests, setInterests] = useState<string[]>([]);
  const [playstyle, setPlaystyle] = useState("");
  const [favoriteBlock, setFavoriteBlock] = useState("");
  const [about, setAbout] = useState("");
  const [referral, setReferral] = useState("");

  useEffect(() => {
    if (profile?.onboarded) nav("/app", { replace: true });
  }, [profile, nav]);

  useEffect(() => {
    if (profile) {
      setName(profile.display_name ?? "");
      setMcUsername(profile.mc_username ?? "");
    }
  }, [profile]);

  const toggle = (v: string) =>
    setInterests(prev => prev.includes(v) ? prev.filter(x => x !== v) : [...prev, v]);

  const save = async () => {
    if (!user) return;
    setSaving(true);
    const { error } = await supabase.from("profiles").update({
      display_name: name.trim() || null,
      mc_username: mcUsername.trim() || null,
      age: age === "" ? null : Number(age),
      interests, playstyle: playstyle || null,
      favorite_block: favoriteBlock.trim() || null,
      about: about.trim() || null,
      referral: referral.trim() || null,
      onboarded: true,
    }).eq("user_id", user.id);
    setSaving(false);
    if (error) { toast.error(error.message); return; }
    toast.success("Willkommen bei Mythos AI ✨");
    await refreshProfile();
    nav("/app", { replace: true });
  };

  const steps = [
    {
      title: "Wer bist du?",
      sub: "Ein paar Basics, damit die AI dich kennt.",
      body: (
        <div className="space-y-4">
          <div><Label>Name / Spitzname</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Steve" className="mt-1.5 bg-input/50" maxLength={50} />
          </div>
          <div><Label>Minecraft-Username (optional)</Label>
            <Input value={mcUsername} onChange={(e) => setMcUsername(e.target.value)} placeholder="Notch" className="mt-1.5 bg-input/50" maxLength={32} />
          </div>
          <div><Label>Alter (optional)</Label>
            <Input type="number" min={1} max={120} value={age} onChange={(e) => setAge(e.target.value ? parseInt(e.target.value) : "")} className="mt-1.5 bg-input/50" />
          </div>
        </div>
      ),
      canNext: name.trim().length > 0,
    },
    {
      title: "Was zockst du?",
      sub: "Wähle alles, was auf dich zutrifft.",
      body: (
        <div className="flex flex-wrap gap-2">
          {INTEREST_OPTIONS.map(i => (
            <button key={i} type="button" onClick={() => toggle(i)}
              className={`px-3.5 py-2 rounded-full text-sm border transition-all ${
                interests.includes(i)
                  ? "bg-gradient-primary text-primary-foreground border-transparent shadow-glow"
                  : "border-border hover:border-primary/50 bg-secondary/30"}`}>
              {i}
            </button>
          ))}
        </div>
      ),
      canNext: interests.length > 0,
    },
    {
      title: "Dein Style?",
      sub: "Wie spielst du am liebsten?",
      body: (
        <div className="grid grid-cols-2 gap-3">
          {PLAYSTYLES.map(p => (
            <button key={p.v} type="button" onClick={() => setPlaystyle(p.v)}
              className={`p-4 rounded-2xl border text-left transition-all ${
                playstyle === p.v
                  ? "border-primary bg-primary/10 shadow-glow"
                  : "border-border hover:border-primary/50 bg-secondary/30"}`}>
              <div className="text-2xl mb-1">{p.e}</div>
              <div className="font-medium text-sm">{p.l}</div>
            </button>
          ))}
        </div>
      ),
      canNext: !!playstyle,
    },
    {
      title: "Persönlich",
      sub: "Hilft der AI, dich besser zu verstehen.",
      body: (
        <div className="space-y-4">
          <div><Label>Lieblings-Block / -Item (optional)</Label>
            <Input value={favoriteBlock} onChange={(e) => setFavoriteBlock(e.target.value)} placeholder="Netherite, Diamant…" className="mt-1.5 bg-input/50" maxLength={50} />
          </div>
          <div><Label>Sag was über dich (optional)</Label>
            <Textarea value={about} onChange={(e) => setAbout(e.target.value)} placeholder="Ich baue gerne Burgen, mag Redstone…" className="mt-1.5 bg-input/50 min-h-[80px]" maxLength={500} />
          </div>
          <div><Label>Wie hast du uns gefunden? (optional)</Label>
            <Input value={referral} onChange={(e) => setReferral(e.target.value)} placeholder="YouTube, Freund, Discord…" className="mt-1.5 bg-input/50" maxLength={100} />
          </div>
        </div>
      ),
      canNext: true,
    },
  ];

  const s = steps[step];
  const last = step === steps.length - 1;

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 py-12">
      <div className="mb-6 flex items-center gap-3">
        <LogoMark size="md" className="h-14 w-14 drop-shadow-[0_0_32px_hsl(var(--primary)/0.4)]" />
        <div className="font-display text-xl"><span className="gradient-text font-bold">Mythos</span> AI</div>
      </div>

      <div className="glass-strong rounded-3xl p-6 md:p-8 w-full max-w-lg animate-fade-in">
        <div className="flex gap-1.5 mb-6">
          {steps.map((_, i) => (
            <div key={i} className={`h-1 flex-1 rounded-full transition-all ${i <= step ? "bg-gradient-primary" : "bg-border"}`} />
          ))}
        </div>

        <div className="mb-1 flex items-center gap-2 text-xs text-muted-foreground font-medium">
          <Sparkles className="h-3.5 w-3.5 text-primary" /> Schritt {step + 1} von {steps.length}
        </div>
        <h1 className="font-display text-2xl md:text-3xl font-bold mb-1"><Sparkles className=\"h-5 w-5 mr-2 text-primary\" />{s.title}</h1>
        <p className="text-sm text-muted-foreground mb-6">{s.sub}</p>

        <div className="mb-8">{s.body}</div>

        <div className="flex gap-2">
          {step > 0 && (
            <Button variant="outline" onClick={() => setStep(step - 1)} className="flex-1">
              <ArrowLeft className="h-4 w-4 mr-1.5" /> Zurück
            </Button>
          )}
          <Button
            onClick={() => last ? save() : setStep(step + 1)}
            disabled={!s.canNext || saving}
            className="flex-1 bg-gradient-primary text-primary-foreground hover:opacity-90"
          >
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : last ? (<>Los geht's <Check className="h-4 w-4 ml-1.5" /></>) : (<>Weiter <ArrowRight className="h-4 w-4 ml-1.5" /></>)}
          </Button>
        </div>

        {step === 0 && (
          <button onClick={() => { if (user) { supabase.from("profiles").update({ onboarded: true }).eq("user_id", user.id).then(() => { refreshProfile(); nav("/app"); }); }}}
            className="block mx-auto mt-4 text-xs text-muted-foreground hover:text-foreground">
            Überspringen
          </button>
        )}
      </div>
    </div>
  );
}
