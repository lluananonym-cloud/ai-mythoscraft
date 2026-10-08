// Mythos Cloud: Backend + KI-Gateway für Apps, die Mythos Code für Nutzer baut (wie Lovable Cloud).
//
// 1) Die KI verbindet eine App (Werkzeug "cloud" in Mythos Code):
//      { action: "connect", api_key: "sk-ant-mythos-…", name: "Meine App" }   (oder Authorization: Bearer <JWT>)
//    -> { app_key, url, client_js, guide }  – client_js wird als mythos-cloud.js ins Projekt geschrieben.
// 2) Die fertige App ruft mit ihrem App-Schlüssel (x-mythos-app: mca_…) auf:
//      { action: "ai", messages, model?, stream?, temperature?, max_tokens? }  OpenAI-kompatibel (auch Streaming)
//      { action: "image", prompt }                                              -> { url }
//      { action: "db", op: "list"|"get"|"insert"|"update"|"delete", collection, id?, data?, where?, limit? }
// Die KI-Kosten laufen über den Besitzer, darum hat jede App ein Tageslimit.
import { createClient } from "npm:@supabase/supabase-js@2.103.3";
import { aiChat } from "../_shared/ai.ts";
import { generateImage } from "../_shared/image.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-api-key, x-mythos-app, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

const MODELS: Record<string, string> = {
  "mythos": "google/gemini-3.6-flash",
  "mythos-lite": "google/gemini-3.1-flash-lite",
  "mythos-pro": "google/gemini-3.1-pro-preview",
};
const PRO_FACTOR = 10;          // Pro-Besitzer: zehnfaches KI-Tageslimit
const MAX_ROWS = 20_000;        // Datensätze pro App
const MAX_ROW_BYTES = 100_000;  // pro Datensatz
const COLLECTION_RE = /^[A-Za-z0-9_-]{1,64}$/;
const UUID_RE = /^[0-9a-f-]{36}$/i;

const db = () => createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function randomKey(len = 32): string {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const arr = new Uint8Array(len);
  crypto.getRandomValues(arr);
  return Array.from(arr, (b) => chars[b % chars.length]).join("");
}

/** Besitzer aus Mythos-API-Schlüssel (Code-App/CLI) oder Website-Login (JWT). */
async function ownerId(req: Request, body: any): Promise<string | null> {
  const key = String(body.api_key || req.headers.get("x-api-key") || "");
  if (key.startsWith("sk-ant-mythos-")) {
    const { data } = await db().from("api_keys").select("user_id,revoked").eq("key_hash", await sha256(key)).maybeSingle();
    return data && !data.revoked ? data.user_id : null;
  }
  const auth = req.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) return null;
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
  const { data } = await client.auth.getClaims(auth.slice(7));
  return (data?.claims?.sub as string) || null;
}

async function isPro(userId: string): Promise<boolean> {
  const [{ data: sub }, { data: roles }] = await Promise.all([
    db().from("subscriptions").select("tier,expires_at").eq("user_id", userId).maybeSingle(),
    db().from("user_roles").select("role").eq("user_id", userId),
  ]);
  return !!roles?.some((r: any) => r.role === "admin") ||
    (sub?.tier === "pro" && (!sub.expires_at || new Date(sub.expires_at) > new Date()));
}

