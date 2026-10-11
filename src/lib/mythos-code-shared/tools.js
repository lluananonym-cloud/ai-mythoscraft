// ---------- Gemeinsame Werkzeuge (CLI + Desktop-App) ----------
// Wird roh in bin/mythos.js (CLI, ESM) und main.js (App, CommonJS) eingefügt.
// Erwartet, dass `fs` und `path` im umgebenden Code schon importiert sind.

// @@TOOL_PARSE@@

const TOOL_PROMPT = [
  "Werkzeuge – antworte pro Schritt mit GENAU EINEM Block:",
  '<tool>{"name":"run","cmd":"..."}</tool>  Shell-Befehl ausführen und warten, bis er ENDET (Ausgabe läuft live mit). Nicht für Server!',
  '<tool>{"name":"start","cmd":"npm run dev"}</tool>  Lang laufenden Prozess (Dev-Server, node server.js, Watcher, Bot) im HINTERGRUND starten. Wartet, bis die Adresse (z. B. http://localhost:5173) in der Ausgabe steht, und kehrt dann zurück – der Prozess läuft weiter.',
  '<tool>{"name":"logs","id":"p1"}</tool>  Ausgabe eines Hintergrundprozesses lesen (ohne id: alle laufenden auflisten)',
  '<tool>{"name":"stop","id":"p1"}</tool>  Hintergrundprozess beenden ("all" = alle)',
  '<tool>{"name":"serve","path":"."}</tool>  Eingebauter Webserver für statische Seiten (Ordner mit index.html, z. B. "." oder "dist"). Liefert die Adresse. Kein npx/http-server/Express nötig.',
  '<tool>{"name":"check","url":"http://localhost:5173"}</tool>  Webseite WIRKLICH prüfen: HTTP-Status, lädt jedes Skript/CSS, Browser-Test mit Konsolenfehlern, erkennt weiße Seiten und „Cannot GET /“, macht (in der App) einen Screenshot. url darf auch ein Pfad sein ("index.html").',
  '<tool>{"name":"verify"}</tool>  Projekt automatisch prüfen: installiert fehlende Pakete, führt Typecheck, Lint, Build und Tests aus (was es im Projekt gibt) und meldet ✓/✗ je Prüfung mit Fehlerausgabe.',
  '<tool>{"name":"read","path":"..."}</tool>  Datei lesen',
  '<tool>{"name":"edit","path":"...","old":"exakter alter Text","new":"neuer Text"}</tool>  Datei gezielt ändern (bevorzugt! "old" muss genau einmal vorkommen, sonst "all":true)',
  '<tool>{"name":"write","path":"...","content":"..."}</tool>  Neue Datei anlegen oder komplett neu schreiben',
  '<tool>{"name":"search","pattern":"regex","path":".","glob":"*.ts"}</tool>  Im Projekt suchen (path und glob optional)',
  '<tool>{"name":"ls","path":"."}</tool>  Ordner auflisten',
  '<tool>{"name":"websearch","query":"..."}</tool>  Im Internet suchen (z. B. Doku, Fehlermeldungen)',
  '<tool>{"name":"fetch","url":"https://..."}</tool>  Webseite als Text lesen',
  '<tool>{"name":"cloud","app":"Name der App"}</tool>  Mythos Cloud verbinden: eigenes Backend (Datenbank) + KI-Gateway (Chat, Streaming, Bilder) für die App, die du baust. Nutze das IMMER, sobald die App Daten speichern oder KI nutzen soll – statt Supabase/Firebase/OpenAI-Keys o. Ä. Legt mythos-cloud.js im Projekt an und erklärt die Nutzung.',
  '<tool>{"name":"todo","items":[{"text":"Schritt","done":false}]}</tool>  Aufgabenliste anlegen/aktualisieren (bei größeren Aufgaben zuerst planen, dann Punkte abhaken; immer die komplette Liste senden)',
  "Es gibt NUR diese Werkzeuge (plus die unten genannten MCP-/Browser-Werkzeuge, falls welche aufgeführt sind). Erfinde keine Werkzeuge, Connectoren oder MCP-Server. Fehlt etwas (z. B. ein nicht verbundener Connector), sag das dem Nutzer kurz und mach mit dem Rest weiter. Schließe jeden Block mit </tool>.",
  "Nach jedem Werkzeug bekommst du das Ergebnis. Suche erst, statt Dateien blind zu lesen. Ändere bestehende Dateien mit edit statt write.",
  "",
  "SO ARBEITEST DU (gründlich wie ein Senior-Entwickler):",
  "1. Verstehen: Projekt erkunden (ls, package.json, relevante Dateien lesen), bevor du etwas änderst.",
  "2. Planen: bei mehr als zwei Schritten zuerst eine todo-Liste.",
  "3. Bauen: vollständig und lauffähig – keine Platzhalter, kein „TODO“, keine „…“ im Code, keine erfundenen Pakete oder Importe. Neue Pakete wirklich installieren. Jede Datei, auf die du verweist, auch anlegen.",
  "4. Prüfen (PFLICHT, bevor du „fertig“ sagst): verify ausführen und jedes ✗ beheben. Bei Webseiten/Web-Apps zusätzlich starten (serve für statische Seiten, start für Dev-Server/Backends) und mit check prüfen; Fehler beheben und erneut prüfen, bis check „ERGEBNIS: ✓“ meldet. Behaupte nie, dass etwas funktioniert, das du nicht geprüft hast.",
  "5. Erst dann die Abschlussantwort.",
  "",
  "HANDELN STATT ERKLÄREN: Du hast vollen Zugriff auf den PC des Nutzers. Wenn er etwas haben will (eine .exe, ein Programm, eine Webseite, ein Skript, eine Datei, ein Build), dann BAUST du es selbst mit deinen Werkzeugen und lieferst das fertige Ergebnis mit Pfad – schreibe NIE nur eine Anleitung, Befehle zum Abtippen oder „so geht's“. Fehlt ein Programm (z. B. pyinstaller, electron-builder, pkg, dotnet), installiere es selbst. Anleitungen nur, wenn der Nutzer ausdrücklich danach fragt.",
  "Beispiele: „mach eine exe“ → Python: run \"pip install pyinstaller\", dann run \"pyinstaller --onefile --noconfirm main.py\" → dist\\main.exe; Node-Skript: run \"npx --yes @yao-pkg/pkg . --targets node18-win-x64 --output dist/app.exe\"; Electron-App: run \"npx --yes electron-builder --win portable\"; C#: run \"dotnet publish -c Release -r win-x64 --self-contained -p:PublishSingleFile=true\". Danach mit ls/run prüfen, dass die .exe existiert (und wenn möglich startet), und den vollen Pfad nennen.",
  "",
  "SERVER: Dev-Server, „npm start“, „node server.js“, Watcher u. Ä. NIE mit run starten – run wartet, bis der Befehl endet, und ein Server endet nie. Immer start (oder serve) nutzen. Der Server läuft nach deiner Antwort weiter, damit der Nutzer die Seite öffnen kann.",
  "",
  "WEBSEITEN RICHTIG AUFSETZEN (gegen weiße Seiten und „Cannot GET /“):",
  "- Einfache Webseite/Spiel ohne Framework: index.html direkt im Projektordner, CSS/JS daneben mit relativen Pfaden (./style.css, ./script.js), normales <script src> ohne import von npm-Paketen. Starten mit serve. Kein Express, kein Build nötig.",
  "- React/Vue/Svelte/TypeScript: als Vite-Projekt (package.json mit vite, index.html im Projektroot mit <script type=\"module\" src=\"/src/main.tsx\">, <div id=\"root\">), dann run \"npm install\", dann start \"npm run dev\". Eine index.html, die .tsx/.ts/.jsx lädt, NIE direkt öffnen oder mit serve ausliefern – das ergibt eine weiße Seite. Fertiger Build: run \"npm run build\", dann serve \"dist\".",
  "- Eigener Node-Server (Express o. Ä.): MUSS „/“ beantworten – app.use(express.static(path.join(__dirname, \"public\"))) mit public/index.html, oder app.get(\"/\", …). Port = process.env.PORT || 3000, beim Start die volle Adresse ausgeben: console.log(\"Server läuft: http://localhost:\" + port).",
  "- Weiße Seite heißt fast immer: JavaScript-Fehler, eine Datei lädt nicht (404) oder falscher Pfad. check zeigt dir Konsolenfehler und fehlende Dateien – behebe die Ursache.",
  "",
  "ANTWORTEN: Vor einem Werkzeug höchstens ein kurzer Satz. Die Abschlussantwort (ohne Werkzeug) knapp und strukturiert in Markdown:",
  "### ✅ Ergebnis – ein bis zwei Sätze, was jetzt geht",
  "**Geändert** – Liste: `datei` – was",
  "**Geprüft** – Liste mit ✓/✗: was du ausgeführt bzw. angesehen hast",
  "**Ansehen/Starten** – Adresse (läuft schon) oder Befehl, falls relevant",
  "**Offen** – nur, wenn etwas fehlt oder der Nutzer etwas tun muss",
  "Keine langen Fließtexte, keinen Code wiederholen. Bei reinen Fragen ohne Codeänderung einfach direkt und gegliedert antworten.",
].join("\n");

