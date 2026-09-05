import { aiFetch } from "../_shared/ai.ts";
import { buildSystemMessages } from "../_shared/identity.ts";
// Claude-compatible /v1/messages endpoint, backed by Lovable AI Gateway.
// MythosAI identity is locked: regardless of any client-supplied system prompt,
// the model is instructed to identify as MythosAI and never claim to be another assistant.
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-api-key, anthropic-version, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join("");
}

function err(code: string, message: string, status: number) {
  return new Response(JSON.stringify({ type: "error", error: { type: code, message } }), {
    status, headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function mapModel(m: string): string {
  const lc = (m || "").toLowerCase();
  if (lc.includes("mythos-v2") || lc.includes("opus")) return "openai/gpt-5.6-sol";
  if (lc.includes("sonnet") || lc.includes("code")) return "google/gemini-3.1-pro-preview";
  if (lc.includes("haiku") || lc.includes("lite")) return "google/gemini-3.1-flash-lite";
  return "google/gemini-3.6-flash";
}

/** Nach außen sichtbarer Mythos-Modellname (nie der echte Anbieter). */
function mythosLabelFor(m: string): string {
  const lc = (m || "").toLowerCase();
  if (lc.includes("mythos-v2") || lc.includes("opus")) return "Mythos v2";
  if (lc.includes("code")) return "MythosCode v1.5";
  if (lc.includes("sonnet")) return "Mythos v1";
  if (lc.includes("haiku") || lc.includes("lite")) return "Mythos v1 Lite";
  return "Mythos v1";
}

function toOpenAI(messages: any[]): any[] {
  return messages.map((m) => {
    if (typeof m.content === "string") return { role: m.role, content: m.content };
    const text = (m.content || []).filter((b: any) => b.type === "text").map((b: any) => b.text).join("\n");
    return { role: m.role, content: text };
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return err("invalid_request_error", "Use POST", 405);

  const apiKey = req.headers.get("x-api-key") || req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!apiKey || !apiKey.startsWith("sk-ant-mythos-")) return err("authentication_error", "Invalid API key", 401);

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const key_hash = await sha256(apiKey);
  const { data: keyRow } = await supabase.from("api_keys").select("*").eq("key_hash", key_hash).maybeSingle();
  if (!keyRow) return err("authentication_error", "API key not found", 401);
  if (keyRow.revoked) return err("authentication_error", "API key revoked", 401);

  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const { count } = await supabase.from("api_usage").select("*", { count: "exact", head: true }).eq("api_key_id", keyRow.id).gte("created_at", since);
  if ((count || 0) >= keyRow.daily_limit) return err("rate_limit_error", `Daily limit of ${keyRow.daily_limit} reached`, 429);

  let body: any;
  try { body = await req.json(); } catch { return err("invalid_request_error", "Invalid JSON", 400); }

  const { model, messages = [], system, max_tokens = 1024, stream = false, temperature } = body;
  if (!Array.isArray(messages) || messages.length === 0) return err("invalid_request_error", "messages required", 400);

  // Identity lock ALWAYS comes first; the user's system prompt comes after but cannot override identity.
  const userSystem = system
    ? (typeof system === "string" ? system : (system || []).map((b: any) => b.text).join("\n"))
    : "";

  const oaiMessages: any[] = [
    { role: "system", content: MYTHOS_CATALOG },
    ...buildSystemMessages(userSystem, {
      modelLabel: mythosLabelFor(model),
      surface: "api",
      lang: "en",
    }),
    ...toOpenAI(messages),
  ];

  const gatewayBody: any = {
    model: mapModel(model),
    messages: oaiMessages,
    max_tokens,
    stream,
  };
  if (typeof temperature === "number") gatewayBody.temperature = temperature;

  const upstream = await aiFetch("gateway", {
    method: "POST",
    headers: { Authorization: `Bearer ${Deno.env.get("LOVABLE_API_KEY")}`, "Content-Type": "application/json" },
    body: JSON.stringify(gatewayBody),
  });

  const logUsage = async (input_tokens?: number, output_tokens?: number) => {
    await supabase.from("api_usage").insert({
      api_key_id: keyRow.id, user_id: keyRow.user_id, model: gatewayBody.model,
      input_tokens, output_tokens, status_code: upstream.status,
    });
    await supabase.from("api_keys").update({ total_requests: Number(keyRow.total_requests) + 1, last_used_at: new Date().toISOString() }).eq("id", keyRow.id);
  };

  if (!upstream.ok) {
    const t = await upstream.text();
    await logUsage();
    if (upstream.status === 429) return err("rate_limit_error", "Upstream rate limit", 429);
    if (upstream.status === 402) return err("api_error", "Provider credits exhausted", 502);
    console.error("Gateway error", upstream.status, t);
    return err("api_error", "Upstream error", 502);
  }

  if (!stream) {
    const data = await upstream.json();
    const text = data.choices?.[0]?.message?.content || "";
    const usage = data.usage || {};
    await logUsage(usage.prompt_tokens, usage.completion_tokens);

    const respId = `msg_${crypto.randomUUID().replace(/-/g, "")}`;
    return new Response(JSON.stringify({
      id: respId,
      type: "message",
      role: "assistant",
      model,
      content: [{ type: "text", text }],
      stop_reason: "end_turn",
      stop_sequence: null,
      usage: { input_tokens: usage.prompt_tokens || 0, output_tokens: usage.completion_tokens || 0 },
    }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  }

  await logUsage();
  const respId = `msg_${crypto.randomUUID().replace(/-/g, "")}`;
  const enc = new TextEncoder();

  const out = new ReadableStream({
    async start(controller) {
      const send = (event: string, data: any) => controller.enqueue(enc.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`));

      send("message_start", { type: "message_start", message: { id: respId, type: "message", role: "assistant", content: [], model, stop_reason: null, stop_sequence: null, usage: { input_tokens: 0, output_tokens: 0 } } });
      send("content_block_start", { type: "content_block_start", index: 0, content_block: { type: "text", text: "" } });

      const reader = upstream.body!.getReader();
      const dec = new TextDecoder();
      let buf = "";
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buf += dec.decode(value, { stream: true });
          let idx;
          while ((idx = buf.indexOf("\n")) !== -1) {
            let line = buf.slice(0, idx); buf = buf.slice(idx + 1);
            if (line.endsWith("\r")) line = line.slice(0, -1);
            if (!line.startsWith("data: ")) continue;
            const json = line.slice(6).trim();
            if (json === "[DONE]") continue;
            try {
              const p = JSON.parse(json);
              const c = p.choices?.[0]?.delta?.content;
              if (c) send("content_block_delta", { type: "content_block_delta", index: 0, delta: { type: "text_delta", text: c } });
            } catch { buf = line + "\n" + buf; break; }
          }
        }
      } catch (e) { console.error("stream err", e); }

      send("content_block_stop", { type: "content_block_stop", index: 0 });
      send("message_delta", { type: "message_delta", delta: { stop_reason: "end_turn", stop_sequence: null }, usage: { output_tokens: 0 } });
      send("message_stop", { type: "message_stop" });
      controller.close();
    },
  });

  return new Response(out, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
});
