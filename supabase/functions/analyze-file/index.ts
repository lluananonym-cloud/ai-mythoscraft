// Analyze an uploaded file (image or PDF) using a vision model.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { url, prompt = "Beschreibe was du hier siehst, sei präzise und hilfreich.", mime } = await req.json();
    if (!url) return new Response(JSON.stringify({ error: "url required" }), { status: 400, headers: corsHeaders });

    const LOVABLE_API_KEY = Deno.env.get("LOVABLE_API_KEY")!;
    const isImage = (mime || "").startsWith("image/") || /\.(png|jpe?g|webp|gif)$/i.test(url);

    if (!isImage) {
      // For PDFs / other: fetch + send as document if supported (Gemini accepts inline PDF as base64)
      const fileRes = await fetch(url);
      const buf = new Uint8Array(await fileRes.arrayBuffer());
      const b64 = btoa(String.fromCharCode(...buf));
      const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
        method: "POST",
        headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "google/gemini-2.5-flash",
          messages: [{
            role: "user",
            content: [
              { type: "text", text: prompt },
              { type: "file", file: { file_data: `data:${mime || "application/pdf"};base64,${b64}`, filename: "doc" } },
            ],
          }],
        }),
      });
      const j = await r.json();
      return new Response(JSON.stringify({ analysis: j.choices?.[0]?.message?.content || "(keine Antwort)" }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const r = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${LOVABLE_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash",
        messages: [{
          role: "user",
          content: [
            { type: "text", text: prompt },
            { type: "image_url", image_url: { url } },
          ],
        }],
      }),
    });
    const j = await r.json();
    return new Response(JSON.stringify({ analysis: j.choices?.[0]?.message?.content || "(keine Antwort)" }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 500, headers: corsHeaders });
  }
});
