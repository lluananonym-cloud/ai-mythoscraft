import LegalPage, { H, V } from "@/components/LegalPage";
import { LEGAL } from "@/lib/legal";

// Impressum nach § 5 ECG, § 14 UGB, § 63 GewO und Offenlegung nach § 25 MedienG (Österreich).
const Impressum = () => (
  <LegalPage title="Impressum">
    <H>Diensteanbieter und Medieninhaber</H>
    <p>
      <strong><V k="firma" /></strong><br />
      Inhaber: <V k="inhaber" /><br />
      <V k="strasse" /><br />
      <V k="plzOrt" />, <V k="land" />
    </p>

    <H>Kontakt</H>
    <p>
      E-Mail: <V k="email" />
      {LEGAL.telefon && <><br />Telefon: <V k="telefon" /></>}
    </p>

    <H>Unternehmensdaten</H>
    <ul>
      <li>Rechtsform: {LEGAL.firmenbuch ? "Eingetragenes Einzelunternehmen (e.U.)" : "Einzelunternehmen"}</li>
      <li>Unternehmensgegenstand: <V k="gegenstand" /></li>
      {LEGAL.firmenbuch && <li>Firmenbuchnummer und Firmenbuchgericht: <V k="firmenbuch" /></li>}
      {LEGAL.uid && <li>UID-Nummer: <V k="uid" /></li>}
      <li>Firmensitz: <V k="plzOrt" /></li>
    </ul>

    <H>Gewerberechtliche Angaben</H>
    <ul>
      <li>Gewerbe: <V k="gewerbe" /></li>
      <li>Gewerbebehörde: <V k="behoerde" /></li>
      <li>Mitglied der <V k="kammer" /></li>
      <li>Anwendbare Rechtsvorschriften: Gewerbeordnung (GewO), abrufbar unter <a href="https://www.ris.bka.gv.at" target="_blank" rel="noreferrer">www.ris.bka.gv.at</a></li>
    </ul>

    <H>Offenlegung nach § 25 Mediengesetz</H>
    <p>
      Medieninhaber: <V k="firma" />, <V k="strasse" />, <V k="plzOrt" />.<br />
      Grundlegende Richtung: Informationen über den KI-Dienst <V k="produkt" />, seine Funktionen, Preise und Neuigkeiten.
    </p>

    <H>Verbraucherstreitbeilegung</H>
    <p>
      Wir sind nicht verpflichtet und nicht bereit, an Streitbeilegungsverfahren vor einer Verbraucherschlichtungsstelle teilzunehmen.
      Bei Problemen schreib uns einfach an die oben genannte E-Mail-Adresse. Verbraucher:innen können sich unabhängig davon an die{" "}
      <a href="https://www.ombudsmann.at" target="_blank" rel="noreferrer">Internet Ombudsstelle</a> wenden.
    </p>

    <H>Haftung für Inhalte und Links</H>
    <p>
      Die Inhalte dieser Website wurden sorgfältig erstellt. Antworten, Bilder, Songs und Videos werden jedoch von künstlicher Intelligenz
      erzeugt und können fehlerhaft sein. Für Inhalte externer Websites, auf die wir verlinken, sind ausschließlich deren Betreiber verantwortlich.
      Bei Bekanntwerden von Rechtsverletzungen entfernen wir entsprechende Links oder Inhalte umgehend.
    </p>

    <H>Urheberrecht</H>
    <p>
      Texte, Grafiken, Logos und Software dieser Website sind urheberrechtlich geschützt. Eine Verwendung außerhalb der Grenzen des
      Urheberrechts ist nur mit unserer Zustimmung erlaubt. Zu KI-erzeugten Inhalten siehe die <a href="/nutzungsbedingungen">AGB</a>.
    </p>
  </LegalPage>
);

export default Impressum;
