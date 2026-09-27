const CFG = { site: "__SITE__", fn: "__FN__" };
const $ = (id) => document.getElementById(id);
let cfg = {}, chat = null, attach = [], busy = false, stopped = false, ctl = null, editing = null;
let queue = [], gitInfo = null, prompts = { nudge: "", summary: "", memoryFile: "MYTHOS.md" }, searchQ = "", liveOut = null;
const EMPTY = $("empty").cloneNode(true);
const fmt = (ms) => { const s = Math.floor(ms / 1000); return (s >= 60 ? Math.floor(s / 60) + "m " : "") + (s % 60) + "s"; };
const post = (fn, body, h) => fetch(CFG.fn + "/" + fn, { method: "POST", headers: Object.assign({ "content-type": "application/json" }, h || {}), body: JSON.stringify(body) });
const el = (tag, cls, text) => { const d = document.createElement(tag); if (cls) d.className = cls; if (text != null) d.textContent = text; return d; };
const base = (p) => p.split(/[\\/]/).filter(Boolean).pop() || p;
const openLink = (u) => window.mythos.open(u);
const md = (box, text) => window.renderMarkdown(box, text, openLink);
const nearBottom = () => { const l = $("log"); return l.scrollHeight - l.scrollTop - l.clientHeight < 160; };
const scrollDown = () => { $("log").scrollTop = 1e9; };

// ---------- Chat-Ansicht ----------
function diffBox(it) {
  const d = el("details", "diff"); d.open = it.d.lines.length <= 40;
  const s = el("summary"); s.append(el("span", "", "✎ " + it.path + "  "), el("span", "plus", "+" + it.d.added), el("span", "minus", " −" + it.d.removed));
  const pre = el("pre");
  it.d.lines.forEach(([op, l]) => pre.appendChild(el("span", op === "+" ? "ln add" : op === "-" ? "ln del" : op === "@" ? "ln gap" : "ln", (op === "@" ? "" : op + " ") + l)));
  d.append(s, pre); return d;
}
function draw(it, i) {
  let d;
  if (it.cls === "a" || it.cls === "sum") {
    d = el("div", "m a" + (it.cls === "sum" ? " sum glass" : "")); const img = el("img"); img.src = "icon.png";
    const b = el("div", "body"); if (it.cls === "sum") b.appendChild(el("div", "sumhead", "📋 Zusammenfassung")); md(b, it.text); d.append(img, b);
  } else if (it.cls === "u") {
    d = el("div", "m u glass"); d.appendChild(document.createTextNode(it.text));
    const imgs = (it.att || []).filter((a) => a.image);
    if (imgs.length) { const box = el("div", "imgs"); imgs.forEach((a) => { const im = el("img"); im.src = "data:" + a.image.media_type + ";base64," + a.image.data; im.title = a.name; box.appendChild(im); }); d.appendChild(box); }
    const b = el("button", "edit", "✎"); b.title = "Nachricht bearbeiten"; b.onclick = () => startEdit(i); d.appendChild(b);
  } else if (it.cls === "diff") d = diffBox(it);
  else if (it.cls === "out") {
    d = el("details", "out"); d.append(el("summary", "", "▸ Ausgabe von " + (it.cmd || "Befehl")), el("pre", "", it.text));
  } else d = el("div", "t " + it.cls, it.text);
  $("col").appendChild(d); scrollDown(); return d;
}
function add(cls, text, extra) {
  const e = $("empty"); if (e) e.remove();
  chat.view.push(Object.assign({ cls: cls, text: text }, extra || {}));
  return draw(chat.view[chat.view.length - 1], chat.view.length - 1);
}
function renderChat() {
  const col = $("col"); col.textContent = "";
  if (!chat.view.length) {
    const e = EMPTY.cloneNode(true);
    e.querySelectorAll("[data-s]").forEach((b) => b.onclick = () => { $("inp").value = b.dataset.s; grow(); $("inp").focus(); });
    col.appendChild(e);
  }
  chat.view.forEach(draw);
  renderGoal(); renderResume();
}

// ---------- Modell ----------
const systemPrompt = async () => "Du bist Mythos Code, ein autonomer Coding-Agent als Desktop-App (Windows). Arbeitsordner: " + (cfg.folder || "(keiner)") +
  (gitInfo ? " · Git-Branch: " + gitInfo.branch : "") + ". Pfade relativ zum Arbeitsordner.\n" + await window.mythos.systemPrompt(cfg.folder, chat.goal || "");
const shorten = (s, max) => s.length <= max ? s : s.slice(0, Math.floor(max * 0.7)) + "\n…[gekürzt]…\n" + s.slice(s.length - Math.floor(max * 0.3));
const withImages = (m, content) => m.images && m.images.length
  ? { role: m.role, content: [{ type: "text", text: content }].concat(m.images.map((i) => ({ type: "image", source: { type: "base64", media_type: i.media_type, data: i.data } }))) }
  : { role: m.role, content: content };
