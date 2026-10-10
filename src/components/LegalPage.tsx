// Gemeinsames Layout für Impressum, Datenschutz, AGB, Cookie-Hinweis und KI-Regeln.
import type { ReactNode } from "react";
import TopNav from "@/components/TopNav";
import { LegalLinks } from "@/components/CookieConsent";
import { LEGAL, hasPlaceholders } from "@/lib/legal";

/** Zeigt einen Wert aus legal.ts; Platzhalter in [Klammern] werden gelb markiert. */
export function V({ k }: { k: keyof typeof LEGAL }) {
  const parts = LEGAL[k].split(/(\[[^\]]+\])/g);
  return (
    <>
      {parts.map((p, i) =>
        /^\[.*\]$/.test(p)
          ? <mark key={i} className="rounded bg-amber-400/20 px-1 text-amber-200">{p}</mark>
          : <span key={i}>{p}</span>,
      )}
    </>
  );
}

export function H({ children }: { children: ReactNode }) {
  return <h2 className="pt-4 font-sans text-lg font-semibold text-foreground">{children}</h2>;
}

export default function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container mx-auto max-w-3xl px-4 py-12">
        <h1 className="mb-2 font-display text-3xl font-bold md:text-4xl">{title}</h1>
        <p className="mb-6 text-xs text-muted-foreground">Stand: {LEGAL.stand}</p>
        {hasPlaceholders && (
          <p className="mb-6 rounded-xl border border-amber-400/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">
            Hinweis für den Betreiber: Gelb markierte Angaben sind Platzhalter und müssen in <code>src/lib/legal.ts</code> ersetzt werden.
          </p>
        )}
        <section className="space-y-3 leading-relaxed text-muted-foreground [&_a]:text-foreground [&_a]:underline [&_li]:ml-5 [&_li]:list-disc [&_strong]:text-foreground">
          {children}
        </section>
        <LegalLinks className="mt-12 !text-xs" />
      </main>
    </div>
  );
}
