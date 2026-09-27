import JSZip from "jszip";

export const CLI_PKG = "mythos-code";
export const CLI_VERSION = "1.2.0";
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

const cliJs = () => `#!/usr/bin/env node
// Mythos Code CLI – Agent mit Zugriff auf Dateien & Shell (mit Bestätigung)
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import readline from "node:readline";
import { exec } from "node:child_process";

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

// Live-Timer: läuft während der ganzen Aufgabe (auch während Befehle laufen), pausiert nur für Rückfragen.
let T0 = 0, tick = null;
const clearLine = () => process.stdout.write("\\r\\x1b[K");
const startTick = () => { if (tick) return; tick = setInterval(() => process.stdout.write("\\r" + C.d + "⏳ Mythos arbeitet… " + fmt(Date.now() - T0) + C.x + "   "), 250); };
const stopTick = () => { if (tick) { clearInterval(tick); tick = null; } clearLine(); };
const say = (s) => { const was = !!tick; stopTick(); console.log(s); if (was) startTick(); };

// Verlauf kürzen, damit lange Aufgaben nicht an zu großem Kontext scheitern. level 0 = mild, 2 = stark.
const shorten = (s, max) => s.length <= max ? s : s.slice(0, Math.floor(max * 0.7)) + "\\n…[gekürzt]…\\n" + s.slice(s.length - Math.floor(max * 0.3));
function compact(history, level) {
  const keep = [6, 8, 4][level], recentMax = [20000, 3000, 1500][level], oldMax = [1500, 800, 400][level];
  const cut = Math.max(0, history.length - keep);
  const older = level === 0 ? history.slice(0, cut) : history.slice(0, Math.min(1, cut));
  return older.map((m) => ({ role: m.role, content: shorten(m.content, oldMax) }))
    .concat(history.slice(cut).map((m) => ({ role: m.role, content: shorten(m.content, recentMax) })));
}

async function once(messages) {
  const ctl = new AbortController(); const to = setTimeout(() => ctl.abort(), 170000);
  try {
    const r = await fetch(API, {
      method: "POST", signal: ctl.signal,
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
        let p; try { p = JSON.parse(line.slice(5)); } catch { continue; }
        if (p.type === "error") { const e = new Error(p.error?.message || "Überlastet"); e.status = 529; throw e; }
        if (p.delta?.text) out += p.delta.text;
      }
    }
    if (!out.trim()) throw new Error("Leere Antwort");
    return out;
  } finally { clearTimeout(to); }
}

async function call(history) {
  for (let a = 0; a < 6; a++) {
    try { return await once(compact(history, Math.min(2, Math.floor(a / 2)))); }
    catch (e) {
      if (e.status === 401) throw new Error("API-Key ungültig – bitte neu anmelden: mythos login");
      if (/Daily limit/i.test(e.message)) throw new Error("Tageslimit erreicht – mit Pro unbegrenzt.");
      await new Promise((r) => setTimeout(r, Math.min(8000, 1500 * (a + 1))));
    }
  }
  throw new Error("Mythos ist gerade nicht erreichbar – bitte gleich nochmal versuchen.");
}

async function runTool(t) {
  const show = t.name === "write" ? \`write \${t.path} (\${(t.content || "").length} Zeichen)\` : \`\${t.name} \${t.cmd || t.path || ""}\`;
  say(C.y + "⚙  " + show + C.x);
  if ((t.name === "run" || t.name === "write") && !autoYes) {
    stopTick();
    const a = (await ask(C.d + "   Erlauben? [j/N/a=immer] " + C.x)).trim().toLowerCase();
    startTick();
    if (a === "a") autoYes = true; else if (a !== "j" && a !== "y") return "Vom Nutzer abgelehnt.";
  }
  try {
    if (t.name === "run") return await new Promise((res) => exec(t.cmd, { encoding: "utf8", timeout: 180000, maxBuffer: 1e7 },
      (err, so, se) => res(((so || "") + (se || "") + (err ? "\\nExit: " + (err.code ?? err.message) : "")).slice(-8000) || "(keine Ausgabe)")));
    if (t.name === "read") return fs.readFileSync(t.path, "utf8").slice(0, 20000);
    if (t.name === "write") { fs.mkdirSync(path.dirname(path.resolve(t.path)), { recursive: true }); fs.writeFileSync(t.path, t.content ?? ""); return "OK geschrieben: " + t.path; }
    if (t.name === "ls") return fs.readdirSync(t.path || ".", { withFileTypes: true }).map((e) => (e.isDirectory() ? "📁 " : "   ") + e.name).join("\\n");
    return "Unbekanntes Werkzeug";
  } catch (e) { return "FEHLER: " + (e.stdout || "") + (e.stderr || "") + e.message; }
}

async function turn(history, input) {
  T0 = Date.now(); startTick();
  try { await turnInner(history, input); }
  catch (e) { say(C.r + "⚠ " + e.message + C.x); }
  finally { stopTick(); console.log(C.g + "✓ Mythos hat " + fmt(Date.now() - T0) + " gearbeitet" + C.x + "\\n"); }
}
async function turnInner(history, input) {
  history.push({ role: "user", content: input });
  for (let i = 0; i < 40; i++) {
    const out = await call(history);
    history.push({ role: "assistant", content: out });
    const m = out.match(/<tool>([\\s\\S]*?)<\\/tool>/);
    const text = out.replace(/<tool>[\\s\\S]*?<\\/tool>/g, "").trim();
    if (text) say(C.b + "Mythos: " + C.x + text + "\\n");
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
    await turn(history, q);
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

// ================= Mythos Code Desktop-App (Electron) =================
export const APP_PKG = "mythos-code-app";
export const APP_DOWNLOAD_SETTING = "codeprogram_download_url";

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
3. Ordner wählen, optional **Vollzugriff** einschalten – fertig. Mit 📎 kannst du Dateien hochladen, oben siehst du Laufzeit und Usage (Pro = unbegrenzt).`;
const FN_BASE = `https://${import.meta.env.VITE_SUPABASE_PROJECT_ID}.supabase.co/functions/v1`;

