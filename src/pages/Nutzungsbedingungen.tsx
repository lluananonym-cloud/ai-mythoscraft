import LegalPage, { H, V } from "@/components/LegalPage";
import { TIER_LIMITS as TIERS } from "@/hooks/useSubscription";

// Allgemeine Geschäfts- und Nutzungsbedingungen (Deutschland: BGB, inkl. Widerrufsbelehrung nach Anlage 1 zu Art. 246a EGBGB).
const Nutzungsbedingungen = () => (
  <LegalPage title="Allgemeine Geschäftsbedingungen und Nutzungsbedingungen">
    <H>1. Geltungsbereich und Anbieter</H>
    <p>
      Diese Bedingungen gelten für alle Verträge über die Nutzung von <V k="produkt" /> (Website, Web-App, Desktop-Apps, Handy-App, Kommandozeile und
      Browser-Erweiterung, zusammen „Dienst“) zwischen dir und <V k="firma" />, <V k="strasse" />, <V k="plzOrt" /> („wir“). Abweichende Bedingungen
      gelten nur, wenn wir ihnen schriftlich zustimmen.
    </p>

    <H>2. Leistungen</H>
    <p>
      Der Dienst bietet KI-gestützte Funktionen wie Chat, Recherche, Bild-, Musik- und Video-Erzeugung, Code-Assistenz, Sprachfunktionen und Werkzeuge
      für Minecraft-Server. Die KI-Modelle stammen teilweise von Drittanbietern. Wir bemühen uns um eine hohe Verfügbarkeit, schulden aber keine
      ununterbrochene Erreichbarkeit. Wartungen, Ausfälle von Drittanbietern und Nutzungslimits sind möglich. Wir dürfen Funktionen weiterentwickeln,
      ändern oder einstellen, solange der wesentliche Leistungsumfang deines bezahlten Tarifs erhalten bleibt.
    </p>

    <H>3. Konto</H>
    <ul>
      <li>Für die meisten Funktionen brauchst du ein Konto. Deine Angaben müssen stimmen.</li>
      <li>Die Nutzung ist ab 16 Jahren erlaubt, darunter nur mit Zustimmung eines Elternteils. Bezahlte Tarife dürfen Minderjährige nur mit Zustimmung der Eltern abschließen.</li>
      <li>Halte deine Zugangsdaten geheim. Bei Verdacht auf Missbrauch schreib uns sofort an <V k="email" />.</li>
    </ul>

    <H>4. Tarife und Preise</H>
    <ul>
      <li><strong>{TIERS.free.label}</strong>: {TIERS.free.priceLabel}, {TIERS.free.chatsPerDay} Nachrichten pro Tag, mit gelegentlicher Werbung.</li>
      <li><strong>{TIERS.light.label}</strong>: {TIERS.light.priceLabel}, {TIERS.light.chatsPerDay} Nachrichten pro Tag und Bilder, seltener Werbung.</li>
      <li><strong>{TIERS.pro.label}</strong>: {TIERS.pro.priceLabel}, mit allen Funktionen und ohne Werbung.</li>
    </ul>
    <p>
      Alle Preise sind Endpreise in Euro. Als Kleinunternehmer nach § 19 UStG weisen wir keine Umsatzsteuer aus. Bezahlt wird im Voraus über PayPal. Bezahlte Tarife laufen monatlich und verlängern sich automatisch um einen Monat, wenn du nicht vor Ende des
      laufenden Monats kündigst. Kündigen kannst du jederzeit im Dashboard über den Kündigungsbutton („Abo kündigen“, § 312k BGB), in deinem PayPal-Konto oder formlos per E-Mail. Der bezahlte Monat läuft bis zum Ende weiter. Gutschein- und Einladungscodes können nicht gegen Geld eingelöst werden.
      Preisänderungen gelten erst ab der nächsten Verlängerung und werden dir mindestens 30 Tage vorher mitgeteilt. Du kannst dann zum Änderungszeitpunkt kündigen.
    </p>

    <H>5. Widerrufsbelehrung für Verbraucher:innen</H>
    <p><strong>Widerrufsrecht</strong></p>
    <p>
      Du hast das Recht, binnen vierzehn Tagen ohne Angabe von Gründen diesen Vertrag zu widerrufen. Die Widerrufsfrist beträgt vierzehn Tage ab dem Tag des
      Vertragsabschlusses. Um dein Widerrufsrecht auszuüben, musst du uns (<V k="firma" />, <V k="strasse" />, <V k="plzOrt" />, E-Mail: <V k="email" />)
      mittels einer eindeutigen Erklärung (z. B. ein mit der Post versandter Brief oder eine E-Mail) über deinen Entschluss, diesen Vertrag zu widerrufen,
      informieren. Du kannst dafür das unten stehende Muster-Widerrufsformular verwenden, das jedoch nicht vorgeschrieben ist. Zur Wahrung der Widerrufsfrist
      reicht es aus, dass du die Mitteilung über die Ausübung des Widerrufsrechts vor Ablauf der Widerrufsfrist absendest.
    </p>
    <p><strong>Folgen des Widerrufs</strong></p>
    <p>
      Wenn du diesen Vertrag widerrufst, haben wir dir alle Zahlungen, die wir von dir erhalten haben, unverzüglich und spätestens binnen vierzehn Tagen ab dem
      Tag zurückzuzahlen, an dem die Mitteilung über deinen Widerruf dieses Vertrags bei uns eingegangen ist. Für diese Rückzahlung verwenden wir dasselbe
      Zahlungsmittel, das du bei der ursprünglichen Transaktion eingesetzt hast (PayPal), es sei denn, mit dir wurde ausdrücklich etwas anderes vereinbart; in keinem
      Fall werden dir wegen dieser Rückzahlung Entgelte berechnet. Hast du verlangt, dass die Dienstleistung während der Widerrufsfrist beginnen soll, so hast du
      uns einen angemessenen Betrag zu zahlen, der dem Anteil der bis zu dem Zeitpunkt, zu dem du uns von der Ausübung des Widerrufsrechts hinsichtlich dieses
      Vertrags unterrichtest, bereits erbrachten Dienstleistungen im Vergleich zum Gesamtumfang der im Vertrag vorgesehenen Dienstleistungen entspricht.
    </p>
    <p><strong>Muster-Widerrufsformular</strong></p>
    <p className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
      (Wenn du den Vertrag widerrufen willst, dann fülle bitte dieses Formular aus und sende es zurück.)<br />
      An <V k="firma" />, <V k="strasse" />, <V k="plzOrt" />, E-Mail: <V k="email" />:<br />
      Hiermit widerrufe(n) ich/wir (*) den von mir/uns (*) abgeschlossenen Vertrag über die Erbringung der folgenden Dienstleistung: Tarif …<br />
      Bestellt am (*) …<br />
      Name des/der Verbraucher(s) …<br />
      Anschrift des/der Verbraucher(s) …<br />
      Unterschrift des/der Verbraucher(s) (nur bei Mitteilung auf Papier)<br />
      Datum …<br />
      (*) Unzutreffendes streichen.
    </p>

    <H>6. Erlaubte Nutzung</H>
    <p>Du darfst den Dienst nicht verwenden, um</p>
    <ul>
      <li>rechtswidrige Inhalte zu erzeugen oder zu verbreiten (z. B. Hetze, Gewaltaufrufe, Darstellungen sexuellen Kindesmissbrauchs, Verleumdung),</li>
      <li>Rechte Dritter zu verletzen (Urheber-, Marken-, Persönlichkeitsrechte), etwa durch täuschend echte Bilder realer Personen ohne deren Zustimmung,</li>
      <li>Schadsoftware, Spam, Betrug oder Angriffe auf Systeme vorzubereiten,</li>
      <li>Limits zu umgehen, den Dienst automatisiert auszulesen oder weiterzuverkaufen, außer über unsere offizielle API mit eigenem Schlüssel.</li>
    </ul>
    <p>Details stehen in den <a href="/ki-regeln">KI-Regeln</a>. Bei Verstößen dürfen wir Inhalte entfernen und Konten nach vorheriger Warnung, bei schweren Verstößen sofort, sperren.</p>

    <H>7. KI-Inhalte und Rechte daran</H>
    <ul>
      <li>Deine Eingaben bleiben deine. Du räumst uns nur die Rechte ein, die wir brauchen, um den Dienst zu erbringen (Speichern, Verarbeiten, Übermitteln an KI-Anbieter).</li>
      <li>An den für dich erzeugten Ergebnissen räumen wir dir, soweit uns Rechte daran zustehen, ein unbefristetes, weltweites und auch kommerzielles Nutzungsrecht ein. Bei Musik und Bildern können zusätzlich die Bedingungen des jeweiligen KI-Anbieters gelten.</li>
      <li>KI-Ergebnisse sind nach deutschem Urheberrecht oft nicht geschützt, und ähnliche Ergebnisse können auch anderen Nutzer:innen entstehen. Wir garantieren nicht, dass Ergebnisse frei von Rechten Dritter sind. Prüfe sie vor einer Veröffentlichung.</li>
      <li>KI-Ergebnisse können falsch, unvollständig oder veraltet sein. Sie ersetzen keine Rechts-, Steuer-, Medizin- oder Finanzberatung.</li>
      <li>Wenn du KI-erzeugte Bilder, Videos oder Töne veröffentlichst, die echt wirken (Deepfakes), musst du sie als KI-generiert kennzeichnen (Art. 50 KI-Verordnung).</li>
    </ul>

    <H>8. Gewährleistung</H>
    <p>Es gelten die gesetzlichen Rechte bei Mängeln, für digitale Produkte insbesondere die §§ 327 ff. BGB. Wir stellen dir dabei auch die nötigen Aktualisierungen bereit (§ 327f BGB).</p>

    <H>9. Haftung</H>
    <p>
      Wir haften unbeschränkt für Vorsatz und grobe Fahrlässigkeit sowie für Personenschäden. Bei leichter Fahrlässigkeit haften wir nur für die Verletzung
      wesentlicher Vertragspflichten und begrenzt auf den typischerweise vorhersehbaren Schaden. Gegenüber Unternehmer:innen ist die Haftung für leichte
      Fahrlässigkeit, entgangenen Gewinn und Datenverlust ausgeschlossen. Für den kostenlosen Tarif haften wir nur für Vorsatz und grobe Fahrlässigkeit.
      Zwingende gesetzliche Ansprüche, etwa nach dem Produkthaftungsgesetz, bleiben unberührt. Gegenüber Verbraucher:innen gilt diese Begrenzung nicht für die Rechte bei Mängeln digitaler Produkte.
    </p>

    <H>10. Werbung buchen</H>
    <p>
      Für gebuchte Anzeigen im Chat gilt ergänzend das per E-Mail übermittelte Angebot. Wir dürfen Anzeigen ablehnen, die rechtswidrig sind oder diesen
      Bedingungen widersprechen. Anzeigen werden als „Werbung“ gekennzeichnet.
    </p>

    <H>11. Laufzeit und Kündigung</H>
    <p>
      Den kostenlosen Tarif kannst du jederzeit beenden, indem du die Löschung deines Kontos verlangst. Bezahlte Tarife siehe Punkt 4. Wir können den Vertrag
      mit einer Frist von 30 Tagen kündigen, aus wichtigem Grund (z. B. schwerer Verstoß gegen Punkt 6) auch sofort. Bereits bezahlte Zeiträume erstatten wir dann anteilig, außer bei einer Kündigung wegen deines Verstoßes.
    </p>

    <H>12. Änderungen dieser Bedingungen</H>
    <p>
      Änderungen teilen wir dir mindestens 30 Tage vorher per E-Mail oder im Dienst mit. Wesentliche Änderungen zu deinem Nachteil gelten nur mit deiner Zustimmung.
      Sonst kannst du bis zum Inkrafttreten kostenlos kündigen.
    </p>

    <H>13. Anwendbares Recht und Gerichtsstand</H>
    <p>
      Es gilt deutsches Recht unter Ausschluss des UN-Kaufrechts. Für Verbraucher:innen mit gewöhnlichem Aufenthalt in einem anderen EU-Staat bleiben die
      zwingenden Schutzvorschriften ihres Heimatstaats unberührt (Art. 6 Rom-I-Verordnung). Gerichtsstand für Kaufleute ist unser Geschäftssitz. Für
      Verbraucher:innen gelten die gesetzlichen Gerichtsstände. Zur Verbraucherschlichtung siehe <a href="/impressum">Impressum</a>.
    </p>

    <H>14. Schlussbestimmungen</H>
    <p>Sollte eine Bestimmung unwirksam sein, bleiben die übrigen wirksam. Kontakt für alle Fragen: <V k="email" />.</p>
  </LegalPage>
);

export default Nutzungsbedingungen;
