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
  ? "\n\nZIEL (/goal): " + goal + "\nDer Nutzer ist nicht am Rechner. Arbeite komplett selbstständig, ohne Rückfragen, bis das Ziel vollständig erreicht und überprüft ist. Erst dann schreibe in deiner letzten Antwort eine eigene Zeile: ZIEL ERREICHT"
  : "";
const GOAL_NUDGE = "Das Ziel ist noch nicht als erreicht gemeldet. Arbeite selbstständig weiter, ohne Rückfragen. Wenn es wirklich vollständig erledigt und geprüft ist, schreibe ZIEL ERREICHT.";
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

/** read/write/edit/search/ls ausführen. `run` macht jede Oberfläche selbst (Live-Ausgabe). */
function fileTool(t, root) {
  const P = (p) => path.resolve(root, p || ".");
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