const appPkg = () => JSON.stringify({
  name: APP_PKG,
  productName: "Mythos Code",
  version: "1.1.0",
  description: "Mythos Code – KI-Coding-Agent als App (by Mythoscraft)",
  main: "main.js",
  author: "Mythoscraft",
  license: "MIT",
  scripts: { start: "electron .", dist: "electron-builder --win nsis --publish never" },
  devDependencies: { electron: "^31.0.0", "electron-builder": "^24.13.3" },
  build: {
    appId: "online.mythoscraft.mythoscode",
    productName: "Mythos Code",
    files: ["main.js", "preload.js", "index.html", "renderer.js", "config.json"],
    win: { target: "nsis" },
    nsis: { oneClick: false, allowToChangeInstallationDirectory: true, createDesktopShortcut: true, artifactName: "MythosCode-Setup.exe" },
  },
}, null, 2);

const appMain = () => `const { app, BrowserWindow, ipcMain, dialog, shell } = require("electron");
const fs = require("fs"); const path = require("path"); const { exec } = require("child_process");
const CFG = () => path.join(app.getPath("userData"), "mythos.json");
const load = () => { try { return JSON.parse(fs.readFileSync(CFG(), "utf8")); } catch { return {}; } };
function win() {
  const w = new BrowserWindow({ width: 1200, height: 820, backgroundColor: "#0a0a0a", title: "Mythos Code",
    autoHideMenuBar: true, webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true } });
  w.loadFile("index.html");
}
app.whenReady().then(win);
app.on("window-all-closed", () => app.quit());
ipcMain.handle("cfg:get", () => load());
ipcMain.handle("cfg:set", (_e, c) => { fs.writeFileSync(CFG(), JSON.stringify(c, null, 2)); return true; });
ipcMain.handle("open", (_e, url) => shell.openExternal(url));
ipcMain.handle("pickFolder", async () => { const r = await dialog.showOpenDialog({ properties: ["openDirectory"] }); return r.canceled ? null : r.filePaths[0]; });
ipcMain.handle("pickFiles", async () => {
  const r = await dialog.showOpenDialog({ properties: ["openFile", "multiSelections"] });
  if (r.canceled) return [];
  return r.filePaths.map((p) => { let c = ""; try { c = fs.readFileSync(p, "utf8").slice(0, 60000); } catch { c = "(Binärdatei)"; } return { name: path.basename(p), path: p, content: c }; });
});
ipcMain.handle("tool", async (_e, t, cwd) => {
  const P = (p) => path.resolve(cwd || process.cwd(), p || ".");
  try {
    if (t.name === "run") return await new Promise((res) => exec(t.cmd, { cwd, timeout: 180000, maxBuffer: 1e7, shell: true },
      (err, so, se) => res(((so || "") + (se || "") + (err ? "\\nExit: " + err.code : "")).slice(-8000) || "(keine Ausgabe)")));
    if (t.name === "read") return fs.readFileSync(P(t.path), "utf8").slice(0, 20000);
    if (t.name === "write") { fs.mkdirSync(path.dirname(P(t.path)), { recursive: true }); fs.writeFileSync(P(t.path), t.content || ""); return "OK geschrieben: " + t.path; }
    if (t.name === "ls") return fs.readdirSync(P(t.path), { withFileTypes: true }).map((e) => (e.isDirectory() ? "[D] " : "    ") + e.name).join("\\n");
    return "Unbekanntes Werkzeug";
  } catch (e) { return "FEHLER: " + e.message; }
});
`;

