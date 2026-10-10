// Speicher-Hinweis und Schalter für externe Dienste (DSGVO, § 25 TDDDG).
// Notwendiges (Anmeldung, Einstellungen, Sicherheit) braucht keine Einwilligung (§ 25 Abs. 2 Nr. 2 TDDDG) und ist immer an.
// "Externe Dienste" = Inhalte, die dein Browser direkt bei Drittanbietern lädt (z. B. Bild-Backup).

export type Consent = { v: 2; necessary: true; external: boolean; at: string };

const KEY = "mythos.consent";
export const CONSENT_EVENT = "mythos-consent";
export const OPEN_CONSENT_EVENT = "mythos-consent-open";

export function getConsent(): Consent | null {
  try {
    const raw = localStorage.getItem(KEY);
    const c = raw ? JSON.parse(raw) : null;
    // v1 (alter Banner mit „Nur notwendige“) zählt nicht mehr, damit niemand ungewollt ohne Bild-Backup bleibt.
    return c?.v === 2 ? (c as Consent) : null;
  } catch { return null; }
}

export function setConsent(external: boolean) {
  const c: Consent = { v: 2, necessary: true, external, at: new Date().toISOString() };
  try { localStorage.setItem(KEY, JSON.stringify(c)); } catch { /* privater Modus */ }
  window.dispatchEvent(new CustomEvent(CONSENT_EVENT, { detail: c }));
}

/** Hat jemand das Bild-Backup aus dem Browser in den Cookie-Einstellungen ausgeschaltet?
 *  Ohne Entscheidung läuft es als Teil der angeforderten Bild-Erzeugung (Art. 6 Abs. 1 lit. b DSGVO). */
export const externalBlocked = () => getConsent()?.external === false;

/** Öffnet das Cookie-Fenster erneut (z. B. über den Link "Cookie-Einstellungen"). */
export const openConsent = () => window.dispatchEvent(new Event(OPEN_CONSENT_EVENT));
