// Leitet MCP-Anfragen (Streamable HTTP) aus dem Web-Chat an Connectoren weiter,
// deren Server den Browser per CORS blockieren. Nur für eingeloggte Nutzer, nur https,
// keine internen Adressen.
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

const PASS = ["content-type", "accept", "mcp-protocol-version", "mcp-session-id", "authorization"];

function blockedHost(host: string) {
  const h = host.toLowerCase().replace(/^\[|\]$/g, "");
  return h === "localhost" || h.endsWith(".localhost") || h.endsWith(".internal") || h.endsWith(".local") ||
    /^(127\.|10\.|0\.|169\.254\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(h) ||
    h === "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);

  const token = (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: auth } = await sb.auth.getUser(token);
  if (!auth?.user) return json({ error: "Bitte einloggen." }, 401);

  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  let url: URL;
  try { url = new URL(String(body?.url || "")); } catch { return json({ error: "Ungültige URL" }); }
  if (url.protocol !== "https:" || blockedHost(url.hostname)) return json({ error: "Nur öffentliche https-Adressen sind erlaubt." });

  const headers: Record<string, string> = {};
  for (const [k, v] of Object.entries(body?.headers || {})) {
    if (PASS.includes(k.toLowerCase()) && typeof v === "string") headers[k] = v;
  }
  try {
    // Standard: MCP-Nachricht als JSON. Für die OAuth-Anmeldung auch GET (Server-Infos) und Formular-POST (Token).
    const method = body?.method === "GET" ? "GET" : "POST";
    const payload = method === "GET" ? undefined : typeof body?.rawBody === "string" ? body.rawBody : JSON.stringify(body.payload);
    const r = await fetch(url, { method, headers, body: payload, redirect: "error", signal: AbortSignal.timeout(60_000) });
    const text = (await r.text()).slice(0, 2_000_000);
    return json({ status: r.status, contentType: r.headers.get("content-type"), sessionId: r.headers.get("mcp-session-id"), body: text });
  } catch (e) {
    return json({ error: "Connector nicht erreichbar: " + (e instanceof Error ? e.message : String(e)) });
  }
});