const appPreload = () => `const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("mythos", {
  getCfg: () => ipcRenderer.invoke("cfg:get"), setCfg: (c) => ipcRenderer.invoke("cfg:set", c),
  open: (u) => ipcRenderer.invoke("open", u), pickFolder: () => ipcRenderer.invoke("pickFolder"),
  pickFiles: () => ipcRenderer.invoke("pickFiles"), tool: (t, cwd) => ipcRenderer.invoke("tool", t, cwd),
});
`;

const appHtml = () => `<!doctype html><html lang="de"><head><meta charset="utf-8"><title>Mythos Code</title>
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; connect-src https:; style-src 'self' 'unsafe-inline'; script-src 'self'">
<style>
*{box-sizing:border-box}body{margin:0;font-family:Segoe UI,system-ui,sans-serif;background:radial-gradient(circle at 20% 0%,#2a2a2a,#050505 60%);color:#eee;height:100vh;display:flex;flex-direction:column}
.glass{background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);backdrop-filter:blur(20px);border-radius:16px}
button{background:#fff;color:#000;border:0;border-radius:12px;padding:10px 16px;font-weight:600;cursor:pointer}button.ghost{background:rgba(255,255,255,.08);color:#eee}
#login{margin:auto;padding:40px;text-align:center;width:420px}#login h1{letter-spacing:.2em;margin:0 0 8px}
header{display:flex;gap:10px;align-items:center;padding:12px 16px;border-bottom:1px solid rgba(255,255,255,.1)}header .sp{flex:1}
.pill{font-size:12px;padding:6px 10px;border-radius:999px;background:rgba(255,255,255,.08)}
#log{flex:1;overflow:auto;padding:20px;display:flex;flex-direction:column;gap:10px}
.m{padding:12px 14px;max-width:85%;white-space:pre-wrap;line-height:1.5}.u{align-self:flex-end;background:rgba(255,255,255,.14)}.a{align-self:flex-start}.t{font-family:Consolas,monospace;font-size:12px;opacity:.7}
footer{padding:12px 16px;display:flex;gap:8px;align-items:flex-end}textarea{flex:1;resize:none;height:60px;background:rgba(255,255,255,.06);color:#eee;border:1px solid rgba(255,255,255,.15);border-radius:12px;padding:10px;font:inherit}
#files{font-size:12px;opacity:.8;padding:0 16px}
</style></head><body>
<div id="login" class="glass"><h1>✦ MYTHOS CODE</h1><p style="opacity:.7">KI-Coding-Agent von Mythoscraft</p>
<button id="btnLogin">Mit MythosAI anmelden</button><p id="lstat" style="opacity:.6;font-size:13px"></p></div>
<div id="app" style="display:none;flex:1;flex-direction:column;min-height:0">
<header><b>✦ MYTHOS CODE</b><span class="pill" id="folder">Kein Ordner</span><button class="ghost" id="btnFolder">Ordner wählen</button>
<label class="pill"><input type="checkbox" id="auto"> Vollzugriff (nicht nachfragen)</label><span class="sp"></span>
<span class="pill" id="timer">⏱ 0s</span><span class="pill" id="usage">Usage …</span><button class="ghost" id="btnOut">Abmelden</button></header>
<div id="log"></div><div id="files"></div>
<footer><button class="ghost" id="btnUp">📎 Dateien</button><textarea id="inp" placeholder="Was soll Mythos bauen? (Enter = senden)"></textarea><button id="btnSend">Senden</button></footer></div>
<script src="renderer.js"></script></body></html>`;

