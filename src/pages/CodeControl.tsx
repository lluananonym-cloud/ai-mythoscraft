// Mythos vom Handy schreiben: Messenger für die Mythos-Code-App auf dem PC. Gekoppelt wird über den Link,
// den der PC bei /koppeln schickt (…/code#k=…). Keine Anmeldung und kein eigener Server: die Nachrichten
// laufen Ende-zu-Ende verschlüsselt über ntfy.sh (siehe src/lib/mythosRelay.ts).
import { useEffect, useRef, useState } from "react";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Loader2, MonitorSmartphone, Send, Unlink } from "lucide-react";
import { toast } from "sonner";
import { RELAY, open, relayKeys, send as relaySend, type RelayKeys } from "@/lib/mythosRelay";

const LS_KEY = "mythos.relayKey";
const LS_TASKS = "mythos.relayTasks"; // Verlauf wie in einem Messenger
const LS_SINCE = "mythos.relaySince";
type Task = { id: string; prompt: string; status: "pending" | "running" | "done" | "error"; result?: string; parts?: string[] };
type Msg = { t: string; id?: string; status?: string; part?: number; of?: number; text?: string };

const QUICK = [
  { label: "Status", text: "status" },
  { label: "Stopp", text: "stop" },
  { label: "Ziel setzen", text: "/goal ", fill: true },
  { label: "Weiter", text: "/weiter" },
];

const ls = {
  get: (k: string) => { try { return localStorage.getItem(k); } catch { return null; } },
  set: (k: string, v: string) => { try { localStorage.setItem(k, v); } catch { /* egal */ } },
  del: (k: string) => { try { localStorage.removeItem(k); } catch { /* egal */ } },
};

