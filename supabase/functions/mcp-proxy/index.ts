// Leitet MCP-Anfragen (Streamable HTTP) aus dem Web-Chat an Connectoren weiter,
// deren Server den Browser per CORS blockieren. Nur für eingeloggte Nutzer, nur https,
// keine internen Adressen. Auch für die OAuth-Anmeldung: GET (Server-Infos) und
// Formular-POST (Token, Registrierung) über "method" und "rawBody".
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

// Diese Header nie weiterreichen; alle anderen (auch eigene Request-Header der Connectoren) schon.
const DROP = ["host", "cookie", "connection", "content-length", "transfer-encoding", "upgrade", "te", "keep-alive", "proxy-authorization", "x-forwarded-for"];

/** Liest die Antwort. Bei MCP über SSE nur bis zur passenden JSON-RPC-Antwort: manche Server halten
 *  den Stream offen, sonst hinge die Anfrage bis zur Zeitgrenze. Benachrichtigungen brauchen keinen Inhalt. */
async function readBody(r: Response, rpc: any): Promise<string> {
  const type = r.headers.get("content-type") || "";
  if (rpc && (rpc.id === undefined || !rpc.method)) { await r.body?.cancel().catch(() => {}); return ""; }
  if (!rpc || !type.includes("text/event-stream") || !r.body) return r.text();
  const reader = r.body.getReader(), dec = new TextDecoder();
  let text = "";
  while (text.length < 2_000_000) {
    const { done, value } = await reader.read();
    if (done) break;
    text += dec.decode(value, { stream: true });
    const hit = text.replace(/\r\n/g, "\n").split("\n\n").some((b) => {
      const d = b.split("\n").filter((l) => l.startsWith("data:")).map((l) => l.slice(5).trim()).join("\n");
      try { const m = JSON.parse(d); return m?.id === rpc.id && !m.method; } catch { return false; }
    });
    if (hit) { await reader.cancel().catch(() => {}); break; }
  }
  return text;
}

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
    if (!DROP.includes(k.toLowerCase()) && typeof v === "string") headers[k] = v;
  }
  const method = body?.method === "GET" ? "GET" : "POST";
  const payload = method === "GET" ? undefined : typeof body?.rawBody === "string" ? body.rawBody : JSON.stringify(body.payload);
  try {
    const r = await fetch(url, { method, headers, body: payload, redirect: "error", signal: AbortSignal.timeout(60_000) });
    const text = (await readBody(r, method === "POST" && typeof body?.rawBody !== "string" ? body?.payload : undefined)).slice(0, 2_000_000);
    return json({ status: r.status, contentType: r.headers.get("content-type"), sessionId: r.headers.get("mcp-session-id"), wwwAuthenticate: r.headers.get("www-authenticate"), body: text });
  } catch (e) {
    return json({ error: "Connector nicht erreichbar: " + (e instanceof Error ? e.message : String(e)) });
  }
});
