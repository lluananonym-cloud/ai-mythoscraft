import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};

const TOOLS = [
  {
    type: "function",
    function: {
      name: "get_minecraft_server_status",
      description: "Holt den Live-Status von mythoscraft.online: online/offline, Spielerzahl, MOTD, Version.",
      parameters: { type: "object", properties: {}, additionalProperties: false },
    },
  },
  {
    type: "function",
    function: {
      name: "search_knowledge_base",
      description: "Sucht in der internen Mythoscraft-Knowledge-Base nach Artikeln (Regeln, Commands, Plugins, FAQ).",
      parameters: {
        type: "object",
        properties: { query: { type: "string", description: "Suchbegriff" } },
        required: ["query"], additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "web_search",
      description: "Sucht im Web nach aktuellen Infos. Nutze für Plugin-Docs, Minecraft-News, allgemeine Recherche.",
      parameters: {
        type: "object",
        properties: { query: { type: "string" } },
        required: ["query"], additionalProperties: false,
      },
    },
  },
];

async function runTool(name: string, args: any, supabase: any): Promise<string> {
  if (name === "get_minecraft_server_status") {
    try {
      const r = await fetch("https://api.mcsrvstat.us/3/mythoscraft.online");
      const d = await r.json();
      if (!d.online) return JSON.stringify({ online: false, message: "Server ist offline" });
      return JSON.stringify({
        online: true,
        players: { online: d.players?.online ?? 0, max: d.players?.max ?? 0, list: d.players?.list?.map((p: any) => p.name).slice(0, 20) || [] },
        version: d.version,
        motd: d.motd?.clean?.join(" ") || "",
        software: d.software,
      });
    } catch (e) { return JSON.stringify({ error: "Status nicht abrufbar" }); }
  }
  if (name === "search_knowledge_base") {
    const q = (args.query as string).toLowerCase();
    const { data } = await supabase.from("knowledge_articles").select("title,category,body").eq("is_published", true);
    const matches = (data || []).filter((a: any) => a.title.toLowerCase().includes(q) || a.body.toLowerCase().includes(q) || a.category.toLowerCase().includes(q)).slice(0, 5);
    return JSON.stringify(matches.length ? matches : { message: "Nichts gefunden" });
  }
  if (name === "web_search") {
    try {
      const r = await fetch(`https://duckduckgo.com/html/?q=${encodeURIComponent(args.query)}`, { headers: { "User-Agent": "Mozilla/5.0" } });
      const html = await r.text();
      const results: any[] = [];
      const re = /<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>([^<]+)<\/a>[\s\S]*?<a[^>]+class="result__snippet"[^>]*>([^<]+)/g;
      let m; let i = 0;
      while ((m = re.exec(html)) !== null && i < 5) { results.push({ url: m[1], title: m[2], snippet: m[3].replace(/<[^>]+>/g, "") }); i++; }
      return JSON.stringify(results.length ? results : { message: "Keine Suchergebnisse" });
    } catch { return JSON.stringify({ error: "Web-Suche fehlgeschlagen" }); }
  }
  return JSON.stringify({ error: "Unknown tool" });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const stream = new ReadableStream({
    async start(controller) {
      const enc = new TextEncoder();
      const sendEvent = (obj: any) => controller.enqueue(enc.encode(`data: ${JSON.stringify(obj)}\n\n`));
      const sendDelta = (text: string) => sendEvent({ choices: [{ delta: { content: text } }] });

      try {
        const { messages } = await req.json();
        const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;
        const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

        const system = `Du bist **Mythos AI Agent** mit Tools. Server: mythoscraft.online (SMP).
Nutze Tools aktiv:
- \`get_minecraft_server_status\` für Server-Status & Spielerzahl
- \`search_knowledge_base\` für interne Server-Infos (Regeln, Commands, Plugins)
- \`web_search\` für aktuelle externe Infos
Plane mehrstufig, kombiniere Tools wenn nötig. Antworte am Ende auf Deutsch in Markdown.`;

        const convo: any[] = [{ role: "system", content: system }, ...messages];

        for (let step = 0; step < 6; step++) {
          const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
            method: "POST",
            headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: "google/gemini-3-flash-preview",
              messages: convo,
              tools: TOOLS,
              tool_choice: "auto",
            }),
          });

          if (!r.ok) {
            if (r.status === 429) { sendDelta("\n\n⚠️ Rate limit erreicht."); break; }
            if (r.status === 402) { sendDelta("\n\n⚠️ AI-Credits aufgebraucht."); break; }
            sendDelta("\n\n⚠️ Fehler beim AI Gateway."); break;
          }

          const data = await r.json();
          const msg = data.choices?.[0]?.message;
          if (!msg) break;

          if (msg.tool_calls?.length) {
            convo.push(msg);
            for (const tc of msg.tool_calls) {
              const args = JSON.parse(tc.function.arguments || "{}");
              const label = tc.function.name === "get_minecraft_server_status"
                ? "Hole Server-Status…"
                : tc.function.name === "search_knowledge_base"
                ? `Suche in Knowledge Base: "${args.query}"`
                : `Web-Suche: "${args.query}"`;
              sendEvent({ tool: label });
              const result = await runTool(tc.function.name, args, supabase);
              convo.push({ role: "tool", tool_call_id: tc.id, content: result });
            }
            continue;
          }

          // Final answer — stream char-by-char for nicer UX
          const final = msg.content || "";
          // chunk in ~12 char pieces
          for (let i = 0; i < final.length; i += 14) {
            sendDelta(final.slice(i, i + 14));
            await new Promise(r => setTimeout(r, 8));
          }
          break;
        }

        controller.enqueue(enc.encode("data: [DONE]\n\n"));
        controller.close();
      } catch (e) {
        console.error("agent error:", e);
        const enc = new TextEncoder();
        controller.enqueue(enc.encode(`data: ${JSON.stringify({ choices: [{ delta: { content: "\n\n⚠️ Agent-Fehler: " + (e instanceof Error ? e.message : "unbekannt") } }] })}\n\n`));
        controller.enqueue(enc.encode("data: [DONE]\n\n"));
        controller.close();
      }
    },
  });

  return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
});
