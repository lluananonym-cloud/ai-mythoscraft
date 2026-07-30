import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Bot, RefreshCcw, Loader2 } from "lucide-react";
import TopNav from "@/components/TopNav";

type Action = { id: number; description: string; status: "pending" | "running" | "done" };

const AgentTool = () => {
  const [actions, setActions] = useState<Action[]>([]);
  const [running, setRunning] = useState(false);

  const startAgent = () => {
    if (running) return;
    setRunning(true);
    const newActions: Action[] = [];
    const steps = [
      "Initialisiere KI‑Modell",
      "Lade Daten für Kontext",
      "Berechne nächste Aktion",
      "Führe Aktion aus",
      "Speichere Ergebnis",
    ];
    steps.forEach((desc, i) => newActions.push({ id: i, description: desc, status: "pending" }));
    setActions(newActions);
    // Simulate progression
    let idx = 0;
    const interval = setInterval(() => {
      setActions(prev =>
        prev.map(a => {
          if (a.id === idx) return { ...a, status: "running" };
          if (a.id < idx) return { ...a, status: "done" };
          return a;
        })
      );
      idx++;
      if (idx > steps.length) {
        clearInterval(interval);
        setRunning(false);
        setActions(prev => prev.map(a => ({ ...a, status: "done" })));
      }
    }, 1200);
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container max-w-2xl py-6 mx-auto">
        <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">AI‑Agent‑Simulator</h1>
        <Button onClick={startAgent} disabled={running} className="bg-gradient-primary text-primary-foreground hover:opacity-90 mb-4">
          <Bot className="h-4 w-4 mr-1" /> Agent starten
        </Button>
        <div className="space-y-2">
          {actions.map(a => (
            <Card key={a.id} className="glass p-3 flex items-center justify-between">
              <span>{a.description}</span>
              <span className="flex items-center gap-1 text-sm">
                {a.status === "running" && <Loader2 className="h-4 w-4 animate-spin" />}
                {a.status === "done" && <span className="text-primary">✓</span>}
                {a.status === "pending" && <span className="text-muted-foreground">⏳</span>}
              </span>
            </Card>
          ))}
        </div>
        {running && (
          <p className="mt-2 text-sm text-muted-foreground flex items-center">
            <RefreshCcw className="h-4 w-4 mr-1 animate-spin" /> Agent arbeitet…
          </p>
        )}
      </main>
    </div>
  );
};

export default AgentTool;
