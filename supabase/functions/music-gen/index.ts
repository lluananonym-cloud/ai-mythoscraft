import { aiFetch } from "../_shared/ai.ts";
import { corsHeaders } from 'npm:@supabase/supabase-js@2/cors';
import { createClient } from "npm:@supabase/supabase-js@2.103.3";

// Echte Songs mit Gesang über ElevenLabs Music (Streaming: die ersten Sekunden sind nach kurzer Zeit hörbar).
// Schlüssel: Secret ELEVENLABS_API_KEY oder per /musikkeyadmin gespeichert (Tabelle app_secrets).
// Ohne Schlüssel antwortet die Funktion mit { error: "no_music_key" } und der Browser nutzt den alten Synth-Modus.
async function elevenKey(): Promise<string | null> {
  const env = Deno.env.get('ELEVENLABS_API_KEY');
  if (env) return env;
  try {
    const admin = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
    const { data } = await admin.from('app_secrets').select('value').eq('name', 'ELEVENLABS_API_KEY').maybeSingle();
    return data?.value || null;
  } catch { return null; }
}

const ELEVEN_ERRORS: Record<number, string> = {
  401: 'ElevenLabs-Schlüssel ist ungültig.',
  402: 'ElevenLabs-Guthaben aufgebraucht oder Plan ohne Musik.',
  429: 'Zu viele Song-Anfragen gleichzeitig, kurz warten.',
};

async function streamSong(req: Request, prompt: string, duration: unknown): Promise<Response | null> {
  const key = await elevenKey();
  if (!key) return null;
  const json = (body: unknown, status: number) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });

  // Kostet Guthaben: nur angemeldete Nutzer (verify_jwt ist für diese Funktion aus).
  const auth = req.headers.get('Authorization') ?? '';
  const authClient = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_ANON_KEY')!);
  const { data: claims } = await authClient.auth.getClaims(auth.replace('Bearer ', ''));
  if (!claims?.claims?.sub) return json({ error: 'Bitte anmelden, um Songs zu erzeugen.' }, 401);

  const seconds = Math.min(180, Math.max(60, Math.round(Number(duration) || 120)));
  const r = await fetch('https://api.elevenlabs.io/v1/music/stream?output_format=mp3_44100_128', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'xi-api-key': key },
    body: JSON.stringify({
      prompt: `${prompt.slice(0, 1500)}\n\nFull song with realistic, expressive lead vocals singing clear lyrics in the language of this request. Natural song structure (intro, verse, chorus, verse, chorus, outro).`,
      music_length_ms: seconds * 1000,
      model_id: 'music_v2_5',
    }),
  });
  if (!r.ok || !r.body) {
    let detail = '';
    try { const t = await r.text(); const j = JSON.parse(t); detail = j?.detail?.message || j?.detail?.status || (typeof j?.detail === 'string' ? j.detail : t); } catch { /* ignore */ }
    return json({ error: ELEVEN_ERRORS[r.status] || `Song fehlgeschlagen (${r.status}) ${String(detail).slice(0, 300)}` }, r.status === 401 ? 500 : r.status);
  }
  return new Response(r.body, {
    headers: { ...corsHeaders, 'Content-Type': 'audio/mpeg', 'X-Song-Seconds': String(seconds), 'Access-Control-Expose-Headers': 'X-Song-Seconds', 'Cache-Control': 'no-store' },
  });
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
      const streamed = await streamSong(req, prompt, duration);
      return streamed ?? new Response(JSON.stringify({ error: 'no_music_key' }), { headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
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
