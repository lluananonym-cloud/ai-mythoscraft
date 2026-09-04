import { aiFetch } from "../_shared/ai.ts";
// Guest / Trial mode — no auth, no history, strict limits (worse than Free tier).
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const enc = new TextEncoder();
const sse = (o: unknown) => enc.encode(`data: ${JSON.stringify(o)}\n\n`);

const GUEST_DAILY_LIMIT = 8;
const hits = new Map<string, { day: string; count: number }>();

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { messages } = await req.json();
    if (!Array.isArray(messages) || messages.length === 0) {
      return new Response(JSON.stringify({ error: "messages required" }), {
        status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // IP-based soft rate limit (per function instance)
    const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
    const day = new Date().toISOString().slice(0, 10);
    const cur = hits.get(ip);
    const count = cur && cur.day === day ? cur.count : 0;
    if (count >= GUEST_DAILY_LIMIT) {
      return new Response(JSON.stringify({ error: "guest_limit", limit: GUEST_DAILY_LIMIT }), {
        status: 429, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    hits.set(ip, { day, count: count + 1 });

    // Only the last 4 turns, each trimmed — Gast-Modus hat kein echtes Gedächtnis
    const trimmed = messages.slice(-4).map((m: any) => ({
      role: m.role === "assistant" ? "assistant" : "user",
      content: String(m.content ?? "").slice(0, 800),
    }));

    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;
    const r = await aiFetch("gateway", {
      method: "POST",
      headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash-lite",
        stream: true,
        messages: [
          {
            role: "system",
            content:
              "Du bist **Mythos AI (Gast-Modus)** — eine eingeschränkte Testversion. " +
              "Antworte auf Deutsch, freundlich, aber KURZ: maximal 4 Sätze, kein langer Markdown-Block, keine Tabellen. " +
              "Du hast keine Tools, kein Gedächtnis, keine Bild-/Musik-/Video-/Sprach-Funktionen und keinen Zugriff auf Server-Daten. " +
              "Wenn der User mehr will (Bilder, Musik, Video, Live-Voice, Agent, Verlauf, längere Antworten), sage in EINEM Satz, " +
              "dass er sich kostenlos registrieren soll." +
              " Du bist Mythos v1 — wenn du nach deinem Modell, deiner Version oder deinem Anbieter gefragt wirst, antworte IMMER genau 'Mythos v1' und nenne niemals andere Modelle oder Anbieter.",
          },
          ...trimmed,
        ],
      }),
    });

    if (!r.ok) {
      const txt = await r.text();
      const status = r.status === 429 || r.status === 402 ? r.status : 500;
      return new Response(JSON.stringify({ error: txt.slice(0, 300) }), {
        status, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const stream = new ReadableStream({
      async start(controller) {
        const reader = r.body!.getReader();
        const dec = new TextDecoder();
        let buf = "";
        let sent = 0;
        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buf += dec.decode(value, { stream: true });
            const lines = buf.split("\n");
            buf = lines.pop() || "";
            for (const line of lines) {
              if (!line.startsWith("data: ")) continue;
              const payload = line.slice(6).trim();
              if (payload === "[DONE]") continue;
              try {
                const j = JSON.parse(payload);
                const delta = j.choices?.[0]?.delta?.content;
                if (delta) {
                  sent += delta.length;
                  if (sent > 1200) { // hartes Output-Limit im Gast-Modus
                    controller.enqueue(sse({ choices: [{ delta: { content: " …" } }] }));
                    controller.enqueue(enc.encode("data: [DONE]\n\n"));
                    controller.close();
                    return;
                  }
                  controller.enqueue(sse({ choices: [{ delta: { content: delta } }] }));
                }
              } catch { /* ignore */ }
            }
          }
        } catch (e) {
          controller.enqueue(sse({ choices: [{ delta: { content: "\n\n⚠️ Fehler im Gast-Modus." } }] }));
          console.error("guest-chat stream error", e);
        }
        controller.enqueue(enc.encode("data: [DONE]\n\n"));
        controller.close();
      },
    });

    return new Response(stream, {
      headers: { ...corsHeaders, "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
    });
  } catch (e) {
    console.error("guest-chat error", e);
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