function clientJs(url: string, appKey: string, name: string): string {
  return `// Mythos Cloud – Backend + KI für "${name.replace(/[\r\n"\\]/g, " ")}" (automatisch von Mythos Code angelegt).
// Läuft im Browser (<script type="module">) und in Node 18+. Keine weiteren Schlüssel nötig.
const URL = ${JSON.stringify(url)};
const APP_KEY = ${JSON.stringify(appKey)};

async function call(body) {
  const r = await fetch(URL, { method: "POST", headers: { "content-type": "application/json", "x-mythos-app": APP_KEY }, body: JSON.stringify(body) });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || ("Mythos Cloud Fehler " + r.status));
  return j;
}

export const ai = {
  /** Antwort als Text. messages: [{ role: "system"|"user"|"assistant", content: "…" }] oder ein einzelner String. */
  async chat(messages, opts = {}) {
    const msgs = typeof messages === "string" ? [{ role: "user", content: messages }] : messages;
    const j = await call({ action: "ai", messages: msgs, ...opts });
    return j.choices?.[0]?.message?.content ?? "";
  },
  /** Antwort Stück für Stück: for await (const t of ai.stream(messages)) … */
  async *stream(messages, opts = {}) {
    const msgs = typeof messages === "string" ? [{ role: "user", content: messages }] : messages;
    const r = await fetch(URL, { method: "POST", headers: { "content-type": "application/json", "x-mythos-app": APP_KEY }, body: JSON.stringify({ action: "ai", messages: msgs, ...opts, stream: true }) });
    if (!r.ok || !r.body) throw new Error((await r.json().catch(() => ({}))).error || ("Mythos Cloud Fehler " + r.status));
    const reader = r.body.getReader(), dec = new TextDecoder();
    let buf = "";
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += dec.decode(value, { stream: true });
      let i;
      while ((i = buf.indexOf("\\n")) >= 0) {
        const line = buf.slice(0, i).trim(); buf = buf.slice(i + 1);
        if (!line.startsWith("data:")) continue;
        const data = line.slice(5).trim();
        if (data === "[DONE]") return;
        try { const t = JSON.parse(data).choices?.[0]?.delta?.content; if (t) yield t; } catch { /* unvollständig */ }
      }
    }
  },
  /** Bild erzeugen, liefert eine Bild-URL. */
  async image(prompt) { return (await call({ action: "image", prompt })).url; },
};

const q = (op, collection, extra) => call({ action: "db", op, collection, ...extra });
export const db = {
  /** Alle Einträge einer Sammlung (neueste zuerst). where: { feld: wert } filtert genau. */
  async list(collection, { where, limit } = {}) { return (await q("list", collection, { where, limit })).rows; },
  async get(collection, id) { return (await q("get", collection, { id })).row; },
  async insert(collection, data) { return (await q("insert", collection, { data })).row; },
  async update(collection, id, data) { return (await q("update", collection, { id, data })).row; },
  async remove(collection, id) { await q("delete", collection, { id }); return true; },
};

export default { ai, db };
if (typeof window !== "undefined") window.MythosCloud = { ai, db };
`;
}

const GUIDE = (file: string) => `Mythos Cloud ist verbunden. ${file} liegt im Projekt und enthält Backend + KI – keine eigenen API-Keys, kein Supabase/Firebase/OpenAI nötig.
Nutzung (ES-Module, im Browser mit <script type="module">, in Node 18+ direkt):
  import { ai, db } from "./${file}";
  const text = await ai.chat([{ role: "system", content: "Du bist …" }, { role: "user", content: frage }]);
  for await (const stück of ai.stream("Erzähl was")) ausgabe.textContent += stück;
  const bildUrl = await ai.image("ein Fuchs im Schnee");
  await db.insert("notizen", { titel: "Hallo", erledigt: false });   // liefert { id, data, created_at, updated_at }
  const liste = await db.list("notizen", { where: { erledigt: false }, limit: 50 });
  await db.update("notizen", id, { erledigt: true });   // ändert nur die genannten Felder
  await db.remove("notizen", id);
Modelle (opts.model): "mythos" (Standard), "mythos-lite" (schnell), "mythos-pro" (stark).
Hinweise: Sammlungsnamen nur Buchstaben/Ziffern/_/-. Daten sind für alle Nutzer der App lesbar – keine Passwörter oder geheimen Daten speichern. Pfad im import an den Ort der Datei anpassen; bei Bundlern (Vite usw.) die Datei z. B. nach src/ legen. Fehler kommen als Exception mit verständlicher Meldung.`;