function compact(h, level) {
  const keep = [6, 8, 4][level], recentMax = [20000, 3000, 1500][level], oldMax = [1500, 800, 400][level];
  const cut = Math.max(0, h.length - keep);
  const older = level === 0 ? h.slice(0, cut) : h.slice(0, Math.min(1, cut));
  return older.map((m) => ({ role: m.role, content: shorten(m.content, oldMax) }))
    .concat(h.slice(cut).map((m) => withImages(m, shorten(m.content, recentMax))));
}
async function once(messages, system, onText) {
  const c = ctl = new AbortController(); const to = setTimeout(() => c.abort(), 170000);
  try {
    const r = await fetch(CFG.fn + "/v1-messages", { method: "POST", signal: c.signal,
      headers: { "content-type": "application/json", "x-api-key": cfg.key, "anthropic-version": "2023-06-01" },
      body: JSON.stringify({ model: "mythos-code", max_tokens: 8000, system: system, messages: messages, stream: true }) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); const e = new Error((j.error && j.error.message) || ("HTTP " + r.status)); e.status = r.status; throw e; }
    const rd = r.body.getReader(), dec = new TextDecoder(); let buf = "", out = "";
    for (;;) { const x = await rd.read(); if (x.done) break; buf += dec.decode(x.value, { stream: true }); let i;
      while ((i = buf.indexOf("\n")) !== -1) { const l = buf.slice(0, i).trim(); buf = buf.slice(i + 1);
        if (!l.startsWith("data:")) continue; let p; try { p = JSON.parse(l.slice(5)); } catch (e) { continue; }
        if (p.type === "error") { const e = new Error((p.error && p.error.message) || "Überlastet"); e.status = 529; throw e; }
        if (p.delta && p.delta.text) { out += p.delta.text; if (onText) onText(out); } } }
    if (!out.trim()) throw new Error("Leere Antwort"); return out;
  } finally { clearTimeout(to); }
}
async function call(h, system, onText) {
  for (let a = 0; a < 6; a++) {
    if (stopped) throw new Error("Gestoppt");
    try { return await once(compact(h, Math.min(2, Math.floor(a / 2))), system, onText); }
    catch (e) {
      if (stopped) throw e;
      if (e.status === 401) throw new Error("Anmeldung abgelaufen – bitte abmelden und neu anmelden.");
      if (/Daily limit/i.test(e.message)) throw new Error("Tageslimit erreicht – mit Pro unbegrenzt.");
      const wait = Math.min(8000, 1500 * (a + 1));
      for (let w = 0; w < wait && !stopped; w += 250) await new Promise((r) => setTimeout(r, 250));
    }
  }
  throw new Error("Mythos ist gerade nicht erreichbar – bitte gleich nochmal versuchen.");
}
async function usage() { try { const j = await (await post("cli-auth", { action: "usage", api_key: cfg.key })).json();
  $("usage").textContent = j.limit == null ? "Usage " + j.used + " · ∞ Pro" : "Usage " + j.used + " / " + j.limit; } catch (e) {} }

// Antwort live mitlesen: sichtbarer Text ohne <tool>-Block (auch nicht halb angefangen).
function visibleText(full) {
  const k = full.indexOf("<tool>"); if (k >= 0) return full.slice(0, k);
  for (let n = 5; n > 0; n--) if (full.endsWith("<tool>".slice(0, n))) return full.slice(0, -n);
  return full;
}
function streamView(anchor) {
  let box = null, body = null, latest = "", timer = 0;
  const paint = () => {
    timer = 0; const vis = visibleText(latest).trim(); if (!vis) return;
    const stick = nearBottom();
    if (!box) { box = el("div", "m a streaming"); const img = el("img"); img.src = "icon.png"; body = el("div", "body"); box.append(img, body); $("col").insertBefore(box, anchor); }
    body.textContent = ""; md(body, vis); if (stick) scrollDown();
  };
  return {
    push(full) { latest = full; if (!timer) timer = setTimeout(paint, 60); },
    done() { clearTimeout(timer); timer = 0; if (box) box.remove(); box = null; },
  };
}

