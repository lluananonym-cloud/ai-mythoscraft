// Dateien, die Mythos im Chat „schickt“: Codeblöcke mit ```datei:name.ext (oder file:…).
// Alles läuft im Browser – keine Edge Function nötig.

export type ChatFile = { name: string; base64: boolean };

/** Liest die Info-Zeile eines Codeblocks: "datei:bericht.csv", "file:logo.png;base64", "csv datei=bericht.csv". */
export function parseFileInfo(info: string): ChatFile | null {
  const m = /(?:^|\s)(?:datei|file|title)[:=]"?([^\s";]+\.[a-zA-Z0-9]{1,6})"?(;base64)?/i.exec(info || "");
  if (!m) return null;
  const name = m[1].split(/[\\/]/).filter(p => p && p !== "..").join("/");
  return name ? { name, base64: !!m[2] || /(?:^|\s)base64(?:\s|$)/i.test(info) } : null;
}

const MIME: Record<string, string> = {
  html: "text/html", htm: "text/html", css: "text/css", js: "text/javascript", mjs: "text/javascript",
  json: "application/json", csv: "text/csv", tsv: "text/tab-separated-values", md: "text/markdown", txt: "text/plain",
  xml: "application/xml", svg: "image/svg+xml", png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg",
  gif: "image/gif", webp: "image/webp", ico: "image/x-icon", pdf: "application/pdf", zip: "application/zip",
  ics: "text/calendar", vcf: "text/vcard", yml: "text/yaml", yaml: "text/yaml", py: "text/x-python",
  mp3: "audio/mpeg", wav: "audio/wav",
};

export const extOf = (name: string) => (/\.([a-zA-Z0-9]+)$/.exec(name)?.[1] || "").toLowerCase();
export const mimeOf = (name: string) => MIME[extOf(name)] || "text/plain";
export const isImageName = (name: string) => /^(png|jpe?g|gif|webp|svg|ico)$/.test(extOf(name));

/** Inhalt → Blob. Base64-Inhalte werden dekodiert (Bilder, PDFs, ZIPs …), Text bekommt UTF-8. */
export function fileBlob(name: string, content: string, base64: boolean): Blob {
  const type = mimeOf(name);
  if (base64) {
    const clean = content.replace(/^data:[^,]*,/, "").replace(/\s+/g, "");
    const bin = atob(clean);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new Blob([bytes], { type });
  }
  // BOM für CSV, damit Excel Umlaute richtig anzeigt.
  const text = extOf(name) === "csv" ? "﻿" + content : content;
  return new Blob([text], { type: type.startsWith("text/") || type.includes("json") || type.includes("xml") || type.includes("svg") ? `${type};charset=utf-8` : type });
}

export function saveBlob(name: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = name.split("/").pop() || name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

export function openBlob(blob: Blob) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.target = "_blank"; a.rel = "noopener";
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** SVG → PNG (für Logos, Icons, Grafiken, die Mythos als SVG zeichnet). */
export async function svgToPng(svg: string, scale = 2): Promise<Blob> {
  const url = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml;charset=utf-8" }));
  try {
    const img = new Image();
    await new Promise<void>((ok, fail) => { img.onload = () => ok(); img.onerror = () => fail(new Error("SVG ungültig")); img.src = url; });
    const vb = /viewBox="[\d.\s-]*?([\d.]+)\s+([\d.]+)"/.exec(svg);
    const w = img.naturalWidth || Number(vb?.[1]) || 512, h = img.naturalHeight || Number(vb?.[2]) || 512;
    const c = document.createElement("canvas");
    c.width = Math.round(w * scale); c.height = Math.round(h * scale);
    c.getContext("2d")!.drawImage(img, 0, 0, c.width, c.height);
    return await new Promise<Blob>((ok, fail) => c.toBlob(b => (b ? ok(b) : fail(new Error("PNG fehlgeschlagen"))), "image/png"));
  } finally { URL.revokeObjectURL(url); }
}

export const fmtSize = (n: number) => (n < 1024 ? `${n} B` : n < 1024 * 1024 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`);

/** Anweisung an das Modell, wie es Dateien schickt (Web-Chat und Code-App nutzen denselben Text). */
export const FILE_PROMPT =
  "## Dateien schicken\n" +
  "Du kannst dem Nutzer echte Dateien zum Herunterladen schicken. Wenn er eine Datei, ein Dokument, eine Tabelle, ein Skript, eine Webseite, ein Logo o. Ä. haben will, " +
  "schreibe den kompletten Inhalt in einen Codeblock, dessen erste Zeile so aussieht:\n" +
  "```datei:dateiname.endung\n…Inhalt…\n```\n" +
  "Der Chat zeigt daraus eine Datei-Karte mit Download. Regeln:\n" +
  "- Sinnvoller Dateiname mit Endung, z. B. datei:bewerbung.md, datei:umsatz.csv, datei:index.html, datei:logo.svg, datei:termin.ics, datei:projekt/app.js (Ordner erlaubt).\n" +
  "- Immer der vollständige Inhalt, nie gekürzt oder mit Platzhaltern.\n" +
  "- Tabellen als .csv (Semikolon als Trenner, öffnet sich in Excel), Bilder/Logos/Grafiken als .svg (lässt sich als PNG speichern), Dokumente als .md oder .html.\n" +
  "- Mehrere Dateien = mehrere Blöcke; der Nutzer kann alle zusammen als ZIP laden. Für ein ganzes Projekt einfach alle Dateien mit Ordnerpfad schicken.\n" +
  "- Kleine Binärdateien nur, wenn du die Bytes sicher kennst: ```datei:name.png;base64 und dann reines Base64.\n" +
  "- Normale Code-Beispiele zum Erklären bleiben normale Codeblöcke ohne datei:.";
