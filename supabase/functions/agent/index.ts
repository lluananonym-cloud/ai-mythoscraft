import { aiFetch } from "../_shared/ai.ts";
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

const MODEL = "google/gemini-3-flash-preview";
const toolLabel = (name: string, args: any) =>
  name === "get_minecraft_server_status" ? "Hole Server-Status…"
    : name === "search_knowledge_base" ? `Suche in Knowledge Base: "${args.query}"`
    : `Web-Suche: "${args.query}"`;

/** Ein Agent: ruft Werkzeuge auf, bis er antwortet. Liefert die Antwort (oder "" bei Fehler). */
async function agentLoop(convo: any[], key: string, supabase: any, onTool: (label: string) => void, maxSteps = 6): Promise<string> {
  for (let step = 0; step < maxSteps; step++) {
    const r = await aiFetch("gateway", {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model: MODEL, messages: convo, tools: TOOLS, tool_choice: "auto" }),
    });
    if (!r.ok) throw new Error(r.status === 429 ? "Rate limit erreicht" : r.status === 402 ? "AI-Credits aufgebraucht" : "Fehler beim AI Gateway");
    const msg = (await r.json()).choices?.[0]?.message;
    if (!msg) return "";
    if (msg.tool_calls?.length) {
      convo.push(msg);
      for (const tc of msg.tool_calls) {
        const args = JSON.parse(tc.function.arguments || "{}");
        onTool(toolLabel(tc.function.name, args));
        convo.push({ role: "tool", tool_call_id: tc.id, content: await runTool(tc.function.name, args, supabase) });
      }
      continue;
    }
    return msg.content || "";
  }
  return "";
}

/** Multi-Agent: Aufgabe in unabhängige Teilaufgaben zerlegen (max. 3). */
async function planSubtasks(question: string, key: string): Promise<{ title: string; task: string }[]> {
  const r = await aiFetch("gateway", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: MODEL,
      messages: [{
        role: "user",
        content: "Zerlege die Anfrage in 2–3 UNABHÄNGIGE Teilrecherchen, die parallel erledigt werden können. " +
          "Ist die Anfrage einfach (Smalltalk, eine einzelne Frage, ein einzelner Fakt), gib genau EINE Teilaufgabe zurück. " +
          'Antworte NUR mit JSON: {"subtasks":[{"title":"kurz","task":"was genau herausfinden"}]}\n\nANFRAGE:\n' + question,
      }],
    }),
  });
  if (!r.ok) return [];
  const text = (await r.json()).choices?.[0]?.message?.content || "";
  try {
    const j = JSON.parse(text.match(/\{[\s\S]*\}/)?.[0] || "{}");
    return Array.isArray(j.subtasks) ? j.subtasks.filter((s: any) => s?.task).slice(0, 3) : [];
  } catch { return []; }
}

const lastUserText = (messages: any[]) => {
  const m = [...messages].reverse().find((x) => x.role === "user");
  if (!m) return "";
  return typeof m.content === "string" ? m.content : (m.content || []).map((p: any) => p.text || "").join(" ");
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const stream = new ReadableStream({
    async start(controller) {
      const enc = new TextEncoder();
      const sendEvent = (obj: any) => controller.enqueue(enc.encode(`data: ${JSON.stringify(obj)}\n\n`));
      const sendDelta = (text: string) => sendEvent({ choices: [{ delta: { content: text } }] });
      const streamText = async (text: string) => {
        for (let i = 0; i < text.length; i += 14) { sendDelta(text.slice(i, i + 14)); await new Promise((r) => setTimeout(r, 8)); }
      };

      try {
        const { messages } = await req.json();
        const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;
        const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

        const system = `Du bist **Mythos AI Agent** mit Tools. Server: mythoscraft.online (SMP).
Nutze Tools aktiv:
- \`get_minecraft_server_status\` für Server-Status & Spielerzahl
- \`search_knowledge_base\` für interne Server-Infos (Regeln, Commands, Plugins)
- \`web_search\` für aktuelle externe Infos
Plane mehrstufig, kombiniere Tools wenn nötig. Antworte am Ende auf Deutsch in Markdown.
Du bist **Mythos v1** — wenn du nach deinem Modell, deiner Version oder deinem Anbieter gefragt wirst, antworte IMMER genau "Mythos v1" und nenne niemals andere Modelle oder Anbieter.`;

        const question = lastUserText(messages);
        const plan = question ? await planSubtasks(question, LOVABLE_API_KEY) : [];

        if (plan.length < 2) {
          // Einfache Anfrage: ein Agent wie bisher.
          const final = await agentLoop([{ role: "system", content: system }, ...messages], LOVABLE_API_KEY, supabase, (label) => sendEvent({ tool: label }));
          await streamText(final || "Ich konnte dazu leider nichts finden.");
        } else {
          // Multi-Agent: Teil-Agenten recherchieren parallel, danach fasst der Haupt-Agent zusammen.
          const status = plan.map((p, i) => ({ i, title: String(p.title || `Teil ${i + 1}`).slice(0, 60), status: "läuft", phase: "startet…" }));
          sendEvent({ agents: status });
          const results = await Promise.all(plan.map(async (p, i) => {
            const sub = [
              { role: "system", content: system + "\n\nDu bist Teil-Agent " + (i + 1) + " von " + plan.length + ". Bearbeite NUR deine Teilaufgabe und antworte knapp mit den gefundenen Fakten und Quellen." },
              { role: "user", content: "Gesamtanfrage: " + question + "\n\nDeine Teilaufgabe: " + p.task },
            ];
            try {
              const text = await agentLoop(sub, LOVABLE_API_KEY, supabase, (label) => { status[i].phase = label; sendEvent({ agents: status }); }, 5);
              status[i].status = "fertig"; status[i].phase = "";
              sendEvent({ agents: status });
              return text;
            } catch (e) {
              status[i].status = "fehler"; status[i].phase = e instanceof Error ? e.message : "Fehler";
              sendEvent({ agents: status });
              return "";
            }
          }));
          const r = await aiFetch("gateway", {
            method: "POST",
            headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: MODEL,
              messages: [
                { role: "system", content: system },
                ...messages,
                { role: "user", content: "Ergebnisse deiner parallelen Teil-Agenten:\n\n" + plan.map((p, i) => `### ${status[i].title}\n${results[i] || "(kein Ergebnis)"}`).join("\n\n") +
                  "\n\nFasse das jetzt zu EINER vollständigen, gut strukturierten Antwort auf die ursprüngliche Anfrage zusammen (Markdown, Deutsch)." },
              ],
            }),
          });
          const final = r.ok ? (await r.json()).choices?.[0]?.message?.content || "" : "";
          await streamText(final || results.filter(Boolean).join("\n\n") || "⚠️ Die Agenten konnten keine Antwort liefern.");
        }

        controller.enqueue(enc.encode("data: [DONE]\n\n"));
        controller.close();
      } catch (e) {
        console.error("agent error:", e);
        sendDelta("\n\n⚠️ Agent-Fehler: " + (e instanceof Error ? e.message : "unbekannt"));
        controller.enqueue(enc.encode("data: [DONE]\n\n"));
        controller.close();
      }
    },
  });

  return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
});
