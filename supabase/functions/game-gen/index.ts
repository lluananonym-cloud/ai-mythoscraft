import { corsHeaders } from "npm:@supabase/supabase-js@2/cors";

const SYSTEM = `Du bist ein Senior Game Developer für 3D-Browser-Casual-Games (Poki/Crazy-Games-Stil).
Erzeuge EIN vollständiges, sofort spielbares HTML-Dokument. KEINE Erklärung, NUR rohes HTML, beginnend mit <!DOCTYPE html>.

PFLICHT:
- Komplett self-contained: NUR diese externen Scripts erlaubt:
    https://unpkg.com/three@0.160.0/build/three.min.js
    https://unpkg.com/three@0.160.0/examples/jsm/controls/OrbitControls.js (als type=module wenn nötig)
- Lieber alles in einem <script> mit dem klassischen THREE Global (kein ES-Module-Import) — robuster.
- Vollbild Canvas (body { margin:0; overflow:hidden; background:#0b0b14; }).
- Responsive resize-Handler.
- Eingaben: Tastatur (WASD/Pfeile/Space) UND Touch (mind. ein großer Tap-Bereich oder On-Screen-Buttons für Mobile).
- Sichtbarer HUD-Overlay (Score, Lives/Time, Restart-Button), gut lesbar auf dunkel.
- Game-Loop mit requestAnimationFrame, Win-/Lose-Condition, "Restart"-Button im DOM.
- Bunte einfache Geometrie (BoxGeometry/SphereGeometry/CylinderGeometry), MeshStandardMaterial, Ambient+Directional Light, Schatten an.
- Schickes Easing/Tweening per Lerp. Kein blockierender Code, keine Audio-Autoplay.
- KEINE TODOs, KEINE Platzhalter, KEINE Comments wie "// add more here". Das Spiel MUSS fertig sein.
- Maximal ~800 Zeilen, fokussiert und fertig.

Mache es FUN: schöner Sky-Color, Partikel/Glow erlaubt, ein klares Spielziel.`;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { prompt, title, genre } = await req.json();
    if (!prompt || typeof prompt !== "string" || prompt.length > 1000) {
      return new Response(JSON.stringify({ error: "Bad prompt" }), { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }
    const apiKey = Deno.env.get("LOVABLE_API_KEY");
    if (!apiKey) return new Response(JSON.stringify({ error: "Missing LOVABLE_API_KEY" }), { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } });

    const userMsg = `Spiel: ${title || "Untitled"}
Genre: ${genre || "casual 3D"}
Beschreibung: ${prompt}

Liefere jetzt das vollständige HTML.`;

    const res = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-2.5-pro",
        messages: [
          { role: "system", content: SYSTEM },
          { role: "user", content: userMsg },
        ],
      }),
    });
    if (!res.ok) {
      const t = await res.text();
      return new Response(JSON.stringify({ error: "AI gateway: " + t.slice(0, 300) }), { status: res.status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }
    const data = await res.json();
    let html: string = data?.choices?.[0]?.message?.content ?? "";
    // strip code fences if present
    html = html.replace(/^```html\s*/i, "").replace(/^```\s*/i, "").replace(/```\s*$/i, "").trim();
    const idx = html.indexOf("<!DOCTYPE");
    if (idx > 0) html = html.slice(idx);
    if (!html.toLowerCase().startsWith("<!doctype") && !html.toLowerCase().startsWith("<html")) {
      return new Response(JSON.stringify({ error: "AI returned no HTML" }), { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify({ html }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e?.message ?? e) }), { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  }
});
