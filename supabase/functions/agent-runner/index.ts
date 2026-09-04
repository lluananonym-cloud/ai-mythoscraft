import { aiFetch } from "../_shared/ai.ts";
// Runs scheduled agent_tasks. Triggered by pg_cron every minute.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  const { data: due } = await supabase
    .from("agent_tasks")
    .select("*")
    .eq("status", "pending")
    .lte("schedule_at", new Date().toISOString())
    .limit(10);

  const results: any[] = [];
  for (const t of due || []) {
    await supabase.from("agent_tasks").update({ status: "running" }).eq("id", t.id);
    try {
      // Load user memories for context
      const { data: mems } = await supabase.from("user_memories").select("content,category").eq("user_id", t.user_id).limit(20);
      const memBlock = mems?.length ? `\n\nWas du über den User weißt:\n${mems.map((m: any) => `- ${m.content}`).join("\n")}` : "";

      const r = await aiFetch("gateway", {
        method: "POST",
        headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "google/gemini-2.5-flash",
          messages: [
            { role: "system", content: `Du bist Mythos AI im Auto-Agent-Modus. Bearbeite die Aufgabe gründlich, recherchiere wenn nötig, gib eine vollständige Markdown-Antwort.${memBlock}` },
            { role: "user", content: t.prompt },
          ],
          tools: [{ type: "function", function: { name: "google_search", description: "Search the web", parameters: { type: "object", properties: { query: { type: "string" } }, required: ["query"] } } }],
        }),
      });
      const j = await r.json();
      const result = j.choices?.[0]?.message?.content || "(keine Antwort)";

      // Save result as a new conversation message for the user to read in their chat
      const { data: conv } = await supabase.from("conversations").insert({
        user_id: t.user_id, title: `🤖 ${t.title}`, mode: "agent",
      }).select().single();
      if (conv) {
        await supabase.from("messages").insert([
          { conversation_id: conv.id, role: "user", content: t.prompt },
          { conversation_id: conv.id, role: "assistant", content: result },
        ]);
      }

      // Reschedule if recurring
      let nextStatus = "done";
      let nextSchedule: string | null = null;
      if (t.recurrence === "daily" || t.recurrence === "weekly") {
        const next = new Date(t.schedule_at);
        next.setDate(next.getDate() + (t.recurrence === "daily" ? 1 : 7));
        nextSchedule = next.toISOString();
        nextStatus = "pending";
      }
      await supabase.from("agent_tasks").update({
        status: nextStatus,
        last_run_at: new Date().toISOString(),
        last_result: result.slice(0, 2000),
        ...(nextSchedule ? { schedule_at: nextSchedule } : {}),
      }).eq("id", t.id);
      results.push({ id: t.id, ok: true });
    } catch (e) {
      console.error("agent-runner error", t.id, e);
      await supabase.from("agent_tasks").update({
        status: "failed", last_result: String(e), last_run_at: new Date().toISOString(),
      }).eq("id", t.id);
      results.push({ id: t.id, ok: false, error: String(e) });
    }
  }
  return new Response(JSON.stringify({ processed: results.length, results }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});
