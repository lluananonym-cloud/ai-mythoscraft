import { aiFetch } from "../_shared/ai.ts";
// Inbound webhook for WhatsApp/SMS messages.
// Currently accepts simple JSON: { phone_number, content, channel?, display_name? }
// Plug Twilio later: parse Twilio's form-urlencoded body (From, Body) and send replies via Twilio API.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const AUTO_HANDOVER_MINUTES = 10;

async function aiReply(history: { role: string; content: string }[], identity: string | null, apiKey: string): Promise<string> {
  const system = identity
    ? `Du bist **${identity}**. Antworte freundlich und kurz auf WhatsApp/SMS (kein Markdown, max. 600 Zeichen).`
    : `Du bist Mythos AI Support für mythoscraft.online. Antworte freundlich und kurz auf WhatsApp/SMS (kein Markdown, max. 600 Zeichen). Bei Unklarheit sage, ein Mitarbeiter meldet sich.`;
  const r = await aiFetch("gateway", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "google/gemini-3-flash-preview",
      messages: [{ role: "system", content: system }, ...history],
    }),
  });
  if (!r.ok) return "Tut mir leid, gerade habe ich technische Probleme. Ein Mitarbeiter meldet sich gleich.";
  const j = await r.json();
  return j.choices?.[0]?.message?.content ?? "...";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const body = await req.json();
    const phone_number = String(body.phone_number || "").trim();
    const content = String(body.content || "").trim();
    const channel = (body.channel || "whatsapp") as "whatsapp" | "sms" | "web";
    const display_name = body.display_name as string | undefined;

    if (!phone_number || !content) {
      return new Response(JSON.stringify({ error: "phone_number and content required" }), {
        status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // Upsert chat
    const { data: existing } = await supabase
      .from("phone_chats").select("*").eq("phone_number", phone_number).maybeSingle();

    let chat = existing;
    if (!chat) {
      const { data: created } = await supabase.from("phone_chats")
        .insert({ phone_number, display_name, mode: "auto", last_message_at: new Date().toISOString() })
        .select().single();
      chat = created;
    } else {
      await supabase.from("phone_chats").update({
        last_message_at: new Date().toISOString(),
        unread_count: (chat.unread_count || 0) + 1,
        ...(display_name && !chat.display_name ? { display_name } : {}),
      }).eq("id", chat.id);
    }

    if (!chat) throw new Error("Chat upsert failed");

    // Insert inbound message
    await supabase.from("phone_messages").insert({
      chat_id: chat.id, direction: "inbound", channel, sender: "user", content,
    });

    // Decide who answers
    const mode = chat.mode as "support" | "ai" | "auto";
    let shouldAiReply = mode === "ai";
    if (mode === "auto") {
      const lastSupport = chat.last_support_response_at ? new Date(chat.last_support_response_at).getTime() : 0;
      const ageMin = (Date.now() - lastSupport) / 60000;
      // First message ever OR support hasn't responded in 10+ min => AI takes over
      shouldAiReply = !lastSupport || ageMin >= AUTO_HANDOVER_MINUTES;
    }

    if (shouldAiReply) {
      const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY");
      if (LOVABLE_API_KEY) {
        // Load last 20 messages for context
        const { data: hist } = await supabase
          .from("phone_messages").select("sender,content")
          .eq("chat_id", chat.id).order("created_at", { ascending: false }).limit(20);
        const ordered = (hist || []).reverse().map((m: any) => ({
          role: m.sender === "user" ? "user" : "assistant",
          content: m.content,
        }));
        const reply = await aiReply(ordered, chat.ai_identity, LOVABLE_API_KEY);

        await supabase.from("phone_messages").insert({
          chat_id: chat.id, direction: "outbound", channel, sender: "ai", content: reply,
        });
        await supabase.from("phone_chats").update({ last_message_at: new Date().toISOString() }).eq("id", chat.id);

        // TODO: send `reply` back to user via Twilio/WhatsApp API when configured
        return new Response(JSON.stringify({ ok: true, ai_replied: true, reply }), {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
    }

    return new Response(JSON.stringify({ ok: true, ai_replied: false, awaiting_support: true }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error("whatsapp-webhook error:", e);
    return new Response(JSON.stringify({ error: e instanceof Error ? e.message : "Unknown" }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
