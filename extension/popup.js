const log = document.getElementById("log");
const input = document.getElementById("in");
const go = document.getElementById("go");
const status = document.getElementById("status");

const ask = (msg) => new Promise((r) => chrome.runtime.sendMessage(msg, r));

function add(text, cls) {
  const el = document.createElement("div");
  el.className = cls === "act" ? "act" : `m ${cls}`;
  el.textContent = text;
  log.appendChild(el);
  log.scrollTop = log.scrollHeight;
  return el;
}

let history = [];
let running = false;

async function run(task) {
  running = true;
  go.disabled = true;
  status.textContent = "arbeitet…";
  add(task, "user");
  history.push({ role: "user", content: task });

  try {
    let page = await ask({ mythos: "page" });

    for (let step = 0; step < 12; step++) {
      const out = await ask({ mythos: "brain", task, history, page });
      if (!out) { add("Keine Antwort vom Server.", "m ai err"); break; }
      if (out.say) add(out.say, "ai");
      history.push({ role: "assistant", content: JSON.stringify({ say: out.say, actions: out.actions }) });

      if (out.done || !out.actions?.length) break;

      for (const action of out.actions) {
        status.textContent = action.type;
        const res = await ask({ mythos: "run", action });
        add(`› ${action.type}: ${res?.info || "keine Rückmeldung"}`, "act");
        if (res?.page) page = res.page;
      }
      if (!page) page = await ask({ mythos: "page" });
      history.push({ role: "user", content: "Aktionen ausgeführt. Hier ist der neue Seiten-Kontext." });
      history = history.slice(-10);
    }
  } catch (e) {
    add(`Fehler: ${e.message || e}`, "m ai err");
  } finally {
    running = false;
    go.disabled = false;
    status.textContent = "bereit";
  }
}

function submit() {
  const t = input.value.trim();
  if (!t || running) return;
  input.value = "";
  run(t);
}

go.addEventListener("click", submit);
input.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); submit(); }
});
input.focus();
