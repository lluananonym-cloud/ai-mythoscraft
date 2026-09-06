// Shared AI helper: Lovable AI Gateway with automatic Google Gemini fallback.
// Wenn die Lovable-Credits leer sind (402), Rate-Limit (429) oder ein 5xx kommt,
// wird automatisch direkt auf die Google Generative Language API umgeschaltet
// (Primary-Key + Backup-Key).

const GATEWAY = "https://ai.gateway.lovable.dev/v1/chat/completions";
const GOOGLE_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

const enc = new TextEncoder();
const sse = (obj: unknown) => enc.encode(`data: ${JSON.stringify(obj)}\n\n`);
const sseDone = () => enc.encode("data: [DONE]\n\n");

export function googleKeys(): string[] {
  return [
    Deno.env.get("GOOGLE_AI_API_KEY"),
    Deno.env.get("GOOGLE_AI_API_KEY_BACKUP"),
  ].filter((k): k is string => !!k && k.length > 5);
}

/** Lovable-Modell-ID -> Google-Modellkette (erstes verfügbares gewinnt) */
export function geminiChain(model: string): string[] {
  const m = String(model || "").toLowerCase();
  if (m.includes("image")) return ["gemini-3.1-flash-image"];
  if (m.includes("lite") || m.includes("nano") || m.includes("luna")) {
    return ["gemini-3.1-flash-lite", "gemini-3.6-flash"];
  }
  if (m.includes("pro") || m.includes("ultra") || m.includes("gpt-5.5") || m.includes("sol")) {
    // pro-preview ist im Free-Tier oft quota-gesperrt -> starke Flash-Modelle als Ersatz
    return ["gemini-3.1-pro-preview", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.1-flash-lite"];
  }
  return ["gemini-3.6-flash", "gemini-3.7-flash", "gemini-3.1-flash-lite"];
}

export function mapToGemini(model: string): string {
  return geminiChain(model)[0];
}


type AnyMsg = { role: string; content: unknown };

function partsFromContent(content: unknown): any[] {
  if (typeof content === "string") return content ? [{ text: content }] : [];
  if (Array.isArray(content)) {
    const out: any[] = [];
    for (const p of content as any[]) {
      if (!p) continue;
      if (p.type === "text" && p.text) out.push({ text: p.text });
      else if (p.type === "image_url" && p.image_url?.url) {
        const url: string = p.image_url.url;
        const m = url.match(/^data:([^;]+);base64,(.+)$/);
        if (m) out.push({ inlineData: { mimeType: m[1], data: m[2] } });
      } else if (typeof p.text === "string" && p.text) out.push({ text: p.text });
    }
    return out;
  }
  return [];
}

/** OpenAI-Chat-Body -> Google GenerateContent-Body */
export function toGeminiBody(body: Record<string, any>) {
  const msgs: AnyMsg[] = Array.isArray(body.messages) ? body.messages : [];
  const systemTexts: string[] = [];
  const contents: any[] = [];
  for (const m of msgs) {
    if (m.role === "system" || m.role === "developer") {
      const parts = partsFromContent(m.content);
      systemTexts.push(parts.map((p) => p.text || "").join("\n"));
      continue;
    }
    if (m.role === "tool") continue;
    const parts = partsFromContent(m.content);
    if (parts.length === 0) continue;
    contents.push({ role: m.role === "assistant" ? "model" : "user", parts });
  }
  const gen: Record<string, unknown> = {};
  const maxOut = body.max_tokens ?? body.max_completion_tokens;
  if (maxOut) gen.maxOutputTokens = maxOut;
  if (body.temperature !== undefined) gen.temperature = body.temperature;

  const out: Record<string, unknown> = { contents };
  if (systemTexts.length) out.systemInstruction = { parts: [{ text: systemTexts.join("\n\n") }] };
  if (Object.keys(gen).length) out.generationConfig = gen;
  return out;
}

async function googleFetch(model: string, stream: boolean, payload: unknown, key: string) {
  const url = `${GOOGLE_BASE}/${model}:${stream ? "streamGenerateContent?alt=sse" : "generateContent"}`;
  return await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify(payload),
  });
}

