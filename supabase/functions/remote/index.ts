// Fernsteuerung: koppelt die Mythos-Code-App (Windows) mit der Mythos-Handy-App bzw. der Website (/code).
// Ablauf wie beim Koppeln einer Smart-TV-App:
//  1. PC ruft "create" auf -> bekommt einen 6-stelligen Code, zeigt ihn im Chat an.
//  2. Handy/Website gibt den Code ein, ruft "claim" auf -> bekommt einen langen geheimen Token (pair_secret).
//     Der Code selbst ist danach ungültig (Einmal-Nutzung, 10 Minuten Ablauf).
//  3. PC pollt "poll" (mit seinem eigenen API-Schlüssel) auf neue Aufgaben, führt sie im aktuell
//     offenen Chat aus und schickt das Ergebnis über "result" zurück.
//  4. Handy schickt Aufgaben über "push" (mit pair_secret) und liest Ergebnisse über "get".
//
// Gespeichert wird in einem privaten Storage-Bucket (nur service_role) statt in eigenen Tabellen:
// die Tabellen remote_pairs/remote_tasks wurden nie angewendet, deshalb schlug /koppeln immer fehl.
// POST { kind, ... }
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function randomCode(): string {
  const n = crypto.getRandomValues(new Uint32Array(1))[0];
  return String(100000 + (n % 900000)); // 6-stellig
}
function randomSecret(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(32))).map((b) => b.toString(16).padStart(2, "0")).join("");
}
const CODE_TTL_MS = 10 * 60 * 1000;
const BUCKET = "remote-control";
const ID_RE = /^[0-9a-f-]{36}$/;

