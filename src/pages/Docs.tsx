import TopNav from "@/components/TopNav";

const Block = ({ lang = "bash", code }: { lang?: string; code: string }) => (
  <pre className="glass rounded-xl p-4 overflow-x-auto text-xs my-3"><code className={`language-${lang} text-foreground`}>{code}</code></pre>
);

const Docs = () => {
  const base = `${typeof window !== "undefined" ? window.location.origin : ""}`;
  const apiUrl = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/v1-messages`;
  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-12 max-w-3xl">
        <h1 className="font-display text-4xl font-bold mb-3">Mythos AI API Docs</h1>
        <p className="text-muted-foreground mb-8">Mythos AI bietet einen Claude-kompatiblen Endpoint. Du kannst ihn direkt in MythosBrowse oder jeder Anthropic-SDK-kompatiblen Software verwenden.</p>

        <section className="glass-strong rounded-2xl p-6 mb-6">
          <h2 className="font-display text-xl font-semibold mb-3">1. API-Key erstellen</h2>
          <p className="text-sm text-muted-foreground mb-2">Gehe ins <a href="/dashboard" className="text-accent hover:underline">Dashboard</a> und erstelle einen Key. Format:</p>
          <code className="font-mono text-xs text-accent">sk-ant-mythos-XXXXXXXXXXXXXXXXX</code>
        </section>

        <section className="glass-strong rounded-2xl p-6 mb-6">
          <h2 className="font-display text-xl font-semibold mb-3">2. Endpoint</h2>
          <Block code={`POST ${apiUrl}`} />
          <p className="text-sm text-muted-foreground mt-2">Headers:</p>
          <Block code={`x-api-key: sk-ant-mythos-...
anthropic-version: 2023-06-01
content-type: application/json`} />
        </section>

        <section className="glass-strong rounded-2xl p-6 mb-6">
          <h2 className="font-display text-xl font-semibold mb-3">3. Beispiel cURL</h2>
          <Block code={`curl ${apiUrl} \\
  -H "x-api-key: sk-ant-mythos-..." \\
  -H "anthropic-version: 2023-06-01" \\
  -H "content-type: application/json" \\
  -d '{
    "model": "claude-3-5-sonnet-20241022",
    "max_tokens": 1024,
    "messages": [{"role": "user", "content": "Hallo Mythos!"}]
  }'`} />
        </section>

        <section className="glass-strong rounded-2xl p-6 mb-6">
          <h2 className="font-display text-xl font-semibold mb-3">4. Anthropic SDK (JS/TS)</h2>
          <Block lang="ts" code={`import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic({
  apiKey: "sk-ant-mythos-...",
  baseURL: "${import.meta.env.VITE_SUPABASE_URL}/functions/v1",
});

const msg = await client.messages.create({
  model: "claude-3-5-sonnet-20241022",
  max_tokens: 1024,
  messages: [{ role: "user", content: "Hallo!" }],
});
console.log(msg);`} />
        </section>

        <section className="glass-strong rounded-2xl p-6 mb-6">
          <h2 className="font-display text-xl font-semibold mb-3">5. Streaming</h2>
          <p className="text-sm text-muted-foreground mb-2">Setze <code className="bg-secondary px-1.5 py-0.5 rounded text-accent text-[0.9em]">"stream": true</code> im Body. Antworten kommen als Server-Sent Events im Anthropic-Format (<code className="bg-secondary px-1.5 py-0.5 rounded text-accent text-[0.9em]">message_start</code>, <code className="bg-secondary px-1.5 py-0.5 rounded text-accent text-[0.9em]">content_block_delta</code>, <code className="bg-secondary px-1.5 py-0.5 rounded text-accent text-[0.9em]">message_stop</code>).</p>
        </section>

        <section className="glass-strong rounded-2xl p-6 mb-6">
          <h2 className="font-display text-xl font-semibold mb-3">6. Limits</h2>
          <ul className="text-sm text-muted-foreground space-y-1 list-disc pl-5">
            <li>Standard: <strong className="text-foreground">100 Requests / Tag</strong> pro Key (kostenlos)</li>
            <li>Höhere Limits: Im <a href="https://discord.gg" className="text-accent">Discord</a> anfragen</li>
            <li>Bei <code className="bg-secondary px-1.5 py-0.5 rounded text-accent text-[0.9em]">429</code>: Tageslimit erreicht oder zu viele parallele Requests</li>
          </ul>
        </section>

        <section className="glass-strong rounded-2xl p-6 mb-6">
          <h2 className="font-display text-xl font-semibold mb-3">7. MythosBrowse</h2>
          <p className="text-sm text-muted-foreground">In MythosBrowse einfach den Mythos-Key statt eines Anthropic-Keys eintragen — das Format ist identisch und alle Endpoints sind kompatibel.</p>
        </section>

        <section id="minecraft-plugin" className="glass-strong rounded-2xl p-6 mt-12 border border-accent/20">
          <h2 className="font-display text-2xl font-bold mb-2">🎮 Minecraft-Plugin Integration</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Verbinde deinen Bukkit/Spigot/Paper-Server mit Mythos AI. Die AI begrüßt Spieler, kommentiert Tode und antwortet im Ingame-Chat auf <code className="text-accent">!ai</code>.
          </p>

          <h3 className="font-display text-lg font-semibold mt-5 mb-2">1. Key generieren</h3>
          <p className="text-sm text-muted-foreground mb-2">
            Gehe zu <a href="/mc-servers" className="text-accent hover:underline">/mc-servers</a> und erstelle einen neuen Server. Du bekommst einen Key:
          </p>
          <code className="font-mono text-xs text-accent">sk-mc-XXXXXXXXXXXXXXXX</code>

          <h3 className="font-display text-lg font-semibold mt-5 mb-2">2. Plugin-Endpoint</h3>
          <Block code={`POST ${import.meta.env.VITE_SUPABASE_URL}/functions/v1/mc-bridge
Authorization: Bearer sk-mc-XXXXXXXX
Content-Type: application/json

{ "action": "chat", "player": "Steve", "message": "!ai wie craft ich Netherite?" }`} />

          <h3 className="font-display text-lg font-semibold mt-5 mb-2">3. Java Beispiel (Paper/Spigot)</h3>
          <Block lang="java" code={`// In deinem onEnable():
String API_KEY = getConfig().getString("mythos-key"); // sk-mc-...
String API_URL = "${import.meta.env.VITE_SUPABASE_URL}/functions/v1/mc-bridge";

// Chat-Listener
@EventHandler
public void onChat(AsyncPlayerChatEvent e) {
    String msg = e.getMessage();
    if (!msg.toLowerCase().startsWith("!ai")) return;
    String player = e.getPlayer().getName();

    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(API_URL).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            String body = "{\\"action\\":\\"chat\\",\\"player\\":\\"" + player + "\\",\\"message\\":\\"" + msg.replace("\\"","\\\\\\"") + "\\"}";
            con.getOutputStream().write(body.getBytes());
            String resp = new String(con.getInputStream().readAllBytes());
            String reply = resp.replaceAll(".*\\"reply\\":\\"([^\\"]+)\\".*", "$1");
            Bukkit.getScheduler().runTask(this, () ->
                Bukkit.broadcastMessage("§b[AI] §r" + reply));
        } catch (Exception ex) { ex.printStackTrace(); }
    });
}

// Join-Event
@EventHandler
public void onJoin(PlayerJoinEvent e) {
    sendEvent("join", e.getPlayer().getName(), "");
}

// Death-Event
@EventHandler
public void onDeath(PlayerDeathEvent e) {
    sendEvent("death", e.getEntity().getName(), e.getDeathMessage());
}

private void sendEvent(String type, String player, String content) {
    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(API_URL).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            String body = "{\\"action\\":\\"event\\",\\"type\\":\\"" + type + "\\",\\"player\\":\\"" + player + "\\",\\"content\\":\\"" + content + "\\"}";
            con.getOutputStream().write(body.getBytes());
            String resp = new String(con.getInputStream().readAllBytes());
            if (resp.contains("\\"reply\\":\\"")) {
                String reply = resp.replaceAll(".*\\"reply\\":\\"([^\\"]+)\\".*", "$1");
                Bukkit.getScheduler().runTask(this, () ->
                    Bukkit.broadcastMessage("§b[AI] §r" + reply));
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    });
}`} />

          <h3 className="font-display text-lg font-semibold mt-5 mb-2">4. config.yml</h3>
          <Block lang="yaml" code={`mythos-key: "sk-mc-XXXXXXXXXXXXXXXX"`} />

          <p className="text-sm text-muted-foreground mt-4">
            Steuere alles (Trigger, On/Off-Schalter, Persona) im <a href="/mc-servers" className="text-accent hover:underline">Server-Dashboard</a>.
          </p>
        </section>
      </main>
    </div>
  );
};
export default Docs;
