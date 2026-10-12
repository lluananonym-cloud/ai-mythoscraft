// Natürliche Vorlese-Stimme über Gemini-TTS (Google AI Studio, gleicher Schlüssel wie der Chat-Fallback).
// POST { text, voice? } -> audio/wav. Nur für angemeldete Nutzer (Supabase-Login) oder mit Mythos-API-Schlüssel
// (Code-App, Header x-api-key). Bei Fehlern/Kontingent-Ende antwortet sie mit Status != 200; die Apps
// fallen dann auf die beste Stimme im Gerät zurück.
import { createClient } from "npm:@supabase/supabase-js@2.103.3";
import { googleKeys } from "../_shared/ai.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-api-key, apikey, x-client-info, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Expose-Headers": "x-tts-provider",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

// Gemini-Stimmen (sprechen alle Deutsch). Unbekannte Namen -> Charon.
const VOICES = new Set(["Charon", "Kore", "Puck", "Aoede", "Fenrir", "Leda", "Orus", "Zephyr", "Sulafat", "Achird", "Gacrux", "Algieba"]);
const MODELS = ["gemini-2.5-flash-preview-tts", "gemini-2.5-flash-tts", "gemini-2.5-pro-preview-tts"];

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function allowed(req: Request): Promise<boolean> {
  const url = Deno.env.get("SUPABASE_URL")!;
  const apiKey = req.headers.get("x-api-key") || "";
  if (apiKey.startsWith("sk-ant-mythos-")) {
    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const { data } = await admin.from("api_keys").select("revoked").eq("key_hash", await sha256(apiKey)).maybeSingle();
    return !!data && !data.revoked;
  }
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "") || "";
  if (!token) return false;
  const { data, error } = await createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!).auth.getClaims(token);
  return !error && !!data?.claims?.sub;
}

/** 16-bit-PCM (mono) in eine WAV-Datei packen. */
function wav(pcm: Uint8Array, rate: number): Uint8Array {
  const out = new Uint8Array(44 + pcm.length);
  const v = new DataView(out.buffer);
  const str = (o: number, s: string) => { for (let i = 0; i < s.length; i++) out[o + i] = s.charCodeAt(i); };
  str(0, "RIFF"); v.setUint32(4, 36 + pcm.length, true); str(8, "WAVE");
  str(12, "fmt "); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  str(36, "data"); v.setUint32(40, pcm.length, true);
  out.set(pcm, 44);
  return out;
}

async function gemini(text: string, voice: string, errors: string[]): Promise<Uint8Array | null> {
  const keys = await googleKeys();
  if (!keys.length) { errors.push("google: kein Schlüssel"); return null; }
  for (const key of keys) {
    for (const model of MODELS) {
      try {
        const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          signal: AbortSignal.timeout(45_000),
          body: JSON.stringify({
            contents: [{ parts: [{ text }] }],
            generationConfig: { responseModalities: ["AUDIO"], speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } } },
          }),
        });
        if (!r.ok) {
          errors.push(`${model}: HTTP ${r.status}`);
          if (r.status === 404 || r.status === 400) continue; // Modell gibt es (so) nicht -> nächstes
          break; // Kontingent/Schlüssel -> nächster Schlüssel
        }
        const part = (await r.json()).candidates?.[0]?.content?.parts?.find((p: any) => p?.inlineData?.data);
        if (!part) { errors.push(`${model}: kein Audio`); continue; }
        const rate = Number(String(part.inlineData.mimeType || "").match(/rate=(\d+)/)?.[1]) || 24000;
        const bin = atob(part.inlineData.data);
        const pcm = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) pcm[i] = bin.charCodeAt(i);
        return wav(pcm, rate);
      } catch (e) { errors.push(`${model}: ${e instanceof Error ? e.message : String(e)}`); }
    }
  }
  return null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);
  try {
    if (!(await allowed(req))) return json({ error: "Bitte anmelden." }, 401);
    const body = await req.json().catch(() => ({}));
    const text = typeof body?.text === "string" ? body.text.trim().slice(0, 1500) : "";
    if (!text) return json({ error: "text required" }, 400);
    const voice = VOICES.has(body?.voice) ? body.voice : "Charon";
    const errors: string[] = [];
    const audio = await gemini(text, voice, errors);
    if (audio) return new Response(audio, { headers: { ...corsHeaders, "Content-Type": "audio/wav", "x-tts-provider": "gemini" } });
    console.warn("[tts]", errors.join(" | "));
    const limited = errors.some((e) => /HTTP 429/.test(e));
    return json({ error: limited ? "Stimmen-Kontingent für heute aufgebraucht" : "Stimme nicht erreichbar", reasons: errors }, limited ? 429 : 502);
  } catch (e) {
    console.error("[tts]", e);
    return json({ error: e instanceof Error ? e.message : "unknown" }, 500);
  }
});
