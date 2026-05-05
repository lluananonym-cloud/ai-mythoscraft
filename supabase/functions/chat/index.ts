import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};

const enc = new TextEncoder();
const sse = (obj: unknown) => enc.encode(`data: ${JSON.stringify(obj)}\n\n`);
const sseDone = () => enc.encode("data: [DONE]\n\n");

async function generateImage(prompt: string, apiKey: string): Promise<{ url: string | null; error?: string }> {
  // Try a sequence of models; some prompts are refused by one but accepted by another
  const models = [
    "google/gemini-2.5-flash-image",
    "google/gemini-3.1-flash-image-preview",
  ];
  let lastErr = "Unknown";
  for (const model of models) {
    try {
      const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
        method: "POST",
        headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          model,
          messages: [{
            role: "user",
            content: `Generate a high quality image: ${prompt}`,
          }],
          modalities: ["image", "text"],
        }),
      });
      if (!r.ok) {
        const txt = await r.text();
        lastErr = `${model}: HTTP ${r.status} — ${txt.slice(0, 200)}`;
        console.error("[image] gateway error", lastErr);
        continue;
      }
      const j = await r.json();
      const url = j.choices?.[0]?.message?.images?.[0]?.image_url?.url;
      if (url) return { url };
      lastErr = `${model}: no image in response. raw=${JSON.stringify(j).slice(0, 300)}`;
      console.error("[image] empty response", lastErr);
    } catch (e) {
      lastErr = `${model}: exception ${e instanceof Error ? e.message : String(e)}`;
      console.error("[image] exception", lastErr);
    }
  }
  return { url: null, error: lastErr };
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
    const { messages, mode = "support", conversationId, personaId, userId: clientUserId } = await req.json();
    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY");
    if (!LOVABLE_API_KEY) throw new Error("LOVABLE_API_KEY missing");

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const lastUser = [...messages].reverse().find((m: any) => m.role === "user");
    const lastTextRaw = lastUser?.content;
    // content can be either a string or multimodal array (text + images). Extract text part for command parsing.
    const lastText: string = typeof lastTextRaw === "string"
      ? lastTextRaw.trim()
      : Array.isArray(lastTextRaw)
        ? (lastTextRaw.find((p: any) => p.type === "text")?.text || "").trim()
        : "";

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
          const { url, error } = await generateImage(prompt, LOVABLE_API_KEY);
          if (url) {
            c.enqueue(sse({ image: { url, prompt } }));
            c.enqueue(sse({ choices: [{ delta: { content: `\n*Generiert mit Nano Banana — ${prompt}*` } }] }));
          } else {
            const hint = error?.includes("safety") || error?.includes("blocked") || error?.includes("SAFETY")
              ? "Das Modell hat den Prompt abgelehnt (Safety-Filter). Versuch es mit einer detaillierteren, neutraleren Beschreibung."
              : "Versuch eine längere/detailliertere Beschreibung (z.B. \"ein bärtiger Mann in einer Kneipe, fotorealistisch\" statt nur \"günther\").";
            c.enqueue(sse({ choices: [{ delta: { content: `❌ Bild-Generierung fehlgeschlagen.\n\n${hint}` } }] }));
            console.error("[/image] failed:", error);
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

    // /translate <lang> <text>  OR  /translate <lang>  (translates last assistant msg)
    const trMatch = lastText.match(/^\/translate\s+(\S+)(?:\s+([\s\S]+))?$/i);
    if (trMatch) {
      const lang = trMatch[1];
      let toTranslate = trMatch[2]?.trim();
      if (!toTranslate) {
        const lastAssist = [...messages].reverse().find((m: any) => m.role === "assistant");
        toTranslate = typeof lastAssist?.content === "string" ? lastAssist.content : "";
      }
      const stream = new ReadableStream({
        async start(c) {
          c.enqueue(sse({ tool: `🌍 Übersetze nach ${lang}` }));
          const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
            method: "POST",
            headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: "google/gemini-2.5-flash",
              messages: [
                { role: "system", content: `Übersetze den folgenden Text nach ${lang}. Behalte Markdown-Formatierung bei. Antworte NUR mit der Übersetzung.` },
                { role: "user", content: toTranslate || "(leer)" },
              ],
              stream: true,
            }),
          });
          if (!r.ok || !r.body) { c.enqueue(sse({ choices: [{ delta: { content: "❌ Übersetzung fehlgeschlagen." } }] })); c.enqueue(sseDone()); c.close(); return; }
          const reader = r.body.getReader();
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            c.enqueue(value);
          }
          c.close();
        },
      });
      return new Response(stream, { headers: { ...corsHeaders, "Content-Type": "text/event-stream" } });
    }

    // /summarize  (summarizes whole conversation)
    if (/^\/summarize\b/i.test(lastText)) {
      const convoText = messages.slice(-30).map((m: any) =>
        `${m.role.toUpperCase()}: ${typeof m.content === "string" ? m.content : "[multimodal]"}`
      ).join("\n");
      const stream = new ReadableStream({
        async start(c) {
          c.enqueue(sse({ tool: "📝 Fasse Konversation zusammen" }));
          const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
            method: "POST",
            headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: "google/gemini-2.5-flash",
              messages: [
                { role: "system", content: "Fasse die Konversation als strukturierte Markdown-Notiz zusammen: **TL;DR**, **Wichtige Punkte** (Bullets), **Offene Fragen / ToDos**." },
                { role: "user", content: convoText },
              ],
              stream: true,
            }),
          });
          if (!r.ok || !r.body) { c.enqueue(sse({ choices: [{ delta: { content: "❌ Zusammenfassung fehlgeschlagen." } }] })); c.enqueue(sseDone()); c.close(); return; }
          const reader = r.body.getReader();
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            c.enqueue(value);
          }
          c.close();
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
    let convUserId: string | null = clientUserId || null;
    if (conversationId) {
      const { data: conv } = await supabase.from("conversations").select("identity_override,user_id").eq("id", conversationId).maybeSingle();
      identityOverride = conv?.identity_override ?? null;
      if (conv?.user_id) convUserId = conv.user_id;
    }

    // Load persona if requested
    let personaPrompt: string | null = null;
    let personaName: string | null = null;
    if (personaId) {
      const { data: p } = await supabase.from("ai_personas").select("name,system_prompt,is_public,user_id").eq("id", personaId).maybeSingle();
      if (p && (p.is_public || p.user_id === convUserId)) {
        personaPrompt = p.system_prompt;
        personaName = p.name;
        // bump use_count async
        supabase.rpc("noop").catch(() => {});
        await supabase.from("ai_personas").update({ use_count: (await supabase.from("ai_personas").select("use_count").eq("id", personaId).single()).data?.use_count + 1 || 1 }).eq("id", personaId);
      }
    }

    // Load user memories
    let memoryBlock = "";
    if (convUserId) {
      const { data: mems } = await supabase.from("user_memories").select("content,category").eq("user_id", convUserId).order("created_at", { ascending: false }).limit(40);
      if (mems && mems.length > 0) {
        memoryBlock = `\n\n## Was du über den User weißt (aus früheren Chats)\n${mems.map((m: any) => `- (${m.category}) ${m.content}`).join("\n")}\n\nNutze dieses Wissen natürlich in deinen Antworten — beziehe dich nicht ständig drauf, sondern wirke einfach so als kenntest du den User.`;
      }
    }

    let system: string;
    if (personaPrompt) {
      system = `${personaPrompt}\n\nAntworte in der Sprache des Users (default Deutsch). Nutze Markdown wenn sinnvoll.${memoryBlock}`;
    } else if (identityOverride) {
      system = `Du bist **${identityOverride}**. Du antwortest in dieser Persona, behältst aber alle nützlichen Fähigkeiten. Antworte in der Sprache des Users (default Deutsch). Nutze Markdown.${memoryBlock}`;
    } else if (mode === "support") {
      const { data: kb } = await supabase
        .from("knowledge_articles").select("title,category,body").eq("is_published", true);
      const kbText = (kb || []).map((a: any) => `### [${a.category}] ${a.title}\n${a.body}`).join("\n\n---\n\n");
      system = `Du bist **Mythos AI**, der offizielle Support-Assistent für den Minecraft-Server **mythoscraft.online** (SMP).
Antworte freundlich, präzise und auf Deutsch. Nutze Markdown.

Slash-Commands die der User nutzen kann: \`/identity <name>\`, \`/image <prompt>\`, \`/music <vibe>\`, \`/research <thema>\`.

Verifiziertes Wissen:

${kbText || "(noch keine Artikel)"}

Wenn unklar: ehrlich sagen + auf Discord oder /helpop verweisen.${memoryBlock}`;
    } else {
      system = `Du bist Mythos AI, ein freundlicher, kompetenter KI-Assistent. Antworte präzise und in der Sprache des Users (default: Deutsch). Nutze Markdown.

Du hast diese Slash-Commands zur Verfügung (sag dem User Bescheid wenn passend):
- \`/identity <name>\` — wechselt deine Persona in diesem Chat
- \`/image <beschreibung>\` — generiert ein Bild
- \`/music <stil/vibe>\` — komponiert einen Funk-Groove
- \`/research <thema>\` — Deep Research mit Web-Suche

Bilder & PDFs: Du kannst hochgeladene Bilder direkt sehen und analysieren.${memoryBlock}`;
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
