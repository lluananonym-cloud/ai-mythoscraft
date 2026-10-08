// Mythos Labs: gemeinsame Helfer für Live-Arena, Welt-Modus, Foto zu App, KI-Team,
// Tagesrückblick und Mythos-Marktplatz. Der Server-Teil liegt in supabase/functions/labs.
import { supabase } from "@/integrations/supabase/client";

export type ApiMsg = { role: "user" | "assistant"; content: string | { type: string; text?: string; image_url?: { url: string } }[] };

async function labs<T = Record<string, unknown>>(body: Record<string, unknown>): Promise<T> {
  const { data, error } = await supabase.functions.invoke("labs", { body });
  if (error) {
    // Fehlermeldung des Servers (z. B. "Bitte zuerst anmelden.") statt "non-2xx status code" zeigen.
    const ctx = (error as { context?: Response }).context;
    const msg = ctx && typeof ctx.json === "function" ? (await ctx.json().catch(() => null))?.error : null;
    throw new Error(msg || error.message || "Verbindungsfehler");
  }
  if (data?.error) throw new Error(data.error);
  return data as T;
}

/** Fallback, falls die Labs-Funktion (noch) nicht bereitsteht: normale Chat-Funktion, Antwort einsammeln. */
async function askViaChat(system: string, messages: ApiMsg[]): Promise<string> {
  const { data: { session } } = await supabase.auth.getSession();
  const first = messages[0];
  const withSystem: ApiMsg[] = system && first && typeof first.content === "string"
    ? [{ ...first, content: `${system}\n\n${first.content}` }, ...messages.slice(1)]
    : messages;
  const r = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/functions/v1/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}` },
    body: JSON.stringify({ userId: session?.user.id, messages: withSystem, mode: "chat", mythos: "v1:normal" }),
  });
  if (!r.ok || !r.body) throw new Error(r.status === 429 ? "Gerade zu viele Anfragen, bitte kurz warten." : "KI nicht erreichbar");
  const reader = r.body.getReader();
  const dec = new TextDecoder();
  let buf = "", out = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true });
    let i;
    while ((i = buf.indexOf("\n")) !== -1) {
      const line = buf.slice(0, i).trim(); buf = buf.slice(i + 1);
      if (!line.startsWith("data: ") || line === "data: [DONE]") continue;
      try { out += JSON.parse(line.slice(6)).choices?.[0]?.delta?.content || ""; } catch { /* Teilzeile */ }
    }
  }
  return out;
}

/** Ein KI-Aufruf ohne Streaming. */
export async function askAI(system: string, messages: ApiMsg[], model?: string): Promise<string> {
  try {
    return (await labs<{ text: string }>({ kind: "ask", system, messages, model })).text;
  } catch (e) {
    const msg = (e as Error).message || "";
    if (/anmelden|zu viele|Guthaben/i.test(msg)) throw e;
    return askViaChat(system, messages);
  }
}

/** HTML-Dokument aus einer KI-Antwort holen (mit oder ohne ```html-Block). */
export function extractHtml(text: string): string {
  const fence = text.match(/```(?:html)?\s*\n([\s\S]*?)```/i);
  let html = (fence ? fence[1] : text).trim();
  const start = html.search(/<!doctype html|<html/i);
  if (start > 0) html = html.slice(start);
  const end = html.toLowerCase().lastIndexOf("</html>");
  if (end > 0) html = html.slice(0, end + 7);
  return /<html|<body|<div|<script/i.test(html) ? html : "";
}

export function extractJson<T>(text: string): T | null {
  const m = text.match(/\{[\s\S]*\}/);
  try { return JSON.parse(m ? m[0] : text) as T; } catch { return null; }
}

export const APP_RULES = `Erzeuge EIN vollständiges, sofort lauffähiges HTML-Dokument (beginnend mit <!DOCTYPE html>), alles in einer Datei mit <style> und <script>.
- Keine externen Bibliotheken außer bei Bedarf von https://cdn.jsdelivr.net.
- Mobil zuerst: <meta name="viewport" content="width=device-width, initial-scale=1">, große Touch-Buttons (mind. 44px), nichts überlappt, kein horizontales Scrollen.
- Modernes, dunkles Design mit klaren Farben, gut lesbar.
- Bedienung über Buttons statt Texteingabe-Befehle.
- Daten bei Bedarf in localStorage speichern, aber die App muss auch ohne gespeicherte Daten starten.
- KEINE Platzhalter, KEINE TODOs, alles muss funktionieren.
Antworte NUR mit dem HTML.`;

/** Bild für Tagesrückblick usw.: erst über den Server, sonst direkt im Browser bei Pollinations. */
export async function makeImage(prompt: string): Promise<string> {
  try {
    return (await labs<{ url: string }>({ kind: "image", prompt })).url;
  } catch {
    const seed = Math.floor(Math.random() * 1e6);
    return `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt.slice(0, 800))}?model=flux&width=768&height=768&nologo=true&referrer=mythoscraft&seed=${seed}`;
  }
}

