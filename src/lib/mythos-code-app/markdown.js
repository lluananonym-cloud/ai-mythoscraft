// Markdown -> DOM, ohne innerHTML (sicher gegen eingeschleustes HTML).
// renderMarkdown(box, text, openLink) füllt `box` mit Überschriften, Listen, Code, Tabellen, Links …
(function () {
  const mk = (tag, cls, text) => { const d = document.createElement(tag); if (cls) d.className = cls; if (text != null) d.textContent = text; return d; };

  function inline(parent, text, openLink) {
    const re = /(`+)([\s\S]*?)\1|\*\*([\s\S]+?)\*\*|~~([\s\S]+?)~~|(?<![\w*])\*([^*\s][^*]*?)\*(?![\w*])|(?<![\w])_([^_\s][^_]*?)_(?![\w])|\[([^\]]+)\]\(([^)\s]+)\)|(https?:\/\/[^\s<>()]+[^\s<>().,;:!?'"])/g;
    let last = 0, m;
    while ((m = re.exec(text))) {
      if (m.index > last) parent.appendChild(document.createTextNode(text.slice(last, m.index)));
      last = re.lastIndex;
      if (m[1]) parent.appendChild(mk("code", "ic", m[2]));
      else if (m[3] != null) inline(parent.appendChild(mk("strong")), m[3], openLink);
      else if (m[4] != null) inline(parent.appendChild(mk("s")), m[4], openLink);
      else if (m[5] != null) inline(parent.appendChild(mk("em")), m[5], openLink);
      else if (m[6] != null) inline(parent.appendChild(mk("em")), m[6], openLink);
      else {
        const label = m[7] != null ? m[7] : m[9], url = m[8] != null ? m[8] : m[9];
        const a = mk("a", "", label); a.href = "#"; a.title = url;
        a.onclick = (e) => { e.preventDefault(); if (/^https?:\/\//i.test(url)) openLink(url); };
        parent.appendChild(a);
      }
    }
    if (last < text.length) parent.appendChild(document.createTextNode(text.slice(last)));
  }

  function codeBlock(lang, code) {
    const wrap = mk("div", "codeblock");
    const head = mk("div", "codehead");
    head.appendChild(mk("span", "", lang || "code"));
    const copy = mk("button", "copy", "Kopieren");
    copy.onclick = () => { navigator.clipboard.writeText(code).then(() => { copy.textContent = "✓ Kopiert"; setTimeout(() => (copy.textContent = "Kopieren"), 1500); }); };
    head.appendChild(copy);
    wrap.append(head, mk("pre", "", code));
    return wrap;
  }

  // ```datei:name.ext (oder file:…, ;base64) → Datei-Karte zum Speichern.
  const fileInfo = (info) => {
    const m = /^(?:datei|file)[:=]"?([^\s";]+\.[a-zA-Z0-9]{1,6})"?(;base64)?/i.exec(info || "");
    if (!m) return null;
    const name = m[1].split(/[\\/]/).filter((p) => p && p !== "..").join("/");
    return name ? { name: name, base64: !!m[2] } : null;
  };
  const fmtSize = (n) => (n < 1024 ? n + " B" : n < 1048576 ? (n / 1024).toFixed(1) + " KB" : (n / 1048576).toFixed(1) + " MB");

  function fileCard(f, content) {
    const card = mk("div", "filecard");
    const ext = (/\.([a-zA-Z0-9]+)$/.exec(f.name) || [])[1] || "";
    const bytes = f.base64 ? Math.floor(content.replace(/\s+/g, "").length * 3 / 4) : new TextEncoder().encode(content).length;
    const icon = mk("div", "fileicon", ext.toUpperCase().slice(0, 4) || "DATEI");
    const info = mk("div", "fileinfo");
    info.append(mk("div", "filename", f.name), mk("div", "filemeta", fmtSize(bytes)));
    const acts = mk("div", "fileacts");
    const file = { name: f.name, content: content, base64: f.base64 };
    if (!f.base64) {
      const view = mk("button", "", "Ansehen");
      let pre = null;
      view.onclick = () => { if (pre) { pre.remove(); pre = null; view.textContent = "Ansehen"; return; } pre = mk("pre", "filepre", content); card.appendChild(pre); view.textContent = "Schließen"; };
      const copy = mk("button", "", "Kopieren");
      copy.onclick = () => { navigator.clipboard.writeText(content).then(() => { copy.textContent = "✓ Kopiert"; setTimeout(() => (copy.textContent = "Kopieren"), 1500); }); };
      acts.append(view, copy);
    }
    const save = mk("button", "primary", "Speichern");
    save.onclick = async () => {
      const r = window.mythos && window.mythos.files.saveAs ? await window.mythos.files.saveAs(file, window.mythosFolder && window.mythosFolder()) : { error: "nicht verfügbar" };
      if (r && r.ok) { save.textContent = "✓ Gespeichert"; setTimeout(() => (save.textContent = "Speichern"), 2000); }
      else if (r && r.error) save.textContent = "Fehler: " + r.error;
    };
    acts.appendChild(save);
    card.append(icon, info, acts);
    card._file = file;
    return card;
  }

  // Mehrere Dateien in einer Antwort → „Alle speichern“ in einen Ordner.
  function saveAllBar(box) {
    const files = [...box.querySelectorAll(":scope > .filecard")].map((c) => c._file);
    if (files.length < 2) return;
    const bar = mk("div", "filesall"), b = mk("button", "", "Alle " + files.length + " Dateien in Ordner speichern");
    b.onclick = async () => {
      const r = await window.mythos.files.saveAll(files, window.mythosFolder && window.mythosFolder());
      if (r && r.ok) b.textContent = "✓ " + r.count + " Dateien gespeichert"; else if (r && r.error) b.textContent = "Fehler: " + r.error;
    };
    bar.appendChild(b); box.appendChild(bar);
  }

  const cells = (row) => row.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim());

  window.renderMarkdown = function (box, text, openLink) {
    openLink = openLink || (() => {});
    const lines = String(text).replace(/\r\n/g, "\n").split("\n");
    let i = 0, para = [];
    const flush = () => { if (!para.length) return; const p = mk("p"); inline(p, para.join("\n"), openLink); box.appendChild(p); para = []; };
    while (i < lines.length) {
      const line = lines[i];
      const fence = line.match(/^\s*(```+|~~~+)\s*(\S*)/);
      if (fence) {
        flush();
        const code = []; i++;
        while (i < lines.length && !lines[i].trim().startsWith(fence[1])) code.push(lines[i++]);
        i++;
        const f = fileInfo(fence[2]);
        box.appendChild(f ? fileCard(f, code.join("\n")) : codeBlock(/^[\w+#.-]*/.exec(fence[2])[0], code.join("\n")));
        continue;
      }
      const h = line.match(/^(#{1,6})\s+(.*)$/);
      if (h) { flush(); const e = mk("h" + Math.min(6, h[1].length + 2)); inline(e, h[2], openLink); box.appendChild(e); i++; continue; }
      if (/^\s*([-*_])(\s*\1){2,}\s*$/.test(line)) { flush(); box.appendChild(mk("hr")); i++; continue; }
      if (/^\s*>/.test(line)) {
        flush(); const q = []; while (i < lines.length && /^\s*>/.test(lines[i])) q.push(lines[i++].replace(/^\s*>\s?/, ""));
        const bq = mk("blockquote"); window.renderMarkdown(bq, q.join("\n"), openLink); box.appendChild(bq); continue;
      }
      if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length && /^\s*\|?\s*:?-{2,}/.test(lines[i + 1])) {
        flush();
        const table = mk("table"), head = mk("tr");
        cells(line).forEach((c) => inline(head.appendChild(mk("th")), c, openLink));
        table.appendChild(head); i += 2;
        while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) {
          const tr = mk("tr"); cells(lines[i++]).forEach((c) => inline(tr.appendChild(mk("td")), c, openLink)); table.appendChild(tr);
        }
        const sc = mk("div", "tablewrap"); sc.appendChild(table); box.appendChild(sc); continue;
      }
      const li = line.match(/^\s*([-*+]|\d+[.)])\s+(.*)$/);
      if (li) {
        flush();
        const ordered = /\d/.test(li[1]), list = mk(ordered ? "ol" : "ul");
        while (i < lines.length) {
          const m = lines[i].match(/^\s*([-*+]|\d+[.)])\s+(.*)$/);
          if (!m || /\d/.test(m[1]) !== ordered) break;
          const item = mk("li"); const task = m[2].match(/^\[([ xX])\]\s+(.*)$/);
          if (task) { item.appendChild(mk("span", "task", task[1] === " " ? "☐ " : "☑ ")); inline(item, task[2], openLink); } else inline(item, m[2], openLink);
          list.appendChild(item); i++;
          while (i < lines.length && /^\s{2,}\S/.test(lines[i]) && !/^\s*([-*+]|\d+[.)])\s+/.test(lines[i])) { item.appendChild(document.createTextNode(" " + lines[i].trim())); i++; }
        }
        box.appendChild(list); continue;
      }
      if (!line.trim()) { flush(); i++; continue; }
      para.push(line); i++;
    }
    flush();
    saveAllBar(box);
  };
})();
