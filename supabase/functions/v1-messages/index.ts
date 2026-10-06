import { aiChat } from "../_shared/ai.ts";
import { buildSystemMessages } from "../_shared/identity.ts";
import { MYTHOS_CATALOG } from "../_shared/catalog.ts";
// Claude-compatible /v1/messages endpoint, backed by Lovable AI Gateway.
// MythosAI identity is locked: regardless of any client-supplied system prompt,
// the model is instructed to identify as MythosAI and never claim to be another assistant.
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-api-key, anthropic-version, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const FALLBACK_MODEL = "google/gemini-3.6-flash";
// Bleibt unter dem Edge-Function-Limit, damit wir immer selbst sauber antworten.
const TIME_BUDGET_MS = 140_000;

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
  if (lc.includes("code")) return "openai/gpt-5.6-sol";
  if (lc.includes("sonnet")) return "google/gemini-3.1-pro-preview";
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

function blockText(b: any): string {
  if (!b) return "";
  if (typeof b === "string") return b;
  if (b.type === "text") return b.text || "";
  if (b.type === "tool_use") return `[tool_use ${b.name}] ${JSON.stringify(b.input ?? {})}`;
  if (b.type === "tool_result") {
    const c = b.content;
    const t = typeof c === "string" ? c : Array.isArray(c) ? c.map(blockText).join("\n") : "";
    return `[tool_result${b.is_error ? " error" : ""}]\n${t}`;
  }
  return "";
}

type OpenAIContent = string | ({ type: "text"; text: string } | { type: "image_url"; image_url: { url: string } })[];

/** Anthropic-Bildblock (base64 oder URL) -> Daten-/Bild-URL für den Gateway. */
// Max. ~8 MB pro Bild (base64 ist ~4/3 so groß) – schützt vor überdimensionierten Anfragen.
const MAX_IMAGE_B64 = 11_000_000;
function imageUrl(b: any): string | null {
  const s = b?.source;
  if (b?.type !== "image" || !s) return null;
  if (s.type === "base64" && typeof s.data === "string" && /^image\/(png|jpeg|gif|webp)$/.test(s.media_type)
    && s.data.length <= MAX_IMAGE_B64 && /^[A-Za-z0-9+/=\s]+$/.test(s.data.slice(0, 200))) return `data:${s.media_type};base64,${s.data}`;
  if (s.type === "url" && typeof s.url === "string" && /^https:\/\/[^\s]{1,2000}$/.test(s.url)) return s.url;
  return null;
}

// Anthropic-Blöcke (auch tool_use/tool_result) -> Text, sonst entstehen leere Nachrichten.
// Bilder in Nutzer-Nachrichten bleiben als image_url-Teile erhalten.
function toOpenAI(messages: any[]): { role: string; content: OpenAIContent }[] {
  return messages.map((m) => {
    const role = m.role === "assistant" ? "assistant" : "user";
    if (typeof m.content === "string") return { role, content: m.content || "(leer)" };
    const blocks: any[] = m.content || [];
    const text = blocks.map(blockText).filter(Boolean).join("\n");
    const images = role === "user" ? blocks.map(imageUrl).filter((u): u is string => !!u).slice(0, 8) : [];
    if (!images.length) return { role, content: text || "(leer)" };
    return { role, content: [{ type: "text" as const, text: text || "Siehe Bild." }, ...images.map((url) => ({ type: "image_url" as const, image_url: { url } }))] };
  });
}

function shorten<T extends OpenAIContent>(s: T, max: number): T {
  if (typeof s !== "string") return s.map((p) => (p.type === "text" ? { ...p, text: shorten(p.text, max) } : p)) as T;
  if (s.length <= max) return s;
  const head = Math.floor(max * 0.7);
  return (s.slice(0, head) + "\n…[gekürzt]…\n" + s.slice(s.length - (max - head))) as T;
}

