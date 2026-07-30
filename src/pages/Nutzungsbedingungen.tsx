import TopNav from "@/components/TopNav";

const Nutzungsbedingungen = () => (
  <div className="min-h-screen flex flex-col">
    <TopNav />
    <main className="container max-w-3xl py-12 mx-auto">
      <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">Nutzungsbedingungen</h1>
      <section className="space-y-4 text-muted-foreground">
        <p>Durch die Nutzung von Mythos AI ("Dienst") akzeptieren Sie die nachfolgenden Bedingungen. Bitte lesen Sie diese sorgfältig.</p>
        <h2 className="font-semibold text-lg">1. Vertragsgegenstand</h2>
        <p>Mythos AI stellt eine Web‑Applikation zur Verfügung, die KI‑gestützte Chat‑, Bild‑, Musik‑ und Video‑Generierung sowie weitere Werkzeuge (z. B. AI‑Twin) anbietet.</p>
        <h2 className="font-semibold text-lg">2. Nutzerkonto</h2>
        <ul className="list-disc list-inside ml-4">
          <li>Sie müssen ein gültiges Konto erstellen und korrekte Angaben machen.</li>
          <li>Sie sind für die Sicherheit Ihrer Zugangsdaten verantwortlich.</li>
          <li>Bei Verdacht auf Missbrauch ist die sofortige Meldung an den Support erforderlich.</li>
        </ul>
        <h2 className="font-semibold text-lg">3. Lizenz und Nutzung</h2>
        <ul className="list-disc list-inside ml-4">
          <li>Der Dienst ist für den persönlichen und nicht‑kommerziellen Gebrauch kostenlos (Free‑Tier).</li>
          <li>Für erweiterte Funktionen (Light, Pro) gelten gesonderte kostenpflichtige Lizenzen.</li>
          <li>Sie dürfen die generierten Inhalte nicht gegen geltendes Recht verstoßen oder Rechte Dritter verletzen.</li>
        </ul>
        <h2 className="font-semibold text-lg">4. Urheberrecht & KI‑Inhalte</h2>
        <p>Die von der KI erzeugten Werke gelten als vom Nutzer erstellt. Sie erhalten das uneingeschränkte Nutzungsrecht, dürfen jedoch keine rechtswidrigen, beleidigenden oder pornografischen Inhalte verbreiten.</p>
        <h2 className="font-semibold text-lg">5. Haftungsbeschränkung</h2>
        <p>Wir übernehmen keine Gewähr für die Richtigkeit, Vollständigkeit oder Verfügbarkeit des Dienstes. Der Dienst wird "wie besehen" bereitgestellt; Haftung für direkte oder indirekte Schäden ist, soweit gesetzlich zulässig, ausgeschlossen.</p>
        <h2 className="font-semibold text-lg">6. Kündigung</h2>
        <p>Sie können Ihr Konto jederzeit löschen. Wir behalten uns das Recht vor, Nutzerkonten bei Verstößen gegen diese Bedingungen zu sperren oder zu löschen.</p>
        <h2 className="font-semibold text-lg">7. Änderungen</h2>
        <p>Wir können diese Nutzungsbedingungen jederzeit ändern. Die jeweils aktuelle Fassung ist auf dieser Seite einsehbar. Die fortgesetzte Nutzung gilt als Zustimmung.</p>
        <h2 className="font-semibold text-lg">8. Anwendbares Recht</h2>
        <p>Es gilt das Recht der Bundesrepublik Deutschland unter Ausschluss des UN-Kaufrechts.</p>
        <p>Bei Fragen kontaktieren Sie bitte <a href="mailto:support@mythoscraft.online" className="underline text-primary">support@mythoscraft.online</a>.</p>
      </section>
    </main>
  </div>
);

export default Nutzungsbedingungen;
