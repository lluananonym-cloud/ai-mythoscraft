// Geräte-Anmeldung für die Mythos Code Desktop-App + Usage-Abfrage.
// actions: start (App) -> approve (Website, eingeloggt) -> poll (App) ; usage (App, mit API-Key)
import { createClient } from "npm:@supabase/supabase-js@2.103.3";
import { corsHeaders } from "npm:@supabase/supabase-js@2/cors";

const json = (d: unknown, status = 200) =>
  new Response(JSON.stringify(d), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

const rnd = (len: number, chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789") => {
  const a = new Uint8Array(len); crypto.getRandomValues(a);
  return Array.from(a, (b) => chars[b % chars.length]).join("");
};
async function sha256(s: string) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  let body: any = {};
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const action = String(body.action || "");

  try {
    if (action === "start") {
      const code = rnd(8);
      const poll_secret = rnd(40, "abcdefghijklmnopqrstuvwxyz0123456789");
      await db.from("cli_device_auth").delete().lt("created_at", new Date(Date.now() - 15 * 60e3).toISOString());
      const { error } = await db.from("cli_device_auth").insert({ code, poll_secret });
      if (error) throw error;
      return json({ code, poll_secret });
    }

    if (action === "approve") {
      const auth = req.headers.get("Authorization") || "";
      const token = auth.replace(/^Bearer\s+/i, "");
      const authClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
      const { data: c } = await authClient.auth.getClaims(token);
      const userId = c?.claims?.sub;
      if (!userId) return json({ error: "Bitte zuerst anmelden." }, 401);
      const code = String(body.code || "").toUpperCase().slice(0, 16);
      const { data: row } = await db.from("cli_device_auth").select("*").eq("code", code).maybeSingle();
      if (!row) return json({ error: "Code ungültig oder abgelaufen." }, 404);
      if (Date.now() - new Date(row.created_at).getTime() > 15 * 60e3) return json({ error: "Code abgelaufen." }, 410);
      if (row.user_id) return json({ error: "Bereits bestätigt." }, 409);
      const key = `sk-ant-mythos-${rnd(48, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")}`;
      const { error: e1 } = await db.from("api_keys").insert({
        user_id: userId, name: "Mythos Code App", key_prefix: key.slice(0, 22), key_hash: await sha256(key),
      });
      if (e1) throw e1;
      await db.from("cli_device_auth").update({ user_id: userId, api_key: key }).eq("code", code);
      return json({ ok: true });
    }

    if (action === "poll") {
      const { data: row } = await db.from("cli_device_auth").select("*")
        .eq("code", String(body.code || "")).eq("poll_secret", String(body.poll_secret || "")).maybeSingle();
      if (!row) return json({ status: "expired" });
      if (!row.api_key) return json({ status: "pending" });
      await db.from("cli_device_auth").delete().eq("code", row.code);
      const { data: prof } = await db.from("profiles").select("display_name,email").eq("user_id", row.user_id).maybeSingle();
      return json({ status: "ok", api_key: row.api_key, name: prof?.display_name || prof?.email || "Mythos-Nutzer" });
    }

    if (action === "usage") {
      const key = String(body.api_key || "");
      const { data: k } = await db.from("api_keys").select("id,user_id,daily_limit,revoked").eq("key_hash", await sha256(key)).maybeSingle();
      if (!k || k.revoked) return json({ error: "Key ungültig" }, 401);
      const [{ data: sub }, { data: roles }, { count }] = await Promise.all([
        db.from("subscriptions").select("tier,expires_at").eq("user_id", k.user_id).maybeSingle(),
        db.from("user_roles").select("role").eq("user_id", k.user_id),
        db.from("api_usage").select("*", { count: "exact", head: true }).eq("api_key_id", k.id)
          .gte("created_at", new Date(Date.now() - 864e5).toISOString()),
      ]);
      const pro = !!roles?.some((r: any) => r.role === "admin") ||
        (sub?.tier === "pro" && (!sub.expires_at || new Date(sub.expires_at) > new Date()));
      return json({ used: count || 0, limit: pro ? null : k.daily_limit, tier: pro ? "pro" : (sub?.tier || "free") });
    }

    return json({ error: "Unbekannte Aktion" }, 400);
  } catch (e) {
    console.error(e);
    return json({ error: e instanceof Error ? e.message : "Fehler" }, 500);
  }
});
