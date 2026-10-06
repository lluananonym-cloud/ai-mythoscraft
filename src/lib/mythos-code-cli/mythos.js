#!/usr/bin/env node
// Mythos Code CLI – KI-Coding-Agent im Terminal (by Mythoscraft)
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import readline from "node:readline";
import { exec, spawn, execFileSync } from "node:child_process";

// @@SHARED_TOOLS@@

const API = process.env.MYTHOS_API_URL || "__API__";
const CFG = path.join(os.homedir(), ".mythos-code.json");
const ROOT = process.cwd();
const C = { c: "\x1b[36m", g: "\x1b[32m", y: "\x1b[33m", r: "\x1b[31m", m: "\x1b[35m", d: "\x1b[2m", b: "\x1b[1m", x: "\x1b[0m" };
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
const ask = (q) => new Promise((r) => rl.question(q, r));
const load = () => { try { return JSON.parse(fs.readFileSync(CFG, "utf8")); } catch { return {}; } };
// Konfiguration (mit API-Schlüssel) nur für den eigenen Benutzer lesbar.
const save = (c) => { fs.writeFileSync(CFG, JSON.stringify(c, null, 2), { mode: 0o600 }); try { fs.chmodSync(CFG, 0o600); } catch { /* Windows: Benutzerordner ist ohnehin privat */ } };
let cfg = load();
let autoYes = process.argv.includes("--yes");
let goal = "";

const SYSTEM = () => "Du bist Mythos Code, ein autonomer Coding-Agent im Terminal des Nutzers (OS: " + process.platform + ", Projektordner: " + ROOT + ").\n" +
  TOOL_PROMPT + memoryPrompt(readMemory(ROOT)) + goalPrompt(goal);

const SITE = process.env.MYTHOS_SITE_URL || "__SITE__";
const FN = process.env.MYTHOS_FN_URL || API.replace(/\/v1-messages$/, "");
const authPost = async (body) => (await fetch(FN + "/cli-auth", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) })).json();

function openBrowser(url) {
  const cmd = process.platform === "win32" ? `start "" "${url}"` : process.platform === "darwin" ? `open "${url}"` : `xdg-open "${url}"`;
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
  console.log(C.c + "\n  Anmeldung mit MythosAI" + C.x);
  console.log("  Dein Browser öffnet sich. Klicke dort auf " + C.b + "Authentifizieren" + C.x + ".");
  console.log(C.d + "  Code: " + s.code + "  ·  Falls sich nichts öffnet: " + url + C.x + "\n");
  openBrowser(url);
  const until = Date.now() + 15 * 60e3;
  process.stdout.write(C.d + "  Warte auf Bestätigung…" + C.x);
  while (Date.now() < until) {
    await new Promise((r) => setTimeout(r, 2000));
    let p; try { p = await authPost({ action: "poll", code: s.code, poll_secret: s.poll_secret }); } catch { continue; }
    if (p.status === "ok") { cfg.key = p.api_key; cfg.name = p.name; save(cfg); clearLine(); console.log(C.g + "  ✓ Angemeldet als " + p.name + C.x + "\n"); return; }
    if (p.status === "expired") break;
  }
  clearLine(); console.log(C.r + "  Anmeldung abgelaufen – starte nochmal mit: mythos login" + C.x); process.exit(1);
}

const fmt = (ms) => { const s = Math.floor(ms / 1000); return (s >= 60 ? Math.floor(s / 60) + "m " : "") + (s % 60) + "s"; };

// Live-Timer: läuft während der ganzen Aufgabe, pausiert für Ausgaben und Rückfragen.
let T0 = 0, tick = null, activity = "";
const clearLine = () => process.stdout.write("\r\x1b[K");
// Zeigt live, was Mythos gerade tut (z. B. „✍ schreibt src/main.js · 12 KB“) – auf Terminalbreite gekürzt.
const tickLine = () => {
  const s = "⏳ Mythos arbeitet… " + fmt(Date.now() - T0) + (activity ? " · " + activity : "") + "  (Strg+C = stoppen)";
  const w = (process.stdout.columns || 100) - 2;
  return s.length > w ? s.slice(0, w - 1) + "…" : s;
};
const startTick = () => { if (tick || !working) return; tick = setInterval(() => process.stdout.write("\r\x1b[K" + C.d + tickLine() + C.x), 250); };
const stopTick = () => { if (tick) { clearInterval(tick); tick = null; } clearLine(); };
let adN = 0, adTier = null, adAdminDay = "";
async function maybeAd() {
  try {
    if (adTier == null) { const j = await (await fetch(API.replace(/\/v1-messages.*$/, "") + "/cli-auth", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ action: "usage", api_key: cfg.key }) })).json(); adTier = j.admin ? "admin" : (j.tier || "free"); }
    let due = false; const today = new Date().toDateString();
    if (adTier === "admin") { due = adAdminDay !== today; adAdminDay = today; }
    else { const every = adTier === "free" ? 4 : adTier === "light" ? 10 : 0; due = every > 0 && (++adN % every === 0); }
    if (due) {
      const fnBase = API.replace(/\/v1-messages.*$/, "");
      let ads = []; try { ads = (await (await fetch(fnBase + "/ad-review", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ action: "list" }) })).json()).ads || []; } catch {}
      const pool = [{ house: true, weight: 3 }].concat(ads); let r = Math.random() * pool.reduce((t, a) => t + Math.max(1, a.weight), 0); let ad = pool[0];
      for (const a of pool) { r -= Math.max(1, a.weight); if (r <= 0) { ad = a; break; } }
      if (!ad.house) { console.log("\x1b[2m📣 Werbung\x1b[0m  \x1b[1m" + ad.product + "\x1b[0m – " + ad.ad_text + ": " + ad.link + "\n\x1b[2m   Buche deine eigene Werbung: __SITE__/werbung\x1b[0m\n"); return; }
    }
    if (due) console.log("\x1b[2m📣 Werbung\x1b[0m  \x1b[1m@at\x1b[0m – dein Feed voller Vibes: https://at-feed-vibes.lovable.app/\n\x1b[2m   Buche deine eigene Werbung: __SITE__/werbung\x1b[0m\n");
  } catch {}
}
const say = (s) => { const was = !!tick; stopTick(); console.log(s); if (was) startTick(); };

