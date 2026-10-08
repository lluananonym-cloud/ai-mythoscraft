// NVIDIA-API über den Server: Die Schlüssel liegen nur als Secrets (NVIDIA_API_KEY, optional NVIDIA_IMAGE_API_KEY) auf Supabase,
// nie im Browser. Nur angemeldete Nutzer dürfen die Funktion aufrufen.
// POST { kind: "chat" | "tts" | "image" | "setkey", model?, messages?, input?, prompt?, which?, value? }
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
const NV = "https://integrate.api.nvidia.com/v1";
const MODEL_RE = /^[\w.\/-]{1,120}$/;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);

  // Nur angemeldete Nutzer
  const auth = req.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);
  const authClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
  const { data: claims, error: ce } = await authClient.auth.getClaims(auth.replace("Bearer ", ""));
  if (ce || !claims?.claims?.sub) return json({ error: "Unauthorized" }, 401);

  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const kind = body?.kind;
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  // Admin: Schlüssel speichern (für Projekte ohne Secrets-Verwaltung). Wird nie zurückgegeben.
  // which: "main"|"image" (NVIDIA, beginnt mit nvapi-) oder "google"|"google2" (Google AI Studio, gratis, beginnt mit AIza).
  if (kind === "setkey") {
    const { data: isAdmin } = await admin.rpc("has_role", { _user_id: claims.claims.sub, _role: "admin" });
    if (!isAdmin) return json({ error: "Nur für Admins." }, 403);
    if (body.which === "elevenlabs") {
      const v = typeof body.value === "string" ? body.value.trim() : "";
      if (!/^[\w-]{20,200}$/.test(v)) return json({ error: "Das sieht nicht nach einem ElevenLabs-Schlüssel aus (meist sk_…)." }, 400);
      const { error } = await admin.from("app_secrets").upsert({ name: "ELEVENLABS_API_KEY", value: v, updated_at: new Date().toISOString() });
      if (error) return json({ error: "Speichern fehlgeschlagen – ist die Migration app_secrets angewendet? (" + error.message + ")" }, 500);
      return json({ ok: true, name: "ELEVENLABS_API_KEY" });
    }
    const isGoogle = body.which === "google" || body.which === "google2";
    const name = isGoogle ? (body.which === "google2" ? "GOOGLE_AI_API_KEY_BACKUP" : "GOOGLE_AI_API_KEY")
      : body.which === "image" ? "NVIDIA_IMAGE_API_KEY" : "NVIDIA_API_KEY";
    const value = typeof body.value === "string" ? body.value.trim() : "";
    const looksRight = isGoogle ? /^AIza[\w-]{20,80}$/.test(value) : /^nvapi-[\w-]{20,200}$/.test(value);
    if (!looksRight) return json({ error: isGoogle ? "Das sieht nicht nach einem Google-AI-Schlüssel aus (beginnt mit AIza)." : "Das sieht nicht nach einem NVIDIA-Schlüssel aus (beginnt mit nvapi-)." }, 400);
    const { error } = await admin.from("app_secrets").upsert({ name, value, updated_at: new Date().toISOString() });
    if (error) return json({ error: "Speichern fehlgeschlagen – ist die Migration app_secrets angewendet? (" + error.message + ")" }, 500);
    return json({ ok: true, name });
  }

  // Schlüssel: Server-Secret hat Vorrang, sonst der per /nvidiakeyadmin gespeicherte. Bilder dürfen einen eigenen haben.
  const stored: Record<string, string> = {};
  const { data: rows } = await admin.from("app_secrets").select("name, value").in("name", ["NVIDIA_API_KEY", "NVIDIA_IMAGE_API_KEY"]);
  for (const r of rows ?? []) stored[r.name] = r.value;
  const mainKey = Deno.env.get("NVIDIA_API_KEY") || Deno.env.get("NV_API_KEY") || stored.NVIDIA_API_KEY;
  const imageKey = Deno.env.get("NVIDIA_IMAGE_API_KEY") || stored.NVIDIA_IMAGE_API_KEY || mainKey;
  const key = kind === "image" ? imageKey : mainKey;
  if (!key) return json({ error: kind === "image" ? "NVIDIA_IMAGE_API_KEY ist nicht gesetzt (Admin: /nvidiakeyadmin bild <Schlüssel>)." : "NVIDIA_API_KEY ist nicht gesetzt (Admin: /nvidiakeyadmin <Schlüssel>)." }, 500);
  const headers = { "Content-Type": "application/json", Authorization: `Bearer ${key}` };

  try {
    if (kind === "chat") {
      const model = typeof body.model === "string" && MODEL_RE.test(body.model) ? body.model : "meta/llama-3.3-70b-instruct";
      const messages = Array.isArray(body.messages) ? body.messages.slice(-40).map((m: any) => ({
        role: m?.role === "assistant" ? "assistant" : m?.role === "system" ? "system" : "user",
        content: typeof m?.content === "string" ? m.content.slice(0, 20000) : "",
      })) : [];
      if (!messages.length) return json({ error: "messages required" }, 400);
      const r = await fetch(`${NV}/chat/completions`, { method: "POST", headers, body: JSON.stringify({ model, messages, temperature: 0.7, max_tokens: 4096 }) });
      if (!r.ok) return json({ error: `NVIDIA ${r.status}` }, r.status === 429 ? 429 : 502);
      const d = await r.json();
      return json({ content: d.choices?.[0]?.message?.content ?? "" });
    }
    if (kind === "tts") {
      const model = typeof body.model === "string" && MODEL_RE.test(body.model) ? body.model : "magpie-tts-multilingual";
      const input = typeof body.input === "string" ? body.input.slice(0, 4000) : "";
      if (!input) return json({ error: "input required" }, 400);
      const r = await fetch(`${NV}/audio/speech`, { method: "POST", headers, body: JSON.stringify({ model, input }) });
      if (!r.ok) return json({ error: `NVIDIA ${r.status}` }, 502);
      const buf = new Uint8Array(await r.arrayBuffer());
      let bin = ""; for (let i = 0; i < buf.length; i += 0x8000) bin += String.fromCharCode(...buf.subarray(i, i + 0x8000));
      return json({ audioBase64: btoa(bin) });
    }
    if (kind === "image") {
      const model = typeof body.model === "string" && MODEL_RE.test(body.model) ? body.model : "qwen-image";
      const prompt = typeof body.prompt === "string" ? body.prompt.slice(0, 2000) : "";
      if (!prompt) return json({ error: "prompt required" }, 400);
      const r = await fetch(`${NV}/images/generations`, { method: "POST", headers, body: JSON.stringify({ model, prompt, n: 1 }) });
      if (!r.ok) return json({ error: `NVIDIA ${r.status}` }, 502);
      const img = (await r.json()).data?.[0];
      return json({ url: img?.url ?? (img?.b64_json ? `data:image/png;base64,${img.b64_json}` : "") });
    }
    return json({ error: "Unknown kind" }, 400);
  } catch (e) {
    console.error("nvidia proxy error:", e);
    return json({ error: "NVIDIA nicht erreichbar" }, 502);
  }
});