// Modelle, die v1-messages versteht (siehe mapModel dort).
const MODELS = [
  { id: "mythos-code", label: "MythosCode", desc: "Standard fürs Programmieren" },
  { id: "mythos-v2", label: "Mythos v2", desc: "am stärksten, etwas langsamer" },
  { id: "mythos-sonnet", label: "Mythos v1", desc: "ausgewogen" },
  { id: "mythos-lite", label: "Mythos Lite", desc: "am schnellsten" },
];
/** Grobe Token-Schätzung (≈ 4 Zeichen pro Token, Bilder pauschal). */
function estimateTokens(system, messages) {
  let chars = String(system || "").length, images = 0;
  for (const m of messages) {
    if (typeof m.content === "string") chars += m.content.length;
    else for (const b of m.content || []) { if (b.type === "image") images++; else chars += String(b.text || "").length; }
  }
  return Math.ceil(chars / 4) + images * 1500;
}
const fmtTokens = (n) => (n >= 1e6 ? (n / 1e6).toFixed(1) + " Mio." : n >= 1000 ? (n / 1000).toFixed(1) + "k" : String(n));

const MEMORY_FILE = "MYTHOS.md";
const memoryPrompt = (mem) => mem
  ? "\n\nProjekt-Gedächtnis (" + MEMORY_FILE + " im Projektordner – halte dich daran, ergänze es bei wichtigen neuen Erkenntnissen per edit):\n" + mem
  : "";
const goalPrompt = (goal) => goal
  ? "\n\nZIEL (/goal): " + goal +
    "\nDer Auftraggeber ist NICHT am Rechner und liest erst viel später mit, was hier im Chat passiert ist – frage ihn nichts, er kann nicht antworten. Behandle jede Entscheidung, jede Rückfrage und jedes Hindernis als etwas, das du selbst lösen musst:" +
    "\n- Gib bei Fehlern NIEMALS auf. Ein fehlgeschlagener Befehl, eine fehlende Abhängigkeit, ein kaputter Build sind Aufgaben, keine Endstationen: lies die Fehlermeldung genau, versuche eine andere Vorgehensweise, installiere Fehlendes selbst (z. B. per npm/pip/winget), nutze bei Bedarf websearch/fetch um die Lösung nachzuschlagen, und probiere es erneut. Erst wenn du wirklich mehrere grundverschiedene Ansätze erfolglos versucht hast, notierst du das Problem knapp und machst mit dem Rest der Aufgabe weiter." +
    "\n- Triff sinnvolle Annahmen statt zu fragen, und schreib kurz dazu, welche Annahme du getroffen hast." +
    "\n- Beginne SOFORT mit einer kurzen Bestätigung und deinem Plan (2–6 Stichpunkte) als normalen Text, BEVOR du das erste Werkzeug nutzt – so sieht der Auftraggeber direkt, dass du das Ziel verstanden hast." +
    "\n- Teile deinen Fortschritt normal im Chat mit (was du tust und warum), aber warte nicht auf eine Antwort. Schreibe vor jedem größeren Schritt einen kurzen Satz dazu." +
    "\n- Baue große Projekte in vielen kleinen Dateien (je höchstens ca. 300 Zeilen) statt in einer riesigen Datei auf einmal." +
    "\n- Das ist eine große, langlaufende Aufgabe: Plane für 1 bis 5 Stunden durchgehende Arbeit in vielen kleinen Schritten, nicht für ein paar Minuten. Höre nicht zu früh auf – arbeite lieber zu gründlich als zu knapp." +
    "\nArbeite komplett selbstständig weiter, bis das Ziel vollständig erreicht und überprüft ist. Erst dann schreibe in deiner letzten Antwort eine eigene Zeile: ZIEL ERREICHT"
  : "";
/** Kurzer Live-Status aus der gerade gestreamten Antwort, z. B. „✍ schreibt src/main.js · 12 KB“. */
function streamActivity(full) {
  if (!full) return "denkt nach…";
  const k = full.lastIndexOf("<tool>");
  if (k < 0 || full.indexOf("</tool>", k) >= 0) return "schreibt Antwort…";
  const t = full.slice(k + 6), kb = (t.length / 1024).toFixed(1).replace(".", ",") + " KB";
  const f = (key) => { const m = t.match(new RegExp('"' + key + '"\\s*:\\s*"([^"\\\\]{0,120})')); return m ? m[1] : ""; };
  const name = f("name"), path = f("path");
  if (name === "write" || name === "edit") return "✍ " + (name === "write" ? "schreibt " : "ändert ") + (path || "eine Datei") + " · " + kb;
  if (name === "run") return "⚙ bereitet Befehl vor: " + f("cmd").slice(0, 60);
  return "🔧 bereitet " + (name || "Werkzeug") + " vor…";
}
const GOAL_NUDGE = "Das Ziel ist noch nicht als erreicht gemeldet. Der Auftraggeber ist nicht da – gib bei Problemen nicht auf, sondern versuche einen anderen Weg, nutze websearch/fetch für Lösungen und arbeite selbstständig weiter, ohne Rückfragen. Wenn es wirklich vollständig erledigt und geprüft ist, schreibe ZIEL ERREICHT.";
const SUMMARY_PROMPT = "Fasse jetzt kurz auf Deutsch zusammen, OHNE Werkzeuge, genau in diesem Format:\n### ✅ Ergebnis\nein bis zwei Sätze\n**Geändert** – `datei` – was\n**Geprüft** – ✓/✗ je Prüfung (verify, check, Tests)\n**Ansehen/Starten** – Adresse oder Befehl (falls relevant)\n**Offen** – was noch fehlt (nur falls etwas fehlt)\nMaximal 15 Zeilen.";

const SKIP_DIRS = new Set(["node_modules", ".git", "dist", "build", ".next", "out", "coverage", ".cache", "vendor", "__pycache__", ".venv", "venv", "target"]);

function readText(file) { try { return fs.readFileSync(file, "utf8"); } catch { return null; } }

// Windows-Konsolenprogramme (cmd, dir, …) schreiben in Pipes im OEM-Zeichensatz (CP850), Node/Git in UTF-8.
const CP850 = "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤ÁÂÀ©╣║╗╝¢¥┐└┴┬├─┼ãÃ╚╔╩╦╠═╬¤ðÐÊËÈıÍÎÏ┘┌█▄¦Ì▀ÓßÔÒõÕµþÞÚÛÙýÝ¯´­±‗¾¶§÷¸°¨·¹³²■ ";
const fromCp850 = (b) => { let s = ""; for (const c of b) s += c < 128 ? String.fromCharCode(c) : CP850[c - 128]; return s; };
/** Decoder für eine Ausgabe-Pipe: jedes Stück als UTF-8, sonst (Windows) als CP850. Angefangene UTF-8-Zeichen werden aufgehoben. */
function outputDecoder() {
  let pending = Buffer.alloc(0);
  return (chunk) => {
    let b = pending.length ? Buffer.concat([pending, chunk]) : chunk;
    let i = b.length - 1, cont = 0;
    while (i >= 0 && cont < 3 && (b[i] & 0xc0) === 0x80) { i--; cont++; }
    const need = i >= 0 ? (b[i] >= 0xf0 ? 4 : b[i] >= 0xe0 ? 3 : b[i] >= 0xc0 ? 2 : 1) : 1;
    const cut = need > 1 && b.length - i < need ? i : b.length;
    pending = Buffer.from(b.subarray(cut)); b = b.subarray(0, cut);
    try { return new TextDecoder("utf-8", { fatal: true }).decode(b); }
    catch { return process.platform === "win32" ? fromCp850(b) : b.toString("latin1"); }
  };
}

/** Zeilen-Diff mit Kontext: { added, removed, lines: [[op, text]] }, op = " " | "+" | "-" | "@" (Lücke). */
function lineDiff(a, b, ctx = 3, maxLines = 400) {
  // Zeilenumbruch am Dateiende ist keine eigene Zeile.
  const toLines = (t) => (t ? t.replace(/\r\n/g, "\n").replace(/\n$/, "").split("\n") : []);
  const A = toLines(a), B = toLines(b);
  let s = 0; while (s < A.length && s < B.length && A[s] === B[s]) s++;
  let ea = A.length, eb = B.length; while (ea > s && eb > s && A[ea - 1] === B[eb - 1]) { ea--; eb--; }
  const x = A.slice(s, ea), y = B.slice(s, eb);
  let ops = [];
  if (x.length * y.length <= 4e6) {
    const n = x.length, m = y.length, L = [];
    for (let i = 0; i <= n; i++) L.push(new Uint32Array(m + 1));
    for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) L[i][j] = x[i] === y[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
    let i = 0, j = 0;
    while (i < n && j < m) {
      if (x[i] === y[j]) { ops.push([" ", x[i]]); i++; j++; }
      else if (L[i + 1][j] >= L[i][j + 1]) ops.push(["-", x[i++]]);
      else ops.push(["+", y[j++]]);
    }
    while (i < n) ops.push(["-", x[i++]]);
    while (j < m) ops.push(["+", y[j++]]);
  } else ops = x.map((l) => ["-", l]).concat(y.map((l) => ["+", l]));
  const all = A.slice(0, s).map((l) => [" ", l]).concat(ops, A.slice(ea).map((l) => [" ", l]));
  const keep = new Uint8Array(all.length);
  let added = 0, removed = 0;
  all.forEach((o, k) => {
    if (o[0] === " ") return;
    if (o[0] === "+") added++; else removed++;
    for (let d = Math.max(0, k - ctx); d <= Math.min(all.length - 1, k + ctx); d++) keep[d] = 1;
  });
  const lines = [];
  let gap = false;
  for (let k = 0; k < all.length; k++) {
    if (!keep[k]) { gap = true; continue; }
    if (lines.length >= maxLines) { lines.push(["@", "… (gekürzt)"]); break; }
    if (gap && lines.length) lines.push(["@", "…"]);
    gap = false; lines.push(all[k]);
  }
  return { added, removed, lines };
}

