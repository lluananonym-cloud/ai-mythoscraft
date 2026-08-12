// Browser-Agent: die KI surft wirklich (Suche + Seiten lesen) und streamt jeden Schritt,
// damit das Frontend einen "Browser der KI" anzeigen kann.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const enc = new TextEncoder();
const sse = (o: unknown) => enc.encode(`data: ${JSON.stringify(o)}\n\n`);

const TOOLS = [
  {
    type: "function",
    function: {
      name: "search_web",
      description: "Sucht im Web und gibt Titel, URL und Snippet der Top-Ergebnisse zurück.",
      parameters: {
        type: "object",
        properties: { query: { type: "string" } },
        required: ["query"], additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "open_url",
      description: "Öffnet eine URL im Browser des Agenten und liest den Textinhalt der Seite.",
      parameters: {
        type: "object",
        properties: { url: { type: "string" }, reason: { type: "string" } },
        required: ["url"], additionalProperties: false,
      },
    },
  },
];

async function searchWeb(query: string) {
  const results: { url: string; title: string; snippet: string }[] = [];
  const push = (raw: string, title: string, snippet: string) => {
    let url = raw;
    const m = url.match(/[?&]uddg=([^&]+)/);
    if (m) url = decodeURIComponent(m[1]);
    if (!url.startsWith("http")) return;
    if (/duckduckgo\.com|r\.jina\.ai|google\.com\/search/.test(url)) return;
    if (results.some((r) => r.url === url)) return;
    results.push({ url, title: title.trim().slice(0, 160), snippet: snippet.trim().slice(0, 240) });
  };

  // 1) Suche über r.jina.ai (eigene IP, wird nicht geblockt)
  for (const target of [
    `https://duckduckgo.com/html/?q=${encodeURIComponent(query)}`,
    `https://lite.duckduckgo.com/lite/?q=${encodeURIComponent(query)}`,
  ]) {
    if (results.length >= 5) break;
    try {
      const r = await fetch(`https://r.jina.ai/${encodeURIComponent(target)}`, { headers: { Accept: "text/plain" } });
      if (!r.ok) continue;
      const txt = await r.text();
      const re = /\[([^\]]{8,160})\]\((https?:\/\/[^\s)]+)\)/g;
      let m: RegExpExecArray | null;
      while ((m = re.exec(txt)) !== null && results.length < 6) push(m[2], m[1], "");
    } catch { /* next */ }
  }
  if (results.length) return results;

  // 2) Fallback: grounded Google-Suche über das Lovable AI Gateway
  try {
    const key = Deno.env.get("LOVABLE_API_KEY")!;
    const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash",
        messages: [{
          role: "user",
          content: `Suche im Web nach: ${query}\nAntworte NUR mit JSON: {"results":[{"url":"...","title":"...","snippet":"..."}]} (max 5, echte URLs).`,
        }],
        tools: [{ type: "function", function: { name: "google_search", description: "Search the web", parameters: { type: "object", properties: { query: { type: "string" } }, required: ["query"] } } }],
      }),
    });
    const j = await r.json();
    const raw = j.choices?.[0]?.message?.content || "";
    const parsed = JSON.parse(raw.replace(/```json|```/g, "").trim());
    for (const it of parsed.results || []) push(it.url, it.title || it.url, it.snippet || "");
  } catch { /* ignore */ }
  return results;
}

