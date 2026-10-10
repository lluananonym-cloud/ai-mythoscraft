import JSZip from "jszip";
// Quelltexte von CLI und Desktop-App liegen als echte Dateien daneben und werden roh in die ZIPs gepackt.
import CLI_JS from "./mythos-code-cli/mythos.js?raw";
import SHARED_TOOLS from "./mythos-code-shared/tools.js?raw";
import APP_MAIN from "./mythos-code-app/main.js?raw";
import APP_PRELOAD from "./mythos-code-app/preload.js?raw";
import APP_HTML from "./mythos-code-app/index.html?raw";
import APP_RENDERER from "./mythos-code-app/renderer.js?raw";
import APP_MARKDOWN from "./mythos-code-app/markdown.js?raw";
import APP_MCP from "./mythos-code-app/mcp.js?raw";
import APP_VOICE from "./mythos-code-app/voice.js?raw";
// Gebündelte Sprach-Worker (npm run build:app-voice): Piper (Stimme) und Whisper (Erkennung), gzip + base64.
import APP_TTS_WORKER_GZ from "./mythos-code-app/tts-worker.js.gz.b64?raw";
import APP_STT_WORKER_GZ from "./mythos-code-app/stt-worker.js.gz.b64?raw";
// Mythos-Handy-App: dünne native iOS-Hülle (SwiftUI + WKWebView) um Chat- und Code-Seite der Website.
import HANDY_PROJECT_YML from "./mythos-handy/project.yml?raw";
import HANDY_APP_SWIFT from "./mythos-handy/Sources/App.swift?raw";
import HANDY_WEBTAB_SWIFT from "./mythos-handy/Sources/WebTab.swift?raw";
import HANDY_ASSETS_CONTENTS from "./mythos-handy/Resources/Assets.xcassets/Contents.json?raw";
import HANDY_APPICON_CONTENTS from "./mythos-handy/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json?raw";

/** base64-kodiertes gzip entpacken (im Browser beim ZIP-Bau). */
async function gunzipB64(b64: string): Promise<string> {
  const bytes = Uint8Array.from(atob(b64.replace(/\s+/g, "")), (c) => c.charCodeAt(0));
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
  return new Response(stream).text();
}

/** Platzhalter ersetzen (alle Vorkommen). Zeilenenden auf LF, sonst bricht z. B. die Shebang-Zeile unter Linux/macOS. */
const fill = (src: string, vars: Record<string, string> = {}) =>
  Object.entries(vars).reduce((out, [k, v]) => out.split(k).join(v.replace(/\r\n/g, "\n")), src.replace(/\r\n/g, "\n"));

export const CLI_PKG = "mythos-code";
export const CLI_VERSION = "1.7.7";
const API = `https://${import.meta.env.VITE_SUPABASE_PROJECT_ID}.supabase.co/functions/v1/v1-messages`;

const pkgJson = () => JSON.stringify({
  name: CLI_PKG,
  version: CLI_VERSION,
  description: "Mythos Code – KI-Coding-Agent im Terminal (by Mythoscraft)",
  bin: { "mythos": "bin/mythos.js", "mythos-code": "bin/mythos.js" },
  type: "module",
  files: ["bin", "README.md"],
  engines: { node: ">=18" },
  keywords: ["ai", "cli", "agent", "mythos", "coding"],
  license: "MIT",
}, null, 2);

const readme = () => `# Mythos Code

KI-Coding-Agent im Terminal – by Mythoscraft.

\`\`\`
npm install -g ${CLI_PKG}
mythos login
mythos
\`\`\`
`;

export async function buildCliZip(site: string): Promise<Blob> {
  const zip = new JSZip();
  const root = zip.folder(CLI_PKG)!;
  root.file("package.json", pkgJson());
  root.file("README.md", readme());
  root.folder("bin")!.file("mythos.js", fill(CLI_JS, { "// @@SHARED_TOOLS@@": SHARED_TOOLS, "__API__": API, "__SITE__": site }), { unixPermissions: "755" });
  return zip.generateAsync({ type: "blob", platform: "UNIX" });
}

