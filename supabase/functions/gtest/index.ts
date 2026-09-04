import { aiChat } from "../_shared/ai.ts";
Deno.serve(async () => {
  const r = await aiChat({ model: "google/gemini-2.5-flash", messages: [{ role: "user", content: "Sag nur: fallback ok" }], stream: false }, "invalid-key-force-fallback");
  return new Response(await r.text(), { status: r.status, headers: { "Content-Type": "application/json" } });
});
