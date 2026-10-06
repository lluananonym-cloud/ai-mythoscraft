// Einfache Bildgenerierung: für das Startbild im VideoPlayer und /bild in der Code-App.
// Nutzt dieselbe Kette wie /image im Chat (Lovable/Google -> NVIDIA -> Pollinations).
import { generateImage } from "../_shared/image.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
  try {
    const { prompt } = await req.json();
    if (!prompt || typeof prompt !== "string") return json({ error: "prompt required" }, 400);
    const { url, error } = await generateImage(prompt.slice(0, 1500), Deno.env.get("LOVABLE_API_KEY"));
    if (url) return json({ url });
    console.error("[image-gen]", error);
    return json({ error: "Bild konnte nicht erstellt werden" }, 502);
  } catch (e) {
    console.error(e);
    return json({ error: e instanceof Error ? e.message : "unknown" }, 500);
  }
});