// ---------- Senden, Warteschlange & Stoppen ----------
function setBusy(b) {
  busy = b; const s = $("btnSend");
  s.textContent = b ? "■" : "↑"; s.title = b ? "Stoppen (Esc)" : "Senden"; s.classList.toggle("stop", b); s.disabled = false;
  $("timer").classList.toggle("busy", b); document.body.classList.toggle("busy", b);
  $("inp").placeholder = b ? "Nächste Aufgabe? Enter reiht sie in die Warteschlange ein" : "Was soll Mythos bauen?  ( / = Befehle )";
  grow();
  window.mythos.working(b); renderResume(); renderQueue();
}
function stop() {
  if (!busy || stopped) return;
  stopped = true; $("btnSend").disabled = true;
  if (ctl) ctl.abort(); window.mythos.abort();
}
const note = (text) => add("note", text);
function setAuto(on) { $("auto").checked = !!on; cfg.auto = !!on; window.mythos.setCfg(cfg); }
// Neue Nutzer-Nachricht anhängen (beim Bearbeiten wird der Chat vorher ab dort abgeschnitten).
function pushUser(display, content, extra) {
  if (editing != null) { const it = chat.view[editing]; chat.history.length = it.h; chat.view.length = editing; editing = null; renderEdit(); renderChat(); }
  if (!chat.history.length) { chat.title = (extra.q || display).replace(/\s+/g, " ").slice(0, 48); chat.folder = cfg.folder || ""; }
  add("u", display, Object.assign({ h: chat.history.length }, extra));
  const images = (extra.att || []).filter((a) => a.image).map((a) => a.image);
  chat.history.push(images.length ? { role: "user", content: content, images: images } : { role: "user", content: content });
}
function send() {
  const q = $("inp").value.trim(); if (!q && !attach.length) return;
  $("inp").value = ""; grow(); renderSlash();
  const item = { q: q || "Schau dir die angehängten Dateien an.", att: attach };
  attach = []; renderFiles();
  if (busy) { queue.push(item); renderQueue(); return; }
  submit(item.q, item.att);
}
function submit(q, att) {
  if (q.startsWith("/")) return command(q);
  const files = att.filter((f) => !f.image);
  let content = q; if (files.length) content += "\n\nHochgeladene Dateien:\n" + files.map((f) => "### " + f.name + "\n" + f.content).join("\n\n");
  const names = att.filter((f) => !f.image).map((f) => f.name);
  pushUser(q + (names.length ? "\n📎 " + names.join(", ") : ""), content, { q: q, att: att });
  runAgent();
}
function renderQueue() {
  const bar = $("queuebar"); bar.textContent = "";
  bar.style.display = queue.length ? "flex" : "none"; if (!queue.length) return;
  bar.appendChild(el("span", "", "⏳ Warteschlange (" + queue.length + "):"));
  queue.forEach((it, i) => {
    const c = el("button", "chip", (it.q.length > 40 ? it.q.slice(0, 40) + "…" : it.q) + "  ✕"); c.title = "Aus der Warteschlange entfernen";
    c.onclick = () => { queue.splice(i, 1); renderQueue(); }; bar.appendChild(c);
  });
  if (!busy) { const go = el("button", "chip", "▶ Abarbeiten"); go.onclick = processQueue; bar.appendChild(go); }
}
function processQueue() { if (busy || !queue.length) return; const it = queue.shift(); renderQueue(); submit(it.q, it.att); }

