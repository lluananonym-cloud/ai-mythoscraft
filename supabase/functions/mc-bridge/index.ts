// MythosAI Minecraft Plugin Bridge
// Auth: Authorization: Bearer <api_key>
//   - sk-mc-XXXX  -> looks up mc_servers.key_hash
//   - 191306      -> MASTER shared key (auto-provisions/uses a shared virtual server row)
//
// Supported actions (from MythosAI plugin v1.0.0):
//   ping                    -> { ok, server }
//   __get_code__            -> mint/return 6-digit link code    { code, verify_code }
//   __link_status__         -> { linked: bool, user?, mc_name? }
//   __unlink__              -> { ok, reply }
//   ai   { player, uuid, message }              -> { reply }
//   chat { player, message }                    -> { reply | ignored }
//   event{ type, player, content? }             -> { reply?: string|null }
//   pay  { from, to, amount }                   -> { ok } (log only, economy handled by Vault plugin-side)

import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
};
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

const MASTER_KEY = "191306";

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join("");
}

async function callAI(systemPrompt: string, userPrompt: string, apiKey: string, retries = 2): Promise<string> {
  for (let i = 0; i <= retries; i++) {
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
    if (r.ok) {
      const j = await r.json();
      return (j.choices?.[0]?.message?.content ?? "").trim() || "...";
    }
    if (r.status === 429 && i < retries) { await new Promise(res => setTimeout(res, (i + 1) * 1000)); continue; }
    if (r.status === 402) return "Keine AI-Credits übrig — sag es dem Admin.";
    console.error("AI gateway error", r.status, await r.text());
    return "Hmm, ich kann gerade nicht antworten.";
  }
  return "Hmm, ich kann gerade nicht antworten.";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const auth = req.headers.get("Authorization") || req.headers.get("authorization") || "";
    const m = auth.match(/^Bearer\s+(.+)$/i);
    if (!m) return json({ error: "Missing Authorization: Bearer <key>" }, 401);
    const key = m[1].trim();

    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY");
    if (!LOVABLE_API_KEY) throw new Error("LOVABLE_API_KEY missing");

    // Resolve server row: master key -> shared "MythosCraft Public" row (auto-provision), else lookup by hash
    let server: any = null;
    if (key === MASTER_KEY) {
      const master_hash = await sha256(MASTER_KEY);
      const found = await supabase.from("mc_servers").select("*").eq("key_hash", master_hash).maybeSingle();
      if (found.data) server = found.data;
      else {
        const ins = await supabase.from("mc_servers").insert({
          name: "MythosCraft (Shared)",
          key_hash: master_hash,
          ingame_chat_enabled: true,
          events_enabled: true,
          greet_on_join: true,
          comment_on_death: true,
          chat_trigger: "!ai",
        }).select("*").single();
        server = ins.data;
      }
    } else {
      const key_hash = await sha256(key);
      const found = await supabase.from("mc_servers").select("*").eq("key_hash", key_hash).maybeSingle();
      server = found.data;
    }
    if (!server || server.revoked) return json({ error: "Invalid or revoked key" }, 403);

    await supabase.from("mc_servers").update({
      last_seen_at: new Date().toISOString(),
      total_events: Number(server.total_events || 0) + 1,
    }).eq("id", server.id);

    const body = await req.json().catch(() => ({}));
    const action = String(body.action || "ping");
    const player = String(body.player || "Player").slice(0, 32);
    const mcUuid = String(body.uuid || "").slice(0, 64);

    // ---- Meta actions used by the plugin ----
    if (action === "ping") {
      return json({ ok: true, server: { name: server.name, ingame_chat: server.ingame_chat_enabled, events: server.events_enabled, trigger: server.chat_trigger } });
    }

    if (action === "__link_status__") {
      if (!mcUuid) return json({ linked: false });
      const { data } = await supabase.from("mc_players").select("mc_name,user_id,linked_at").eq("mc_uuid", mcUuid).maybeSingle();
      return json({ linked: !!data, mc_name: data?.mc_name ?? null, linked_at: data?.linked_at ?? null });
    }

    if (action === "__get_code__") {
      if (!mcUuid) return json({ error: "uuid required" }, 400);
      // If already linked, just say so.
      const { data: already } = await supabase.from("mc_players").select("mc_name").eq("mc_uuid", mcUuid).maybeSingle();
      if (already) return json({ linked: true, reply: `Dein Account (${already.mc_name}) ist bereits verknüpft.` });
      // Reuse unexpired code, else mint fresh
      const { data: existing } = await supabase.from("mc_link_codes")
        .select("code,expires_at").eq("mc_uuid", mcUuid).is("claimed_by", null)
        .gt("expires_at", new Date().toISOString()).maybeSingle();
      let code = existing?.code;
      if (!code) {
        code = String(Math.floor(100000 + Math.random() * 900000));
        await supabase.from("mc_link_codes").insert({ code, mc_uuid: mcUuid, mc_name: player, server_id: server.id });
      }
      return json({ code, verify_code: code, expires_in: 600, website: "https://ai-mythoscraft.lovable.app/dashboard" });
    }

    if (action === "__unlink__") {
      if (!mcUuid) return json({ error: "uuid required" }, 400);
      const { error } = await supabase.from("mc_players").delete().eq("mc_uuid", mcUuid);
      if (error) return json({ ok: false, reply: "Konnte nicht entkoppeln." }, 500);
      return json({ ok: true, reply: "Account entkoppelt." });
    }

    // ---- Persona ----
    let personaPrompt = "Du bist die Server-AI für einen Minecraft-Server. Antworte sehr kurz (max 2 Sätze), freundlich, manchmal witzig. Plain text, keine Markdown-Symbole. Deutsch.";
    if (server.ai_persona_id) {
      const { data: p } = await supabase.from("ai_personas").select("system_prompt,name").eq("id", server.ai_persona_id).maybeSingle();
      if (p?.system_prompt) personaPrompt = `${p.system_prompt}\n\nWICHTIG: Minecraft-Chat, plain text, max 2 Sätze.`;
    }

    if (action === "ai") {
      const userText = String(body.message || "").trim();
      if (!mcUuid) return json({ reply: "Fehler: kein UUID vom Plugin übergeben." });

      const { data: linked } = await supabase.from("mc_players").select("user_id").eq("mc_uuid", mcUuid).maybeSingle();
      if (!linked) {
        const { data: existing } = await supabase.from("mc_link_codes")
          .select("code").eq("mc_uuid", mcUuid).is("claimed_by", null)
          .gt("expires_at", new Date().toISOString()).maybeSingle();
        let code = existing?.code;
        if (!code) {
          code = String(Math.floor(100000 + Math.random() * 900000));
          await supabase.from("mc_link_codes").insert({ code, mc_uuid: mcUuid, mc_name: player, server_id: server.id });
        }
        return json({
          linked: false, code, verify_code: code,
          reply: `Hi ${player}! Dein Verifizierungs-Code: ${code}\nGib ihn ein auf ai-mythoscraft.lovable.app -> Dashboard -> Minecraft verknüpfen.`,
        });
      }

      if (!userText) return json({ reply: `Hi ${player}, sag mir was mit /ai <deine Frage>` });

      const { data: mems } = await supabase.from("user_memories").select("content").eq("user_id", linked.user_id).limit(20);
      const { data: prof } = await supabase.from("profiles").select("display_name,about,interests,playstyle,favorite_block").eq("user_id", linked.user_id).maybeSingle();
      const profBlock = prof ? `\nProfil: name=${prof.display_name ?? player}${prof.about ? `, ueber=${prof.about}` : ""}${prof.playstyle ? `, playstyle=${prof.playstyle}` : ""}${prof.favorite_block ? `, lieblingsblock=${prof.favorite_block}` : ""}${prof.interests?.length ? `, interessen=${prof.interests.join(", ")}` : ""}` : "";
      const memBlock = mems?.length ? `\nErinnerungen:\n${mems.map((m: any) => `- ${m.content}`).join("\n")}` : "";
      const sys = `${personaPrompt}${profBlock}${memBlock}`;
      const reply = await callAI(sys, `Spieler ${player} fragt: ${userText}`, LOVABLE_API_KEY);
      const trimmed = reply.replace(/[*_`#>]/g, "").slice(0, 220);
      await supabase.from("mc_events").insert({
        server_id: server.id, event_type: "ai_command", player_name: player, content: userText, ai_response: trimmed,
      });
      return json({ reply: trimmed });
    }

    if (action === "chat") {
      const message = String(body.message || "").trim();
      if (!server.ingame_chat_enabled) return json({ ok: true, ignored: true });
      const trigger = server.chat_trigger || "!ai";
      if (!message.toLowerCase().startsWith(trigger.toLowerCase())) return json({ ok: true, ignored: true });
      const userText = message.slice(trigger.length).trim();
      if (!userText) return json({ reply: `Hi ${player}, frag mich was nach ${trigger}!` });
      const reply = await callAI(personaPrompt, `Spieler ${player} fragt: ${userText}`, LOVABLE_API_KEY);
      const trimmed = reply.replace(/[*_`#>]/g, "").slice(0, 220);
      await supabase.from("mc_events").insert({
        server_id: server.id, event_type: "chat", player_name: player, content: userText, ai_response: trimmed,
      });
      return json({ reply: trimmed });
    }

    if (action === "event") {
      const type = String(body.type || "").toLowerCase();
      const content = String(body.content || "").slice(0, 200);
      if (!server.events_enabled) return json({ ok: true, ignored: true });
      let reply = "";
      if (type === "join" && server.greet_on_join)
        reply = await callAI(personaPrompt, `Spieler ${player} ist beigetreten. Begrüße ihn kurz kreativ (1 Satz, kein "Willkommen" am Anfang).`, LOVABLE_API_KEY);
      else if (type === "death" && server.comment_on_death)
        reply = await callAI(personaPrompt, `Spieler ${player} ist gestorben${content ? ` (${content})` : ""}. Kurzer lustiger Spruch (1 Satz).`, LOVABLE_API_KEY);
      const trimmed = reply.replace(/[*_`#>]/g, "").slice(0, 200);
      await supabase.from("mc_events").insert({
        server_id: server.id, event_type: type, player_name: player, content, ai_response: trimmed || null,
      });
      return json({ reply: trimmed || null });
    }

    if (action === "pay") {
      await supabase.from("mc_events").insert({
        server_id: server.id, event_type: "pay", player_name: player,
        content: `${player} -> ${body.to} : ${body.amount}`,
      });
      return json({ ok: true });
    }

    return json({ error: `Unknown action: ${action}` }, 400);
  } catch (e) {
    console.error("mc-bridge error", e);
    return json({ error: e instanceof Error ? e.message : "Error" }, 500);
  }
});
