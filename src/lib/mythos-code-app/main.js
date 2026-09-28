const { app, BrowserWindow, ipcMain, dialog, shell, Tray, Menu, Notification, nativeImage, powerSaveBlocker, session, desktopCapturer, safeStorage } = require("electron");
const fs = require("fs"); const path = require("path"); const { exec, execFile, spawn } = require("child_process");
const { McpManager } = require("./mcp.js");
// Sprachmodus: mehrere Threads für die lokale Spracherkennung, falls keine Grafikkarte (WebGPU) nutzbar ist.
app.commandLine.appendSwitch("enable-features", "SharedArrayBuffer");

// @@SHARED_TOOLS@@

const CFG = () => path.join(app.getPath("userData"), "mythos.json");
const canEncrypt = () => { try { return safeStorage.isEncryptionAvailable(); } catch { return false; } };
function load() {
  let c; try { c = JSON.parse(fs.readFileSync(CFG(), "utf8")); } catch { return {}; }
  if (c.keyEnc) { try { c.key = safeStorage.decryptString(Buffer.from(c.keyEnc, "base64")); } catch { /* auf anderem PC verschlüsselt */ } delete c.keyEnc; }
  return c;
}
function save(c) {
  const out = Object.assign({}, c);
  if (out.key && canEncrypt()) { out.keyEnc = safeStorage.encryptString(out.key).toString("base64"); delete out.key; }
  const tmp = CFG() + ".tmp"; fs.writeFileSync(tmp, JSON.stringify(out, null, 2)); fs.renameSync(tmp, CFG());
}
/** Nur Projekte, denen der Nutzer vertraut, dürfen eigene Hooks und MCP-Server starten. */
const samePath = (a, b) => path.resolve(a).toLowerCase() === path.resolve(b).toLowerCase();
const isTrusted = (cwd) => !!cwd && (load().trusted || []).some((t) => samePath(t, cwd));
// Unerwartete Fehler protokollieren statt die App abstürzen zu lassen.
const logError = (e) => { try { fs.appendFileSync(path.join(app.getPath("userData"), "error.log"), new Date().toISOString() + " " + (e && e.stack || e) + "\n"); } catch { /* egal */ } };
process.on("uncaughtException", logError);
process.on("unhandledRejection", logError);
let w = null, tray = null, working = false, quitting = false, blocker = null;
const showWin = () => { if (w) { w.show(); w.focus(); } };
function win() {
  w = new BrowserWindow({ width: 1200, height: 820, minWidth: 720, minHeight: 520, backgroundColor: "#0a0a0a", title: "Mythos Code",
    icon: path.join(__dirname, "icon.png"),
    autoHideMenuBar: true, webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, sandbox: true, nodeIntegration: false, webSecurity: true, backgroundThrottling: false } });
  w.webContents.setWindowOpenHandler(({ url }) => { if (safeUrl(url)) shell.openExternal(url); return { action: "deny" }; });
  w.webContents.on("will-navigate", (e, url) => { if (!url.startsWith("file:")) { e.preventDefault(); if (safeUrl(url)) shell.openExternal(url); } });
  w.loadFile("index.html");
  // AFK: Schließen, während Mythos arbeitet, versteckt das Fenster nur – die Arbeit läuft im Tray weiter.
  w.on("close", (e) => { if (working && !quitting) { e.preventDefault(); w.hide(); toTray(); } });
}
function toTray() {
  if (!tray) {
    tray = new Tray(nativeImage.createFromPath(path.join(__dirname, "icon.png")).resize({ width: 16, height: 16 }));
    tray.setToolTip("Mythos Code");
    tray.setContextMenu(Menu.buildFromTemplate([{ label: "Öffnen", click: showWin }, { label: "Beenden", click: () => { quitting = true; app.quit(); } }]));
    tray.on("click", showWin);
  }
  if (Notification.isSupported()) new Notification({ title: "Mythos Code", body: "Mythos arbeitet im Hintergrund weiter. Klick auf das Symbol unten rechts zum Öffnen." }).show();
}
const single = app.requestSingleInstanceLock();
if (!single) app.quit(); else app.on("second-instance", showWin);
app.whenReady().then(() => {
  const allowed = new Set(["media", "display-capture", "notifications", "clipboard-read", "clipboard-sanitized-write", "fullscreen"]);
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(allowed.has(perm) && wc === (w && w.webContents)));
  session.defaultSession.setPermissionCheckHandler((wc, perm) => allowed.has(perm));
  // Sprachmodus „Bildschirm teilen“: nur auf Knopfdruck im Fenster, dann den Hauptbildschirm freigeben.
  session.defaultSession.setDisplayMediaRequestHandler((_req, cb) => {
    desktopCapturer.getSources({ types: ["screen"] }).then((src) => cb(src.length ? { video: src[0] } : {})).catch(() => cb({}));
  });
  win();
});
app.on("before-quit", () => { quitting = true; });
app.on("window-all-closed", () => app.quit());
// AFK: Solange Mythos arbeitet, geht der PC nicht in den Energiesparmodus.
ipcMain.handle("working", (_e, on) => {
  working = !!on;
  if (working && blocker == null) blocker = powerSaveBlocker.start("prevent-app-suspension");
  if (!working && blocker != null) { powerSaveBlocker.stop(blocker); blocker = null; }
  return true;
});
ipcMain.handle("notify", (_e, title, body) => {
  if ((w && w.isVisible() && w.isFocused()) || !Notification.isSupported()) return false;
  const n = new Notification({ title, body }); n.on("click", showWin); n.show(); return true;
});
ipcMain.handle("cfg:get", () => load());
ipcMain.handle("cfg:set", (_e, c) => { save(c || {}); return true; });
const safeUrl = (u) => /^(https?:\/\/|mailto:)/i.test(String(u || ""));
ipcMain.handle("open", (_e, url) => (safeUrl(url) ? shell.openExternal(url) : false));
ipcMain.handle("pickFolder", async () => { const r = await dialog.showOpenDialog({ properties: ["openDirectory"] }); return r.canceled ? null : r.filePaths[0]; });
ipcMain.handle("pickFiles", async () => {
  const r = await dialog.showOpenDialog({ properties: ["openFile", "multiSelections"] });
  if (r.canceled) return [];
  const IMG = { png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp" };
  return r.filePaths.map((p) => {
    const mt = IMG[path.extname(p).slice(1).toLowerCase()];
    if (mt) { try { return { name: path.basename(p), image: { media_type: mt, data: fs.readFileSync(p).toString("base64") } }; } catch {} }
    let c = ""; try { c = fs.readFileSync(p, "utf8").slice(0, 60000); } catch { c = "(Binärdatei)"; }
    return { name: path.basename(p), path: p, content: c };
  });
});
// Gespeicherte Chats: ein JSON pro Chat im Benutzerordner.
const CHATS = () => path.join(app.getPath("userData"), "chats");
const chatFile = (id) => path.join(CHATS(), String(id || "").replace(/[^a-zA-Z0-9_-]/g, "") + ".json");
ipcMain.handle("chats:list", () => {
  let names = []; try { names = fs.readdirSync(CHATS()).filter((f) => f.endsWith(".json")); } catch { return []; }
  return names.map((f) => { try { const c = JSON.parse(fs.readFileSync(path.join(CHATS(), f), "utf8")); return { id: c.id, title: c.title, folder: c.folder, updated: c.updated }; } catch { return null; } })
    .filter(Boolean).sort((a, b) => b.updated - a.updated);
});
ipcMain.handle("chats:get", (_e, id) => { try { return JSON.parse(fs.readFileSync(chatFile(id), "utf8")); } catch { return null; } });
ipcMain.handle("chats:save", (_e, c) => { fs.mkdirSync(CHATS(), { recursive: true }); fs.writeFileSync(chatFile(c.id), JSON.stringify(c)); return true; });
ipcMain.handle("chats:delete", (_e, id) => { try { fs.unlinkSync(chatFile(id)); } catch {} return true; });
// Chats durchsuchen: Titel und Inhalt aller gespeicherten Chats.
ipcMain.handle("chats:search", (_e, q) => {
  const needle = String(q || "").toLowerCase().trim();
  if (!needle) return [];
  let names = []; try { names = fs.readdirSync(CHATS()).filter((f) => f.endsWith(".json")); } catch { return []; }
  const hits = [];
  for (const f of names) {
    let c; try { c = JSON.parse(fs.readFileSync(path.join(CHATS(), f), "utf8")); } catch { continue; }
    let snippet = (c.title || "").toLowerCase().includes(needle) ? c.title : "";
    if (!snippet) for (const m of c.history || []) {
      const t = String(m.content || ""), k = t.toLowerCase().indexOf(needle);
      if (k >= 0) { snippet = (k > 30 ? "…" : "") + t.slice(Math.max(0, k - 30), k + needle.length + 50).replace(/\s+/g, " "); break; }
    }
    if (snippet) hits.push({ id: c.id, title: c.title, folder: c.folder, updated: c.updated, snippet });
  }
  return hits.sort((a, b) => b.updated - a.updated).slice(0, 50);
});

// ---------- Werkzeuge ----------
const send = (ch, payload) => { if (w && !w.isDestroyed()) w.webContents.send(ch, payload); };
const mcp = new McpManager({ onChange: () => send("mcp-changed", mcp.status()) });
app.on("will-quit", () => { mcp.stopAll(); killAll(); });

// System-Prompt: Werkzeuge + verbundene MCP-Werkzeuge + Gedächtnis + Ziel.
function mcpPrompt() {
  const tools = mcp.status().filter((s) => s.status === "connected").flatMap((s) => s.tools.map((t) => ({ server: s.name, ...t })));
  if (!tools.length) return "";
  const args = (t) => (t.inputSchema && t.inputSchema.properties
    ? " · args: " + JSON.stringify(Object.fromEntries(Object.entries(t.inputSchema.properties).map(([k, v]) => [k, (v && v.type) || "any"]))).slice(0, 200) : "");
  return '\n\nMCP-Werkzeuge (externe Server) – Aufruf: <tool>{"name":"mcp","server":"...","tool":"...","args":{...}}</tool>\n' +
    tools.slice(0, 60).map((t) => "- " + t.server + " / " + t.name + ": " + (t.description || "").replace(/\s+/g, " ").slice(0, 160) + args(t)).join("\n");
}
ipcMain.handle("prompt:system", (_e, folder, goal) => TOOL_PROMPT + mcpPrompt() + memoryPrompt(folder ? readMemory(folder) : "") + goalPrompt(goal));
ipcMain.handle("prompts", () => ({ nudge: GOAL_NUDGE, summary: SUMMARY_PROMPT, review: REVIEW_PROMPT, memoryFile: MEMORY_FILE, models: MODELS }));

// Laufende Prozesse je Kennung – mehrere Aufgaben (und das Terminal) laufen parallel.
const children = new Map();
function killTree(p) { try { if (process.platform === "win32") exec("taskkill /pid " + p.pid + " /T /F"); else p.kill(); } catch {} }
function killAll() { for (const p of children.values()) killTree(p); }
ipcMain.handle("abort", (_e, id) => { const p = children.get(id); if (p) killTree(p); return true; });

/** Befehl ausführen, Ausgabe live ans Fenster ({ id, chunk } auf `channel`). Ergebnis: { text, code }. */
function runLive(cmd, cwd, id, channel, timeoutMs = 600000, env) {
  return new Promise((res) => {
    let out = "";
    const p = spawn(cmd, { cwd: cwd || undefined, shell: true, windowsHide: true, env: { ...process.env, ...(env || {}) } });
    children.set(id, p);
    const on = (decode) => (d) => { const s = decode(d); out += s; if (out.length > 200000) out = out.slice(-100000); send(channel, { id, chunk: s }); };
    p.stdout.on("data", on(outputDecoder())); p.stderr.on("data", on(outputDecoder()));
    const to = setTimeout(() => { out += "\n[Nach " + Math.round(timeoutMs / 60000) + " Minuten abgebrochen]"; killTree(p); }, timeoutMs);
    p.on("error", (e) => { out += "\nFEHLER: " + e.message; });
    p.on("close", (code) => { clearTimeout(to); if (children.get(id) === p) children.delete(id); res({ text: out, code: code == null ? 1 : code }); });
  });
}

// ---------- Hooks: <projekt>/.mythos/hooks.json und hooks.json im App-Datenordner ----------
// Format wie bei Claude Code: { "PreToolUse": [{ "matcher": "edit|write", "command": "..." }], "PostToolUse": [...], "Stop": [...] }
// Exit-Code 2 bei PreToolUse blockiert das Werkzeug (Ausgabe geht als Begründung an Mythos).
const readJsonFile = (f, fallback) => { try { return JSON.parse(fs.readFileSync(f, "utf8")); } catch { return fallback; } };
function loadHooks(cwd) {
  const all = {};
  const sources = [readJsonFile(path.join(app.getPath("userData"), "hooks.json"), {}), cwd && isTrusted(cwd) ? readJsonFile(path.join(cwd, ".mythos", "hooks.json"), {}) : {}];
  for (const src of sources) for (const [ev, list] of Object.entries(src.hooks || src)) if (Array.isArray(list)) (all[ev] = all[ev] || []).push(...list);
  return all;
}
function hookMatches(h, toolName) {
  if (!h || !h.command || h.disabled) return false;
  if (!h.matcher || !toolName) return true;
  try { return new RegExp("^(" + h.matcher + ")$", "i").test(toolName); } catch { return false; }
}
function runHooks(event, payload, cwd) {
  const list = (loadHooks(cwd)[event] || []).filter((h) => hookMatches(h, payload.tool_name));
  return Promise.all(list.map((h) => new Promise((res) => {
    const p = spawn(h.command, { cwd: cwd || undefined, shell: true, windowsHide: true, env: { ...process.env, MYTHOS_PROJECT_DIR: cwd || "", MYTHOS_HOOK_EVENT: event } });
    let out = "";
    const d1 = outputDecoder(), d2 = outputDecoder();
    const t = setTimeout(() => killTree(p), (h.timeout || 60) * 1000);
    p.stdout.on("data", (d) => (out += d1(d))); p.stderr.on("data", (d) => (out += d2(d)));
    p.on("close", (code) => { clearTimeout(t); res({ event, command: h.command, code: code == null ? 1 : code, out: out.trim().slice(-4000) }); });
    p.on("error", (e) => { clearTimeout(t); res({ event, command: h.command, code: 1, out: e.message }); });
    p.stdin.on("error", () => {});
    p.stdin.end(JSON.stringify({ hook_event_name: event, cwd, ...payload }));
  })));
}
ipcMain.handle("hooks:run", (_e, event, payload, cwd) => runHooks(event, payload, cwd));
ipcMain.handle("hooks:list", (_e, cwd) => loadHooks(cwd));

// Ein Werkzeug ausführen (mit Hooks). Liefert { result, diff?, code?, hooks }.
ipcMain.handle("tool", async (_e, t, cwd, runId) => {
  const root = cwd || process.cwd();
  const toolName = t.name === "mcp" ? "mcp__" + t.server + "__" + t.tool : t.name;
  const pre = await runHooks("PreToolUse", { tool_name: toolName, tool_input: t }, cwd);
  const blocked = pre.filter((h) => h.code === 2);
  if (blocked.length) return { result: "Durch Hook blockiert: " + (blocked.map((h) => h.out).join("\n") || "ohne Begründung"), hooks: pre };
  let r;
  if (t.name === "run") {
    const x = await runLive(t.cmd, root, runId, "tool-output");
    r = { result: (x.text + (x.code ? "\nExit: " + x.code : "")).slice(-8000) || "(keine Ausgabe)", code: x.code };
  } else if (isWebTool(t)) r = await webTool(t);
  else if (t.name === "mcp") { const x = await mcp.call(t.server, t.tool, t.args || {}); r = { result: String(x.output).slice(0, 20000) }; }
  else r = fileTool(t, root);
  const post = await runHooks("PostToolUse", { tool_name: toolName, tool_input: t, tool_output: r.result.slice(0, 4000) }, cwd);
  const notes = post.filter((h) => h.out || h.code).map((h) => "[Hook „" + h.command + "“ → Exit " + h.code + "]\n" + h.out);
  if (notes.length) r.result += "\n\nHook-Ausgabe:\n" + notes.join("\n");
  return { ...r, hooks: pre.concat(post) };
});

// ---------- Automatisch testen ----------
ipcMain.handle("tests:detect", (_e, cwd) => {
  if (!cwd) return null;
  const has = (f) => fs.existsSync(path.join(cwd, f));
  const pkg = readJsonFile(path.join(cwd, "package.json"), null);
  const t = pkg && pkg.scripts && pkg.scripts.test;
  if (t && !/no test specified/i.test(t)) return "npm test";
  if (has("Cargo.toml")) return "cargo test";
  if (has("go.mod")) return "go test ./...";
  if (has("pytest.ini") || has("conftest.py") || (has("tests") && fs.readdirSync(path.join(cwd, "tests")).some((f) => /^test_.*\.py$|_test\.py$/.test(f)))) return "python -m pytest -q";
  if (has("pom.xml")) return "mvn -q test";
  return null;
});
// CI=1: Test-Runner wie vitest/jest laufen einmal durch statt im Watch-Modus.
ipcMain.handle("tests:run", async (_e, cwd, cmd, runId) => {
  const x = await runLive(cmd, cwd, runId, "tool-output", 600000, { CI: "1", FORCE_COLOR: "0" });
  return { text: x.text.slice(-8000), code: x.code };
});

// ---------- MCP-Server: <projekt>/.mcp.json und mcp.json im App-Datenordner ----------
ipcMain.handle("mcp:configure", (_e, cwd) => {
  const servers = (f) => readJsonFile(f, {}).mcpServers || {};
  mcp.configure(servers(path.join(app.getPath("userData"), "mcp.json")), { cwd, servers: cwd && isTrusted(cwd) ? servers(path.join(cwd, ".mcp.json")) : {} });
  return mcp.status();
});
ipcMain.handle("mcp:status", () => mcp.status());
ipcMain.handle("mcp:restart", (_e, name) => mcp.restart(name));
// Konfigurationsdatei öffnen (bei Bedarf mit Vorlage anlegen).
ipcMain.handle("config:open", (_e, which, cwd) => {
  const file = which === "mcp-global" ? path.join(app.getPath("userData"), "mcp.json")
    : which === "mcp-project" ? path.join(cwd, ".mcp.json")
    : which === "hooks-global" ? path.join(app.getPath("userData"), "hooks.json")
    : path.join(cwd, ".mythos", "hooks.json");
  if (!fs.existsSync(file)) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const tpl = /mcp/.test(which)
      ? { mcpServers: { beispiel: { command: "npx", args: ["-y", "@modelcontextprotocol/server-filesystem", "."], disabled: true } } }
      : { PreToolUse: [], PostToolUse: [{ matcher: "edit|write", command: "echo Datei geändert", disabled: true }], Stop: [] };
    fs.writeFileSync(file, JSON.stringify(tpl, null, 2));
  }
  shell.openPath(file);
  return file;
});