type Pair = { id: string; user_id: string; api_key_hash: string; device_name: string; claimed: boolean; pair_secret?: string; code?: string | null; created_at: string; last_seen_at?: string };
type Task = { id: string; pair_id: string; prompt: string; status: "pending" | "running" | "done" | "error"; result?: string; created_at: string; updated_at: string };

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
let bucketReady = false;
async function ensureBucket() {
  if (bucketReady) return;
  const { error } = await admin.storage.createBucket(BUCKET, { public: false });
  if (error && !/exist/i.test(error.message)) console.warn("[remote] createBucket", error.message);
  bucketReady = true;
}
async function get<T>(path: string): Promise<T | null> {
  await ensureBucket();
  const { data, error } = await admin.storage.from(BUCKET).download(path);
  if (error || !data) return null;
  try { return JSON.parse(await data.text()) as T; } catch { return null; }
}
async function put(path: string, value: unknown) {
  await ensureBucket();
  const { error } = await admin.storage.from(BUCKET).upload(path, new Blob([JSON.stringify(value)], { type: "application/json" }), { upsert: true, contentType: "application/json" });
  if (error) throw new Error(error.message);
}
async function del(paths: string[]) { await ensureBucket(); await admin.storage.from(BUCKET).remove(paths); }
async function list(prefix: string) {
  await ensureBucket();
  const { data } = await admin.storage.from(BUCKET).list(prefix, { limit: 200, sortBy: { column: "created_at", order: "asc" } });
  return (data || []).filter((f) => f.name.endsWith(".json"));
}
const pairPath = (id: string) => `pairs/${id}.json`;
const codePath = (code: string) => `codes/${code}.json`;
const taskPath = (pairId: string, taskId: string) => `tasks/${pairId}/${taskId}.json`;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);

  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const kind = body?.kind;
  const now = () => new Date().toISOString();

  try {
    // ---- PC-Seite: braucht immer den eigenen API-Schlüssel (wie beim normalen Chat) ----
    const auth = async (api_key: string) => {
      if (typeof api_key !== "string" || !api_key.startsWith("sk-ant-mythos-")) return null;
      const key_hash = await sha256(api_key);
      const { data } = await admin.from("api_keys").select("id,user_id,revoked").eq("key_hash", key_hash).maybeSingle();
      if (!data || data.revoked) return null;
      return { userId: data.user_id as string, keyHash: key_hash };
    };
    const pcPair = async () => {
      const a = await auth(body.api_key);
      if (!a) return { err: json({ error: "Ungültiger API-Schlüssel – bitte in der App ab- und wieder anmelden." }, 401) };
      if (typeof body.pair_id !== "string" || !ID_RE.test(body.pair_id)) return { err: json({ error: "Kopplung nicht gefunden." }, 404) };
      const pair = await get<Pair>(pairPath(body.pair_id));
      if (!pair || pair.api_key_hash !== a.keyHash) return { err: json({ error: "Kopplung nicht gefunden." }, 404) };
      return { pair };
    };

    if (kind === "create") {
      const a = await auth(body.api_key);
      if (!a) return json({ error: "Ungültiger API-Schlüssel – bitte in der App ab- und wieder anmelden." }, 401);
      const device_name = typeof body.device_name === "string" ? body.device_name.slice(0, 60) : "PC";
      let code = "";
      for (let tries = 0; tries < 5; tries++) {
        code = randomCode();
        const taken = await get<{ id: string; created_at: string }>(codePath(code));
        if (!taken || Date.now() - new Date(taken.created_at).getTime() > CODE_TTL_MS) break;
      }
      const pair: Pair = { id: crypto.randomUUID(), user_id: a.userId, api_key_hash: a.keyHash, device_name, claimed: false, code, created_at: now() };
      await put(pairPath(pair.id), pair);
      await put(codePath(code), { id: pair.id, created_at: pair.created_at });
      return json({ pair_id: pair.id, code });
    }

    if (kind === "status") {
      const r = await pcPair(); if (r.err) return r.err;
      return json({ claimed: !!r.pair!.claimed, device_name: r.pair!.device_name });
    }

    if (kind === "poll") {
      const r = await pcPair(); if (r.err) return r.err;
      const pair = r.pair!;
      if (!pair.claimed) return json({ task: null });
      for (const f of await list(`tasks/${pair.id}`)) {
        const t = await get<Task>(`tasks/${pair.id}/${f.name}`);
        if (t && t.status === "pending") {
          t.status = "running"; t.updated_at = now();
          await put(taskPath(pair.id, t.id), t);
          return json({ task: { id: t.id, prompt: t.prompt } });
        }
      }
      return json({ task: null });
    }

    if (kind === "result") {
      const r = await pcPair(); if (r.err) return r.err;
      if (typeof body.task_id !== "string" || !ID_RE.test(body.task_id)) return json({ error: "Aufgabe nicht gefunden." }, 404);
      const t = await get<Task>(taskPath(r.pair!.id, body.task_id));
      if (!t) return json({ error: "Aufgabe nicht gefunden." }, 404);
      t.status = body.status === "error" ? "error" : "done";
      t.result = typeof body.result === "string" ? body.result.slice(0, 60000) : "";
      t.updated_at = now();
      await put(taskPath(t.pair_id, t.id), t);
      return json({ ok: true });
    }

    // ---- Handy-Seite: braucht den Code (einmalig) bzw. danach den pair_secret ----
    if (kind === "claim") {
      const code = typeof body.code === "string" ? body.code.replace(/\D/g, "").slice(0, 6) : "";
      if (code.length !== 6) return json({ error: "Bitte den 6-stelligen Code eingeben." }, 400);
      const ref = await get<{ id: string; created_at: string }>(codePath(code));
      if (!ref) return json({ error: "Code unbekannt oder schon abgelaufen." }, 404);
      const pair = await get<Pair>(pairPath(ref.id));
      if (!pair || pair.code !== code) return json({ error: "Code unbekannt oder schon abgelaufen." }, 404);
      if (pair.claimed) return json({ error: "Dieser Code wurde schon verwendet." }, 409);
      if (Date.now() - new Date(pair.created_at).getTime() > CODE_TTL_MS) return json({ error: "Der Code ist abgelaufen – bitte auf dem PC einen neuen erzeugen." }, 410);
      const pair_secret = randomSecret();
      pair.claimed = true; pair.pair_secret = await sha256(pair_secret); pair.code = null;
      pair.device_name = typeof body.device_name === "string" ? body.device_name.slice(0, 60) : "Handy";
      await put(pairPath(pair.id), pair);
      await del([codePath(code)]);
      return json({ pair_id: pair.id, pair_secret });
    }

    // Ab hier: Handy authentifiziert sich mit pair_id + pair_secret.
    const authPhone = async () => {
      if (typeof body.pair_id !== "string" || !ID_RE.test(body.pair_id) || typeof body.pair_secret !== "string" || !body.pair_secret) return null;
      const pair = await get<Pair>(pairPath(body.pair_id));
      if (!pair || !pair.claimed || pair.pair_secret !== await sha256(body.pair_secret)) return null;
      return pair;
    };

    if (kind === "push") {
      const p = await authPhone();
      if (!p) return json({ error: "Nicht (mehr) gekoppelt." }, 401);
      const prompt = typeof body.prompt === "string" ? body.prompt.trim().slice(0, 8000) : "";
      if (!prompt) return json({ error: "Leere Aufgabe." }, 400);
      const t: Task = { id: crypto.randomUUID(), pair_id: p.id, prompt, status: "pending", created_at: now(), updated_at: now() };
      await put(taskPath(p.id, t.id), t);
      return json({ task_id: t.id });
    }

    if (kind === "get") {
      const p = await authPhone();
      if (!p) return json({ error: "Nicht (mehr) gekoppelt." }, 401);
      if (typeof body.task_id !== "string" || !ID_RE.test(body.task_id)) return json({ error: "Aufgabe nicht gefunden." }, 404);
      const t = await get<Task>(taskPath(p.id, body.task_id));
      if (!t) return json({ error: "Aufgabe nicht gefunden." }, 404);
      return json({ status: t.status, result: t.result || "" });
    }

    if (kind === "unpair") {
      const p = await authPhone();
      if (!p) return json({ ok: true }); // schon getrennt
      const tasks = await list(`tasks/${p.id}`);
      await del([pairPath(p.id), ...tasks.map((f) => `tasks/${p.id}/${f.name}`)]);
      return json({ ok: true });
    }

    return json({ error: "Unknown kind" }, 400);
  } catch (e) {
    console.error("[remote]", e);
    return json({ error: "Fernsteuerung gerade nicht verfügbar: " + (e instanceof Error ? e.message : String(e)) }, 500);
  }
});
