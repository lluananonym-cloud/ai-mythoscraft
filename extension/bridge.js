/* Brücke: erlaubt der Mythos-AI-Website, Aufgaben direkt an die Erweiterung
   zu geben (ohne Extension-ID, über window.postMessage). */
(() => {
  if (window.__mythosBridge) return;
  window.__mythosBridge = true;

  const version = chrome.runtime.getManifest().version;
  const post = (payload) => window.postMessage({ source: "mythos-ext", version, ...payload }, "*");
  const ask = (msg) => new Promise((r) => { try { chrome.runtime.sendMessage(msg, r); } catch { r(null); } });

  let busy = false;

  // Heartbeat: Website weiß dadurch verlässlich, dass die Erweiterung aktiv ist
  post({ type: "ready" });
  const beat = setInterval(() => post({ type: "ready", busy }), 1500);
  window.addEventListener("pagehide", () => clearInterval(beat));

  // Live-Events aus dem Hintergrund an die Website weitergeben
  chrome.runtime.onMessage.addListener((msg) => {
    if (msg?.mythos !== "event") return;
    if (msg.line) {
      const l = msg.line;
      if (l.kind === "ai") post({ type: "say", text: l.text });
      else if (l.kind === "act") post({ type: "action", action: "", info: l.text });
      else if (l.kind === "err") post({ type: "error", message: l.text });
    }
    if (typeof msg.busy === "boolean") { busy = msg.busy; post({ type: msg.busy ? "start" : "done" }); }
  });

  window.addEventListener("message", async (e) => {
    if (e.source !== window) return;
    const d = e.data;
    if (!d || d.source !== "mythos-web") return;

    if (d.type === "ping") { post({ type: "ready", busy }); return; }
    if (d.type === "history") {
      const st = await ask({ mythos: "state" });
      post({ type: "history", lines: st?.lines || [], busy: !!st?.busy });
      return;
    }
    if (d.type === "stop") { await ask({ mythos: "stop" }); return; }
    if (d.type === "clear") { await ask({ mythos: "clear" }); return; }
    if (d.type === "forget") { await ask({ mythos: "forget" }); return; }
    if (d.type !== "task" || !d.task) return;

    busy = true;
    post({ type: "start", task: String(d.task).slice(0, 1500) });
    const res = await ask({ mythos: "task", task: String(d.task).slice(0, 1500) });
    if (!res?.ok) { post({ type: "error", message: res?.error || "Aufgabe konnte nicht gestartet werden." }); post({ type: "done" }); busy = false; }
  });
})();
