/* Inhalts-Skript: führt die Aktionen der KI auf der echten Seite aus und
   zeigt dabei einen sichtbaren Maus-Cursor, der sich bewegt und klickt. */
(() => {
  if (window.__mythosContent) return;
  window.__mythosContent = true;

  let cursorEl = null;

  function ensureCursor() {
    // Doppelte Cursor (z.B. nach erneuter Injektion) konsequent entfernen
    const all = Array.from(document.querySelectorAll("[data-mythos-cursor]"));
    if (all.length) {
      cursorEl = all[0];
      all.slice(1).forEach((el) => el.remove());
      return cursorEl;
    }

    cursorEl = document.createElement("div");
    cursorEl.setAttribute("data-mythos-cursor", "");
    cursorEl.style.cssText = "position:fixed;left:50%;top:50%;width:30px;height:30px;z-index:2147483647;pointer-events:none;" +
      "transition:left .32s cubic-bezier(.22,.61,.36,1),top .32s cubic-bezier(.22,.61,.36,1);filter:drop-shadow(0 0 8px rgba(139,92,246,.75)) drop-shadow(0 2px 3px rgba(0,0,0,.5))";
    cursorEl.innerHTML =
      '<svg viewBox="0 0 30 30" width="30" height="30"><defs><linearGradient id="mxg" x1="0" y1="0" x2="1" y2="1">' +
      '<stop offset="0" stop-color="#22d3ee"/><stop offset=".55" stop-color="#8b5cf6"/><stop offset="1" stop-color="#ec4899"/></linearGradient></defs>' +
      '<path d="M5 3l18 8.2-7.4 2.3L12.6 21z" fill="url(#mxg)" stroke="#fff" stroke-width="1.6" stroke-linejoin="round"/></svg>' +
      '<div data-tag style="position:absolute;left:24px;top:22px;padding:2px 8px;border-radius:999px;font:600 11px/1.6 system-ui,sans-serif;color:#fff;white-space:nowrap;' +
      'background:linear-gradient(90deg,#22d3ee,#8b5cf6,#ec4899);box-shadow:0 2px 10px rgba(0,0,0,.35)">Mythos</div>' +
      '<div data-ring style="position:absolute;left:-12px;top:-12px;width:40px;height:40px;border-radius:50%;' +
      'border:2px solid #a78bfa;box-shadow:0 0 14px #8b5cf6;opacity:0;transform:scale(.4);transition:all .4s ease-out"></div>';
    document.documentElement.appendChild(cursorEl);
    return cursorEl;
  }

  /* Leuchtender Rahmen in Logo-Farben + Hinweis, solange Mythos AI Zugriff hat. */
  function setFrame(on) {
    let f = document.querySelector("[data-mythos-frame]");
    if (!on) { f?.remove(); return; }
    if (f) return;
    f = document.createElement("div");
    f.setAttribute("data-mythos-frame", "");
    f.style.cssText = "position:fixed;inset:0;z-index:2147483646;pointer-events:none;border-radius:0;" +
      "box-shadow:inset 0 0 0 3px #8b5cf6,inset 0 0 28px 6px rgba(139,92,246,.55),inset 0 0 60px 10px rgba(34,211,238,.25);animation:mythosGlow 2.4s ease-in-out infinite";
    f.innerHTML = '<style>@keyframes mythosGlow{0%,100%{box-shadow:inset 0 0 0 3px #22d3ee,inset 0 0 28px 6px rgba(34,211,238,.5),inset 0 0 60px 10px rgba(139,92,246,.25)}50%{box-shadow:inset 0 0 0 3px #ec4899,inset 0 0 30px 8px rgba(236,72,153,.5),inset 0 0 64px 12px rgba(139,92,246,.35)}}</style>' +
      '<div style="position:absolute;top:10px;left:50%;transform:translateX(-50%);padding:6px 14px;border-radius:999px;font:600 12px/1.4 system-ui,sans-serif;color:#fff;' +
      'background:rgba(10,10,10,.82);backdrop-filter:blur(12px);border:1px solid rgba(255,255,255,.18);box-shadow:0 0 18px rgba(139,92,246,.6)">✦ Mythos AI hat Zugriff auf den Browser</div>';
    document.documentElement.appendChild(f);
  }

  function moveCursor(x, y) {
    const el = ensureCursor();
    el.style.left = `${x}px`;
    el.style.top = `${y}px`;
    return new Promise((r) => setTimeout(r, 330));
  }

  function ripple() {
    const ring = ensureCursor().querySelector("[data-ring]");
    if (!ring) return;
    ring.style.opacity = "1";
    ring.style.transform = "scale(.4)";
    requestAnimationFrame(() => {
      ring.style.transform = "scale(1.3)";
      ring.style.opacity = "0";
    });
  }

  const visible = (el) => {
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    return r.width > 2 && r.height > 2 && s.visibility !== "hidden" && s.display !== "none" && s.opacity !== "0";
  };

  function label(el) {
    return (
      el.getAttribute("aria-label") ||
      el.getAttribute("placeholder") ||
      el.getAttribute("name") ||
      el.value ||
      (el.innerText || "").trim() ||
      el.getAttribute("title") ||
      ""
    ).replace(/\s+/g, " ").trim();
  }

  function interactive() {
    const nodes = Array.from(
      document.querySelectorAll("a[href], button, input, textarea, select, [role=button], [role=link], [onclick]"),
    ).filter(visible);
    return nodes.slice(0, 120);
  }

  function snapshot() {
    const els = interactive().map((el, index) => ({
      index,
      tag: el.tagName.toLowerCase(),
      type: el.getAttribute("type") || "",
      label: label(el).slice(0, 100),
    }));
    return {
      url: location.href,
      title: document.title,
      text: (document.body?.innerText || "").replace(/\s+\n/g, "\n").slice(0, 8000),
      elements: els,
    };
  }

  function byIndex(i) {
    return interactive()[i] || null;
  }

  function byText(text) {
    const t = String(text || "").toLowerCase();
    return (
      interactive().find((el) => label(el).toLowerCase() === t) ||
      interactive().find((el) => label(el).toLowerCase().includes(t)) ||
      null
    );
  }

  async function pointAt(el) {
    const rr = el.getBoundingClientRect();
    if (rr.top < 0 || rr.bottom > innerHeight) { el.scrollIntoView({ block: "center", behavior: "instant" }); await new Promise((r) => setTimeout(r, 60)); }
    const r = el.getBoundingClientRect();
    await moveCursor(r.left + r.width / 2, r.top + r.height / 2);
  }

  async function realClick(el) {
    await pointAt(el);
    ripple();
    const r = el.getBoundingClientRect();
    const opts = { bubbles: true, cancelable: true, view: window, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
    el.dispatchEvent(new PointerEvent("pointerdown", { ...opts, pointerId: 1, isPrimary: true }));
    el.dispatchEvent(new MouseEvent("mousedown", opts));
    el.focus?.();
    el.dispatchEvent(new PointerEvent("pointerup", { ...opts, pointerId: 1, isPrimary: true }));
    el.dispatchEvent(new MouseEvent("mouseup", opts));
    el.dispatchEvent(new MouseEvent("click", opts));
    await new Promise((r2) => setTimeout(r2, 150));
  }

  async function typeInto(el, text, enter) {
    await pointAt(el);
    ripple();
    el.focus?.();
    const setter = Object.getOwnPropertyDescriptor(
      el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype,
      "value",
    )?.set;
    let acc = "";
    for (const ch of String(text)) {
      acc += ch;
      if (setter) setter.call(el, acc);
      else el.textContent = acc;
      el.dispatchEvent(new Event("input", { bubbles: true }));
      await new Promise((r) => setTimeout(r, 8));
    }
    el.dispatchEvent(new Event("change", { bubbles: true }));
    if (enter) {
      for (const type of ["keydown", "keypress", "keyup"]) {
        el.dispatchEvent(new KeyboardEvent(type, { key: "Enter", code: "Enter", keyCode: 13, which: 13, bubbles: true }));
      }
      el.form?.requestSubmit?.();
    }
    await new Promise((r) => setTimeout(r, 150));
  }

  async function perform(action) {
    switch (action.type) {
      case "click": {
        const el = action.index != null ? byIndex(action.index) : byText(action.text);
        if (!el) return { ok: false, info: `Element nicht gefunden (${action.index ?? action.text})` };
        await realClick(el);
        return { ok: true, info: `geklickt: ${label(el).slice(0, 60)}` };
      }
      case "type": {
        const el = action.index != null ? byIndex(action.index) : byText(action.text ? action.selectorText || action.text : "");
        if (!el) return { ok: false, info: "Eingabefeld nicht gefunden" };
        await typeInto(el, action.text ?? "", !!action.enter);
        return { ok: true, info: `getippt: ${String(action.text ?? "").slice(0, 40)}` };
      }
      case "scroll": {
        window.scrollBy({ top: action.amount ?? 700, behavior: "smooth" });
        await new Promise((r) => setTimeout(r, 350));
        return { ok: true, info: "gescrollt" };
      }
      case "wait": {
        await new Promise((r) => setTimeout(r, Math.min(action.ms ?? 800, 5000)));
        return { ok: true, info: "gewartet" };
      }
      case "read":
        return { ok: true, info: "Seite gelesen" };
      default:
        return { ok: false, info: `unbekannte Aktion: ${action.type}` };
    }
  }

  chrome.runtime.onMessage.addListener((msg, _s, respond) => {
    if (msg?.mythos === "snapshot") { respond(snapshot()); return true; }
    if (msg?.mythos === "frame") { setFrame(!!msg.on); if (!msg.on) document.querySelector("[data-mythos-cursor]")?.remove(), (cursorEl = null); respond({ ok: true }); return true; }
    if (msg?.mythos === "act") {
      perform(msg.action)
        .then((res) => respond({ ...res, page: snapshot() }))
        .catch((e) => respond({ ok: false, info: String(e), page: snapshot() }));
      return true;
    }
    return false;
  });
})();
