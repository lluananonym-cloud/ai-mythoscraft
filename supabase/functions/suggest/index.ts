// Generate 3 short follow-up suggestions based on chat history
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { messages } = await req.json();
    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;
    const recent = (messages || []).slice(-6).map((m: any) =>
      `${m.role === "user" ? "USER" : "AI"}: ${typeof m.content === "string" ? m.content : "[multimodal]"}`
    ).join("\n").slice(0, 2500);

    const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash-lite",
        messages: [
          { role: "system", content: "Du generierst genau 3 sehr kurze (max 6 Wörter) Folge-Fragen / Follow-up-Aktionen, die der User als nächstes klicken könnte. Sprache des Users beibehalten. Keine Nummerierung." },
          { role: "user", content: `Chat:\n${recent}\n\nGib genau 3 Vorschläge zurück.` },
        ],
        tools: [{
          type: "function",
          function: {
            name: "suggestions",
            description: "Return 3 follow-up suggestions",
            parameters: {
              type: "object",
              properties: {
                items: { type: "array", items: { type: "string" }, minItems: 3, maxItems: 3 },
              },
              required: ["items"],
              additionalProperties: false,
            },
          },
        }],
        tool_choice: { type: "function", function: { name: "suggestions" } },
      }),
    });
    if (!r.ok) return new Response(JSON.stringify({ items: [] }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
    const j = await r.json();
    const args = j.choices?.[0]?.message?.tool_calls?.[0]?.function?.arguments;
    let items: string[] = [];
    try { items = JSON.parse(args || "{}").items || []; } catch {}
    return new Response(JSON.stringify({ items: items.slice(0, 3) }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  } catch (e) {
    console.error("suggest error", e);
    return new Response(JSON.stringify({ items: [] }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  }
});
