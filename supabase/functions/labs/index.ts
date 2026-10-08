// Mythos Labs: Backend für Live-Arena, Foto zu App, KI-Team, Tagesrückblick und Mythos-Marktplatz.
// Gespeichert wird (wie bei "remote") in einem privaten Storage-Bucket statt in eigenen Tabellen,
// weil Migrationen, die über GitHub kommen, in Lovable Cloud nicht ausgeführt werden.
// POST { kind, ... }
import { createClient } from "npm:@supabase/supabase-js@2.103.3";
import { aiChat } from "../_shared/ai.ts";
import { generateImage } from "../_shared/image.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

const BUCKET = "mythos-labs";
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
let bucketReady = false;
async function ensureBucket() {
  if (bucketReady) return;
  const { error } = await admin.storage.createBucket(BUCKET, { public: false });
  if (error && !/exist/i.test(error.message)) console.warn("[labs] createBucket", error.message);
  bucketReady = true;
}
async function get<T>(path: string): Promise<T | null> {
  await ensureBucket();
  const { data, error } = await admin.storage.from(BUCKET).download(path);
  if (error || !data) return null;
  try { return JSON.parse(await data.text()) as T; } catch { return null; }
}
async function put(path: string, value: unknown) {
  await ensureBucket();
  const { error } = await admin.storage.from(BUCKET).upload(path, new Blob([JSON.stringify(value)], { type: "application/json" }), { upsert: true, contentType: "application/json" });
  if (error) throw new Error(error.message);
}

// Modelle, die in der Arena gegeneinander antreten (und die "ask" erlaubt).
const ARENA_MODELS: Record<string, string> = {
  "google/gemini-3.6-flash": "Gemini 3.6 Flash",
  "google/gemini-3.1-flash-lite": "Gemini 3.1 Flash Lite",
  "openai/gpt-5.4-mini": "GPT-5.4 Mini",
  "openai/gpt-5.6-luna": "GPT-5.6 Luna",
};
const ASK_MODELS = new Set([...Object.keys(ARENA_MODELS), "google/gemini-3.1-pro-preview", "openai/gpt-5.4", "openai/gpt-5.5"]);
const DEFAULT_MODEL = "google/gemini-3.6-flash";

type Votes = Record<string, { wins: number; losses: number; ties: number }>;
type MarketEntry = {
  id: string; user_id: string; author: string; type: "app" | "image"; title: string; description: string;
  image_url?: string; likes: string[]; copies: number; created_at: string;
};
type MarketItem = MarketEntry & { html?: string; prompt?: string };
const INDEX = "market/index.json";
const itemPath = (id: string) => `market/items/${id}.json`;
const ID_RE = /^[0-9a-f-]{36}$/;

