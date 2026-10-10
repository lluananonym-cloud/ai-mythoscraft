import LegalPage, { H, V } from "@/components/LegalPage";

// Regeln für KI-Inhalte und Transparenz nach der KI-Verordnung (EU) 2024/1689.
const KIRegeln = () => (
  <LegalPage title="KI-Regeln und Transparenz">
    <H>Du sprichst mit einer KI</H>
    <p>
      <V k="produkt" /> ist ein KI-System. Antworten, Bilder, Songs, Videos und Code werden von KI-Modellen erzeugt und nicht von Menschen geprüft.
      Sie können falsch sein. Prüfe wichtige Informationen selbst. Erzeugte Bilder tragen im Chat den Hinweis „KI-generiert“.
    </p>

    <H>Was nicht erlaubt ist</H>
    <ul>
      <li>Rechtswidrige Inhalte, etwa Verhetzung, Gewaltaufrufe, Terrorpropaganda oder Darstellungen sexuellen Kindesmissbrauchs.</li>
      <li>Sexuelle oder entwürdigende Bilder realer Personen und täuschend echte Fälschungen (Deepfakes), um andere zu täuschen oder bloßzustellen.</li>
      <li>Urheberrechtlich geschützte Werke (Texte, Songs, Figuren) ohne Erlaubnis nachmachen oder als eigene ausgeben.</li>
      <li>Persönliche Daten anderer ohne deren Zustimmung eingeben oder veröffentlichen.</li>
      <li>Spam, Betrug, Phishing, Schadsoftware oder Angriffe auf fremde Systeme.</li>
      <li>Mobbing, Belästigung und Drohungen.</li>
    </ul>

    <H>Wenn du KI-Inhalte veröffentlichst</H>
    <ul>
      <li>Kennzeichne echt wirkende KI-Bilder, -Videos und -Stimmen als „KI-generiert“ (Art. 50 KI-Verordnung).</li>
      <li>Prüfe vor einer kommerziellen Nutzung, ob Rechte Dritter (Marken, Logos, bekannte Figuren, Stimmen) betroffen sind.</li>
    </ul>

    <H>Melden</H>
    <p>
      Rechtswidrige Inhalte oder Missbrauch kannst du an <V k="email" /> melden. Wir prüfen jede Meldung, entfernen rechtswidrige Inhalte und sperren bei Bedarf
      Konten. Strafbare Inhalte melden wir an die Behörden.
    </p>
  </LegalPage>
);

export default KIRegeln;
