import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { messages, mode = "support" } = await req.json();
    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY");
    if (!LOVABLE_API_KEY) throw new Error("LOVABLE_API_KEY missing");

    // Build system prompt
    let system = `Du bist Mythos AI, ein freundlicher, kompetenter KI-Assistent. Antworte präzise und in der Sprache des Users (default: Deutsch). Nutze Markdown.`;

    if (mode === "support") {
      const supabase = createClient(
        Deno.env.get("SUPABASE_URL")!,
        Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      );
      const { data: kb } = await supabase
        .from("knowledge_articles")
        .select("title,category,body")
        .eq("is_published", true);

      const kbText = (kb || []).map((a: any) => `### [${a.category}] ${a.title}\n${a.body}`).join("\n\n---\n\n");

      system = `Du bist **Mythos AI**, der offizielle Support-Assistent für den Minecraft-Server **mythoscraft.online** (SMP).
Antworte freundlich, präzise und auf Deutsch (außer der User schreibt in einer anderen Sprache). Nutze Markdown.

Wenn der User nach Server-Status / Spielerzahl fragt: sag, dass er den Agent-Modus aktivieren soll.

Hier ist dein verifiziertes Wissen über den Server:

${kbText || "(noch keine Artikel in der Knowledge Base)"}

Wenn du etwas nicht weißt, sag es ehrlich und verweise auf den Discord oder /helpop ingame.`;
    } else if (mode === "general") {
      system = `Du bist Mythos AI, ein hilfreicher allgemeiner KI-Assistent. Antworte präzise und in der Sprache des Users. Nutze Markdown.`;
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
