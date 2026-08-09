/* Hintergrund-Service-Worker: verbindet Popup, Tabs und Inhalts-Skript. */
const ENDPOINT = "https://rdxaqiacoitzeowehdts.supabase.co/functions/v1/browser-control";
const ANON =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJkeGFxaWFjb2l0emVvd2VoZHRzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY0NDQzMTAsImV4cCI6MjA5MjAyMDMxMH0.M-1p5wgGsFUMRs_TcH46cpV3slLkLFky3JELQCJhk5o";

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
      return await chrome.tabs.sendMessage(tabId, payload);
    } catch {
      await ensureInjected(tabId);
      await new Promise((r) => setTimeout(r, 500));
    }
  }
  return null;
}

chrome.runtime.onMessage.addListener((msg, _sender, respond) => {
  (async () => {
    if (msg?.mythos === "brain") {
      const r = await fetch(ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${ANON}` },
        body: JSON.stringify({ task: msg.task, history: msg.history, page: msg.page }),
      });
      respond(await r.json());
      return;
    }

    if (msg?.mythos === "page") {
      const tab = await activeTab();
      if (!tab?.id) return respond(null);
      await ensureInjected(tab.id);
      respond(await send(tab.id, { mythos: "snapshot" }));
      return;
    }

    if (msg?.mythos === "run") {
      const action = msg.action;
      if (action.type === "open") {
        const url = action.url?.startsWith("http") ? action.url : `https://${action.url}`;
        const tab = await activeTab();
        if (tab?.id) await chrome.tabs.update(tab.id, { url });
        else await chrome.tabs.create({ url });
        await new Promise((r) => setTimeout(r, 2200));
        const t2 = await activeTab();
        if (t2?.id) await ensureInjected(t2.id);
        const page = t2?.id ? await send(t2.id, { mythos: "snapshot" }) : null;
        return respond({ ok: true, info: `geöffnet: ${url}`, page });
      }
      const tab = await activeTab();
      if (!tab?.id) return respond({ ok: false, info: "Kein aktiver Tab" });
      await ensureInjected(tab.id);
      respond((await send(tab.id, { mythos: "act", action })) || { ok: false, info: "Seite antwortet nicht" });
      return;
    }

    respond(null);
  })();
  return true;
});