async function ask(body: any): Promise<string> {
  const model = ASK_MODELS.has(body.model) ? body.model : DEFAULT_MODEL;
  const messages = Array.isArray(body.messages) ? body.messages.slice(-12) : [];
  if (!messages.length) throw new Error("Keine Nachricht");
  const out = [...(typeof body.system === "string" && body.system ? [{ role: "system", content: body.system.slice(0, 12000) }] : []), ...messages];
  const r = await aiChat({ model, messages: out, stream: false, ...(body.json ? { response_format: { type: "json_object" } } : {}) });
  if (!r.ok) {
    const t = await r.text().catch(() => "");
    throw new Error(r.status === 429 ? "Gerade zu viele Anfragen, bitte kurz warten." : r.status === 402 ? "KI-Guthaben ist leer." : "KI nicht erreichbar: " + t.slice(0, 160));
  }
  const j = await r.json().catch(() => null);
  return j?.choices?.[0]?.message?.content ?? "";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const body = await req.json().catch(() => ({}));
    const kind = String(body.kind || "");

    // Angemeldeter Nutzer (für alles, was KI kostet oder etwas speichert).
    const token = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
    const { data: auth } = token ? await admin.auth.getUser(token) : { data: { user: null } };
    const user = auth?.user ?? null;

    // Öffentlich lesbar: Marktplatz-Liste und Arena-Rangliste.
    if (kind === "market_list") {
      const list = (await get<MarketEntry[]>(INDEX)) || [];
      return json({ items: list.map((e) => ({ ...e, likes: e.likes.length, liked: !!user && e.likes.includes(user.id), mine: !!user && e.user_id === user.id })) });
    }
    if (kind === "arena_board") {
      const votes = (await get<Votes>("arena/votes.json")) || {};
      return json({ board: Object.entries(ARENA_MODELS).map(([id, label]) => ({ id, label, ...(votes[id] || { wins: 0, losses: 0, ties: 0 }) })) });
    }

    if (!user) return json({ error: "Bitte zuerst anmelden." }, 401);

    if (kind === "ask") return json({ text: await ask(body) });

    if (kind === "image") {
      const prompt = String(body.prompt || "").slice(0, 900);
      if (!prompt) return json({ error: "Kein Bild-Wunsch" }, 400);
      const r = await generateImage(prompt);
      return r.url ? json({ url: r.url }) : json({ error: "Bild konnte nicht erzeugt werden" }, 502);
    }

    if (kind === "arena_fight") {
      const prompt = String(body.prompt || "").slice(0, 4000);
      if (!prompt) return json({ error: "Keine Aufgabe" }, 400);
      const ids = Object.keys(ARENA_MODELS).sort(() => Math.random() - 0.5).slice(0, 2);
      const system = "Du bist ein hilfreicher KI-Assistent. Antworte in der Sprache des Nutzers (Standard Deutsch), klar und mit Markdown. Nenne niemals deinen Modellnamen oder Anbieter.";
      const answers = await Promise.all(ids.map((model) =>
        ask({ model, system, messages: [{ role: "user", content: prompt }] }).catch((e) => "⚠️ " + (e instanceof Error ? e.message : String(e)))));
      return json({ a: { id: ids[0], label: ARENA_MODELS[ids[0]], text: answers[0] }, b: { id: ids[1], label: ARENA_MODELS[ids[1]], text: answers[1] } });
    }

    if (kind === "arena_vote") {
      const a = String(body.a || ""), b = String(body.b || ""), winner = String(body.winner || "");
      if (!ARENA_MODELS[a] || !ARENA_MODELS[b] || a === b || !["a", "b", "tie"].includes(winner)) return json({ error: "Ungültige Abstimmung" }, 400);
      const votes = (await get<Votes>("arena/votes.json")) || {};
      for (const id of [a, b]) votes[id] = votes[id] || { wins: 0, losses: 0, ties: 0 };
      if (winner === "tie") { votes[a].ties++; votes[b].ties++; }
      else { const w = winner === "a" ? a : b, l = winner === "a" ? b : a; votes[w].wins++; votes[l].losses++; }
      await put("arena/votes.json", votes);
      return json({ ok: true });
    }

    if (kind === "market_publish") {
      const type = body.type === "image" ? "image" : "app";
      const title = String(body.title || "").trim().slice(0, 80);
      const html = type === "app" ? String(body.html || "") : "";
      const image_url = type === "image" ? String(body.image_url || "") : "";
      if (!title) return json({ error: "Titel fehlt" }, 400);
      if (type === "app" && (html.length < 50 || html.length > 400_000)) return json({ error: "App ist leer oder zu groß" }, 400);
      if (type === "image" && !/^https:\/\//i.test(image_url)) return json({ error: "Bild-Link fehlt" }, 400);
      const entry: MarketEntry = {
        id: crypto.randomUUID(), user_id: user.id, type, title,
        author: String(body.author || user.email?.split("@")[0] || "Mythos-Nutzer").slice(0, 40),
        description: String(body.description || "").slice(0, 300),
        ...(image_url ? { image_url } : {}), likes: [], copies: 0, created_at: new Date().toISOString(),
      };
      await put(itemPath(entry.id), { ...entry, html, prompt: String(body.prompt || "").slice(0, 1000) });
      const list = (await get<MarketEntry[]>(INDEX)) || [];
      await put(INDEX, [entry, ...list].slice(0, 500));
      return json({ ok: true, id: entry.id });
    }

    if (kind === "market_get" || kind === "market_like" || kind === "market_take" || kind === "market_delete") {
      const id = String(body.id || "");
      if (!ID_RE.test(id)) return json({ error: "Unbekannter Eintrag" }, 400);
      const list = (await get<MarketEntry[]>(INDEX)) || [];
      const i = list.findIndex((e) => e.id === id);
      if (i < 0) return json({ error: "Nicht mehr im Marktplatz" }, 404);
      const e = list[i];
      if (kind === "market_get") {
        const item = await get<MarketItem>(itemPath(id));
        return json({ item: item ? { ...item, likes: item.likes.length } : null });
      }
      if (kind === "market_like") {
        e.likes = e.likes.includes(user.id) ? e.likes.filter((u) => u !== user.id) : [...e.likes, user.id];
      } else if (kind === "market_take") {
        e.copies++;
      } else {
        if (e.user_id !== user.id) return json({ error: "Nur eigene Einträge löschen" }, 403);
        list.splice(i, 1);
        await admin.storage.from(BUCKET).remove([itemPath(id)]);
      }
      await put(INDEX, list);
      const item = kind === "market_take" ? await get<MarketItem>(itemPath(id)) : null;
      return json({ ok: true, likes: e.likes.length, liked: e.likes.includes(user.id), item });
    }

    return json({ error: "Unbekannte Aktion" }, 400);
  } catch (e) {
    return json({ error: e instanceof Error ? e.message : String(e) }, 500);
  }
});
