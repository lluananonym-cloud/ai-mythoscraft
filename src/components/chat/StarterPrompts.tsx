import { useMemo, useState } from "react";
import {
  HelpCircle, Film, Image as ImageIcon, Target, Music, Clapperboard, Monitor, Globe, Code2, Lightbulb,
  Languages, Gamepad2, Server, Briefcase, Smile, Compass, RefreshCw, MessageSquare, type LucideIcon,
} from "lucide-react";

type Starter = { icon: LucideIcon; label: string; tags: string };

/** Vorrat an Startvorschlägen; jedes Öffnen zeigt eine andere Auswahl. */
const STARTERS: Starter[] = [
  { icon: HelpCircle, label: "Wie verbinde ich mich mit dem Server?", tags: "minecraft server" },
  { icon: Server, label: "Welche Spielmodi gibt es auf dem Mythos-Server?", tags: "minecraft server spiel" },
  { icon: Gamepad2, label: "Gib mir 5 Ideen für ein Minecraft-Bauprojekt", tags: "minecraft bauen spiel" },
  { icon: Film, label: "/video epische Drohnenaufnahme über einer Burg", tags: "video film" },
  { icon: Film, label: "/video Raumschiff landet bei Sonnenuntergang auf dem Mars", tags: "video film weltraum" },
  { icon: ImageIcon, label: "/image ein epischer Drache über mythoscraft", tags: "bild image drache" },
  { icon: ImageIcon, label: "/image gemütliches Holzhaus im Schnee, Pixel-Art", tags: "bild image pixel" },
  { icon: ImageIcon, label: "/image Logo für meinen YouTube-Kanal, neon, minimalistisch", tags: "bild logo youtube" },
  { icon: Music, label: "/music chilliger Lo-Fi-Beat zum Lernen", tags: "musik song lernen" },
  { icon: Music, label: "/music epischer Soundtrack für ein Abenteuer", tags: "musik song" },
  { icon: Clapperboard, label: "/film Ein Ritter findet ein Portal in eine andere Welt", tags: "film video geschichte" },
  { icon: Monitor, label: "/cloudpc Recherchiere die besten Gaming-Mäuse unter 50 €", tags: "cloud pc recherche kaufen" },
  { icon: Target, label: "/goal Erstelle mir einen kompletten Lernplan für die Matheprüfung", tags: "schule mathe lernen" },
  { icon: Target, label: "/goal Plane mir ein 4-Wochen-Trainingsprogramm für zu Hause", tags: "sport fitness training" },
  { icon: Globe, label: "/research Was gibt es Neues bei KI diese Woche?", tags: "ki news recherche" },
  { icon: Code2, label: "Programmier mir ein Snake-Spiel in HTML und JavaScript", tags: "code programmieren spiel web" },
  { icon: Code2, label: "Erklär mir, wie ein Minecraft-Plugin in Java aufgebaut ist", tags: "code java minecraft plugin" },
  { icon: Code2, label: "Bau mir eine schicke Landingpage für mein Projekt", tags: "code web seite" },
  { icon: Lightbulb, label: "Erklär mir schwarze Löcher, als wäre ich 12", tags: "wissen physik weltraum" },
  { icon: Lightbulb, label: "Gib mir 10 kreative Ideen für ein Wochenende", tags: "ideen freizeit" },
  { icon: Languages, label: "/translate englisch Ich freue mich auf das Wochenende!", tags: "sprache englisch übersetzen" },
  { icon: Briefcase, label: "Hilf mir, eine Bewerbung für einen Ferienjob zu schreiben", tags: "bewerbung job schreiben" },
  { icon: Briefcase, label: "Schreib mir eine höfliche E-Mail an meinen Lehrer", tags: "schule mail schreiben" },
  { icon: Smile, label: "Erzähl mir einen richtig guten Witz", tags: "witz spaß" },
  { icon: Smile, label: "Schreib eine kurze Gruselgeschichte mit überraschendem Ende", tags: "geschichte schreiben" },
  { icon: Compass, label: "Plane mir einen Tagesausflug mit kleinem Budget", tags: "reise ausflug" },
  { icon: Gamepad2, label: "Welche Spiele sind gerade angesagt und warum?", tags: "spiel gaming" },
  { icon: Target, label: "/goal Hilf mir, ein eigenes YouTube-Video zu planen", tags: "youtube video planen" },
];

const shuffle = <T,>(a: T[]) => {
  const b = a.slice();
  for (let i = b.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [b[i], b[j]] = [b[j], b[i]]; }
  return b;
};

/** 4 Vorschläge: bis zu zwei passend zu den letzten Chats, eine Anknüpfung an den letzten Chat, der Rest zufällig. */
function pick(titles: string[]): Starter[] {
  const words = titles.join(" ").toLowerCase();
  const all = shuffle(STARTERS);
  const out = all.filter((s) => s.tags.split(" ").some((k) => k.length > 2 && words.includes(k))).slice(0, 2);
  for (const s of all) { if (out.length >= 4) break; if (!out.includes(s)) out.push(s); }
  const last = titles.find((t) => t && !/^neuer chat$/i.test(t.trim()));
  if (last && Math.random() < 0.7) {
    const t = last.length > 40 ? last.slice(0, 39) + "…" : last;
    out[3] = { icon: MessageSquare, label: `Erzähl mir mehr zu „${t}“`, tags: "" };
  }
  return out;
}

export default function StarterPrompts({ titles, onPick }: { titles: string[]; onPick: (text: string) => void }) {
  const [round, setRound] = useState(0);
  // Neu würfeln bei jedem Öffnen, beim Klick auf „Andere Vorschläge“ und sobald die Chatliste geladen ist.
  const key = titles.slice(0, 8).join("|");
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const items = useMemo(() => pick(titles.slice(0, 8)), [round, key]);
  return (
    <div className="w-full max-w-2xl">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {items.map(({ icon: Icon, label }) => (
          <button
            key={label}
            onClick={() => onPick(label)}
            className="border border-white/10 hover:border-white/20 hover:bg-white/5 rounded-2xl p-4 text-sm text-left transition-colors flex items-start gap-3"
          >
            <Icon className="h-4 w-4 text-foreground/60 mt-0.5 shrink-0" />
            <span className="text-foreground/90">{label}</span>
          </button>
        ))}
      </div>
      <div className="mt-3 flex justify-center">
        <button onClick={() => setRound((r) => r + 1)} className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-muted-foreground hover:bg-white/5 hover:text-foreground">
          <RefreshCw className="h-3 w-3" /> Andere Vorschläge
        </button>
      </div>
    </div>
  );
}
