// Mythos Cloud-PC: Ergebnis weitergeben – Website aus dem Dateisystem zusammensetzen und Projekt als ZIP packen.
import { Vfs, norm, dirname } from "./vfs";

/** HTML-Datei mit ihren lokalen CSS-, JS- und Bild-Dateien zu einer einzigen Seite zusammensetzen. */
export function buildPage(fs: Vfs, path: string): string {
  const base = dirname(path);
  let html = fs.read(path);
  const local = (ref: string) => {
    if (/^(https?:|data:|\/\/|#|mailto:)/i.test(ref)) return null;
    const p = norm(ref.split(/[?#]/)[0], base);
    return fs.isFile(p) ? p : null;
  };
  html = html.replace(/<link\b[^>]*href=["']([^"']+)["'][^>]*>/gi, (tag, href) => {
    if (!/stylesheet/i.test(tag)) return tag;
    const p = local(href);
    return p ? `<style data-file="${p}">\n${fs.read(p)}\n</style>` : tag;
  });
  html = html.replace(/<script\b([^>]*)\bsrc=["']([^"']+)["']([^>]*)>\s*<\/script>/gi, (tag, pre, src, post) => {
    const p = local(src);
    return p ? `<script${pre}${post} data-file="${p}">\n${fs.read(p).replace(/<\/script/gi, "<\\/script")}\n</script>` : tag;
  });
  html = html.replace(/(<img\b[^>]*\bsrc=["'])([^"']+)(["'])/gi, (tag, a, src, b) => {
    const p = local(src);
    if (!p || !/\.svg$/i.test(p)) return tag;
    return a + "data:image/svg+xml;charset=utf-8," + encodeURIComponent(fs.read(p)) + b;
  });
  // Links auf andere Seiten des Projekts an den PC-Browser melden statt ins Leere zu laufen.
  const bridge = `<script>document.addEventListener("click",function(e){var a=e.target.closest&&e.target.closest("a[href]");if(!a)return;var h=a.getAttribute("href");if(!h||/^(https?:|mailto:|#|javascript:)/i.test(h))return;e.preventDefault();parent.postMessage({mythosPcOpen:h,from:${JSON.stringify(path)}},"*")});</script>`;
  html = /<\/body>/i.test(html) ? html.replace(/<\/body>/i, bridge + "</body>") : html + bridge;
  // JavaScript-Fehler der Seite an den PC melden, damit der Agent sie sieht und beheben kann.
  const errors = `<script>(function(){function s(m){try{parent.postMessage({mythosPcError:String(m).slice(0,400)},"*")}catch(_){}}window.addEventListener("error",function(e){s((e.message||"Fehler")+(e.lineno?" (Zeile "+e.lineno+")":""))});window.addEventListener("unhandledrejection",function(e){s("Promise: "+(e.reason&&e.reason.message||e.reason))});var ce=console.error;console.error=function(){s(Array.prototype.join.call(arguments," "));ce.apply(console,arguments)}})();</script>`;
  const head = html.match(/<head[^>]*>/i);
  return head ? html.replace(head[0], head[0] + errors) : errors + html;
}

// ---------- ZIP (ohne Bibliothek, unkomprimiert) ----------
const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(b: Uint8Array) {
  let c = 0xffffffff;
  for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

export function makeZip(files: [string, string][]): Blob {
  const enc = new TextEncoder();
  const parts: Uint8Array[] = [];
  const central: Uint8Array[] = [];
  let offset = 0;
  const d = new Date();
  const time = (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1);
  const date = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  for (const [path, content] of files) {
    const name = enc.encode(path.replace(/^\//, ""));
    const data = enc.encode(content);
    const crc = crc32(data);
    const head = new DataView(new ArrayBuffer(30));
    head.setUint32(0, 0x04034b50, true); head.setUint16(4, 20, true); head.setUint16(6, 0x0800, true);
    head.setUint16(10, time, true); head.setUint16(12, date, true);
    head.setUint32(14, crc, true); head.setUint32(18, data.length, true); head.setUint32(22, data.length, true);
    head.setUint16(26, name.length, true);
    const cen = new DataView(new ArrayBuffer(46));
    cen.setUint32(0, 0x02014b50, true); cen.setUint16(4, 20, true); cen.setUint16(6, 20, true); cen.setUint16(8, 0x0800, true);
    cen.setUint16(12, time, true); cen.setUint16(14, date, true);
    cen.setUint32(16, crc, true); cen.setUint32(20, data.length, true); cen.setUint32(24, data.length, true);
    cen.setUint16(28, name.length, true); cen.setUint32(42, offset, true);
    parts.push(new Uint8Array(head.buffer), name, data);
    central.push(new Uint8Array(cen.buffer), name);
    offset += 30 + name.length + data.length;
  }
  const size = central.reduce((s, c) => s + c.length, 0);
  const end = new DataView(new ArrayBuffer(22));
  end.setUint32(0, 0x06054b50, true);
  end.setUint16(8, files.length, true); end.setUint16(10, files.length, true);
  end.setUint32(12, size, true); end.setUint32(16, offset, true);
  return new Blob([...parts, ...central, new Uint8Array(end.buffer)], { type: "application/zip" });
}

export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}
