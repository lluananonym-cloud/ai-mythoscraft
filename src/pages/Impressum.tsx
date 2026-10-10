import LegalPage, { H, V } from "@/components/LegalPage";
import { LEGAL } from "@/lib/legal";

// Impressum nach § 5 Digitale-Dienste-Gesetz (DDG) und § 18 Abs. 2 Medienstaatsvertrag (MStV), Deutschland.
const Impressum = () => (
  <LegalPage title="Impressum">
    <H>Angaben gemäß § 5 DDG</H>
    <p>
      <strong><V k="firma" /></strong><br />
      <V k="strasse" /><br />
      <V k="plzOrt" /><br />
      <V k="land" />
    </p>

    <H>Kontakt</H>
    <p>
      E-Mail: <V k="email" />
      {LEGAL.telefon && <><br />Telefon: <V k="telefon" /></>}
    </p>

    <H>Unternehmensdaten</H>
    <ul>
      <li>Rechtsform: Einzelunternehmen</li>
      <li>Tätigkeit: <V k="gegenstand" /></li>
      {LEGAL.register && <li>Registereintrag: <V k="register" /></li>}
      {LEGAL.uid && <li>Umsatzsteuer: <V k="uid" /></li>}
      <li>Zuständige Behörde für die Gewerbeanmeldung: <V k="behoerde" /></li>
    </ul>

    <H>Verantwortlich für den Inhalt nach § 18 Abs. 2 MStV</H>
    <p>
      <V k="inhaber" />, <V k="strasse" />, <V k="plzOrt" />
    </p>

    <H>Verbraucherstreitbeilegung</H>
    <p>
      Wir sind nicht bereit und nicht verpflichtet, an Streitbeilegungsverfahren vor einer Verbraucherschlichtungsstelle teilzunehmen (§ 36 VSBG).
      Bei Problemen schreib uns einfach an die oben genannte E-Mail-Adresse.
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
