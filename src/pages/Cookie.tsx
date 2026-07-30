import TopNav from "@/components/TopNav";

const Cookie = () => (
  <div className="min-h-screen flex flex-col">
    <TopNav />
    <main className="container max-w-3xl py-12 mx-auto">
      <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">Cookie‑ und Tracking‑Hinweis</h1>
      <section className="space-y-4 text-muted-foreground">
        <p>Unsere Website verwendet Cookies, um Ihnen ein optimales Nutzungserlebnis zu bieten und um analytische Daten zu erheben.</p>
        <h2 className="font-semibold text-lg">Was sind Cookies?</h2>
        <p>Cookies sind kleine Textdateien, die beim Besuch einer Website in Ihrem Browser gespeichert werden. Sie dienen unter anderem dazu, Einstellungen zu speichern, die Session zu verwalten und das Nutzerverhalten zu analysieren.</p>
        <h2 className="font-semibold text-lg">Welche Cookies verwenden wir?</h2>
        <ul className="list-disc list-inside ml-4">
          <li><strong>Essentielle Cookies:</strong> Notwendig für das Einloggen und die Aufrechterhaltung Ihrer Session.</li>
          <li><strong>Performance‑Cookies:</strong> Helfen uns, die Performance der Seite zu messen (z. B. Google Analytics – IP‑Anonymisierung aktiviert).</li>
          <li><strong>Funktionale Cookies:</strong> Speichern Ihre Präferenzen, z. B. Theme‑Auswahl.</li>
        </ul>
        <h2 className="font-semibold text-lg">Ihr Widerspruchsrecht</h2>
        <p>Sie können die Verwendung von nicht‑essentiellen Cookies jederzeit über die Einstellungen Ihres Browsers deaktivieren. Bitte beachten Sie, dass dadurch einige Funktionen der Seite ggf. nicht mehr verfügbar sind.</p>
        <h2 className="font-semibold text-lg">Weitere Informationen</h2>
        <p>Weitere Details zu den von uns verwendeten Cookies finden Sie in unserer <a href="/datenschutz" className="underline text-primary">Datenschutzerklärung</a>.</p>
      </section>
    </main>
  </div>
);

export default Cookie;
