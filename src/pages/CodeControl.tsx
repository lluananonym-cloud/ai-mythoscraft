// "Code"-Tab der Mythos-Handy-App: koppelt sich per 6-stelligem Code mit der Windows-App und
// schickt Aufgaben dorthin (laufen im gerade auf dem PC geöffneten Chat, wie normal eingetippt).
// Braucht keine eigene Anmeldung – der Kopplungscode ist der Schlüssel, wie bei einer Smart-TV-App.
import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Loader2, MonitorSmartphone, Send, Unlink } from "lucide-react";
import { toast } from "sonner";

const LS_KEY = "mythos.remotePair"; // { pairId, secret, device }
type Pair = { pairId: string; secret: string; device?: string };
type Task = { id: string; prompt: string; status: "pending" | "running" | "done" | "error"; result?: string };

const call = async (body: Record<string, unknown>) => {
  const { data, error } = await supabase.functions.invoke("remote", { body });
  if (error) {
    // Bei Fehlern steht die eigentliche Meldung im Antwort-Body, nicht in error.message.
    let msg = error.message;
    try { const b = await (error as { context?: Response }).context?.json(); if (b?.error) msg = b.error; } catch { /* kein JSON */ }
    throw new Error(msg);
  }
  if (data?.error) throw new Error(data.error);
  return data;
};

const CodeControl = () => {
  const [pair, setPair] = useState<Pair | null>(() => {
    try { const s = localStorage.getItem(LS_KEY); return s ? JSON.parse(s) : null; } catch { return null; }
  });
  // /koppeln 123456 im Web-Chat öffnet diese Seite mit ?code=123456
  const [params] = useSearchParams();
  const [code, setCode] = useState(() => (params.get("code") || "").replace(/\D/g, "").slice(0, 6));
  const [connecting, setConnecting] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [tasks, setTasks] = useState<Task[]>([]);
  const pollers = useRef<Record<string, ReturnType<typeof setInterval>>>({});

  useEffect(() => () => { Object.values(pollers.current).forEach(clearInterval); }, []);

  const connect = async () => {
    const clean = code.replace(/\D/g, "").slice(0, 6);
    if (clean.length !== 6) { toast.error("Bitte den 6-stelligen Code vom PC eingeben."); return; }
    setConnecting(true);
    try {
      const r = await call({ kind: "claim", code: clean, device_name: "iPhone" });
      const p: Pair = { pairId: r.pair_id, secret: r.pair_secret, device: "PC" };
      localStorage.setItem(LS_KEY, JSON.stringify(p));
      setPair(p); setCode("");
      toast.success("Verbunden!");
    } catch (e) { toast.error(e instanceof Error ? e.message : "Verbinden fehlgeschlagen"); }
    setConnecting(false);
  };

  const disconnect = async () => {
    if (pair) call({ kind: "unpair", pair_id: pair.pairId, pair_secret: pair.secret }).catch(() => {});
    localStorage.removeItem(LS_KEY); setPair(null); setTasks([]);
  };

  const pollTask = (pairId: string, secret: string, id: string) => {
    pollers.current[id] = setInterval(async () => {
      try {
        const r = await call({ kind: "get", pair_id: pairId, pair_secret: secret, task_id: id });
        if (r.status === "done" || r.status === "error") {
          clearInterval(pollers.current[id]); delete pollers.current[id];
          setTasks((prev) => prev.map((t) => (t.id === id ? { ...t, status: r.status, result: r.result } : t)));
        }
      } catch { /* nächster Versuch reicht */ }
    }, 1500);
  };

  const send = async () => {
    if (!pair || !prompt.trim()) return;
    const text = prompt.trim(); setPrompt("");
    try {
      const r = await call({ kind: "push", pair_id: pair.pairId, pair_secret: pair.secret, prompt: text });
      const task: Task = { id: r.task_id, prompt: text, status: "pending" };
      setTasks((prev) => [...prev, task]);
      pollTask(pair.pairId, pair.secret, task.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Senden fehlgeschlagen");
      if (e instanceof Error && /gekoppelt/i.test(e.message)) disconnect();
    }
  };

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-8 max-w-md pb-28">
        {!pair ? (
          <div className="glass-strong rounded-2xl p-6 text-center space-y-5">
            <div className="inline-flex h-14 w-14 rounded-2xl bg-white/5 border border-white/10 items-center justify-center">
              <MonitorSmartphone className="h-7 w-7" />
            </div>
            <h1 className="font-display text-2xl font-bold">Mit PC koppeln</h1>
            <p className="text-sm text-muted-foreground">
              Gib in der Mythos-Code-App auf deinem PC <code className="font-mono">/koppeln</code> ein und trag hier den angezeigten Code ein.
            </p>
            <Input
              value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
              placeholder="123456" inputMode="numeric" className="text-center text-2xl tracking-[0.3em] font-mono h-14"
              onKeyDown={(e) => e.key === "Enter" && connect()}
            />
            <Button onClick={connect} disabled={connecting || code.length !== 6} className="w-full bg-gradient-primary text-primary-foreground hover:opacity-90">
              {connecting ? <Loader2 className="h-4 w-4 animate-spin mr-1.5" /> : null}
              Verbinden
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="glass rounded-xl px-4 py-3 flex items-center justify-between">
              <span className="text-sm flex items-center gap-2"><span className="h-2 w-2 rounded-full bg-green-500" /> Verbunden mit {pair.device || "PC"}</span>
              <Button variant="ghost" size="sm" onClick={disconnect} className="text-muted-foreground"><Unlink className="h-3.5 w-3.5 mr-1" /> Trennen</Button>
            </div>
            <p className="text-xs text-muted-foreground">Aufgaben laufen im gerade auf dem PC geöffneten Chat – wie dort eingetippt.</p>
            <div className="space-y-3">
              {tasks.slice().reverse().map((t) => (
                <div key={t.id} className="space-y-1.5">
                  <div className="glass rounded-xl rounded-br-sm px-3.5 py-2.5 ml-8 text-sm">{t.prompt}</div>
                  <div className="glass-strong rounded-xl rounded-bl-sm px-3.5 py-2.5 mr-8 text-sm whitespace-pre-wrap">
                    {t.status === "pending" || t.status === "running" ? (
                      <span className="flex items-center gap-2 text-muted-foreground"><Loader2 className="h-3.5 w-3.5 animate-spin" /> {t.status === "running" ? "Mythos arbeitet …" : "Wartet …"}</span>
                    ) : (t.result || "(keine Antwort)")}
                  </div>
                </div>
              ))}
            </div>
            <div className="fixed bottom-0 left-0 right-0 border-t border-white/10 bg-background/95 backdrop-blur p-3 flex gap-2 max-w-md mx-auto">
              <Input value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder="Aufgabe an den PC …"
                onKeyDown={(e) => e.key === "Enter" && send()} />
              <Button onClick={send} disabled={!prompt.trim()} size="icon" className="shrink-0 bg-gradient-primary text-primary-foreground hover:opacity-90">
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
