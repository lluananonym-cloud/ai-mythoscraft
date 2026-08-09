/* Inhalts-Skript: führt die Aktionen der KI auf der echten Seite aus und
   zeigt dabei einen sichtbaren Maus-Cursor, der sich bewegt und klickt. */
(() => {
  if (window.__mythosContent) return;
  window.__mythosContent = true;

  let cursorEl = null;

  function ensureCursor() {
    if (cursorEl && document.body.contains(cursorEl)) return cursorEl;
    cursorEl = document.createElement("div");
    cursorEl.setAttribute("data-mythos-cursor", "");
    cursorEl.style.cssText = [
      "position:fixed", "left:50%", "top:50%", "width:22px", "height:22px",
      "z-index:2147483647", "pointer-events:none",
      "transition:left .5s cubic-bezier(.22,.61,.36,1), top .5s cubic-bezier(.22,.61,.36,1)",
      "filter:drop-shadow(0 2px 4px rgba(0,0,0,.6))",
    ].join(";");
    cursorEl.innerHTML =
      '<svg viewBox="0 0 24 24" width="22" height="22" fill="#fff" stroke="#111" stroke-width="1.2">' +
      '<path d="M4 2l7 18 2.2-6.6L20 11z"/></svg>' +
      '<div data-ring style="position:absolute;left:-9px;top:-9px;width:40px;height:40px;border-radius:50%;' +
      'border:2px solid #7c5cff;opacity:0;transform:scale(.4);transition:all .45s ease-out"></div>';
    document.documentElement.appendChild(cursorEl);
    return cursorEl;
  }

  function moveCursor(x, y) {
    const el = ensureCursor();
    el.style.left = `${x}px`;
    el.style.top = `${y}px`;
    return new Promise((r) => setTimeout(r, 520));
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
    el.scrollIntoView({ block: "center", behavior: "smooth" });
    await new Promise((r) => setTimeout(r, 350));
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
    await new Promise((r2) => setTimeout(r2, 400));
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
      await new Promise((r) => setTimeout(r, 25));
    }
    el.dispatchEvent(new Event("change", { bubbles: true }));
    if (enter) {
      for (const type of ["keydown", "keypress", "keyup"]) {
        el.dispatchEvent(new KeyboardEvent(type, { key: "Enter", code: "Enter", keyCode: 13, which: 13, bubbles: true }));
      }
      el.form?.requestSubmit?.();
    }
    await new Promise((r) => setTimeout(r, 400));
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
        await new Promise((r) => setTimeout(r, 600));
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
    if (msg?.mythos === "act") {
      perform(msg.action)
        .then((res) => respond({ ...res, page: snapshot() }))
        .catch((e) => respond({ ok: false, info: String(e), page: snapshot() }));
      return true;
    }
    return false;
  });
})();
