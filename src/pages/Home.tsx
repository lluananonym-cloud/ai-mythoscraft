import { useEffect, useRef, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { Loader2, Send, MessageSquare, Image as ImageIcon, Music, Film, Code2, Mic, Download, LayoutDashboard, ArrowRight } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import TopNav from "@/components/TopNav";
import Landing from "./Landing";

type RecentConv = { id: string; title: string; updated_at: string };

const QUICK = [
  { icon: ImageIcon, label: "Bild erstellen", prefix: "/image " },
  { icon: Music, label: "Musik machen", prefix: "/music " },
  { icon: Film, label: "Video erstellen", prefix: "/video " },
  { icon: Code2, label: "Code schreiben", prefix: "Schreib mir Code für " },
];

const greeting = () => {
  const h = new Date().getHours();
  if (h < 5) return "Gute Nacht";
  if (h < 11) return "Guten Morgen";
  if (h < 18) return "Hallo";
  return "Guten Abend";
};

const timeAgo = (iso: string) => {
  const min = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (min < 1) return "gerade eben";
  if (min < 60) return `vor ${min} Min.`;
  const h = Math.round(min / 60);
  if (h < 24) return `vor ${h} Std.`;
  const d = Math.round(h / 24);
  return d === 1 ? "gestern" : `vor ${d} Tagen`;
};

// Startseite für Eingeloggte: Eingabefeld, Schnellstart und die letzten Chats.
const LoggedInHome = () => {
  const { user, profile } = useAuth();
  const nav = useNavigate();
  const [input, setInput] = useState("");
  const [recent, setRecent] = useState<RecentConv[] | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    supabase.from("conversations").select("id,title,updated_at")
      .order("updated_at", { ascending: false }).limit(6)
      .then(({ data }) => setRecent((data as RecentConv[]) || []));
  }, [user]);

  const start = () => {
    const text = input.trim();
    if (!text) { nav("/app"); return; }
    nav(`/app?q=${encodeURIComponent(text)}`);
  };

  const name = profile?.display_name || user?.email?.split("@")[0] || "";

  return (
    <div className="min-h-screen relative overflow-hidden">
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute -top-40 -left-32 h-[420px] w-[420px] rounded-full bg-primary/25 blur-3xl animate-pulse-glow" />
        <div className="absolute top-1/3 -right-24 h-[380px] w-[380px] rounded-full bg-accent/20 blur-3xl" />
      </div>

      <TopNav />

      <main className="container max-w-3xl pt-14 sm:pt-24 pb-16">
        <h1 className="font-display text-3xl sm:text-5xl font-bold tracking-tight text-center mb-8 animate-fade-in">
          {greeting()}{name ? <>, <span className="gradient-text">{name}</span></> : null}
        </h1>

        <div className="glass rounded-2xl p-3 focus-within:border-primary/50 transition-colors animate-fade-in">
          <Textarea
            ref={inputRef}
            autoFocus
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); start(); } }}
            placeholder="Wie kann ich dir heute helfen?"
            rows={3}
            className="resize-none border-0 bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0 text-base"
          />
          <div className="flex items-center justify-between gap-2 pt-1">
            <div className="flex items-center gap-1">
              <Button asChild variant="ghost" size="icon" className="h-9 w-9" aria-label="Live-Sprachchat">
                <Link to="/voice"><Mic className="h-4 w-4" /></Link>
              </Button>
            </div>
            <Button onClick={start} size="icon" className="h-9 w-9 bg-gradient-primary text-primary-foreground" aria-label="Senden">
              <Send className="h-4 w-4" />
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap justify-center gap-2 mt-4">
          {QUICK.map(q => (
            <button
              key={q.label}
              onClick={() => { setInput(q.prefix); inputRef.current?.focus(); }}
              className="glass rounded-full px-4 py-1.5 text-sm text-muted-foreground hover:text-foreground hover:border-primary/40 transition-colors inline-flex items-center gap-2"
            >
              <q.icon className="h-4 w-4" /> {q.label}
            </button>
          ))}
        </div>

        <section className="mt-12">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-medium text-muted-foreground">Letzte Chats</h2>
            <Link to="/app" className="text-sm text-muted-foreground hover:text-foreground inline-flex items-center gap-1">
              Alle Chats <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          {recent === null ? (
            <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
          ) : recent.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-6">Noch keine Chats. Schreib oben einfach los.</p>
          ) : (
            <div className="grid sm:grid-cols-2 gap-2">
              {recent.map(c => (
                <Link key={c.id} to={`/app/c/${c.id}`} className="glass rounded-xl px-4 py-3 hover:border-primary/40 transition-colors flex items-center gap-3 min-w-0">
                  <MessageSquare className="h-4 w-4 text-primary shrink-0" />
                  <span className="truncate flex-1 text-sm">{c.title}</span>
                  <span className="text-xs text-muted-foreground shrink-0">{timeAgo(c.updated_at)}</span>
                </Link>
              ))}
            </div>
          )}
        </section>

        <section className="mt-10 grid grid-cols-3 gap-2">
          {[
            { to: "/voice", icon: Mic, label: "Sprechen" },
            { to: "/downloads", icon: Download, label: "Downloads" },
            { to: "/dashboard", icon: LayoutDashboard, label: "Konto" },
          ].map(l => (
            <Link key={l.to} to={l.to} className="glass rounded-xl p-4 flex flex-col items-center gap-2 text-sm text-muted-foreground hover:text-foreground hover:border-primary/40 transition-colors">
              <l.icon className="h-5 w-5" /> {l.label}
            </Link>
          ))}
        </section>
      </main>
    </div>
  );
};

// "/" zeigt Ausgeloggten die Landingpage und Eingeloggten ihre Startseite.
const Home = () => {
  const { user, loading, profile } = useAuth();
  if (loading) return <div className="min-h-screen flex items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary" /></div>;
  if (!user) return <Landing />;
  if (profile?.start_in_chat) return <Navigate to="/app" replace />;
  return <LoggedInHome />;
};

export default Home;
