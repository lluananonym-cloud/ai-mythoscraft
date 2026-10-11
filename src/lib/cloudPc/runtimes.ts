// Mythos Cloud-PC: Programme ausführen und ins Internet schauen – kostenlos, ohne eigenen Server.
// JavaScript läuft in einem Web Worker (eigene kleine "node"-Umgebung), Python über Pyodide (WebAssembly)
// aus dem jsDelivr-CDN, Webseiten liest der Reader von r.jina.ai (frei, ohne Schlüssel).

export type Out = (text: string, kind?: "out" | "err" | "info") => void;
export type RunResult = { code: number; changed: Record<string, string> };

const NODE_TIMEOUT = 15_000;
const PY_TIMEOUT = 60_000;

// Läuft im Worker: CommonJS-require für Projektdateien, fs/path im Speicher, Timer werden mitgezählt,
// damit der Lauf erst endet, wenn nichts mehr aussteht.
const NODE_WORKER = String.raw`
self.onmessage = (e) => {
  const { files, entry, cwd, args } = e.data;
  const changed = {};
  const send = (kind, text) => self.postMessage({ type: "out", kind, text });
  const show = (x) => typeof x === "string" ? x : (() => { try { return x instanceof Error ? (x.stack || String(x)) : JSON.stringify(x, null, 2) ?? String(x); } catch { return String(x); } })();
  const fmt = (a) => a.map(show).join(" ") + "\n";
  const console = { log: (...a) => send("out", fmt(a)), info: (...a) => send("out", fmt(a)), debug: (...a) => send("out", fmt(a)),
    warn: (...a) => send("err", fmt(a)), error: (...a) => send("err", fmt(a)), table: (x) => send("out", fmt([x])) };
  const norm = (p, base) => { const raw = String(p).startsWith("/") ? String(p) : (base || cwd) + "/" + p; const o = []; for (const s of raw.split("/")) { if (!s || s === ".") continue; if (s === "..") o.pop(); else o.push(s); } return "/" + o.join("/"); };
  const dirname = (p) => norm(p).replace(/\/[^/]*$/, "") || "/";
  const path = { join: (...p) => { const n = norm(p.join("/"), "/"); return String(p[0] || "").startsWith("/") ? n : n.slice(1) || "."; }, resolve: (...p) => norm(p.join("/")),
    dirname, basename: (p, ext) => { const b = String(p).split("/").pop() || ""; return ext && b.endsWith(ext) ? b.slice(0, -ext.length) : b; },
    extname: (p) => { const m = String(p).match(/\.[^./]*$/); return m ? m[0] : ""; }, sep: "/" };
  const has = (p) => Object.prototype.hasOwnProperty.call(files, p);
  const fs = {
    readFileSync: (p) => { const n = norm(p); if (!has(n)) throw new Error("ENOENT: Datei nicht gefunden: " + n); return files[n]; },
    writeFileSync: (p, d) => { const n = norm(p); files[n] = String(d); changed[n] = files[n]; },
    appendFileSync: (p, d) => { const n = norm(p); files[n] = (has(n) ? files[n] : "") + String(d); changed[n] = files[n]; },
    existsSync: (p) => { const n = norm(p); return has(n) || Object.keys(files).some((f) => f.startsWith(n + "/")); },
    readdirSync: (p) => { const n = norm(p); const pre = n === "/" ? "/" : n + "/"; return [...new Set(Object.keys(files).filter((f) => f.startsWith(pre)).map((f) => f.slice(pre.length).split("/")[0]))]; },
    mkdirSync: () => {}, unlinkSync: (p) => { delete files[norm(p)]; },
  };
  fs.promises = Object.fromEntries(Object.entries(fs).map(([k, f]) => [k.replace(/Sync$/, ""), async (...a) => f(...a)]));
  let pending = 0;
  const timers = new Set();
  const _st = self.setTimeout, _ct = self.clearTimeout, _si = self.setInterval, _ci = self.clearInterval;
  const setTimeout = (fn, ms, ...a) => { pending++; const id = _st(() => { timers.delete(id); pending--; try { fn(...a); } catch (err) { send("err", show(err) + "\n"); } }, ms); timers.add(id); return id; };
  const clearTimeout = (id) => { if (timers.delete(id)) pending--; _ct(id); };
  const setInterval = (fn, ms, ...a) => { pending++; const id = _si(() => { try { fn(...a); } catch (err) { send("err", show(err) + "\n"); } }, ms); timers.add(id); return id; };
  const clearInterval = (id) => { if (timers.delete(id)) pending--; _ci(id); };
  const _fetch = self.fetch.bind(self);
  const fetch = (...a) => { pending++; return _fetch(...a).finally(() => { pending--; }); };
  let exitCode = 0;
  const process = { argv: ["node", entry, ...(args || [])], env: { HOME: "/", USER: "mythos" }, platform: "mythos", version: "v20-mythos",
    cwd: () => cwd, exit: (c) => { exitCode = c || 0; throw { __exit: true }; }, stdout: { write: (s) => send("out", String(s)) }, stderr: { write: (s) => send("err", String(s)) } };
  const cache = {};
  const run = (p) => {
    if (cache[p]) return cache[p].exports;
    const module = { exports: {} };
    cache[p] = module;
    if (p.endsWith(".json")) { module.exports = JSON.parse(files[p]); return module.exports; }
    const fn = new Function("require", "module", "exports", "console", "__filename", "__dirname", "process", "setTimeout", "clearTimeout", "setInterval", "clearInterval", "fetch", files[p].replace(/^#!.*/, ""));
    fn(makeRequire(dirname(p)), module, module.exports, console, p, dirname(p), process, setTimeout, clearTimeout, setInterval, clearInterval, fetch);
    return module.exports;
  };
  const builtin = { fs, path, "fs/promises": fs.promises, process, util: { inspect: show, format: (...a) => a.map(show).join(" ") },
    events: { EventEmitter: class { constructor() { this.h = {}; } on(n, f) { (this.h[n] ||= []).push(f); return this; } emit(n, ...a) { (this.h[n] || []).forEach((f) => f(...a)); return true; } } } };
  const makeRequire = (dir) => (name) => {
    const key = String(name).replace(/^node:/, "");
    if (builtin[key]) return builtin[key];
    if (/^[./]/.test(key)) {
      const base = norm(key, dir);
      for (const c of [base, base + ".js", base + ".json", base + "/index.js"]) if (has(c)) return run(c);
      throw new Error("Modul nicht gefunden: " + name);
    }
    throw new Error("Paket '" + name + "' gibt es auf dem Cloud-PC nicht (kein npm). Nutze eigene Dateien oder ein CDN im Browser.");
  };
  self.addEventListener("unhandledrejection", (ev) => { ev.preventDefault(); exitCode = 1; send("err", show(ev.reason) + "\n"); });
  const finish = () => { if (pending > 0) return void _st(finish, 30); self.postMessage({ type: "done", code: exitCode, changed }); };
  try { run(entry); } catch (err) { if (!err || !err.__exit) { exitCode = 1; send("err", show(err) + "\n"); } }
  _st(finish, 0);
};
`;

