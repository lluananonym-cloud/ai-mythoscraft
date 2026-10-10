// ---------- Werkzeug-Blöcke robust lesen (CLI, Desktop-App: Haupt- und Fensterprozess) ----------
// Wird roh über den Platzhalter TOOL_PARSE eingefügt. Keine Abhängigkeiten.
// Modelle schreiben Aufrufe nicht immer sauber: fehlendes </tool>, <tool_call> statt <tool>, ```json-Zäune,
// {"name":…,"arguments":{…}} statt flacher Felder, echte Zeilenumbrüche in Strings oder abgeschnittenes JSON.
// Nichts davon darf als Rohtext im Chat landen.

const TOOL_NAMES = ["run", "read", "edit", "write", "search", "ls", "websearch", "fetch", "cloud", "todo"];
const TOOL_OPEN_RE = /<(tool_call|tool_use|tool)(?:\s[^>]*)?>/i;
// Ein ganzer Block – auch nicht geschlossen (dann bis zum Textende).
const TOOL_BLOCK_RE = /<(tool_call|tool_use|tool)(?:\s[^>]*)?>[\s\S]*?(?:<\/\1\s*>|$)/gi;

/** Alle Werkzeug-Blöcke und verwaiste End-Tags entfernen. */
function stripToolBlocks(text) {
  return String(text || "").replace(TOOL_BLOCK_RE, "").replace(/<\/(?:tool_call|tool_use|tool)\s*>/gi, "");
}

/** Erstes JSON-Objekt im Text herausschneiden. Rohe Zeilenumbrüche in Strings werden escaped,
 *  abgeschnittenes JSON wird geschlossen. → { json, complete } oder null. */
function jsonObjectAt(s) {
  const start = s.indexOf("{");
  if (start < 0) return null;
  let out = "", inStr = false, esc = false;
  const stack = [];
  for (let i = start; i < s.length; i++) {
    const ch = s[i];
    if (inStr) {
      if (esc) { esc = false; out += ch; continue; }
      if (ch === "\\") { esc = true; out += ch; continue; }
      if (ch === '"') { inStr = false; out += ch; continue; }
      out += ch === "\n" ? "\\n" : ch === "\r" ? "\\r" : ch === "\t" ? "\\t" : ch;
      continue;
    }
    if (ch === '"') { inStr = true; out += ch; continue; }
    if (ch === "{" || ch === "[") stack.push(ch === "{" ? "}" : "]");
    else if (ch === "}" || ch === "]") { stack.pop(); out += ch; if (!stack.length) return { json: out, complete: true }; continue; }
    out += ch;
  }
  // Abgeschnitten: offenen String und Klammern schließen, hängendes Komma/Doppelpunkt weg.
  if (esc) out = out.slice(0, -1);
  if (inStr) out += '"';
  out = out.replace(/[,:]\s*$/, "");
  while (stack.length) out += stack.pop();
  return { json: out, complete: false };
}

/** Ersten Werkzeug-Aufruf aus einer Antwort lesen.
 *  → { tool: {name, …} | null, error: string | "", text: sichtbarer Text ohne Werkzeug-Blöcke } */
function parseToolBlock(out) {
  out = String(out || "");
  const text = stripToolBlocks(out).trim();
  const m = TOOL_OPEN_RE.exec(out);
  if (!m) return { tool: null, error: "", text };
  let body = out.slice(m.index + m[0].length);
  const end = body.search(/<\/(?:tool_call|tool_use|tool)\s*>/i);
  if (end >= 0) body = body.slice(0, end);
  body = body.replace(/```[a-z]*\s*/gi, "");
  const raw = jsonObjectAt(body);
  if (!raw) return { tool: null, error: "Im Werkzeug-Block steht kein JSON-Objekt.", text };
  let t;
  try { t = JSON.parse(raw.json); } catch (e) { return { tool: null, error: "Werkzeug-JSON ungültig (" + e.message + ").", text }; }
  if (!t || typeof t !== "object" || Array.isArray(t)) return { tool: null, error: "Werkzeug-JSON muss ein Objekt sein.", text };
  // Name auch aus <tool name="…"> oder anderen üblichen Feldern.
  const attr = m[0].match(/\bname\s*=\s*["']([^"']+)["']/i);
  if (typeof t.name !== "string") t.name = String(t.tool_name || t.function || (attr && attr[1]) || "");
  // {"name":"x","arguments":{…}} / "input" / "parameters" / "args" flach machen (bei MCP: nach "args").
  for (const k of ["arguments", "input", "parameters", "args"]) {
    const a = t[k];
    if (!a || typeof a !== "object" || Array.isArray(a)) continue;
    if (t.name === "mcp") { if (k !== "args") { delete t[k]; if (!t.args) t.args = a; } continue; }
    delete t[k]; for (const x of Object.keys(a)) if (!(x in t)) t[x] = a[x];
  }
  // mcp__server__tool (Hook-Schreibweise) → MCP-Aufruf.
  const mm = t.name.match(/^mcp__(.+?)__(.+)$/);
  if (mm) { const { name, ...rest } = t; t = { name: "mcp", server: mm[1], tool: mm[2], args: rest.args || rest }; }
  t.name = t.name.trim();
  if (!t.name) return { tool: null, error: "Im Werkzeug-Block fehlt \"name\".", text };
  // Abgeschnittene Aufrufe nicht ausführen (sonst z. B. halbe Dateien) – außer harmlose wie todo.
  if (!raw.complete && t.name !== "todo") return { tool: null, error: "Der Werkzeug-Aufruf " + t.name + " ist abgeschnitten.", text };
  return { tool: t, error: "", text };
}

/** Prüfen, ob es das Werkzeug wirklich gibt. extra = { mcp: [{ server, tools: [Namen] }], browser: bool }.
 *  → "" wenn ok, sonst eine Erklärung fürs Modell (mit der Liste, was es gibt). */
function toolProblem(t, extra) {
  extra = extra || {};
  const mcp = extra.mcp || [];
  const avail = TOOL_NAMES.concat(mcp.length ? ["mcp"] : [], extra.browser ? ["browser"] : []);
  const list = "Verfügbar sind nur: " + avail.join(", ") + (mcp.length ? " · MCP: " + mcp.map((s) => s.server + " (" + s.tools.join(", ") + ")").join("; ") : " · keine MCP-Connectoren verbunden") + ".";
  const tail = " Erfinde keine Werkzeuge. Wenn etwas damit nicht geht, sag das dem Nutzer kurz und mach mit dem Rest weiter.";
  if (t.name === "mcp") {
    const s = mcp.find((x) => x.server === t.server) || mcp.find((x) => String(x.server).toLowerCase() === String(t.server || "").toLowerCase());
    if (!s) return "FEHLER: MCP-Server „" + (t.server || "?") + "“ ist nicht verbunden. " + list + tail;
    t.server = s.server;
    if (!s.tools.includes(t.tool)) return "FEHLER: MCP-Werkzeug „" + t.tool + "“ gibt es bei " + s.server + " nicht. " + list + tail;
    return "";
  }
  if (t.name === "browser") return extra.browser ? "" : "FEHLER: Das Browser-Plugin ist nicht verbunden. " + list + tail;
  return TOOL_NAMES.includes(t.name) ? "" : "FEHLER: Werkzeug „" + t.name + "“ gibt es nicht. " + list + tail;
}
