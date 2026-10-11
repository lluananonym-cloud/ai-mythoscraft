// Anmeldung bei MCP-Connectoren wie bei Claude: Server prüfen, OAuth-Infos erkennen,
// Login im Popup mit PKCE (Rückleitung auf /mcp-callback) und Token erneuern.
// Blockiert ein Server den Browser (CORS), läuft die Anfrage über die Edge-Funktion "mcp-proxy".
import { supabase } from "@/integrations/supabase/client";
import type { McpServer, McpToken } from "@/lib/chatPrefs";

const PROTOCOL = "2025-06-18";
export const CIMD_URL = "https://mythos-core-ai-ai.lovable.app/oauth/mythos-web.json";
export const redirectUri = () => `${window.location.origin}/mcp-callback`;

export class McpAuthError extends Error {
  auth = true;
  constructor(msg = "Anmeldung nötig") { super(msg); }
}

type Res = { status: number; text: string; headers: Record<string, string> };

// Server, die den Browser per CORS blockieren: danach gleich über den Proxy.
const proxyOnly = new Set<string>();

/** Anfrage direkt aus dem Browser, bei CORS-Sperre über den Proxy.
 *  rpc: JSON-RPC-Nachricht statt body – dann wird bei SSE nur bis zur Antwort gelesen (manche Server halten den Stream offen).
 *  headOnly: nur Status und Header, Inhalt wird verworfen. */
export async function rawFetch(url: string, init: { method?: string; headers?: Record<string, string>; body?: string; rpc?: unknown; headOnly?: boolean } = {}): Promise<Res> {
  const method = init.method || "GET";
  const origin = new URL(url).origin;
  if (!proxyOnly.has(origin)) {
    try {
      const r = await fetch(url, { method, headers: init.headers, body: init.rpc !== undefined ? JSON.stringify(init.rpc) : init.body });
      const headers: Record<string, string> = {};
      r.headers.forEach((v, k) => { headers[k] = v; });
      if (init.headOnly) { r.body?.cancel().catch(() => {}); return { status: r.status, text: "", headers }; }
      return { status: r.status, text: await r.text(), headers };
    } catch {
      proxyOnly.add(origin);
    }
  }
  const { data, error } = await supabase.functions.invoke("mcp-proxy", {
    body: init.rpc !== undefined ? { url, method, headers: init.headers || {}, payload: init.rpc } : { url, method, headers: init.headers || {}, rawBody: init.body },
  });
  if (error || data?.error) throw new Error(data?.error || error?.message || "Proxy-Fehler");
  return {
    status: data.status, text: data.body || "",
    headers: { "content-type": data.contentType || "", "mcp-session-id": data.sessionId || "", "www-authenticate": data.wwwAuthenticate || "" },
  };
}

async function getJson(url: string) {
  try {
    const r = await rawFetch(url, { headers: { Accept: "application/json" } });
    return r.status >= 200 && r.status < 300 ? JSON.parse(r.text) : null;
  } catch { return null; }
}

type Meta = { authorization_endpoint: string; token_endpoint: string; registration_endpoint?: string; client_id_metadata_document_supported?: boolean };

async function discover(mcpUrl: string, wwwAuth?: string) {
  const u = new URL(mcpUrl), p = u.pathname.replace(/\/$/, "");
  const hinted = wwwAuth?.match(/resource_metadata="([^"]+)"/)?.[1];
  let prm: any = null;
  for (const c of [hinted, p && `${u.origin}/.well-known/oauth-protected-resource${p}`, `${u.origin}/.well-known/oauth-protected-resource`].filter(Boolean) as string[]) {
    prm = await getJson(c); if (prm) break;
  }
  const iss = new URL(prm?.authorization_servers?.[0] || u.origin), ip = iss.pathname.replace(/\/$/, "");
  let meta: Meta | null = null;
  for (const c of [`${iss.origin}/.well-known/oauth-authorization-server${ip}`, `${iss.origin}/.well-known/openid-configuration${ip}`, ip && `${iss.origin}${ip}/.well-known/openid-configuration`].filter(Boolean) as string[]) {
    meta = await getJson(c); if (meta?.authorization_endpoint && meta?.token_endpoint) break; meta = null;
  }
  const found = !!(meta || prm);
  if (!meta) meta = { authorization_endpoint: `${iss.origin}/authorize`, token_endpoint: `${iss.origin}/token`, registration_endpoint: `${iss.origin}/register` };
  return { meta, found, prm: !!prm, resource: (prm?.resource as string) || mcpUrl, scope: Array.isArray(prm?.scopes_supported) ? prm.scopes_supported.join(" ") : undefined };
}

