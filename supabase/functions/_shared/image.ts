// Gemeinsame Bildgenerierung für Chat (/image), image-gen (Video, Code-App /bild).
// Reihenfolge: Lovable-Gateway (mit Google-Fallback aus ai.ts) -> NVIDIA -> Pollinations (gratis) -> AI Horde (gratis, anonym).
// Sind die Lovable-Credits aufgebraucht (402), springt die Kette sofort weiter, statt jedes
// Lovable-Modell einzeln durchzuprobieren. Pollinations läuft ohne Schlüssel; mit dem optionalen
// Secret POLLINATIONS_API_KEY (kostenlos auf enter.pollinations.ai) gibt es höhere Limits.
// Daten-URLs werden in den öffentlichen Bucket "chat-uploads" hochgeladen, damit Chats keine
// riesigen base64-Strings speichern und Bilder überall angezeigt werden können.
import { createClient } from "npm:@supabase/supabase-js@2.103.3";
import { aiFetch } from "./ai.ts";

const MODELS = ["google/gemini-2.5-flash-image", "google/gemini-3.1-flash-image-preview"];

function admin() {
  const url = Deno.env.get("SUPABASE_URL"), sr = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  return url && sr ? createClient(url, sr) : null;
}

async function storeBytes(bytes: Uint8Array, mime: string): Promise<string | null> {
  const sb = admin();
  if (!sb) return null;
  const ext = mime.includes("jpeg") ? "jpg" : mime.includes("webp") ? "webp" : "png";
  const path = `generated/${new Date().toISOString().slice(0, 10)}/${crypto.randomUUID()}.${ext}`;
  const { error } = await sb.storage.from("chat-uploads").upload(path, bytes, { contentType: mime, upsert: false });
  if (error) { console.warn("[image] upload failed", error.message); return null; }
  return sb.storage.from("chat-uploads").getPublicUrl(path).data.publicUrl;
}

async function storeDataUrl(url: string): Promise<string> {
  const m = url.match(/^data:([^;]+);base64,(.+)$/);
  if (!m) return url;
  const bin = atob(m[2]);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return (await storeBytes(bytes, m[1])) ?? url;
}

async function viaGateway(prompt: string, lovableKey: string | undefined, errors: string[]): Promise<string | null> {
  for (const model of MODELS) {
    try {
      const r = await aiFetch("gateway", {
        method: "POST",
        headers: { Authorization: `Bearer ${lovableKey ?? ""}`, "Content-Type": "application/json" },
        body: JSON.stringify({ model, messages: [{ role: "user", content: `Generate a high quality image: ${prompt}` }], modalities: ["image", "text"] }),
      });
      if (!r.ok) {
        const info = (await r.text()).slice(0, 160);
        errors.push(`${model}: HTTP ${r.status} ${info}`);
        // Credits leer / kein Google-Schlüssel: das zweite Modell scheitert genauso -> direkt zum Backup.
        if (r.status === 402 || r.status === 429 || /no google key|quota|credits/i.test(info)) break;
        continue;
      }
      const j = await r.json();
      const url = j.choices?.[0]?.message?.images?.[0]?.image_url?.url;
      if (url) return url;
      errors.push(`${model}: kein Bild`);
    } catch (e) { errors.push(`${model}: ${e instanceof Error ? e.message : String(e)}`); }
  }
  return null;
}

