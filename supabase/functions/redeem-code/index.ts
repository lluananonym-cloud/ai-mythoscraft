import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const auth = req.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const authClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
    const token = auth.replace("Bearer ", "");
    const { data: claimsData, error: ce } = await authClient.auth.getClaims(token);
    if (ce || !claimsData?.claims?.sub) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const userId = claimsData.claims.sub as string;

    const body = await req.json();
    const rawCode = String(body?.code || "").trim().toUpperCase();
    if (!rawCode || rawCode.length > 64) {
      return new Response(JSON.stringify({ error: "Code erforderlich" }), {
        status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    const { data: code, error: codeErr } = await supabase
      .from("boost_codes")
      .select("*")
      .eq("code", rawCode)
      .maybeSingle();

    if (codeErr || !code) {
      return new Response(JSON.stringify({ error: "Code ungültig" }), {
        status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (code.expires_at && new Date(code.expires_at) < new Date()) {
      return new Response(JSON.stringify({ error: "Code ist abgelaufen" }), {
        status: 410, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (code.max_uses > 0 && code.used_count >= code.max_uses) {
      return new Response(JSON.stringify({ error: "Code wurde bereits maximal eingelöst" }), {
        status: 410, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Check if this user already redeemed
    const { data: existing } = await supabase
      .from("boost_redemptions")
      .select("id")
      .eq("code_id", code.id)
      .eq("user_id", userId)
      .maybeSingle();
    if (existing) {
      return new Response(JSON.stringify({ error: "Du hast diesen Code bereits eingelöst" }), {
        status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Apply effect
    let appliedDailyLimit: number | null = null;
    let bonusRemaining: number | null = null;
    let expiresAt: string | null = null;

    if (code.mode === "permanent") {
      appliedDailyLimit = code.daily_limit;
      // bump all of user's keys to at least this limit
      await supabase
        .from("api_keys")
        .update({ daily_limit: code.daily_limit })
        .eq("user_id", userId)
        .lt("daily_limit", code.daily_limit);
    } else if (code.mode === "temporary") {
      appliedDailyLimit = code.daily_limit;
      expiresAt = new Date(Date.now() + (code.duration_days || 7) * 86400000).toISOString();
      await supabase
        .from("api_keys")
        .update({ daily_limit: code.daily_limit })
        .eq("user_id", userId)
        .lt("daily_limit", code.daily_limit);
    } else if (code.mode === "oneshot") {
      bonusRemaining = code.bonus_requests || 0;
    }

    const { error: redErr } = await supabase.from("boost_redemptions").insert({
      code_id: code.id,
      user_id: userId,
      mode: code.mode,
      daily_limit: appliedDailyLimit,
      bonus_remaining: bonusRemaining,
      expires_at: expiresAt,
    });
    if (redErr) throw redErr;

    await supabase
      .from("boost_codes")
      .update({ used_count: code.used_count + 1 })
      .eq("id", code.id);

    return new Response(JSON.stringify({
      success: true,
      mode: code.mode,
      daily_limit: appliedDailyLimit,
      bonus_remaining: bonusRemaining,
      expires_at: expiresAt,
    }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  } catch (e) {
    console.error(e);
    return new Response(JSON.stringify({ error: e instanceof Error ? e.message : "Error" }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
