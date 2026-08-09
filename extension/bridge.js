/* Brücke: erlaubt der Mythos-AI-Website, Aufgaben direkt an die Erweiterung
   zu geben (ohne Extension-ID, über window.postMessage). */
(() => {
  if (window.__mythosBridge) return;
  window.__mythosBridge = true;

  const post = (payload) => window.postMessage({ source: "mythos-ext", ...payload }, "*");
  const ask = (msg) => new Promise((r) => chrome.runtime.sendMessage(msg, r));

  // Website erkennt: Erweiterung ist installiert
  post({ type: "ready" });
  window.addEventListener("message", async (e) => {
    if (e.source !== window) return;
    const d = e.data;
    if (!d || d.source !== "mythos-web") return;

    if (d.type === "ping") { post({ type: "ready" }); return; }
    if (d.type !== "task" || !d.task) return;

    const task = String(d.task).slice(0, 1500);
    let history = [];
    post({ type: "start", task });

    try {
      let page = await ask({ mythos: "page" });
      for (let step = 0; step < 12; step++) {
        const out = await ask({ mythos: "brain", task, history, page });
        if (!out) { post({ type: "error", message: "Keine Antwort vom Server." }); break; }
        if (out.say) post({ type: "say", text: out.say });
        history.push({ role: "assistant", content: JSON.stringify({ say: out.say, actions: out.actions }) });
        if (out.done || !out.actions?.length) break;

        for (const action of out.actions) {
          const res = await ask({ mythos: "run", action });
          post({ type: "action", action: action.type, info: res?.info || "" });
          if (res?.page) page = res.page;
        }
        if (!page) page = await ask({ mythos: "page" });
        history.push({ role: "user", content: "Aktionen ausgeführt, neuer Seiten-Kontext folgt." });
        history = history.slice(-10);
      }
    } catch (err) {
      post({ type: "error", message: String(err?.message || err) });
    }
    post({ type: "done" });
  });
})();
