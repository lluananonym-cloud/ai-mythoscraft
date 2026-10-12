// Was Mythos selbst kann: Befehle und Bereiche der Website. Geht als Hinweis an jedes Modell im Chat,
// damit Mythos seine Fähigkeiten kennt, sie empfiehlt und nicht z. B. behauptet, er könne keine Bilder machen.
export type CommandInfo = { cmd: string; args: string; desc: string; admin?: boolean };

const AREAS: [string, string][] = [
  ["/voice", "Live-Anruf mit Mythos (sprechen, dazwischenreden)"],
  ["/filmstudio", "Kurzfilm aus einer Idee mit Sprecher und Musik"],
  ["/cloud-pc", "Agent mit eigenem PC im Browser, live zuschauen"],
  ["/raeume", "Gemeinsame Räume: Gruppen-Chat mit Live-Cursorn, Notiz und Dateien"],
  ["/groups", "Gruppen-Chats"],
  ["/create", "App-Builder: Apps aus einer Beschreibung bauen"],
  ["/build", "Build-Ansicht für eigene Apps"],
  ["/agents", "Eigene Agenten anlegen"],
  ["/personas", "Personas (eigene Charaktere für den Chat)"],
  ["/memories", "Gedächtnis: was Mythos sich über dich merkt"],
  ["/twin", "Digitaler Zwilling"],
  ["/arena", "Zwei KI-Antworten im Vergleich"],
  ["/games", "KI-Spiele"],
  ["/welt", "Mythos-Welt"],
  ["/foto-app", "Foto-App"],
  ["/ki-team", "KI-Team"],
  ["/rueckblick", "Tagesrückblick mit Bild"],
  ["/marktplatz", "Marktplatz"],
  ["/entdecken", "Entdecken"],
  ["/mc-servers", "Minecraft-Server verbinden (MythosCraft)"],
  ["/browser", "Mythos Browser"],
  ["/code", "Mythos Code (Coding-Agent als Windows-App und CLI)"],
  ["/downloads", "Downloads: Browser, Notch, Code-App, CLI"],
  ["/docs", "API-Doku (Claude-kompatible Mythos-API)"],
  ["/dashboard", "Konto, Abo (Light 2,99 €, Pro 6,99 €) und API-Schlüssel"],
];

export function capabilitiesPrompt(commands: CommandInfo[], isAdmin = false): string {
  const cmds = commands.filter((c) => isAdmin || !c.admin).map((c) => `- \`${c.cmd}${c.args ? " " + c.args : ""}\` – ${c.desc}`);
  return [
    "## Deine Fähigkeiten (Mythos)",
    "Das hier kannst du wirklich. Empfiehl passende Befehle oder Seiten, erfinde keine anderen.",
    "Bilder: Du KANNST Bilder erzeugen. Schreibt der Nutzer „mach/generiere ein Bild von …“, erzeugt die App das Bild automatisch. " +
      "Sag dann nie, dass du keine Bilder anzeigen kannst, und verweise nicht auf /image.",
    "Vorlesen: Jede Antwort hat einen Lautsprecher-Knopf; Stimmen sind unter /voice wählbar.",
    "Dateien: Nutzer können Bilder und PDFs anhängen, du siehst und analysierst sie.",
    "",
    "### Chat-Befehle",
    ...cmds,
    "",
    "### Bereiche der Website",
    ...AREAS.map(([p, d]) => `- ${p} – ${d}`),
  ].join("\n");
}
