// Alle Angaben für Impressum, Datenschutz und AGB an EINER Stelle (Anbieter in Deutschland).
// Alles in [eckigen Klammern] ist ein Platzhalter und muss vor dem Veröffentlichen ersetzt werden.
// Solange ein Platzhalter drin ist, zeigen die Rechtsseiten ihn gelb markiert an.

export const LEGAL = {
  /** Name des Anbieters. Als Einzelunternehmer ohne Handelsregister-Eintrag einfach Vor- und Nachname. */
  firma: "Luan Schneider",
  /** Inhaber (bei Einzelunternehmen dieselbe Person) */
  inhaber: "Luan Schneider",
  /** Ladungsfähige Anschrift (Wohnadresse oder Adresse eines Impressum-Service, kein Postfach) */
  strasse: "[Straße Hausnummer]",
  plzOrt: "[PLZ Ort]",
  land: "Deutschland",
  email: "[kontakt@deine-domain.de]",
  /** optional: "" blendet die Zeile aus */
  telefon: "",
  /** Handelsregister, nur falls eingetragen, z. B. "HRA 12345, Amtsgericht München". Sonst "" (Zeile wird ausgeblendet). */
  register: "",
  /** USt-IdNr. oder Satz zur Kleinunternehmerregelung ("" blendet die Zeile aus) */
  uid: "keine USt-IdNr., Kleinunternehmer gemäß § 19 UStG (es wird keine Umsatzsteuer berechnet)",
  /** Unternehmensgegenstand */
  gegenstand: "Entwicklung und Betrieb von Software und KI-gestützten Online-Diensten",
  /** Zuständiges Gewerbeamt (Stadt oder Gemeinde, bei der das Gewerbe angemeldet ist) */
  behoerde: "[Gewerbeamt der Stadt/Gemeinde …]",
  /** Bundesland, für die zuständige Datenschutz-Aufsichtsbehörde */
  bundesland: "[Bundesland]",
  /** Stand der Rechtstexte */
  stand: "Oktober 2026",
  produkt: "Mythos AI",
  website: "mythoscraft.online",
};

/** true, solange noch ein Platzhalter in den Angaben steht. */
export const hasPlaceholders = Object.values(LEGAL).some((v) => /\[[^\]]+\]/.test(v));
