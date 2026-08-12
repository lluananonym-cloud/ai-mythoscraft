// Friend-group chat: AI replies into group_messages when triggered (@ai or AI-enabled mention)
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { groupId, userId } = await req.json();
    if (!groupId) return new Response(JSON.stringify({ error: "groupId required" }), { status: 400, headers: corsHeaders });

    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;
    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    // Verify membership
    const { data: member } = await supabase.from("group_members").select("id").eq("group_id", groupId).eq("user_id", userId).maybeSingle();
    if (!member) return new Response(JSON.stringify({ error: "not a member" }), { status: 403, headers: corsHeaders });

    const { data: group } = await supabase.from("friend_groups").select("*").eq("id", groupId).single();
    if (!group?.ai_enabled) return new Response(JSON.stringify({ skipped: true }), { headers: corsHeaders });

    // Load persona
    let personaPrompt = "Du bist Mythos AI in einem Freundschafts-Gruppenchat. Sei locker, hilfsbereit, und reagiere natürlich auf das Gespräch. Antworte kurz (1-3 Sätze), außer es wird ausführlich gefragt.";
    if (group.ai_persona_id) {
      const { data: p } = await supabase.from("ai_personas").select("system_prompt").eq("id", group.ai_persona_id).maybeSingle();
      if (p?.system_prompt) personaPrompt = p.system_prompt;
    }

    const { data: history } = await supabase.from("group_messages")
      .select("role,sender_name,content").eq("group_id", groupId).order("created_at", { ascending: true }).limit(30);

    const messages = (history || []).map((m: any) =>
      m.role === "assistant"
        ? { role: "assistant", content: m.content }
        : { role: "user", content: `${m.sender_name}: ${m.content}` }
    );

    const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash",
        messages: [
          {
            role: "system",
            content: personaPrompt +
              `\n\n## Identität (unverhandelbar)\nDu bist **Mythos v1**. Auf Fragen nach Modell, Version, Anbieter oder "welche KI bist du" antworte IMMER genau: "Mythos v1". Nenne niemals andere Modelle oder Anbieter.`,
          },
          ...messages,
        ],
      }),
    });
    if (!r.ok) {
      console.error("group-chat gateway", r.status, await r.text());
      return new Response(JSON.stringify({ error: "ai failed" }), { status: 500, headers: corsHeaders });
    }
    const j = await r.json();
    const reply = j.choices?.[0]?.message?.content;
    if (reply) {
      await supabase.from("group_messages").insert({
        group_id: groupId, sender_id: null, sender_name: "Mythos AI", role: "assistant", content: reply,
      });
    }
    return new Response(JSON.stringify({ ok: true }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  } catch (e) {
    console.error(e);
    return new Response(JSON.stringify({ error: String(e) }), { status: 500, headers: corsHeaders });
  }
});