// ---------- Stoppen mit Strg+C ----------
let working = false, stopped = false, ctl = null, child = null;
function killChild() {
  if (!child) return;
  try { if (process.platform === "win32") exec("taskkill /pid " + child.pid + " /T /F"); else child.kill("SIGTERM"); } catch {}
}
rl.on("SIGINT", () => {
  if (!working) { console.log(); rl.close(); process.exit(0); }
  if (stopped) return;
  stopped = true; killChild(); if (ctl) ctl.abort();
  say(C.r + "■ Wird gestoppt…" + C.x);
});

// ---------- Markdown im Terminal ----------
function mdLine(line, st) {
  if (/^\s*```/.test(line)) { st.code = !st.code; return C.d + (st.code ? "┌─ " + line.trim().slice(3) : "└─") + C.x; }
  if (st.code) return C.c + "│ " + line + C.x;
  // Markdown-Tabellen: Trennzeile als Linie, Zellen als ausgerichtete Spalten.
  if (/^\s*\|.*\|\s*$/.test(line)) {
    if (/^\s*\|[\s:|-]+\|\s*$/.test(line)) return C.d + "  " + "─".repeat(52) + C.x;
    const cells = line.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim().replace(/\*\*(.+?)\*\*/g, "$1"));
    return "  " + cells.map((c, i) => (i === 0 ? c.padEnd(22) : c.padStart(9))).join(" ");
  }
  const h = line.match(/^(#{1,6})\s+(.*)/);
  if (h) return C.b + C.m + h[2] + C.x;
  return line
    .replace(/^(\s*)[-*]\s+/, "$1• ")
    .replace(/\*\*(.+?)\*\*/g, C.b + "$1" + C.x)
    .replace(/`([^`]+)`/g, C.c + "$1" + C.x)
    .replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g, "$1 " + C.d + "($2)" + C.x);
}

// Antwort live mitlesen: fertige Zeilen sofort formatiert ausgeben, <tool>-Blöcke ausblenden.
function streamPrinter() {
  let printed = 0, buf = "", started = false;
  const st = { code: false };
  const line = (l) => {
    stopTick();
    if (!started) { if (!l.trim()) { startTick(); return; } started = true; process.stdout.write(C.b + "Mythos: " + C.x); }
    console.log(mdLine(l, st));
    startTick();
  };
  return {
    push(full) {
      let vis = full;
      const k = vis.indexOf("<tool>");
      if (k >= 0) vis = vis.slice(0, k);
      else for (let n = 5; n > 0; n--) if (vis.endsWith("<tool>".slice(0, n))) { vis = vis.slice(0, -n); break; }
      if (vis.length <= printed) return;
      buf += vis.slice(printed); printed = vis.length;
      let i; while ((i = buf.indexOf("\n")) !== -1) { line(buf.slice(0, i)); buf = buf.slice(i + 1); }
    },
    end() { if (buf.trim()) line(buf); buf = ""; if (started) { stopTick(); console.log(); startTick(); } },
  };
}

// Sichtbare Gedanken (wie bei base44): laufen gedimmt Wort für Wort, bevor die eigentliche Antwort beginnt.
function thinkPrinter() {
  let printed = 0, started = false, ended = false;
  return {
    push(full) {
      if (ended) return;
      if (!started) { stopTick(); process.stdout.write(C.d + "💭 "); started = true; }
      if (full.length <= printed) return;
      process.stdout.write(full.slice(printed)); printed = full.length;
    },
    end() { if (ended) return; ended = true; if (started) process.stdout.write(C.x + "\n"); startTick(); },
  };
}

// ---------- Modell ----------
// Verlauf kürzen, damit lange Aufgaben nicht an zu großem Kontext scheitern. level 0 = mild, 2 = stark.
const shorten = (s, max) => s.length <= max ? s : s.slice(0, Math.floor(max * 0.7)) + "\n…[gekürzt]…\n" + s.slice(s.length - Math.floor(max * 0.3));
const withImages = (m, content) => m.images && m.images.length
  ? { role: m.role, content: [{ type: "text", text: content }].concat(m.images.map((i) => ({ type: "image", source: { type: "base64", media_type: i.media_type, data: i.data } }))) }
  : { role: m.role, content };
function compact(history, level) {
  const keep = [6, 8, 4][level], recentMax = [20000, 3000, 1500][level], oldMax = [1500, 800, 400][level];
  const cut = Math.max(0, history.length - keep);
  const older = level === 0 ? history.slice(0, cut) : history.slice(0, Math.min(1, cut));
  return older.map((m) => ({ role: m.role, content: shorten(m.content, oldMax) }))
    .concat(history.slice(cut).map((m) => withImages(m, shorten(m.content, recentMax))));
}

let tokens = { in: 0, out: 0 };
async function once(messages, onText, onThink) {
  const c = ctl = new AbortController(); const to = setTimeout(() => c.abort(), 170000);
  const system = SYSTEM();
  tokens.in += estimateTokens(system, messages);
  try {
    const r = await fetch(API, {
      method: "POST", signal: c.signal,
      headers: { "content-type": "application/json", "x-api-key": cfg.key, "anthropic-version": "2023-06-01" },
      body: JSON.stringify({ model: cfg.model || "mythos-code", max_tokens: 8000, system, messages, stream: true }),
    });
    if (!r.ok) { const j = await r.json().catch(() => ({})); const e = new Error(j?.error?.message || ("HTTP " + r.status)); e.status = r.status; throw e; }
    const reader = r.body.getReader(); const dec = new TextDecoder(); let buf = "", out = "", think = "";
    while (true) {
      const { done, value } = await reader.read(); if (done) break;
      buf += dec.decode(value, { stream: true });
      let i; while ((i = buf.indexOf("\n")) !== -1) {
        const line = buf.slice(0, i).trim(); buf = buf.slice(i + 1);
        if (!line.startsWith("data:")) continue;
        let p; try { p = JSON.parse(line.slice(5)); } catch { continue; }
        if (p.type === "error") { const e = new Error(p.error?.message || "Überlastet"); e.status = 529; throw e; }
        // Sichtbare Gedanken: kommen als eigener Block VOR der Antwort.
        if (p.delta?.type === "thinking_delta" && p.delta.thinking) { think += p.delta.thinking; if (onThink) onThink(think); }
        if (p.delta?.text) { out += p.delta.text; if (onText) onText(out); }
      }
    }
    if (!out.trim()) throw new Error("Leere Antwort");
    tokens.out += Math.ceil(out.length / 4);
    return out;
  } finally { clearTimeout(to); }
}

async function call(history, onText, onThink) {
  for (let a = 0; a < 6; a++) {
    if (stopped) throw new Error("Gestoppt");
    try { return await once(compact(history, Math.min(2, Math.floor(a / 2))), onText, onThink); }
    catch (e) {
      if (stopped) throw e;
      if (e.status === 401) throw new Error("Anmeldung ungültig – bitte neu anmelden: mythos login");
      if (/Daily limit/i.test(e.message)) throw new Error("Tageslimit erreicht – mit Pro unbegrenzt.");
      // Konfigurationsproblem (Guthaben leer, kein Ausweich-Schlüssel) statt normaler Überlastung -> nicht sinnlos wiederholen.
      if (/nicht konfiguriert/i.test(e.message)) throw e;
      await new Promise((r) => setTimeout(r, Math.min(8000, 1500 * (a + 1))));
    }
  }
  throw new Error("Mythos ist gerade nicht erreichbar – bitte gleich nochmal versuchen.");
}

// ---------- Werkzeuge ----------
function diffText(d) {
  const shown = d.lines.slice(0, 80).map(([op, l]) =>
    op === "+" ? C.g + "  + " + l + C.x : op === "-" ? C.r + "  - " + l + C.x : op === "@" ? C.d + "  " + l + C.x : C.d + "    " + l + C.x);
  if (d.lines.length > 80) shown.push(C.d + "  … (" + (d.lines.length - 80) + " weitere Zeilen)" + C.x);
  return shown.join("\n");
}

// Befehl ausführen und die Ausgabe live im Terminal zeigen.
function runLive(cmd) {
  stopTick();
  return new Promise((res) => {
    let out = "";
    child = spawn(cmd, { shell: true, cwd: ROOT, env: process.env });
    const on = (decode) => (d) => { const s = decode(d); out += s; if (out.length > 200000) out = out.slice(-100000); process.stdout.write(C.d + s + C.x); };
    child.stdout.on("data", on(outputDecoder())); child.stderr.on("data", on(outputDecoder()));
    const to = setTimeout(() => { out += "\n[Nach 10 Minuten abgebrochen]"; killChild(); }, 600000);
    child.on("error", (e) => { out += "\nFEHLER: " + e.message; });
    child.on("close", (code) => {
      clearTimeout(to); child = null;
      if (out && !out.endsWith("\n")) process.stdout.write("\n");
      startTick();
      res((out + (code ? "\nExit: " + code : "")).slice(-8000) || "(keine Ausgabe)");
    });
  });
}

async function runTool(t) {
  if (t.name === "todo") {
    const items = normalizeTodos(t);
    if (!items.length) return 'FEHLER: "items" fehlt – sende die komplette Liste.';
    const done = items.filter((x) => x.done).length;
    say(C.c + C.b + "📋 Aufgabenliste " + done + "/" + items.length + C.x + "\n" + items.map((x) => (x.done ? C.g + "  ☑ " + C.d : "  ☐ ") + x.text + C.x).join("\n"));
    return todoSummary(items);
  }
  const label = t.name === "run" ? "run " + t.cmd
    : t.name === "search" ? "search /" + t.pattern + "/" + (t.glob ? " " + t.glob : "")
    : t.name === "write" ? "write " + t.path + " (" + String(t.content || "").length + " Zeichen)"
    : t.name === "websearch" ? "websearch " + t.query
    : t.name === "fetch" ? "fetch " + t.url
    : t.name + " " + (t.path || "");
  say(C.y + "⚙  " + label + C.x);
  // Sicherheitsnetz: gefährliche Befehle fragen immer nach (auch mit --yes) und werden bei /goal blockiert.
  const why = t.name === "run" ? dangerCheck(t.cmd) : null;
  if (why) {
    say(C.r + "🛡 Sicherheitsnetz: dieser Befehl " + why + "." + C.x);
    if (goal) return "BLOCKIERT vom Sicherheitsnetz: Der Befehl " + why + ". Der Nutzer ist nicht da und kann das nicht bestätigen – finde einen sichereren Weg ohne diesen Befehl.";
    stopTick();
    const a = (await ask(C.r + "   Wirklich ausführen? [j/N] " + C.x)).trim().toLowerCase();
    startTick();
    if (a !== "j" && a !== "y") return "Vom Nutzer abgelehnt (Sicherheitsnetz). Finde einen sichereren Weg.";
  }
  if ((t.name === "run" || t.name === "write" || t.name === "edit") && !autoYes && !why) {
    stopTick();
    const a = (await ask(C.d + "   Erlauben? [j/N/a=immer] " + C.x)).trim().toLowerCase();
    startTick();
    if (a === "a") autoYes = true; else if (a !== "j" && a !== "y") return "Vom Nutzer abgelehnt.";
  }
  if (t.name === "run") return runLive(t.cmd);
  if (isWebTool(t)) {
    const r = await webTool(t);
    say(C.d + "   " + r.result.split("\n").filter(Boolean).slice(0, 3).join(" · ").slice(0, 160) + C.x);
    return r.result;
  }
  const r = fileTool(t, ROOT);
  if (r.diff && r.diff.lines.length) say(diffText(r.diff));
  return r.result;
}

// ---------- Eine Aufgabe (auch /goal) ----------
async function turnInner(history) {
  const max = goal ? 4000 : 40;
  let nudges = 0;
  for (let i = 0; i < max && !stopped; i++) {
    const pr = streamPrinter();
    let think = null;
    activity = "denkt nach…";
    const out = await call(history,
      (full) => { if (think) think.end(); activity = streamActivity(full); pr.push(full); },
      (t) => { if (!think) think = thinkPrinter(); think.push(t); });
    if (think) think.end();
    pr.end(); activity = "";
    if (stopped) break;
    history.push({ role: "assistant", content: out });
    const m = out.match(/<tool>([\s\S]*?)<\/tool>/);
    if (!m) {
      if (!goal || /ZIEL ERREICHT/.test(out)) return goal ? "reached" : "done";
      if (++nudges > 40) { say(C.y + "🎯 Mythos kommt beim Ziel nicht weiter – schau es dir bitte an (/weiter macht weiter)." + C.x); return "stuck"; }
      history.push({ role: "user", content: GOAL_NUDGE });
      continue;
    }
    nudges = 0;
    let t; try { t = JSON.parse(m[1]); } catch { history.push({ role: "user", content: "Tool-JSON ungültig." }); continue; }
    const res = await runTool(t);
    history.push({ role: "user", content: "Werkzeug-Ergebnis:\n" + res });
  }
  if (!stopped) say(C.y + "Schrittlimit (" + max + ") erreicht – mit /weiter macht Mythos weiter." + C.x);
  return stopped ? "stopped" : "limit";
}

async function summarize(history) {
  history.push({ role: "user", content: SUMMARY_PROMPT });
  say(C.m + C.b + "📋 Zusammenfassung" + C.x);
  const pr = streamPrinter();
  let think = null;
  const out = await call(history,
    (full) => { if (think) think.end(); pr.push(full); },
    (t) => { if (!think) think = thinkPrinter(); think.push(t); });
  if (think) think.end();
  pr.end();
  history.push({ role: "assistant", content: out });
  return out;
}

async function turn(history, input, images) {
  if (input != null) history.push({ role: "user", content: input, images });
  T0 = Date.now(); working = true; stopped = false; startTick();
  const tok0 = tokens.in + tokens.out;
  const wasGoal = goal;
  let status = "error", summary = "";
  try {
    status = await turnInner(history);
    if (wasGoal && status !== "stopped" && !stopped) summary = await summarize(history);
  } catch (e) { if (!stopped) say(C.r + "⚠ " + e.message + C.x); }
  if (stopped) { status = "stopped"; if (history[history.length - 1]?.role === "user") history.push({ role: "assistant", content: "(Vom Nutzer gestoppt.)" }); }
  working = false; stopTick();
  const took = fmt(Date.now() - T0);
  console.log((stopped ? C.r + "■ Gestoppt nach " + took : C.g + "✓ Mythos hat " + took + " gearbeitet") + C.x +
    C.d + "  · ≈ " + fmtTokens(tokens.in + tokens.out - tok0) + " Tokens (Sitzung ≈ " + fmtTokens(tokens.in + tokens.out) + ")" + C.x + "\n");
  if (!stopped && status !== "error") await maybeAd();
  if (status === "reached") { console.log(C.g + "🎯 Ziel erreicht: " + wasGoal + C.x + "\n"); goal = ""; }
  // Handy-Benachrichtigung bei /goal oder längeren Aufgaben.
  if (cfg.notify && (wasGoal || Date.now() - T0 > 60000)) {
    const title = status === "reached" ? "🎯 Ziel erreicht" : status === "done" ? "✓ Mythos ist fertig" : status === "stopped" ? "■ Gestoppt" : "⚠ Mythos braucht dich";
    pushNotify(cfg.notify, "Mythos Code – " + path.basename(ROOT), title + " (" + took + ")" + (summary ? "\n\n" + summary.slice(0, 1500) : ""));
  }
}

// ---------- Git ----------
const git = (args) => {
  try { return { ok: true, out: execFileSync("git", args, { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], maxBuffer: 1e7 }) }; }
  catch (e) { return { ok: false, out: ((e.stdout || "") + (e.stderr || "")) || e.message }; }
};
const gitBranch = () => { const b = git(["rev-parse", "--abbrev-ref", "HEAD"]); return b.ok ? b.out.trim() : ""; };

