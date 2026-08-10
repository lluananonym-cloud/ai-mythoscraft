/* Hintergrund-Service-Worker: führt Aufgaben selbst aus (läuft weiter, auch wenn
   das Popup geschlossen wird), speichert Chat-Verlauf + Erinnerung und kann
   mehrere Tabs parallel bedienen (Multitasking). */
const ENDPOINT = "https://rdxaqiacoitzeowehdts.supabase.co/functions/v1/browser-control";
const ANON =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJkeGFxaWFjb2l0emVvd2VoZHRzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY0NDQzMTAsImV4cCI6MjA5MjAyMDMxMH0.M-1p5wgGsFUMRs_TcH46cpV3slLkLFky3JELQCJhk5o";

const CHAT_TTL = 60_000; // Verlauf 1 Minute nach Schließen behalten
const runs = new Map(); // tabId -> { task, cancel }

/* ---------- Speicher ---------- */
const get = async (k, d) => (await chrome.storage.local.get(k))[k] ?? d;
const set = (o) => chrome.storage.local.set(o);

async function loadChat(tabId) {
  const c = await get(`chat:${tabId}`, null);
  if (!c) return [];
  if (c.closedAt && Date.now() - c.closedAt > CHAT_TTL) {
    await chrome.storage.local.remove(`chat:${tabId}`);
    return [];
  }
  return c.lines || [];
}

async function pushLine(tabId, line) {
  const lines = (await loadChat(tabId)).concat([line]).slice(-200);
  await set({ [`chat:${tabId}`]: { lines, closedAt: null } });
}

async function loadMemory() {
  return await get("memory", []);
}

async function addMemory(entry) {
  const mem = (await loadMemory()).concat([entry]).slice(-20);
  await set({ memory: mem });
}

/* ---------- Tabs / Inhalts-Skript ---------- */
async function activeTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

async function ensureInjected(tabId) {
  try {
    await chrome.scripting.executeScript({ target: { tabId }, files: ["content.js"] });
  } catch { /* bereits injiziert oder geschützte Seite */ }
}

async function send(tabId, payload, tries = 3) {
  for (let i = 0; i < tries; i++) {
    try {
      const res = await chrome.tabs.sendMessage(tabId, payload);
      if (res !== undefined) return res;
    } catch { /* noch nicht bereit */ }
    await ensureInjected(tabId);
    await new Promise((r) => setTimeout(r, 400));
  }
  return null;
}

const broadcast = (ev) => { try { chrome.runtime.sendMessage({ mythos: "event", ...ev }); } catch { /* keine Empfänger */ } };

async function emit(tabId, line) {
  await pushLine(tabId, line);
  broadcast({ tabId, line });
}

/* ---------- KI-Gehirn ---------- */
async function brain(task, history, page, memory) {
  const r = await fetch(ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${ANON}` },
    body: JSON.stringify({ task, history, page, memory }),
  });
  if (!r.ok) throw new Error(`Server ${r.status}`);
  return await r.json();
}

/* ---------- Eine Aktion ausführen ---------- */
async function runAction(tabId, action) {
  if (action.type === "open") {
    const url = String(action.url || "").startsWith("http") ? action.url : `https://${action.url}`;
    await chrome.tabs.update(tabId, { url });
    await new Promise((r) => setTimeout(r, 2000));
    await ensureInjected(tabId);
    const page = await send(tabId, { mythos: "snapshot" });
    return { ok: true, info: `geöffnet: ${url}`, page };
  }
  if (action.type === "newtab") {
    const tab = await chrome.tabs.create({ url: action.url, active: false });
    return { ok: true, info: `neuer Tab: ${action.url}`, newTabId: tab.id };
  }
  await ensureInjected(tabId);
  return (await send(tabId, { mythos: "act", action })) || { ok: false, info: "Seite antwortet nicht" };
}

/* ---------- Aufgabe komplett abarbeiten ---------- */
async function runTask(tabId, task) {
  if (runs.has(tabId)) {
    await emit(tabId, { kind: "err", text: "In diesem Tab läuft bereits eine Aufgabe." });
    return;
  }
  const state = { task, cancel: false };
  runs.set(tabId, state);
  await emit(tabId, { kind: "you", text: task });
  broadcast({ tabId, busy: true });

  const memory = await loadMemory();
  let history = [];
  let lastSay = "";

  try {
    await ensureInjected(tabId);
    let page = await send(tabId, { mythos: "snapshot" });

    for (let step = 0; step < 14 && !state.cancel; step++) {
      const out = await brain(task, history, page, memory);
      if (!out) { await emit(tabId, { kind: "err", text: "Keine Antwort vom Server." }); break; }
      if (out.say) { lastSay = out.say; await emit(tabId, { kind: "ai", text: out.say }); }
      history.push({ role: "assistant", content: JSON.stringify({ say: out.say, actions: out.actions }) });
      if (out.done || !out.actions?.length) break;

      for (const action of out.actions) {
        if (state.cancel) break;
        const res = await runAction(tabId, action);
        await emit(tabId, { kind: "act", text: `${action.type}: ${res?.info || "ausgeführt"}` });
        if (res?.page) page = res.page;
      }
      if (!page) page = await send(tabId, { mythos: "snapshot" });
      history.push({ role: "user", content: "Aktionen ausgeführt, neuer Seiten-Kontext folgt." });
      history = history.slice(-10);
    }
    await addMemory({ task, result: lastSay.slice(0, 400), url: page?.url || "", at: Date.now() });
  } catch (e) {
    await emit(tabId, { kind: "err", text: String(e?.message || e) });
  } finally {
    runs.delete(tabId);
    broadcast({ tabId, busy: false, done: true });
  }
}

/* ---------- Nachrichten ---------- */
chrome.runtime.onMessage.addListener((msg, sender, respond) => {
  (async () => {
    const m = msg?.mythos;
    if (!m) return respond(null);

    if (m === "installed") return respond({ ok: true, version: chrome.runtime.getManifest().version });

    if (m === "state") {
      const tab = sender.tab || (await activeTab());
      const tabId = msg.tabId ?? tab?.id;
      return respond({ tabId, lines: await loadChat(tabId), busy: runs.has(tabId), memory: await loadMemory() });
    }

    if (m === "task") {
      const tab = msg.tabId ? { id: msg.tabId } : sender.tab || (await activeTab());
      if (!tab?.id) return respond({ ok: false, error: "Kein aktiver Tab" });
      runTask(tab.id, String(msg.task || "").slice(0, 1500));
      return respond({ ok: true, tabId: tab.id });
    }

    if (m === "stop") {
      const tabId = msg.tabId ?? (sender.tab || (await activeTab()))?.id;
      const r = runs.get(tabId);
      if (r) r.cancel = true;
      return respond({ ok: true });
    }

    if (m === "closed") {
      const tabId = msg.tabId ?? (sender.tab || (await activeTab()))?.id;
      const c = await get(`chat:${tabId}`, null);
      if (c) await set({ [`chat:${tabId}`]: { ...c, closedAt: Date.now() } });
      return respond({ ok: true });
    }

    if (m === "clear") {
      const tabId = msg.tabId ?? (sender.tab || (await activeTab()))?.id;
      await chrome.storage.local.remove(`chat:${tabId}`);
      return respond({ ok: true });
    }

    if (m === "forget") { await set({ memory: [] }); return respond({ ok: true }); }

    respond(null);
  })();
  return true;
});

chrome.tabs.onRemoved.addListener((tabId) => {
  runs.delete(tabId);
  chrome.storage.local.remove(`chat:${tabId}`);
});