/** Schlüssel aus dem Link (#k=…) übernehmen und aus der Adresszeile entfernen. */
function takeKeyFromLink(): { key: string | null; fresh: boolean } {
  const m = window.location.hash.match(/[#&]k=([\w-]{40,})/);
  if (m) {
    if (ls.get(LS_KEY) !== m[1]) { ls.del(LS_TASKS); ls.del(LS_SINCE); }
    ls.set(LS_KEY, m[1]);
    window.history.replaceState(null, "", window.location.pathname);
    return { key: m[1], fresh: true };
  }
  return { key: ls.get(LS_KEY), fresh: false };
}

const CodeControl = () => {
  const [{ key, fresh }] = useState(takeKeyFromLink);
  const [pairKey, setPairKey] = useState(key);
  const [keys, setKeys] = useState<RelayKeys | null>(null);
  const [online, setOnline] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [tasks, setTasks] = useState<Task[]>(() => {
    try { return JSON.parse(ls.get(LS_TASKS) || "[]"); } catch { return []; }
  });
  const end = useRef<HTMLDivElement>(null);

  useEffect(() => { ls.set(LS_TASKS, JSON.stringify(tasks.slice(-60))); }, [tasks]);
  useEffect(() => { end.current?.scrollIntoView({ block: "end" }); }, [tasks]);

  useEffect(() => {
    if (!pairKey) return;
    let cancelled = false;
    let es: EventSource | null = null;
    const seen = new Set<string>();
    relayKeys(pairKey).then((k) => {
      if (cancelled) return;
      setKeys(k);
      es = new EventSource(RELAY + k.outbox + "/sse?since=" + (ls.get(LS_SINCE) || "1h"));
      es.onmessage = async (ev) => {
        let m: { event?: string; id: string; message: string };
        try { m = JSON.parse(ev.data); } catch { return; }
        if (m.event !== "message" || seen.has(m.id)) return;
        seen.add(m.id);
        let msg: Msg;
        try { msg = await open<Msg>(k.aes, m.message); } catch { return; }
        ls.set(LS_SINCE, m.id);
        if (msg.t === "online") { setOnline(true); return; }
        setTasks((prev) => prev.map((t) => {
          if (t.id !== msg.id || t.status === "done" || t.status === "error") return t;
          if (msg.t === "ack") return { ...t, status: "running" };
          if (msg.t !== "result") return t;
          const parts = [...(t.parts || [])];
          parts[msg.part ?? 0] = msg.text || "";
          const complete = parts.filter((p) => p != null).length >= (msg.of || 1);
          return complete
            ? { id: t.id, prompt: t.prompt, status: msg.status === "error" ? "error" : "done", result: parts.join("") }
            : { ...t, status: "running", parts };
        }));
      };
      // Ist der PC an? Er antwortet auf "hello" mit "online".
      relaySend(k, { t: "hello", quiet: !fresh }).catch(() => {});
    }).catch(() => {
      toast.error("Der Kopplungs-Link ist ungültig. Bitte am PC /koppeln neu eingeben.");
      ls.del(LS_KEY); setPairKey(null);
    });
    return () => { cancelled = true; es?.close(); };
  }, [pairKey, fresh]);

  const disconnect = () => {
    ls.del(LS_KEY); ls.del(LS_TASKS); ls.del(LS_SINCE);
    setPairKey(null); setKeys(null); setTasks([]); setOnline(false);
  };

  const send = async (override?: string) => {
    const text = (override ?? prompt).trim();
    if (!keys || !text) return;
    if (override == null) setPrompt("");
    const task: Task = { id: crypto.randomUUID(), prompt: text, status: "pending" };
    setTasks((prev) => [...prev, task]);
    try {
      await relaySend(keys, { t: "task", id: task.id, text });
    } catch (e) {
      setTasks((prev) => prev.map((t) => (t.id === task.id ? { ...t, status: "error", result: (e as Error).message } : t)));
    }
  };

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-8 max-w-md pb-28">
        {!pairKey ? (
          <div className="glass-strong rounded-2xl p-6 text-center space-y-4">
            <div className="inline-flex h-14 w-14 rounded-2xl bg-white/5 border border-white/10 items-center justify-center">
              <MonitorSmartphone className="h-7 w-7" />
            </div>
            <h1 className="font-display text-2xl font-bold">Mythos vom Handy schreiben</h1>
            <p className="text-sm text-muted-foreground">
              Gib in der Mythos-Code-App auf deinem PC <code className="font-mono">/koppeln</code> ein. Mythos schickt dir einen Link,
              zum Beispiel in deinen Discord-Kanal. Tipp ihn auf dem Handy an, dann bist du verbunden.
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="glass rounded-xl px-4 py-3 flex items-center justify-between">
              <span className="text-sm flex items-center gap-2">
                <span className={`h-2 w-2 rounded-full ${online ? "bg-green-500" : "bg-yellow-500"}`} />
                {online ? "PC ist online" : "Verbunden, warte auf den PC …"}
              </span>
              <Button variant="ghost" size="sm" onClick={disconnect} className="text-muted-foreground"><Unlink className="h-3.5 w-3.5 mr-1" /> Trennen</Button>
            </div>
            <p className="text-xs text-muted-foreground">Schreib Mythos wie in einem Messenger. Aufgaben laufen im gerade auf dem PC geöffneten Chat. Ist der PC aus, kommen sie an, sobald er wieder läuft. Tipp: Seite zum Startbildschirm hinzufügen.</p>
            <div className="flex flex-wrap gap-2">
              {QUICK.map((q) => (
                <Button key={q.label} size="sm" variant="outline" disabled={!keys} onClick={() => (q.fill ? setPrompt(q.text) : send(q.text))}>{q.label}</Button>
              ))}
            </div>
            <div className="space-y-3">
              {tasks.map((t) => (
                <div key={t.id} className="space-y-1.5">
                  <div className="glass rounded-xl rounded-br-sm px-3.5 py-2.5 ml-8 text-sm whitespace-pre-wrap">{t.prompt}</div>
                  <div className="glass-strong rounded-xl rounded-bl-sm px-3.5 py-2.5 mr-8 text-sm whitespace-pre-wrap break-words">
                    {t.status === "pending" || t.status === "running" ? (
                      <span className="flex items-center gap-2 text-muted-foreground"><Loader2 className="h-3.5 w-3.5 animate-spin" /> {t.status === "running" ? "Mythos arbeitet …" : "Wartet auf den PC …"}</span>
                    ) : (t.result || "(keine Antwort)")}
                  </div>
                </div>
              ))}
              <div ref={end} />
            </div>
            <div className="fixed bottom-0 left-0 right-0 border-t border-white/10 bg-background/95 backdrop-blur p-3 pb-[max(env(safe-area-inset-bottom),0.75rem)] flex gap-2 max-w-md mx-auto">
              <Input value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder="Nachricht an Mythos …"
                onKeyDown={(e) => { if (e.key === "Enter") void send(); }} />
              <Button onClick={() => send()} disabled={!prompt.trim() || !keys} size="icon" className="shrink-0 bg-gradient-primary text-primary-foreground hover:opacity-90">
                <Send className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default CodeControl;
