import { Link } from "react-router-dom";
import LabsShell from "@/components/labs/LabsShell";
import { Compass, Swords, Globe2, Camera, Users, CalendarHeart, Store, ChevronRight } from "lucide-react";

const LABS = [
  { to: "/arena", icon: Swords, title: "Live-Arena", text: "Zwei KI-Modelle, eine Aufgabe. Du stimmst ab, wer gewinnt." },
  { to: "/welt", icon: Globe2, title: "Welt-Modus", text: "Deine Chats, Bilder und Apps als 3D-Insel zum Anklicken." },
  { to: "/foto-app", icon: Camera, title: "Foto zu App", text: "Skizze fotografieren, fertige klickbare App bekommen." },
  { to: "/ki-team", icon: Users, title: "KI-Team", text: "Designerin, Programmierer und Tester bauen deine App zusammen." },
  { to: "/rueckblick", icon: CalendarHeart, title: "Tagesrückblick", text: "Dein Tag als kurzer Comic oder als Video." },
  { to: "/marktplatz", icon: Store, title: "Mythos-Marktplatz", text: "Apps und Bilder anderer übernehmen und weiterbauen." },
];

/** Übersicht aller neuen Funktionen, jede mit einem großen Button. */
export default function Entdecken() {
  return (
    <LabsShell icon={Compass} title="Entdecken" subtitle="Neue Sachen, die du mit Mythos ausprobieren kannst." back={false}>
      <div className="grid gap-3 sm:grid-cols-2">
        {LABS.map(({ to, icon: Icon, title, text }) => (
          <Link key={to} to={to} className="glass rounded-2xl p-4 flex items-center gap-3 hover:bg-white/10 transition-colors min-h-[84px]">
            <span className="shrink-0 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-fuchsia-500/30 to-violet-600/30">
              <Icon className="h-6 w-6 text-fuchsia-300" />
            </span>
            <span className="flex-1 min-w-0">
              <span className="block font-display text-base">{title}</span>
              <span className="block text-xs text-muted-foreground">{text}</span>
            </span>
            <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground" />
          </Link>
        ))}
      </div>
    </LabsShell>
  );
}