export type ProbeResult = { auth: "now" | "ondemand" | "none"; client: "cimd" | "dcr" | "custom"; oauth: boolean };

/** Verbindet sich einmal mit dem Server und erkennt, welche Anmeldung er braucht. */
export async function probe(url: string, headers: Record<string, string> = {}): Promise<ProbeResult> {
  let r: Res;
  try {
    r = await rawFetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json, text/event-stream", "MCP-Protocol-Version": PROTOCOL, ...headers },
      rpc: { jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: PROTOCOL, capabilities: {}, clientInfo: { name: "mythos-web", version: "1" } } },
      headOnly: true,
    });
  } catch (e) { throw new Error("Server nicht erreichbar: " + (e instanceof Error ? e.message : String(e))); }
  const needsAuth = r.status === 401 || r.status === 403;
  if (!needsAuth && (r.status < 200 || r.status >= 300)) throw new Error(`Server antwortet mit HTTP ${r.status}`);
  const d = await discover(url, r.headers["www-authenticate"]).catch(() => null);
  // Ohne 401 gilt ein Server nur als „Anmeldung möglich“, wenn er sich selbst als geschützte Ressource beschreibt.
  const oauth = !!(d && (needsAuth ? d.found : d.prm));
  // Wie Claude: automatisch registrieren, wo es geht (CIMD braucht unsere veröffentlichte Client-Datei).
  const client = !oauth ? "dcr" : d!.meta.registration_endpoint ? "dcr" : d!.meta.client_id_metadata_document_supported ? "cimd" : "custom";
  return { auth: needsAuth ? "now" : oauth ? "ondemand" : "none", client, oauth };
}

const b64url = (buf: ArrayBuffer | Uint8Array) =>
  btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const random = (n: number) => b64url(crypto.getRandomValues(new Uint8Array(n)));

// Der eigene Header erzwingt eine CORS-Vorabfrage: Ohne sie schickt der Browser das Formular ab, liest nur die Antwort
// nicht – der Code wäre verbraucht und der Weg über den Proxy liefe in "invalid_grant".
async function tokenRequest(o: Omit<McpToken, "accessToken">, params: Record<string, string>): Promise<Pick<McpToken, "accessToken" | "refreshToken" | "expiresAt">> {
  const form = new URLSearchParams({ ...params, client_id: o.clientId, resource: o.resource });
  if (o.clientSecret) form.set("client_secret", o.clientSecret);
  const r = await rawFetch(o.tokenEndpoint, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json", "MCP-Protocol-Version": PROTOCOL }, body: form.toString() });
  let j: any = {};
  try { j = JSON.parse(r.text); } catch { j = Object.fromEntries(new URLSearchParams(r.text)); }
  if (r.status < 200 || r.status >= 300 || !j.access_token) throw new Error(j.error_description || j.error || `Token-Fehler (HTTP ${r.status})`);
  return { accessToken: j.access_token, refreshToken: j.refresh_token || undefined, expiresAt: j.expires_in ? Date.now() + Number(j.expires_in) * 1000 : undefined };
}

export async function refreshToken(t: McpToken): Promise<McpToken> {
  if (!t.refreshToken) throw new McpAuthError("Anmeldung abgelaufen");
  try {
    const n = await tokenRequest(t, { grant_type: "refresh_token", refresh_token: t.refreshToken });
    return { ...t, ...n, refreshToken: n.refreshToken || t.refreshToken };
  } catch { throw new McpAuthError("Anmeldung abgelaufen"); }
}

/** Öffnet ein leeres Popup – muss direkt im Klick passieren, sonst blockiert der Browser es. */
export const openLoginPopup = () => window.open("about:blank", "mythos-mcp-login", "width=520,height=720");

/** OAuth-Login im Popup. Gibt das Token zurück. */
export async function login(server: McpServer, popup?: Window | null): Promise<McpToken> {
  const win = popup || openLoginPopup();
  if (!win) throw new Error("Popup blockiert – bitte Popups für diese Seite erlauben.");
  try {
    const { meta, resource, scope } = await discover(server.url);
    const redirect = redirectUri();
    let clientId: string, clientSecret: string | undefined;
    if (server.clientMode === "custom") {
      if (!server.clientId) throw new Error("Bitte eine Client-ID angeben.");
      clientId = server.clientId; clientSecret = server.clientSecret || undefined;
    } else if (server.clientMode === "cimd") {
      clientId = CIMD_URL;
    } else {
      if (!meta.registration_endpoint) throw new Error("Dieser Server erlaubt keine automatische Registrierung – wähle „Eigenen OAuth-Client verwenden“.");
      const reg = await rawFetch(meta.registration_endpoint, {
        method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ client_name: "Mythos", client_uri: window.location.origin, redirect_uris: [redirect], grant_types: ["authorization_code", "refresh_token"], response_types: ["code"], token_endpoint_auth_method: "none" }),
      });
      if (reg.status < 200 || reg.status >= 300) throw new Error(`Registrierung fehlgeschlagen (HTTP ${reg.status})`);
      const rj = JSON.parse(reg.text);
      clientId = rj.client_id; clientSecret = rj.client_secret;
    }
    const verifier = random(48), state = random(16);
    const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
    const auth = new URL(meta.authorization_endpoint);
    const q: Record<string, string> = { response_type: "code", client_id: clientId, redirect_uri: redirect, code_challenge: challenge, code_challenge_method: "S256", state, resource };
    if (scope) q.scope = scope;
    for (const [k, v] of Object.entries(q)) auth.searchParams.set(k, v);
    win.location.href = auth.toString();
    const code = await waitForCode(state, win);
    const base = { tokenEndpoint: meta.token_endpoint, clientId, clientSecret, resource };
    return { ...base, ...(await tokenRequest(base, { grant_type: "authorization_code", code, redirect_uri: redirect, code_verifier: verifier })) };
  } catch (e) {
    try { win.close(); } catch { /* egal */ }
    throw e;
  }
}