export const ADMIN_GUIDE = `## ✦ Mythos Code – Paket bereit

Die ZIP **${CLI_PKG}.zip** wurde heruntergeladen. So lädst du sie bei npm hoch:

1. ZIP entpacken, dann in CMD in den Ordner wechseln:
\`\`\`cmd
cd Downloads\\${CLI_PKG}\\${CLI_PKG}
\`\`\`
2. Kostenlosen Account auf https://www.npmjs.com/signup erstellen und einloggen:
\`\`\`cmd
npm login
\`\`\`
3. Lokal testen (optional):
\`\`\`cmd
npm link
mythos
\`\`\`
4. Veröffentlichen:
\`\`\`cmd
npm publish --access public
\`\`\`
> Falls der Name \`${CLI_PKG}\` schon vergeben ist: in \`package.json\` den \`name\` z. B. auf \`@deinname/mythos-code\` ändern.

Für Updates: \`version\` in package.json erhöhen und erneut \`npm publish\`.
Danach können Pro-Nutzer im Chat **/code** eingeben.`;

export const USER_GUIDE = `## ✦ Mythos Code installieren

Öffne **CMD** (oder PowerShell / Terminal) und gib ein:
\`\`\`cmd
npm install -g ${CLI_PKG}
\`\`\`
Dann anmelden – dein Browser öffnet sich, dort auf **Authentifizieren** klicken:
\`\`\`cmd
mythos login
\`\`\`
Und starten – in dem Projektordner, an dem Mythos arbeiten soll:
\`\`\`cmd
mythos
\`\`\`
Mythos Code kann Dateien lesen/schreiben und Befehle ausführen – vor jeder Aktion fragt es dich (\`a\` = immer erlauben, oder mit \`mythos --yes\` starten).
Voraussetzung: [Node.js](https://nodejs.org) ab Version 18.

**Im Terminal:** \`/hilfe\` zeigt alle Befehle, **Strg+C** stoppt Mythos. \`/goal <ziel>\` arbeitet selbstständig bis zum Ziel (mit Zusammenfassung), \`/merken <text>\` schreibt ins Projekt-Gedächtnis \`MYTHOS.md\`, \`/commit\` committet mit einer von Mythos geschriebenen Nachricht, \`/handy <ntfy-link>\` schickt dir eine Nachricht aufs Handy. Bilder: Datei einfach ins Terminal ziehen. Mythos kann im Internet suchen, \`/modell\` wechselt das Modell, \`/tokens\` zeigt den Verbrauch. Neu: \`/review\`, \`/pr\`, \`/stats\`, \`/export\`, \`/kompakt\`, Dateien mit \`@pfad\` erwähnen und eigene Befehle aus \`.mythos/commands/\`.`;

// ================= Mythos Code Desktop-App (Electron) =================
export const APP_PKG = "mythos-code-app";
export const APP_DOWNLOAD_SETTING = "codeprogram_download_url";
export const APP_UPDATE_SETTING = "codeprogram_update";
export const APP_VERSION = "2.3.2";

/** Google-Drive-Freigabelink -> direkter Download-Link (andere https-Links bleiben unverändert). */
export function toDirectDownloadUrl(input: string): string | null {
  let u: URL;
  try { u = new URL(input.trim()); } catch { return null; }
  if (u.protocol !== "https:") return null;
  if (/(^|\.)google\.com$/.test(u.hostname)) {
    const id = u.pathname.match(/\/file\/d\/([\w-]+)/)?.[1] || u.searchParams.get("id");
    if (id) return `https://drive.usercontent.google.com/download?id=${encodeURIComponent(id)}&export=download&confirm=t`;
  }
  return u.toString();
}

