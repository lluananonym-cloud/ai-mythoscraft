import { aiFetch } from "../_shared/ai.ts";
import { corsHeaders } from 'npm:@supabase/supabase-js@2/cors';

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
    const { prompt } = await req.json();
    if (!prompt || typeof prompt !== 'string') {
      return new Response(JSON.stringify({ error: 'prompt required' }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    }
    const key = Deno.env.get('LOVABLE_API_KEY');
    if (!key) return new Response(JSON.stringify({ error: 'LOVABLE_API_KEY missing' }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });

    const r = await fetch('https://ai.gateway.lovable.dev/v1/chat/completions', {
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