// Kürzt lange Verläufe: erste Nachricht (Aufgabe) + die letzten `keepLast`, jede Nachricht max. `maxChars`.
function compact(msgs: { role: string; content: OpenAIContent }[], keepLast: number, maxChars: number) {
  const picked = msgs.length > keepLast + 1 ? [msgs[0], ...msgs.slice(-keepLast)] : msgs;
  return picked.map((m) => ({ role: m.role, content: shorten(m.content, maxChars) }));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return err("invalid_request_error", "Use POST", 405);
  const startedAt = Date.now();

  const apiKey = req.headers.get("x-api-key") || req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!apiKey || !apiKey.startsWith("sk-ant-mythos-")) return err("authentication_error", "Invalid API key", 401);

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const key_hash = await sha256(apiKey);
  const { data: keyRow } = await supabase.from("api_keys").select("*").eq("key_hash", key_hash).maybeSingle();
  if (!keyRow) return err("authentication_error", "API key not found", 401);
  if (keyRow.revoked) return err("authentication_error", "API key revoked", 401);

  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const { count } = await supabase.from("api_usage").select("*", { count: "exact", head: true }).eq("api_key_id", keyRow.id).gte("created_at", since);
  const [{ data: subRow }, { data: roleRows }] = await Promise.all([
    supabase.from("subscriptions").select("tier,expires_at").eq("user_id", keyRow.user_id).maybeSingle(),
    supabase.from("user_roles").select("role").eq("user_id", keyRow.user_id),
  ]);
  const isPro = !!roleRows?.some((r: any) => r.role === "admin") ||
    (subRow?.tier === "pro" && (!subRow.expires_at || new Date(subRow.expires_at) > new Date()));
  if (!isPro && (count || 0) >= keyRow.daily_limit) return err("rate_limit_error", `Daily limit of ${keyRow.daily_limit} reached`, 429);

  let body: any;
  try { body = await req.json(); } catch { return err("invalid_request_error", "Invalid JSON", 400); }

  const { model, messages = [], system, max_tokens = 1024, stream = false, temperature } = body;
  // Aufwand aus der Code-App (wie bei Claude): low | normal | high | max
  const effort = ["low", "normal", "high", "max"].includes(body.effort) ? body.effort as string : "";
  if (!Array.isArray(messages) || messages.length === 0) return err("invalid_request_error", "messages required", 400);

  // Identity lock ALWAYS comes first; the user's system prompt comes after but cannot override identity.
  const userSystem = system
    ? (typeof system === "string" ? system : (system || []).map((b: any) => b.text).join("\n"))
    : "";

  const systemMsgs: any[] = [
    { role: "system", content: MYTHOS_CATALOG },
    ...buildSystemMessages(userSystem, {
      modelLabel: mythosLabelFor(model),
      surface: "api",
      lang: "en",
    }),
  ];
  const conv = toOpenAI(messages);
  const primary = mapModel(model);

  // Erst voller Verlauf, dann gekürzt, dann schnelles Ausweichmodell mit stark gekürztem Verlauf.
  const attempts = [
    { model: primary, msgs: conv },
    { model: primary, msgs: compact(conv, 12, 6000) },
    { model: FALLBACK_MODEL, msgs: compact(conv, 8, 3000) },
  ];

  const gatewayBodyFor = (a: { model: string; msgs: any[] }, streamFlag: boolean) => {
    const b: any = { model: a.model, messages: [...systemMsgs, ...a.msgs], stream: streamFlag };
    if (!a.model.startsWith("openai/")) b.max_tokens = max_tokens;
    if (typeof temperature === "number") b.temperature = temperature;
    // Sichtbare Gedanken: bei unterstützten Modellen kurz mitdenken lassen und live mitschicken.
    if (a.model.startsWith("openai/")) b.reasoning_effort = effort === "high" ? "medium" : effort === "max" ? "high" : "low";
    return b;
  };

  let lastStatus = 503;
  let lastBody = "";
  let usedModel = primary;

  /** Nächster erfolgreicher Upstream-Response ab Versuch `from`, oder null. */
  const nextUpstream = async (from: number, streamFlag: boolean): Promise<{ resp: Response; index: number } | null> => {
    for (let i = from; i < attempts.length; i++) {
      const remaining = TIME_BUDGET_MS - (Date.now() - startedAt);
      if (remaining < 5_000) break;
      try {
        const r = await Promise.race([
          aiChat(gatewayBodyFor(attempts[i], streamFlag), Deno.env.get("LOVABLE_API_KEY")),
          new Promise<never>((_, rej) => setTimeout(() => rej(new Error("timeout")), remaining)),
        ]);
        if (r.ok) { usedModel = attempts[i].model; return { resp: r, index: i }; }
        lastStatus = r.status;
        lastBody = await r.text().catch(() => "");
        console.error("upstream failed", attempts[i].model, r.status, lastBody.slice(0, 300));
      } catch (e) {
        lastStatus = 504; lastBody = "";
        console.error("upstream exception", attempts[i].model, e instanceof Error ? e.message : String(e));
      }
    }
    return null;
  };

  const logUsage = async (status: number, input_tokens?: number, output_tokens?: number) => {
    await supabase.from("api_usage").insert({
      api_key_id: keyRow.id, user_id: keyRow.user_id, model: usedModel,
      input_tokens, output_tokens, status_code: status,
    });
    await supabase.from("api_keys").update({ total_requests: Number(keyRow.total_requests) + 1, last_used_at: new Date().toISOString() }).eq("id", keyRow.id);
  };

  // "AI unavailable" kommt von aiChat(), wenn sowohl Lovable (z. B. Guthaben leer) als auch
  // der Google-Ausweichweg (kein Schlüssel gesetzt) fehlschlagen – das ist ein Konfigurationsproblem,
  // kein normales Überlasten, und bekommt deshalb eine eigene, hilfreiche Meldung.
  const failureError = () => lastStatus === 429
    ? { type: "rate_limit_error", message: "Mythos ist gerade stark ausgelastet – bitte kurz warten.", status: 429 }
    : /AI unavailable/i.test(lastBody)
    ? { type: "api_error", message: "Mythos ist gerade nicht erreichbar – die KI-Anbindung ist im Projekt nicht konfiguriert (Lovable-Guthaben leer und kein Ausweich-Schlüssel gesetzt). Admin: Guthaben aufladen oder im Website-Chat /apikeyadmin AIza… setzen.", status: 503 }
    : { type: "overloaded_error", message: "Mythos ist gerade überlastet – bitte gleich nochmal versuchen.", status: 529 };

  if (!stream) {
    for (let from = 0; from < attempts.length;) {
      const got = await nextUpstream(from, false);
      if (!got) break;
      const data = await got.resp.json().catch(() => null);
      const text = data?.choices?.[0]?.message?.content || "";
      if (!text) { from = got.index + 1; continue; }
      const usage = data.usage || {};
      await logUsage(200, usage.prompt_tokens, usage.completion_tokens);
      return new Response(JSON.stringify({
        id: `msg_${crypto.randomUUID().replace(/-/g, "")}`,
        type: "message",
        role: "assistant",
        model,
        content: [{ type: "text", text }],
        stop_reason: "end_turn",
        stop_sequence: null,
        usage: { input_tokens: usage.prompt_tokens || 0, output_tokens: usage.completion_tokens || 0 },
      }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }
    await logUsage(lastStatus);
    const f = failureError();
    return err(f.type, f.message, f.status);
  }

  // Streaming: Header sofort senden und per Ping wach halten, damit lange Anfragen nicht in einen Timeout laufen.
  const respId = `msg_${crypto.randomUUID().replace(/-/g, "")}`;
  const enc = new TextEncoder();

  const out = new ReadableStream({
    async start(controller) {
      const send = (event: string, data: any) => controller.enqueue(enc.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`));
      const ping = setInterval(() => { try { send("ping", { type: "ping" }); } catch { /* closed */ } }, 5000);

      send("message_start", { type: "message_start", message: { id: respId, type: "message", role: "assistant", content: [], model, stop_reason: null, stop_sequence: null, usage: { input_tokens: 0, output_tokens: 0 } } });

      let emitted = 0;
      let blockOpen = false;
      let thinkOpen = false;
      // Gedanken (falls das Modell welche liefert) als eigener Block VOR dem Text, live Wort für Wort.
      const emitThink = (text: string) => {
        if (blockOpen) return; // Text hat schon begonnen – keine Gedanken mehr mischen.
        if (!thinkOpen) { send("content_block_start", { type: "content_block_start", index: 0, content_block: { type: "thinking", thinking: "" } }); thinkOpen = true; }
        send("content_block_delta", { type: "content_block_delta", index: 0, delta: { type: "thinking_delta", thinking: text } });
      };
      const emit = (text: string) => {
        if (thinkOpen && !blockOpen) send("content_block_stop", { type: "content_block_stop", index: 0 });
        if (!blockOpen) { send("content_block_start", { type: "content_block_start", index: thinkOpen ? 1 : 0, content_block: { type: "text", text: "" } }); blockOpen = true; }
        emitted += text.length;
        send("content_block_delta", { type: "content_block_delta", index: thinkOpen ? 1 : 0, delta: { type: "text_delta", text } });
      };

      for (let from = 0; from < attempts.length && emitted === 0;) {
        const got = await nextUpstream(from, true);
        if (!got) break;
        from = got.index + 1;
        const reader = got.resp.body!.getReader();
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
                const d = JSON.parse(json).choices?.[0]?.delta;
                const think = typeof d?.reasoning === "string" ? d.reasoning
                  : typeof d?.reasoning_content === "string" ? d.reasoning_content
                  : Array.isArray(d?.reasoning_details) ? d.reasoning_details.map((x: any) => x?.text || x?.summary || "").join("") : "";
                if (think) emitThink(think);
                if (d?.content) emit(d.content);
              } catch { buf = line + "\n" + buf; break; }
            }
          }
        } catch (e) { console.error("stream err", e); }
      }

      clearInterval(ping);
      if (emitted === 0) {
        if (thinkOpen) send("content_block_stop", { type: "content_block_stop", index: 0 });
        await logUsage(lastStatus);
        const f = failureError();
        send("error", { type: "error", error: { type: f.type, message: f.message } });
        controller.close();
        return;
      }
      await logUsage(200, undefined, Math.ceil(emitted / 4));
      send("content_block_stop", { type: "content_block_stop", index: thinkOpen ? 1 : 0 });
      send("message_delta", { type: "message_delta", delta: { stop_reason: "end_turn", stop_sequence: null }, usage: { output_tokens: Math.ceil(emitted / 4) } });
      send("message_stop", { type: "message_stop" });
      controller.close();
    },
  });

  return new Response(out, { headers: { ...corsHeaders, "Content-Type": "text/event-stream", "Cache-Control": "no-cache" } });
});