export const APP_USER_GUIDE = (url: string) => `## ✦ Mythos Code App

Der Download von **MythosCode-Setup.exe** startet gleich. Falls nicht: [hier klicken](${url}).

1. **MythosCode-Setup.exe** ausführen. Falls Windows „Der Computer wurde durch Windows geschützt“ zeigt: **Weitere Informationen → Trotzdem ausführen**.
2. App öffnen → **Mit MythosAI anmelden** → im Browser auf **Authentifizieren** klicken.
3. Ordner wählen, optional **Vollzugriff** einschalten – fertig. Mit 📎 kannst du Dateien hochladen, oben siehst du Laufzeit und Usage (Pro = unbegrenzt).
4. Links findest du deine **Projekte** und **gespeicherten Chats**. Mit ✎ bearbeitest du eine Nachricht, mit ■ (oder Esc) stoppst du Mythos.
5. Tippe **/** für Befehle. Mit **/goal <ziel>** arbeitet Mythos selbstständig, bis das Ziel erreicht ist – du kannst das Fenster schließen (Mythos läuft im Tray weiter), der PC muss aber anbleiben. Nach einem Neustart geht es mit **▶ Weitermachen** weiter.
6. Dateien und Bilder einfach ins Fenster ziehen oder mit **Strg+V** einfügen. Während Mythos arbeitet, reiht **Enter** weitere Aufgaben in die **Warteschlange** ein.
7. \`/merken\` füllt das Projekt-Gedächtnis (\`MYTHOS.md\`), \`/commit\` und \`/push\` erledigen Git, \`/handy <ntfy-link>\` schickt dir Nachrichten aufs Handy.
8. Oben: Modell wählen, 👁 Live-Vorschau, 🗂 Dateibaum mit Editor, ⌨ Terminal, ☀ helles Design. Mehrere Chats können gleichzeitig arbeiten. **Automatisch testen** unten einschalten – Mythos repariert fehlschlagende Tests selbst. \`/mcp\` und \`/hooks\` richten MCP-Server und Hooks ein.
9. 🎙 **Sprachmodus**: einfach mit Mythos reden – er hört zu (Whisper), arbeitet und antwortet mit natürlicher Stimme (Piper). Beides läuft lokal, beim ersten Mal werden die Sprachmodelle geladen. „Hey Mythos“, 📷 Kamera und 🖥 Bildschirm lassen sich dazuschalten.
10. Mythos plant größere Aufgaben als **📋 Aufgabenliste**. \`/review\` prüft deine Änderungen, \`/pr\` erstellt einen Pull Request, \`/stats\` zeigt Projektzahlen, \`/export\` speichert den Chat. Eigene Befehle legst du mit \`/befehl-neu\` an. Tastenkürzel: \`/tasten\`.
11. Mit **@** erwähnst du Dateien (ihr Inhalt geht mit), **✨** formuliert deine Aufgabe präziser, **📌** pinnt Chats an, **↻** generiert die letzte Antwort neu. \`/später 22:00 <aufgabe>\` plant Aufgaben, \`/kompakt\` fasst lange Chats zusammen, \`/ton aus\` schaltet den Fertig-Ton ab.`;
const FN_BASE = `https://${import.meta.env.VITE_SUPABASE_PROJECT_ID}.supabase.co/functions/v1`;

const appPkg = () => JSON.stringify({
  name: APP_PKG,
  productName: "Mythos Code",
  version: APP_VERSION,
  description: "Mythos Code – KI-Coding-Agent als App (by Mythoscraft)",
  main: "main.js",
  author: "Mythoscraft",
  license: "MIT",
  scripts: { start: "electron .", dist: "electron-builder --win nsis --publish never" },
  devDependencies: { electron: "^31.0.0", "electron-builder": "^24.13.3" },
  build: {
    appId: "online.mythoscraft.mythoscode",
    productName: "Mythos Code",
    files: ["main.js", "preload.js", "index.html", "markdown.js", "mcp.js", "voice.js", "tts-worker.js", "stt-worker.js", "renderer.js", "config.json", "icon.png"],
    win: { target: "nsis", icon: "icon.png" },
    nsis: { oneClick: false, allowToChangeInstallationDirectory: true, createDesktopShortcut: true, shortcutName: "Mythos Code", artifactName: "MythosCode-Setup.exe" },
  },
}, null, 2);

const appWorkflow = () => `name: Build Mythos Code Setup
on:
  push:
  workflow_dispatch:
jobs:
  build:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: npm install
      - run: npm run dist
      - uses: actions/upload-artifact@v4
        with:
          name: MythosCode-Setup
          path: dist/*.exe
`;

export async function buildAppZip(site: string): Promise<Blob> {
  const zip = new JSZip();
  const root = zip.folder(APP_PKG)!;
  // Mythoscraft-Logo: App-Icon, Setup-EXE-Icon und Logo in der Oberfläche.
  const icon = await fetch(new URL("/icon.png", site)).then((r) => (r.ok ? r.arrayBuffer() : null)).catch(() => null);
  if (!icon) throw new Error("Logo (/icon.png) konnte nicht geladen werden.");
  root.file("icon.png", icon);
  root.file("package.json", appPkg());
  root.file("main.js", fill(APP_MAIN, { "// @@SHARED_TOOLS@@": SHARED_TOOLS }));
  root.file("preload.js", fill(APP_PRELOAD));
  root.file("index.html", fill(APP_HTML));
  root.file("markdown.js", fill(APP_MARKDOWN));
  root.file("mcp.js", fill(APP_MCP));
  root.file("voice.js", fill(APP_VOICE));
  root.file("tts-worker.js", await gunzipB64(APP_TTS_WORKER_GZ));
  root.file("stt-worker.js", await gunzipB64(APP_STT_WORKER_GZ));
  root.file("renderer.js", fill(APP_RENDERER, { "__SITE__": site, "__FN__": FN_BASE }));
  root.file("config.json", JSON.stringify({ site }, null, 2));
  root.file("README.md", "# Mythos Code App\n\nWird per GitHub Actions zu `MythosCode-Setup.exe` gebaut.\n");
  root.folder(".github")!.folder("workflows")!.file("build.yml", appWorkflow());
  return zip.generateAsync({ type: "blob", platform: "UNIX" });
}

