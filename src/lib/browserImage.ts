// Bild direkt aus dem Browser erzeugen, wenn alle Server-Anbieter ausgefallen sind.
// Der Browser hat eine eigene IP und damit eigene Gratis-Limits (Server-IPs sind oft gesperrt).
// Reihenfolge: Pollinations (schnell, ohne Schlüssel) -> AI Horde (Community-Netz, langsamer).
// Fertige Bilder werden, wenn möglich, in den eigenen Storage kopiert, damit sie nicht ablaufen.
import { supabase } from "@/integrations/supabase/client";
import { externalAllowed } from "@/lib/consent";

const HORDE = "https://aihorde.net/api/v2";
const HORDE_HEADERS = { "Content-Type": "application/json", apikey: "0000000000", "Client-Agent": "mythoscraft:1.0:mythoscraft.online" };

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export function pollinationsUrl(prompt: string): string {
  const seed = Math.floor(Math.random() * 1e6);
  return `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt.slice(0, 800))}?model=flux&width=1024&height=1024&nologo=true&referrer=mythoscraft&seed=${seed}`;
}

/** Lädt eine Bild-URL wie ein <img>. true, sobald sie wirklich ein Bild liefert. */
function loads(url: string, timeoutMs: number): Promise<boolean> {
  return new Promise((resolve) => {
    const img = new Image();
    const t = setTimeout(() => { img.src = ""; resolve(false); }, timeoutMs);
    img.onload = () => { clearTimeout(t); resolve(img.naturalWidth > 32); };
    img.onerror = () => { clearTimeout(t); resolve(false); };
    img.src = url;
  });
}

async function store(blob: Blob): Promise<string | null> {
  try {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) return null;
    const ext = blob.type.includes("jpeg") ? "jpg" : blob.type.includes("webp") ? "webp" : "png";
    const path = `${user.id}/generated-${Date.now()}.${ext}`;
    const { error } = await supabase.storage.from("chat-uploads").upload(path, blob, { contentType: blob.type || "image/png" });
    if (error) return null;
    return supabase.storage.from("chat-uploads").getPublicUrl(path).data.publicUrl;
  } catch { return null; }
}

async function viaPollinations(prompt: string, reasons: string[]): Promise<string | null> {
  for (let attempt = 0; attempt < 2; attempt++) {
    const url = pollinationsUrl(prompt);
    if (await loads(url, 60_000)) {
      // Kopie im eigenen Storage, falls der Anbieter CORS erlaubt; sonst bleibt der Link.
      try {
        const r = await fetch(url);
        if (r.ok) return (await store(await r.blob())) ?? url;
      } catch { /* CORS: Link reicht */ }
      return url;
    }
    // Gratis-Limit: etwa ein Bild alle 15 Sekunden.
    if (attempt === 0) await sleep(16_000);
  }
  reasons.push("Pollinations (Browser): nicht erreichbar oder Limit");
  return null;
}

async function viaHorde(prompt: string, reasons: string[], signal?: AbortSignal): Promise<string | null> {
  try {
    const r = await fetch(`${HORDE}/generate/async`, {
      method: "POST", headers: HORDE_HEADERS, signal,
      body: JSON.stringify({ prompt: prompt.slice(0, 900), params: { width: 512, height: 512, steps: 20, n: 1 }, nsfw: false, censor_nsfw: true, r2: true, shared: true }),
    });
    if (!r.ok) { reasons.push(`AI Horde: HTTP ${r.status}`); return null; }
    const { id } = await r.json();
    if (!id) { reasons.push("AI Horde: kein Auftrag"); return null; }
    const until = Date.now() + 4 * 60_000;
    while (Date.now() < until) {
      if (signal?.aborted) break;
      await sleep(3000);
      const c = await fetch(`${HORDE}/generate/check/${id}`, { headers: HORDE_HEADERS, signal }).then((x) => x.json()).catch(() => null);
      if (c?.faulted) { reasons.push("AI Horde: Auftrag fehlgeschlagen"); return null; }
      if (!c?.done) continue;
      const st = await fetch(`${HORDE}/generate/status/${id}`, { headers: HORDE_HEADERS, signal }).then((x) => x.json());
      const img = st?.generations?.[0]?.img as string | undefined;
      if (!img) { reasons.push("AI Horde: kein Bild"); return null; }
      if (!/^https?:/i.test(img)) return `data:image/webp;base64,${img}`;
      // Horde-Links laufen ab: ins eigene Storage kopieren.
      try { const b = await (await fetch(img)).blob(); return (await store(b)) ?? img; } catch { return img; }
    }
    fetch(`${HORDE}/generate/status/${id}`, { method: "DELETE", headers: HORDE_HEADERS }).catch(() => {});
    reasons.push("AI Horde: Warteschlange zu lang");
  } catch (e) {
    if ((e as Error)?.name !== "AbortError") reasons.push(`AI Horde: ${(e as Error)?.message || "Fehler"}`);
  }
  return null;
}

/** Erzeugt ein Bild im Browser. Liefert die URL oder null plus Gründe pro Anbieter. */
export async function generateImageInBrowser(prompt: string, signal?: AbortSignal): Promise<{ url: string | null; reasons: string[] }> {
  const reasons: string[] = [];
  // Direkt-Laden bei Drittanbietern nur mit Einwilligung (Cookie-Einstellungen → Externe KI-Dienste).
  if (!externalAllowed()) {
    reasons.push("Browser-Backup ist aus. Erlaube unten bei „Cookie-Einstellungen“ die externen KI-Dienste, dann klappt es auch bei vollen Servern.");
    return { url: null, reasons };
  }
  const url = (await viaPollinations(prompt, reasons)) ?? (signal?.aborted ? null : await viaHorde(prompt, reasons, signal));
  return { url, reasons };
}
