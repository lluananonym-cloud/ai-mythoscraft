// MCP-Client für den Browser (Streamable HTTP, wie bei Claude "Connectoren").
// Versucht erst die direkte Verbindung; blockiert der Server den Browser (CORS),
// geht die Anfrage über die Edge-Funktion "mcp-proxy".
import { supabase } from "@/integrations/supabase/client";
import { getMcpServers, setMcpServers, type McpServer } from "@/lib/chatPrefs";
import { McpAuthError, refreshToken } from "@/lib/mcpOauth";

const PROTOCOL = "2025-06-18";
const CLIENT = { name: "mythos-web", version: "1" };

export type McpTool = { name: string; description?: string; inputSchema?: unknown };

type Session = { id?: string; viaProxy: boolean; ready: boolean };
const sessions = new Map<string, Session>();
let rpcId = 1;

function parseSse(text: string): any[] {
  return text.replace(/\r\n/g, "\n").split(/\n\n/).map(block => block.split("\n")
    .filter(l => l.startsWith("data:")).map(l => l.slice(5).trim()).join("\n"))
    .filter(Boolean).map(d => { try { return JSON.parse(d); } catch { return null; } }).filter(Boolean);
}

/** Liest die Antwort; bei SSE nur bis zur passenden Antwort – manche Server halten den Stream offen. */
async function readBody(r: Response, msg: any): Promise<string> {
  const type = r.headers.get("content-type") || "";
  if (msg.id === undefined || !msg.method) { r.body?.cancel().catch(() => {}); return ""; }
  if (!type.includes("text/event-stream") || !r.body) return r.text();
  const reader = r.body.getReader(), dec = new TextDecoder();
  let text = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    text += dec.decode(value, { stream: true });
    if (parseSse(text).some((m: any) => m?.id === msg.id && !m.method)) { reader.cancel().catch(() => {}); break; }
  }
  return text;
}

/** Aktuelle Daten des Connectors (Token kann inzwischen erneuert worden sein). */
const fresh = (server: McpServer) => getMcpServers().find(x => x.id === server.id) || server;
function saveToken(server: McpServer, oauth: McpServer["oauth"]) {
  setMcpServers(getMcpServers().map(x => x.id === server.id ? { ...x, oauth } : x));
}

async function authHeader(server: McpServer, force = false): Promise<string | undefined> {
  if (server.auth) return server.auth;
  let t = fresh(server).oauth;
  if (!t) return undefined;
  if (force || (t.expiresAt && t.expiresAt - 60_000 < Date.now() && t.refreshToken)) {
    t = await refreshToken(t);
    saveToken(server, t);
  }
  return "Bearer " + t.accessToken;
}

async function post(server: McpServer, s: Session, msg: any, retried = false): Promise<any | undefined> {
  const headers: Record<string, string> = {
    ...(server.headers || {}),
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "MCP-Protocol-Version": PROTOCOL,
  };
  if (s.id) headers["Mcp-Session-Id"] = s.id;
  const authz = await authHeader(server);
  if (authz) headers.Authorization = authz;

  let status: number, body: string, type: string, sid: string | null;
  if (!s.viaProxy) {
    try {
      const r = await fetch(server.url, { method: "POST", headers, body: JSON.stringify(msg) });
      status = r.status; type = r.headers.get("content-type") || ""; sid = r.headers.get("mcp-session-id");
      body = r.ok ? await readBody(r, msg) : await r.text();
    } catch {
      // Netzwerk/CORS-Fehler -> ab jetzt über den Server
      s.viaProxy = true;
      return post(server, s, msg);
    }
  } else {
    const { data, error } = await supabase.functions.invoke("mcp-proxy", { body: { url: server.url, headers, payload: msg } });
    if (error || data?.error) throw new Error(data?.error || error?.message || "Proxy-Fehler");
    status = data.status; type = data.contentType || ""; sid = data.sessionId; body = data.body || "";
  }
  if (status === 401) {
    const t = fresh(server).oauth;
    if (!retried && !server.auth && t?.refreshToken) { await authHeader(server, true); return post(server, s, msg, true); }
    throw new McpAuthError();
  }
  if (sid) s.id = sid;
  if (status === 202 || status === 204) return undefined;
  if (status < 200 || status >= 300) throw new Error(`HTTP ${status}: ${body.slice(0, 200)}`);
  if (msg.id === undefined) return undefined;
  const replies = type.includes("text/event-stream") ? parseSse(body) : [JSON.parse(body)];
  const reply = replies.flat().find((r: any) => r && r.id === msg.id);
  if (!reply) throw new Error("Keine Antwort vom MCP-Server");
  if (reply.error) throw new Error(reply.error.message || "MCP-Fehler");
  return reply.result;
}

