const { app, BrowserWindow, ipcMain, dialog, shell, Tray, Menu, Notification, nativeImage, powerSaveBlocker } = require("electron");
const fs = require("fs"); const path = require("path"); const { exec, execFile, spawn } = require("child_process");

// @@SHARED_TOOLS@@

const CFG = () => path.join(app.getPath("userData"), "mythos.json");
const load = () => { try { return JSON.parse(fs.readFileSync(CFG(), "utf8")); } catch { return {}; } };
let w = null, tray = null, working = false, quitting = false, blocker = null;
const showWin = () => { if (w) { w.show(); w.focus(); } };
function win() {
  w = new BrowserWindow({ width: 1200, height: 820, minWidth: 720, minHeight: 520, backgroundColor: "#0a0a0a", title: "Mythos Code",
    icon: path.join(__dirname, "icon.png"),
    autoHideMenuBar: true, webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, backgroundThrottling: false } });
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
app.whenReady().then(win);
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
ipcMain.handle("cfg:set", (_e, c) => { fs.writeFileSync(CFG(), JSON.stringify(c, null, 2)); return true; });
ipcMain.handle("open", (_e, url) => shell.openExternal(url));
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
ipcMain.handle("prompt:system", (_e, folder, goal) => TOOL_PROMPT + memoryPrompt(folder ? readMemory(folder) : "") + goalPrompt(goal));
ipcMain.handle("prompts", () => ({ nudge: GOAL_NUDGE, summary: SUMMARY_PROMPT, memoryFile: MEMORY_FILE }));
let child = null;
function killChild() {
  if (!child) return;
  try { if (process.platform === "win32") exec("taskkill /pid " + child.pid + " /T /F"); else child.kill(); } catch {}
}
// Stopp-Knopf: laufenden Befehl samt Unterprozessen beenden.
ipcMain.handle("abort", () => { killChild(); return true; });
// Befehl ausführen, Ausgabe live an das Fenster schicken.
function runLive(cmd, cwd) {
  return new Promise((res) => {
    let out = "";
    child = spawn(cmd, { cwd: cwd || undefined, shell: true, windowsHide: true });
    const on = (decode) => (d) => { const s = decode(d); out += s; if (out.length > 200000) out = out.slice(-100000); send("tool-output", s); };
    child.stdout.on("data", on(outputDecoder())); child.stderr.on("data", on(outputDecoder()));
    const to = setTimeout(() => { out += "\n[Nach 10 Minuten abgebrochen]"; killChild(); }, 600000);
    child.on("error", (e) => { out += "\nFEHLER: " + e.message; });
    child.on("close", (code) => { clearTimeout(to); child = null; res((out + (code ? "\nExit: " + code : "")).slice(-8000) || "(keine Ausgabe)"); });
  });
}
const send = (ch, payload) => { if (w && !w.isDestroyed()) w.webContents.send(ch, payload); };
// Liefert { result, diff? } – diff wird im Chat als Änderungsansicht gezeigt.
ipcMain.handle("tool", async (_e, t, cwd) => {
  if (t.name === "run") return { result: await runLive(t.cmd, cwd) };
  return fileTool(t, cwd || process.cwd());
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
