const log = document.getElementById("log");
const input = document.getElementById("in");
const go = document.getElementById("go");
const status = document.getElementById("status");
const statusDot = document.getElementById("statusDot");
const tabs = [document.getElementById("tabChat"), document.getElementById("tabConnections")];
const connections = document.getElementById("connections");

const ask = (msg) => new Promise((r) => chrome.runtime.sendMessage(msg, r));

let tabId = null;
let busy = false;

function render(line) {
  const el = document.createElement("div");
  el.className =
    line.kind === "act" ? "act" :
    line.kind === "err" ? "m ai err" :
    line.kind === "you" ? "m user" : "m ai";
  el.textContent = line.kind === "act" ? `› ${line.text}` : line.text;
  log.appendChild(el);
  log.scrollTop = log.scrollHeight;
}

function setBusy(v) {
  busy = v;
  go.textContent = v ? "Stop" : "Los";
  status.textContent = v ? "arbeitet…" : "bereit";
  statusDot.className = "dot" + (v ? " busy" : "");
}

function show(which) { const conn = which === "connections"; log.style.display = conn ? "none" : "flex"; document.querySelector("footer").style.display = conn ? "none" : "flex"; document.querySelector(".hint").style.display = conn ? "none" : "block"; connections.style.display = conn ? "block" : "none"; tabs[0].classList.toggle("on", !conn); tabs[1].classList.toggle("on", conn); if (conn) refreshApp(); }
async function refreshApp() { const st = await ask({ mythos: "app-state" }); document.getElementById("appState").textContent = st?.connected ? "Verbunden – Aufgaben aus Mythos Code erscheinen automatisch im aktiven Tab." : st?.paired ? "Gekoppelt, aber Mythos Code ist gerade nicht erreichbar." : "Nicht gekoppelt. Öffne in der Windows-App „Verbindungen“ und gib den sechsstelligen Code hier ein."; document.querySelector(".pair").style.display = st?.paired ? "none" : "flex"; document.getElementById("unpairBtn").style.display = st?.paired ? "block" : "none"; }

async function boot() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  tabId = tab?.id ?? null;
  const st = await ask({ mythos: "state", tabId });
  if (st?.lines?.length) { log.innerHTML = ""; st.lines.forEach(render); }
  setBusy(!!st?.busy);
  input.focus();
}

async function submit() {
  if (busy) { await ask({ mythos: "stop", tabId }); setBusy(false); return; }
  const t = input.value.trim();
  if (!t) return;
  input.value = "";
  setBusy(true);
  await ask({ mythos: "task", task: t, tabId });
  input.focus();
}

chrome.runtime.onMessage.addListener((msg) => {
  if (msg?.mythos !== "event") return;
  if (msg.tabId != null && tabId != null && msg.tabId !== tabId) return;
  if (msg.line) render(msg.line);
  if (typeof msg.busy === "boolean") setBusy(msg.busy);
});

go.addEventListener("click", submit);
tabs[0].addEventListener("click", () => show("chat")); tabs[1].addEventListener("click", () => show("connections"));
document.getElementById("pairBtn").addEventListener("click", async () => { const code = document.getElementById("pairCode").value.trim(); const r = await ask({ mythos: "app-pair", code }); if (!r?.ok) { document.getElementById("appState").textContent = r?.error || "Kopplung fehlgeschlagen"; return; } document.getElementById("pairCode").value = ""; refreshApp(); });
document.getElementById("unpairBtn").addEventListener("click", async () => { await ask({ mythos: "app-unpair" }); refreshApp(); });
input.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); submit(); }
});
window.addEventListener("unload", () => { chrome.runtime.sendMessage({ mythos: "closed", tabId }); });

boot();
