import JSZip from "jszip";

export const CLI_PKG = "mythos-code";
const API = `https://${import.meta.env.VITE_SUPABASE_PROJECT_ID}.supabase.co/functions/v1/v1-messages`;

const pkgJson = () => JSON.stringify({
  name: CLI_PKG,
  version: "1.1.0",
  description: "Mythos Code – KI-Coding-Agent im Terminal (by Mythoscraft)",
  bin: { "mythos": "bin/mythos.js", "mythos-code": "bin/mythos.js" },
  type: "module",
  files: ["bin", "README.md"],
  engines: { node: ">=18" },
  keywords: ["ai", "cli", "agent", "mythos", "coding"],
  license: "MIT",
}, null, 2);

const cliJs = () => `#!/usr/bin/env node
// Mythos Code CLI – Agent mit Zugriff auf Dateien & Shell (mit Bestätigung)
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import readline from "node:readline";
import { execSync } from "node:child_process";

const API = process.env.MYTHOS_API_URL || "${API}";
const CFG = path.join(os.homedir(), ".mythos-code.json");
const C = { c: "\\x1b[36m", g: "\\x1b[32m", y: "\\x1b[33m", r: "\\x1b[31m", d: "\\x1b[2m", b: "\\x1b[1m", x: "\\x1b[0m" };
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
const ask = (q) => new Promise((r) => rl.question(q, r));
const load = () => { try { return JSON.parse(fs.readFileSync(CFG, "utf8")); } catch { return {}; } };
const save = (c) => fs.writeFileSync(CFG, JSON.stringify(c, null, 2));
let cfg = load();
let autoYes = process.argv.includes("--yes");

const SYSTEM = \`Du bist Mythos Code, ein autonomer Coding-Agent im Terminal des Nutzers (OS: \${process.platform}, CWD: \${process.cwd()}).
Du kannst Werkzeuge nutzen. Um ein Werkzeug aufzurufen, antworte mit GENAU EINEM Block:
<tool>{"name":"run","cmd":"..."}</tool>          -> Shell-Befehl ausführen
<tool>{"name":"read","path":"..."}</tool>        -> Datei lesen
<tool>{"name":"write","path":"...","content":"..."}</tool> -> Datei schreiben/erstellen
<tool>{"name":"ls","path":"."}</tool>            -> Ordner auflisten
Nach jedem Werkzeug bekommst du das Ergebnis. Arbeite Schritt für Schritt, bis die Aufgabe erledigt ist, dann antworte normal ohne <tool>. Sei präzise.\`;

async function login() {
  console.log(C.c + "Hol dir einen kostenlosen API-Key im Mythos Dashboard (sk-ant-mythos-...)." + C.x);
  const k = (await ask("API-Key: ")).trim();
  if (!k.startsWith("sk-ant-mythos-")) { console.log(C.r + "Ungültiger Key." + C.x); process.exit(1); }
  cfg.key = k; save(cfg); console.log(C.g + "✓ Gespeichert in " + CFG + C.x);
}

const fmt = (ms) => { const s = Math.floor(ms / 1000); return (s >= 60 ? Math.floor(s / 60) + "m " : "") + (s % 60) + "s"; };

async function once(messages) {
  const r = await fetch(API, {
    method: "POST",
    headers: { "content-type": "application/json", "x-api-key": cfg.key, "anthropic-version": "2023-06-01" },
    body: JSON.stringify({ model: cfg.model || "mythos-code", max_tokens: 8000, system: SYSTEM, messages, stream: true }),
  });
  if (!r.ok) { const j = await r.json().catch(() => ({})); const e = new Error(j?.error?.message || ("HTTP " + r.status)); e.status = r.status; throw e; }
  const reader = r.body.getReader(); const dec = new TextDecoder(); let buf = "", out = "";
  while (true) {
    const { done, value } = await reader.read(); if (done) break;
    buf += dec.decode(value, { stream: true });
    let i; while ((i = buf.indexOf("\\n")) !== -1) {
      const line = buf.slice(0, i).trim(); buf = buf.slice(i + 1);
      if (!line.startsWith("data:")) continue;
      try { const p = JSON.parse(line.slice(5)); if (p.delta?.text) out += p.delta.text; } catch {}
    }
  }
  if (!out.trim()) throw new Error("Leere Antwort");
  return out;
}

async function call(messages) {
  const t0 = Date.now();
  const tick = setInterval(() => process.stdout.write("\\r" + C.d + "⏳ Mythos arbeitet… " + fmt(Date.now() - t0) + C.x + "   "), 250);
  try {
    for (let a = 1; a <= 4; a++) {
      try { return await once(messages); }
      catch (e) { if (a === 4 || e.status === 401) throw e; await new Promise((r) => setTimeout(r, 1500 * a)); }
    }
  } finally { clearInterval(tick); process.stdout.write("\\r\\x1b[K"); }
}

async function runTool(t) {
  const show = t.name === "write" ? \`write \${t.path} (\${(t.content || "").length} Zeichen)\` : \`\${t.name} \${t.cmd || t.path || ""}\`;
  console.log(C.y + "⚙  " + show + C.x);
  if ((t.name === "run" || t.name === "write") && !autoYes) {
    const a = (await ask(C.d + "   Erlauben? [j/N/a=immer] " + C.x)).trim().toLowerCase();
    if (a === "a") autoYes = true; else if (a !== "j" && a !== "y") return "Vom Nutzer abgelehnt.";
  }
  try {
    if (t.name === "run") return execSync(t.cmd, { encoding: "utf8", stdio: "pipe", timeout: 120000, maxBuffer: 1e7 }).slice(-8000) || "(keine Ausgabe)";
    if (t.name === "read") return fs.readFileSync(t.path, "utf8").slice(0, 20000);
    if (t.name === "write") { fs.mkdirSync(path.dirname(path.resolve(t.path)), { recursive: true }); fs.writeFileSync(t.path, t.content ?? ""); return "OK geschrieben: " + t.path; }
    if (t.name === "ls") return fs.readdirSync(t.path || ".", { withFileTypes: true }).map((e) => (e.isDirectory() ? "📁 " : "   ") + e.name).join("\\n");
    return "Unbekanntes Werkzeug";
  } catch (e) { return "FEHLER: " + (e.stdout || "") + (e.stderr || "") + e.message; }
}

async function turn(history, input) {
  const T0 = Date.now();
  try { await turnInner(history, input); }
  finally { console.log(C.g + "✓ Mythos hat " + fmt(Date.now() - T0) + " gearbeitet" + C.x + "\\n"); }
}
async function turnInner(history, input) {
  history.push({ role: "user", content: input });
  for (let i = 0; i < 40; i++) {
    const out = await call(history);
    history.push({ role: "assistant", content: out });
    const m = out.match(/<tool>([\\s\\S]*?)<\\/tool>/);
    const text = out.replace(/<tool>[\\s\\S]*?<\\/tool>/g, "").trim();
    if (text) console.log(C.b + "Mythos: " + C.x + text + "\\n");
    if (!m) return;
    let t; try { t = JSON.parse(m[1]); } catch { history.push({ role: "user", content: "Tool-JSON ungültig." }); continue; }
    const res = await runTool(t);
    history.push({ role: "user", content: "Werkzeug-Ergebnis:\\n" + res });
  }
}

async function main() {
  const args = process.argv.slice(2).filter((a) => a !== "--yes");
  if (args[0] === "login" || !cfg.key) await login();
  if (args[0] === "login") process.exit(0);
  if (args[0] === "logout") { save({}); console.log("Abgemeldet."); process.exit(0); }
  console.log(C.c + C.b + "\\n  ✦ MYTHOS CODE" + C.x + C.d + "  – by Mythoscraft · /exit zum Beenden · /clear leeren\\n" + C.x);
  const history = [];
  if (args.length) { await turn(history, args.join(" ")); }
  while (true) {
    const q = (await ask(C.c + "› " + C.x)).trim();
    if (!q) continue;
    if (q === "/exit") break;
    if (q === "/clear") { history.length = 0; console.log("Verlauf geleert."); continue; }
    try { await turn(history, q); } catch (e) { console.log(C.r + "Fehler: " + e.message + C.x); }
  }
  rl.close();
}
main();
`;

const readme = () => `# Mythos Code

KI-Coding-Agent im Terminal – by Mythoscraft.

\`\`\`
npm install -g ${CLI_PKG}
mythos login
mythos
\`\`\`
`;

export async function buildCliZip(): Promise<Blob> {
  const zip = new JSZip();
  const root = zip.folder(CLI_PKG)!;
  root.file("package.json", pkgJson());
  root.file("README.md", readme());
  root.folder("bin")!.file("mythos.js", cliJs(), { unixPermissions: "755" });
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
Dann anmelden (API-Key aus deinem Dashboard):
\`\`\`cmd
mythos login
\`\`\`
Und starten – in dem Projektordner, an dem Mythos arbeiten soll:
\`\`\`cmd
mythos
\`\`\`
Mythos Code kann Dateien lesen/schreiben und Befehle ausführen – vor jeder Aktion fragt es dich (\`a\` = immer erlauben, oder mit \`mythos --yes\` starten).
Voraussetzung: [Node.js](https://nodejs.org) ab Version 18.`;