async function viaNvidia(prompt: string, errors: string[]): Promise<string | null> {
  let key = Deno.env.get("NVIDIA_IMAGE_API_KEY") || Deno.env.get("NVIDIA_API_KEY") || "";
  if (!key) {
    try {
      const { data } = await admin()!.from("app_secrets").select("name, value").in("name", ["NVIDIA_IMAGE_API_KEY", "NVIDIA_API_KEY"]);
      const s: Record<string, string> = {}; for (const r of data ?? []) s[r.name] = r.value;
      key = s.NVIDIA_IMAGE_API_KEY || s.NVIDIA_API_KEY || "";
    } catch { /* Tabelle fehlt -> kein NVIDIA */ }
  }
  if (!key) { errors.push("nvidia: kein Schlüssel hinterlegt"); return null; }
  try {
    const r = await fetch("https://integrate.api.nvidia.com/v1/images/generations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
      body: JSON.stringify({ model: "qwen-image", prompt: prompt.slice(0, 2000), n: 1 }),
    });
    if (!r.ok) { errors.push(`nvidia: HTTP ${r.status}`); return null; }
    const img = (await r.json()).data?.[0];
    return img?.url ?? (img?.b64_json ? `data:image/png;base64,${img.b64_json}` : null);
  } catch (e) { errors.push(`nvidia: ${e instanceof Error ? e.message : String(e)}`); return null; }
}

async function pollinationsKey(): Promise<string> {
  const env = Deno.env.get("POLLINATIONS_API_KEY");
  if (env) return env;
  try {
    const { data } = await admin()!.from("app_secrets").select("value").eq("name", "POLLINATIONS_API_KEY").maybeSingle();
    return (data as { value?: string } | null)?.value ?? "";
  } catch { return ""; }
}

async function viaPollinations(prompt: string, errors: string[]): Promise<string | null> {
  const p = encodeURIComponent(prompt.slice(0, 800));
  const seed = Math.floor(Math.random() * 1e6);
  const key = await pollinationsKey();
  // Mit Schlüssel die neue API, sonst (und als zweiter Versuch) der freie Endpunkt ohne Anmeldung.
  const tries: { url: string; headers?: Record<string, string> }[] = [];
  if (key) tries.push({ url: `https://gen.pollinations.ai/image/${p}?model=flux&width=1024&height=1024&nologo=true&seed=${seed}`, headers: { Authorization: `Bearer ${key}` } });
  tries.push({ url: `https://image.pollinations.ai/prompt/${p}?model=flux&width=1024&height=1024&nologo=true&private=true&referrer=mythoscraft&seed=${seed}` });
  for (const t of tries) {
    try {
      const r = await fetch(t.url, { headers: t.headers, signal: AbortSignal.timeout(35_000) });
      const mime = (r.headers.get("content-type") || "").split(";")[0];
      if (!r.ok || !mime.startsWith("image/")) { errors.push(`pollinations: HTTP ${r.status}`); continue; }
      const bytes = new Uint8Array(await r.arrayBuffer());
      return (await storeBytes(bytes, mime)) ?? (t.headers ? null : t.url);
    } catch (e) { errors.push(`pollinations: ${e instanceof Error ? e.message : String(e)}`); }
  }
  return null;
}