// ---------- Live-Vorschau: eigenes Fenster, lädt bei Dateiänderungen neu ----------
let preview = null, watcher = null, reloadTimer = null;
ipcMain.handle("preview:open", (_e, target, cwd) => {
  let url = String(target || "").trim();
  if (!url) {
    const idx = cwd && ["index.html", "public/index.html", "dist/index.html", "build/index.html"].map((f) => path.join(cwd, f)).find((f) => fs.existsSync(f));
    if (!idx) return { error: "Keine index.html gefunden – gib eine Adresse an, z. B. /vorschau http://localhost:5173" };
    url = idx;
  } else if (!/^https?:\/\//i.test(url)) {
    const f = path.resolve(cwd || ".", url);
    if (!fs.existsSync(f)) return { error: "Nicht gefunden: " + url };
    url = f;
  }
  if (!preview || preview.isDestroyed()) {
    preview = new BrowserWindow({ width: 1024, height: 768, title: "Mythos Code – Vorschau", icon: path.join(__dirname, "icon.png"), autoHideMenuBar: true,
      webPreferences: { contextIsolation: true, sandbox: true } });
    preview.webContents.setWindowOpenHandler(({ url: u }) => { shell.openExternal(u); return { action: "deny" }; });
    preview.on("closed", () => { preview = null; if (watcher) { watcher.close(); watcher = null; } });
  }
  if (/^https?:/i.test(url)) preview.loadURL(url); else preview.loadFile(url);
  preview.show(); preview.focus();
  if (watcher) { watcher.close(); watcher = null; }
  if (cwd) {
    try {
      watcher = fs.watch(cwd, { recursive: true }, (_ev, f) => {
        if (!f || /(^|[\\/])(node_modules|\.git)([\\/]|$)/.test(f)) return;
        clearTimeout(reloadTimer);
        reloadTimer = setTimeout(() => { if (preview && !preview.isDestroyed()) preview.webContents.reloadIgnoringCache(); }, 400);
      });
    } catch {}
  }
  return { ok: true, url };
});