export const APP_ADMIN_GUIDE = `## ✦ Mythos Code App – Paket bereit

Die ZIP **${APP_PKG}.zip** wurde heruntergeladen. So wird daraus eine **Setup-EXE**:

1. ZIP entpacken.
2. Auf https://github.com/new ein neues Repository erstellen, z. B. \`mythos-code-app\` (Privat geht auch).
3. Auf der Repo-Seite **„uploading an existing file“** klicken und **den Inhalt** des Ordners \`${APP_PKG}\` hineinziehen – **inklusive dem Ordner \`.github\`** (versteckter Ordner! In Windows unter *Ansicht → Ausgeblendete Elemente* einschalten). → **Commit changes**.
4. Oben auf **Actions** klicken. Der Build „Build Mythos Code Setup“ startet automatisch (ca. 3–5 Min.). Falls nicht: links auswählen → **Run workflow**.
5. Wenn er grün ist: den Build öffnen → unten bei **Artifacts** auf **MythosCode-Setup** klicken → ZIP entpacken → darin liegt **MythosCode-Setup.exe**.
6. Die EXE in **Google Drive** hochladen → Rechtsklick → **Freigeben** → „Jeder mit dem Link“ → Link kopieren.
7. Hier im Chat eingeben:
\`\`\`
/codeprogrammadminupload <dein Google-Drive-Link>
\`\`\`
Danach kann jeder mit **/codeprogramm** die Setup-EXE herunterladen.`;

// ================= Mythos-Handy-App (iOS, per AltStore/AltServer sideloaden) =================
export const HANDY_PKG = "mythos-handy-app";
export const HANDY_DOWNLOAD_SETTING = "handyapp_download_url";
export const HANDY_VERSION = "1.0.0";

/** PNG im Browser auf Zielgröße bringen (für die iOS-App-Icons in allen nötigen Auflösungen). */
async function resizePng(buf: ArrayBuffer, size: number): Promise<ArrayBuffer> {
  const bitmap = await createImageBitmap(new Blob([buf], { type: "image/png" }));
  const canvas = new OffscreenCanvas(size, size);
  const ctx = canvas.getContext("2d")!;
  ctx.drawImage(bitmap, 0, 0, size, size);
  const blob = await canvas.convertToBlob({ type: "image/png" });
  return blob.arrayBuffer();
}

const handyWorkflow = () => `name: Mythos-Handy-App bauen
on:
  push:
    branches: [main]
  workflow_dispatch: {}
jobs:
  build:
    runs-on: macos-15
    steps:
      - uses: actions/checkout@v4
      - name: XcodeGen installieren
        run: brew install xcodegen
      - name: Xcode-Projekt erzeugen
        run: xcodegen generate
      - name: Ohne Signatur für ein echtes Gerät bauen
        run: |
          xcodebuild -project MythosHandy.xcodeproj -scheme MythosHandy -configuration Release \\
            -sdk iphoneos -derivedDataPath build \\
            CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \\
            build
      - name: .ipa packen (unsigniert – AltStore/AltServer signiert beim Installieren)
        run: |
          set -e
          mkdir -p ipa/Payload
          cp -R "build/Build/Products/Release-iphoneos/Mythos.app" ipa/Payload/
          cd ipa && zip -qr ../Mythos.ipa Payload
      - uses: actions/upload-artifact@v4
        with:
          name: Mythos-ipa
          path: Mythos.ipa
`;

