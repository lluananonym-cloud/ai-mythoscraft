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
  const unwrap = (u: string) => {
    const m = u.match(/uddg=([^&]+)/);
    let out = m ? decodeURIComponent(m[1]) : u;
    if (out.startsWith("//")) out = `https:${out}`;
    return out;
  };

  // 1) DuckDuckGo Lite (stabiles, schlankes HTML)
  try {
    const r = await fetch(`https://lite.duckduckgo.com/lite/?q=${encodeURIComponent(query)}`, {
      headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Accept-Language": "de,en;q=0.8" },
    });
    const html = await r.text();
    const linkRe = /<a[^>]+href="([^"]+)"[^>]*class=['"]result-link['"][^>]*>([\s\S]*?)<\/a>/g;
    const snipRe = /class=['"]result-snippet['"][^>]*>([\s\S]*?)<\/td>/g;
    const snippets: string[] = [];
    let sm: RegExpExecArray | null;
    while ((sm = snipRe.exec(html)) !== null) snippets.push(sm[1].replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim());
    let m: RegExpExecArray | null;
    let i = 0;
    while ((m = linkRe.exec(html)) !== null && results.length < 6) {
      results.push({
        url: unwrap(m[1]),
        title: m[2].replace(/<[^>]+>/g, "").replace(/&amp;/g, "&").replace(/\s+/g, " ").trim(),
        snippet: (snippets[i] || "").slice(0, 240),
      });
      i++;
    }
  } catch { /* next */ }
  if (results.length) return results;

  // 2) Fallback: Seite über r.jina.ai rendern und Links aus dem Markdown ziehen
  try {
    const r = await fetch(`https://r.jina.ai/https://duckduckgo.com/html/?q=${encodeURIComponent(query)}`, {
      headers: { Accept: "text/plain" },
    });
    const txt = await r.text();
    const re = /\[([^\]]{8,140})\]\((https?:\/\/[^\s)]+)\)/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(txt)) !== null && results.length < 6) {
      const url = m[2];
      if (url.includes("duckduckgo.com")) continue;
      results.push({ url, title: m[1].trim(), snippet: "" });
    }
  } catch { /* ignore */ }
  return results;
}

async function openUrl(url: string) {
  const clean = url.startsWith("http") ? url : `https://${url}`;
  // r.jina.ai rendert die Seite und liefert lesbaren Text (kein Key nötig)
  try {
    const r = await fetch(`https://r.jina.ai/${clean}`, {
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
        const { task, model } = await req.json();
        const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;

        const convo: any[] = [
          {
            role: "system",
            content: `Du bist **Mythos Browser-Agent**. Du hast einen echten Browser: \`search_web\` und \`open_url\`.
Vorgehen: erst suchen, dann 2–4 relevante Seiten wirklich öffnen und lesen, dann antworten.
Öffne immer mindestens eine Seite, bevor du antwortest. Nutze pro Schritt maximal 2 Tool-Calls.
Antworte am Ende auf Deutsch in Markdown mit Quellen-Links am Ende.`,
          },
          { role: "user", content: String(task ?? "").slice(0, 2000) },
        ];

        for (let step = 0; step < 8; step++) {
          const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
            method: "POST",
            headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: model || "google/gemini-2.5-flash",
              messages: convo,
              tools: TOOLS,
              tool_choice: "auto",
            }),
          });

          if (!r.ok) {
            if (r.status === 429) { delta("\n\n⚠️ Rate limit erreicht — bitte kurz warten."); break; }
            if (r.status === 402) { delta("\n\n⚠️ AI-Credits aufgebraucht."); break; }
            delta(`\n\n⚠️ Gateway-Fehler (${r.status}).`); break;
          }

          const data = await r.json();
          const msg = data.choices?.[0]?.message;
          if (!msg) break;

          if (msg.tool_calls?.length) {
            convo.push(msg);
            for (const tc of msg.tool_calls) {
              let args: any = {};
              try { args = JSON.parse(tc.function.arguments || "{}"); } catch { /* ignore */ }

              if (tc.function.name === "search_web") {
                send({ browser: { type: "search", query: args.query, status: "running" } });
                const res = await searchWeb(args.query || "");
                send({ browser: { type: "search", query: args.query, status: "done", results: res } });
                convo.push({ role: "tool", tool_call_id: tc.id, content: JSON.stringify(res.length ? res : { message: "keine Ergebnisse" }) });
              } else if (tc.function.name === "open_url") {
                send({ browser: { type: "page", url: args.url, reason: args.reason || "", status: "running" } });
                const page = await openUrl(args.url || "");
                send({
                  browser: {
                    type: "page", url: page.url, title: page.title, reason: args.reason || "",
                    status: "done", excerpt: page.text.slice(0, 600),
                    screenshot: `https://s.wordpress.com/mshots/v1/${encodeURIComponent(page.url)}?w=1280`,
                  },
                });
                convo.push({ role: "tool", tool_call_id: tc.id, content: JSON.stringify({ url: page.url, title: page.title, text: page.text }) });
              } else {
                convo.push({ role: "tool", tool_call_id: tc.id, content: JSON.stringify({ error: "unknown tool" }) });
              }
            }
            continue;
          }

          const final = msg.content || "(keine Antwort)";
          for (let i = 0; i < final.length; i += 16) {
            delta(final.slice(i, i + 16));
            await new Promise((res) => setTimeout(res, 6));
          }
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
