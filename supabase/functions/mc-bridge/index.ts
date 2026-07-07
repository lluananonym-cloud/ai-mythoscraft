// Minecraft Plugin Bridge — receives events/chat from Bukkit/Spigot/Paper plugin
// Auth: Authorization: Bearer sk-mc-XXXXXXXX...
//
// Endpoints (all POST to /mc-bridge, body decides action):
//   { action: "ping" }                                          -> { ok, server: { name } }
//   { action: "chat", player, message }                         -> { reply: "..." }   (only if message starts with chat_trigger)
//   { action: "event", type: "join"|"leave"|"death", player, content? } -> { reply?: "..." }   (greet on join, comment on death)
//
// All requests log to mc_events. AI replies stream-free (single response) for plugin simplicity.

import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
};

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join("");
}

async function callAI(systemPrompt: string, userPrompt: string, apiKey: string): Promise<string> {
  const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "google/gemini-3-flash-preview",
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: userPrompt },
      ],
      max_tokens: 250,
    }),
  });
  if (!r.ok) {
    console.error("AI gateway error", r.status, await r.text());
    return "Hmm, ich kann gerade nicht antworten.";
  }
  const j = await r.json();
  return (j.choices?.[0]?.message?.content ?? "").trim() || "...";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const auth = req.headers.get("Authorization") || req.headers.get("authorization") || "";
    const m = auth.match(/^Bearer\s+(sk-mc-[A-Za-z0-9]+)$/);
    if (!m) {
      return new Response(JSON.stringify({ error: "Missing or invalid Authorization. Use: Authorization: Bearer sk-mc-..." }), {
        status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const key = m[1];
    const key_hash = await sha256(key);

    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY");
    if (!LOVABLE_API_KEY) throw new Error("LOVABLE_API_KEY missing");

    const { data: server } = await supabase.from("mc_servers").select("*").eq("key_hash", key_hash).maybeSingle();
    if (!server || server.revoked) {
      return new Response(JSON.stringify({ error: "Invalid or revoked key" }), {
        status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Update last_seen
    await supabase.from("mc_servers").update({
      last_seen_at: new Date().toISOString(),
      total_events: Number(server.total_events || 0) + 1,
    }).eq("id", server.id);

    const body = await req.json().catch(() => ({}));
    const action = body.action || "ping";

    // Optional persona system prompt
    let personaPrompt = "Du bist die Server-AI für einen Minecraft-Server. Antworte sehr kurz (max 2 Sätze), freundlich, manchmal witzig. Keine Markdown-Symbole, plain text — wird im Minecraft-Chat angezeigt. Antworte auf Deutsch.";
    if (server.ai_persona_id) {
      const { data: p } = await supabase.from("ai_personas").select("system_prompt,name").eq("id", server.ai_persona_id).maybeSingle();
      if (p?.system_prompt) personaPrompt = `${p.system_prompt}\n\nWICHTIG: Du antwortest im Minecraft-Chat. Plain text only, keine Markdown-Symbole, max 2 Sätze.`;
    }

    if (action === "ping") {
      return new Response(JSON.stringify({ ok: true, server: { name: server.name, ingame_chat: server.ingame_chat_enabled, events: server.events_enabled, trigger: server.chat_trigger } }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (action === "chat") {
      const player = String(body.player || "Player").slice(0, 32);
      const message = String(body.message || "").trim();
      if (!server.ingame_chat_enabled) {
        return new Response(JSON.stringify({ ok: true, ignored: true }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
      }
      const trigger = server.chat_trigger || "!ai";
      if (!message.toLowerCase().startsWith(trigger.toLowerCase())) {
        return new Response(JSON.stringify({ ok: true, ignored: true }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
      }
      const userText = message.slice(trigger.length).trim();
      if (!userText) {
        return new Response(JSON.stringify({ reply: `Hi ${player}, frag mich was nach ${trigger}!` }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
      }
      const reply = await callAI(personaPrompt, `Spieler ${player} fragt im Ingame-Chat: ${userText}`, LOVABLE_API_KEY);
      const trimmed = reply.replace(/[*_`#>]/g, "").slice(0, 220);
      await supabase.from("mc_events").insert({
        server_id: server.id, event_type: "chat", player_name: player, content: userText, ai_response: trimmed,
      });
      return new Response(JSON.stringify({ reply: trimmed }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }

    if (action === "event") {
      const type = String(body.type || "").toLowerCase();
      const player = String(body.player || "Player").slice(0, 32);
      const content = String(body.content || "").slice(0, 200);
      if (!server.events_enabled) {
        return new Response(JSON.stringify({ ok: true, ignored: true }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
      }
      let reply = "";
      if (type === "join" && server.greet_on_join) {
        reply = await callAI(personaPrompt, `Spieler ${player} ist gerade dem Server beigetreten. Begrüße ihn kurz und kreativ (1 Satz, kein "Willkommen" am Anfang).`, LOVABLE_API_KEY);
      } else if (type === "death" && server.comment_on_death) {
        reply = await callAI(personaPrompt, `Spieler ${player} ist gestorben${content ? ` (${content})` : ""}. Mach einen kurzen lustigen Spruch (1 Satz).`, LOVABLE_API_KEY);
      }
      const trimmed = reply.replace(/[*_`#>]/g, "").slice(0, 200);
      await supabase.from("mc_events").insert({
        server_id: server.id, event_type: type, player_name: player, content, ai_response: trimmed || null,
      });
      return new Response(JSON.stringify({ reply: trimmed || null }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }

    // ============== /ai IN-GAME COMMAND (with account linking) ==============
    // Body: { action: "ai", player, uuid, message }
    // Flow: if player not linked -> mint 6-digit code, tell them to enter it on the website profile.
    //       if linked -> chat as that user (their memories + persona) and reply.
    if (action === "ai") {
      const player = String(body.player || "Player").slice(0, 32);
      const mcUuid = String(body.uuid || "").slice(0, 64);
      const userText = String(body.message || "").trim();
      if (!mcUuid) {
        return new Response(JSON.stringify({ reply: "Fehler: kein UUID vom Plugin übergeben." }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
      }

      // Already linked?
      const { data: linked } = await supabase.from("mc_players").select("user_id").eq("mc_uuid", mcUuid).maybeSingle();
      if (!linked) {
        // Reuse an unexpired unclaimed code if we have one, else mint a new 6-digit one.
        const { data: existing } = await supabase.from("mc_link_codes")
          .select("code,expires_at").eq("mc_uuid", mcUuid).is("claimed_by", null)
          .gt("expires_at", new Date().toISOString()).maybeSingle();
        let code = existing?.code;
        if (!code) {
          code = String(Math.floor(100000 + Math.random() * 900000));
          await supabase.from("mc_link_codes").insert({
            code, mc_uuid: mcUuid, mc_name: player, server_id: server.id,
          });
        }
        return new Response(JSON.stringify({
          reply: `Hi ${player}! Dein Verifizierungs-Code: ${code}\nGib ihn ein auf: ${server.website_url || "der Mythos AI Website"} -> Profil -> Minecraft verknuepfen. Danach kannst du hier per /ai chatten.`,
        }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
      }

      if (!userText) {
        return new Response(JSON.stringify({ reply: `Hi ${player}, sag mir was mit /ai <deine Frage>` }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
      }

      // Load memories for this user for personalization
      const { data: mems } = await supabase.from("user_memories").select("content,category").eq("user_id", linked.user_id).limit(20);
      const memBlock = mems?.length ? `\n\nWas du ueber den Spieler weisst:\n${mems.map((m: any) => `- ${m.content}`).join("\n")}` : "";
      const sys = `${personaPrompt}${memBlock}\n\nWICHTIG: Antwort im Minecraft-Chat. Plain text, keine Markdown-Symbole, max 2 Saetze.`;
      const reply = await callAI(sys, `Spieler ${player} fragt: ${userText}`, LOVABLE_API_KEY);
      const trimmed = reply.replace(/[*_`#>]/g, "").slice(0, 220);
      await supabase.from("mc_events").insert({
        server_id: server.id, event_type: "ai_command", player_name: player, content: userText, ai_response: trimmed,
      });
      return new Response(JSON.stringify({ reply: trimmed }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }



    return new Response(JSON.stringify({ error: "Unknown action. Use: ping, chat, event" }), {
      status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error("mc-bridge error", e);
    return new Response(JSON.stringify({ error: e instanceof Error ? e.message : "Error" }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
