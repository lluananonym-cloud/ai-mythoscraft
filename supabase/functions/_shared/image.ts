// Gemeinsame Bildgenerierung für Chat (/image), image-gen (Video, Code-App /bild).
// Reihenfolge: Lovable-Gateway (mit Google-Fallback aus ai.ts) -> NVIDIA -> Pollinations (gratis, ohne Schlüssel).
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
      if (!r.ok) { errors.push(`${model}: HTTP ${r.status} ${(await r.text()).slice(0, 160)}`); continue; }
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
  if (!key) return null;
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

async function viaPollinations(prompt: string, errors: string[]): Promise<string | null> {
  try {
    const url = `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt.slice(0, 800))}?width=1024&height=1024&nologo=true&seed=${Math.floor(Math.random() * 1e6)}`;
    const r = await fetch(url, { signal: AbortSignal.timeout(60_000) });
    const mime = (r.headers.get("content-type") || "").split(";")[0];
    if (!r.ok || !mime.startsWith("image/")) { errors.push(`pollinations: HTTP ${r.status}`); return null; }
    const bytes = new Uint8Array(await r.arrayBuffer());
    return (await storeBytes(bytes, mime)) ?? url;
  } catch (e) { errors.push(`pollinations: ${e instanceof Error ? e.message : String(e)}`); return null; }
}

/** Erzeugt ein Bild und liefert eine anzeigbare URL (möglichst https aus dem Storage). */
export async function generateImage(prompt: string, lovableKey?: string): Promise<{ url: string | null; error?: string }> {
  const errors: string[] = [];
  const url = (await viaGateway(prompt, lovableKey, errors)) ?? (await viaNvidia(prompt, errors)) ?? (await viaPollinations(prompt, errors));
  if (!url) { console.error("[image] all providers failed", errors.join(" | ")); return { url: null, error: errors.join(" | ") }; }
  return { url: await storeDataUrl(url) };
}
