import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { LucideIcon } from "lucide-react";
import { ArrowLeft } from "lucide-react";
import TopNav from "@/components/TopNav";

type Props = { icon: LucideIcon; title: string; subtitle: string; children: ReactNode; back?: boolean; wide?: boolean };

/** Gemeinsamer Rahmen der Labs-Seiten: Kopfzeile mit Zurück-Knopf, mobil ohne Überlappungen. */
export default function LabsShell({ icon: Icon, title, subtitle, children, back = true, wide }: Props) {
  return (
    <div className="min-h-[100dvh]">
      <TopNav />
      <main className={`container ${wide ? "max-w-6xl" : "max-w-4xl"} px-4 py-5 md:py-8 pb-[max(env(safe-area-inset-bottom),1.5rem)] space-y-5`}>
        <header className="flex items-center gap-3 min-w-0">
          {back && (
            <Link to="/entdecken" aria-label="Zurück zu Entdecken"
              className="shrink-0 flex h-10 w-10 items-center justify-center rounded-xl bg-white/5 hover:bg-white/10">
              <ArrowLeft className="h-5 w-5" />
            </Link>
          )}
          <span className="shrink-0 flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-fuchsia-500/30 to-violet-600/30">
            <Icon className="h-5 w-5 text-fuchsia-300" />
          </span>
          <div className="min-w-0">
            <h1 className="font-display text-xl md:text-3xl font-bold truncate">{title}</h1>
            <p className="text-xs md:text-sm text-muted-foreground line-clamp-2">{subtitle}</p>
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}