// ---------- Dateibaum & Editor ----------
const inside = (cwd, rel) => { const f = path.resolve(cwd, rel || "."); const r = path.relative(path.resolve(cwd), f); return r.startsWith("..") || path.isAbsolute(r) ? null : f; };
ipcMain.handle("files:list", (_e, cwd, rel) => {
  const dir = cwd && inside(cwd, rel);
  if (!dir) return [];
  try {
    return fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.name !== ".git")
      .map((e) => ({ name: e.name, dir: e.isDirectory(), rel: path.relative(cwd, path.join(dir, e.name)).split(path.sep).join("/") }))
      .sort((a, b) => (b.dir - a.dir) || a.name.localeCompare(b.name, "de"));
  } catch { return []; }
});
ipcMain.handle("files:read", (_e, cwd, rel) => {
  const f = cwd && inside(cwd, rel);
  if (!f) return { error: "Ungültiger Pfad" };
  try {
    const st = fs.statSync(f);
    if (st.size > 2e6) return { error: "Datei zu groß (" + (st.size / 1e6).toFixed(1) + " MB)" };
    const buf = fs.readFileSync(f);
    if (buf.includes(0)) return { error: "Binärdatei – kann hier nicht angezeigt werden" };
    return { text: buf.toString("utf8") };
  } catch (e) { return { error: e.message }; }
});
ipcMain.handle("files:save", (_e, cwd, rel, text) => {
  const f = cwd && inside(cwd, rel);
  if (!f) return { error: "Ungültiger Pfad" };
  try { const before = readText(f) ?? ""; fs.writeFileSync(f, text); return { ok: true, diff: lineDiff(before, text) }; } catch (e) { return { error: e.message }; }
});
ipcMain.handle("files:reveal", (_e, cwd, rel) => { const f = cwd && inside(cwd, rel); if (f) shell.showItemInFolder(f); return !!f; });