/** Google-SSE -> OpenAI-kompatibler SSE-Stream */
function googleSseToOpenAi(src: ReadableStream<Uint8Array>): ReadableStream<Uint8Array> {
  const dec = new TextDecoder();
  let buf = "";
  return new ReadableStream({
    async start(c) {
      const reader = src.getReader();
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buf += dec.decode(value, { stream: true });
          const lines = buf.split("\n");
          buf = lines.pop() ?? "";
          for (const line of lines) {
            const t = line.trim();
            if (!t.startsWith("data:")) continue;
            const raw = t.slice(5).trim();
            if (!raw || raw === "[DONE]") continue;
            try {
              const j = JSON.parse(raw);
              const parts = j.candidates?.[0]?.content?.parts ?? [];
              const text = parts.map((p: any) => p.text || "").join("");
              if (text) c.enqueue(sse({ choices: [{ delta: { content: text } }] }));
            } catch (_e) { /* ignore partial */ }
          }
        }
      } catch (e) {
        console.error("[gemini-fallback] stream error", e);
      }
      c.enqueue(sseDone());
      c.close();
    },
  });
}

function shouldFallback(status: number) {
  return status === 402 || status === 429 || status === 401 || status === 403 || status >= 500;
}

/**
 * Ruft das Lovable Gateway auf und fällt bei Credit-/Limit-/Serverfehlern
 * automatisch auf Google Gemini zurück. Antwort ist immer OpenAI-kompatibel.
 */
export async function aiChat(
  body: Record<string, any>,
  lovableKey?: string,
): Promise<Response> {
  const streaming = body.stream === true;
  const key = lovableKey ?? Deno.env.get("LOVABLE_API_KEY");

  if (key) {
    try {
      const r = await fetch(GATEWAY, {
        method: "POST",
        headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (r.ok) return r;
      const info = await r.text().catch(() => "");
      console.warn("[ai] gateway failed", r.status, info.slice(0, 200));
      if (!shouldFallback(r.status)) {
        return new Response(info, { status: r.status, headers: { "Content-Type": "application/json" } });
      }
    } catch (e) {
      console.warn("[ai] gateway exception", e instanceof Error ? e.message : String(e));
    }
  }

  // ---- Google Fallback ----
  const keys = googleKeys();
  const gModel = mapToGemini(body.model);
  const payload = toGeminiBody(body);
  let lastStatus = 503;
  let lastText = "no google key configured";

  for (const gk of keys) {
    try {
      const r = await googleFetch(gModel, streaming, payload, gk);
      if (!r.ok) {
        lastStatus = r.status;
        lastText = (await r.text().catch(() => "")).slice(0, 300);
        console.warn("[ai] google key failed", r.status, lastText);
        continue;
      }
      if (streaming && r.body) {
        return new Response(googleSseToOpenAi(r.body), {
          headers: { "Content-Type": "text/event-stream" },
        });
      }
      const j = await r.json();
      const text = (j.candidates?.[0]?.content?.parts ?? []).map((p: any) => p.text || "").join("");
      return new Response(
        JSON.stringify({ choices: [{ message: { role: "assistant", content: text }, finish_reason: "stop" }] }),
        { headers: { "Content-Type": "application/json" } },
      );
    } catch (e) {
      lastText = e instanceof Error ? e.message : String(e);
      console.warn("[ai] google exception", lastText);
    }
  }

  return new Response(JSON.stringify({ error: `AI unavailable: ${lastText}` }), {
    status: lastStatus,
    headers: { "Content-Type": "application/json" },
  });
}

/** Bequemer Helper für nicht-streamende Aufrufe: gibt direkt den Text zurück. */
export async function aiText(body: Record<string, any>, lovableKey?: string): Promise<string> {
  const r = await aiChat({ ...body, stream: false }, lovableKey);
  if (!r.ok) return "";
  const j = await r.json().catch(() => null);
  return j?.choices?.[0]?.message?.content ?? "";
}

/**
 * Drop-in-Ersatz für `fetch("https://ai.gateway.lovable.dev/v1/chat/completions", init)`.
 * Gleiche Signatur wie fetch, aber mit automatischem Google-Gemini-Fallback.
 */
export async function aiFetch(_url: string, init: RequestInit): Promise<Response> {
  let body: Record<string, any> = {};
  try { body = JSON.parse(String(init?.body ?? "{}")); } catch { /* ignore */ }
  const h = new Headers(init?.headers as HeadersInit);
  const auth = h.get("Authorization") || "";
  const key = auth.replace(/^Bearer\s+/i, "").trim() || undefined;
  return await aiChat(body, key);
}
