import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import TopNav from "@/components/TopNav";
import Logo from "@/components/Logo";
import { Sparkles, Zap, Shield, Code2, Brain, MessageSquare, Server, Key, ArrowRight } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

const Feature = ({ icon: Icon, title, desc }: any) => (
  <div className="glass rounded-2xl p-6 hover:border-primary/40 transition-all duration-300 hover:-translate-y-1 group">
    <div className="h-12 w-12 rounded-xl bg-gradient-primary/20 border border-primary/20 flex items-center justify-center mb-4 group-hover:glow-primary transition-shadow">
      <Icon className="h-6 w-6 text-primary" />
    </div>
    <h3 className="font-display text-lg font-semibold mb-2">{title}</h3>
    <p className="text-sm text-muted-foreground leading-relaxed">{desc}</p>
  </div>
);

const Landing = () => {
  const { user } = useAuth();
  return (
    <div className="min-h-screen">
      <TopNav />

      <main>
        {/* Hero */}
        <section className="container pt-12 sm:pt-20 pb-20 sm:pb-32 text-center relative">
          <div className="inline-flex items-center gap-2 glass rounded-full px-4 py-1.5 mb-6 sm:mb-8 animate-fade-in">
            <span className="h-2 w-2 rounded-full bg-accent animate-pulse" />
            <span className="text-xs font-medium text-muted-foreground">Powered by Lovable AI · Free Forever</span>
          </div>
          <h1 className="font-display text-4xl sm:text-5xl md:text-7xl lg:text-8xl font-bold tracking-tight mb-5 sm:mb-6 animate-fade-in">
            Die KI für<br />
            <span className="gradient-text">Mythoscraft</span>
          </h1>
          <p className="text-base sm:text-lg md:text-xl text-muted-foreground max-w-2xl mx-auto mb-8 sm:mb-10 animate-fade-in px-2">
            Dein intelligenter Support-Assistent für <span className="text-foreground font-medium">mythoscraft.online</span> — mit Claude-kompatibler API, mächtigem Agent-Modus und Live-Server-Tools.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-3 animate-fade-in">
            <Link to={user ? "/app" : "/auth"}>
              <Button size="lg" className="bg-gradient-primary text-primary-foreground hover:opacity-90 glow-primary text-base px-8 h-12">
                <Sparkles className="h-5 w-5 mr-2" /> Mythos AI testen <ArrowRight className="h-4 w-4 ml-2" />
              </Button>
            </Link>
            <Link to="/docs">
              <Button size="lg" variant="outline" className="glass border-glass-border text-base px-8 h-12">
                <Code2 className="h-5 w-5 mr-2" /> API-Dokumentation
              </Button>
            </Link>
          </div>
        </section>

        {/* Features */}
        <section className="container pb-24">
          <div className="text-center mb-12">
            <h2 className="font-display text-3xl md:text-5xl font-bold mb-4">Was Mythos AI besonders macht</h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">Mehr als nur ein Chatbot. Eine vollwertige KI-Plattform für deinen Server.</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            <Feature icon={MessageSquare} title="Server-Support" desc="Kennt mythoscraft.online in- und auswendig: Regeln, Commands, Plugins, FAQ — alles live aus der Knowledge Base." />
            <Feature icon={Brain} title="Agent-Modus" desc="Mehrstufiges Reasoning mit Tools: Web-Suche, Live-Server-Status und Wissens-Lookup im Hintergrund." />
            <Feature icon={Server} title="Live Server-Status" desc="Frag in Echtzeit: Ist der Server online? Wer spielt gerade? Mythos AI sieht direkt nach." />
            <Feature icon={Key} title="Claude-kompatible API" desc="Generiere kostenlose API-Keys im Format sk-ant-mythos-... — nutze sie in MythosBrowse oder eigenem Code." />
            <Feature icon={Zap} title="Streaming Antworten" desc="Token-für-Token rendering. Schnell, flüssig, modern — wie ChatGPT, nur fokussierter." />
            <Feature icon={Shield} title="Sicher & Privat" desc="Jeder User hat seine eigenen Chats und API-Keys. Strikte Row-Level-Security auf jeder Tabelle." />
          </div>
        </section>

        {/* CTA */}
        <section className="container pb-24">
          <div className="glass-strong rounded-3xl p-10 md:p-16 text-center relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-cosmic opacity-20 pointer-events-none" />
            <div className="relative">
              <h2 className="font-display text-3xl md:text-5xl font-bold mb-4">Bereit, Mythos AI zu nutzen?</h2>
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