async function commit(msg) {
  const st = git(["status", "--porcelain"]);
  if (!st.ok) return say(C.r + "Kein Git-Repository: " + st.out.trim() + C.x);
  if (!st.out.trim()) return say(C.d + "Nichts zu committen – alles sauber." + C.x);
  if (!msg) {
    const diff = git(["diff", "HEAD"]).out || git(["diff"]).out;
    process.stdout.write(C.d + "Mythos schreibt die Commit-Nachricht…" + C.x);
    stopped = false;
    try {
      msg = (await call([{ role: "user", content: "Schreibe eine kurze Git-Commit-Nachricht auf Deutsch für diese Änderungen: erste Zeile max. 72 Zeichen, optional Leerzeile + Stichpunkte. Antworte NUR mit der Nachricht, ohne Werkzeuge, ohne Codeblock.\n\n" + st.out + "\n" + diff.slice(0, 20000) }]))
        .replace(/<tool>[\s\S]*?<\/tool>/g, "").replace(/^```\w*\n?|```$/g, "").trim();
    } catch (e) { clearLine(); return say(C.r + "⚠ " + e.message + C.x); }
    clearLine();
  }
  console.log(C.b + "Commit-Nachricht:" + C.x + "\n" + C.c + msg + C.x);
  if (!autoYes && !/^[jy]/i.test((await ask(C.d + "Committen? [j/N] " + C.x)).trim())) return say("Abgebrochen.");
  git(["add", "-A"]);
  const c = git(["commit", "-m", msg]);
  say((c.ok ? C.g : C.r) + c.out.trim() + C.x);
}

