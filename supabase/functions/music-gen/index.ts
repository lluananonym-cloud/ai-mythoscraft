import { aiFetch, googleKeys } from "../_shared/ai.ts";
import { corsHeaders } from 'npm:@supabase/supabase-js@2/cors';
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

// Echte Songs mit Gesang, ohne neuen Schlüssel: zuerst Google Lyria 3 (über den vorhandenen Lovable-Zugang,
// dann über die schon gespeicherten Google-AI-Schlüssel). Optional ElevenLabs, falls je ein Schlüssel hinterlegt wird.
// Klappt nichts davon, antwortet die Funktion mit { error: "no_music_key" } und der Browser nutzt den alten Synth-Modus.

const LYRIA_MODELS = ['lyria-3.5-pro-preview', 'lyria-3-pro-preview'];

async function elevenKey(): Promise<string | null> {
  const env = Deno.env.get('ELEVENLABS_API_KEY');
  if (env) return env;
  try {
    const admin = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
    const { data } = await admin.from('app_secrets').select('value').eq('name', 'ELEVENLABS_API_KEY').maybeSingle();
    return data?.value || null;
  } catch { return null; }
}

function songPrompt(prompt: string, seconds: number): string {
  return `${prompt.slice(0, 1500)}\n\nCreate a complete song of about ${seconds} seconds (${Math.round(seconds / 6) / 10} minutes) with realistic, expressive lead vocals singing clear lyrics in the language of this request. Natural song structure: intro, verse, chorus, verse, chorus, outro.`;
}

/** Sucht in einer beliebigen Modell-Antwort nach Audio (data:-URL, inlineData, audio.data). */
function findAudio(node: unknown, depth = 0): { data: string; mime: string } | null {
  if (!node || depth > 8) return null;
  if (typeof node === 'string') {
    const m = node.match(/^data:(audio\/[\w.+-]+);base64,(.+)$/s);
    return m ? { mime: m[1], data: m[2] } : null;
  }
  if (typeof node !== 'object') return null;
  const o = node as Record<string, any>;
  const inline = o.inlineData ?? o.inline_data;
  if (inline?.data && /audio/.test(inline.mimeType ?? inline.mime_type ?? '')) return { data: inline.data, mime: inline.mimeType ?? inline.mime_type };
  if (o.audio?.data && typeof o.audio.data === 'string') return { data: o.audio.data, mime: o.audio.format ? `audio/${o.audio.format === 'mp3' ? 'mpeg' : o.audio.format}` : 'audio/mpeg' };
  for (const v of Array.isArray(o) ? o : Object.values(o)) {
    const f = findAudio(v, depth + 1);
    if (f) return f;
  }
  return null;
}

function findText(node: any): string {
  const parts = node?.candidates?.[0]?.content?.parts;
  if (Array.isArray(parts)) return parts.map((p: any) => p?.text ?? '').join('\n').trim();
  const c = node?.choices?.[0]?.message?.content;
  return typeof c === 'string' ? c.trim() : '';
}

async function lyriaViaLovable(text: string, errors: string[]) {
  const key = Deno.env.get('LOVABLE_API_KEY');
  if (!key) return null;
  for (const model of LYRIA_MODELS) {
    const r = await fetch('https://ai.gateway.lovable.dev/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      body: JSON.stringify({ model: `google/${model}`, messages: [{ role: 'user', content: text }], modalities: ['audio', 'text'] }),
    }).catch((e) => { errors.push(`lovable ${model}: ${e}`); return null; });
    if (!r) continue;
    if (!r.ok) { errors.push(`lovable ${model}: ${r.status} ${(await r.text()).slice(0, 160)}`); continue; }
    const j = await r.json();
    const audio = findAudio(j);
    if (audio) return { ...audio, lyrics: findText(j), source: `lovable/${model}` };
    errors.push(`lovable ${model}: keine Audiodaten`);
  }
  return null;
}

async function lyriaViaGoogle(text: string, errors: string[]) {
  for (const key of await googleKeys()) {
    for (const model of LYRIA_MODELS) {
      const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
        body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text }] }] }),
      }).catch((e) => { errors.push(`google ${model}: ${e}`); return null; });
      if (!r) continue;
      if (!r.ok) { errors.push(`google ${model}: ${r.status} ${(await r.text()).slice(0, 160)}`); continue; }
      const j = await r.json();
      const audio = findAudio(j);
      if (audio) return { ...audio, lyrics: findText(j), source: `google/${model}` };
      errors.push(`google ${model}: keine Audiodaten`);
    }
  }
  return null;
}

