// Fernsteuerung: koppelt die Mythos-Code-App (Windows) mit der Mythos-Handy-App.
// Ablauf wie beim Koppeln einer Smart-TV-App:
//  1. PC ruft "create" auf -> bekommt einen 6-stelligen Code, zeigt ihn im Chat an.
//  2. Handy gibt den Code ein, ruft "claim" auf -> bekommt einen langen geheimen Token (pair_secret).
//     Der Code selbst ist danach ungültig (Einmal-Nutzung, 10 Minuten Ablauf).
//  3. PC pollt "poll" (mit seinem eigenen API-Schlüssel) auf neue Aufgaben, führt sie im aktuell
//     offenen Chat aus und schickt das Ergebnis über "result" zurück.
//  4. Handy schickt Aufgaben über "push" (mit pair_secret) und liest Ergebnisse über "get".
// POST { kind, ... }
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function randomCode(): string {
  return String(Math.floor(100000 + Math.random() * 900000)); // 6-stellig
}
function randomSecret(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(32))).map((b) => b.toString(16).padStart(2, "0")).join("");
}
const CODE_TTL_MS = 10 * 60 * 1000;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const kind = body?.kind;

  // ---- PC-Seite: braucht immer den eigenen API-Schlüssel (wie beim normalen Chat) ----
  const auth = async (api_key: string) => {
    if (typeof api_key !== "string" || !api_key.startsWith("sk-ant-mythos-")) return null;
    const key_hash = await sha256(api_key);
    const { data } = await admin.from("api_keys").select("id,user_id,revoked").eq("key_hash", key_hash).maybeSingle();
    if (!data || data.revoked) return null;
    return { userId: data.user_id as string, keyHash: key_hash };
  };

  if (kind === "create") {
    const a = await auth(body.api_key);
    if (!a) return json({ error: "Ungültiger API-Schlüssel." }, 401);
    const device_name = typeof body.device_name === "string" ? body.device_name.slice(0, 60) : "PC";
    let code = "", tries = 0;
    // Fast immer beim ersten Versuch frei; erneut ziehen, falls der Code gerade in Benutzung ist.
    for (; tries < 5; tries++) {
      code = randomCode();
      const { data } = await admin.from("remote_pairs").select("id").eq("code", code).maybeSingle();
      if (!data) break;
    }
    const { data: row, error } = await admin.from("remote_pairs")
      .insert({ code, user_id: a.userId, api_key_hash: a.keyHash, device_name, claimed: false })
      .select("id").single();
    if (error || !row) return json({ error: "Kopplung fehlgeschlagen: " + (error?.message || "unbekannt") }, 500);
    return json({ pair_id: row.id, code });
  }

  if (kind === "status") {
    const a = await auth(body.api_key);
    if (!a) return json({ error: "Ungültiger API-Schlüssel." }, 401);
    const { data } = await admin.from("remote_pairs").select("claimed, device_name, api_key_hash, user_id").eq("id", body.pair_id).maybeSingle();
    if (!data || data.api_key_hash !== a.keyHash) return json({ error: "Kopplung nicht gefunden." }, 404);
    return json({ claimed: !!data.claimed, device_name: data.device_name });
  }

  if (kind === "poll") {
    const a = await auth(body.api_key);
    if (!a) return json({ error: "Ungültiger API-Schlüssel." }, 401);
    const { data: pair } = await admin.from("remote_pairs").select("id, api_key_hash, claimed").eq("id", body.pair_id).maybeSingle();
    if (!pair || pair.api_key_hash !== a.keyHash) return json({ error: "Kopplung nicht gefunden." }, 404);
    await admin.from("remote_pairs").update({ last_seen_at: new Date().toISOString() }).eq("id", pair.id);
    if (!pair.claimed) return json({ task: null });
    const { data: task } = await admin.from("remote_tasks").select("id, prompt").eq("pair_id", pair.id).eq("status", "pending").order("created_at").limit(1).maybeSingle();
    if (!task) return json({ task: null });
    await admin.from("remote_tasks").update({ status: "running", updated_at: new Date().toISOString() }).eq("id", task.id);
    return json({ task: { id: task.id, prompt: task.prompt } });
  }

  if (kind === "result") {
    const a = await auth(body.api_key);
    if (!a) return json({ error: "Ungültiger API-Schlüssel." }, 401);
    const { data: pair } = await admin.from("remote_pairs").select("id, api_key_hash").eq("id", body.pair_id).maybeSingle();
    if (!pair || pair.api_key_hash !== a.keyHash) return json({ error: "Kopplung nicht gefunden." }, 404);
    const status = body.status === "error" ? "error" : "done";
    const result = typeof body.result === "string" ? body.result.slice(0, 60000) : "";
    const { error } = await admin.from("remote_tasks").update({ status, result, updated_at: new Date().toISOString() }).eq("id", body.task_id).eq("pair_id", pair.id);
    if (error) return json({ error: error.message }, 500);
    return json({ ok: true });
  }

  // ---- Handy-Seite: braucht den Code (einmalig) bzw. danach den pair_secret ----
  if (kind === "claim") {
    const code = typeof body.code === "string" ? body.code.replace(/\D/g, "").slice(0, 6) : "";
    if (code.length !== 6) return json({ error: "Bitte den 6-stelligen Code eingeben." }, 400);
    const { data: pair } = await admin.from("remote_pairs").select("id, claimed, created_at").eq("code", code).maybeSingle();
    if (!pair) return json({ error: "Code unbekannt oder schon abgelaufen." }, 404);
    if (pair.claimed) return json({ error: "Dieser Code wurde schon verwendet." }, 409);
    if (Date.now() - new Date(pair.created_at).getTime() > CODE_TTL_MS) return json({ error: "Der Code ist abgelaufen – bitte auf dem PC einen neuen erzeugen." }, 410);
    const pair_secret = randomSecret();
    const device_name = typeof body.device_name === "string" ? body.device_name.slice(0, 60) : "Handy";
    const { error } = await admin.from("remote_pairs").update({ claimed: true, pair_secret, code: null, device_name }).eq("id", pair.id).eq("claimed", false);
    if (error) return json({ error: "Kopplung fehlgeschlagen." }, 500);
    return json({ pair_id: pair.id, pair_secret });
  }

  // Ab hier: Handy authentifiziert sich mit pair_id + pair_secret.
  const authPhone = async () => {
    if (typeof body.pair_id !== "string" || typeof body.pair_secret !== "string" || !body.pair_secret) return null;
    const { data } = await admin.from("remote_pairs").select("id, pair_secret, claimed").eq("id", body.pair_id).maybeSingle();
    if (!data || !data.claimed || data.pair_secret !== body.pair_secret) return null;
    return data;
  };

  if (kind === "push") {
    const p = await authPhone();
    if (!p) return json({ error: "Nicht (mehr) gekoppelt." }, 401);
    const prompt = typeof body.prompt === "string" ? body.prompt.trim().slice(0, 8000) : "";
    if (!prompt) return json({ error: "Leere Aufgabe." }, 400);
    const { data: row, error } = await admin.from("remote_tasks").insert({ pair_id: p.id, prompt }).select("id").single();
    if (error || !row) return json({ error: "Senden fehlgeschlagen." }, 500);
    return json({ task_id: row.id });
  }

  if (kind === "get") {
    const p = await authPhone();
    if (!p) return json({ error: "Nicht (mehr) gekoppelt." }, 401);
    const { data: task } = await admin.from("remote_tasks").select("status, result").eq("id", body.task_id).eq("pair_id", p.id).maybeSingle();
    if (!task) return json({ error: "Aufgabe nicht gefunden." }, 404);
    return json({ status: task.status, result: task.result || "" });
  }

  if (kind === "unpair") {
    const p = await authPhone();
    if (!p) return json({ ok: true }); // schon getrennt
    await admin.from("remote_pairs").delete().eq("id", p.id);
    return json({ ok: true });
  }

  return json({ error: "Unknown kind" }, 400);
});
