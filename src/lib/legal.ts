// Alle Angaben für Impressum, Datenschutz und AGB an EINER Stelle.
// Alles in [eckigen Klammern] ist ein Platzhalter und muss vor dem Veröffentlichen ersetzt werden.
// Solange ein Platzhalter drin ist, zeigen die Rechtsseiten ihn gelb markiert an.

export const LEGAL = {
  /** Firmenwortlaut laut Firmenbuch, z. B. "Max Muster e.U." */
  firma: "[Vorname Nachname] e.U.",
  /** Inhaber:in des Einzelunternehmens */
  inhaber: "[Vorname Nachname]",
  /** Anschrift des Unternehmenssitzes (keine Postfachadresse) */
  strasse: "[Straße Hausnummer/Tür]",
  plzOrt: "[PLZ Ort]",
  land: "Österreich",
  email: "[kontakt@deine-domain.at]",
  /** optional: "" blendet die Zeile aus */
  telefon: "[+43 …] (optional, empfohlen)",
  /** Firmenbuchnummer und -gericht, z. B. "FN 123456a, Landesgericht Linz".
   *  Noch nicht im Firmenbuch eingetragen? Dann "" eintragen und bei firma nur deinen Namen (ohne "e.U."). */
  firmenbuch: "[FN …], [Landes-/Handelsgericht …]",
  /** UID-Nummer, falls vorhanden; sonst Satz zur Kleinunternehmerregelung stehen lassen ("" blendet die Zeile aus) */
  uid: "[ATU…] – oder: keine UID, Kleinunternehmer gemäß § 6 Abs. 1 Z 27 UStG",
  /** Unternehmensgegenstand */
  gegenstand: "Entwicklung und Betrieb von Software und KI-gestützten Online-Diensten",
  /** Gewerbebehörde (Bezirkshauptmannschaft oder Magistrat) */
  behoerde: "[Bezirkshauptmannschaft/Magistrat …]",
  /** Gewerbe laut Gewerbeschein */
  gewerbe: "Dienstleistungen in der automatischen Datenverarbeitung und Informationstechnik (freies Gewerbe)",
  kammer: "Wirtschaftskammer [Bundesland], Fachgruppe Unternehmensberatung, Buchhaltung und Informationstechnologie (UBIT)",
  /** Stand der Rechtstexte */
  stand: "Oktober 2026",
  produkt: "Mythos AI",
  website: "mythoscraft.online",
};

/** true, solange noch ein Platzhalter in den Angaben steht. */
export const hasPlaceholders = Object.values(LEGAL).some((v) => /\[[^\]]+\]/.test(v));