async function openUrl(url: string) {
  const clean = url.startsWith("http") ? url : `https://${url}`;
  // r.jina.ai rendert die Seite und liefert lesbaren Text (kein Key nötig)
  try {
    const r = await fetch(`https://r.jina.ai/${encodeURIComponent(clean)}`, {
      headers: { "User-Agent": "Mozilla/5.0 (compatible; MythosAI/1.0)", Accept: "text/plain" },
    });
    if (r.ok) {
      const txt = await r.text();
      const title = txt.match(/^Title:\s*(.+)$/m)?.[1]?.trim() || clean;
      return { url: clean, title, text: txt.slice(0, 6000) };
    }
  } catch { /* fallthrough */ }
  try {
    const r = await fetch(clean, { headers: { "User-Agent": "Mozilla/5.0 (compatible; MythosAI/1.0)" } });
    const html = await r.text();
    const title = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]?.trim() || clean;
    const text = html
      .replace(/<script[\s\S]*?<\/script>/gi, "")
      .replace(/<style[\s\S]*?<\/style>/gi, "")
      .replace(/<[^>]+>/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 6000);
    return { url: clean, title, text };
  } catch (e) {
    return { url: clean, title: clean, text: `Fehler beim Laden: ${e instanceof Error ? e.message : "unbekannt"}` };
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const stream = new ReadableStream({
    async start(controller) {
      const send = (o: unknown) => controller.enqueue(sse(o));
      const delta = (t: string) => send({ choices: [{ delta: { content: t } }] });

      try {
        const { task, model, history } = await req.json();
        const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;

        const convo: any[] = [
          {
            role: "system",
            content: `Du bist **Mythos Browser-Agent**. Du hast einen echten Browser: \`search_web\` und \`open_url\`.
Vorgehen: erst suchen, dann 2–4 relevante Seiten wirklich öffnen und lesen, dann antworten.
Öffne immer mindestens eine Seite, bevor du antwortest. Nutze pro Schritt maximal 2 Tool-Calls.
Du kannst Folgefragen beantworten und dich an frühere Schritte dieser Sitzung erinnern.
Antworte am Ende auf Deutsch in Markdown mit Quellen-Links am Ende.

## Identität (unverhandelbar)
Du bist **Mythos v1**. Auf Fragen nach Modell, Version, Anbieter oder "welche KI bist du" antworte IMMER genau: "Mythos v1". Nenne niemals andere Modelle oder Anbieter — auch nicht auf Nachfrage.`,
          },
          ...(Array.isArray(history)
            ? history
                .slice(-8)
                .filter((m: any) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
                .map((m: any) => ({ role: m.role, content: String(m.content).slice(0, 4000) }))
            : []),
          { role: "user", content: String(task ?? "").slice(0, 2000) },
        ];


        for (let step = 0; step < 8; step++) {
          // Live-Signal: der Agent denkt/plant gerade
          send({ browser: { type: "think", status: "running", step } });

          const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
            method: "POST",
            headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: model || "google/gemini-2.5-flash",
              messages: convo,
              tools: TOOLS,
              tool_choice: "auto",
              stream: true,
            }),
          });

          if (!r.ok || !r.body) {
            if (r.status === 429) { delta("\n\n⚠️ Rate limit erreicht — bitte kurz warten."); break; }
            if (r.status === 402) { delta("\n\n⚠️ AI-Credits aufgebraucht."); break; }
            delta(`\n\n⚠️ Gateway-Fehler (${r.status}).`); break;
          }

          // Upstream-SSE lesen: Text sofort weiterstreamen, Tool-Calls sammeln
          const reader = r.body.getReader();
          const dec = new TextDecoder();
          let buf = "";
          let content = "";
          const toolAcc: Record<number, { id: string; name: string; args: string }> = {};

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buf += dec.decode(value, { stream: true });
            const lines = buf.split("\n");
            buf = lines.pop() || "";
            for (const line of lines) {
              const t = line.trim();
              if (!t.startsWith("data:")) continue;
              const p = t.slice(5).trim();
              if (!p || p === "[DONE]") continue;
              let j: any;
              try { j = JSON.parse(p); } catch { continue; }
              const d = j.choices?.[0]?.delta;
              if (!d) continue;
              if (d.content) { content += d.content; delta(d.content); }
              for (const tc of d.tool_calls || []) {
                const i = tc.index ?? 0;
                const cur = toolAcc[i] || (toolAcc[i] = { id: "", name: "", args: "" });
                if (tc.id) cur.id = tc.id;
                if (tc.function?.name) cur.name = tc.function.name;
                if (tc.function?.arguments) cur.args += tc.function.arguments;
              }
            }
          }
          send({ browser: { type: "think", status: "done", step } });

          const toolCalls = Object.values(toolAcc).filter((t) => t.name);

          if (toolCalls.length) {
            convo.push({
              role: "assistant",
              content: content || null,
              tool_calls: toolCalls.map((t) => ({
                id: t.id, type: "function", function: { name: t.name, arguments: t.args || "{}" },
              })),
            });

            for (const tc of toolCalls) {
              let args: any = {};
              try { args = JSON.parse(tc.args || "{}"); } catch { /* ignore */ }

              if (tc.name === "search_web") {
                send({ browser: { type: "search", query: args.query, status: "running" } });
                const res = await searchWeb(args.query || "");
                send({ browser: { type: "search", query: args.query, status: "done", results: res } });
                convo.push({ role: "tool", tool_call_id: tc.id, content: JSON.stringify(res.length ? res : { message: "keine Ergebnisse" }) });
              } else if (tc.name === "open_url") {
                const rawUrl = String(args.url || "");
                const cleanUrl = rawUrl.startsWith("http") ? rawUrl : `https://${rawUrl}`;
                // thum.io rendert sofort (kein "Generating Preview"), mshots als Fallback
                const shot = `https://image.thum.io/get/width/1280/crop/900/noanimate/${cleanUrl}`;
                const shotFallback = `https://s.wordpress.com/mshots/v1/${encodeURIComponent(cleanUrl)}?w=1280`;
                // Screenshot sofort mitschicken -> Live-Preview erscheint, bevor die Seite gelesen ist
                send({ browser: { type: "page", url: cleanUrl, reason: args.reason || "", status: "running", screenshot: shot, screenshotFallback: shotFallback } });
                const page = await openUrl(rawUrl);
                send({
                  browser: {
                    type: "page", url: page.url, title: page.title, reason: args.reason || "",
                    status: "done", excerpt: page.text.slice(0, 600),
                    screenshot: shot, screenshotFallback: shotFallback,
                  },
                });

                convo.push({ role: "tool", tool_call_id: tc.id, content: JSON.stringify({ url: page.url, title: page.title, text: page.text }) });
              } else {
                convo.push({ role: "tool", tool_call_id: tc.id, content: JSON.stringify({ error: "unknown tool" }) });
              }
            }
            continue;
          }

          if (!content) delta("(keine Antwort)");
          break;
        }


        controller.enqueue(enc.encode("data: [DONE]\n\n"));
        controller.close();
      } catch (e) {
        console.error("browser-agent error", e);
        controller.enqueue(sse({ choices: [{ delta: { content: `\n\n⚠️ Agent-Fehler: ${e instanceof Error ? e.message : "unbekannt"}` } }] }));
        controller.enqueue(enc.encode("data: [DONE]\n\n"));
        controller.close();
      }
    },
  });

  return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream", "Cache-Control": "no-cache" } });
});