async function connect(req: Request, body: any) {
  const userId = await ownerId(req, body);
  if (!userId) return json({ error: "Nicht angemeldet – bitte in Mythos Code anmelden." }, 401);
  const name = String(body.name || body.app || "Meine App").trim().slice(0, 80) || "Meine App";
  const client = db();
  let { data: proj } = await client.from("app_cloud_projects").select("id,app_key,name").eq("user_id", userId).eq("name", name).maybeSingle();
  if (!proj) {
    const { count } = await client.from("app_cloud_projects").select("*", { count: "exact", head: true }).eq("user_id", userId);
    if ((count || 0) >= 50) return json({ error: "Maximal 50 Cloud-Apps pro Konto." }, 400);
    const { data, error } = await client.from("app_cloud_projects")
      .insert({ user_id: userId, name, app_key: "mca_" + randomKey(32) }).select("id,app_key,name").single();
    if (error) throw error;
    proj = data;
  }
  const url = `${Deno.env.get("SUPABASE_URL")}/functions/v1/app-cloud`;
  const file = "mythos-cloud.js";
  return json({ app: proj.name, app_key: proj.app_key, url, file, client_js: clientJs(url, proj.app_key, proj.name), guide: GUIDE(file) });
}

async function listApps(req: Request, body: any) {
  const userId = await ownerId(req, body);
  if (!userId) return json({ error: "Nicht angemeldet" }, 401);
  const { data } = await db().from("app_cloud_projects").select("id,name,created_at,last_used_at,ai_daily_limit").eq("user_id", userId).order("created_at", { ascending: false });
  return json({ apps: data || [] });
}

async function appAi(proj: any, body: any) {
  const client = db();
  const since = new Date(Date.now() - 864e5).toISOString();
  const [{ count }, pro] = await Promise.all([
    client.from("app_cloud_usage").select("*", { count: "exact", head: true }).eq("project_id", proj.id).eq("kind", "ai").gte("created_at", since),
    isPro(proj.user_id),
  ]);
  const limit = proj.ai_daily_limit * (pro ? PRO_FACTOR : 1);
  if ((count || 0) >= limit) return json({ error: `KI-Tageslimit dieser App erreicht (${limit}). Morgen geht es weiter${pro ? "" : " – mit Mythos Pro ist das Limit 10× höher"}.` }, 429);

  if (body.action === "image") {
    const prompt = String(body.prompt || "").trim().slice(0, 1500);
    if (!prompt) return json({ error: "prompt fehlt" }, 400);
    await client.from("app_cloud_usage").insert({ project_id: proj.id, kind: "ai" });
    const { url, error } = await generateImage(prompt, Deno.env.get("LOVABLE_API_KEY"));
    return url ? json({ url }) : json({ error: error || "Bild konnte nicht erstellt werden" }, 502);
  }

  const messages = Array.isArray(body.messages) ? body.messages.slice(-60) : null;
  if (!messages?.length) return json({ error: "messages fehlt" }, 400);
  const clean = messages
    .filter((m: any) => m && ["system", "user", "assistant"].includes(m.role))
    .map((m: any) => ({ role: m.role, content: typeof m.content === "string" ? m.content.slice(0, 50_000) : m.content }));
  if (!clean.length) return json({ error: "messages leer" }, 400);
  const model = MODELS[String(body.model || "mythos")] || MODELS.mythos;
  const req: Record<string, unknown> = { model, messages: clean, stream: body.stream === true };
  if (typeof body.temperature === "number") req.temperature = body.temperature;
  if (typeof body.max_tokens === "number") req.max_tokens = Math.min(Math.max(1, body.max_tokens), 8192);
  await client.from("app_cloud_usage").insert({ project_id: proj.id, kind: "ai" });
  const r = await aiChat(req, Deno.env.get("LOVABLE_API_KEY"));
  if (!r.ok) {
    const info = await r.text().catch(() => "");
    console.error("[app-cloud] ai failed", r.status, info.slice(0, 300));
    return json({ error: r.status === 429 ? "KI gerade ausgelastet, bitte gleich nochmal versuchen." : "KI nicht erreichbar." }, r.status === 429 ? 429 : 502);
  }
  if (req.stream) return new Response(r.body, { headers: { ...corsHeaders, "Content-Type": "text/event-stream", "Cache-Control": "no-cache" } });
  const j = await r.json();
  // Nach außen nur der Mythos-Modellname.
  return json({ ...j, model: String(body.model || "mythos") });
}