// AI Horde: freies, community-betriebenes Netz. Anonym (Schlüssel 0000000000) ohne Anmeldung,
// dafür manchmal langsam. Optional mit eigenem Schlüssel (AI_HORDE_API_KEY) schneller.
async function viaHorde(prompt: string, errors: string[], deadline: number): Promise<string | null> {
  const key = Deno.env.get("AI_HORDE_API_KEY") || "0000000000";
  const H = { "Content-Type": "application/json", apikey: key, "Client-Agent": "mythoscraft:1.0:mythoscraft.online" };
  try {
    const r = await fetch("https://aihorde.net/api/v2/generate/async", {
      method: "POST", headers: H, signal: AbortSignal.timeout(15_000),
      body: JSON.stringify({ prompt: prompt.slice(0, 900), params: { width: 512, height: 512, steps: 20, n: 1 }, nsfw: false, censor_nsfw: true, r2: true, shared: true }),
    });
    if (!r.ok) { errors.push(`horde: HTTP ${r.status} ${(await r.text()).slice(0, 120)}`); return null; }
    const { id } = await r.json();
    if (!id) { errors.push("horde: keine Auftrags-ID"); return null; }
    while (Date.now() < deadline) {
      await new Promise((res) => setTimeout(res, 3000));
      const c = await fetch(`https://aihorde.net/api/v2/generate/check/${id}`, { headers: H, signal: AbortSignal.timeout(10_000) }).then((x) => x.json()).catch(() => null);
      if (c?.faulted) { errors.push("horde: Auftrag fehlgeschlagen"); return null; }
      if (!c?.done) continue;
      const st = await fetch(`https://aihorde.net/api/v2/generate/status/${id}`, { headers: H, signal: AbortSignal.timeout(15_000) }).then((x) => x.json());
      const img = st?.generations?.[0]?.img as string | undefined;
      if (!img) { errors.push("horde: kein Bild"); return null; }
      if (!/^https?:/i.test(img)) return `data:image/webp;base64,${img}`;
      // Horde-Links laufen ab -> ins eigene Storage kopieren.
      const g = await fetch(img, { signal: AbortSignal.timeout(20_000) });
      const mime = (g.headers.get("content-type") || "image/webp").split(";")[0];
      return (await storeBytes(new Uint8Array(await g.arrayBuffer()), mime)) ?? img;
    }
    fetch(`https://aihorde.net/api/v2/generate/status/${id}`, { method: "DELETE", headers: H }).catch(() => {});
    errors.push("horde: zu langsam (Warteschlange)");
  } catch (e) { errors.push(`horde: ${e instanceof Error ? e.message : String(e)}`); }
  return null;
}

export type ImageProvider = "nano-banana" | "nvidia" | "pollinations" | "horde";
export const PROVIDER_LABEL: Record<ImageProvider, string> = {
  "nano-banana": "Nano Banana",
  nvidia: "Qwen-Image (NVIDIA)",
  pollinations: "Flux (Pollinations)",
  horde: "Stable Diffusion (AI Horde)",
};

/** Kurze Gründe pro Anbieter (eine Zeile je Anbieter) für die Fehlermeldung im Chat. */
export function explainErrors(error: string | undefined): string[] {
  const seen = new Map<string, string>();
  for (const raw of (error || "").split(" | ")) {
    const m = raw.match(/^([^:]+):\s*(.*)$/);
    if (!m) continue;
    const name = /gemini|google/i.test(m[1]) ? "Lovable/Google" : m[1][0].toUpperCase() + m[1].slice(1);
    const why = m[2].replace(/\{"error":"?|"?\}$/g, "").replace(/AI unavailable:\s*/i, "").replace(/no google key configured/i, "Credits leer und kein GOOGLE_AI_API_KEY").slice(0, 110);
    seen.set(name, why);
  }
  return [...seen].map(([k, v]) => `${k}: ${v}`);
}

/** Erzeugt ein Bild und liefert eine anzeigbare URL (möglichst https aus dem Storage). */
export async function generateImage(prompt: string, lovableKey?: string): Promise<{ url: string | null; provider?: ImageProvider; error?: string }> {
  const errors: string[] = [];
  // Edge Functions dürfen nicht ewig laufen: AI Horde bekommt nur die restliche Zeit.
  const deadline = Date.now() + 130_000;
  const chain: [ImageProvider, () => Promise<string | null>][] = [
    ["nano-banana", () => viaGateway(prompt, lovableKey, errors)],
    ["nvidia", () => viaNvidia(prompt, errors)],
    ["pollinations", () => viaPollinations(prompt, errors)],
    ["horde", () => viaHorde(prompt, errors, deadline)],
  ];
  for (const [provider, run] of chain) {
    if (Date.now() > deadline - 10_000) { errors.push(`${provider}: keine Zeit mehr`); break; }
    const url = await run();
    if (url) return { url: await storeDataUrl(url), provider };
  }
  console.error("[image] all providers failed", errors.join(" | "));
  return { url: null, error: errors.join(" | ") };
}
