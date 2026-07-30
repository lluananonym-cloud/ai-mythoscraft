import TopNav from "@/components/TopNav";

const Datenschutz = () => (
  <div className="min-h-screen flex flex-col">
    <TopNav />
    <main className="container max-w-3xl py-12 mx-auto">
      <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">Datenschutzerklärung</h1>
      <section className="space-y-4 text-muted-foreground">
        <p>Wir nehmen den Schutz Ihrer personenbezogenen Daten sehr ernst und behandeln diese vertraulich gemäß den geltenden Datenschutzbestimmungen, insbesondere der DSGVO.</p>
        <h2 className="font-semibold text-lg">1. Verantwortliche Stelle</h2>
        <p>Mythos AI GmbH, Musterstraße 123, 12345 Beispielstadt, Deutschland ("Verantwortlicher").</p>
        <h2 className="font-semibold text-lg">2. Erhebung und Verarbeitung von Daten</h2>
        <p>Wir erheben und verarbeiten Daten, die Sie uns im Rahmen der Nutzung unseres Dienstes freiwillig zur Verfügung stellen, z. B.:</p>
        <ul className="list-disc list-inside ml-4">
          <li>E‑Mail‑Adresse und Profilinformationen (z. B. Anzeigename).</li>
          <li>Inhalte Ihrer Chats, inklusive AI‑Twin‑Training‑Beispiele.</li>
          <li>Technische Daten (IP‑Adresse, Browser‑Informationen, Gerätetyp) zur Gewährleistung von Sicherheit und Betrieb.</li>
        </ul>
        <h2 className="font-semibold text-lg">3. Zweck der Verarbeitung</h2>
        <ul className="list-disc list-inside ml-4">
          <li>Bereitstellung und Betrieb des AI‑Chat‑Dienstes.</li>
          <li>Personalisierung von AI‑Twin‑Modellen (nur für den jeweiligen Account).</li>
          <li>Statistische Auswertungen zur Verbesserung des Services (anonymisiert).</li>
          <li>Erfüllung gesetzlicher Pflichten (z. B. Aufbewahrungspflichten).</li>
        </ul>
        <h2 className="font-semibold text-lg">4. Weitergabe an Dritte</h2>
        <p>Ihre Daten werden nicht an Dritte verkauft oder vermarktet. Eine Weitergabe erfolgt nur, wenn es zur Erfüllung des Vertrages erforderlich ist (z. B. Supabase als Datenbank‑Provider) oder gesetzlich vorgeschrieben ist.</p>
        <h2 className="font-semibold text-lg">5. Ihre Rechte</h2>
        <p>Sie haben das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung, Datenübertragbarkeit und Widerspruch. Für Anfragen wenden Sie sich bitte an <a href="mailto:support@mythoscraft.online" className="underline text-primary">support@mythoscraft.online</a>.</p>
        <h2 className="font-semibold text-lg">6. Datensicherheit</h2>
        <p>Wir setzen technische und organisatorische Sicherheitsmaßnahmen ein, um Ihre Daten vor unbefugtem Zugriff, Verlust oder Manipulation zu schützen.</p>
        <h2 className="font-semibold text-lg">7. Änderungen dieser Erklärung</h2>
        <p>Wir behalten uns vor, diese Datenschutzerklärung zu aktualisieren. Die jeweils aktuelle Version ist jederzeit auf dieser Seite verfügbar.</p>
      </section>
    </main>
  </div>
);

export default Datenschutz;