// ---------- Bilder: Pfade zu Bilddateien in der Eingabe (z. B. per Drag & Drop ins Terminal) ----------
const IMG = { png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp" };
function extractImages(input) {
  const images = [];
  const text = input.replace(/"([^"]+\.(png|jpe?g|gif|webp))"|'([^']+\.(png|jpe?g|gif|webp))'|(\S+\.(png|jpe?g|gif|webp))/gi, (all, a, _1, b, _2, c) => {
    const p = path.resolve(ROOT, a || b || c);
    try {
      const st = fs.statSync(p);
      if (!st.isFile() || st.size > 5e6 || images.length >= 5) return all;
      images.push({ media_type: IMG[p.split(".").pop().toLowerCase()], data: fs.readFileSync(p).toString("base64") });
      return "[Bild: " + path.basename(p) + "]";
    } catch { return all; }
  });
  return { text, images };
}

const HELP = [
  ["/goal <ziel>", "Mythos arbeitet selbstständig, bis das Ziel erreicht ist (+ Zusammenfassung)"],
  ["/goal stop", "Ziel beenden"],
  ["/weiter", "Unterbrochene Arbeit fortsetzen"],
  ["/merken <text>", "Etwas ins Projekt-Gedächtnis (MYTHOS.md) schreiben"],
  ["/gedaechtnis", "Projekt-Gedächtnis anzeigen"],
  ["/git", "Git-Status anzeigen"],
  ["/commit [nachricht]", "Alles committen (ohne Nachricht schreibt Mythos sie)"],
  ["/push", "git push"],
  ["/handy <url>", "Handy-Benachrichtigung (ntfy.sh-Thema oder Discord-Webhook) · /handy test · /handy aus"],
  ["/vollzugriff an|aus", "Ohne Nachfragen arbeiten"],
  ["/modell [name]", "Modell anzeigen oder wechseln"],
  ["/tokens", "Geschätzten Token-Verbrauch dieser Sitzung anzeigen"],
  ["/clear", "Verlauf leeren"],
  ["/review [schwerpunkt]", "Code-Review der Git-Änderungen (ändert nichts)"],
  ["/pr [branch]", "Branch pushen und Pull Request erstellen"],
  ["/stats", "Projektstatistik: Dateien, Zeilen, Sprachen"],
  ["/export [datei]", "Chat als Markdown-Datei speichern"],
  ["/kompakt", "Langen Chat zusammenfassen und Tokens sparen"],
  ["@pfad/datei", "Datei in der Nachricht erwähnen – ihr Inhalt geht mit"],
  ["/befehle", "Eigene Befehle (.mythos/commands/*.md) anzeigen"],
  ["/exit", "Beenden"],
];

