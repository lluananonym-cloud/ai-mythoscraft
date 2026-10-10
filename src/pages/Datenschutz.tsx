import LegalPage, { H, V } from "@/components/LegalPage";
import { openConsent } from "@/lib/consent";

// Datenschutzerklärung nach Art. 13/14 DSGVO, DSG und § 165 TKG 2021.
const Datenschutz = () => (
  <LegalPage title="Datenschutzerklärung">
    <p>
      Wir verarbeiten personenbezogene Daten nur, soweit das für den Betrieb von <V k="produkt" /> nötig ist, und nach der
      Datenschutz-Grundverordnung (DSGVO), dem österreichischen Datenschutzgesetz (DSG) und dem Telekommunikationsgesetz 2021 (TKG 2021).
    </p>

    <H>1. Verantwortlicher</H>
    <p>
      <V k="firma" />, Inhaber <V k="inhaber" />, <V k="strasse" />, <V k="plzOrt" />, <V k="land" />.<br />
      E-Mail: <V k="email" />. Ein Datenschutzbeauftragter ist nicht bestellt und gesetzlich nicht erforderlich.
    </p>

    <H>2. Welche Daten wir verarbeiten und warum</H>
    <ul>
      <li>
        <strong>Besuch der Website:</strong> IP-Adresse, Zeitpunkt, aufgerufene Seite, Browser und Gerätetyp. Das ist technisch nötig, um die Seite
        auszuliefern und vor Missbrauch zu schützen (Art. 6 Abs. 1 lit. f DSGVO). Server-Protokolle werden nur so lange aufbewahrt, wie es für Betrieb und Sicherheit nötig ist.
      </li>
      <li>
        <strong>Konto:</strong> E-Mail-Adresse, Passwort (nur verschlüsselt gespeichert), Anzeigename, gewähltes Abo und Einstellungen. Bei Anmeldung über
        Google, Apple oder Microsoft erhalten wir von dort Name, E-Mail und Profilbild. Zweck: Vertragserfüllung (Art. 6 Abs. 1 lit. b DSGVO).
      </li>
      <li>
        <strong>Chats und erzeugte Inhalte:</strong> Deine Nachrichten, hochgeladene Dateien und Bilder, erzeugte Bilder, Songs und Videos sowie gespeicherte
        Erinnerungen und Personas. Wir brauchen sie, um dir Antworten zu liefern und deinen Verlauf anzuzeigen (Art. 6 Abs. 1 lit. b DSGVO).
        Wir verwenden deine Chats <strong>nicht</strong>, um eigene KI-Modelle zu trainieren.
      </li>
      <li>
        <strong>Bezahlte Abos:</strong> Tarif, Laufzeit, PayPal-Abo-ID und Zahlungsstatus. Zweck: Vertragserfüllung und gesetzliche Aufbewahrungspflichten (Art. 6 Abs. 1 lit. b und c DSGVO).
      </li>
      <li>
        <strong>Werbeanfragen:</strong> Wenn du über „Werbung buchen“ eine Anzeige anfragst, verarbeiten wir Produkt, Link, Anzeigentext, Zeitraum und
        Kontakt-E-Mail, um die Anfrage zu prüfen und dir ein Angebot zu schicken (Art. 6 Abs. 1 lit. b DSGVO). Die Anfrage wird über FormSubmit per E-Mail an uns weitergeleitet.
      </li>
      <li>
        <strong>Desktop-App, Browser-Erweiterung und Fernsteuerung:</strong> Wenn du Mythos Code oder die Erweiterung koppelst, verarbeiten wir einen Kopplungscode
        und die Befehle, die du von unterwegs schickst. Dateien auf deinem PC bleiben auf deinem PC, außer du schickst sie bewusst an die KI.
      </li>
      <li>
        <strong>Support-Anfragen:</strong> Inhalt deiner Nachricht und deine Kontaktdaten, um die Anfrage zu beantworten (Art. 6 Abs. 1 lit. b bzw. f DSGVO).
      </li>
    </ul>

    <H>3. Künstliche Intelligenz</H>
    <p>
      Antworten, Bilder, Songs und Videos werden von KI-Modellen erzeugt. Dafür wird dein Eingabetext (und gegebenenfalls angehängte Bilder) an den jeweiligen
      KI-Anbieter übermittelt, der das Ergebnis berechnet. Es gibt keine automatisierte Entscheidung mit rechtlicher Wirkung im Sinne von Art. 22 DSGVO.
      Gib bitte keine sensiblen Daten (Gesundheit, Passwörter, Bankdaten) oder Daten anderer Personen ohne deren Erlaubnis ein.
    </p>

    <H>4. Empfänger und Auftragsverarbeiter</H>
    <p>Wir setzen folgende Dienstleister ein. Mit Auftragsverarbeitern bestehen Verträge nach Art. 28 DSGVO.</p>
    <ul>
      <li><strong>Lovable</strong> (Lovable Labs Incorporated / Lovable AB, Schweden): Hosting der Website und KI-Schnittstelle (AI Gateway).</li>
      <li><strong>Supabase</strong> (Supabase Inc., USA): Datenbank, Anmeldung, Dateispeicher und Server-Funktionen („Lovable Cloud“).</li>
      <li><strong>Google</strong> (Google Ireland Ltd. / Google LLC, USA): KI-Modelle Gemini und Lyria (Text, Bild, Musik) sowie optional die Anmeldung mit Google.</li>
      <li><strong>OpenAI</strong> (OpenAI Ireland Ltd. / OpenAI LLC, USA): KI-Modelle, sofern du ein solches Modell wählst (über das AI Gateway).</li>
      <li><strong>NVIDIA</strong> (NVIDIA Corporation, USA): Ausweich-KI-Modelle für Text und Bild.</li>
      <li><strong>Pollinations.ai</strong> und <strong>AI Horde</strong> (Haidra, Niederlande): kostenlose Bild-Backups, wenn die anderen Anbieter ausgelastet sind.</li>
      <li><strong>ElevenLabs</strong> (Eleven Labs Inc., USA): nur, falls für Songs freigeschaltet.</li>
      <li><strong>Suche und Webseiten-Abruf</strong> für die Recherche-Funktion: DuckDuckGo und Jina AI (der Suchbegriff bzw. die aufgerufene Adresse wird übermittelt).</li>
      <li><strong>PayPal</strong> (PayPal (Europe) S.à r.l. et Cie, S.C.A., Luxemburg): Abwicklung bezahlter Abos. PayPal ist dabei eigener Verantwortlicher. Wir erhalten nur die Abo-ID, den Status und das Datum der nächsten Abbuchung, keine Bank- oder Kartendaten. Die PayPal-Knöpfe werden erst geladen, wenn du ein Abo abschließen willst, und PayPal setzt dann eigene, für die Zahlung nötige Cookies.</li>
      <li><strong>Apple</strong> und <strong>Microsoft</strong>: nur, wenn du dich damit anmeldest.</li>
      <li><strong>FormSubmit</strong> (formsubmit.co): Weiterleitung von Werbeanfragen per E-Mail.</li>
      <li><strong>GitHub</strong> (GitHub Inc., USA): Bereitstellung der Downloads (Desktop-Apps).</li>
      <li><strong>Minecraft-Dienste</strong> (mc-heads.net, mcsrvstat.us): Anzeige von Skins und Serverstatus, wenn du diese Funktionen nutzt.</li>
    </ul>

    <H>5. Übermittlung in Drittländer</H>
    <p>
      Einige Anbieter sitzen in den USA. Die Übermittlung erfolgt auf Grundlage des Angemessenheitsbeschlusses der EU-Kommission zum EU-US Data Privacy
      Framework (Art. 45 DSGVO), sofern der Anbieter zertifiziert ist, und ansonsten auf Grundlage von EU-Standardvertragsklauseln (Art. 46 Abs. 2 lit. c DSGVO).
    </p>

    <H>6. Cookies, lokaler Speicher und externe Dienste</H>
    <p>
      Wir verwenden keine Tracking- oder Werbe-Cookies. Im lokalen Speicher deines Browsers liegen nur Daten, die technisch nötig sind: deine Anmeldung,
      Einstellungen (z. B. gewähltes Modell, Seitenleiste) und deine Cookie-Entscheidung (§ 165 Abs. 3 TKG 2021, keine Einwilligung nötig).
    </p>
    <p>
      <strong>Mit deiner Einwilligung</strong> (Art. 6 Abs. 1 lit. a DSGVO) lädt dein Browser Bilder direkt bei Pollinations.ai oder AI Horde, wenn unsere Server
      gerade keine Bilder erzeugen können. Dabei erhält der Anbieter deine IP-Adresse und den Bild-Wunsch. Du kannst die Einwilligung jederzeit widerrufen:{" "}
      <button onClick={openConsent} className="text-foreground underline">Cookie-Einstellungen öffnen</button>.
      Schriftarten werden von unserem eigenen Server geladen, nicht von Google.
    </p>
    <p>
      Offline-Funktionen (z. B. <code>/offline</code>, Spracherkennung) laden die KI-Modelle einmalig von Hugging Face bzw. jsDelivr in deinen Browser. Die
      Berechnung läuft danach auf deinem Gerät.
    </p>

    <H>7. Werbung im Chat</H>
    <p>
      In kostenlosen Tarifen zeigen wir gelegentlich gekennzeichnete Werbung. Die Auswahl erfolgt zufällig und ohne Profilbildung. Es werden keine Daten an Werbekunden weitergegeben.
    </p>

    <H>8. Speicherdauer</H>
    <p>
      Kontodaten, Chats und erzeugte Inhalte speichern wir, bis du sie löschst oder die Löschung deines Kontos verlangst (formlos per E-Mail). Danach werden sie innerhalb von 30 Tagen entfernt,
      soweit keine gesetzlichen Aufbewahrungspflichten bestehen (z. B. 7 Jahre für Buchhaltungsunterlagen nach § 132 BAO).
    </p>

    <H>9. Mindestalter</H>
    <p>
      Die Nutzung ist ab 14 Jahren erlaubt (§ 4 Abs. 4 DSG). Jüngere dürfen <V k="produkt" /> nur mit Zustimmung eines Elternteils nutzen.
    </p>

    <H>10. Deine Rechte</H>
    <p>
      Du hast das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung, Datenübertragbarkeit und Widerspruch (Art. 15 bis 21 DSGVO) sowie
      das Recht, eine Einwilligung jederzeit mit Wirkung für die Zukunft zu widerrufen. Schreib dafür an <V k="email" />.
    </p>
    <p>
      Wenn du meinst, dass wir deine Daten rechtswidrig verarbeiten, kannst du dich bei der Aufsichtsbehörde beschweren: Österreichische Datenschutzbehörde,
      Barichgasse 40–42, 1030 Wien, <a href="https://www.dsb.gv.at" target="_blank" rel="noreferrer">www.dsb.gv.at</a>.
    </p>

    <H>11. Sicherheit</H>
    <p>
      Alle Verbindungen sind verschlüsselt (HTTPS). Zugriff auf deine Daten haben nur du und, soweit für Betrieb und Support nötig, wir. API-Schlüssel liegen nur auf dem Server.
    </p>

    <H>12. Änderungen</H>
    <p>Wir passen diese Erklärung an, wenn sich der Dienst oder die Rechtslage ändert. Es gilt die jeweils hier veröffentlichte Fassung.</p>
  </LegalPage>
);

export default Datenschutz;
