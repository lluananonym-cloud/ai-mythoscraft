// Simple, free image-generation endpoint backed by Lovable AI.
// Used by client-side VideoPlayer to get a base frame to animate.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

async function genOnce(prompt: string, model: string, apiKey: string): Promise<string | null> {
  const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model,
      messages: [{ role: "user", content: `Generate a high quality cinematic image: ${prompt}` }],
      modalities: ["image", "text"],
    }),
  });
  if (!r.ok) {
    console.error("image-gen gateway error", model, r.status, (await r.text()).slice(0, 200));
    return null;
  }
  const j = await r.json();
  return j.choices?.[0]?.message?.images?.[0]?.image_url?.url ?? null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
  try {
    const { prompt } = await req.json();
    if (!prompt || typeof prompt !== "string") {
      return new Response(JSON.stringify({ error: "prompt required" }), {
        status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const apiKey = Deno.env.get("LOVABLE_API_KEY");
    if (!apiKey) throw new Error("LOVABLE_API_KEY not configured");

    for (const m of ["google/gemini-2.5-flash-image", "google/gemini-3.1-flash-image-preview"]) {
      const url = await genOnce(prompt, m, apiKey);
      if (url) return new Response(JSON.stringify({ url }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    return new Response(JSON.stringify({ error: "Bild konnte nicht erstellt werden" }), {
      status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error(e);
    return new Response(JSON.stringify({ error: e instanceof Error ? e.message : "unknown" }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
