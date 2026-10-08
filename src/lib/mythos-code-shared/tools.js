// ---------- Gemeinsame Werkzeuge (CLI + Desktop-App) ----------
// Wird roh in bin/mythos.js (CLI, ESM) und main.js (App, CommonJS) eingefügt.
// Erwartet, dass `fs` und `path` im umgebenden Code schon importiert sind.

const TOOL_PROMPT = [
  "Werkzeuge – antworte pro Schritt mit GENAU EINEM Block:",
  '<tool>{"name":"run","cmd":"..."}</tool>  Shell-Befehl ausführen (Ausgabe läuft live mit)',
  '<tool>{"name":"read","path":"..."}</tool>  Datei lesen',
  '<tool>{"name":"edit","path":"...","old":"exakter alter Text","new":"neuer Text"}</tool>  Datei gezielt ändern (bevorzugt! "old" muss genau einmal vorkommen, sonst "all":true)',
  '<tool>{"name":"write","path":"...","content":"..."}</tool>  Neue Datei anlegen oder komplett neu schreiben',
  '<tool>{"name":"search","pattern":"regex","path":".","glob":"*.ts"}</tool>  Im Projekt suchen (path und glob optional)',
  '<tool>{"name":"ls","path":"."}</tool>  Ordner auflisten',
  '<tool>{"name":"websearch","query":"..."}</tool>  Im Internet suchen (z. B. Doku, Fehlermeldungen)',
  '<tool>{"name":"fetch","url":"https://..."}</tool>  Webseite als Text lesen',
  '<tool>{"name":"cloud","app":"Name der App"}</tool>  Mythos Cloud verbinden: eigenes Backend (Datenbank) + KI-Gateway (Chat, Streaming, Bilder) für die App, die du baust. Nutze das IMMER, sobald die App Daten speichern oder KI nutzen soll – statt Supabase/Firebase/OpenAI-Keys o. Ä. Legt mythos-cloud.js im Projekt an und erklärt die Nutzung.',
  '<tool>{"name":"todo","items":[{"text":"Schritt","done":false}]}</tool>  Aufgabenliste anlegen/aktualisieren (bei größeren Aufgaben zuerst planen, dann Punkte abhaken; immer die komplette Liste senden)',
  "Nach jedem Werkzeug bekommst du das Ergebnis. Suche erst, statt Dateien blind zu lesen. Ändere bestehende Dateien mit edit statt write.",
  "Arbeite Schritt für Schritt, bis die Aufgabe erledigt ist, dann antworte normal ohne <tool> (Markdown erlaubt).",
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
const SUMMARY_PROMPT = "Fasse jetzt kurz auf Deutsch zusammen, OHNE Werkzeuge:\n**Geändert:** welche Dateien und was\n**Geklappt:** was funktioniert (und wie geprüft)\n**Offen:** was noch fehlt oder beachtet werden muss\nMaximal 12 Zeilen.";

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