async function realSong(req: Request, prompt: string, duration: unknown): Promise<Response> {
  const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });

  // Kostet Guthaben: nur angemeldete Nutzer (verify_jwt ist für diese Funktion aus).
  const auth = req.headers.get('Authorization') ?? '';
  const authClient = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_ANON_KEY')!);
  const { data: claims } = await authClient.auth.getClaims(auth.replace('Bearer ', ''));
  if (!claims?.claims?.sub) return json({ error: 'Bitte anmelden, um Songs zu erzeugen.' }, 401);

  const seconds = Math.min(180, Math.max(60, Math.round(Number(duration) || 120)));
  const text = songPrompt(prompt, seconds);
  const errors: string[] = [];

  // ElevenLabs streamt (spielt sofort), daher zuerst, falls ein Schlüssel existiert.
  const eleven = await elevenKey();
  if (eleven) {
    const r = await fetch('https://api.elevenlabs.io/v1/music/stream?output_format=mp3_44100_128', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'xi-api-key': eleven },
      body: JSON.stringify({ prompt: text, music_length_ms: seconds * 1000, model_id: 'music_v2_5' }),
    }).catch(() => null);
    if (r?.ok && r.body) {
      return new Response(r.body, {
        headers: { ...corsHeaders, 'Content-Type': 'audio/mpeg', 'X-Song-Seconds': String(seconds), 'Access-Control-Expose-Headers': 'X-Song-Seconds', 'Cache-Control': 'no-store' },
      });
    }
    errors.push(`elevenlabs: ${r?.status ?? 'netzwerk'}`);
  }

  const song = (await lyriaViaLovable(text, errors)) ?? (await lyriaViaGoogle(text, errors));
  if (song) return json({ audio: song.data, mime: song.mime, lyrics: song.lyrics, seconds, source: song.source });

  console.warn('[music-gen] kein Musikmodell erreichbar', errors);
  return json({ error: 'no_music_key', details: errors.slice(0, 6) });
}

const SYSTEM = `You are a songwriter + composer. Given a user prompt, output STRICT JSON for a short song WITH VOCALS. Schema:
{
  "title": string,
  "bpm": number (70-140),
  "key": "C"|"C#"|"D"|"D#"|"E"|"F"|"F#"|"G"|"G#"|"A"|"A#"|"B",
  "scale": "major"|"minor",
  "bars": number (8-16),
  "chords": string[]  // roman numerals ("I","vi","IV","V","ii","iii","VII"); length == bars
  "melody": number[]  // 16 * bars entries, scale degrees 1-8, 0 = rest
  "bass_pattern": "root"|"walk"|"octave",
  "drums": "none"|"soft"|"beat"|"driving",
  "lead": "sine"|"square"|"saw"|"triangle"|"pluck",
  "pad": boolean,
  "lyrics": string,          // 2-4 short lines that fit the mood, singable, matches language of prompt
  "vocal_style": string,     // short instruction for how to sing, e.g. "warm melodic pop female, gentle vibrato, on beat"
  "voice": "alloy"|"ash"|"ballad"|"coral"|"echo"|"sage"|"shimmer"|"verse"
}
Return ONLY JSON. No prose.`;

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const { prompt, duration, mode } = await req.json();
    if (!prompt || typeof prompt !== 'string') {
      return new Response(JSON.stringify({ error: 'prompt required' }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    }
    if (mode === 'stream') {
      return await realSong(req, prompt, duration);
    }
    const key = Deno.env.get('LOVABLE_API_KEY');
    if (!key) return new Response(JSON.stringify({ error: 'LOVABLE_API_KEY missing' }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });

    const r = await aiFetch('gateway', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${key}` },
      body: JSON.stringify({
        model: 'google/gemini-2.5-flash',
        messages: [
          { role: 'system', content: SYSTEM },
          { role: 'user', content: prompt },
        ],
        response_format: { type: 'json_object' },
      }),
    });
    if (r.status === 429) return new Response(JSON.stringify({ error: 'rate_limited' }), { status: 429, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    if (r.status === 402) return new Response(JSON.stringify({ error: 'credits' }), { status: 402, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    if (!r.ok) return new Response(JSON.stringify({ error: 'ai_failed', details: await r.text() }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });

    const data = await r.json();
    let content = data.choices?.[0]?.message?.content ?? '{}';
    if (typeof content !== 'string') content = JSON.stringify(content);
    let spec: any;
    try { spec = JSON.parse(content); } catch { spec = null; }
    if (!spec) return new Response(JSON.stringify({ error: 'bad_json' }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });

    // --- Generate vocals via Lovable AI TTS ---
    let vocalB64: string | null = null;
    let vocalMime = 'audio/mpeg';
    try {
      if (spec.lyrics && typeof spec.lyrics === 'string') {
        const voice = spec.voice && typeof spec.voice === 'string' ? spec.voice : 'shimmer';
        const style = spec.vocal_style || 'sing melodically, warm expressive vocal, on beat';
        const tts = await fetch('https://ai.gateway.lovable.dev/v1/audio/speech', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${key}` },
          body: JSON.stringify({
            model: 'openai/gpt-4o-mini-tts',
            input: spec.lyrics,
            voice,
            instructions: `Sing these lyrics like a song at roughly ${spec.bpm ?? 100} BPM in ${spec.key ?? 'C'} ${spec.scale ?? 'major'}. Style: ${style}. Use pitch, rhythm and melodic phrasing — do NOT read flatly. Feel free to hold notes.`,
            response_format: 'mp3',
          }),
        });
        if (tts.ok) {
          const buf = new Uint8Array(await tts.arrayBuffer());
          // base64 encode
          let bin = '';
          for (let i = 0; i < buf.length; i++) bin += String.fromCharCode(buf[i]);
          vocalB64 = btoa(bin);
        }
      }
    } catch (_) { /* vocals optional */ }

    return new Response(JSON.stringify({ spec, vocal: vocalB64, vocal_mime: vocalMime }), { headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
  } catch (e: any) {
    return new Response(JSON.stringify({ error: e?.message ?? 'unknown' }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
  }
});