async function session(server: McpServer, viaProxy = false): Promise<Session> {
  let s = sessions.get(server.id);
  if (s?.ready && (!viaProxy || s.viaProxy)) return s;
  s = { viaProxy, ready: false };
  sessions.set(server.id, s);
  await post(server, s, { jsonrpc: "2.0", id: rpcId++, method: "initialize", params: { protocolVersion: PROTOCOL, capabilities: {}, clientInfo: CLIENT } });
  await post(server, s, { jsonrpc: "2.0", method: "notifications/initialized" });
  s.ready = true;
  return s;
}

async function rpc(server: McpServer, method: string, params: unknown) {
  const run = async (viaProxy: boolean) => post(server, await session(server, viaProxy), { jsonrpc: "2.0", id: rpcId++, method, params });
  try {
    return await run(false);
  } catch (e) {
    const s = sessions.get(server.id);
    sessions.delete(server.id); // beim nächsten Mal neu verbinden
    // Direkt verbunden, aber der Server lehnt danach ab (z. B. Sitzungs-ID für den Browser nicht lesbar):
    // einmal über den Mythos-Server versuchen.
    if (!(e as { auth?: boolean })?.auth && s && !s.viaProxy && /^HTTP 4\d\d/.test((e as Error)?.message || "")) {
      try { return await run(true); } catch (e2) { sessions.delete(server.id); throw e2; }
    }
    throw e;
  }
}

export async function listTools(server: McpServer): Promise<McpTool[]> {
  const r = await rpc(server, "tools/list", {});
  return (r?.tools || []) as McpTool[];
}

export async function callTool(server: McpServer, name: string, args: unknown): Promise<string> {
  const r = await rpc(server, "tools/call", { name, arguments: args ?? {} });
  const parts = (r?.content || []).map((c: any) =>
    c.type === "text" ? c.text :
    c.type === "image" ? `[Bild ${c.mimeType || ""}]` :
    c.type === "resource" ? (c.resource?.text ?? `[Ressource ${c.resource?.uri || ""}]`) :
    c.type === "resource_link" ? `[Link ${c.uri}]` : JSON.stringify(c));
  if (!parts.length && r?.structuredContent) parts.push(JSON.stringify(r.structuredContent, null, 2));
  return (r?.isError ? "FEHLER: " : "") + parts.join("\n");
}

/** Alle Werkzeuge aller eingeschalteten Connectoren, Name = "server__tool". */
// Werkzeuglisten kurz merken: sonst fragt jede Chat-Nachricht erst alle Server ab, bevor die KI antwortet.
const toolCache = new Map<string, { at: number; tools: McpTool[] }>();
const TOOL_TTL = 5 * 60_000;
const withTimeout = <T,>(p: Promise<T>, ms: number) =>
  Promise.race([p, new Promise<T>((_, rej) => setTimeout(() => rej(new Error("Zeitüberschreitung")), ms))]);

export async function collectTools(servers: McpServer[]) {
  const out: { key: string; server: McpServer; tool: McpTool }[] = [];
  await Promise.all(servers.filter(s => s.enabled).map(async server => {
    try {
      const hit = toolCache.get(server.url);
      const tools = hit && Date.now() - hit.at < TOOL_TTL ? hit.tools : await withTimeout(listTools(server), 12000);
      toolCache.set(server.url, { at: hit && hit.tools === tools ? hit.at : Date.now(), tools });
      const prefix = server.name.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "") || "mcp";
      for (const tool of tools) out.push({ key: `${prefix}__${tool.name}`, server, tool });
    } catch { /* Server nicht erreichbar – einfach weglassen */ }
  }));
  return out;
}