/** @pfad in der Eingabe → Dateien im Projekt, die es gibt (max. 8, je 60.000 Zeichen). */
function mentionedFiles(text) {
  const paths = [...new Set([...text.matchAll(/(?:^|\s)@([^\s@]+)/g)].map((m) => m[1].replace(/[.,;:!?)]+$/, "")))].slice(0, 8);
  const out = [];
  for (const p of paths) {
    const full = path.resolve(ROOT, p);
    if (path.relative(ROOT, full).startsWith("..")) continue;
    try { if (fs.statSync(full).isFile()) { const c = readText(full); if (c != null && !c.includes("\u0000")) out.push({ name: p, content: c.slice(0, 60000) }); } } catch { /* gibt es nicht */ }
  }
  return out;
}
const COMMAND_DIRS = [path.join(os.homedir(), ".mythos", "commands"), path.join(ROOT, ".mythos", "commands")];
const mdLines = (md) => { const st = { code: false }; return md.split("\n").map((l) => mdLine(l, st)).join("\n"); };
async function pullRequest(name) {
  const head = git(["rev-parse", "--abbrev-ref", "HEAD"]);
  if (!head.ok) return console.log(C.r + "Kein Git-Repository." + C.x + "\n");
  if (git(["status", "--porcelain"]).out.trim()) return console.log(C.y + "Es gibt noch nicht committete Änderungen – erst /commit, dann /pr." + C.x + "\n");
  if (!git(["remote", "get-url", "origin"]).out.trim()) return console.log(C.y + "Kein Remote „origin“ eingerichtet – verbinde das Projekt zuerst mit GitHub (git remote add origin <url>)." + C.x + "\n");
  let branch = head.out.trim();
  if (!autoYes && !/^[jy]/i.test((await ask(C.d + "Branch pushen und Pull Request erstellen? [j/N] " + C.x)).trim())) return console.log("Abgebrochen.\n");
  if (/^(main|master)$/.test(branch)) {
    branch = name ? name.replace(/\s+/g, "-") : "mythos/" + new Date().toISOString().slice(0, 16).replace(/[-:T]/g, "");
    const co = git(["checkout", "-b", branch]); if (!co.ok) return console.log(C.r + co.out + C.x);
  }
  const push = git(["push", "-u", "origin", branch]); if (!push.ok) return console.log(C.r + push.out.trim() + C.x + "\n");
  try { const out = execFileSync("gh", ["pr", "create", "--fill"], { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }); return console.log(C.g + "🔀 " + out.trim() + C.x + "\n"); }
  catch (e) {
    const m = git(["remote", "get-url", "origin"]).out.trim().match(/github\.com[:/](.+?)(?:\.git)?$/);
    if (!m) return console.log(C.r + ((e.stderr || "") + "" || "Kein GitHub-Remote gefunden.") + C.x + "\n");
    const url = "https://github.com/" + m[1] + "/compare/" + branch + "?expand=1";
    openBrowser(url);
    return console.log(C.g + "🔀 Branch „" + branch + "“ gepusht. PR-Seite im Browser: " + url + C.x + (e.code === "ENOENT" ? C.d + "\n   (Mit der GitHub-CLI „gh“ würde der PR direkt erstellt.)" + C.x : "") + "\n");
  }
}

