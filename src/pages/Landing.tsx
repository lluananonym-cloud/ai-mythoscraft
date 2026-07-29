import { useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import TopNav from "@/components/TopNav";
import Logo from "@/components/Logo";
import {
  Sparkles, Zap, Shield, Code2, Brain, MessageSquare, Server, Key, ArrowRight, Check, Crown,
  Film, Music, AudioLines, Image as ImageIcon,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { TIER_LIMITS } from "@/hooks/useSubscription";

const Feature = ({ icon: Icon, title, desc }: any) => (
  <div className="glass rounded-2xl p-6 hover:border-primary/40 transition-all duration-300 hover:-translate-y-1 group">
    <div className="h-12 w-12 rounded-xl bg-gradient-primary/20 border border-primary/20 flex items-center justify-center mb-4 group-hover:glow-primary transition-shadow">
      <Icon className="h-6 w-6 text-primary" />
    </div>
    <h3 className="font-display text-lg font-semibold mb-2">{title}</h3>
    <p className="text-sm text-muted-foreground leading-relaxed">{desc}</p>
  </div>
);

const Stat = ({ value, label }: { value: string; label: string }) => (
  <div className="text-center">
    <div className="font-display text-2xl sm:text-3xl font-bold gradient-text">{value}</div>
    <div className="text-[11px] uppercase tracking-wider text-muted-foreground mt-1">{label}</div>
  </div>
);

const Landing = () => {
  const { user, profile } = useAuth();
  const nav = useNavigate();

  useEffect(() => {
    if (user && profile?.start_in_chat) nav("/app", { replace: true });
  }, [user, profile?.start_in_chat, nav]);

  return (
    <div className="min-h-screen relative overflow-hidden">
      {/* Aurora background — pure CSS, no extra deps */}
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute -top-40 -left-32 h-[480px] w-[480px] rounded-full bg-primary/30 blur-3xl animate-pulse-glow" />
        <div className="absolute top-1/3 -right-24 h-[420px] w-[420px] rounded-full bg-accent/25 blur-3xl" style={{ animation: "pulse-glow 6s ease-in-out infinite" }} />
        <div className="absolute bottom-0 left-1/3 h-[360px] w-[360px] rounded-full bg-primary/20 blur-3xl" style={{ animation: "pulse-glow 8s ease-in-out infinite 1s" }} />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,transparent_0%,hsl(var(--background))_70%)]" />
      </div>

      <TopNav />

      <main>
        {/* Hero */}
        <section className="container pt-12 sm:pt-20 pb-16 sm:pb-24 text-center relative">
          <div className="inline-flex items-center gap-2 glass rounded-full px-4 py-1.5 mb-6 sm:mb-8 animate-fade-in">
            <span className="h-2 w-2 rounded-full bg-accent animate-pulse" />
            <span className="text-xs font-medium text-muted-foreground">Live Voice · Video · Musik · Bild — alles in einer App</span>
          </div>
          <h1 className="font-display text-5xl sm:text-6xl md:text-7xl lg:text-8xl font-bold tracking-tight mb-5 sm:mb-6 animate-fade-in leading-[0.95]">
            Eine KI.<br />
            <span className="gradient-text">Alles drin.</span>
          </h1>
          <p className="text-base sm:text-lg md:text-xl text-muted-foreground max-w-2xl mx-auto mb-8 sm:mb-10 animate-fade-in px-2">
            Chat, Live-Sprache, Bild-, Musik- &amp; Video-Generierung — alles in einem schnellen, schönen Interface. Frei nutzbar für <span className="text-foreground font-medium">mythoscraft.online</span>.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-3 animate-fade-in mb-12">
            <Link to={user ? "/app" : "/auth"}>
              <Button size="lg" className="bg-gradient-primary text-primary-foreground hover:opacity-90 glow-primary text-base px-8 h-12">
                <Sparkles className="h-5 w-5 mr-2" /> Mythos AI starten <ArrowRight className="h-4 w-4 ml-2" />
              </Button>
            </Link>
            <Link to="/docs">
              <Button size="lg" variant="outline" className="glass border-glass-border text-base px-8 h-12">
                <Code2 className="h-5 w-5 mr-2" /> API-Dokumentation
              </Button>
            </Link>
          </div>

          {/* Capability pills */}
          <div className="flex flex-wrap items-center justify-center gap-2 max-w-2xl mx-auto animate-fade-in">
            {[
              { icon: MessageSquare, label: "Chat" },
              { icon: AudioLines, label: "Live-Voice" },
              { icon: ImageIcon, label: "Bilder" },
              { icon: Music, label: "Musik" },
              { icon: Film, label: "Video" },
              { icon: Brain, label: "Agent" },
            ].map(({ icon: Icon, label }) => (
              <span key={label} className="inline-flex items-center gap-1.5 glass rounded-full px-3 py-1.5 text-xs text-foreground/80">
                <Icon className="h-3.5 w-3.5 text-primary" /> {label}
              </span>
            ))}
          </div>
        </section>

        {/* Stats strip */}
        <section className="container pb-16 sm:pb-24">
          <div className="glass-strong rounded-3xl px-6 py-6 sm:py-8 grid grid-cols-2 sm:grid-cols-4 gap-6">
            <Stat value="6+" label="KI-Modalitäten" />
            <Stat value="0 €" label="Free Forever Tier" />
            <Stat value="<1s" label="Stream-Latenz" />
            <Stat value="100%" label="Im Browser möglich" />
          </div>
        </section>

        {/* Features */}
        <section className="container pb-24">
          <div className="text-center mb-12">
            <h2 className="font-display text-3xl md:text-5xl font-bold mb-4">Was Mythos AI besonders macht</h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">Mehr als ein Chatbot. Eine vollwertige KI-Plattform — sauber, schnell, schön.</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            <Feature icon={MessageSquare} title="Server-Support" desc="Kennt mythoscraft.online in- und auswendig: Regeln, Commands, Plugins, FAQ — alles live aus der Knowledge Base." />
            <Feature icon={Brain} title="Agent-Modus" desc="Mehrstufiges Reasoning mit Tools: Web-Suche, Live-Server-Status und Wissens-Lookup im Hintergrund." />
            <Feature icon={AudioLines} title="Live-Sprachchat" desc="Tippen war gestern. Sprich direkt mit Mythos — ein animierter Mic-Orb reagiert auf deine Stimme." />
            <Feature icon={Film} title="Video-Generierung" desc="Cinematic Clips aus reinem Text. Bild + Animation, gerendert im Browser, kostenlos & herunterladbar." />
            <Feature icon={Music} title="Musik im Browser" desc="Echte KI-Komposition via MusicGen — läuft lokal nach einmaligem Modell-Download. Kein API-Key nötig." />
            <Feature icon={Key} title="Claude-kompatible API" desc="Generiere kostenlose API-Keys im Format sk-ant-mythos-… für eigene Tools oder MythosBrowse." />
          </div>
        </section>

        {/* Pricing */}
        <section className="container pb-24" id="pricing">
          <div className="text-center mb-12">
            <h2 className="font-display text-3xl md:text-5xl font-bold mb-4">Einfach. Fair. Drei Pläne.</h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">Starte kostenlos. Upgrade nur wenn du mehr willst.</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5 max-w-5xl mx-auto">
            {[
              { key:"free",  name:"Free",  icon: Sparkles, accent:"from-zinc-400 to-zinc-600",
                features:["20 Chats / Tag","Alle Standard-Personas","Server-Knowledge","Community-Support"] },
              { key:"light", name:"Light", icon: Zap, accent:"from-sky-400 to-indigo-500", tag:"Mitte",
                features:[`${TIER_LIMITS.light.chatsPerDay} Chats / Tag`,"Bilder generieren","Alle Personas","Keine Werbung"] },
              { key:"pro",   name:"Pro",   icon: Crown, accent:"from-fuchsia-500 to-rose-500", tag:"Beliebt", highlight:true,
                features:["Unbegrenzte Chats","Live-Sprachchat","Bilder · Musik · Video","Priorität bei Updates"] },
            ].map((p:any) => (
              <div key={p.key} className={`relative glass-strong rounded-3xl p-7 transition-all hover:-translate-y-1 ${p.highlight ? "border-primary/60 ring-1 ring-primary/30 glow-primary" : ""}`}>
                {p.tag && (
                  <span className={`absolute -top-3 right-5 text-[10px] uppercase tracking-wider px-2.5 py-1 rounded-full text-white bg-gradient-to-r ${p.accent}`}>{p.tag}</span>
                )}
                <div className={`h-11 w-11 rounded-xl bg-gradient-to-br ${p.accent} flex items-center justify-center mb-4`}>
                  <p.icon className="h-5 w-5 text-white" />
                </div>
                <div className="font-display text-2xl font-bold">{p.name}</div>
                <div className="text-sm text-muted-foreground mb-5">{TIER_LIMITS[p.key as keyof typeof TIER_LIMITS].priceLabel}</div>
                <ul className="space-y-2 text-sm mb-6">
                  {p.features.map((f:string) => <li key={f} className="flex items-center gap-2"><Check className="h-4 w-4 text-primary shrink-0" />{f}</li>)}
                </ul>
                <Link to={user ? "/app" : "/auth"} className="block">
                  <Button variant={p.highlight ? "default" : "outline"} className={`w-full ${p.highlight ? "bg-gradient-primary text-primary-foreground" : ""}`}>
                    {p.key === "free" ? "Loslegen" : "Upgrade"}
                  </Button>
                </Link>
              </div>
            ))}
          </div>
        </section>

        {/* CTA */}
        <section className="container pb-24">
          <div className="glass-strong rounded-3xl p-10 md:p-16 text-center relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-cosmic opacity-20 pointer-events-none" />
            <div className="relative">
              <h2 className="font-display text-3xl md:text-5xl font-bold mb-4">Bereit für die volle Mythos-Erfahrung?</h2>
              <p className="text-muted-foreground mb-8 max-w-xl mx-auto">Account erstellen, API-Key generieren, loslegen. Komplett kostenlos.</p>
              <Link to={user ? "/app" : "/auth"}>
                <Button size="lg" className="bg-gradient-primary text-primary-foreground hover:opacity-90 glow-primary text-base px-10 h-12">
                  Jetzt starten <ArrowRight className="h-4 w-4 ml-2" />
                </Button>
              </Link>
            </div>
          </div>
        </section>

        <footer className="border-t border-border/50 py-8">
          <div className="container flex flex-col md:flex-row items-center justify-between gap-4 text-sm text-muted-foreground">
            <Logo size="sm" />
            <p>© 2026 Mythos AI · Built for <a href="https://mythoscraft.online" className="text-accent hover:underline">mythoscraft.online</a></p>
          </div>
        </footer>
      </main>
    </div>
  );
};
export default Landing;
