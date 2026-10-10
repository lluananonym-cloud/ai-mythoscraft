import LegalPage, { H } from "@/components/LegalPage";
import { openConsent } from "@/lib/consent";

// Cookie-Hinweis: was im Browser gespeichert wird und wofür (§ 165 Abs. 3 TKG 2021).
const ROWS: [string, string, string][] = [
  ["Anmeldung (sb-…-auth-token)", "Hält dich eingeloggt", "Notwendig, bis zur Abmeldung"],
  ["mythos.consent", "Speichert deine Cookie-Entscheidung", "Notwendig, 12 Monate"],
  ["mythos.model, mythos.sidebar.collapsed, Chat-Einstellungen", "Gewähltes KI-Modell, Seitenleiste, eigene Anweisungen, Projekte", "Notwendig, bis du sie löschst"],
  ["mythos_ad_n, mythos_ad_admin", "Zählt, wann wieder Werbung kommt (ohne Profil)", "Notwendig, bis du sie löschst"],
  ["Offline-Modelle (Browser-Cache)", "KI-Modelle für /offline und Sprache, damit sie nicht jedes Mal neu laden", "Nur wenn du die Funktion nutzt"],
];

const Cookie = () => (
  <LegalPage title="Cookie-Hinweis">
    <p>
      Wir verwenden <strong>keine</strong> Tracking-, Analyse- oder Werbe-Cookies und geben keine Daten an Werbenetzwerke weiter.
      Im Browser speichern wir nur, was der Dienst technisch braucht. Dafür ist keine Einwilligung nötig.
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

    <H>Externe KI-Dienste (nur mit Einwilligung)</H>
    <p>
      Wenn unsere Server gerade kein Bild erzeugen können, kann dein Browser es direkt bei Pollinations.ai oder AI Horde laden. Dabei erfahren
      diese Anbieter deine IP-Adresse und den Bild-Wunsch. Das passiert nur, wenn du „Alle akzeptieren“ gewählt oder „Externe KI-Dienste“
      eingeschaltet hast.
    </p>
    <p>
      <button onClick={openConsent} className="rounded-full border border-white/15 bg-white/[0.06] px-4 py-2 text-foreground hover:bg-white/[0.12]">
        Cookie-Einstellungen ändern
      </button>
    </p>

    <H>Löschen</H>
    <p>Du kannst alle gespeicherten Daten jederzeit in den Einstellungen deines Browsers löschen („Website-Daten löschen“). Danach musst du dich neu anmelden.</p>
    <p>Mehr dazu in der <a href="/datenschutz">Datenschutzerklärung</a>.</p>
  </LegalPage>
);

export default Cookie;
