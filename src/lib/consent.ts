// Einwilligung für Speicher und externe Dienste (DSGVO, § 165 Abs. 3 TKG 2021).
// Notwendiges (Anmeldung, Einstellungen, Sicherheit) braucht keine Einwilligung und ist immer an.
// "Externe Dienste" = Inhalte, die dein Browser direkt bei Drittanbietern lädt (z. B. Bild-Backup).

export type Consent = { v: 1; necessary: true; external: boolean; at: string };

const KEY = "mythos.consent";
export const CONSENT_EVENT = "mythos-consent";
export const OPEN_CONSENT_EVENT = "mythos-consent-open";

export function getConsent(): Consent | null {
  try {
    const raw = localStorage.getItem(KEY);
    const c = raw ? JSON.parse(raw) : null;
    return c?.v === 1 ? (c as Consent) : null;
  } catch { return null; }
}

export function setConsent(external: boolean) {
  const c: Consent = { v: 1, necessary: true, external, at: new Date().toISOString() };
  try { localStorage.setItem(KEY, JSON.stringify(c)); } catch { /* privater Modus */ }
  window.dispatchEvent(new CustomEvent(CONSENT_EVENT, { detail: c }));
}

/** Darf der Browser externe Dienste direkt laden? (ohne Entscheidung: nein) */
export const externalAllowed = () => getConsent()?.external === true;

/** Öffnet das Cookie-Fenster erneut (z. B. über den Link "Cookie-Einstellungen"). */
export const openConsent = () => window.dispatchEvent(new Event(OPEN_CONSENT_EVENT));
