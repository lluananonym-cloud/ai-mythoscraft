import { aiFetch } from "../_shared/ai.ts";
import { mythosIdentity, mythosIdentityReminder } from "../_shared/identity.ts";
import { MYTHOS_CATALOG, MYTHOS_FILES } from "../_shared/catalog.ts";
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
      const r = await aiFetch("gateway", {
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
  const r = await aiFetch("gateway", {
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

// ============== MYTHOS MODEL CATALOG ==============
// Identität kommt zentral aus _shared/identity.ts
type Tune = {
  model: string;
  reasoning?: "none" | "low" | "medium" | "high";
  fast?: boolean;      // OpenAI priority serving tier -> tiefere Latenz
  maxOut?: number;     // Output-Cap -> Instant/Low antworten spürbar schneller
  web?: boolean;       // darf live im Web suchen
  temperature?: number;
  memory?: boolean;   // darf Langzeit-Erinnerungen laden
};
type Resolved = Tune & { effortLabel: string; familyLabel: string; style: string };

const MYTHOS_MAP: Record<string, Tune> = {
  // --- Mythos v1: Allrounder ---
  "v1:instant": { model: "google/gemini-3.1-flash-lite", maxOut: 320, web: false, temperature: 0.4 },
  "v1:low": { model: "google/gemini-3.6-flash", maxOut: 700, web: false, temperature: 0.6 },
  "v1:normal": { model: "google/gemini-3.6-flash", maxOut: 1600, web: true, temperature: 0.7 },
  "v1:high": { model: "google/gemini-3.1-pro-preview", maxOut: 3500, web: true, temperature: 0.7 },
  "v1:ultra": { model: "openai/gpt-5.5", reasoning: "medium", maxOut: 5000, web: true },

  // --- MythosCode v1.1 ---
  "code11:instant": { model: "openai/gpt-5.4-nano", reasoning: "none", maxOut: 400, web: false },
  "code11:low": { model: "openai/gpt-5.4-mini", reasoning: "none", fast: true, maxOut: 900, web: false },
  "code11:normal": { model: "openai/gpt-5.4", reasoning: "low", maxOut: 2200, web: true },
  "code11:high": { model: "openai/gpt-5.4", reasoning: "high", maxOut: 4000, web: true },
  "code11:ultra": { model: "openai/gpt-5.5", reasoning: "medium", maxOut: 6000, web: true },
  "code11:ultracode": { model: "openai/gpt-5.5", reasoning: "high", maxOut: 9000, web: true },

  // --- Mythos v2 (Pro) ---
  "v2:instant": { model: "openai/gpt-5.6-luna", reasoning: "none", maxOut: 400, web: false },
  "v2:low": { model: "openai/gpt-5.6-luna", reasoning: "none", fast: true, maxOut: 1000, web: false },
  "v2:normal": { model: "openai/gpt-5.6-sol", reasoning: "low", fast: true, maxOut: 3000, web: true },
  "v2:high": { model: "openai/gpt-5.6-sol", reasoning: "medium", fast: true, maxOut: 6000, web: true },
  "v2:ultra": { model: "openai/gpt-5.6-sol", reasoning: "high", maxOut: 12000, web: true },

  // --- MythosCode v1.5 (Pro) ---
  "code15:instant": { model: "openai/gpt-5.6-luna", reasoning: "none", maxOut: 500, web: false },
  "code15:low": { model: "openai/gpt-5.6-terra", reasoning: "none", fast: true, maxOut: 1200, web: false },
  "code15:normal": { model: "openai/gpt-5.6-sol", reasoning: "none", fast: true, maxOut: 3000, web: true },
  "code15:high": { model: "openai/gpt-5.6-sol", reasoning: "none", fast: true, maxOut: 5000, web: true },
  "code15:ultra": { model: "openai/gpt-5.5", reasoning: "high", maxOut: 9000, web: true },
  "code15:giga": { model: "openai/gpt-5.5", reasoning: "high", maxOut: 16000, web: true },
};

const FAMILY_LABEL: Record<string, string> = {
  v1: "Mythos v1", code11: "MythosCode v1.1", v2: "Mythos v2", code15: "MythosCode v1.5",
};
const EFFORT_MEMORY: Record<string, boolean> = {
  instant: false, low: false, normal: true, high: true, ultra: true, ultracode: true, giga: true,
};
const EFFORT_LABEL: Record<string, string> = {
  instant: "Instant", low: "Low", normal: "Normal", high: "High",
  ultra: "Ultra", ultracode: "Ultra Code", giga: "Giga Code",
};
const EFFORT_STYLE: Record<string, string> = {
  instant: "Modus INSTANT: Antworte in maximal 1-2 Sätzen. Keine Vorrede, keine Überschriften, keine Listen, keine Rückfragen, kein Markdown außer Code-Blöcken. Direkt die Antwort, sofort.",
  low: "Modus LOW: Antworte kurz und direkt — ein kleiner Absatz oder max. 3 Bullets. Keine Einleitung, kein Fazit.",
  normal: "Modus NORMAL: Ausbalanciert — klar strukturiert, angemessen ausführlich, mit kurzem Beispiel wenn hilfreich.",
  high: "Modus HIGH: Denke gründlich durch, prüfe Randfälle, strukturiere mit Überschriften/Bullets und begründe deine Empfehlung.",
  ultra: "Modus ULTRA: Maximal sorgfältig — Annahmen offenlegen, Alternativen vergleichen, tiefe, vollständige und präzise Antwort mit klarer Struktur.",
  ultracode: "Modus ULTRA CODE: Vollständige, lauffähige Implementierungen, Fehlerbehandlung, Tests/Beispiele und kurze Architektur-Begründung.",
  giga: "Modus GIGA CODE: Projekt-Qualität — Architektur, vollständiger Code, Edge-Cases, Tests, Performance- und Security-Hinweise.",
};

function resolveMythos(id: unknown): Resolved {
  const key = typeof id === "string" && MYTHOS_MAP[id] ? id : "v1:normal";
  const [fam, eff] = key.split(":");
  const m = MYTHOS_MAP[key];
  return {
    ...m,
    memory: m.memory ?? EFFORT_MEMORY[eff] ?? true,
    familyLabel: FAMILY_LABEL[fam],
    effortLabel: EFFORT_LABEL[eff],
    style: EFFORT_STYLE[eff],
  };
}


function needsWeb(text: string): { need: boolean; query: string } {
  const t = text.toLowerCase();
  if (t.startsWith("/")) return { need: false, query: "" };
  const patterns = [
    /\b(aktuell|aktuelle|aktuellen|heute|gestern|neueste|neuste|news|nachrichten|gerade eben|momentan)\b/,
    /\b(wetter|preis|preise|kurs|aktie|bitcoin|ergebnis|spielstand|tabelle|release|version|update)\b/,
    /\b(suche|google|recherchier|recherchiere|schau nach|im internet|online nach)\b/,
    /\b(wann (kommt|ist|war)|wer ist|wer hat|wieviel kostet|wie viel kostet)\b/,
    /https?:\/\//,
  ];
  if (patterns.some(r => r.test(t))) return { need: true, query: text.slice(0, 300) };
  return { need: false, query: "" };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { messages, mode = "support", conversationId, personaId, userId: clientUserId, model: requestedModel, mythos, voice: voiceMode } = await req.json();
    const resolved = resolveMythos(mythos);
    const ALLOWED_MODELS = new Set([
      "openai/gpt-5.5-pro",
      "openai/gpt-5.5",
      "openai/gpt-5.4",
      "google/gemini-3.1-pro-preview",
    ]);
    const chatModel = (typeof requestedModel === "string" && ALLOWED_MODELS.has(requestedModel))
      ? requestedModel
      : resolved.model;
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
          const r = await aiFetch("gateway", {
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
          const r = await aiFetch("gateway", {
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
          const r = await aiFetch("gateway", {
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
        // bump use_count async (non-blocking, ignore errors)
        const cur = await supabase.from("ai_personas").select("use_count").eq("id", personaId).maybeSingle();
        await supabase.from("ai_personas").update({ use_count: (cur.data?.use_count ?? 0) + 1 }).eq("id", personaId);
      }
    }

    // Load user memories
    let memoryBlock = "";
    if (convUserId && resolved.memory !== false) {
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

    if (voiceMode) system += `\n\n## Sprach-Modus\nDeine Antwort wird laut vorgelesen. Antworte daher in gesprochener Sprache: kurze Sätze, maximal 3-5 Sätze, keine Listen, keine Emojis, kein Markdown, keine Links, keine Code-Blöcke.`;
    const identityOpts = {
      modelLabel: resolved.familyLabel,
      effortLabel: resolved.effortLabel,
      surface: "app" as const,
      persona: personaName ?? identityOverride ?? null,
      lang: "de" as const,
    };
    system = `${mythosIdentity(identityOpts)}\n\n${MYTHOS_CATALOG}\n\n${MYTHOS_FILES}\n\n${system}\n\n## Antwort-Aufwand: ${resolved.effortLabel}\n${resolved.style}\n\n${mythosIdentityReminder(identityOpts)}`;

    // ---- optional live web search (streamed status first) ----
    // Instant/Low suchen nie im Web -> keine Extra-Latenz.
    const web = needsWeb(lastText);
    let webContext = "";
    let searchQuery = "";
    if (web.need && resolved.web !== false) {
      searchQuery = web.query;
    }

    // Instant hält den Kontext klein -> deutlich schnellere Time-to-first-token.
    const histLimit = resolved.effortLabel === "Instant" ? 6 : resolved.effortLabel === "Low" ? 12 : 40;
    const trimmed = Array.isArray(messages) ? messages.slice(-histLimit) : messages;
    const outMessages: any[] = [{ role: "system", content: system }, ...trimmed];

    const isOpenAI = String(chatModel).startsWith("openai/");
    const body: Record<string, unknown> = {
      model: chatModel,
      messages: outMessages,
      stream: true,
    };
    if (resolved.reasoning) body.reasoning_effort = resolved.reasoning;
    if (resolved.fast && isOpenAI) body.service_tier = "priority";
    if (resolved.maxOut) {
      if (isOpenAI) body.max_completion_tokens = resolved.maxOut;
      else body.max_tokens = resolved.maxOut;
    }
    if (resolved.temperature !== undefined && !isOpenAI) body.temperature = resolved.temperature;


    const startUpstream = async () => {
      const r = await aiFetch("gateway", {
        method: "POST",
        headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      return r;
    };

    if (searchQuery) {
      const stream = new ReadableStream({
        async start(c) {
          c.enqueue(sse({ search: searchQuery }));
          try {
            webContext = await webResearch(searchQuery, LOVABLE_API_KEY);
          } catch (_e) { webContext = ""; }
          if (webContext) {
            outMessages[0] = {
              role: "system",
              content: `${system}\n\n## Frische Web-Ergebnisse (gerade recherchiert)\n${webContext.slice(0, 6000)}\n\nNutze diese Ergebnisse für deine Antwort und nenne die Quellen kurz.`,
            };
            body.messages = outMessages;
          }
          const r = await startUpstream();
          if (!r.ok || !r.body) {
            c.enqueue(sse({ choices: [{ delta: { content: "❌ Antwort fehlgeschlagen. Bitte nochmal versuchen." } }] }));
            c.enqueue(sseDone()); c.close(); return;
          }
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

    const resp = await startUpstream();

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
