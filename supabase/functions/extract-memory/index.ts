import { aiFetch } from "../_shared/ai.ts";
// Background memory extractor — called by frontend after each user message.
// Looks at the last user message, extracts durable facts (name, hobbies, preferences),
// and inserts them into user_memories. Skips if nothing worth remembering.

import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const auth = req.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } });

    const authClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
    const token = auth.replace("Bearer ", "");
    const { data: claimsData, error: ce } = await authClient.auth.getClaims(token);
    if (ce || !claimsData?.claims?.sub) return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    const userId = claimsData.claims.sub;

    const { text } = await req.json();
    if (!text || typeof text !== "string" || text.length < 6) {
      return new Response(JSON.stringify({ extracted: [] }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }

    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY");
    if (!LOVABLE_API_KEY) throw new Error("LOVABLE_API_KEY missing");

    const r = await aiFetch("gateway", {
      method: "POST",
      headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash-lite",
        messages: [
          { role: "system", content: `You extract durable, useful facts about a USER from a single chat message they wrote, that an AI assistant should remember long-term. ONLY extract: name, age, location, hobbies, profession, preferences, owned things (Minecraft server, pets), goals, relationships. Skip: questions, requests, ephemeral state ("I am hungry now"), opinions about external things. If nothing useful, return empty list. Output English short third-person facts (e.g. "Plays Minecraft", "Likes coding", "Owns a server called mythoscraft"). Max 3 facts.` },
          { role: "user", content: text },
        ],
        tools: [{
          type: "function",
          function: {
            name: "save_memories",
            description: "Save extracted user facts",
            parameters: {
              type: "object",
              properties: {
                memories: {
                  type: "array",
                  items: {
                    type: "object",
                    properties: {
                      content: { type: "string" },
                      category: { type: "string", enum: ["personal", "preference", "hobby", "minecraft", "work", "other"] },
                    },
                    required: ["content", "category"],
                  },
                },
              },
              required: ["memories"],
            },
          },
        }],
        tool_choice: { type: "function", function: { name: "save_memories" } },
      }),
    });
    if (!r.ok) {
      console.error("extract-memory gateway", r.status);
      return new Response(JSON.stringify({ extracted: [] }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }
    const j = await r.json();
    const args = j.choices?.[0]?.message?.tool_calls?.[0]?.function?.arguments;
    if (!args) return new Response(JSON.stringify({ extracted: [] }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    let parsed: any = {};
    try { parsed = JSON.parse(args); } catch { return new Response(JSON.stringify({ extracted: [] }), { headers: { ...corsHeaders, "Content-Type": "application/json" } }); }
    const memories = (parsed.memories || []).filter((m: any) => m && m.content);
    if (memories.length === 0) {
      return new Response(JSON.stringify({ extracted: [] }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }

    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    // Avoid duplicates: fetch recent memories
    const { data: existing } = await supabase.from("user_memories").select("content").eq("user_id", userId).order("created_at", { ascending: false }).limit(50);
    const existingSet = new Set((existing || []).map((m: any) => m.content.toLowerCase().trim()));

    const toInsert = memories
      .filter((m: any) => !existingSet.has(m.content.toLowerCase().trim()))
      .map((m: any) => ({ user_id: userId, content: String(m.content).slice(0, 280), category: m.category || "general", source: "auto" }));

    if (toInsert.length > 0) {
      await supabase.from("user_memories").insert(toInsert);
    }

    return new Response(JSON.stringify({ extracted: toInsert.map((m: any) => m.content) }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error("extract-memory error", e);
    return new Response(JSON.stringify({ extracted: [], error: e instanceof Error ? e.message : "Error" }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
