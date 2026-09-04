import { aiFetch } from "../_shared/ai.ts";
// Twin training - analyzes user's recent messages to build a style profile
import { corsHeaders } from 'npm:@supabase/supabase-js@2/cors';
import { createClient } from 'npm:@supabase/supabase-js@2';

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });

  try {
    const auth = req.headers.get('Authorization');
    if (!auth) return json({ error: 'unauthorized' }, 401);

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_ANON_KEY')!,
      { global: { headers: { Authorization: auth } } }
    );
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) return json({ error: 'unauthorized' }, 401);

    // Check Pro
    const { data: sub } = await supabase.from('subscriptions').select('tier,expires_at').eq('user_id', user.id).maybeSingle();
    const { data: roles } = await supabase.from('user_roles').select('role').eq('user_id', user.id);
    const isAdmin = roles?.some((r: any) => r.role === 'admin');
    const isPro = isAdmin || (sub?.tier === 'pro' && (!sub.expires_at || new Date(sub.expires_at) > new Date()));
    if (!isPro) return json({ error: 'pro_required' }, 403);

    const body = await req.json().catch(() => ({}));
    const extraSamples: string[] = Array.isArray(body.samples) ? body.samples.slice(0, 50) : [];

    // Pull last 200 user messages
    const { data: msgs } = await supabase.from('messages')
      .select('content,conversations!inner(user_id)')
      .eq('role', 'user').eq('conversations.user_id', user.id)
      .order('created_at', { ascending: false }).limit(200);

    const samples = [
      ...extraSamples,
      ...(msgs || []).map((m: any) => String(m.content || '')).filter(t => t.length > 8 && t.length < 600),
    ].slice(0, 100);

    if (samples.length < 3) return json({ error: 'not_enough_data', message: 'Schreibe ein paar Nachrichten oder füge Beispiele manuell hinzu.' }, 400);

    const LOVABLE_API_KEY = Deno.env.get('LOVABLE_API_KEY');
    if (!LOVABLE_API_KEY) return json({ error: 'config_missing' }, 500);

    const prompt = `Analysiere folgende Nachrichten EINER Person und erstelle ein Style-Profil auf Deutsch.

Nachrichten:
${samples.map((s, i) => `${i + 1}. ${s}`).join('\n')}

Antworte AUSSCHLIESSLICH als JSON:
{
  "style_summary": "<2-4 Sätze: wie tickt diese Person, typische Themen, Energie>",
  "tone": "<ein Wort z.B. lässig, sarkastisch, freundlich, direkt, nerdy>",
  "vocabulary": ["<8-15 typische Wörter/Phrasen/Slang dieser Person>"]
}`;

    const aiRes = await aiFetch('gateway', {
      method: 'POST',
      headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: 'google/gemini-2.5-flash',
        messages: [{ role: 'user', content: prompt }],
        response_format: { type: 'json_object' },
      }),
    });

    if (!aiRes.ok) {
      const t = await aiRes.text();
      if (aiRes.status === 429) return json({ error: 'rate_limit' }, 429);
      if (aiRes.status === 402) return json({ error: 'credits' }, 402);
      return json({ error: 'ai_failed', detail: t }, 500);
    }
    const data = await aiRes.json();
    let parsed: any = {};
    try { parsed = JSON.parse(data.choices[0].message.content); } catch { return json({ error: 'parse_failed' }, 500); }

    const update = {
      style_summary: String(parsed.style_summary || '').slice(0, 1500),
      tone: String(parsed.tone || '').slice(0, 50),
      vocabulary: Array.isArray(parsed.vocabulary) ? parsed.vocabulary.slice(0, 20).map((s: any) => String(s).slice(0, 80)) : [],
      training_samples: samples.slice(0, 30),
      is_trained: true,
      last_trained_at: new Date().toISOString(),
    };

    await supabase.from('ai_twins').upsert({ user_id: user.id, ...update }, { onConflict: 'user_id' });
    return json({ ok: true, ...update });
  } catch (e: any) {
    return json({ error: 'server', detail: e?.message }, 500);
  }
});

function json(obj: any, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
}
