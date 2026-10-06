// Chat-Werbung: list (öffentlich, nur freigegebene), get/approve/reject (nur Admins).
// Beim Annehmen schätzt die KI anhand des Budgets, wie häufig die Werbung erscheint (weight 1–10).
import { createClient } from "npm:@supabase/supabase-js@2.103.3";
import { corsHeaders } from "npm:@supabase/supabase-js@2/cors";
import { aiText } from "../_shared/ai.ts";

const json = (d: unknown, status = 200) =>
  new Response(JSON.stringify(d), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const body = await req.json().catch(() => ({}));
    const action = String(body.action || "");
    const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    if (action === "list") {
      const { data } = await db.from("ad_campaigns").select("id,product,link,ad_text,weight").eq("status", "approved");
      return json({ ads: data || [] });
    }

    const token = (req.headers.get("Authorization") || "").replace("Bearer ", "");
    const anon = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
    const { data: cl } = await anon.auth.getClaims(token);
    const uid = cl?.claims?.sub as string | undefined;
    if (!uid) return json({ error: "Bitte als Admin anmelden" }, 401);
    const { data: isAdmin } = await db.rpc("has_role", { _user_id: uid, _role: "admin" });
    if (!isAdmin) return json({ error: "Nur für Admins" }, 403);

    const id = String(body.id || "");
    if (!/^[0-9a-f-]{36}$/i.test(id)) return json({ error: "Ungültige ID" }, 400);
    const { data: ad } = await db.from("ad_campaigns").select("*").eq("id", id).maybeSingle();
    if (!ad) return json({ error: "Werbung nicht gefunden" }, 404);

    if (action === "get") return json({ ad });
    if (action === "reject") { await db.from("ad_campaigns").update({ status: "rejected" }).eq("id", id); return json({ ok: true }); }
    if (action === "pause") { await db.from("ad_campaigns").update({ status: "paused" }).eq("id", id); return json({ ok: true }); }
    if (action === "approve") {
      let weight = 3, note = "Standard-Häufigkeit";
      try {
        const out = await aiText({
          model: "openai/gpt-6-astra", reasoning_effort: "low",
          messages: [
            { role: "system", content: "Du bewertest Werbebudgets für Chat-Werbung (monatliche Zahlung). Antworte NUR als JSON {\"weight\":1-10,\"note\":\"kurze Begründung\"}. 1 = sehr kleines Budget (selten), 10 = sehr großes Budget (sehr häufig). Unklare Angaben = 3." },
            { role: "user", content: `Produkt: ${ad.product}\nBudget/Laufzeit: ${ad.budget}` },
          ],
        });
        const m = out.match(/\{[\s\S]*\}/); const j = m ? JSON.parse(m[0]) : {};
        weight = Math.max(1, Math.min(10, Math.round(Number(j.weight) || 3))); note = String(j.note || note).slice(0, 300);
      } catch { /* Standard verwenden */ }
      await db.from("ad_campaigns").update({ status: "approved", weight, ai_note: note, approved_at: new Date().toISOString() }).eq("id", id);
      return json({ ok: true, weight, note });
    }
    return json({ error: "Unbekannte Aktion" }, 400);
  } catch (e) {
    return json({ error: String((e as Error)?.message || e) }, 500);
  }
});
