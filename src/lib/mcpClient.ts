// MCP-Client für den Browser (Streamable HTTP, wie bei Claude "Connectoren").
// Versucht erst die direkte Verbindung; blockiert der Server den Browser (CORS),
// geht die Anfrage über die Edge-Funktion "mcp-proxy".
import { supabase } from "@/integrations/supabase/client";
import type { McpServer } from "@/lib/chatPrefs";

const PROTOCOL = "2025-06-18";
const CLIENT = { name: "mythos-web", version: "1" };

export type McpTool = { name: string; description?: string; inputSchema?: unknown };

type Session = { id?: string; viaProxy: boolean; ready: boolean };
const sessions = new Map<string, Session>();
let rpcId = 1;

function parseSse(text: string): any[] {
  return text.split(/\n\n/).map(block => block.split("\n")
    .filter(l => l.startsWith("data:")).map(l => l.slice(5).trim()).join("\n"))
    .filter(Boolean).map(d => { try { return JSON.parse(d); } catch { return null; } }).filter(Boolean);
}

async function post(server: McpServer, s: Session, msg: any): Promise<any | undefined> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "MCP-Protocol-Version": PROTOCOL,
  };
  if (s.id) headers["Mcp-Session-Id"] = s.id;
  if (server.auth) headers.Authorization = server.auth;

  let status: number, body: string, type: string, sid: string | null;
  if (!s.viaProxy) {
    try {
      const r = await fetch(server.url, { method: "POST", headers, body: JSON.stringify(msg) });
      status = r.status; type = r.headers.get("content-type") || ""; sid = r.headers.get("mcp-session-id");
      body = await r.text();
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

async function session(server: McpServer): Promise<Session> {
  let s = sessions.get(server.id);
  if (s?.ready) return s;
  s = { viaProxy: false, ready: false };
  sessions.set(server.id, s);
  await post(server, s, { jsonrpc: "2.0", id: rpcId++, method: "initialize", params: { protocolVersion: PROTOCOL, capabilities: {}, clientInfo: CLIENT } });
  await post(server, s, { jsonrpc: "2.0", method: "notifications/initialized" });
  s.ready = true;
  return s;
}

async function rpc(server: McpServer, method: string, params: unknown) {
  try {
    const s = await session(server);
    return await post(server, s, { jsonrpc: "2.0", id: rpcId++, method, params });
  } catch (e) {
    sessions.delete(server.id); // beim nächsten Mal neu verbinden
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
export async function collectTools(servers: McpServer[]) {
  const out: { key: string; server: McpServer; tool: McpTool }[] = [];
  await Promise.all(servers.filter(s => s.enabled).map(async server => {
    try {
      const tools = await listTools(server);
      const prefix = server.name.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "") || "mcp";
      for (const tool of tools) out.push({ key: `${prefix}__${tool.name}`, server, tool });
    } catch { /* Server nicht erreichbar – einfach weglassen */ }
  }));
  return out;
}