export async function buildHandyZip(site: string): Promise<Blob> {
  const zip = new JSZip();
  const root = zip.folder(HANDY_PKG)!;
  const icon = await fetch(new URL("/icon.png", site)).then((r) => (r.ok ? r.arrayBuffer() : null)).catch(() => null);
  if (!icon) throw new Error("Logo (/icon.png) konnte nicht geladen werden.");
  root.file("project.yml", fill(HANDY_PROJECT_YML, { "__APP_VERSION__": HANDY_VERSION }));
  root.folder("Sources")!.file("App.swift", fill(HANDY_APP_SWIFT, { "__SITE__": site }));
  root.folder("Sources")!.file("WebTab.swift", HANDY_WEBTAB_SWIFT);
  const assets = root.folder("Resources")!.folder("Assets.xcassets")!;
  assets.file("Contents.json", HANDY_ASSETS_CONTENTS);
  const appicon = assets.folder("AppIcon.appiconset")!;
  appicon.file("Contents.json", HANDY_APPICON_CONTENTS);
  for (const size of [40, 60, 58, 87, 80, 120, 180, 1024]) {
    appicon.file(`icon-${size}.png`, await resizePng(icon, size));
  }
  root.file(".gitignore", "MythosHandy.xcodeproj/\nbuild/\nipa/\nDerivedData/\nGenerated-Info.plist\n");
  root.file("README.md", "# Mythos-Handy-App\n\nDünne iOS-Hülle um die Mythos-Website (Chat- und Code-Tab). Wird per GitHub Actions zu einer unsignierten `Mythos.ipa` gebaut – Installation über AltStore/AltServer.\n");
  root.folder(".github")!.folder("workflows")!.file("build-ipa.yml", handyWorkflow());
  return zip.generateAsync({ type: "blob", platform: "UNIX" });
}

export const HANDY_ADMIN_GUIDE = `## ✦ Mythos-Handy-App – Paket bereit

Die ZIP **${HANDY_PKG}.zip** wurde heruntergeladen. So wird daraus eine installierbare iPhone-App:

1. ZIP entpacken.
2. Auf https://github.com/new ein neues Repository erstellen, z. B. \`mythos-handy-app\` (Privat geht auch).
3. Auf der Repo-Seite **„uploading an existing file“** klicken und **den Inhalt** des Ordners \`${HANDY_PKG}\` hineinziehen – **inklusive dem Ordner \`.github\`** (versteckter Ordner! In Windows unter *Ansicht → Ausgeblendete Elemente* einschalten). → **Commit changes**.
4. Oben auf **Actions** klicken. Der Build „Mythos-Handy-App bauen“ startet automatisch (ca. 5–10 Min., da Xcode). Falls nicht: links auswählen → **Run workflow**.
5. Wenn er grün ist: den Build öffnen → unten bei **Artifacts** auf **Mythos-ipa** klicken → ZIP entpacken → darin liegt **Mythos.ipa**.
6. Die \`.ipa\` in **Google Drive** hochladen → Rechtsklick → **Freigeben** → „Jeder mit dem Link“ → Link kopieren.
7. Hier im Chat eingeben:
\`\`\`
/handyappupload <dein Google-Drive-Link>
\`\`\`
Danach kann jeder mit **/handyapp** die Anleitung zum Installieren bekommen.

**Wichtig:** Die \`.ipa\` ist absichtlich unsigniert – erst **AltStore/AltServer** signiert sie beim Installieren mit einer kostenlosen Apple-ID. Ohne das lässt sich die Datei nicht auf einem iPhone installieren.`;

export const HANDY_USER_GUIDE = (url: string) => `## ✦ Mythos-Handy-App installieren

Die App ist eine schlanke Hülle um die Website: **Chat** funktioniert wie hier, dazu ein **Code**-Tab, mit dem du die Mythos-Code-App auf deinem PC von unterwegs fernsteuern kannst.

**In der EU (z. B. Deutschland) – der einfachste Weg, ohne ständiges Neu-Signieren:**
1. **AltStore PC** installieren: https://altstore.io (dein iPhone muss auf **Region: EU-Land** stehen, ab iOS 17.4).
2. Mit einer **kostenlosen Apple-ID** anmelden (keine Kreditkarte, kein Entwicklerkonto nötig).
3. Die \`.ipa\`-Datei herunterladen: ${url}
4. In AltStore PC über **„+“ / Datei installieren** die \`.ipa\` auswählen.

**Außerhalb der EU – klassischer AltStore + AltServer:**
1. AltServer auf einem Windows/Mac-PC installieren: https://altstore.io
2. iPhone per Kabel verbinden, in AltServer **„Install AltStore“** wählen, mit kostenloser Apple-ID anmelden.
3. Die \`.ipa\` (${url}) auf das iPhone übertragen (AirDrop/Dateien) und über AltStore installieren.
4. **Wichtig:** Mit einer kostenlosen Apple-ID läuft die Signatur nach **7 Tagen** ab – AltServer muss dafür ab und zu (mit dem iPhone im selben WLAN) laufen, damit es sich automatisch erneuert.

**Danach in der App:**
- **Chat-Tab:** wie hier auf der Website, einfach anmelden.
- **Code-Tab:** in der Mythos-Code-App auf dem PC \`/koppeln\` eingeben, den 6-stelligen Code hier eintippen – schon kannst du dem PC von unterwegs Aufgaben schicken.`;
