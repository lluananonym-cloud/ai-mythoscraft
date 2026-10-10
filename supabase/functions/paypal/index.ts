// Abos über PayPal (Subscriptions API). Braucht nur zwei Secrets in Lovable Cloud:
//   PAYPAL_CLIENT_ID und PAYPAL_CLIENT_SECRET (aus developer.paypal.com → Apps & Credentials).
// Optional PAYPAL_MODE=sandbox zum Testen mit Spielgeld (Standard: live).
// Produkt und Pläne (Light, Pro) legt die Funktion beim ersten Aufruf selbst bei PayPal an.
// Kein Webhook nötig: Beim Abschluss und beim Öffnen der App fragt sie PayPal direkt nach dem
// Status des Abos und verlängert "expires_at" bis zur nächsten Abbuchung.
//
// Aktionen (POST JSON { action, ... }):
//   config                        -> { clientId, mode, plans: { light, pro } }
//   activate { subscriptionId }   -> Abo prüfen und freischalten (eingeloggt)
//   sync                          -> eigenes PayPal-Abo neu prüfen (eingeloggt)
//   cancel                        -> eigenes PayPal-Abo kündigen (eingeloggt, läuft bis Monatsende)
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

type Tier = "light" | "pro";
const PLANS: Record<Tier, { name: string; price: string; description: string }> = {
  light: { name: "Mythos AI Light", price: "2.99", description: "150 Chats pro Tag, Bilder, keine Werbung" },
  pro: { name: "Mythos AI Pro", price: "6.99", description: "Unbegrenzte Chats, Bilder, Musik, Video, Sprache, Mythos Code" },
};
const PRODUCT_NAME = "Mythos AI";

const admin = () => createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

async function secret(name: string): Promise<string> {
  const env = Deno.env.get(name);
  if (env) return env.trim();
  try {
    const { data } = await admin().from("app_secrets").select("value").eq("name", name).maybeSingle();
    return ((data as { value?: string } | null)?.value ?? "").trim();
  } catch { return ""; }
}

async function creds() {
  const [id, sec, mode] = await Promise.all([secret("PAYPAL_CLIENT_ID"), secret("PAYPAL_CLIENT_SECRET"), secret("PAYPAL_MODE")]);
  const sandbox = /sandbox|test/i.test(mode);
  return { id, sec, sandbox, base: sandbox ? "https://api-m.sandbox.paypal.com" : "https://api-m.paypal.com" };
}

let tokenCache: { token: string; until: number; key: string } | null = null;
async function accessToken(c: Awaited<ReturnType<typeof creds>>): Promise<string> {
  const key = c.id + c.base;
  if (tokenCache && tokenCache.key === key && Date.now() < tokenCache.until) return tokenCache.token;
  const r = await fetch(`${c.base}/v1/oauth2/token`, {
    method: "POST",
    headers: { Authorization: `Basic ${btoa(`${c.id}:${c.sec}`)}`, "Content-Type": "application/x-www-form-urlencoded" },
    body: "grant_type=client_credentials",
  });
  if (!r.ok) throw new Error(`PayPal-Anmeldung fehlgeschlagen (${r.status}). Stimmen Client-ID, Secret und Modus (live/sandbox)?`);
  const j = await r.json();
  tokenCache = { token: j.access_token, until: Date.now() + (Number(j.expires_in) - 120) * 1000, key };
  return j.access_token;
}

async function pp(c: Awaited<ReturnType<typeof creds>>, path: string, init: RequestInit = {}) {
  const r = await fetch(`${c.base}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${await accessToken(c)}`, "Content-Type": "application/json", Prefer: "return=representation", ...(init.headers ?? {}) },
  });
  const text = await r.text();
  const body = text ? JSON.parse(text) : {};
  if (!r.ok) throw new Error(`PayPal ${path}: ${r.status} ${body?.message ?? text.slice(0, 200)}`);
  return body;
}

let planCache: { key: string; plans: Record<Tier, string> } | null = null;
/** Sucht Produkt und Pläne bei PayPal und legt fehlende an. Ergebnis wird pro Instanz gemerkt. */
async function ensurePlans(c: Awaited<ReturnType<typeof creds>>): Promise<Record<Tier, string>> {
  if (planCache?.key === c.id + c.base) return planCache.plans;
  const products = await pp(c, "/v1/catalogs/products?page_size=20");
  let product = (products.products ?? []).find((p: { name: string }) => p.name === PRODUCT_NAME);
  if (!product) {
    product = await pp(c, "/v1/catalogs/products", {
      method: "POST",
      body: JSON.stringify({ name: PRODUCT_NAME, description: "KI-Assistent: Chat, Bilder, Musik, Code", type: "SERVICE", category: "SOFTWARE" }),
    });
  }
  const list = await pp(c, `/v1/billing/plans?product_id=${product.id}&page_size=20`);
  const plans = {} as Record<Tier, string>;
  for (const tier of Object.keys(PLANS) as Tier[]) {
    const want = PLANS[tier];
    const found = (list.plans ?? []).find((p: { name: string; status: string }) => p.name === want.name && p.status === "ACTIVE");
    if (found) { plans[tier] = found.id; continue; }
    const created = await pp(c, "/v1/billing/plans", {
      method: "POST",
      body: JSON.stringify({
        product_id: product.id,
        name: want.name,
        description: want.description,
        status: "ACTIVE",
        billing_cycles: [{
          frequency: { interval_unit: "MONTH", interval_count: 1 },
          tenure_type: "REGULAR", sequence: 1, total_cycles: 0,
          pricing_scheme: { fixed_price: { value: want.price, currency_code: "EUR" } },
        }],
        payment_preferences: { auto_bill_outstanding: true, payment_failure_threshold: 1 },
      }),
    });
    plans[tier] = created.id;
  }
  planCache = { key: c.id + c.base, plans };
  return plans;
}

