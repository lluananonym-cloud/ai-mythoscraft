import JSZip from "jszip";

export const CLI_PKG = "mythos-code";
export const CLI_VERSION = "1.3.0";
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

const SITE = process.env.MYTHOS_SITE_URL || "__SITE__";
const FN = process.env.MYTHOS_FN_URL || API.replace(/\\/v1-messages$/, "");
const authPost = async (body) => (await fetch(FN + "/cli-auth", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) })).json();

function openBrowser(url) {
  const cmd = process.platform === "win32" ? \`start "" "\${url}"\` : process.platform === "darwin" ? \`open "\${url}"\` : \`xdg-open "\${url}"\`;
  exec(cmd, () => {});
}

async function loginWithKey() {
  const k = (await ask("API-Key (sk-ant-mythos-...): ")).trim();
  if (!k.startsWith("sk-ant-mythos-")) { console.log(C.r + "Ungültiger Key." + C.x); process.exit(1); }
  cfg.key = k; save(cfg); console.log(C.g + "✓ Angemeldet" + C.x);
}

// Anmeldung über die Website: Browser öffnet sich, dort auf "Authentifizieren" klicken.
async function login() {
  if (process.argv.includes("--key")) return loginWithKey();
  let s;
  try { s = await authPost({ action: "start", client: "cli" }); } catch { s = null; }
  if (!s?.code) { console.log(C.r + "Anmeldung gerade nicht möglich – versuch es gleich nochmal." + C.x); process.exit(1); }
  const url = SITE + "/cli-auth?code=" + s.code;
  console.log(C.c + "\\n  Anmeldung mit MythosAI" + C.x);
  console.log("  Dein Browser öffnet sich. Klicke dort auf " + C.b + "Authentifizieren" + C.x + ".");
  console.log(C.d + "  Code: " + s.code + "  ·  Falls sich nichts öffnet: " + url + C.x + "\\n");
  openBrowser(url);
  const until = Date.now() + 15 * 60e3;
  process.stdout.write(C.d + "  Warte auf Bestätigung…" + C.x);
  while (Date.now() < until) {
    await new Promise((r) => setTimeout(r, 2000));
    let p; try { p = await authPost({ action: "poll", code: s.code, poll_secret: s.poll_secret }); } catch { continue; }
    if (p.status === "ok") { cfg.key = p.api_key; cfg.name = p.name; save(cfg); clearLine(); console.log(C.g + "  ✓ Angemeldet als " + p.name + C.x + "\\n"); return; }
    if (p.status === "expired") break;
  }
  clearLine(); console.log(C.r + "  Anmeldung abgelaufen – starte nochmal mit: mythos login" + C.x); process.exit(1);
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
      if (e.status === 401) throw new Error("Anmeldung ungültig – bitte neu anmelden: mythos login");
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
  const args = process.argv.slice(2).filter((a) => a !== "--yes" && a !== "--key");
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

export async function buildCliZip(site: string): Promise<Blob> {
  const zip = new JSZip();
  const root = zip.folder(CLI_PKG)!;
  root.file("package.json", pkgJson());
  root.file("README.md", readme());
  root.folder("bin")!.file("mythos.js", cliJs().replace("__SITE__", site), { unixPermissions: "755" });
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
  version: "1.2.0",
  description: "Mythos Code – KI-Coding-Agent als App (by Mythoscraft)",
  main: "main.js",
  author: "Mythoscraft",
  license: "MIT",
  scripts: { start: "electron .", dist: "electron-builder --win nsis --publish never" },
  devDependencies: { electron: "^31.0.0", "electron-builder": "^24.13.3" },
  build: {
    appId: "online.mythoscraft.mythoscode",
    productName: "Mythos Code",
    files: ["main.js", "preload.js", "index.html", "renderer.js", "config.json", "icon.png"],
    win: { target: "nsis", icon: "icon.png" },
    nsis: { oneClick: false, allowToChangeInstallationDirectory: true, createDesktopShortcut: true, shortcutName: "Mythos Code", artifactName: "MythosCode-Setup.exe" },
  },
}, null, 2);

const appMain = () => `const { app, BrowserWindow, ipcMain, dialog, shell } = require("electron");
const fs = require("fs"); const path = require("path"); const { exec } = require("child_process");
const CFG = () => path.join(app.getPath("userData"), "mythos.json");
const load = () => { try { return JSON.parse(fs.readFileSync(CFG(), "utf8")); } catch { return {}; } };
function win() {
  const w = new BrowserWindow({ width: 1200, height: 820, minWidth: 720, minHeight: 520, backgroundColor: "#0a0a0a", title: "Mythos Code",
    icon: path.join(__dirname, "icon.png"),
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
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; connect-src https:; img-src 'self' data:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; script-src 'self'">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Instrument+Serif&family=JetBrains+Mono:wght@400;500&display=swap">
<style>
:root{--bg:#0a0a0a;--fg:#fafafa;--muted:#8a8a8a;--line:rgba(255,255,255,.08);--line2:rgba(255,255,255,.14);--panel:#171717}
*{box-sizing:border-box}html,body{height:100%}
body{margin:0;font-family:Inter,"Segoe UI",system-ui,sans-serif;background:var(--bg);color:var(--fg);display:flex;flex-direction:column;overflow:hidden;-webkit-font-smoothing:antialiased}
.bg{position:fixed;inset:0;pointer-events:none;z-index:0;overflow:hidden}
.blob{position:absolute;border-radius:50%;filter:blur(120px);opacity:.22;animation:float 22s ease-in-out infinite}
.b1{width:520px;height:520px;background:#7c3aed;top:-180px;left:-120px}.b2{width:480px;height:480px;background:#0ea5e9;bottom:-200px;right:-120px;animation-delay:-8s}
@keyframes float{0%,100%{transform:translate(0,0)}50%{transform:translate(40px,30px)}}
@keyframes bob{0%,100%{transform:translateY(0)}50%{transform:translateY(-8px)}}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
.serif{font-family:"Instrument Serif",Georgia,serif;font-weight:400;letter-spacing:-.01em}
.grad{background:linear-gradient(180deg,#fff,#b3b3b3);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent}
.glass{background:linear-gradient(135deg,rgba(255,255,255,.07),rgba(255,255,255,.02)),rgba(20,20,20,.6);border:1px solid var(--line);backdrop-filter:blur(24px);box-shadow:0 8px 32px rgba(0,0,0,.5),inset 0 1px 0 rgba(255,255,255,.08)}
button{font:inherit;cursor:pointer;border:0;color:inherit;background:none}
.btn{background:#fff;color:#0a0a0a;border-radius:999px;padding:12px 26px;font-weight:600;font-size:15px;transition:transform .15s,opacity .15s}.btn:hover{transform:translateY(-1px)}.btn:disabled{opacity:.5;cursor:default;transform:none}
.chip{display:inline-flex;align-items:center;gap:6px;font-size:12px;padding:7px 12px;border-radius:999px;border:1px solid var(--line);background:rgba(255,255,255,.04);color:#d4d4d4;white-space:nowrap}
button.chip:hover{background:rgba(255,255,255,.09)}
.mono{font-family:"JetBrains Mono",Consolas,monospace}
.screen{position:relative;z-index:1;flex:1;display:flex;flex-direction:column;min-height:0}
#login{align-items:center;justify-content:center;text-align:center;padding:24px}
.logo-xl{width:132px;height:132px;animation:bob 6s ease-in-out infinite;filter:drop-shadow(0 0 40px rgba(124,58,237,.45))}
#login h1{font-size:56px;margin:18px 0 6px;line-height:1}
#login p{color:var(--muted);margin:0 0 30px;font-size:15px}
#lstat{margin-top:22px;min-height:60px;color:var(--muted);font-size:13px}
#lstat .code{display:inline-block;margin-top:10px;font-size:22px;letter-spacing:.3em;padding:10px 18px;border-radius:14px;color:var(--fg)}
header{display:flex;gap:8px;align-items:center;padding:12px 18px;border-bottom:1px solid var(--line)}
header .brand{display:flex;align-items:center;gap:10px;margin-right:6px}header .brand img{width:28px;height:28px}header .brand span{font-size:22px}
header .sp{flex:1}
#timer.busy::before{content:"";width:7px;height:7px;border-radius:50%;background:#a78bfa;animation:pulse 1.2s infinite}
#log{flex:1;overflow:auto;padding:28px 18px 12px}
.col{max-width:820px;margin:0 auto;display:flex;flex-direction:column;gap:14px}
#empty{text-align:center;padding-top:9vh}#empty img{width:96px;height:96px;animation:bob 6s ease-in-out infinite;filter:drop-shadow(0 0 32px rgba(124,58,237,.4))}
#empty h2{font-size:42px;margin:14px 0 6px}#empty p{color:var(--muted);margin:0 0 22px}
.sugg{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
.m{line-height:1.6;font-size:14.5px;white-space:pre-wrap;word-wrap:break-word}
.u{align-self:flex-end;max-width:80%;padding:12px 16px;border-radius:20px 20px 6px 20px}
.a{display:flex;gap:12px;align-items:flex-start}.a img{width:26px;height:26px;margin-top:1px;flex-shrink:0}.a .body{min-width:0;flex:1}
.m pre{white-space:pre;overflow:auto;background:#111;border:1px solid var(--line);border-radius:12px;padding:12px 14px;font-family:"JetBrains Mono",Consolas,monospace;font-size:12.5px;margin:8px 0}
.t{align-self:flex-start;font-family:"JetBrains Mono",Consolas,monospace;font-size:12px;color:#a3a3a3;padding:6px 12px;border-radius:10px;border:1px solid var(--line);background:rgba(255,255,255,.03);max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.t.live{color:#c4b5fd;animation:pulse 1.6s infinite}.t.done{color:#86efac;animation:none}
footer{padding:10px 18px 18px}
.composer{max-width:820px;margin:0 auto;border-radius:28px;background:var(--panel);border:1px solid var(--line2);box-shadow:0 8px 32px rgba(0,0,0,.4);padding:8px;display:flex;gap:6px;align-items:flex-end;transition:border-color .15s}
.composer:focus-within{border-color:rgba(255,255,255,.28)}
.round{width:40px;height:40px;border-radius:50%;display:grid;place-items:center;flex-shrink:0;font-size:17px}
#btnUp{background:rgba(255,255,255,.06)}#btnUp:hover{background:rgba(255,255,255,.12)}
#btnSend{background:#fff;color:#0a0a0a;font-weight:700}#btnSend:disabled{opacity:.35}
textarea{flex:1;resize:none;min-height:40px;max-height:200px;background:transparent;color:var(--fg);border:0;outline:0;padding:10px 6px;font:inherit;font-size:15px;line-height:1.45}
textarea::placeholder{color:#737373}
.under{max-width:820px;margin:8px auto 0;display:flex;gap:8px;align-items:center;flex-wrap:wrap;font-size:12px;color:var(--muted)}
.switch{display:inline-flex;align-items:center;gap:8px;cursor:pointer;user-select:none}
.switch input{appearance:none;width:30px;height:18px;border-radius:999px;background:rgba(255,255,255,.15);position:relative;cursor:pointer;margin:0;transition:background .15s}
.switch input::after{content:"";position:absolute;top:2px;left:2px;width:14px;height:14px;border-radius:50%;background:#fff;transition:left .15s}
.switch input:checked{background:#8b5cf6}.switch input:checked::after{left:14px}
#files .chip{margin-right:4px}
::-webkit-scrollbar{width:10px}::-webkit-scrollbar-thumb{background:rgba(255,255,255,.1);border-radius:10px;border:3px solid var(--bg)}
</style></head><body>
<div class="bg"><div class="blob b1"></div><div class="blob b2"></div></div>
<div id="login" class="screen">
  <img class="logo-xl" src="icon.png" alt="Mythoscraft">
  <h1 class="serif grad">Mythos Code</h1>
  <p>Dein KI-Coding-Agent von Mythoscraft</p>
  <button class="btn" id="btnLogin">Mit MythosAI anmelden</button>
  <div id="lstat"></div>
</div>
<div id="app" class="screen" style="display:none">
  <header>
    <div class="brand"><img src="icon.png" alt=""><span class="serif">Mythos Code</span></div>
    <button class="chip" id="btnFolder" title="Arbeitsordner wählen">📁 <span id="folder">Ordner wählen</span></button>
    <span class="sp"></span>
    <span class="chip mono" id="timer">⏱ 0s</span>
    <span class="chip" id="usage">Usage …</span>
    <button class="chip" id="btnOut">Abmelden</button>
  </header>
  <div id="log"><div class="col" id="col">
    <div id="empty">
      <img src="icon.png" alt="">
      <h2 class="serif grad">Was bauen wir heute?</h2>
      <p>Wähle oben einen Ordner und beschreibe deine Aufgabe.</p>
      <div class="sugg">
        <button class="chip" data-s="Erkläre mir die Struktur dieses Projekts.">Projekt erklären</button>
        <button class="chip" data-s="Finde und behebe Fehler in diesem Projekt.">Fehler finden</button>
        <button class="chip" data-s="Erstelle eine kleine Webseite mit index.html, style.css und script.js.">Webseite bauen</button>
      </div>
    </div>
  </div></div>
  <footer>
    <div class="composer">
      <button class="round" id="btnUp" title="Dateien hochladen">📎</button>
      <textarea id="inp" rows="1" placeholder="Was soll Mythos bauen?  (Enter = senden, Shift+Enter = neue Zeile)"></textarea>
      <button class="round" id="btnSend" title="Senden">↑</button>
    </div>
    <div class="under">
      <label class="switch"><input type="checkbox" id="auto"> Vollzugriff auf alle Dateien (nicht nachfragen)</label>
      <span class="sp" style="flex:1"></span><span id="files"></span>
    </div>
  </footer>
</div>
<script src="renderer.js"></script></body></html>`;

const appRenderer = () => `const CFG = ${JSON.stringify({ site: "__SITE__", fn: FN_BASE })};
CFG.site = location.protocol === "file:" ? CFG.site : CFG.site;
const $ = (id) => document.getElementById(id);
let cfg = {}, history = [], attach = [], busy = false;
const fmt = (ms) => { const s = Math.floor(ms / 1000); return (s >= 60 ? Math.floor(s / 60) + "m " : "") + (s % 60) + "s"; };
const post = (fn, body, h) => fetch(CFG.fn + "/" + fn, { method: "POST", headers: Object.assign({ "content-type": "application/json" }, h || {}), body: JSON.stringify(body) });
const el = (tag, cls, text) => { const d = document.createElement(tag); if (cls) d.className = cls; if (text != null) d.textContent = text; return d; };
// Text mit \`\`\`-Codeblöcken sicher (nur textContent) darstellen.
function renderText(box, text) {
  text.split(/\`\`\`[\\w+-]*\\n?/).forEach((part, i) => { if (!part) return; box.appendChild(i % 2 ? el("pre", "", part.replace(/\\n$/, "")) : document.createTextNode(part)); });
}
function add(cls, text) {
  const e = $("empty"); if (e) e.remove();
  let d;
  if (cls === "a") { d = el("div", "m a"); const img = el("img"); img.src = "icon.png"; const b = el("div", "body"); renderText(b, text); d.append(img, b); }
  else if (cls === "u") { d = el("div", "m u glass"); renderText(d, text); }
  else d = el("div", "t " + cls, text);
  $("col").appendChild(d); $("log").scrollTop = 1e9; return d;
}
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
  $("usage").textContent = j.limit == null ? "Usage " + j.used + " · ∞ Pro" : "Usage " + j.used + " / " + j.limit; } catch (e) {} }
async function send() {
  const q = $("inp").value.trim(); if (!q || busy) return; $("inp").value = ""; grow(); busy = true; $("btnSend").disabled = true;
  let content = q; if (attach.length) content += "\\n\\nHochgeladene Dateien:\\n" + attach.map((f) => "### " + f.name + "\\n" + f.content).join("\\n\\n");
  add("u", q + (attach.length ? "\\n📎 " + attach.map((f) => f.name).join(", ") : "")); attach = []; renderFiles(); history.push({ role: "user", content });
  const t0 = Date.now(); const live = add("live", "⏳ Mythos arbeitet… 0s"); $("timer").classList.add("busy");
  const tick = setInterval(() => { const s = fmt(Date.now() - t0); $("timer").textContent = "arbeitet… " + s; live.textContent = "⏳ Mythos arbeitet… " + s; $("col").appendChild(live); }, 250);
  try {
    for (let i = 0; i < 40; i++) {
      const out = await call(history); history.push({ role: "assistant", content: out });
      const m = out.match(/<tool>([\\s\\S]*?)<\\/tool>/); const text = out.replace(/<tool>[\\s\\S]*?<\\/tool>/g, "").trim();
      if (text) add("a", text); if (!m) break;
      let t; try { t = JSON.parse(m[1]); } catch (e) { history.push({ role: "user", content: "Tool-JSON ungültig." }); continue; }
      add("tool", "⚙ " + t.name + " " + (t.cmd || t.path || ""));
      let res; if ((t.name === "run" || t.name === "write") && !$("auto").checked && !confirm("Mythos möchte ausführen:\\n" + t.name + " " + (t.cmd || t.path))) res = "Vom Nutzer abgelehnt.";
      else res = await window.mythos.tool(t, cfg.folder);
      history.push({ role: "user", content: "Werkzeug-Ergebnis:\\n" + res });
    }
  } catch (e) { add("a", "⚠ " + e.message); }
  clearInterval(tick); const took = fmt(Date.now() - t0); $("timer").textContent = "⏱ " + took; $("timer").classList.remove("busy");
  live.textContent = "✓ Mythos hat " + took + " gearbeitet"; live.className = "t done"; $("col").appendChild(live); $("log").scrollTop = 1e9;
  busy = false; $("btnSend").disabled = false; usage();
}
function setStatus(text, code) {
  const s = $("lstat"); s.textContent = text;
  if (code) { s.appendChild(document.createElement("br")); s.appendChild(el("span", "code mono glass", code)); }
}
async function login() {
  $("btnLogin").disabled = true; setStatus("Browser öffnet sich …");
  let s; try { s = await (await post("cli-auth", { action: "start", client: "app" })).json(); } catch (e) { s = null; }
  if (!s || !s.code) { setStatus("Anmeldung gerade nicht möglich – bitte nochmal versuchen."); $("btnLogin").disabled = false; return; }
  window.mythos.open(CFG.site + "/cli-auth?code=" + s.code);
  setStatus("Klicke im Browser auf „Authentifizieren“. Dein Code:", s.code);
  const iv = setInterval(async () => {
    let p; try { p = await (await post("cli-auth", { action: "poll", code: s.code, poll_secret: s.poll_secret })).json(); } catch (e) { return; }
    if (p.status === "ok") { clearInterval(iv); cfg.key = p.api_key; cfg.name = p.name; await window.mythos.setCfg(cfg); show(); }
    if (p.status === "expired") { clearInterval(iv); setStatus("Abgelaufen – bitte nochmal versuchen."); $("btnLogin").disabled = false; }
  }, 2000);
}
const base = (p) => p.split(/[\\\\/]/).filter(Boolean).pop() || p;
function show() {
  $("login").style.display = "none"; $("app").style.display = "flex";
  $("folder").textContent = cfg.folder ? base(cfg.folder) : "Ordner wählen"; $("btnFolder").title = cfg.folder || "Arbeitsordner wählen";
  usage(); $("inp").focus();
}
function renderFiles() {
  const f = $("files"); f.textContent = "";
  attach.forEach((a, i) => { const c = el("button", "chip", "📎 " + a.name + "  ✕"); c.title = "Entfernen"; c.onclick = () => { attach.splice(i, 1); renderFiles(); }; f.appendChild(c); });
}
function grow() { const t = $("inp"); t.style.height = "auto"; t.style.height = Math.min(200, t.scrollHeight) + "px"; }
$("btnLogin").onclick = login; $("btnSend").onclick = send;
$("inp").oninput = grow;
$("inp").onkeydown = (e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } };
document.querySelectorAll("[data-s]").forEach((b) => b.onclick = () => { $("inp").value = b.dataset.s; grow(); $("inp").focus(); });
$("btnFolder").onclick = async () => { const f = await window.mythos.pickFolder(); if (f) { cfg.folder = f; await window.mythos.setCfg(cfg); show(); } };
$("btnUp").onclick = async () => { const fs = await window.mythos.pickFiles(); attach = attach.concat(fs); renderFiles(); };
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
  // Mythoscraft-Logo: App-Icon, Setup-EXE-Icon und Logo in der Oberfläche.
  const icon = await fetch(new URL("/icon.png", site)).then((r) => (r.ok ? r.arrayBuffer() : null)).catch(() => null);
  if (!icon) throw new Error("Logo (/icon.png) konnte nicht geladen werden.");
  root.file("icon.png", icon);
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
