// NVIDIA-API über den Server: Der Schlüssel liegt nur als Secret (NVIDIA_API_KEY) auf Supabase,
// nie im Browser. Nur angemeldete Nutzer dürfen die Funktion aufrufen.
// POST { kind: "chat" | "tts" | "image", model?, messages?, input?, prompt? }
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

  const key = Deno.env.get("NVIDIA_API_KEY") || Deno.env.get("NV_API_KEY");
  if (!key) return json({ error: "NVIDIA_API_KEY ist auf dem Server nicht gesetzt." }, 500);

  // Nur angemeldete Nutzer
  const auth = req.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);
  const authClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
  const { data: claims, error: ce } = await authClient.auth.getClaims(auth.replace("Bearer ", ""));
  if (ce || !claims?.claims?.sub) return json({ error: "Unauthorized" }, 401);

  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const kind = body?.kind;
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
