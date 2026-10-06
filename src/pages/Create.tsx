import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Code2, Film, Image as ImageIcon, Music, Sparkles, type LucideIcon } from "lucide-react";

type Kind = {
  id: string;
  label: string;
  desc: string;
  icon: LucideIcon;
  cmd: string;
  placeholder: string;
  examples: string[];
};

// Jede Kachel nutzt den passenden Befehl im Chat (/image, /music, /video, /code).
const KINDS: Kind[] = [
  {
    id: "image", label: "Bild", desc: "KI-Bild aus deiner Beschreibung", icon: ImageIcon, cmd: "/image",
    placeholder: "z. B. Ein Drache über einer Burg bei Sonnenuntergang",
    examples: ["Futuristische Stadt bei Nacht, Neonlichter", "Süßer Fuchs im Minecraft-Stil", "Logo für ein Gaming-Team, blau und gold"],
  },
  {
    id: "music", label: "Musik", desc: "Eigener KI-Song nach Stil oder Stimmung", icon: Music, cmd: "/music",
    placeholder: "z. B. Chillige Lo-Fi-Beats zum Lernen",
    examples: ["Epische Orchestermusik", "Fröhlicher 8-Bit-Gamesound", "Entspannter Lo-Fi-Hip-Hop"],
  },
  {
    id: "video", label: "Video", desc: "Kurzes KI-Video zu einer Szene", icon: Film, cmd: "/video",
    placeholder: "z. B. Wellen am Strand bei Sonnenaufgang",
    examples: ["Rakete startet ins All", "Regen in einer Neonstadt", "Wald im Herbstwind"],
  },
  {
    id: "code", label: "Code", desc: "Programme, Skripte und Webseiten schreiben", icon: Code2, cmd: "/code",
    placeholder: "z. B. Eine Todo-Liste als Webseite in HTML",
    examples: ["Taschenrechner in Python", "Discord-Bot, der Hallo sagt", "Landingpage für mein Projekt"],
  },
];

const Create = () => {
  const nav = useNavigate();
  const [kind, setKind] = useState<Kind>(KINDS[0]);
  const [prompt, setPrompt] = useState("");

  const start = (text = prompt) => {
    const t = text.trim();
    if (!t) return;
    nav(`/app?q=${encodeURIComponent(`${kind.cmd} ${t}`)}`);
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container max-w-3xl mx-auto px-4 py-8 animate-fade-in">
        <div className="text-center mb-8">
          <h1 className="font-display text-3xl md:text-4xl font-bold tracking-tight">
            Was willst du <span className="gradient-text">erstellen</span>?
          </h1>
          <p className="mt-2 text-muted-foreground">Wähle aus, beschreib kurz deine Idee, Mythos macht den Rest.</p>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
          {KINDS.map((k) => {
            const active = k.id === kind.id;
            return (
              <button
                key={k.id}
                type="button"
                onClick={() => setKind(k)}
                aria-pressed={active}
                className={`glass rounded-2xl p-4 text-left transition hover:border-primary/60 border ${active ? "border-primary bg-primary/10" : "border-transparent"}`}
              >
                <k.icon className={`h-7 w-7 mb-2 ${active ? "text-primary" : "text-muted-foreground"}`} />
                <div className="font-semibold">{k.label}</div>
                <div className="text-xs text-muted-foreground mt-0.5">{k.desc}</div>
              </button>
            );
          })}
        </div>

        <form
          onSubmit={(e) => { e.preventDefault(); start(); }}
          className="glass-strong rounded-2xl p-5 space-y-4"
        >
          <Textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); start(); }
            }}
            placeholder={kind.placeholder}
            rows={3}
            className="bg-input/50 resize-none"
          />
          <div className="flex flex-wrap gap-2">
            {kind.examples.map((ex) => (
              <Button key={ex} type="button" variant="secondary" size="sm" onClick={() => start(ex)}>
                {ex}
              </Button>
            ))}
          </div>
          <Button
            type="submit"
            disabled={!prompt.trim()}
            className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90"
          >
            <Sparkles className="h-4 w-4 mr-1.5" />{kind.label} erstellen
          </Button>
        </form>

        {kind.id === "code" && (
          <p className="mt-4 text-sm text-center text-muted-foreground">
            Größere Projekte auf deinem PC? Nimm <Link to="/code" className="text-primary hover:underline">Mythos Code</Link>.
          </p>
        )}
      </main>
    </div>
  );
};

export default Create;
