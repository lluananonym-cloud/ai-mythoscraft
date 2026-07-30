import TopNav from "@/components/TopNav";

const KIRegeln = () => (
  <div className="min-h-screen flex flex-col">
    <TopNav />
    <main className="container max-w-3xl py-12 mx-auto">
      <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">Regeln für KI‑generierte Inhalte</h1>
      <section className="space-y-4 text-muted-foreground">
        <p>Die Nutzung der KI‑gestützten Funktionen von Mythos AI unterliegt folgenden Richtlinien. Durch die Nutzung erklären Sie sich mit diesen Bedingungen einverstanden.</p>
        <ul className="list-disc list-inside ml-4">
          <li>Erzeugen Sie keine Inhalte, die gegen geltendes Recht verstoßen (z. B. Hassrede, defamatory Äußerungen, Gewaltverherrlichung, Kinder‑pornografie).</li>
          <li>Veröffentlichen Sie keine urheberrechtlich geschützten Materialien ohne entsprechende Lizenz oder Erlaubnis.</li>
          <li>Vermeiden Sie die Nutzung von KI‑Ergebnissen für Spam, betrügerische Aktivitäten oder Täuschungsversuche.</li>
          <li>Respektieren Sie die Privatsphäre Dritter – veröffentlichen Sie keine persönlichen Daten ohne Einwilligung.</li>
          <li>Bei generierten Inhalten, die eindeutig als KI‑basiert erkennbar sind, kennzeichnen Sie diese, wenn ein möglicher Missbrauch besteht.</li>
        </ul>
        <p>Verstöße gegen diese Regeln können zur Sperrung des Accounts und zur Meldung an zuständige Behörden führen.</p>
        <p>Bei Fragen oder Meldungen von Verstößen kontaktieren Sie bitte unser Support-Team unter <a href="mailto:support@mythoscraft.online" className="underline text-primary">support@mythoscraft.online</a>.</p>
      </section>
    </main>
  </div>
);

export default KIRegeln;