async function userId(req: Request): Promise<string | null> {
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
  const { data } = await client.auth.getClaims(token);
  return (data?.claims?.sub as string) ?? null;
}

/** Abo bei PayPal prüfen und in "subscriptions" eintragen. */
async function apply(c: Awaited<ReturnType<typeof creds>>, uid: string, subId: string) {
  const plans = await ensurePlans(c);
  const s = await pp(c, `/v1/billing/subscriptions/${encodeURIComponent(subId)}`);
  const tier = (Object.keys(plans) as Tier[]).find((t) => plans[t] === s.plan_id);
  if (!tier) return { ok: false, error: "Dieses PayPal-Abo gehört zu keinem Mythos-Tarif." };
  if (s.custom_id && s.custom_id !== uid) return { ok: false, error: "Dieses PayPal-Abo gehört zu einem anderen Konto." };
  const active = s.status === "ACTIVE" || s.status === "APPROVED";
  // Bezahlt bis zur nächsten Abbuchung (+2 Tage Puffer); gekündigte Abos laufen bis dahin weiter.
  const next = s.billing_info?.next_billing_time;
  const last = s.billing_info?.last_payment?.time;
  const paidUntil = next ? new Date(next).getTime()
    : last ? new Date(last).getTime() + 31 * 86400e3
    : Date.now() + (active ? 31 * 86400e3 : 0);
  const expires = (active || s.status === "CANCELLED")
    ? new Date(paidUntil + 2 * 86400e3).toISOString()
    : new Date().toISOString(); // ausgesetzt oder abgelaufen
  const row = { user_id: uid, tier, source: "paid", expires_at: expires, note: `paypal:${s.id}:${s.status}` };
  const { error } = await admin().from("subscriptions").upsert(row, { onConflict: "user_id" });
  if (error) {
    // Ältere Datenbank kennt nur free/pro: dann fehlt die Migration für "light".
    if (/tier_check|check constraint/i.test(error.message)) return { ok: false, error: "light_needs_migration" };
    throw new Error(error.message);
  }
  return { ok: true, tier, status: s.status, expires_at: row.expires_at };
}

async function ownSubId(uid: string): Promise<string | null> {
  const { data } = await admin().from("subscriptions").select("note").eq("user_id", uid).maybeSingle();
  const m = String((data as { note?: string } | null)?.note ?? "").match(/^paypal:([^:]+)/);
  return m?.[1] ?? null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const body = await req.json().catch(() => ({}));
    const c = await creds();
    if (!c.id || !c.sec) return json({ error: "not_configured" }, 503);

    if (body.action === "config") {
      const plans = await ensurePlans(c);
      return json({ clientId: c.id, mode: c.sandbox ? "sandbox" : "live", plans });
    }

    const uid = await userId(req);
    if (!uid) return json({ error: "Bitte zuerst anmelden." }, 401);

    if (body.action === "activate") {
      const subId = String(body.subscriptionId ?? "").trim();
      if (!/^I-[A-Z0-9]{6,}$/.test(subId)) return json({ error: "Ungültige Abo-ID" }, 400);
      return json(await apply(c, uid, subId));
    }
    if (body.action === "sync") {
      const subId = await ownSubId(uid);
      if (!subId) return json({ ok: true, none: true });
      return json(await apply(c, uid, subId));
    }
    if (body.action === "cancel") {
      const subId = await ownSubId(uid);
      if (!subId) return json({ error: "Kein PayPal-Abo gefunden." }, 404);
      await pp(c, `/v1/billing/subscriptions/${encodeURIComponent(subId)}/cancel`, {
        method: "POST", body: JSON.stringify({ reason: "Vom Nutzer in Mythos AI gekündigt" }),
      });
      return json(await apply(c, uid, subId));
    }
    return json({ error: "Unbekannte Aktion" }, 400);
  } catch (e) {
    console.error("[paypal]", e);
    return json({ error: e instanceof Error ? e.message : "Fehler" }, 500);
  }
});