/** Gezieltes Ändern: `old` muss genau einmal vorkommen (oder all=true). */
function editFile(file, oldText, newText, all) {
  const cur = readText(file);
  if (cur == null) return { error: "Datei nicht gefunden: " + file };
  if (!oldText) return { error: '"old" fehlt – gib den exakten Text an, der ersetzt werden soll.' };
  let o = String(oldText), n = String(newText ?? "");
  // CRLF-Dateien mit LF-Suchtext trotzdem treffen.
  if (!cur.includes(o) && cur.includes("\r\n")) { o = o.replace(/\r?\n/g, "\r\n"); n = n.replace(/\r?\n/g, "\r\n"); }
  const count = cur.split(o).length - 1;
  if (count === 0) return { error: "Text nicht gefunden in " + file + " – lies die Datei neu und nimm den exakten Text (inkl. Einrückung)." };
  if (count > 1 && !all) return { error: "Text kommt " + count + "-mal vor in " + file + ' – nimm mehr umgebenden Kontext oder setze "all":true.' };
  const at = cur.indexOf(o);
  const next = all ? cur.split(o).join(n) : cur.slice(0, at) + n + cur.slice(at + o.length);
  fs.writeFileSync(file, next);
  return { diff: lineDiff(cur, next), count: all ? count : 1 };
}

function globToRe(glob) {
  const g = String(glob).replace(/\\/g, "/").replace(/[.+^${}()|[\]]/g, "\\$&")
    .replace(/\*\*\/?/g, "\u0001").replace(/\*/g, "[^/]*").replace(/\?/g, "[^/]").replace(/\u0001/g, ".*");
  return new RegExp("(^|/)" + g + "$", "i");
}

