import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};

const enc = new TextEncoder();
const sse = (obj: unknown) => enc.encode(`data: ${JSON.stringify(obj)}\n\n`);
const sseDone = () => enc.encode("data: [DONE]\n\n");

async function generateImage(prompt: string, apiKey: string): Promise<string | null> {
  const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "google/gemini-2.5-flash-image",
      messages: [{ role: "user", content: prompt }],
      modalities: ["image", "text"],
    }),
  });
  if (!r.ok) return null;
  const j = await r.json();
  return j.choices?.[0]?.message?.images?.[0]?.image_url?.url ?? null;
}

async function webResearch(query: string, apiKey: string): Promise<string> {
  // Use Lovable AI with google_search tool for grounded research
  const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "google/gemini-2.5-flash",
      messages: [
        { role: "system", content: "Du bist ein Recherche-Agent. Suche im Web, vergleiche Quellen, liefere strukturierte Antwort mit Markdown-Überschriften und Bullet-Points. Nenne Quellen als Links am Ende." },
        { role: "user", content: query },
      ],
      tools: [{ type: "function", function: { name: "google_search", description: "Search the web", parameters: { type: "object", properties: { query: { type: "string" } }, required: ["query"] } } }],
    }),
  });
  if (!r.ok) return `Recherche fehlgeschlagen (${r.status}).`;
  const j = await r.json();
  return j.choices?.[0]?.message?.content ?? "Keine Antwort.";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { messages, mode = "support", conversationId } = await req.json();
    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY");
    if (!LOVABLE_API_KEY) throw new Error("LOVABLE_API_KEY missing");

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const lastUser = [...messages].reverse().find((m: any) => m.role === "user");
    const lastText: string = (lastUser?.content || "").trim();

    // ============== SLASH COMMANDS ==============
    // /identity <name>
    const idMatch = lastText.match(/^\/identity\s+(.+)$/i);
    if (idMatch && conversationId) {
      const newId = idMatch[1].trim().slice(0, 60);
      await supabase.from("conversations").update({ identity_override: newId }).eq("id", conversationId);
      const stream = new ReadableStream({
        start(c) {
          c.enqueue(sse({ choices: [{ delta: { content: `✨ Identität gewechselt zu **${newId}**. Ab jetzt antworte ich als ${newId} in diesem Chat.` } }] }));
          c.enqueue(sseDone()); c.close();
        },
      });
      return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
    }

    // /image <prompt>
    const imgMatch = lastText.match(/^\/image\s+(.+)$/i);
    if (imgMatch) {
      const prompt = imgMatch[1].trim();
      const stream = new ReadableStream({
        async start(c) {
          c.enqueue(sse({ tool: `🎨 Generiere Bild: ${prompt}` }));
          const url = await generateImage(prompt, LOVABLE_API_KEY);
          if (url) {
            // Send image as a dedicated event so the frontend can render a real <img>
            c.enqueue(sse({ image: { url, prompt } }));
            c.enqueue(sse({ choices: [{ delta: { content: `\n*Generiert mit Nano Banana — ${prompt}*` } }] }));
          } else {
            c.enqueue(sse({ choices: [{ delta: { content: "❌ Bild-Generierung fehlgeschlagen. Versuch es nochmal mit einer anderen Beschreibung." } }] }));
          }
          c.enqueue(sseDone()); c.close();
        },
      });
      return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
    }

    // /music <style/description>
    const musicMatch = lastText.match(/^\/music\s+(.+)$/i);
    if (musicMatch) {
      const desc = musicMatch[1].trim();
      const stream = new ReadableStream({
        async start(c) {
          c.enqueue(sse({ tool: `🎵 Komponiere Funk-Track: ${desc}` }));
          // Ask Gemini to design a funk pattern as JSON
          const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
            method: "POST",
            headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: "google/gemini-2.5-flash",
              messages: [
                { role: "system", content: "You design funk music patterns inspired by artists like FUNK SERENO, Brazilian funk, and 70s funk. Output ONLY valid JSON, no markdown." },
                { role: "user", content: `Design a funk groove for: "${desc}". Return JSON exactly in this shape:\n{\n  "title": "string",\n  "bpm": 95-130,\n  "key": "C|D|E|F|G|A|B",\n  "bars": 8,\n  "bass": [16 numbers, midi notes 28-50, 0=rest],\n  "kick":  [16 0/1 values],\n  "snare": [16 0/1 values],\n  "hihat": [16 0/1 values],\n  "stab":  [16 numbers, midi notes 60-75, 0=rest],\n  "vibe": "short description"\n}\nMake it groovy and danceable. Syncopated bass. Snares on 5,13. Kicks on 1,7,11. Hats sixteenths.` },
              ],
              response_format: { type: "json_object" },
            }),
          });
          let pattern: any = null;
          if (r.ok) {
            const j = await r.json();
            try { pattern = JSON.parse(j.choices?.[0]?.message?.content || "{}"); } catch {}
          }
          if (pattern && pattern.bass) {
            c.enqueue(sse({ music: pattern }));
            c.enqueue(sse({ choices: [{ delta: { content: `\n🎶 **${pattern.title || "Funk Groove"}** — ${pattern.bpm} BPM in ${pattern.key || "C"}\n\n*${pattern.vibe || desc}*\n\n▶️ Drück Play oben um's zu hören.` } }] }));
          } else {
            c.enqueue(sse({ choices: [{ delta: { content: "❌ Konnte den Groove nicht komponieren. Nochmal versuchen?" } }] }));
          }
          c.enqueue(sseDone()); c.close();
        },
      });
      return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
    }

    // /research <query>
    const resMatch = lastText.match(/^\/research\s+(.+)$/i);
    if (resMatch) {
      const query = resMatch[1].trim();
      const stream = new ReadableStream({
        async start(c) {
          c.enqueue(sse({ tool: `Deep Research: ${query}` }));
          const result = await webResearch(query, LOVABLE_API_KEY);
          // chunk into deltas so frontend renders progressively
          for (const chunk of result.match(/.{1,40}/gs) || [result]) {
            c.enqueue(sse({ choices: [{ delta: { content: chunk } }] }));
            await new Promise(r => setTimeout(r, 15));
          }
          c.enqueue(sseDone()); c.close();
        },
      });
      return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
    }

    // ============== NORMAL CHAT ==============
    let identityOverride: string | null = null;
    if (conversationId) {
      const { data: conv } = await supabase.from("conversations").select("identity_override").eq("id", conversationId).maybeSingle();
      identityOverride = conv?.identity_override ?? null;
    }

    let system = identityOverride
      ? `Du bist **${identityOverride}**. Du antwortest in dieser Persona, behältst aber alle nützlichen Fähigkeiten. Antworte in der Sprache des Users (default Deutsch). Nutze Markdown.`
      : `Du bist Mythos AI, ein freundlicher, kompetenter KI-Assistent. Antworte präzise und in der Sprache des Users (default: Deutsch). Nutze Markdown.

Du hast diese Slash-Commands zur Verfügung (sag dem User Bescheid wenn passend):
- \`/identity <name>\` — wechselt deine Persona in diesem Chat
- \`/image <beschreibung>\` — generiert ein Bild
- \`/music <stil/vibe>\` — komponiert einen Funk-Groove (z.B. "/music funk sereno style banger")
- \`/research <thema>\` — Deep Research mit Web-Suche`;

    if (mode === "support" && !identityOverride) {
      const { data: kb } = await supabase
        .from("knowledge_articles").select("title,category,body").eq("is_published", true);
      const kbText = (kb || []).map((a: any) => `### [${a.category}] ${a.title}\n${a.body}`).join("\n\n---\n\n");

      system = `Du bist **Mythos AI**, der offizielle Support-Assistent für den Minecraft-Server **mythoscraft.online** (SMP).
Antworte freundlich, präzise und auf Deutsch. Nutze Markdown.

Slash-Commands die der User nutzen kann: \`/identity <name>\`, \`/image <prompt>\`, \`/research <thema>\`.

Wenn der User nach Server-Status fragt: Agent-Modus aktivieren.

Verifiziertes Wissen:

${kbText || "(noch keine Artikel)"}

Wenn unklar: ehrlich sagen + auf Discord oder /helpop verweisen.`;
    }

    const resp = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-3-flash-preview",
        messages: [{ role: "system", content: system }, ...messages],
        stream: true,
      }),
    });

    if (!resp.ok) {
      if (resp.status === 429) return new Response(JSON.stringify({ error: "Rate limit" }), { status: 429, headers: { ...corsHeaders, "Content-Type": "application/json" } });
      if (resp.status === 402) return new Response(JSON.stringify({ error: "Credits exhausted" }), { status: 402, headers: { ...corsHeaders, "Content-Type": "application/json" } });
      const t = await resp.text();
      console.error("Gateway error:", resp.status, t);
      return new Response(JSON.stringify({ error: "AI gateway error" }), { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }

    return new Response(resp.body, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
  } catch (e) {
    console.error("chat error:", e);
    return new Response(JSON.stringify({ error: e instanceof Error ? e.message : "Unknown" }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