// ---------- Eingebautes Terminal ----------
ipcMain.handle("term:run", (_e, cwd, cmd) => runLive(cmd, cwd, "term", "term-output", 30 * 60000));
ipcMain.handle("term:kill", () => { const p = children.get("term"); if (p) killTree(p); return true; });
ipcMain.handle("term:cd", (_e, cwd, dir) => {
  const d = path.resolve(cwd || require("os").homedir(), dir || require("os").homedir());
  try { return fs.statSync(d).isDirectory() ? d : null; } catch { return null; }
});

// ---------- Projekt-Gedächtnis (MYTHOS.md) ----------
ipcMain.handle("memory:get", (_e, cwd) => (cwd ? readMemory(cwd) : ""));
ipcMain.handle("memory:add", (_e, cwd, text) => { if (!cwd) return false; addMemory(cwd, text); return true; });

// ---------- Handy-Benachrichtigung ----------
ipcMain.handle("push", (_e, url, title, message) => pushNotify(url, title, message));

// ---------- Git ----------
const git = (args, cwd) => new Promise((res) => execFile("git", args, { cwd, timeout: 60000, maxBuffer: 1e7, windowsHide: true },
  (err, so, se) => res({ ok: !err, out: (so || "") + (se || "") })));
ipcMain.handle("git:info", async (_e, cwd) => {
  if (!cwd) return null;
  const b = await git(["rev-parse", "--abbrev-ref", "HEAD"], cwd);
  if (!b.ok) return null;
  const s = await git(["status", "--porcelain"], cwd);
  return { branch: b.out.trim(), changed: s.out.split("\n").filter(Boolean).length, status: s.out };
});
ipcMain.handle("git:diff", async (_e, cwd) => {
  const st = await git(["status", "--short"], cwd);
  let d = await git(["diff", "HEAD"], cwd);
  if (!d.ok) d = await git(["diff"], cwd);
  return st.out + "\n" + d.out.slice(0, 20000);
});
ipcMain.handle("git:commit", async (_e, cwd, msg) => {
  const a = await git(["add", "-A"], cwd);
  if (!a.ok) return a;
  return git(["commit", "-m", msg], cwd);
});
ipcMain.handle("git:push", (_e, cwd) => git(["push"], cwd));

