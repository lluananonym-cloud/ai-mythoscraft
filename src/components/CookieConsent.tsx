// Hinweis-Banner zu Speicher und externen Diensten. Erscheint beim ersten Besuch, bis "Verstanden" gewählt ist.
// Es gibt kein Tracking, darum braucht es keine Einwilligung (§ 25 Abs. 2 Nr. 2 TDDDG). Das Bild-Backup aus dem
// Browser gehört zur angeforderten Bild-Erzeugung (Art. 6 Abs. 1 lit. b DSGVO) und lässt sich hier ausschalten.
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Switch } from "@/components/ui/switch";
import { OPEN_CONSENT_EVENT, CONSENT_EVENT, getConsent, setConsent } from "@/lib/consent";

export default function CookieConsent() {
  const [open, setOpen] = useState(false);
  const [details, setDetails] = useState(false);
  const [external, setExternal] = useState(true);

  useEffect(() => {
    if (!getConsent()) setOpen(true);
    const reopen = () => { setExternal(getConsent()?.external ?? true); setDetails(true); setOpen(true); };
    window.addEventListener(OPEN_CONSENT_EVENT, reopen);
    return () => window.removeEventListener(OPEN_CONSENT_EVENT, reopen);
  }, []);

  if (!open) return null;
  const save = () => { setConsent(external); setOpen(false); setDetails(false); };

  return (
    <div role="dialog" aria-modal="false" aria-labelledby="consent-title" className="fixed inset-x-0 bottom-0 z-[100] p-3 sm:p-4 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
      <div className="mx-auto max-w-2xl rounded-3xl border border-white/10 bg-[hsl(0_0%_8%/0.97)] p-5 text-sm shadow-[0_12px_48px_hsl(0_0%_0%/0.6)] backdrop-blur-xl">
        <h2 id="consent-title" className="mb-1.5 font-sans text-base font-semibold text-foreground">Cookies und Datenschutz</h2>
        <p className="text-muted-foreground leading-relaxed">
          Wir speichern nur, was für Anmeldung, Einstellungen und Sicherheit nötig ist. Kein Tracking, keine Werbe-Cookies.
          Wenn unsere Server gerade kein Bild erzeugen können, lädt dein Browser es direkt bei einem externen KI-Dienst. Dabei sieht der
          Anbieter deine IP-Adresse. Das kannst du in den Einstellungen ausschalten. Mehr dazu in der{" "}
          <Link to="/datenschutz" className="underline text-foreground">Datenschutzerklärung</Link> und im{" "}
          <Link to="/cookie" className="underline text-foreground">Cookie-Hinweis</Link>.
        </p>

        {details && (
          <div className="mt-4 space-y-3 rounded-2xl border border-white/10 bg-white/[0.03] p-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="font-medium text-foreground">Notwendig</div>
                <div className="text-xs text-muted-foreground">Anmeldung, Sitzung, Einstellungen, Sicherheit. Immer aktiv.</div>
              </div>
              <Switch checked disabled aria-label="Notwendig (immer aktiv)" />
            </div>
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="font-medium text-foreground">Bild-Backup im Browser</div>
                <div className="text-xs text-muted-foreground">Pollinations.ai und AI Horde, nur wenn du ein Bild anforderst und unsere Server ausgelastet sind. Ausgeschaltet erzeugen nur unsere Server Bilder.</div>
              </div>
              <Switch checked={external} onCheckedChange={setExternal} aria-label="Bild-Backup im Browser" />
            </div>
          </div>
        )}

        <div className="mt-4 flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end">
          {!details && (
            <button onClick={() => setDetails(true)} className="h-11 rounded-full border border-white/15 bg-white/[0.06] px-5 font-medium text-foreground hover:bg-white/[0.12]">
              Einstellungen
            </button>
          )}
          <button onClick={save} className="h-11 rounded-full bg-foreground px-5 font-medium text-background hover:opacity-90">
            {details ? "Speichern" : "Verstanden"}
          </button>
        </div>
      </div>
    </div>
  );
}

/** Kurze Zeile mit allen Rechts-Links (Impressum muss von jeder Seite leicht erreichbar sein, § 5 DDG). */
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
