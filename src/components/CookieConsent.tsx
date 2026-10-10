// Cookie- und Einwilligungs-Banner. Erscheint beim ersten Besuch, bis eine Entscheidung getroffen ist.
// "Alle akzeptieren" und "Nur notwendige" sind gleich groß und gleich gut erreichbar (keine Dark Patterns).
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Switch } from "@/components/ui/switch";
import { CONSENT_EVENT, OPEN_CONSENT_EVENT, getConsent, setConsent } from "@/lib/consent";

export default function CookieConsent() {
  const [open, setOpen] = useState(false);
  const [details, setDetails] = useState(false);
  const [external, setExternal] = useState(false);

  useEffect(() => {
    if (!getConsent()) setOpen(true);
    const reopen = () => { setExternal(getConsent()?.external ?? false); setDetails(true); setOpen(true); };
    window.addEventListener(OPEN_CONSENT_EVENT, reopen);
    return () => window.removeEventListener(OPEN_CONSENT_EVENT, reopen);
  }, []);

  if (!open) return null;
  const decide = (ext: boolean) => { setConsent(ext); setOpen(false); setDetails(false); };

  return (
    <div role="dialog" aria-modal="false" aria-labelledby="consent-title" className="fixed inset-x-0 bottom-0 z-[100] p-3 sm:p-4 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
      <div className="mx-auto max-w-2xl rounded-3xl border border-white/10 bg-[hsl(0_0%_8%/0.97)] p-5 text-sm shadow-[0_12px_48px_hsl(0_0%_0%/0.6)] backdrop-blur-xl">
        <h2 id="consent-title" className="mb-1.5 font-sans text-base font-semibold text-foreground">Cookies und externe Dienste</h2>
        <p className="text-muted-foreground leading-relaxed">
          Wir speichern nur, was für Anmeldung, Einstellungen und Sicherheit nötig ist. Wir nutzen kein Tracking und keine Werbe-Cookies.
          Optional darf dein Browser Bilder direkt bei externen KI-Diensten laden, wenn unsere Server gerade ausgelastet sind. Dabei
          sieht der Anbieter deine IP-Adresse. Mehr dazu in der <Link to="/datenschutz" className="underline text-foreground">Datenschutzerklärung</Link> und
          im <Link to="/cookie" className="underline text-foreground">Cookie-Hinweis</Link>.
        </p>

        {details && (
          <div className="mt-4 space-y-3 rounded-2xl border border-white/10 bg-white/[0.03] p-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="font-medium text-foreground">Notwendig</div>
                <div className="text-xs text-muted-foreground">Anmeldung, Sitzung, Spracheinstellungen, Sicherheit. Immer aktiv.</div>
              </div>
              <Switch checked disabled aria-label="Notwendig (immer aktiv)" />
            </div>
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="font-medium text-foreground">Externe KI-Dienste im Browser</div>
                <div className="text-xs text-muted-foreground">Bild-Backup über Pollinations.ai und AI Horde direkt aus deinem Browser. Ohne Zustimmung erzeugen nur unsere Server Bilder.</div>
              </div>
              <Switch checked={external} onCheckedChange={setExternal} aria-label="Externe KI-Dienste erlauben" />
            </div>
          </div>
        )}

        <div className="mt-4 flex flex-col-reverse gap-2 sm:flex-row sm:items-center">
          <button onClick={() => (details ? decide(external) : setDetails(true))} className="h-11 rounded-full px-4 text-muted-foreground hover:text-foreground sm:mr-auto">
            {details ? "Auswahl speichern" : "Einstellungen"}
          </button>
          <button onClick={() => decide(false)} className="h-11 rounded-full border border-white/15 bg-white/[0.06] px-5 font-medium text-foreground hover:bg-white/[0.12]">
            Nur notwendige
          </button>
          <button onClick={() => decide(true)} className="h-11 rounded-full border border-white/15 bg-white/[0.06] px-5 font-medium text-foreground hover:bg-white/[0.12]">
            Alle akzeptieren
          </button>
        </div>
      </div>
    </div>
  );
}

/** Kurze Zeile mit allen Rechts-Links (Impressum muss von jeder Seite leicht erreichbar sein, § 5 ECG). */
export function LegalLinks({ className = "" }: { className?: string }) {
  const L = "hover:text-foreground hover:underline";
  return (
    <nav className={`flex flex-wrap items-center justify-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground ${className}`}>
      <Link to="/impressum" className={L}>Impressum</Link>
      <Link to="/datenschutz" className={L}>Datenschutz</Link>
      <Link to="/nutzungsbedingungen" className={L}>AGB</Link>
      <Link to="/ki-regeln" className={L}>KI-Regeln</Link>
      <button onClick={() => window.dispatchEvent(new Event(OPEN_CONSENT_EVENT))} className={L}>Cookie-Einstellungen</button>
    </nav>
  );
}

export { CONSENT_EVENT };