// ---------- Eigene Befehle (.mythos/commands/*.md im Projekt + commands/ im App-Datenordner) ----------
const commandDirs = (cwd) => [path.join(app.getPath("userData"), "commands"), cwd ? path.join(cwd, ".mythos", "commands") : null].filter(Boolean);
ipcMain.handle("commands:list", (_e, cwd) => loadCommands(commandDirs(cwd)).map((c) => ({ name: c.name, desc: c.desc, body: c.body })));
ipcMain.handle("commands:create", (_e, cwd, name) => {
  const safe = String(name || "mein-befehl").toLowerCase().replace(/[^a-z0-9äöüß_-]/g, "-").replace(/^-+|-+$/g, "") || "mein-befehl";
  const dir = cwd ? path.join(cwd, ".mythos", "commands") : path.join(app.getPath("userData"), "commands");
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, safe + ".md");
  if (!fs.existsSync(file)) fs.writeFileSync(file, COMMAND_TEMPLATE);
  shell.openPath(file);
  return { name: "/" + safe, file };
});

// ---------- Projektstatistik ----------
ipcMain.handle("project:stats", (_e, cwd) => (cwd ? projectStats(cwd) : "Kein Projektordner gewählt."));

// ---------- Chat als Markdown exportieren ----------
ipcMain.handle("chats:export", async (_e, title, messages) => {
  const name = String(title || "Mythos-Chat").replace(/[\/:*?"<>|]+/g, " ").trim().slice(0, 60) || "Mythos-Chat";
  const r = await dialog.showSaveDialog(w, { defaultPath: path.join(app.getPath("documents"), name + ".md"), filters: [{ name: "Markdown", extensions: ["md"] }] });
  if (r.canceled || !r.filePath) return null;
  fs.writeFileSync(r.filePath, chatToMarkdown(title, messages));
  return r.filePath;
});

// ---------- Pull Request: Branch pushen, per GitHub-CLI (gh) oder im Browser öffnen ----------
ipcMain.handle("git:pr", async (_e, cwd, newBranch) => {
  const head = await git(["rev-parse", "--abbrev-ref", "HEAD"], cwd);
  if (!head.ok) return { ok: false, out: "Der Projektordner ist kein Git-Repository." };
  const dirty = await git(["status", "--porcelain"], cwd);
  if (dirty.out.trim()) return { ok: false, out: "Es gibt noch nicht committete Änderungen – erst /commit, dann /pr." };
  const origin = await git(["remote", "get-url", "origin"], cwd);
  if (!origin.ok || !origin.out.trim()) return { ok: false, out: "Kein Remote „origin“ eingerichtet – verbinde das Projekt zuerst mit GitHub (git remote add origin <url>)." };
  let branch = head.out.trim();
  // Von main/master aus einen eigenen Branch anlegen, sonst gäbe es nichts zu vergleichen.
  if (/^(main|master)$/.test(branch)) {
    branch = newBranch || "mythos/" + new Date().toISOString().slice(0, 16).replace(/[-:T]/g, "");
    const co = await git(["checkout", "-b", branch], cwd);
    if (!co.ok) return { ok: false, out: co.out };
  }
  const push = await git(["push", "-u", "origin", branch], cwd);
  if (!push.ok) return { ok: false, out: push.out };
  const gh = await new Promise((res) => execFile("gh", ["pr", "create", "--fill"], { cwd, timeout: 120000, windowsHide: true },
    (err, so, se) => res({ ok: !err, out: ((so || "") + (se || "")).trim(), missing: !!err && err.code === "ENOENT" })));
  if (gh.ok) return { ok: true, branch, out: gh.out, url: (gh.out.match(/https:\/\/\S+/) || [])[0] };
  const remote = await git(["remote", "get-url", "origin"], cwd);
  const m = remote.out.trim().match(/github\.com[:/](.+?)(?:\.git)?$/);
  if (!m) return { ok: false, out: gh.out || "Kein GitHub-Remote (origin) gefunden." };
  const url = "https://github.com/" + m[1] + "/compare/" + branch.split("/").map(encodeURIComponent).join("/") + "?expand=1";
  shell.openExternal(url);
  return { ok: true, branch, url, out: gh.missing ? "GitHub-CLI (gh) ist nicht installiert – die PR-Seite ist im Browser geöffnet." : (gh.out ? gh.out + "\n" : "") + "PR-Seite im Browser geöffnet." };
});

// ---------- @-Erwähnungen: Dateien im Projekt finden ----------
ipcMain.handle("files:find", (_e, cwd, query) => {
  if (!cwd) return [];
  const q = String(query || "").toLowerCase().split("\\").join("/");
  const hits = []; let seen = 0;
  const walk = (dir) => {
    let entries; try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      if (hits.length >= 40 || seen > 8000) return;
      const full = path.join(dir, e.name);
      if (e.isDirectory()) { if (!SKIP_DIRS.has(e.name)) walk(full); continue; }
      seen++;
      const rel = path.relative(cwd, full).split(path.sep).join("/");
      if (!q || rel.toLowerCase().includes(q)) hits.push(rel);
    }
  };
  walk(cwd);
  // Treffer im Dateinamen zuerst, dann kürzere Pfade.
  const score = (r) => (path.basename(r).toLowerCase().startsWith(q) ? 0 : path.basename(r).toLowerCase().includes(q) ? 1 : 2) * 1000 + r.length;
  return hits.sort((a, b) => score(a) - score(b)).slice(0, 12);
});

// ---------- Sicherheit ----------
ipcMain.handle("safety:check", (_e, cmd) => dangerCheck(cmd));
/** Hat das Projekt eigene Hooks / MCP-Server, die Befehle ausführen würden? */
ipcMain.handle("project:config", (_e, cwd) => {
  if (!cwd) return { hooks: 0, mcp: 0, trusted: false };
  const h = readJsonFile(path.join(cwd, ".mythos", "hooks.json"), {}), m = readJsonFile(path.join(cwd, ".mcp.json"), {});
  const hooks = Object.values(h.hooks || h).filter(Array.isArray).reduce((a, l) => a + l.filter((x) => x && x.command && !x.disabled).length, 0);
  const mcpN = Object.values(m.mcpServers || {}).filter((x) => x && !x.disabled).length;
  return { hooks, mcp: mcpN, trusted: isTrusted(cwd) };
});
ipcMain.handle("safety:status", () => ({ encrypted: canEncrypt(), sandbox: true }));