function toolLabel(t) {
  if (t.name === "run") return "⚙ run " + t.cmd;
  if (t.name === "search") return "🔎 search /" + t.pattern + "/" + (t.glob ? " " + t.glob : "");
  if (t.name === "edit") return "✎ edit " + t.path;
  if (t.name === "write") return "✎ write " + t.path;
  return "⚙ " + t.name + " " + (t.path || "");
}
async function runAgent() {
  stopped = false; chat.unfinished = true; setBusy(true); saveChat();
  const goal = chat.goal || "", max = goal ? 200 : 40;
  let steps = 0, nudges = 0, finished = false, reached = false, summary = "";
  const t0 = Date.now(); const live = draw({ cls: "live", text: "⏳ Mythos arbeitet… 0s" }, -1);
  const tick = setInterval(() => { const s = fmt(Date.now() - t0); $("timer").textContent = "arbeitet… " + s; live.textContent = "⏳ Mythos arbeitet… " + s; $("col").appendChild(live); }, 250);
  try {
    for (; steps < max && !stopped; steps++) {
      const sv = streamView(live);
      let out; try { out = await call(chat.history, await systemPrompt(), (full) => sv.push(full)); } finally { sv.done(); }
      if (stopped) break;
      chat.history.push({ role: "assistant", content: out });
      const m = out.match(/<tool>([\s\S]*?)<\/tool>/); const text = out.replace(/<tool>[\s\S]*?<\/tool>/g, "").trim();
      if (text) add("a", text);
      if (!m) {
        if (!goal || /ZIEL ERREICHT/.test(out)) { finished = true; reached = !!goal; break; }
        // /goal: Mythos hat aufgehört, ohne das Ziel als erreicht zu melden -> weiter antreiben.
        if (++nudges > 5) { note("🎯 Mythos kommt beim Ziel nicht weiter – schau es dir bitte an. Mit /weiter geht es weiter."); break; }
        chat.history.push({ role: "user", content: prompts.nudge });
        saveChat(); continue;
      }
      nudges = 0;
      let t; try { t = JSON.parse(m[1]); } catch (e) { chat.history.push({ role: "user", content: "Tool-JSON ungültig." }); continue; }
      add("tool", toolLabel(t));
      let r;
      if ((t.name === "run" || t.name === "write" || t.name === "edit") && !$("auto").checked && !confirm("Mythos möchte ausführen:\n" + t.name + " " + (t.cmd || t.path))) r = { result: "Vom Nutzer abgelehnt." };
      else {
        if (t.name === "run") { liveOut = el("pre", "liveout"); $("col").insertBefore(liveOut, live); }
        try { r = await window.mythos.tool(t, cfg.folder); } finally { if (liveOut) { liveOut.remove(); liveOut = null; } }
        if (t.name === "run") add("out", r.result.slice(-4000), { cmd: t.cmd });
        if (r.diff && r.diff.lines.length) add("diff", "", { path: t.path, d: r.diff });
      }
      chat.history.push({ role: "user", content: "Werkzeug-Ergebnis:\n" + r.result });
      saveChat();
    }
    if (!finished && !stopped && steps >= max) note("Schrittlimit (" + max + ") erreicht – mit /weiter macht Mythos weiter.");
    // Nach /goal: kurze Zusammenfassung, was passiert ist.
    if (goal && !stopped) {
      chat.history.push({ role: "user", content: prompts.summary });
      const sv = streamView(live);
      try { summary = await call(chat.history, await systemPrompt(), (full) => sv.push(full)); } finally { sv.done(); }
      summary = summary.replace(/<tool>[\s\S]*?<\/tool>/g, "").trim();
      chat.history.push({ role: "assistant", content: summary });
      add("sum", summary);
    }
  } catch (e) { if (!stopped) add("a", "⚠ " + e.message); }
  clearInterval(tick); live.remove();
  const took = fmt(Date.now() - t0); $("timer").textContent = "⏱ " + took;
  if (stopped && chat.history[chat.history.length - 1].role === "user") chat.history.push({ role: "assistant", content: "(Vom Nutzer gestoppt.)" });
  if (reached) { note("🎯 Ziel erreicht: " + goal); chat.goal = ""; renderGoal(); }
  add("done", stopped ? "■ Gestoppt nach " + took : "✓ Mythos hat " + took + " gearbeitet");
  chat.unfinished = !finished; setBusy(false); saveChat(); usage(); refreshGit();
  const title = stopped ? "■ Gestoppt nach " + took : reached ? "🎯 Ziel erreicht" : finished ? "✓ Fertig nach " + took : "⚠ Unterbrochen – Mythos braucht dich";
  window.mythos.notify("Mythos Code", title);
  if (cfg.notify && (goal || Date.now() - t0 > 60000)) window.mythos.push(cfg.notify, "Mythos Code – " + (cfg.folder ? base(cfg.folder) : chat.title), title + (summary ? "\n\n" + summary.slice(0, 1500) : ""));
  if (!stopped) processQueue(); else renderQueue();
}

// ---------- AFK: Weitermachen & Ziel ----------
function renderResume() { $("resumebar").style.display = chat && !busy && chat.unfinished && chat.history.length ? "flex" : "none"; }
function renderGoal() { $("goalbar").style.display = chat && chat.goal ? "flex" : "none"; $("goaltext").textContent = chat && chat.goal ? "Ziel: " + chat.goal : ""; }
function continueChat() {
  if (busy || !chat.history.length) return;
  if (chat.history[chat.history.length - 1].role === "assistant") chat.history.push({ role: "user", content: "Mach bitte genau dort weiter, wo du aufgehört hast." });
  note("▶ Weitermachen"); runAgent();
}
function endGoal() { if (!chat.goal) return; chat.goal = ""; renderGoal(); note("🎯 Ziel beendet."); saveChat(); }

// ---------- Git ----------
async function refreshGit() {
  gitInfo = cfg.folder ? await window.mythos.git.info(cfg.folder).catch(() => null) : null;
  const g = $("git"); g.style.display = gitInfo ? "" : "none";
  if (gitInfo) { $("gitb").textContent = gitInfo.branch + (gitInfo.changed ? " · " + gitInfo.changed + " geändert" : " ✓"); g.title = gitInfo.status || "Keine Änderungen"; }
}
async function gitCommit(msg) {
  await refreshGit();
  if (!gitInfo) return note("Der Projektordner ist kein Git-Repository.");
  if (!gitInfo.changed) return note("Nichts zu committen – alles sauber ✓");
  if (!msg) {
    const n = draw({ cls: "note", text: "⏳ Mythos schreibt die Commit-Nachricht…" }, -1); // nur angezeigt, nicht gespeichert
    stopped = false;
    try {
      msg = (await call([{ role: "user", content: "Schreibe eine kurze Git-Commit-Nachricht auf Deutsch für diese Änderungen: erste Zeile max. 72 Zeichen, optional Leerzeile + Stichpunkte. Antworte NUR mit der Nachricht, ohne Werkzeuge, ohne Codeblock.\n\n" + await window.mythos.git.diff(cfg.folder) }], "Du schreibst Git-Commit-Nachrichten."))
        .replace(/<tool>[\s\S]*?<\/tool>/g, "").replace(/^```\w*\n?|```$/g, "").trim();
    } catch (e) { return note("⚠ " + e.message); } finally { n.remove(); }
  }
  if (!confirm("Alle Änderungen committen mit dieser Nachricht?\n\n" + msg)) return note("Commit abgebrochen.");
  const r = await window.mythos.git.commit(cfg.folder, msg);
  note((r.ok ? "✓ Committet\n" : "⚠ Commit fehlgeschlagen\n") + r.out.trim());
  refreshGit();
}