async function appDb(proj: any, body: any) {
  const client = db();
  const op = String(body.op || "");
  const collection = String(body.collection || "");
  if (!COLLECTION_RE.test(collection)) return json({ error: "Ungültiger Sammlungsname (nur Buchstaben, Ziffern, _ und -)" }, 400);
  const id = body.id != null ? String(body.id) : "";
  if (["get", "update", "delete"].includes(op) && !UUID_RE.test(id)) return json({ error: "Ungültige id" }, 400);
  const rows = () => client.from("app_cloud_rows").select("id,data,created_at,updated_at").eq("project_id", proj.id).eq("collection", collection);
  const checkData = (d: unknown) => {
    if (!d || typeof d !== "object" || Array.isArray(d)) return "data muss ein Objekt sein";
    if (JSON.stringify(d).length > MAX_ROW_BYTES) return "Datensatz zu groß (max. 100 KB)";
    return null;
  };

  if (op === "list") {
    let query = rows().order("created_at", { ascending: false }).limit(Math.min(Math.max(1, Number(body.limit) || 100), 1000));
    if (body.where && typeof body.where === "object" && !Array.isArray(body.where)) query = query.contains("data", body.where);
    const { data, error } = await query;
    if (error) throw error;
    return json({ rows: data });
  }
  if (op === "get") {
    const { data } = await rows().eq("id", id).maybeSingle();
    return data ? json({ row: data }) : json({ error: "Nicht gefunden" }, 404);
  }
  if (op === "insert") {
    const bad = checkData(body.data); if (bad) return json({ error: bad }, 400);
    const { count } = await client.from("app_cloud_rows").select("*", { count: "exact", head: true }).eq("project_id", proj.id);
    if ((count || 0) >= MAX_ROWS) return json({ error: `Speicher voll (max. ${MAX_ROWS} Einträge pro App)` }, 400);
    const { data, error } = await client.from("app_cloud_rows").insert({ project_id: proj.id, collection, data: body.data })
      .select("id,data,created_at,updated_at").single();
    if (error) throw error;
    return json({ row: data });
  }
  if (op === "update") {
    const bad = checkData(body.data); if (bad) return json({ error: bad }, 400);
    const { data: cur } = await rows().eq("id", id).maybeSingle();
    if (!cur) return json({ error: "Nicht gefunden" }, 404);
    const merged = { ...(cur.data as object), ...body.data };
    if (JSON.stringify(merged).length > MAX_ROW_BYTES) return json({ error: "Datensatz zu groß (max. 100 KB)" }, 400);
    const { data, error } = await client.from("app_cloud_rows").update({ data: merged, updated_at: new Date().toISOString() })
      .eq("id", id).eq("project_id", proj.id).select("id,data,created_at,updated_at").single();
    if (error) throw error;
    return json({ row: data });
  }
  if (op === "delete") {
    const { error } = await client.from("app_cloud_rows").delete().eq("id", id).eq("project_id", proj.id).eq("collection", collection);
    if (error) throw error;
    return json({ ok: true });
  }
  return json({ error: "Unbekannte op (list, get, insert, update, delete)" }, 400);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Ungültiges JSON" }, 400); }
  try {
    const action = String(body.action || "");
    if (action === "connect") return await connect(req, body);
    if (action === "list") return await listApps(req, body);

    const appKey = String(req.headers.get("x-mythos-app") || body.app_key || "");
    if (!appKey.startsWith("mca_")) return json({ error: "App-Schlüssel fehlt (x-mythos-app)" }, 401);
    const { data: proj } = await db().from("app_cloud_projects").select("id,user_id,ai_daily_limit").eq("app_key", appKey).maybeSingle();
    if (!proj) return json({ error: "App-Schlüssel unbekannt" }, 401);
    db().from("app_cloud_projects").update({ last_used_at: new Date().toISOString() }).eq("id", proj.id).then(() => {}, () => {});

    if (action === "ai" || action === "image") return await appAi(proj, body);
    if (action === "db") return await appDb(proj, body);
    return json({ error: "Unbekannte action (ai, image, db)" }, 400);
  } catch (e) {
    console.error("[app-cloud]", e);
    return json({ error: e instanceof Error ? e.message : "Fehler" }, 500);
  }
});