/** Foto verkleinern, damit es schnell hochgeht (max. 1280 px, JPEG). */
export function shrinkImage(file: File, max = 1280): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const s = Math.min(1, max / Math.max(img.width, img.height));
      const c = document.createElement("canvas");
      c.width = Math.round(img.width * s); c.height = Math.round(img.height * s);
      c.getContext("2d")!.drawImage(img, 0, 0, c.width, c.height);
      URL.revokeObjectURL(url);
      resolve(c.toDataURL("image/jpeg", 0.85));
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("Bild konnte nicht gelesen werden")); };
    img.src = url;
  });
}

// ---------- Eigene Apps (aus Foto zu App, KI-Team und Marktplatz) ----------
export type MyApp = { id: string; title: string; html: string; source: "foto" | "team" | "markt"; created_at: string };
const APPS_KEY = "mythos.labs.apps";

export function listMyApps(): MyApp[] {
  try { return JSON.parse(localStorage.getItem(APPS_KEY) || "[]"); } catch { return []; }
}
/** Speichert eine App; mit id wird die vorhandene Version ersetzt (z. B. nach einer Änderung). */
export function saveMyApp(app: Omit<MyApp, "id" | "created_at"> & { id?: string }): MyApp {
  const full: MyApp = { ...app, id: app.id || crypto.randomUUID(), created_at: new Date().toISOString() };
  const all = [full, ...listMyApps().filter((a) => a.id !== full.id)].slice(0, 40);
  try { localStorage.setItem(APPS_KEY, JSON.stringify(all)); } catch { /* Speicher voll: App bleibt nur in dieser Sitzung */ }
  return full;
}
export function deleteMyApp(id: string) {
  try { localStorage.setItem(APPS_KEY, JSON.stringify(listMyApps().filter((a) => a.id !== id))); } catch { /* egal */ }
}

// Apps laufen in einem abgeschotteten iframe (ohne Zugriff auf Mythos-Login). Dort wirft localStorage einen Fehler,
// deshalb bekommen sie einen Speicher im Arbeitsspeicher, damit sie trotzdem funktionieren.
const STORAGE_SHIM = `<script>(function(){function M(){var d={};return{getItem:function(k){return Object.prototype.hasOwnProperty.call(d,k)?d[k]:null},setItem:function(k,v){d[k]=String(v)},removeItem:function(k){delete d[k]},clear:function(){d={}},key:function(i){return Object.keys(d)[i]||null},get length(){return Object.keys(d).length}}}["localStorage","sessionStorage"].forEach(function(n){try{window[n].length}catch(e){try{Object.defineProperty(window,n,{value:M(),configurable:true})}catch(_){}}})})();</script>`;

export function safeHtml(html: string): string {
  const m = html.match(/<head[^>]*>/i);
  return m ? html.replace(m[0], m[0] + STORAGE_SHIM) : STORAGE_SHIM + html;
}

/** Öffnet eine App im neuen Tab, aber weiter abgeschottet (Apps aus dem Marktplatz stammen von anderen Nutzern). */
export function openHtmlInTab(html: string, title = "Mythos App") {
  const esc = (t: string) => t.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  const page = `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${esc(title)}</title>` +
    `<style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%;display:block}</style></head>` +
    `<body><iframe sandbox="allow-scripts allow-forms allow-modals allow-pointer-lock" srcdoc="${esc(safeHtml(html))}"></iframe></body></html>`;
  const w = window.open(URL.createObjectURL(new Blob([page], { type: "text/html" })), "_blank");
  if (!w) throw new Error("Pop-up wurde blockiert");
}

// ---------- Live-Arena ----------
export type ArenaSide = { id: string; label: string; text: string };
export const arenaFight = (prompt: string) => labs<{ a: ArenaSide; b: ArenaSide }>({ kind: "arena_fight", prompt });
export const arenaVote = (a: string, b: string, winner: "a" | "b" | "tie") => labs({ kind: "arena_vote", a, b, winner });
export type BoardRow = { id: string; label: string; wins: number; losses: number; ties: number };
export const arenaBoard = () => labs<{ board: BoardRow[] }>({ kind: "arena_board" }).then((r) => r.board);

// ---------- Marktplatz ----------
export type MarketEntry = {
  id: string; author: string; type: "app" | "image"; title: string; description: string; image_url?: string;
  likes: number; liked: boolean; mine: boolean; copies: number; created_at: string;
};
export type MarketItem = MarketEntry & { html?: string; prompt?: string };
export const marketList = () => labs<{ items: MarketEntry[] }>({ kind: "market_list" }).then((r) => r.items);
export const marketGet = (id: string) => labs<{ item: MarketItem | null }>({ kind: "market_get", id }).then((r) => r.item);
export const marketLike = (id: string) => labs<{ likes: number; liked: boolean }>({ kind: "market_like", id });
export const marketTake = (id: string) => labs<{ item: MarketItem | null }>({ kind: "market_take", id }).then((r) => r.item);
export const marketDelete = (id: string) => labs({ kind: "market_delete", id });
export const marketPublish = (p: { type: "app" | "image"; title: string; description?: string; html?: string; image_url?: string; author?: string; prompt?: string }) =>
  labs<{ id: string }>({ kind: "market_publish", ...p });
