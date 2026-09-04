import { aiFetch } from "../_shared/ai.ts";
// Gehirn der Mythos-Browser-Erweiterung: bekommt den Seiten-Kontext und gibt
// konkrete Aktionen zurück, die die Extension im echten Browser ausführt.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const SYSTEM = `Du bist **Mythos Browser Control** und steuerst den echten Browser des Nutzers über eine Erweiterung.

Du bekommst pro Runde den Kontext der aktiven Seite (URL, Titel, sichtbarer Text, klickbare Elemente mit Index).
Antworte AUSSCHLIESSLICH mit JSON (kein Markdown, keine Erklärung außerhalb):

{
  "say": "kurzer Satz auf Deutsch, was du gerade machst",
  "actions": [
    { "type": "open", "url": "https://..." },
    { "type": "click", "index": 3 },
    { "type": "click", "text": "Anmelden" },
    { "type": "type", "index": 5, "text": "hallo", "enter": true },
    { "type": "scroll", "amount": 800 },
    { "type": "wait", "ms": 1200 },
    { "type": "read" }
  ],
  "done": false
}

Regeln:
- Maximal 3 Aktionen pro Runde, danach bekommst du den neuen Seiten-Kontext.
- Nutze "index" aus der Elementliste, wenn möglich; sonst "text".
- Bei Logins, Passwörtern, Zahlungen oder Captchas: setze "done": true und bitte den Nutzer in "say", das selbst zu erledigen.
- Wenn die Aufgabe erfüllt ist: "done": true und in "say" das Ergebnis (darf länger sein).
- Erfinde keine Inhalte — lies sie von der Seite.`;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { task, history, page, memory } = await req.json();
    const key = Deno.env.get("LOVABLE_API_KEY")!;

    const ctx = page
      ? `Aktive Seite:\nURL: ${page.url}\nTitel: ${page.title}\n\nElemente:\n${(page.elements || [])
          .slice(0, 60)
          .map((e: any) => `[${e.index}] ${e.tag}${e.type ? `/${e.type}` : ""}: ${String(e.label || "").slice(0, 80)}`)
          .join("\n")}\n\nSeitentext:\n${String(page.text || "").slice(0, 6000)}`
      : "Es ist noch keine Seite geladen.";

    const mem = Array.isArray(memory) && memory.length
      ? `Erinnerung an frühere Aufgaben des Nutzers:\n${memory
          .slice(-10)
          .map((m: any) => `- ${String(m.task || "").slice(0, 120)} → ${String(m.result || "").slice(0, 160)}`)
          .join("\n")}`
      : "";

    const messages = [
      { role: "system", content: SYSTEM },
      ...(mem ? [{ role: "system", content: mem }] : []),
      { role: "user", content: `Aufgabe: ${String(task ?? "").slice(0, 1500)}` },
      ...(Array.isArray(history) ? history.slice(-8) : []),
      { role: "user", content: ctx },
    ];


    let r: Response | null = null;
    for (let i = 0; i < 3; i++) {
      r = await aiFetch("gateway", {
        method: "POST",
        headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "google/gemini-2.5-flash",
          messages,
          response_format: { type: "json_object" },
        }),
      });
      if (r.status !== 429) break;
      await new Promise((res) => setTimeout(res, 800 * (i + 1)));
    }

    if (!r || !r.ok) {
      const status = r?.status ?? 500;
      const msg = status === 429 ? "Zu viele Anfragen — kurz warten." : status === 402 ? "AI-Credits aufgebraucht." : `Gateway-Fehler (${status}).`;
      return new Response(JSON.stringify({ say: `⚠️ ${msg}`, actions: [], done: true }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const j = await r.json();
    const raw = j.choices?.[0]?.message?.content || "{}";
    let out: any;
    try {
      out = JSON.parse(raw.replace(/```json|```/g, "").trim());
    } catch {
      out = { say: String(raw).slice(0, 1500), actions: [], done: true };
    }
    if (!Array.isArray(out.actions)) out.actions = [];
    out.actions = out.actions.slice(0, 3);

    return new Response(JSON.stringify(out), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(
      JSON.stringify({ say: `⚠️ Fehler: ${e instanceof Error ? e.message : "unbekannt"}`, actions: [], done: true }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