const PY_WORKER = String.raw`
importScripts("https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js");
let py = null;
let sendOut = () => {};
self.onmessage = async (e) => {
  const { files, entry, cwd, code } = e.data;
  const send = (kind, text) => self.postMessage({ type: "out", kind, text });
  sendOut = send;
  try {
    if (!py) {
      send("info", "Python wird geladen (beim ersten Mal ein paar Sekunden) …\n");
      py = await loadPyodide({ stdout: (t) => sendOut("out", t + "\n"), stderr: (t) => sendOut("err", t + "\n") });
    }
    const ROOT = "/home/pc";
    py.runPython("import os, shutil\nif os.path.exists('" + ROOT + "'): shutil.rmtree('" + ROOT + "')\nos.makedirs('" + ROOT + "')");
    for (const [p, c] of Object.entries(files)) {
      const full = ROOT + p;
      py.FS.mkdirTree(full.replace(/\/[^/]*$/, ""));
      py.FS.writeFile(full, c);
    }
    py.FS.mkdirTree(ROOT + cwd);
    py.runPython("import os, sys\nos.chdir('" + ROOT + cwd + "')\nsys.path.insert(0, '" + ROOT + cwd + "')");
    const src = code ?? files[entry];
    try { await py.loadPackagesFromImports(src, { messageCallback: () => {} }); } catch { /* Paket nicht verfügbar: Fehler kommt beim Import */ }
    let codeOut = 0;
    try {
      py.globals.set("__mythos_src", src);
      py.globals.set("__mythos_file", entry ? ROOT + entry : "<befehl>");
      await py.runPythonAsync("exec(compile(__mythos_src, __mythos_file, 'exec'), {'__name__': '__main__', '__file__': __mythos_file})");
    } catch (err) {
      const msg = String(err && err.message || err);
      const m = msg.match(/SystemExit: (\d+)/);
      if (m) codeOut = Number(m[1]);
      else if (!/SystemExit/.test(msg)) { codeOut = 1; send("err", msg.split("\n").filter((l) => !/_pyodide|pyodide\.asm/.test(l)).join("\n").replace(new RegExp(ROOT, "g"), "") + "\n"); }
    }
    const changed = {};
    const walk = (dir) => {
      for (const name of py.FS.readdir(dir)) {
        if (name === "." || name === ".." || name === "__pycache__") continue;
        const full = dir + "/" + name;
        const st = py.FS.stat(full);
        if (py.FS.isDir(st.mode)) walk(full);
        else {
          const rel = full.slice(ROOT.length);
          if (st.size > 400000) continue;
          const text = py.FS.readFile(full, { encoding: "utf8" });
          if (files[rel] !== text) changed[rel] = text;
        }
      }
    };
    walk(ROOT);
    self.postMessage({ type: "done", code: codeOut, changed });
  } catch (err) {
    send("err", "Python konnte nicht starten: " + (err && err.message || err) + "\n");
    self.postMessage({ type: "done", code: 1, changed: {} });
  }
};
`;