const RESULT_KEY = "mythos.mcpOauthResult";

/** Wartet auf die Rückleitung (/mcp-callback meldet sich per postMessage oder localStorage). */
function waitForCode(state: string, win: Window): Promise<string> {
  return new Promise((resolve, reject) => {
    const done = (r: { state?: string; code?: string; error?: string } | null) => {
      if (!r || r.state !== state) return;
      cleanup();
      if (r.code) resolve(r.code); else reject(new Error(r.error || "Anmeldung fehlgeschlagen"));
    };
    const onMsg = (e: MessageEvent) => { if (e.origin === window.location.origin && e.data?.type === "mythos-mcp-oauth") done(e.data); };
    const onStorage = (e: StorageEvent) => { if (e.key === RESULT_KEY && e.newValue) { try { done(JSON.parse(e.newValue)); } catch { /* egal */ } } };
    // win.closed ist nach manchen Login-Seiten (COOP) immer true – darum nur eine Zeitgrenze.
    void win;
    const timer = setInterval(() => {
      try { const v = localStorage.getItem(RESULT_KEY); if (v) done(JSON.parse(v)); } catch { /* egal */ }
    }, 700);
    const limit = setTimeout(() => { cleanup(); reject(new Error("Anmeldung abgebrochen")); }, 5 * 60_000);
    const cleanup = () => {
      clearInterval(timer); clearTimeout(limit); window.removeEventListener("message", onMsg); window.removeEventListener("storage", onStorage);
      try { localStorage.removeItem(RESULT_KEY); } catch { /* egal */ }
    };
    window.addEventListener("message", onMsg);
    window.addEventListener("storage", onStorage);
  });
}

/** Wird von der Seite /mcp-callback im Popup aufgerufen. */
export function reportCallback() {
  const p = new URLSearchParams(window.location.search);
  const r = { type: "mythos-mcp-oauth", state: p.get("state") || "", code: p.get("code") || undefined, error: p.get("error_description") || p.get("error") || undefined };
  try { localStorage.setItem(RESULT_KEY, JSON.stringify(r)); } catch { /* egal */ }
  try { window.opener?.postMessage(r, window.location.origin); } catch { /* egal */ }
  return !!r.code;
}
