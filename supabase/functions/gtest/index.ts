const models = ["gemini-3.1-pro-preview","gemini-3.6-flash","gemini-3.1-flash-lite","gemini-3.7-flash"];
Deno.serve(async () => {
  const keys = [Deno.env.get("GOOGLE_AI_API_KEY"), Deno.env.get("GOOGLE_AI_API_KEY_BACKUP")].filter(Boolean) as string[];
  const out: any[] = [];
  for (let i=0;i<keys.length;i++) for (const m of models) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${m}:generateContent`, {
      method:"POST", headers:{"Content-Type":"application/json","x-goog-api-key":keys[i]},
      body: JSON.stringify({contents:[{role:"user",parts:[{text:"hi"}]}]}),
    });
    out.push({key:i, m, status:r.status, body:(await r.text()).slice(0,160)});
  }
  return new Response(JSON.stringify(out,null,1),{headers:{"Content-Type":"application/json"}});
});
