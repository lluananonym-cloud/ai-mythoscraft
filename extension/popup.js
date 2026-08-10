const log = document.getElementById("log");
const input = document.getElementById("in");
const go = document.getElementById("go");
const status = document.getElementById("status");

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
}

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
input.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); submit(); }
});
window.addEventListener("unload", () => { chrome.runtime.sendMessage({ mythos: "closed", tabId }); });

boot();