/** Projekt durchsuchen (Regex, Groß-/Kleinschreibung egal, ohne node_modules & Co.). */
function searchFiles(root, pattern, glob, max = 200) {
  let re;
  try { re = new RegExp(pattern, "i"); } catch { re = new RegExp(String(pattern).replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"); }
  const gre = glob ? globToRe(glob) : null;
  const hits = [];
  let files = 0;
  const walk = (dir) => {
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      if (hits.length >= max || files > 20000) return;
      const full = path.join(dir, e.name);
      if (e.isDirectory()) { if (!SKIP_DIRS.has(e.name)) walk(full); continue; }
      const rel = path.relative(root, full).split(path.sep).join("/");
      if (gre && !gre.test(rel)) continue;
      let st; try { st = fs.statSync(full); } catch { continue; }
      if (st.size > 1e6) continue;
      files++;
      const text = readText(full);
      if (text == null || text.includes("\u0000")) continue;
      const lines = text.split(/\r?\n/);
      for (let i = 0; i < lines.length && hits.length < max; i++) {
        if (re.test(lines[i])) hits.push(rel + ":" + (i + 1) + ": " + lines[i].trim().slice(0, 200));
      }
    }
  };
  walk(root);
  if (!hits.length) return "Keine Treffer für /" + pattern + "/" + (glob ? " in " + glob : "") + ".";
  return hits.join("\n") + (hits.length >= max ? "\n… (mehr als " + max + " Treffer – Suche eingrenzen)" : "");
}

// ---------- Sicherheitsnetz für Befehle ----------
// Diese Befehle fragen IMMER nach (auch mit Vollzugriff) und werden im /goal-Modus blockiert,
// weil dann niemand da ist, der sie bestätigen könnte.
const DANGER = [
  [/\brm\s+-[a-z]*(?:rf|fr)[a-z]*\s+(?:--no-preserve-root\s+)?(?:\/|~|\*|\$HOME|%USERPROFILE%)(?:\s|$)/i, "löscht rekursiv ein System- oder Home-Verzeichnis"],
  [/\bformat(?:\.com)?\s+[a-z]:/i, "formatiert ein Laufwerk"],
  [/\b(?:rd|rmdir)\s+\/s\b[^&|;]*\b[a-z]:\\?\s*(?:$|[&|;])/i, "löscht ein ganzes Laufwerk"],
  [/\b(?:del|erase)\b[^&|;]*\/s\b[^&|;]*\b[a-z]:\\\*?\s*(?:$|[&|;])/i, "löscht ein ganzes Laufwerk"],
  [/Remove-Item\b[^|;]*-Recurse[^|;]*\s["']?[a-z]:\\?["']?(?:\s|$|;)/i, "löscht rekursiv ein ganzes Laufwerk"],
  [/\bgit\s+push\b[^;&|]*\s(?:--force(?:-with-lease)?|-f)\b/i, "überschreibt die Git-Historie auf dem Server (Force-Push)"],
  [/\bgit\s+reset\s+--hard\b|\bgit\s+clean\s+-[a-z]*f/i, "verwirft lokale Änderungen unwiderruflich"],
  [/\b(?:shutdown|restart-computer|stop-computer)\b/i, "fährt den PC herunter oder startet ihn neu"],
  [/\breg(?:\.exe)?\s+delete\b|Remove-Item(?:Property)?\b[^|;]*\bHK(?:LM|CU):/i, "löscht Einträge in der Windows-Registry"],
  [/\b(?:curl|wget|iwr|irm|invoke-webrequest|invoke-restmethod)\b[^|]*\|\s*(?:sh|bash|zsh|iex|invoke-expression|powershell|pwsh)\b/i, "lädt ein Skript aus dem Internet und führt es sofort aus"],
  [/\bmkfs(?:\.\w+)?\b|\bdd\s+[^|;]*\bof=\/dev\//i, "überschreibt ein Laufwerk"],
  [/:\(\)\s*\{\s*:\|:&\s*\};:/, "startet eine Fork-Bombe"],
  [/\b(?:npm|pnpm|yarn)\s+publish\b|\bgh\s+release\s+create\b|\bgh\s+repo\s+delete\b/i, "veröffentlicht oder löscht etwas öffentlich"],
  [/\b(?:Set-MpPreference|netsh\s+advfirewall\s+set)\b/i, "ändert Windows-Sicherheitseinstellungen (Virenschutz/Firewall)"],
];
/** Grund, warum ein Befehl gefährlich ist, oder null. */
function dangerCheck(cmd) {
  const c = String(cmd || "");
  for (const [re, why] of DANGER) if (re.test(c)) return why;
  return null;
}

/** Liegt `p` (relativ zu root) innerhalb des Projektordners? */
function insideRoot(root, p) {
  const r = path.relative(path.resolve(root), path.resolve(root, p || "."));
  return !(r.startsWith("..") || path.isAbsolute(r));
}

/** read/write/edit/search/ls ausführen. `run` macht jede Oberfläche selbst (Live-Ausgabe). */
function fileTool(t, root) {
  const P = (p) => path.resolve(root, p || ".");
  // Schreiben nur innerhalb des Projekts – und nie in Gits interne Dateien.
  if (t.name === "write" || t.name === "edit") {
    if (!insideRoot(root, t.path)) return { result: "FEHLER: Schreiben außerhalb des Projektordners ist gesperrt (" + t.path + "). Arbeite nur mit Dateien im Projekt." };
    if (/^\.git(?:[\\/]|$)/.test(path.relative(path.resolve(root), P(t.path)))) return { result: "FEHLER: Gits interne Dateien (.git/) werden nicht direkt bearbeitet – nutze git-Befehle." };
  }
  try {
    if (t.name === "read") {
      const c = readText(P(t.path));
      return { result: c == null ? "FEHLER: Datei nicht gefunden: " + t.path : c.slice(0, 20000) + (c.length > 20000 ? "\n…[gekürzt – Datei ist länger]" : "") };
    }
    if (t.name === "write") {
      const file = P(t.path), before = readText(file) ?? "", content = String(t.content ?? "");
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, content);
      const d = lineDiff(before, content);
      return { result: "OK geschrieben: " + t.path + " (+" + d.added + " −" + d.removed + ")", diff: d };
    }
    if (t.name === "edit") {
      const r = editFile(P(t.path), t.old, t.new, t.all);
      if (r.error) return { result: "FEHLER: " + r.error };
      return { result: "OK geändert: " + t.path + " (" + r.count + "×, +" + r.diff.added + " −" + r.diff.removed + ")", diff: r.diff };
    }
    if (t.name === "search") return { result: searchFiles(P(t.path), String(t.pattern || ""), t.glob) };
    if (t.name === "ls") return { result: fs.readdirSync(P(t.path), { withFileTypes: true }).map((e) => (e.isDirectory() ? "[D] " : "    ") + e.name).join("\n") || "(leer)" };
    return { result: "Unbekanntes Werkzeug: " + t.name };
  } catch (e) { return { result: "FEHLER: " + e.message }; }
}

// ---------- Web: Seite lesen & Suche ----------
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36 MythosCode";
const ENTITIES = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " ", auml: "ä", ouml: "ö", uuml: "ü", Auml: "Ä", Ouml: "Ö", Uuml: "Ü", szlig: "ß" };
const decodeEntities = (s) => s.replace(/&(#x[0-9a-f]+|#\d+|\w+);/gi, (m, e) =>
  e[0] === "#" ? String.fromCodePoint(e[1] === "x" || e[1] === "X" ? parseInt(e.slice(2), 16) : +e.slice(1)) : ENTITIES[e] ?? m);
function htmlToText(html) {
  return decodeEntities(html
    .replace(/<(script|style|noscript|svg|head)[\s\S]*?<\/\1>/gi, " ")
    .replace(/<\/(p|div|li|h[1-6]|tr|section|article|header|footer|pre|blockquote)>|<br\s*\/?>/gi, "\n")
    .replace(/<li[^>]*>/gi, "• ")
    .replace(/<[^>]+>/g, " "))
    .replace(/[ \t\f\v]+/g, " ").replace(/\n\s*\n\s*/g, "\n\n").trim();
}
async function webGet(url) {
  const ctl = new AbortController(); const to = setTimeout(() => ctl.abort(), 20000);
  try {
    const r = await fetch(url, { headers: { "user-agent": WEB_UA, "accept-language": "de,en;q=0.8" }, redirect: "follow", signal: ctl.signal });
    const type = r.headers.get("content-type") || "";
    const buf = Buffer.from(await r.arrayBuffer()).subarray(0, 3e6);
    return { ok: r.ok, status: r.status, type, text: buf.toString("utf8"), url: r.url };
  } finally { clearTimeout(to); }
}
async function webTool(t) {
  try {
    if (t.name === "fetch") {
      const url = String(t.url || "");
      if (!/^https?:\/\//i.test(url)) return { result: "FEHLER: Bitte eine vollständige http(s)-URL angeben." };
      const r = await webGet(url);
      if (!r.ok) return { result: "FEHLER: HTTP " + r.status + " für " + url };
      const text = /html/i.test(r.type) ? htmlToText(r.text) : r.text;
      return { result: "Inhalt von " + r.url + ":\n\n" + text.slice(0, 20000) + (text.length > 20000 ? "\n…[gekürzt]" : "") };
    }
    if (t.name === "websearch") {
      const q = String(t.query || "").trim();
      if (!q) return { result: "FEHLER: query fehlt." };
      const r = await webGet("https://html.duckduckgo.com/html/?q=" + encodeURIComponent(q));
      const hits = [];
      // Jeder Treffer ist ein eigener Block mit Titel-Link und (meist) Kurzbeschreibung.
      for (const block of r.text.split(/<div[^>]+class="[^"]*\bresult\b/).slice(1)) {
        if (hits.length >= 8) break;
        const a = block.match(/class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/);
        if (!a) continue;
        let href = decodeEntities(a[1]);
        const u = href.match(/[?&]uddg=([^&]+)/); if (u) href = decodeURIComponent(u[1]);
        if (/duckduckgo\.com\/y\.js|result--ad/.test(href + block.slice(0, 200))) continue; // Werbung
        const sn = block.match(/class="result__snippet"[^>]*>([\s\S]*?)<\/(?:a|div)>/);
        hits.push((hits.length + 1) + ". " + htmlToText(a[2]) + "\n   " + href + (sn ? "\n   " + htmlToText(sn[1]).slice(0, 300) : ""));
      }
      return { result: hits.length ? "Suchergebnisse für „" + q + "“:\n\n" + hits.join("\n\n") + "\n\nMit fetch kannst du eine Seite genauer lesen." : "Keine Suchergebnisse für „" + q + "“." };
    }
    return { result: "Unbekanntes Werkzeug: " + t.name };
  } catch (e) { return { result: "FEHLER: " + (e.name === "AbortError" ? "Zeitüberschreitung" : e.message) }; }
}
const isWebTool = (t) => t.name === "fetch" || t.name === "websearch";

// ---------- Aufgabenliste (todo) ----------
/** Werkzeug-Eingabe prüfen → saubere Liste [{ text, done }]. */
function normalizeTodos(t) {
  const items = Array.isArray(t && t.items) ? t.items : [];
  return items.slice(0, 40).map((x) => (typeof x === "string" ? { text: x, done: false } : { text: String((x && x.text) || "").slice(0, 200), done: !!(x && x.done) })).filter((x) => x.text);
}
const todoSummary = (items) => "Aufgabenliste aktualisiert (" + items.filter((x) => x.done).length + "/" + items.length + " erledigt).";

// ---------- Eigene Befehle: .mythos/commands/<name>.md ----------
// Erste Zeile = Beschreibung (optional mit "# "), Rest = Anweisung an Mythos. $ARGUMENTS wird ersetzt.
function loadCommands(dirs) {
  const out = new Map();
  for (const dir of dirs) {
    let names = [];
    try { names = fs.readdirSync(dir).filter((f) => /\.md$/i.test(f)); } catch { continue; }
    for (const f of names) {
      const text = readText(path.join(dir, f));
      if (!text || !text.trim()) continue;
      const name = f.replace(/\.md$/i, "").toLowerCase().replace(/[^a-z0-9äöüß_-]/g, "-");
      const lines = text.replace(/\r\n/g, "\n").split("\n");
      const desc = lines[0].replace(/^#+\s*/, "").trim().slice(0, 100);
      out.set(name, { name: "/" + name, desc: desc || "Eigener Befehl", body: text.trim(), file: path.join(dir, f) });
    }
  }
  return [...out.values()];
}
const fillCommand = (cmd, args) => (cmd.body.includes("$ARGUMENTS") ? cmd.body.split("$ARGUMENTS").join(args || "") : cmd.body + (args ? "\n\n" + args : ""));
const COMMAND_TEMPLATE = "# Tests schreiben für eine Datei\nSchreibe gründliche Tests für $ARGUMENTS.\nNutze das Test-Framework, das im Projekt schon verwendet wird, und führe die Tests am Ende aus.\n";

// ---------- Code-Review ----------
const REVIEW_PROMPT = "Führe ein gründliches Code-Review der folgenden Änderungen durch. ÄNDERE KEINE DATEIEN – nur lesen und bewerten.\n" +
  "Gliedere die Antwort so:\n**🐞 Fehler** (echte Bugs, mit Datei:Zeile)\n**⚠️ Risiken** (Sicherheit, Randfälle, Performance)\n**💡 Verbesserungen** (Lesbarkeit, Vereinfachung)\n**✅ Gut gelöst**\n" +
  "Wenn eine Kategorie leer ist, schreib „nichts gefunden“. Sei konkret, keine allgemeinen Floskeln.";

// ---------- Projektstatistik ----------
const LANG = { js: "JavaScript", mjs: "JavaScript", cjs: "JavaScript", jsx: "JavaScript (JSX)", ts: "TypeScript", tsx: "TypeScript (TSX)", py: "Python", java: "Java", kt: "Kotlin", cs: "C#", cpp: "C++", cc: "C++", c: "C", h: "C/C++ Header", go: "Go", rs: "Rust", rb: "Ruby", php: "PHP", swift: "Swift", html: "HTML", css: "CSS", scss: "SCSS", vue: "Vue", svelte: "Svelte", json: "JSON", md: "Markdown", yml: "YAML", yaml: "YAML", sql: "SQL", sh: "Shell", ps1: "PowerShell", bat: "Batch", lua: "Lua", dart: "Dart", xml: "XML", toml: "TOML" };
function projectStats(root) {
  const byLang = new Map(), big = [];
  let files = 0, lines = 0, bytes = 0, skipped = 0;
  const walk = (dir) => {
    let entries; try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      if (files > 30000) return;
      const full = path.join(dir, e.name);
      if (e.isDirectory()) { if (!SKIP_DIRS.has(e.name)) walk(full); continue; }
      const ext = path.extname(e.name).slice(1).toLowerCase(), lang = LANG[ext];
      if (!lang) { skipped++; continue; }
      let st; try { st = fs.statSync(full); } catch { continue; }
      if (st.size > 2e6) { skipped++; continue; }
      const text = readText(full); if (text == null) continue;
      const n = text ? text.split("\n").length : 0;
      files++; lines += n; bytes += st.size;
      const l = byLang.get(lang) || { files: 0, lines: 0 }; l.files++; l.lines += n; byLang.set(lang, l);
      big.push({ file: path.relative(root, full).split(path.sep).join("/"), lines: n });
    }
  };
  walk(root);
  big.sort((a, b) => b.lines - a.lines);
  const langs = [...byLang.entries()].sort((a, b) => b[1].lines - a[1].lines);
  const fmt = (n) => n.toLocaleString("de-DE");
  let md = "### 📊 Projektstatistik: " + path.basename(root) + "\n\n";
  md += "**" + fmt(files) + " Code-Dateien · " + fmt(lines) + " Zeilen · " + (bytes / 1024 / 1024).toFixed(1) + " MB**" + (skipped ? " (" + fmt(skipped) + " andere Dateien übersprungen)" : "") + "\n\n";
  if (langs.length) {
    md += "| Sprache | Dateien | Zeilen | Anteil |\n|---|---:|---:|---:|\n";
    md += langs.slice(0, 12).map(([name, v]) => "| " + name + " | " + fmt(v.files) + " | " + fmt(v.lines) + " | " + Math.round((v.lines / Math.max(1, lines)) * 100) + " % |").join("\n") + "\n\n";
  }
  if (big.length) md += "**Größte Dateien:**\n" + big.slice(0, 5).map((b) => "- `" + b.file + "` – " + fmt(b.lines) + " Zeilen").join("\n") + "\n";
  return md;
}

// ---------- Chat als Markdown ----------
function chatToMarkdown(title, messages) {
  const date = new Date().toLocaleString("de-DE");
  let md = "# " + (title || "Mythos-Chat") + "\n\n_Exportiert aus Mythos Code am " + date + "_\n\n---\n\n";
  for (const m of messages) md += (m.role === "user" ? "## 🧑 Du\n\n" : m.role === "tool" ? "#### ⚙ " : "## ✦ Mythos\n\n") + String(m.text || "").trim() + "\n\n";
  return md;
}

function readMemory(root) { const t = readText(path.join(root, MEMORY_FILE)); return t ? t.slice(0, 8000) : ""; }
function addMemory(root, text) {
  const f = path.join(root, MEMORY_FILE);
  const cur = readText(f) || "# Projekt-Gedächtnis\n\nMythos liest diese Datei bei jeder Aufgabe mit.\n";
  fs.writeFileSync(f, cur.replace(/\s*$/, "\n") + "- " + String(text).trim() + "\n");
  return f;
}

/** Handy-Benachrichtigung: ntfy.sh-Thema, Discord-Webhook oder beliebige URL (JSON-POST). */
async function pushNotify(url, title, message) {
  if (!url) return false;
  try {
    const u = new URL(url);
    let target = url, body;
    if (/(^|\.)discord(app)?\.com$/.test(u.hostname)) body = { content: "**" + title + "**\n" + message };
    else if (/ntfy/.test(u.hostname)) { target = u.origin; body = { topic: u.pathname.replace(/^\/+|\/+$/g, ""), title, message }; }
    else body = { title, message };
    const r = await fetch(target, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
    return r.ok;
  } catch { return false; }
}

// ---------- Prüfen, Hintergrund-Prozesse, eingebauter Webserver ----------
// Braucht zusätzlich `spawn`, `exec` (child_process) und `http` im umgebenden Code.

const stripAnsi = (s) => String(s || "").replace(/\x1b\[[0-9;?]*[ -/]*[@-~]/g, "").replace(/\x1b\][^\x07]*\x07/g, "");
const tailText = (s, n) => { s = stripAnsi(s).trim(); return s.length > n ? "…" + s.slice(-n) : s; };
function killProc(p) {
  if (!p || p.exitCode != null) return;
  try {
    if (process.platform === "win32") exec("taskkill /pid " + p.pid + " /T /F");
    else { try { process.kill(-p.pid, "SIGTERM"); } catch { p.kill("SIGTERM"); } }
  } catch { /* schon beendet */ }
}



/** Lokale Adresse aus Server-Ausgabe lesen („Local: http://localhost:5173/“, „listening on port 3000“ …). */
function findLocalUrl(out) {
  const s = stripAnsi(out);
  const m = s.match(/https?:\/\/(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1?\])(?::\d+)?(?:\/[^\s"'<>)\]]*)?/i);
  if (m) return m[0].replace(/0\.0\.0\.0|\[::1?\]/, "localhost").replace(/[.,;:]+$/, "");
  const p = s.match(/(?:listening|running|started|läuft|serving|server|bound)[^\n]{0,40}?\bport\s*:?\s*(\d{2,5})\b/i) || s.match(/\bport\s*:?\s*(\d{4,5})\b[^\n]{0,30}(?:listening|running|ready|läuft)/i);
  return p ? "http://localhost:" + p[1] + "/" : "";
}

const BG = new Map(); let bgSeq = 0;
/** Langlaufenden Befehl im Hintergrund starten. Wartet, bis eine Adresse erscheint, der Prozess endet oder nichts mehr passiert. */
function bgStart(cmd, cwd, opts) {
  opts = opts || {};
  // Derselbe Befehl im selben Ordner läuft schon → erst beenden (sonst „Port belegt“).
  for (const j of BG.values()) if (j.cmd === cmd && j.cwd === cwd && j.code == null) { killProc(j.p); BG.delete(j.id); }
  return new Promise((resolve) => {
    const id = "p" + (++bgSeq);
    let p;
    try {
      p = spawn(cmd, { cwd: cwd || undefined, shell: true, windowsHide: true, detached: process.platform !== "win32",
        env: { ...process.env, BROWSER: "none", FORCE_COLOR: "0", NO_COLOR: "1", ...(opts.env || {}) } });
    } catch (e) { resolve({ result: "FEHLER: " + e.message }); return; }
    const job = { id, cmd, cwd, p, out: "", url: "", code: null, t0: Date.now(), last: Date.now() };
    BG.set(id, job);
    let done = false;
    const max = Math.min(300, Math.max(5, +opts.wait || 90)) * 1000;
    const finish = () => {
      if (done) return; done = true; clearInterval(timer);
      const out = tailText(job.out, 3000);
      if (job.code != null) {
        BG.delete(id);
        const busy = /EADDRINUSE|address already in use|port \d+ is (?:already )?in use/i.test(job.out);
        resolve({ result: (job.code === 0 ? "Befehl ist fertig (Exit 0) – er lief nicht dauerhaft weiter.\n" : "FEHLER: Prozess beendet mit Exit " + job.code + ".\n") +
          (busy ? "Der Port ist belegt – stoppe den alten Prozess (logs/stop) oder nimm einen anderen Port.\n" : "") + (out || "(keine Ausgabe)"), code: job.code });
        return;
      }
      resolve({ id, url: job.url, result: (job.url
        ? "✓ Läuft im Hintergrund (id " + id + "): " + job.url + "\nAls Nächstes: Seite mit check prüfen."
        : "Läuft im Hintergrund (id " + id + "), noch keine Adresse in der Ausgabe erkannt. Mit logs nachsehen.") +
        "\nAusgabe ansehen: {\"name\":\"logs\",\"id\":\"" + id + "\"} · beenden: {\"name\":\"stop\",\"id\":\"" + id + "\"}\n\nAusgabe bisher:\n" + (out || "(keine)") });
    };
    const on = (dec) => (d) => {
      const s = dec(d); job.out += s; job.last = Date.now();
      if (job.out.length > 200000) job.out = job.out.slice(-100000);
      if (!done && opts.onChunk) opts.onChunk(s);
      if (!job.url) { const u = findLocalUrl(job.out); if (u) { job.url = u; setTimeout(finish, 1200); } }
    };
    p.stdout.on("data", on(outputDecoder())); p.stderr.on("data", on(outputDecoder()));
    p.on("error", (e) => { job.out += "\nFEHLER: " + e.message; });
    p.on("close", (code) => { job.code = code == null ? 1 : code; setTimeout(finish, 100); });
    const timer = setInterval(() => {
      const el = Date.now() - job.t0;
      if (el > max || (el > 25000 && Date.now() - job.last > 25000)) finish();
    }, 500);
  });
}
function bgLogs(id) {
  if (!id) {
    const list = [...BG.values()].filter((j) => j.code == null);
    return list.length ? "Laufende Hintergrundprozesse:\n" + list.map((j) => "- " + j.id + ": " + j.cmd + (j.url ? " → " + j.url : "") + " (seit " + Math.round((Date.now() - j.t0) / 1000) + " s)").join("\n") : "Keine Hintergrundprozesse.";
  }
  const j = BG.get(String(id));
  if (!j) return "FEHLER: Kein Hintergrundprozess mit id " + id + ". " + bgLogs("");
  return (j.code == null ? "läuft" : "beendet (Exit " + j.code + ")") + " · " + j.cmd + (j.url ? " · " + j.url : "") + "\n" + (tailText(j.out, 6000) || "(keine Ausgabe)");
}
function bgStop(id) {
  const list = id === "all" || !id ? [...BG.values()] : [BG.get(String(id))].filter(Boolean);
  if (!list.length) return "FEHLER: Kein Hintergrundprozess mit id " + id + ".";
  list.forEach((j) => { killProc(j.p); BG.delete(j.id); });
  return "Beendet: " + list.map((j) => j.id + " (" + j.cmd + ")").join(", ");
}
function stopAllBackground() {
  for (const j of BG.values()) killProc(j.p);
  BG.clear();
  for (const s of SERVERS.values()) try { s.server.close(); } catch { /* egal */ }
  SERVERS.clear();
}
process.on("exit", stopAllBackground);

// ---------- Eingebauter Webserver (für statische Seiten) ----------
const MIME = { html: "text/html; charset=utf-8", htm: "text/html; charset=utf-8", css: "text/css; charset=utf-8", js: "text/javascript; charset=utf-8", mjs: "text/javascript; charset=utf-8",
  json: "application/json; charset=utf-8", map: "application/json", svg: "image/svg+xml", png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp", avif: "image/avif",
  ico: "image/x-icon", woff: "font/woff", woff2: "font/woff2", ttf: "font/ttf", otf: "font/otf", mp3: "audio/mpeg", wav: "audio/wav", ogg: "audio/ogg", mp4: "video/mp4", webm: "video/webm",
  txt: "text/plain; charset=utf-8", xml: "application/xml", pdf: "application/pdf", wasm: "application/wasm", webmanifest: "application/manifest+json" };
const SERVERS = new Map();
/** Braucht diese index.html einen Build-Schritt (TS/JSX/Vite)? */
const needsBundler = (html) => /<script[^>]+src=["'][^"']*\.(?:tsx?|jsx|vue|svelte)["']/i.test(html);
function listenFree(server, port) {
  return new Promise((res, rej) => {
    const tryPort = (p) => {
      server.once("error", (e) => (e.code === "EADDRINUSE" && p < port + 30 ? tryPort(p + 1) : rej(e)));
      server.listen(p, "127.0.0.1", () => res(server.address().port));
    };
    tryPort(port);
  });
}
async function serveDir(root, rel) {
  let target = path.resolve(root, rel || ".");
  if (!fs.existsSync(target)) return { result: "FEHLER: Nicht gefunden: " + (rel || ".") };
  let page = "";
  if (fs.statSync(target).isFile()) { page = path.basename(target); target = path.dirname(target); }
  if (!page && !fs.existsSync(path.join(target, "index.html"))) {
    const sub = ["dist", "build", "public", "out", "www", "site", "docs"].find((d) => fs.existsSync(path.join(target, d, "index.html")));
    const pkg = readText(path.join(target, "package.json")) || "";
    if (!sub) return { result: "FEHLER: In „" + (rel || ".") + "“ gibt es keine index.html – darum zeigt ein Server dort „Cannot GET /“. Lege eine index.html an" + (pkg ? " oder starte das Projekt mit start (z. B. npm run dev)" : "") + "." };
    if (/"(?:vite|react-scripts|next|@vue\/cli-service|parcel)"/.test(pkg) && sub === "public") return { result: "FEHLER: Das ist ein Projekt mit Build-Schritt. Starte es mit start \"npm run dev\" oder baue es (npm run build) und nutze dann serve \"dist\"." };
    target = path.join(target, sub);
  }
  const indexHtml = readText(path.join(target, page || "index.html")) || "";
  if (needsBundler(indexHtml)) return { result: "FEHLER: " + (page || "index.html") + " lädt TypeScript/JSX direkt (z. B. /src/main.tsx). Das kann ein Browser nicht ausführen → weiße Seite. Nutze start \"npm run dev\" (Vite) oder npm run build und dann serve \"dist\"." };
  const dir = target;
  let srv = SERVERS.get(dir);
  if (!srv) {
    const server = http.createServer((req, res) => {
      let p; try { p = decodeURIComponent(new URL(req.url, "http://x").pathname); } catch { p = "/"; }
      let f = path.join(dir, p);
      if (path.relative(dir, f).startsWith("..")) { res.writeHead(403); res.end("Verboten"); return; }
      try { if (fs.statSync(f).isDirectory()) f = path.join(f, "index.html"); } catch { /* gibt es nicht */ }
      // Einzelseiten-Apps: unbekannte Pfade ohne Dateiendung → index.html.
      if (!fs.existsSync(f) && !path.extname(p) && fs.existsSync(path.join(dir, "index.html"))) f = path.join(dir, "index.html");
      fs.readFile(f, (err, data) => {
        if (err) { res.writeHead(404, { "content-type": "text/plain; charset=utf-8" }); res.end("404 – Datei nicht gefunden: " + p); return; }
        res.writeHead(200, { "content-type": MIME[path.extname(f).slice(1).toLowerCase()] || "application/octet-stream", "cache-control": "no-store" });
        res.end(data);
      });
    });
    let port;
    try { port = await listenFree(server, 5500); } catch (e) { return { result: "FEHLER: Webserver konnte nicht starten: " + e.message }; }
    srv = { server, url: "http://127.0.0.1:" + port + "/" };
    SERVERS.set(dir, srv);
  }
  const url = srv.url + (page && page !== "index.html" ? encodeURI(page) : "");
  return { url, result: "✓ Webserver läuft: " + url + " (Ordner: " + (path.relative(root, dir) || ".") + ", Dateien werden bei jedem Aufruf frisch gelesen)\nAls Nächstes: Seite mit check prüfen." };
}

// ---------- Seite prüfen (HTTP + alle Skripte/CSS) ----------
async function getLocal(url, ms, method, accept) {
  const tries = [url];
  if (/\/\/localhost\b/i.test(url)) tries.push(url.replace(/\/\/localhost\b/i, "//127.0.0.1"), url.replace(/\/\/localhost\b/i, "//[::1]"));
  let last;
  for (const u of tries) {
    const ctl = new AbortController(); const to = setTimeout(() => ctl.abort(), ms || 15000);
    try {
      const r = await fetch(u, { method: method || "GET", redirect: "follow", signal: ctl.signal, headers: { "user-agent": WEB_UA, accept: accept || "text/html,*/*" } });
      const text = method === "HEAD" ? "" : Buffer.from(await r.arrayBuffer()).subarray(0, 3e6).toString("utf8");
      return { ok: r.ok, status: r.status, type: r.headers.get("content-type") || "", text, url: r.url || u };
    } catch (e) { last = e; } finally { clearTimeout(to); }
  }
  throw last;
}
/** HTTP-Prüfung einer Seite. → { lines, fail, warn, url, html, reachable } */
async function httpPageCheck(url) {
  const lines = []; let fail = 0, warn = 0;
  let r;
  try { r = await getLocal(url, 20000); }
  catch (e) {
    const why = e.name === "AbortError" ? "Zeitüberschreitung" : (e.cause && e.cause.code) || e.message;
    return { reachable: false, fail: 1, warn: 0, url, html: "", lines: ["✗ Nicht erreichbar (" + why + "). Läuft der Server? Mit start/serve starten und die Adresse aus der Ausgabe nehmen (logs zeigt sie)."] };
  }
  const isHtml = /html/i.test(r.type) || /^\s*<(?:!doctype|html)/i.test(r.text);
  lines.push((r.ok ? "✓" : "✗") + " HTTP " + r.status + " · " + (r.type.split(";")[0] || "?") + " · " + (r.text.length / 1024).toFixed(1).replace(".", ",") + " KB" + (r.url.replace(/\/$/, "") !== url.replace(/\/$/, "") ? " · nach Weiterleitung: " + r.url : ""));
  if (!r.ok) {
    fail++;
    if (/Cannot (?:GET|POST) \//i.test(r.text)) lines.push("✗ „" + (r.text.match(/Cannot \w+ \/[^\s<]*/) || ["Cannot GET /"])[0] + "“: Der Server (meist Express) hat für diese Adresse keine Route und liefert keine index.html aus. Lösung: app.use(express.static(path.join(__dirname, \"public\"))) mit public/index.html, oder app.get(\"/\", …) – für eine reine Webseite ohne Backend einfach das serve-Werkzeug nutzen.");
    else lines.push("Antwort: " + htmlToText(r.text).slice(0, 400));
    return { reachable: true, status: r.status, fail, warn, url: r.url, html: r.text, lines };
  }
  if (!isHtml) return { reachable: true, status: r.status, fail, warn, url: r.url, html: "", lines: lines.concat(["ℹ Keine HTML-Seite – Inhalt: " + r.text.slice(0, 300)]) };
  const html = r.text;
  const title = (html.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [])[1];
  if (title) lines.push("Titel: „" + decodeEntities(title.trim()).slice(0, 80) + "“");
  if (/file:\/\/|(?:src|href)=["'][a-z]:\\/i.test(html)) { fail++; lines.push("✗ Die Seite verweist auf lokale Dateipfade (file:// oder C:\\…) – im Browser über http lädt das nicht. Relative Pfade nutzen (./style.css)."); }
  if (needsBundler(html) && !/@vite\/client|\/@react-refresh/.test(html)) { fail++; lines.push("✗ index.html lädt TypeScript/JSX direkt (.tsx/.ts/.jsx) ohne Dev-Server → weiße Seite. Mit start \"npm run dev\" (Vite) starten oder bauen und dist ausliefern."); }
  // Alle eingebundenen Skripte und Stylesheets laden.
  const refs = [];
  html.replace(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*>/gi, (_m, s) => refs.push(["Skript", s]));
  html.replace(/<link\b[^>]*>/gi, (tag) => { if (/rel=["'](?:stylesheet|modulepreload)["']/i.test(tag)) { const h = tag.match(/href=["']([^"']+)["']/i); if (h) refs.push([/stylesheet/i.test(tag) ? "CSS" : "Modul", h[1]]); } });
  const base = new URL(r.url);
  let okRefs = 0;
  for (const [kind, ref] of refs.slice(0, 30)) {
    let u; try { u = new URL(ref, base); } catch { continue; }
    if (u.host !== base.host) continue;
    try {
      const a = await getLocal(u.href, 15000, "GET", kind === "CSS" ? "text/css,*/*;q=0.1" : "*/*");
      const isJs = kind !== "CSS";
      if (!a.ok) { fail++; lines.push("✗ " + kind + " " + u.pathname + " → HTTP " + a.status + " (Datei fehlt oder Pfad falsch)"); }
      else if (/html/i.test(a.type) && !/html/i.test(u.pathname)) { fail++; lines.push("✗ " + kind + " " + u.pathname + " → liefert HTML statt " + (isJs ? "JavaScript" : "CSS") + " (Datei fehlt, Server schickt index.html)"); }
      else okRefs++;
    } catch (e) { fail++; lines.push("✗ " + kind + " " + u.pathname + " → " + e.message); }
  }
  if (refs.length) lines.push((okRefs === refs.length ? "✓ " : "") + okRefs + "/" + refs.length + " eingebundene Skripte/CSS laden");
  const bodyText = htmlToText((html.match(/<body[^>]*>([\s\S]*)<\/body>/i) || [, html])[1]);
  if (bodyText.length < 3) {
    const hasScripts = /<script\b/i.test(html);
    if (!hasScripts) { fail++; lines.push("✗ Die Seite ist leer: kein sichtbarer Text und keine Skripte."); }
    else { warn++; lines.push("ℹ Der Inhalt entsteht erst per JavaScript (leeres <body>/#root) – ob er wirklich erscheint, zeigt nur der Browser-Test."); }
  } else lines.push("✓ Sichtbarer Text im HTML: " + bodyText.length + " Zeichen – „" + bodyText.replace(/\s+/g, " ").slice(0, 120) + "“");
  return { reachable: true, status: r.status, fail, warn, url: r.url, html, lines };
}

/** Projekt automatisch prüfen: fehlende Pakete installieren, Typecheck, Lint, Build, Tests. */
function runCapture(cmd, cwd, opts) {
  opts = opts || {};
  return new Promise((res) => {
    let out = "";
    const p = spawn(cmd, { cwd, shell: true, windowsHide: true, env: { ...process.env, FORCE_COLOR: "0", NO_COLOR: "1", BROWSER: "none", ...(opts.env || {}) } });
    if (opts.track) opts.track(p);
    const on = (dec) => (d) => { const s = dec(d); out += s; if (out.length > 200000) out = out.slice(-100000); if (opts.onChunk) opts.onChunk(s); };
    p.stdout.on("data", on(outputDecoder())); p.stderr.on("data", on(outputDecoder()));
    const to = setTimeout(() => { out += "\n[Zeitüberschreitung – abgebrochen]"; killProc(p); }, opts.timeout || 600000);
    p.on("error", (e) => { out += "\nFEHLER: " + e.message; });
    p.on("close", (code) => { clearTimeout(to); res({ code: code == null ? 1 : code, out }); });
  });
}
function detectChecks(root) {
  const has = (f) => fs.existsSync(path.join(root, f));
  const checks = [];
  let pkg = null; try { pkg = JSON.parse(readText(path.join(root, "package.json")) || "null"); } catch { pkg = null; }
  if (pkg) {
    const pm = has("pnpm-lock.yaml") ? "pnpm" : has("yarn.lock") ? "yarn" : has("bun.lockb") || has("bun.lock") ? "bun" : "npm";
    const run = (s) => (pm === "npm" ? "npm run " + s : pm + " run " + s);
    const sc = pkg.scripts || {};
    const deps = Object.keys(Object.assign({}, pkg.dependencies, pkg.devDependencies));
    if (deps.length && !has("node_modules")) checks.push({ label: "Pakete installieren", cmd: pm + " install", fatal: true });
    const tc = ["typecheck", "type-check", "tsc", "check-types"].find((s) => sc[s]);
    if (tc) checks.push({ label: "Typecheck", cmd: run(tc) });
    else if (has("tsconfig.json") && !/\btsc\b/.test(sc.build || "") && (deps.includes("typescript") || has("node_modules/typescript"))) {
      const ts = readText(path.join(root, "tsconfig.json")) || "";
      checks.push({ label: "Typecheck", cmd: /"references"\s*:/.test(ts) ? "npx tsc -b" : "npx tsc --noEmit" });
    }
    if (sc.lint) checks.push({ label: "Lint", cmd: run("lint"), soft: true });
    if (sc.build) checks.push({ label: "Build", cmd: run("build") });
    if (sc.test && !/no test specified/i.test(sc.test)) checks.push({ label: "Tests", cmd: pm === "npm" ? "npm test" : pm + " test", env: { CI: "1" } });
    if (!sc.build && !sc.test) {
      const main = [pkg.main, "index.js", "server.js", "app.js"].find((f) => f && has(f) && /\.c?js$/.test(f));
      if (main) checks.push({ label: "Syntax " + main, cmd: "node --check " + JSON.stringify(main) });
    }
  }
  const py = has("requirements.txt") || has("pyproject.toml") || has("setup.py") || (!pkg && fs.readdirSync(root).some((f) => /\.py$/.test(f)));
  if (py) {
    const pyc = process.platform === "win32" ? "python" : "python3";
    checks.push({ label: "Python-Syntax", cmd: pyc + " -m compileall -q -x \"(node_modules|venv|\\.venv|__pycache__)\" ." });
    if (has("pytest.ini") || has("conftest.py") || has("tests")) checks.push({ label: "Tests", cmd: pyc + " -m pytest -q" });
  }
  if (has("Cargo.toml")) checks.push({ label: "Build", cmd: "cargo build" }, { label: "Tests", cmd: "cargo test" });
  if (has("go.mod")) checks.push({ label: "Build", cmd: "go build ./..." }, { label: "Tests", cmd: "go test ./..." });
  if (has("pom.xml")) checks.push({ label: "Build", cmd: "mvn -q -DskipTests package" });
  return { checks, pkg };
}
/** Statische Seiten: verlinkte lokale Dateien müssen existieren. */
function staticRefCheck(root) {
  const pages = [];
  const walk = (dir, depth) => {
    let entries; try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) { if (depth < 3 && !SKIP_DIRS.has(e.name) && !e.name.startsWith(".")) walk(full, depth + 1); }
      else if (/\.html?$/i.test(e.name) && pages.length < 40) pages.push(full);
    }
  };
  walk(root, 0);
  const missing = [];
  for (const f of pages) {
    const html = readText(f) || "";
    html.replace(/<(?:script|img|source|link|iframe)\b[^>]*\b(?:src|href)=["']([^"'#?]+)/gi, (_m, ref) => {
      if (/^(?:[a-z]+:|\/\/|data:|\$|\{)/i.test(ref)) return;
      const tgt = ref.startsWith("/") ? path.join(root, ref) : path.join(path.dirname(f), ref);
      if (!fs.existsSync(tgt) && !/\/src\/main\.\w+$/.test(ref)) missing.push(path.relative(root, f).split(path.sep).join("/") + " → " + ref);
    });
  }
  return { pages: pages.length, missing };
}
async function verifyProject(root, opts) {
  opts = opts || {};
  const { checks, pkg } = detectChecks(root);
  const lines = []; let fail = 0;
  for (const c of checks) {
    if (opts.onChunk) opts.onChunk("\n▶ " + c.label + ": " + c.cmd + "\n");
    const r = await runCapture(c.cmd, root, { onChunk: opts.onChunk, track: opts.track, env: c.env, timeout: 900000 });
    if (r.code === 0) lines.push("✓ " + c.label + " (" + c.cmd + ")");
    else {
      if (!c.soft) fail++;
      lines.push((c.soft ? "⚠ " : "✗ ") + c.label + " (" + c.cmd + ") – Exit " + r.code + (c.soft ? " (Lint-Hinweise; beheben, wenn sie deine Änderungen betreffen)" : "") + ":\n" + tailText(r.out, c.soft ? 1500 : 3500).split("\n").map((l) => "    " + l).join("\n"));
      if (c.fatal) break;
    }
  }
  const st = staticRefCheck(root);
  if (st.missing.length) { fail++; lines.push("✗ Fehlende Dateien in HTML-Seiten:\n" + st.missing.slice(0, 20).map((m) => "    " + m).join("\n")); }
  else if (st.pages && !pkg) lines.push("✓ " + st.pages + " HTML-Seite(n): alle verlinkten lokalen Dateien vorhanden");
  if (!lines.length) return { result: "Keine automatischen Prüfungen erkannt (kein package.json-Skript, keine Tests, keine HTML-Seiten). Prüfe selbst mit run (z. B. das Programm einmal ausführen)." };
  const web = st.pages || (pkg && /"(?:vite|react|react-dom|vue|svelte|next|express|astro)"/.test(JSON.stringify(Object.assign({}, pkg.dependencies, pkg.devDependencies))));
  return { fail, result: "Prüfbericht:\n" + lines.join("\n") + "\n\n" + (fail ? "ERGEBNIS: ✗ " + fail + " Prüfung(en) fehlgeschlagen – Ursache beheben und verify erneut ausführen." : "ERGEBNIS: ✓ alle Prüfungen bestanden.") +
    (web ? "\nDas ist ein Web-Projekt: starte es (serve bzw. start) und prüfe die Seite mit check." : "") };
}

// ---------- Werkzeuge start/logs/stop/serve/verify/check (gemeinsamer Teil) ----------
const DEV_TOOLS = ["start", "logs", "stop", "serve", "verify", "check"];
const isDevTool = (t) => DEV_TOOLS.includes(t.name);
/** Ziel für check: Adresse, Datei/Ordner (→ eingebauter Webserver) oder zuletzt gestarteter Server. */
async function checkTarget(t, root) {
  let url = String(t.url || t.path || "").trim();
  if (!url) {
    const job = [...BG.values()].reverse().find((j) => j.url && j.code == null);
    const srv = [...SERVERS.values()].pop();
    url = (job && job.url) || (srv && srv.url) || "";
    if (!url) return { error: "FEHLER: Keine Adresse angegeben und kein laufender Server. Erst serve/start, dann check mit \"url\"." };
  }
  if (!/^https?:\/\//i.test(url)) {
    if (/^(?:localhost|127\.0\.0\.1)(?::\d+)?/i.test(url)) url = "http://" + url;
    else { const s = await serveDir(root, url.replace(/^file:\/\/\/?/i, "")); if (!s.url) return { error: s.result }; url = s.url; }
  }
  return { url };
}
/** Prüfbericht aus HTTP-Prüfung h und (optional) Browser-Test rv = renderVerdict(…). */
function checkReport(url, h, rv, note) {
  const lines = h.lines.concat(rv ? ["", "Browser-Test (echt gerendert):"].concat(rv.lines) : note ? [note] : []);
  const fail = h.fail + (rv ? rv.fail : 0);
  return "Seitenprüfung " + url + "\n" + lines.join("\n") + "\n\n" + (fail
    ? "ERGEBNIS: ✗ Probleme gefunden – Ursache beheben und erneut prüfen."
    : rv ? "ERGEBNIS: ✓ Seite lädt ohne Fehler und zeigt Inhalt." : "ERGEBNIS: ✓ Seite antwortet, alle Dateien laden.");
}
/** Muss die Seite noch im Browser gerendert werden? (erreichbar und HTML) */
const wantsRender = (r) => !!(r && r.http && r.http.reachable && r.http.html && r.http.status < 400);
/** opts: { onChunk, track } – Live-Ausgabe und Abbrechen der Oberfläche. */
async function devTool(t, root, opts) {
  opts = opts || {};
  try {
    if (t.name === "start") {
      if (!t.cmd) return { result: 'FEHLER: "cmd" fehlt.' };
      return await bgStart(String(t.cmd), root, { onChunk: opts.onChunk, wait: t.wait });
    }
    if (t.name === "logs") return { result: bgLogs(t.id) };
    if (t.name === "stop") return { result: bgStop(t.id) };
    if (t.name === "serve") return await serveDir(root, t.path);
    if (t.name === "verify") return await verifyProject(root, opts);
    if (t.name === "check") {
      const tg = await checkTarget(t, root);
      if (tg.error) return { result: tg.error };
      const h = await httpPageCheck(tg.url);
      return { url: tg.url, http: h, result: checkReport(tg.url, h) };
    }
  } catch (e) { return { result: "FEHLER: " + e.message }; }
  return { result: "Unbekanntes Werkzeug: " + t.name };
}

// ---------- Browser-Test ohne Fenster (CLI): installiertes Chrome/Edge/Chromium headless ----------
function findChrome() {
  const env = process.env.MYTHOS_CHROME || process.env.CHROME_PATH;
  const pf = [process.env["PROGRAMFILES"], process.env["PROGRAMFILES(X86)"], process.env.LOCALAPPDATA].filter(Boolean);
  const list = (env ? [env] : []).concat(process.platform === "win32"
    ? pf.flatMap((b) => [b + "\\Google\\Chrome\\Application\\chrome.exe", b + "\\Microsoft\\Edge\\Application\\msedge.exe", b + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe"])
    : process.platform === "darwin"
      ? ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge", "/Applications/Chromium.app/Contents/MacOS/Chromium"]
      : ["/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/microsoft-edge", "/snap/bin/chromium"]);
  return list.find((f) => { try { return fs.statSync(f).isFile(); } catch { return false; } }) || "";
}
function chromeRun(bin, args, ms) {
  return new Promise((res) => {
    let out = "", err = "";
    const p = spawn(bin, args, { windowsHide: true });
    const to = setTimeout(() => killProc(p), ms);
    p.stdout.on("data", (d) => { out += d; }); p.stderr.on("data", (d) => { err += d; if (err.length > 400000) err = err.slice(-200000); });
    p.on("error", (e) => { clearTimeout(to); res({ out, err: err + e.message, code: 1 }); });
    p.on("close", (code) => { clearTimeout(to); res({ out, err, code }); });
  });
}
/** Seite in echtem Chrome rendern: sichtbarer Text, Konsolenfehler, Screenshot. null = kein Browser gefunden. */
async function headlessRender(url) {
  const bin = findChrome();
  if (!bin) return null;
  const tmp = path.join(process.env.TEMP || process.env.TMPDIR || "/tmp", "mythos-check-" + process.pid);
  try { fs.mkdirSync(tmp, { recursive: true }); } catch { /* egal */ }
  const shot = path.join(tmp, "shot.png");
  const base = ["--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check", "--hide-scrollbars", "--user-data-dir=" + path.join(tmp, "profil"),
    "--enable-logging=stderr", "--v=0", "--virtual-time-budget=5000", "--window-size=1280,800"];
  if (process.platform === "linux" && process.getuid && process.getuid() === 0) base.push("--no-sandbox");
  const dom = await chromeRun(bin, base.concat(["--dump-dom", url]), 45000);
  try { fs.rmSync(shot, { force: true }); } catch { /* egal */ }
  await chromeRun(bin, base.concat(["--screenshot=" + shot, url]), 45000);
  const errors = [];
  for (const m of dom.err.matchAll(/CONSOLE[:(]\d+\)?\]\s*"([^\n]*?)",\s*source:\s*(\S*)\s*\((\d+)\)/g)) {
    const msg = m[1] + (m[2] ? " (" + m[2].split("/").pop() + ":" + m[3] + ")" : "");
    if (!/favicon\.ico/.test(msg) && !errors.includes(msg) && /error|uncaught|failed|refused|not defined|cannot|unexpected|typeerror|syntaxerror|404|500/i.test(msg)) errors.push(msg);
  }
  const html = dom.out || "";
  const body = (html.match(/<body[^>]*>([\s\S]*)<\/body>/i) || [, html])[1];
  const text = htmlToText(body);
  const vis = (body.match(/<(?:img|svg|canvas|video|button|input|iframe)\b/gi) || []).length;
  let image = null;
  try { const b = fs.readFileSync(shot); if (b.length > 2000) image = { media_type: "image/png", data: b.toString("base64") }; } catch { /* kein Screenshot */ }
  return { ok: !!html, browser: path.basename(bin), title: (html.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [])[1] || "", text: text.length, sample: text.replace(/\s+/g, " ").slice(0, 200), vis, errors, image, shot: image ? shot : "" };
}
/** Browser-Ergebnis in Prüfzeilen übersetzen. r = { text, vis, errors, failed?, overlay?, sample } */
function renderVerdict(r) {
  const lines = []; let fail = 0;
  const blank = r.text < 3 && r.vis < 1;
  if (r.overlay) { fail++; lines.push("✗ Fehler-Overlay des Dev-Servers sichtbar: " + r.overlay.slice(0, 800)); }
  if (blank) { fail++; lines.push("✗ WEISSE SEITE: Im Browser erscheint kein Inhalt (kein Text, keine Bilder/Elemente)." + (r.errors.length ? " Ursache steht wahrscheinlich in den Konsolenfehlern unten." : " Prüfe, ob das Skript den Inhalt wirklich ins richtige Element (#root/#app) rendert und ob alle Dateien laden.")); }
  else lines.push("✓ Im Browser sichtbar: " + r.text + " Zeichen Text" + (r.vis ? ", " + r.vis + " Bilder/Bedienelemente" : "") + (r.sample ? " – „" + r.sample.slice(0, 140) + "“" : ""));
  const errs = [...new Set(r.errors)];
  if (errs.length) { fail++; lines.push("✗ Konsolenfehler (" + errs.length + "):\n" + errs.slice(0, 12).map((e) => "    " + e.slice(0, 400)).join("\n")); }
  else lines.push("✓ Keine Konsolenfehler");
  if (r.failed && r.failed.length) { fail++; lines.push("✗ Nicht geladene Dateien:\n" + r.failed.slice(0, 12).map((e) => "    " + e).join("\n")); }
  return { lines, fail };
}
