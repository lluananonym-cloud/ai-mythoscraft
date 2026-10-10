// Anmeldung bei MCP-Connectoren per OAuth (wie bei Claude: "Verbinden" → Login-Fenster → fertig).
// Ablauf nach MCP-Spezifikation: Server-Infos suchen (.well-known), Mythos als App registrieren
// (Dynamic Client Registration oder Client-ID-Dokument), Login mit PKCE, Token speichern und erneuern.
import { supabase } from "@/integrations/supabase/client";
import { getMcpServers, setMcpServers, type McpOAuth, type McpServer } from "@/lib/chatPrefs";

const PROTOCOL = "2025-06-18";
const RESULT_KEY = "mythos.mcpOauthResult";
const CIMD_ORIGIN = "https://mythoscraft.online";

export class McpAuthError extends Error {
  constructor(msg = "Anmeldung nötig") { super(msg); this.name = "McpAuthError"; }
}

type Res = { status: number; contentType: string; body: string };

/** HTTP-Anfrage: erst direkt, blockiert der Browser (CORS), über die Edge-Funktion "mcp-proxy". */
export async function httpFetch(url: string, init: { method?: "GET" | "POST"; headers?: Record<string, string>; body?: string } = {}): Promise<Res> {
  const method = init.method || "GET";
  try {
    const r = await fetch(url, { method, headers: init.headers, body: init.body });
    return { status: r.status, contentType: r.headers.get("content-type") || "", body: await r.text() };
  } catch {
    const { data, error } = await supabase.functions.invoke("mcp-proxy", {
      body: { url, method, headers: init.headers || {}, rawBody: init.body },
    });
    if (error || data?.error) throw new Error(data?.error || error?.message || "Proxy-Fehler");
    return { status: data.status, contentType: data.contentType || "", body: data.body || "" };
  }
}

async function getJson(url: string): Promise<any | null> {
  try {
    const r = await httpFetch(url, { headers: { Accept: "application/json" } });
    if (r.status < 200 || r.status >= 300) return null;
    return JSON.parse(r.body);
  } catch { return null; }
}

async function discover(mcpUrl: string) {
  const u = new URL(mcpUrl);
  const path = u.pathname.replace(/\/$/, "");
  let prm: any = null;
  for (const c of [path && `${u.origin}/.well-known/oauth-protected-resource${path}`, `${u.origin}/.well-known/oauth-protected-resource`].filter(Boolean) as string[]) {
    prm = await getJson(c);
    if (prm) break;
  }
  const issuer = new URL(prm?.authorization_servers?.[0] || u.origin);
  const ip = issuer.pathname.replace(/\/$/, "");
  const candidates = [
    `${issuer.origin}/.well-known/oauth-authorization-server${ip}`,
    `${issuer.origin}/.well-known/openid-configuration${ip}`,
    ip && `${issuer.origin}${ip}/.well-known/openid-configuration`,
  ].filter(Boolean) as string[];
  let meta: any = null;
  for (const c of candidates) {
    meta = await getJson(c);
    if (meta?.authorization_endpoint && meta?.token_endpoint) break;
    meta = null;
  }
  if (!meta) {
    // Fallback aus der Spezifikation (ältere Server ohne Metadaten)
    meta = { authorization_endpoint: `${issuer.origin}/authorize`, token_endpoint: `${issuer.origin}/token`, registration_endpoint: `${issuer.origin}/register` };
  }
  return {
    meta,
    resource: (prm?.resource as string) || mcpUrl,
    scope: Array.isArray(prm?.scopes_supported) ? prm.scopes_supported.join(" ") : undefined,
  };
}

const redirectUri = () => `${location.origin}/mcp-oauth`;

async function register(meta: any): Promise<{ clientId: string; clientSecret?: string }> {
  if (meta.client_id_metadata_document_supported && location.origin === CIMD_ORIGIN) {
    return { clientId: `${CIMD_ORIGIN}/mcp-client.json` };
  }
  if (!meta.registration_endpoint) {
    if (meta.client_id_metadata_document_supported) return { clientId: `${CIMD_ORIGIN}/mcp-client.json` };
    throw new Error("Dieser Server erlaubt keine automatische Anmeldung für fremde Apps.");
  }
  const r = await httpFetch(meta.registration_endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({
      client_name: "Mythos",
      client_uri: CIMD_ORIGIN,
      redirect_uris: [redirectUri()],
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
      token_endpoint_auth_method: "none",
    }),
  });
  if (r.status < 200 || r.status >= 300) throw new Error(`Registrierung fehlgeschlagen (HTTP ${r.status}): ${r.body.slice(0, 200)}`);
  const j = JSON.parse(r.body);
  return { clientId: j.client_id, clientSecret: j.client_secret };
}

const b64url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const randomString = (n = 48) => b64url(crypto.getRandomValues(new Uint8Array(n)));

async function tokenRequest(o: Pick<McpOAuth, "tokenEndpoint" | "clientId" | "clientSecret" | "resource">, params: Record<string, string>) {
  const form = new URLSearchParams({ ...params, client_id: o.clientId, resource: o.resource });
  if (o.clientSecret) form.set("client_secret", o.clientSecret);
  const r = await httpFetch(o.tokenEndpoint, {
    method: "POST",
    // MCP-Protocol-Version erzwingt eine CORS-Vorabfrage: so wird ein blockierter Code nicht "verbraucht".
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json", "MCP-Protocol-Version": PROTOCOL },
    body: form.toString(),
  });
  let j: any = {};
  try { j = JSON.parse(r.body); } catch { j = Object.fromEntries(new URLSearchParams(r.body)); }
  if (r.status < 200 || r.status >= 300 || !j.access_token) {
    throw new Error(j.error_description || j.error || `Token-Fehler (HTTP ${r.status})`);
  }
  return {
    accessToken: j.access_token as string,
    refreshToken: (j.refresh_token as string) || undefined,
    expiresAt: j.expires_in ? Date.now() + Number(j.expires_in) * 1000 : undefined,
  };
}

