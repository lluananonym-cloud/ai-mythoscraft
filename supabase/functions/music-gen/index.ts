import { corsHeaders } from 'npm:@supabase/supabase-js@2/cors';

const SYSTEM = `You are a music composer. Given a user prompt, output STRICT JSON describing a short instrumental song. Schema:
{
  "title": string,
  "bpm": number (60-160),
  "key": "C"|"C#"|"D"|"D#"|"E"|"F"|"F#"|"G"|"G#"|"A"|"A#"|"B",
  "scale": "major"|"minor",
  "bars": number (4-16),
  "chords": string[]  // roman numerals like "I","vi","IV","V","ii","iii","VII"; length == bars
  "melody": number[]  // 16 * bars entries, scale degrees 1-8 or 0 for rest
  "bass_pattern": "root"|"walk"|"octave",
  "drums": "none"|"soft"|"beat"|"driving",
  "lead": "sine"|"square"|"saw"|"triangle"|"pluck",
  "pad": boolean
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
    if (!r.ok) {
      const t = await r.text();
      return new Response(JSON.stringify({ error: 'ai_failed', details: t }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    }
    const data = await r.json();
    let content = data.choices?.[0]?.message?.content ?? '{}';
    if (typeof content !== 'string') content = JSON.stringify(content);
    let spec: any;
    try { spec = JSON.parse(content); } catch { spec = null; }
    if (!spec) return new Response(JSON.stringify({ error: 'bad_json' }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    return new Response(JSON.stringify({ spec }), { headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
  } catch (e: any) {
    return new Response(JSON.stringify({ error: e?.message ?? 'unknown' }), { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
  }
});