function workerUrl(src: string) {
  return URL.createObjectURL(new Blob([src], { type: "text/javascript" }));
}

let pyWorker: Worker | null = null;
let busyWorker: Worker | null = null;

/** Laufendes Programm abbrechen (Stopp-Knopf, Strg+C). */
export function killRunning() {
  if (!busyWorker) return false;
  busyWorker.terminate();
  if (busyWorker === pyWorker) pyWorker = null;
  busyWorker = null;
  return true;
}

function runInWorker(w: Worker, msg: unknown, out: Out, timeout: number, persistent: boolean): Promise<RunResult> {
  busyWorker = w;
  return new Promise((resolve) => {
    let done = false;
    const end = (r: RunResult) => {
      if (done) return;
      done = true;
      clearTimeout(t);
      w.onmessage = null;
      busyWorker = null;
      if (!persistent) w.terminate();
      resolve(r);
    };
    const t = setTimeout(() => {
      out(`\nAbgebrochen: Das Programm lief länger als ${timeout / 1000} Sekunden.\n`, "err");
      w.terminate();
      if (w === pyWorker) pyWorker = null;
      end({ code: 124, changed: {} });
    }, timeout);
    w.onmessage = (e: MessageEvent) => {
      const d = e.data;
      if (d.type === "out") out(d.text, d.kind);
      else if (d.type === "done") end({ code: d.code, changed: d.changed || {} });
    };
    w.onerror = (e) => { out(`${e.message}\n`, "err"); end({ code: 1, changed: {} }); };
    const check = setInterval(() => { if (busyWorker !== w) { clearInterval(check); if (!done) { out("\nAbgebrochen.\n", "err"); end({ code: 130, changed: {} }); } } }, 100);
    w.postMessage(msg);
  });
}

export function runNode(files: Record<string, string>, entry: string, cwd: string, args: string[], out: Out) {
  const url = workerUrl(NODE_WORKER);
  const w = new Worker(url);
  URL.revokeObjectURL(url);
  return runInWorker(w, { files, entry, cwd, args }, out, NODE_TIMEOUT, false);
}

export function runPython(files: Record<string, string>, entry: string | null, cwd: string, out: Out, code?: string) {
  if (!pyWorker) {
    const url = workerUrl(PY_WORKER);
    pyWorker = new Worker(url);
    URL.revokeObjectURL(url);
  }
  return runInWorker(pyWorker, { files, entry, cwd, code }, out, PY_TIMEOUT, true);
}

// ---------- Internet ----------
const READER = "https://r.jina.ai/";

/** Webseite als lesbaren Text (Markdown) holen. */
export async function readWeb(url: string, signal?: AbortSignal): Promise<string> {
  const u = /^https?:\/\//i.test(url) ? url : `https://${url}`;
  const r = await fetch(READER + u, { signal, headers: { Accept: "text/plain", "X-Return-Format": "markdown" } });
  if (!r.ok) throw new Error(r.status === 429 ? "Der Lesedienst ist gerade ausgelastet, bitte kurz warten." : `Seite nicht lesbar (Fehler ${r.status})`);
  return (await r.text()).trim();
}

export type SearchHit = { title: string; url: string; snippet: string };

/** Websuche: DuckDuckGo über den Reader, sonst Wikipedia (beides frei und ohne Schlüssel). */
export async function searchWeb(query: string, signal?: AbortSignal): Promise<SearchHit[]> {
  try {
    const md = await readWeb(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`, signal);
    const hits: SearchHit[] = [];
    const re = /\[([^\]]{3,200})\]\((https?:\/\/[^)\s]+)\)/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(md)) && hits.length < 8) {
      let url = m[2];
      const real = url.match(/[?&]uddg=([^&]+)/);
      if (real) url = decodeURIComponent(real[1]);
      if (/duckduckgo\.com/.test(url) || hits.some((h) => h.url === url)) continue;
      const after = md.slice(re.lastIndex, re.lastIndex + 400).replace(/\[[^\]]*\]\([^)]*\)/g, "").replace(/\s+/g, " ").trim();
      hits.push({ title: m[1].replace(/[*_`]/g, "").trim(), url, snippet: after.slice(0, 200) });
    }
    if (hits.length) return hits;
  } catch (e) {
    if ((e as Error).name === "AbortError") throw e;
  }
  const lang = /[äöüß]|\b(der|die|das|und|wie|was|wetter)\b/i.test(query) ? "de" : "en";
  const r = await fetch(`https://${lang}.wikipedia.org/w/api.php?action=query&list=search&format=json&origin=*&srlimit=6&srsearch=${encodeURIComponent(query)}`, { signal });
  if (!r.ok) throw new Error("Suche nicht erreichbar");
  const j = await r.json();
  return (j?.query?.search || []).map((s: { title: string; snippet: string }) => ({
    title: s.title,
    url: `https://${lang}.wikipedia.org/wiki/${encodeURIComponent(s.title.replace(/ /g, "_"))}`,
    snippet: s.snippet.replace(/<[^>]+>/g, "").replace(/&quot;/g, '"').replace(/&amp;/g, "&"),
  }));
}