function saveOAuth(id: string, oauth: McpOAuth | undefined) {
  setMcpServers(getMcpServers().map(s => s.id === id ? { ...s, oauth } : s));
}

function waitForCode(state: string, popup: Window | null): Promise<string> {
  return new Promise((resolve, reject) => {
    const done = (data: any) => {
      if (!data || data.state !== state) return;
      cleanup();
      if (data.error) reject(new Error(data.error_description || data.error));
      else resolve(data.code);
    };
    const onStorage = (e: StorageEvent) => {
      if (e.key === RESULT_KEY && e.newValue) { try { done(JSON.parse(e.newValue)); } catch { /* ignorieren */ } }
    };
    const onMessage = (e: MessageEvent) => {
      if (e.origin === location.origin && e.data?.type === "mythos-mcp-oauth") done(e.data);
    };
    const timer = setTimeout(() => { cleanup(); reject(new Error("Anmeldung abgebrochen")); }, 5 * 60_000);
    const cleanup = () => {
      clearTimeout(timer);
      window.removeEventListener("storage", onStorage);
      window.removeEventListener("message", onMessage);
      try { localStorage.removeItem(RESULT_KEY); } catch { /* egal */ }
      try { popup?.close(); } catch { /* egal */ }
    };
    window.addEventListener("storage", onStorage);
    window.addEventListener("message", onMessage);
  });
}

/** Startet die Anmeldung. Muss direkt aus einem Klick aufgerufen werden (sonst blockiert der Browser das Fenster). */
export async function login(server: McpServer): Promise<McpServer> {
  const popup = window.open("about:blank", "mythos-mcp-oauth", "width=520,height=720");
  try {
    const { meta, resource, scope } = await discover(server.url);
    const { clientId, clientSecret } = await register(meta);
    const verifier = randomString();
    const challenge = b64url(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier))));
    const state = randomString(16);
    const auth = new URL(meta.authorization_endpoint);
    auth.searchParams.set("response_type", "code");
    auth.searchParams.set("client_id", clientId);
    auth.searchParams.set("redirect_uri", redirectUri());
    auth.searchParams.set("code_challenge", challenge);
    auth.searchParams.set("code_challenge_method", "S256");
    auth.searchParams.set("state", state);
    auth.searchParams.set("resource", resource);
    if (scope) auth.searchParams.set("scope", scope);
    if (popup) popup.location.href = auth.toString();
    else window.open(auth.toString(), "mythos-mcp-oauth");

    const code = await waitForCode(state, popup);
    const base = { tokenEndpoint: meta.token_endpoint as string, clientId, clientSecret, resource };
    const tokens = await tokenRequest(base, { grant_type: "authorization_code", code, redirect_uri: redirectUri(), code_verifier: verifier });
    const oauth: McpOAuth = { ...base, ...tokens };
    saveOAuth(server.id, oauth);
    return { ...server, oauth };
  } catch (e) {
    try { popup?.close(); } catch { /* egal */ }
    throw e;
  }
}

export function logout(server: McpServer) { saveOAuth(server.id, undefined); }

/** Token erneuern. Gibt den neuen Stand zurück oder wirft McpAuthError. */
export async function refresh(server: McpServer): Promise<McpServer> {
  const o = getMcpServers().find(s => s.id === server.id)?.oauth || server.oauth;
  if (!o?.refreshToken) throw new McpAuthError();
  try {
    const t = await tokenRequest(o, { grant_type: "refresh_token", refresh_token: o.refreshToken });
    const oauth: McpOAuth = { ...o, ...t, refreshToken: t.refreshToken || o.refreshToken };
    saveOAuth(server.id, oauth);
    return { ...server, oauth };
  } catch {
    throw new McpAuthError("Anmeldung abgelaufen");
  }
}

/** Authorization-Header für einen Server (OAuth-Token hat Vorrang, läuft er bald ab, wird er erneuert). */
export async function authHeader(server: McpServer): Promise<string | undefined> {
  const fresh = getMcpServers().find(s => s.id === server.id) || server;
  const o = fresh.oauth;
  if (!o) return fresh.auth;
  if (o.expiresAt && o.expiresAt - 60_000 < Date.now() && o.refreshToken) {
    const r = await refresh(fresh);
    return `Bearer ${r.oauth!.accessToken}`;
  }
  return `Bearer ${o.accessToken}`;
}

/** Wird auf der Rückleitungsseite /mcp-oauth aufgerufen. */
export function finishRedirect(params: URLSearchParams) {
  const data = {
    type: "mythos-mcp-oauth",
    state: params.get("state"),
    code: params.get("code"),
    error: params.get("error"),
    error_description: params.get("error_description"),
  };
  try { localStorage.setItem(RESULT_KEY, JSON.stringify({ ...data, at: Date.now() })); } catch { /* egal */ }
  try { window.opener?.postMessage(data, location.origin); } catch { /* egal */ }
}
