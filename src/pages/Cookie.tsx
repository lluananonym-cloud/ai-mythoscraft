import LegalPage, { H } from "@/components/LegalPage";

// Cookie-Hinweis: was im Browser gespeichert wird und wofür (§ 25 TDDDG).
const ROWS: [string, string, string][] = [
  ["Anmeldung (sb-…-auth-token)", "Hält dich eingeloggt", "Notwendig, bis zur Abmeldung"],
  ["mythos.consent", "Speichert deine Cookie-Entscheidung", "Notwendig, 12 Monate"],
  ["mythos.model, mythos.sidebar.collapsed, Chat-Einstellungen", "Gewähltes KI-Modell, Seitenleiste, eigene Anweisungen, Projekte", "Notwendig, bis du sie löschst"],
  ["mythos_ad_n, mythos_ad_admin", "Zählt, wann wieder Werbung kommt (ohne Profil)", "Notwendig, bis du sie löschst"],
  ["PayPal-Cookies", "Nur beim Abo-Abschluss: Zahlung und Betrugsschutz durch PayPal (eigener Verantwortlicher)", "Notwendig für die Zahlung, Dauer laut PayPal"],
  ["Offline-Modelle (Browser-Cache)", "KI-Modelle für /offline und Sprache, damit sie nicht jedes Mal neu laden", "Nur wenn du die Funktion nutzt"],
];

const Cookie = () => (
  <LegalPage title="Cookie-Hinweis">
    <p>
      Wir verwenden <strong>keine</strong> Tracking-, Analyse- oder Werbe-Cookies und geben keine Daten an Werbenetzwerke weiter.
      Im Browser speichern wir nur, was der Dienst technisch braucht. Dafür ist keine Einwilligung nötig (§ 25 Abs. 2 Nr. 2 TDDDG).
    </p>

    <H>Was gespeichert wird</H>
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="text-foreground"><tr><th className="py-2 pr-4">Name</th><th className="py-2 pr-4">Zweck</th><th className="py-2">Art und Dauer</th></tr></thead>
        <tbody>
          {ROWS.map(([n, z, d]) => (
            <tr key={n} className="border-t border-white/10 align-top"><td className="py-2 pr-4 font-mono text-xs">{n}</td><td className="py-2 pr-4">{z}</td><td className="py-2">{d}</td></tr>
          ))}
        </tbody>
      </table>
    </div>

    <H>Löschen</H>
    <p>Du kannst alle gespeicherten Daten jederzeit in den Einstellungen deines Browsers löschen („Website-Daten löschen“). Danach musst du dich neu anmelden.</p>
    <p>Mehr dazu in der <a href="/datenschutz">Datenschutzerklärung</a>.</p>
  </LegalPage>
);

export default Cookie;