// ---------- Befehle ----------
const COMMANDS = [
  ["/goal", "<ziel>", "Mythos arbeitet selbstständig, bis das Ziel erreicht ist – danach Zusammenfassung"],
  ["/weiter", "", "Unterbrochene oder gestoppte Arbeit fortsetzen"],
  ["/neu", "", "Neuen Chat starten"],
  ["/projekt", "", "Projektordner wählen"],
  ["/merken", "<text>", "Etwas ins Projekt-Gedächtnis (MYTHOS.md) schreiben"],
  ["/gedaechtnis", "", "Projekt-Gedächtnis anzeigen"],
  ["/git", "", "Git-Status anzeigen"],
  ["/commit", "[nachricht]", "Alles committen – ohne Nachricht schreibt Mythos sie"],
  ["/push", "", "git push"],
  ["/handy", "<url>|test|aus", "Handy-Benachrichtigung (ntfy.sh-Thema oder Discord-Webhook)"],
  ["/warteschlange", "[leeren]", "Warteschlange anzeigen oder leeren"],
  ["/suche", "<text>", "Alle Chats durchsuchen"],
  ["/umbenennen", "<name>", "Aktuellen Chat umbenennen"],
  ["/vollzugriff", "an|aus", "Vollzugriff ein- oder ausschalten"],
  ["/hilfe", "", "Alle Befehle anzeigen"],
];
async function command(q) {
  const cmd = q.split(/\s+/)[0].toLowerCase(), arg = q.slice(cmd.length).trim();
  if (cmd !== "/goal" && cmd !== "/ziel" && editing != null) { editing = null; renderEdit(); }
  if (cmd === "/goal" || cmd === "/ziel") {
    if (!arg) return note(chat.goal ? "🎯 Aktuelles Ziel: " + chat.goal + "\nBeenden mit /goal stop" : "So geht's: /goal <was Mythos erreichen soll>\nMythos arbeitet dann ohne Rückfragen, bis das Ziel erreicht ist, und fasst am Ende zusammen.");
    if (/^(stop|aus|ende|beenden)$/i.test(arg)) return endGoal();
    if (!$("auto").checked) { if (!confirm("Mit /goal arbeitet Mythos ohne Nachfragen weiter.\nVollzugriff dafür einschalten?")) return; setAuto(true); }
    pushUser("🎯 /goal " + arg, "Neues Ziel: " + arg + "\nArbeite jetzt komplett selbstständig daran, bis es erreicht und geprüft ist.", { q: "/goal " + arg });
    chat.goal = arg; renderGoal();
    return runAgent();
  }
  if (cmd === "/weiter") return chat.history.length ? continueChat() : note("Hier gibt es noch nichts zum Weitermachen.");
  if (cmd === "/neu") return newChat();
  if (cmd === "/projekt") return pickProject();
  if (cmd === "/merken") {
    if (!cfg.folder) return note("Wähle zuerst einen Projektordner.");
    if (!arg) return note("So geht's: /merken <was Mythos sich für dieses Projekt merken soll>");
    await window.mythos.memory.add(cfg.folder, arg); return note("🧠 In " + prompts.memoryFile + " gemerkt: " + arg);
  }
  if (cmd === "/gedaechtnis" || cmd === "/gedächtnis") {
    const m = cfg.folder ? await window.mythos.memory.get(cfg.folder) : "";
    return note(m ? "🧠 " + prompts.memoryFile + ":\n" + m : "Noch kein Projekt-Gedächtnis. Mit /merken <text> anlegen – Mythos liest " + prompts.memoryFile + " dann bei jeder Aufgabe mit.");
  }
  if (cmd === "/git") { await refreshGit(); return note(gitInfo ? "⎇ " + gitInfo.branch + "\n" + (gitInfo.status.trim() || "Keine Änderungen ✓") : "Der Projektordner ist kein Git-Repository."); }
  if (cmd === "/commit") return gitCommit(arg);
  if (cmd === "/push") {
    if (!cfg.folder) return note("Wähle zuerst einen Projektordner.");
    const n = draw({ cls: "note", text: "⏳ git push…" }, -1);
    const r = await window.mythos.git.push(cfg.folder); n.remove();
    note((r.ok ? "✓ Gepusht\n" : "⚠ Push fehlgeschlagen\n") + r.out.trim());
    return refreshGit();
  }
  if (cmd === "/handy") {
    if (/^(aus|off)$/i.test(arg)) { delete cfg.notify; window.mythos.setCfg(cfg); return note("📱 Handy-Benachrichtigung aus."); }
    if (/^test$/i.test(arg)) return note(cfg.notify && await window.mythos.push(cfg.notify, "Mythos Code", "Test – Benachrichtigungen funktionieren ✓") ? "📱 Test gesendet ✓" : "📱 Senden fehlgeschlagen – URL prüfen (/handy <url>).");
    if (!/^https:\/\//i.test(arg)) return note("📱 So geht's: App „ntfy“ aufs Handy, ein geheimes Thema abonnieren, dann:\n/handy https://ntfy.sh/dein-geheimes-thema\nOder einen Discord-Webhook-Link angeben. Test: /handy test");
    cfg.notify = arg; window.mythos.setCfg(cfg);
    return note("📱 Gespeichert. Bei /goal und Aufgaben über 1 Minute bekommst du eine Nachricht aufs Handy. Test: /handy test");
  }
  if (cmd === "/warteschlange") {
    if (/^leeren$/i.test(arg)) { queue = []; renderQueue(); return note("Warteschlange geleert."); }
    return note(queue.length ? "⏳ Warteschlange:\n" + queue.map((it, i) => (i + 1) + ". " + it.q).join("\n") : "Die Warteschlange ist leer. Tipp: Während Mythos arbeitet, reiht Enter neue Aufgaben ein.");
  }
  if (cmd === "/suche") { $("csearch").value = arg; searchQ = arg; renderSide(); return $("csearch").focus(); }
  if (cmd === "/umbenennen") {
    if (!arg) return note("So geht's: /umbenennen <neuer Name>");
    chat.title = arg.slice(0, 80); note("✎ Chat heißt jetzt „" + chat.title + "“."); return chat.history.length ? saveChat() : renderSide();
  }
  if (cmd === "/vollzugriff") { const on = !/^(aus|off|0)$/i.test(arg); setAuto(on); return note(on ? "Vollzugriff ist an – Mythos fragt nicht mehr nach." : "Vollzugriff ist aus – Mythos fragt vor Befehlen nach."); }
  if (cmd === "/hilfe" || cmd === "/help") return note(COMMANDS.map((c) => c[0] + (c[1] ? " " + c[1] : "") + " – " + c[2]).join("\n"));
  note("Unbekannter Befehl: " + cmd + " – /hilfe zeigt alle Befehle.");
}
function renderSlash() {
  const v = $("inp").value, box = $("slash");
  const list = /^\/\S*$/.test(v) ? COMMANDS.filter((c) => c[0].startsWith(v.toLowerCase())) : [];
  box.textContent = ""; box.style.display = list.length ? "flex" : "none";
  list.forEach((c) => {
    const b = el("button"); b.append(el("span", "c", c[0] + (c[1] ? " " + c[1] : "")), el("span", "d", c[2]));
    b.onclick = () => { $("inp").value = c[0] + (c[1] ? " " : ""); renderSlash(); grow(); $("inp").focus(); };
    box.appendChild(b);
  });
}

// ---------- Bearbeiten ----------
function renderEdit() { $("editbar").style.display = editing == null ? "none" : "flex"; }
function startEdit(i) {
  if (busy) return; const it = chat.view[i]; if (!it) return;
  editing = i; $("inp").value = it.q != null ? it.q : it.text; attach = (it.att || []).slice(); renderFiles(); grow(); renderEdit(); $("inp").focus();
}
function cancelEdit() {
  if (editing == null) return;
  editing = null; $("inp").value = ""; attach = []; renderFiles(); grow(); renderEdit();
}

// ---------- Gespeicherte Chats ----------
const newId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
function newChat() {
  chat = { id: newId(), title: "Neuer Chat", folder: cfg.folder || "", created: Date.now(), updated: Date.now(), history: [], view: [] };
  cancelEdit(); renderChat(); renderSide(); $("inp").focus();
}
async function saveChat() { if (!chat.history.length) return; chat.updated = Date.now(); await window.mythos.chats.save(chat); renderSide(); }
async function openChat(id) {
  if (busy || (chat && chat.id === id)) return;
  const c = await window.mythos.chats.get(id); if (!c) return renderSide();
  chat = c; chat.view = chat.view || []; chat.history = chat.history || []; cancelEdit();
  if (c.folder && c.folder !== cfg.folder) { cfg.folder = c.folder; addProject(c.folder); await window.mythos.setCfg(cfg); renderHeader(); refreshGit(); }
  renderChat(); renderSide();
}
async function deleteChat(c) {
  if (busy && chat.id === c.id) return;
  if (!confirm("Chat „" + (c.title || "Chat") + "“ löschen?")) return;
  await window.mythos.chats.remove(c.id);
  if (chat.id === c.id) newChat(); else renderSide();
}
function renameChat(c, span) {
  const inp = el("input", "ren"); inp.value = c.title || ""; span.replaceWith(inp); inp.focus(); inp.select();
  inp.onclick = (e) => e.stopPropagation();
  let done = false;
  const finish = async (ok) => {
    if (done) return; done = true; const t = inp.value.trim().slice(0, 80);
    if (ok && t) {
      const full = chat.id === c.id ? chat : await window.mythos.chats.get(c.id);
      if (full) { full.title = t; await window.mythos.chats.save(full); }
    }
    renderSide();
  };
  inp.onkeydown = (e) => { e.stopPropagation(); if (e.key === "Enter") finish(true); if (e.key === "Escape") finish(false); };
  inp.onblur = () => finish(true);
}

// ---------- Projekte ----------
const projects = () => (cfg.projects = cfg.projects || []);
function addProject(p) { if (p && !projects().includes(p)) projects().unshift(p); }
function renderHeader() { $("folder").textContent = cfg.folder ? base(cfg.folder) : "Projekt wählen"; $("btnFolder").title = cfg.folder || "Projektordner wählen"; }
async function setProject(p) {
  if (busy) return;
  cfg.folder = p || ""; addProject(p); await window.mythos.setCfg(cfg); renderHeader(); refreshGit();
  if (chat.history.length) newChat(); else { chat.folder = cfg.folder; renderSide(); }
}
async function pickProject() { if (busy) return; const f = await window.mythos.pickFolder(); if (f) setProject(f); }
async function removeProject(p) {
  if (busy) return;
  cfg.projects = projects().filter((x) => x !== p);
  if (cfg.folder === p) return setProject(cfg.projects[0] || "");
  await window.mythos.setCfg(cfg); renderSide();
}
function chatRow(c, withSnippet) {
  const row = el("div", "item" + (chat && c.id === chat.id ? " on" : "")); row.title = c.title || "Chat";
  const name = el("span", "name", c.title || "Chat");
  const date = el("span", "date", new Date(c.updated).toLocaleDateString("de-DE", { day: "2-digit", month: "2-digit" }));
  const ed = el("button", "x", "✎"); ed.title = "Umbenennen"; ed.onclick = (e) => { e.stopPropagation(); renameChat(c, name); };
  const x = el("button", "x", "✕"); x.title = "Löschen"; x.onclick = (e) => { e.stopPropagation(); deleteChat(c); };
  row.append(name, date, ed, x); row.onclick = () => openChat(c.id);
  if (!withSnippet) return row;
  const wrap = el("div", "hit"); wrap.append(row, el("div", "snip", (c.folder ? "📁 " + base(c.folder) + " · " : "") + c.snippet));
  wrap.onclick = () => openChat(c.id); return wrap;
}
async function renderSide() {
  const pl = $("plist"); pl.textContent = "";
  projects().forEach((p) => {
    const row = el("div", "item" + (p === cfg.folder ? " on" : "")); row.title = p;
    const x = el("button", "x", "✕"); x.title = "Aus der Liste entfernen (Dateien bleiben)"; x.onclick = (e) => { e.stopPropagation(); removeProject(p); };
    row.append(el("span", "name", "📁 " + base(p)), x);
    row.onclick = () => { if (p !== cfg.folder) setProject(p); };
    pl.appendChild(row);
  });
  if (!projects().length) pl.appendChild(el("div", "hint", "Noch keine Projekte"));
  const q = searchQ.trim();
  let list = [];
  try { list = q ? await window.mythos.chats.search(q) : (await window.mythos.chats.list()).filter((c) => (c.folder || "") === (cfg.folder || "")); } catch (e) {}
  if (q !== searchQ.trim()) return; // inzwischen neue Suche
  const cl = $("clist"); cl.textContent = "";
  list.forEach((c) => cl.appendChild(chatRow(c, !!q)));
  if (!list.length) cl.appendChild(el("div", "hint", q ? "Keine Treffer in deinen Chats" : "Noch keine gespeicherten Chats"));
}

// ---------- Dateien, Drag & Drop, Bilder einfügen ----------
// Bilder verkleinern (max. 1568 px), damit sie schnell und günstig ans Modell gehen.
function shrinkImage(mediaType, b64) {
  return new Promise((res) => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, 1568 / Math.max(img.width, img.height));
      if (scale === 1 && b64.length < 1.5e6) return res({ media_type: mediaType, data: b64 });
      const c = document.createElement("canvas"); c.width = Math.round(img.width * scale); c.height = Math.round(img.height * scale);
      c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
      res({ media_type: "image/jpeg", data: c.toDataURL("image/jpeg", 0.85).split(",")[1] });
    };
    img.onerror = () => res(null);
    img.src = "data:" + mediaType + ";base64," + b64;
  });
}
const readAs = (file, how) => new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = () => rej(r.error); r[how](file); });
async function addFiles(files) {
  for (const f of Array.from(files).slice(0, 10)) {
    try {
      if (/^image\/(png|jpe?g|gif|webp)$/.test(f.type)) {
        const url = await readAs(f, "readAsDataURL");
        const image = await shrinkImage(f.type, String(url).split(",")[1]);
        if (image) attach.push({ name: f.name || "Bild.png", image: image });
      } else if (f.size <= 2e6) {
        const text = await readAs(f, "readAsText");
        attach.push({ name: f.name, content: String(text).includes("\u0000") ? "(Binärdatei)" : String(text).slice(0, 60000) });
      } else note("📎 " + f.name + " ist zu groß (max. 2 MB).");
    } catch (e) { note("📎 " + (f.name || "Datei") + " konnte nicht gelesen werden (Ordner lassen sich nicht ablegen – nutze dafür 📁)."); }
  }
  renderFiles(); $("inp").focus();
}
function renderFiles() {
  const f = $("files"); f.textContent = "";
  attach.forEach((a, i) => { const c = el("button", "chip", (a.image ? "🖼 " : "📎 ") + a.name + "  ✕"); c.title = "Entfernen"; c.onclick = () => { attach.splice(i, 1); renderFiles(); }; f.appendChild(c); });
}
let dragDepth = 0;
document.addEventListener("dragenter", (e) => { if (!e.dataTransfer || !Array.from(e.dataTransfer.types).includes("Files")) return; dragDepth++; document.body.classList.add("dragging"); });
document.addEventListener("dragleave", () => { dragDepth = Math.max(0, dragDepth - 1); if (!dragDepth) document.body.classList.remove("dragging"); });
document.addEventListener("dragover", (e) => e.preventDefault());
document.addEventListener("drop", (e) => {
  e.preventDefault(); dragDepth = 0; document.body.classList.remove("dragging");
  if ($("app").style.display !== "none" && e.dataTransfer && e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
});
$("inp").addEventListener("paste", (e) => {
  const files = Array.from((e.clipboardData && e.clipboardData.files) || []);
  if (!files.length) return;
  e.preventDefault(); addFiles(files);
});

// ---------- Anmeldung ----------
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
function show() {
  $("login").style.display = "none"; $("app").style.display = "flex";
  renderHeader(); refreshGit(); if (!chat) newChat(); else renderSide();
  usage(); $("inp").focus();
}
function grow() { const t = $("inp"); t.style.height = "auto"; if (t.value) t.style.height = Math.min(200, t.scrollHeight) + "px"; }

window.mythos.onOutput((chunk) => {
  if (!liveOut) return;
  const stick = nearBottom();
  liveOut.textContent = (liveOut.textContent + chunk.replace(/\x1b\[[0-9;?]*[A-Za-z]/g, "")).slice(-20000);
  liveOut.scrollTop = 1e9; if (stick) scrollDown();
});
$("btnLogin").onclick = login;
$("btnSend").onclick = () => (busy ? stop() : send());
$("inp").oninput = () => { grow(); renderSlash(); };
$("inp").onkeydown = (e) => {
  if (e.key === "Tab" && $("slash").style.display !== "none") { e.preventDefault(); $("slash").firstChild.click(); return; }
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
};
document.addEventListener("keydown", (e) => {
  if (e.key !== "Escape") return;
  if ($("slash").style.display !== "none") { $("slash").style.display = "none"; return; }
  if (busy) stop(); else cancelEdit();
});
let searchTimer = 0;
$("csearch").oninput = () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { searchQ = $("csearch").value; renderSide(); }, 200); };
$("git").onclick = () => command("/git");
$("btnResume").onclick = continueChat; $("btnGoalEnd").onclick = endGoal;
$("auto").onchange = () => setAuto($("auto").checked);
$("btnFolder").onclick = pickProject; $("btnAddProj").onclick = pickProject;
$("btnNew").onclick = () => { if (!busy) newChat(); };
$("btnEditCancel").onclick = cancelEdit;
$("btnUp").onclick = async () => {
  const picked = await window.mythos.pickFiles();
  for (const p of picked) {
    if (p.image) { const image = await shrinkImage(p.image.media_type, p.image.data); if (image) attach.push({ name: p.name, image: image }); }
    else attach.push(p);
  }
  renderFiles();
};
$("btnOut").onclick = async () => { stop(); cfg = { projects: cfg.projects, folder: cfg.folder, auto: cfg.auto, notify: cfg.notify }; await window.mythos.setCfg(cfg); location.reload(); };
window.mythos.prompts().then((p) => { prompts = p; });
window.mythos.getCfg().then((c) => { cfg = c || {}; $("auto").checked = !!cfg.auto; addProject(cfg.folder); if (cfg.key) show(); });