const appRenderer = () => `const CFG = ${JSON.stringify({ site: "__SITE__", fn: FN_BASE })};
CFG.site = location.protocol === "file:" ? CFG.site : CFG.site;
const $ = (id) => document.getElementById(id);
let cfg = {}, history = [], attach = [], busy = false;
const fmt = (ms) => { const s = Math.floor(ms / 1000); return (s >= 60 ? Math.floor(s / 60) + "m " : "") + (s % 60) + "s"; };
const post = (fn, body, h) => fetch(CFG.fn + "/" + fn, { method: "POST", headers: Object.assign({ "content-type": "application/json" }, h || {}), body: JSON.stringify(body) });
function add(cls, text) { const d = document.createElement("div"); d.className = "m glass " + cls; d.textContent = text; $("log").appendChild(d); $("log").scrollTop = 1e9; return d; }
const SYSTEM = () => "Du bist Mythos Code, ein autonomer Coding-Agent als Desktop-App (Windows). Arbeitsordner: " + (cfg.folder || "(keiner)") +
  ". Werkzeuge – antworte mit GENAU EINEM Block:\\n<tool>{\\"name\\":\\"run\\",\\"cmd\\":\\"...\\"}</tool>\\n<tool>{\\"name\\":\\"read\\",\\"path\\":\\"...\\"}</tool>\\n<tool>{\\"name\\":\\"write\\",\\"path\\":\\"...\\",\\"content\\":\\"...\\"}</tool>\\n<tool>{\\"name\\":\\"ls\\",\\"path\\":\\".\\"}</tool>\\nPfade relativ zum Arbeitsordner. Arbeite Schritt für Schritt, am Ende normal ohne <tool> antworten.";
const shorten = (s, max) => s.length <= max ? s : s.slice(0, Math.floor(max * 0.7)) + "\\n…[gekürzt]…\\n" + s.slice(s.length - Math.floor(max * 0.3));
function compact(h, level) {
  const keep = [6, 8, 4][level], recentMax = [20000, 3000, 1500][level], oldMax = [1500, 800, 400][level];
  const cut = Math.max(0, h.length - keep);
  const older = level === 0 ? h.slice(0, cut) : h.slice(0, Math.min(1, cut));
  return older.map((m) => ({ role: m.role, content: shorten(m.content, oldMax) }))
    .concat(h.slice(cut).map((m) => ({ role: m.role, content: shorten(m.content, recentMax) })));
}
async function once(messages) {
  const ctl = new AbortController(); const to = setTimeout(() => ctl.abort(), 170000);
  try {
    const r = await fetch(CFG.fn + "/v1-messages", { method: "POST", signal: ctl.signal,
      headers: { "content-type": "application/json", "x-api-key": cfg.key, "anthropic-version": "2023-06-01" },
      body: JSON.stringify({ model: "mythos-code", max_tokens: 8000, system: SYSTEM(), messages, stream: true }) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); const e = new Error((j.error && j.error.message) || ("HTTP " + r.status)); e.status = r.status; throw e; }
    const rd = r.body.getReader(), dec = new TextDecoder(); let buf = "", out = "";
    for (;;) { const x = await rd.read(); if (x.done) break; buf += dec.decode(x.value, { stream: true }); let i;
      while ((i = buf.indexOf("\\n")) !== -1) { const l = buf.slice(0, i).trim(); buf = buf.slice(i + 1);
        if (!l.startsWith("data:")) continue; let p; try { p = JSON.parse(l.slice(5)); } catch (e) { continue; }
        if (p.type === "error") { const e = new Error((p.error && p.error.message) || "Überlastet"); e.status = 529; throw e; }
        if (p.delta && p.delta.text) out += p.delta.text; } }
    if (!out.trim()) throw new Error("Leere Antwort"); return out;
  } finally { clearTimeout(to); }
}
async function call(h) {
  for (let a = 0; a < 6; a++) {
    try { return await once(compact(h, Math.min(2, Math.floor(a / 2)))); }
    catch (e) {
      if (e.status === 401) throw new Error("Anmeldung abgelaufen – bitte abmelden und neu anmelden.");
      if (/Daily limit/i.test(e.message)) throw new Error("Tageslimit erreicht – mit Pro unbegrenzt.");
      await new Promise((r) => setTimeout(r, Math.min(8000, 1500 * (a + 1))));
    }
  }
  throw new Error("Mythos ist gerade nicht erreichbar – bitte gleich nochmal versuchen.");
}
async function usage() { try { const j = await (await post("cli-auth", { action: "usage", api_key: cfg.key })).json();
  $("usage").textContent = j.limit == null ? "Usage " + j.used + " / ∞ (Pro)" : "Usage " + j.used + " / " + j.limit; } catch (e) {} }
async function send() {
  const q = $("inp").value.trim(); if (!q || busy) return; $("inp").value = ""; busy = true;
  let content = q; if (attach.length) content += "\\n\\nHochgeladene Dateien:\\n" + attach.map((f) => "### " + f.name + "\\n" + f.content).join("\\n\\n");
  attach = []; $("files").textContent = ""; add("u", q); history.push({ role: "user", content });
  const t0 = Date.now(); const live = add("t", "⏳ Mythos arbeitet… 0s");
  const tick = setInterval(() => { const s = fmt(Date.now() - t0); $("timer").textContent = "⏱ arbeitet… " + s; live.textContent = "⏳ Mythos arbeitet… " + s; $("log").appendChild(live); }, 250);
  try {
    for (let i = 0; i < 40; i++) {
      const out = await call(history); history.push({ role: "assistant", content: out });
      const m = out.match(/<tool>([\\s\\S]*?)<\\/tool>/); const text = out.replace(/<tool>[\\s\\S]*?<\\/tool>/g, "").trim();
      if (text) add("a", text); if (!m) break;
      let t; try { t = JSON.parse(m[1]); } catch (e) { history.push({ role: "user", content: "Tool-JSON ungültig." }); continue; }
      add("t", "⚙ " + t.name + " " + (t.cmd || t.path || ""));
      let res; if ((t.name === "run" || t.name === "write") && !$("auto").checked && !confirm("Mythos möchte ausführen:\\n" + t.name + " " + (t.cmd || t.path))) res = "Vom Nutzer abgelehnt.";
      else res = await window.mythos.tool(t, cfg.folder);
      history.push({ role: "user", content: "Werkzeug-Ergebnis:\\n" + res });
    }
  } catch (e) { add("a", "⚠ " + e.message); }
  clearInterval(tick); const took = fmt(Date.now() - t0); $("timer").textContent = "⏱ " + took;
  live.textContent = "✓ Mythos hat " + took + " gearbeitet"; $("log").appendChild(live); $("log").scrollTop = 1e9; busy = false; usage();
}
async function login() {
  $("lstat").textContent = "Browser öffnet sich …";
  const s = await (await post("cli-auth", { action: "start" })).json();
  window.mythos.open(CFG.site + "/cli-auth?code=" + s.code);
  $("lstat").textContent = "Code: " + s.code + " – bestätige im Browser.";
  const iv = setInterval(async () => { const p = await (await post("cli-auth", { action: "poll", code: s.code, poll_secret: s.poll_secret })).json();
    if (p.status === "ok") { clearInterval(iv); cfg.key = p.api_key; cfg.name = p.name; await window.mythos.setCfg(cfg); show(); }
    if (p.status === "expired") { clearInterval(iv); $("lstat").textContent = "Abgelaufen – nochmal versuchen."; } }, 2000);
}
function show() { $("login").style.display = "none"; $("app").style.display = "flex"; $("folder").textContent = cfg.folder || "Kein Ordner"; usage(); }
$("btnLogin").onclick = login; $("btnSend").onclick = send;
$("inp").onkeydown = (e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } };
$("btnFolder").onclick = async () => { const f = await window.mythos.pickFolder(); if (f) { cfg.folder = f; await window.mythos.setCfg(cfg); $("folder").textContent = f; } };
$("btnUp").onclick = async () => { const fs = await window.mythos.pickFiles(); attach = attach.concat(fs); $("files").textContent = "📎 " + attach.map((f) => f.name).join(", "); };
$("btnOut").onclick = async () => { cfg = {}; await window.mythos.setCfg(cfg); location.reload(); };
window.mythos.getCfg().then((c) => { cfg = c || {}; if (cfg.key) show(); });
`;

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
  root.file("package.json", appPkg());
  root.file("main.js", appMain());
  root.file("preload.js", appPreload());
  root.file("index.html", appHtml());
  root.file("renderer.js", appRenderer().replace("__SITE__", site));
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