async function command(q, history) {
  const cmd = q.split(/\s+/)[0].toLowerCase(), arg = q.slice(cmd.length).trim();
  if (cmd === "/hilfe" || cmd === "/help") return console.log(HELP.map(([c, d]) => "  " + C.c + c.padEnd(22) + C.x + d).join("\n") + "\n");
  if (cmd === "/clear") { history.length = 0; goal = ""; return console.log("Verlauf geleert.\n"); }
  if (cmd === "/goal" || cmd === "/ziel") {
    if (!arg) return console.log(goal ? "🎯 Aktuelles Ziel: " + goal : "So geht's: /goal <was Mythos erreichen soll>");
    if (/^(stop|aus|ende|beenden)$/i.test(arg)) { goal = ""; return console.log("🎯 Ziel beendet.\n"); }
    if (!autoYes) { autoYes = true; console.log(C.d + "Vollzugriff für /goal eingeschaltet – Mythos fragt nicht mehr nach." + C.x); }
    goal = arg;
    console.log(C.m + C.b + "🎯 Ziel angenommen: " + C.x + arg + C.d + "\n   Mythos plant jetzt und arbeitet selbstständig – der Fortschritt erscheint hier. Stoppen: Strg+C" + C.x);
    return turn(history, "Neues Ziel: " + arg + "\nArbeite jetzt komplett selbstständig daran, bis es erreicht und geprüft ist.");
  }
  if (cmd === "/weiter") {
    if (!history.length) return console.log("Hier gibt es noch nichts zum Weitermachen.");
    return turn(history, history[history.length - 1].role === "assistant" ? "Mach bitte genau dort weiter, wo du aufgehört hast." : null);
  }
  if (cmd === "/merken") { if (!arg) return console.log("So geht's: /merken <text>"); addMemory(ROOT, arg); return console.log(C.g + "✓ In " + MEMORY_FILE + " gemerkt." + C.x + "\n"); }
  if (cmd === "/gedaechtnis" || cmd === "/gedächtnis") return console.log(readMemory(ROOT) || "Noch kein Projekt-Gedächtnis. Mit /merken <text> anlegen.", "\n");
  if (cmd === "/git") { const s = git(["status", "--short", "--branch"]); return console.log((s.ok ? s.out : C.r + s.out + C.x).trim() + "\n"); }
  if (cmd === "/commit") return commit(arg);
  if (cmd === "/push") { const p = git(["push"]); return console.log((p.ok ? C.g : C.r) + p.out.trim() + C.x + "\n"); }
  if (cmd === "/handy") {
    if (/^(aus|off)$/i.test(arg)) { delete cfg.notify; save(cfg); return console.log("Handy-Benachrichtigung aus.\n"); }
    if (/^test$/i.test(arg)) return console.log(cfg.notify && await pushNotify(cfg.notify, "Mythos Code", "Test – Benachrichtigungen funktionieren ✓") ? C.g + "✓ Gesendet." + C.x : C.r + "Senden fehlgeschlagen – URL prüfen." + C.x);
    if (!/^https:\/\//i.test(arg)) return console.log("So geht's: App „ntfy“ aufs Handy, Thema abonnieren, dann:\n  /handy https://ntfy.sh/dein-geheimes-thema\nOder einen Discord-Webhook-Link angeben.\n");
    cfg.notify = arg; save(cfg);
    return console.log(C.g + "✓ Gespeichert. Bei /goal und Aufgaben über 1 Minute bekommst du eine Nachricht. Test: /handy test" + C.x + "\n");
  }
  if (cmd === "/modell" || cmd === "/model") {
    const cur = cfg.model || "mythos-code";
    if (!arg) return console.log(MODELS.map((m) => (m.id === cur ? C.g + "● " : "  ") + m.id.padEnd(15) + C.x + m.label + C.d + " – " + m.desc + C.x).join("\n") + "\n\nWechseln mit /modell <name>\n");
    const m = MODELS.find((x) => x.id === arg.toLowerCase() || x.label.toLowerCase() === arg.toLowerCase());
    if (!m) return console.log(C.r + "Unbekanntes Modell. Verfügbar: " + MODELS.map((x) => x.id).join(", ") + C.x + "\n");
    cfg.model = m.id; save(cfg);
    return console.log(C.g + "✓ Modell: " + m.label + C.x + "\n");
  }
  if (cmd === "/tokens") return console.log("≈ " + fmtTokens(tokens.in) + " Eingabe + " + fmtTokens(tokens.out) + " Ausgabe = " + fmtTokens(tokens.in + tokens.out) + " Tokens (geschätzt)\n");
  if (cmd === "/vollzugriff") { autoYes = !/^(aus|off)$/i.test(arg); return console.log(autoYes ? "Vollzugriff an.\n" : "Vollzugriff aus – Mythos fragt vor Befehlen nach.\n"); }
  if (cmd === "/review") {
    const st = git(["status", "--porcelain"]);
    if (!st.ok) return console.log(C.r + "Kein Git-Repository." + C.x + "\n");
    const diff = (git(["diff", "HEAD"]).out || git(["diff"]).out).trim();
    if (!diff && !st.out.trim()) return console.log("🔍 Keine Änderungen – nichts zu prüfen.\n");
    return turn(history, REVIEW_PROMPT + (arg ? "\n\nBesonders beachten: " + arg : "") + "\n\n" + st.out + "\n```diff\n" + diff.slice(0, 60000) + "\n```");
  }
  if (cmd === "/pr") return pullRequest(arg);
  if (cmd === "/kompakt") {
    if (history.length < 4) return console.log("🗜 Der Chat ist noch kurz – Komprimieren lohnt sich erst später.\n");
    const before = Math.ceil(history.reduce((a, m) => a + String(m.content).length, 0) / 4);
    if (before < 1500) return console.log("🗜 Der Verlauf hat erst ≈ " + fmtTokens(before) + " Tokens – Komprimieren lohnt sich ab etwa 1,5k.\n");
    const transcript = history.map((m) => (m.role === "user" ? "NUTZER/WERKZEUG: " : "MYTHOS: ") + String(m.content).slice(0, 3000)).join("\n\n").slice(-60000);
    process.stdout.write(C.d + "🗜 Fasse den Chat zusammen…" + C.x);
    stopped = false;
    let sum = "";
    try {
      sum = (await call([{ role: "user", content: "Fasse den bisherigen Verlauf dieses Coding-Chats so zusammen, dass ein Agent nahtlos weiterarbeiten kann: **Ziel**, **Stand**, **Wichtige Dateien & Entscheidungen**, **Offene Punkte**. Maximal 25 Zeilen, ohne Werkzeuge.\n\nVERLAUF:\n" + transcript }]))
        .replace(/<tool>[\s\S]*?<\/tool>/g, "").trim();
    } catch (e) { clearLine(); return console.log(C.r + "⚠ " + e.message + C.x + "\n"); }
    clearLine();
    history.length = 0;
    history.push({ role: "user", content: "Zusammenfassung des bisherigen Chats (komprimiert):\n\n" + sum }, { role: "assistant", content: "Verstanden – ich arbeite auf Basis dieser Zusammenfassung weiter." });
    return console.log(mdLines(sum) + "\n" + C.g + "🗜 Komprimiert: ≈ " + fmtTokens(before) + " → ≈ " + fmtTokens(Math.ceil(sum.length / 4)) + " Tokens" + C.x + "\n");
  }
  if (cmd === "/stats") { const b = gitBranch(); return console.log(mdLines(projectStats(ROOT) + (b ? "\n**Git:** Branch `" + b + "`" : "")) + "\n"); }
  if (cmd === "/export") {
    const file = path.resolve(ROOT, arg || "mythos-chat-" + new Date().toISOString().slice(0, 16).replace(/[:T]/g, "-") + ".md");
    const msgs = history.map((m) => ({ role: /^Werkzeug-Ergebnis/.test(m.content) ? "tool" : m.role, text: String(m.content).replace(/<tool>[\s\S]*?<\/tool>/g, "").slice(0, 20000) })).filter((m) => m.text.trim());
    fs.writeFileSync(file, chatToMarkdown("Mythos-Chat – " + path.basename(ROOT), msgs));
    return console.log(C.g + "✓ Gespeichert: " + file + C.x + "\n");
  }
  if (cmd === "/befehle") {
    const list = loadCommands(COMMAND_DIRS);
    return console.log(list.length ? list.map((c) => "  " + C.c + c.name.padEnd(20) + C.x + c.desc).join("\n") + "\n" : "Noch keine eigenen Befehle. Leg eine Markdown-Datei in .mythos/commands/ an, z. B. tests.md → /tests.\nErste Zeile = Beschreibung, Rest = Anweisung an Mythos, $ARGUMENTS = was du dahinter schreibst.\n");
  }
  const own = loadCommands(COMMAND_DIRS).find((c) => c.name === cmd);
  if (own) return turn(history, fillCommand(own, arg));
  console.log("Unbekannter Befehl: " + cmd + " – /hilfe zeigt alle Befehle.\n");
}

async function main() {
  const args = process.argv.slice(2).filter((a) => a !== "--yes" && a !== "--key");
  if (args[0] === "login" || !cfg.key) await login();
  if (args[0] === "login") process.exit(0);
  if (args[0] === "logout") { save({}); console.log("Abgemeldet."); process.exit(0); }
  const branch = gitBranch();
  console.log(C.c + C.b + "\n  ✦ MYTHOS CODE" + C.x + C.d + "  – by Mythoscraft · " + path.basename(ROOT) + (branch ? " ⎇ " + branch : "") +
    (readMemory(ROOT) ? " · Gedächtnis ✓" : "") + "\n  /hilfe für Befehle · Strg+C stoppt · Bilder: Datei ins Terminal ziehen\n" + C.x);
  const history = [];
  if (args.length) { const { text, images } = extractImages(args.join(" ")); await turn(history, text, images); }
  while (true) {
    const q = (await ask(C.c + (goal ? "🎯 " : "") + "› " + C.x)).trim();
    if (!q) continue;
    if (q === "/exit") break;
    if (q.startsWith("/")) { await command(q, history); continue; }
    const { text, images } = extractImages(q);
    if (images.length) console.log(C.d + "🖼 " + images.length + " Bild(er) angehängt" + C.x);
    const files = mentionedFiles(text);
    if (files.length) console.log(C.d + "📎 " + files.map((f) => f.name).join(", ") + C.x);
    await turn(history, text + files.map((f) => "\n\n### " + f.name + "\n```\n" + f.content + "\n```").join(""), images);
  }
  rl.close();
}
main();
