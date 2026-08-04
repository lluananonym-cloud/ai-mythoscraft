import { useEffect, useRef, useState } from "react";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import Paywall from "@/components/Paywall";
import { useSubscription } from "@/hooks/useSubscription";
import { Globe, Loader2, Search, Play, ExternalLink, Monitor } from "lucide-react";

type Step =
  | { kind: "search"; query: string; status: "running" | "done"; results?: { url: string; title: string; snippet: string }[] }
  | { kind: "page"; url: string; title?: string; reason?: string; status: "running" | "done"; excerpt?: string; screenshot?: string };

const BrowserAgent = () => {
  const { isPro, loading } = useSubscription();
  const [paywall, setPaywall] = useState(false);
  const [task, setTask] = useState("");
  const [steps, setSteps] = useState<Step[]>([]);
  const [answer, setAnswer] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState<number | null>(null);
  const stepsEnd = useRef<HTMLDivElement>(null);

  useEffect(() => { stepsEnd.current?.scrollIntoView({ behavior: "smooth" }); }, [steps.length]);

  const activePage = (() => {
    const idx = active ?? [...steps].reverse().findIndex((s) => s.kind === "page");
    const list = steps.filter((s) => s.kind === "page") as Extract<Step, { kind: "page" }>[];
    if (active != null) {
      const s = steps[active];
      return s && s.kind === "page" ? s : list[list.length - 1];
    }
    return list[list.length - 1];
  })();

  const run = async () => {
    if (!task.trim() || busy) return;
    if (!isPro) { setPaywall(true); return; }
    setBusy(true); setError(null); setSteps([]); setAnswer(""); setActive(null);

    try {
      const r = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/functions/v1/browser-agent`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}`,
        },
        body: JSON.stringify({ task: task.trim() }),
      });
      if (!r.ok || !r.body) throw new Error(`Agent nicht erreichbar (${r.status})`);

      const reader = r.body.getReader();
      const dec = new TextDecoder();
      let buf = "";
      let acc = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true });
        const lines = buf.split("\n");
        buf = lines.pop() || "";
        for (const line of lines) {
          if (!line.startsWith("data: ")) continue;
          const p = line.slice(6).trim();
          if (p === "[DONE]") continue;
          let j: any;
          try { j = JSON.parse(p); } catch { continue; }

          if (j.browser) {
            const b = j.browser;
            if (b.type !== "search" && b.type !== "page") continue;
            setSteps((prev) => {
              const copy = [...prev];
              const i = copy.findIndex(
                (s) => s.status === "running" && ((b.type === "search" && s.kind === "search") || (b.type === "page" && s.kind === "page" && s.url === b.url)),
              );
              const next: Step =
                b.type === "search"
                  ? { kind: "search", query: b.query, status: b.status, results: b.results }
                  : { kind: "page", url: b.url, title: b.title, reason: b.reason, status: b.status, excerpt: b.excerpt, screenshot: b.screenshot };
              if (i >= 0) copy[i] = next; else copy.push(next);
              return copy;
            });
            continue;
          }
          const d = j.choices?.[0]?.delta?.content;
          if (d) { acc += d; setAnswer(acc); }
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Fehler");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-[100dvh] flex flex-col">
      <TopNav />
      <main className="container max-w-6xl py-6 flex-1">
        <div className="mb-5">
          <h1 className="font-display text-2xl sm:text-3xl font-bold flex items-center gap-2">
            <Globe className="h-6 w-6 text-primary" /> Browser-Agent
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Die KI surft wirklich: sie sucht, öffnet Seiten und liest sie — du siehst live jeden Schritt in ihrem Browser.
          </p>
        </div>

        <div className="glass rounded-2xl p-3 flex items-end gap-2 mb-6">
          <Textarea
            value={task}
            onChange={(e) => setTask(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); run(); } }}
            rows={1}
            placeholder="z.B. Vergleiche die besten Minecraft-Anti-Cheat-Plugins 2026 und nenne Preise"
            className="min-h-[40px] max-h-32 resize-none border-0 bg-transparent focus-visible:ring-0 text-sm"
          />
          <Button onClick={run} disabled={busy || !task.trim() || loading} className="bg-gradient-primary text-primary-foreground shrink-0">
            {busy ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Play className="h-4 w-4 mr-1" />}
            {busy ? "Surft…" : "Agent starten"}
          </Button>
        </div>

        {error && <div className="text-sm text-destructive mb-4">{error}</div>}

        <div className="grid lg:grid-cols-[320px_1fr] gap-5">
          {/* Schritte */}
          <div className="space-y-2 lg:max-h-[70vh] lg:overflow-y-auto pr-1">
            <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1">Aktionen</div>
            {steps.length === 0 && !busy && (
              <div className="text-xs text-muted-foreground glass rounded-xl p-3">Noch keine Schritte — starte eine Aufgabe.</div>
            )}
            {steps.map((s, i) => (
              <button
                key={i}
                onClick={() => setActive(i)}
                className={`w-full text-left glass rounded-xl p-3 transition-colors ${active === i ? "border-primary/50" : "hover:border-primary/30"}`}
              >
                <div className="flex items-center gap-2 text-xs font-medium">
                  {s.kind === "search" ? <Search className="h-3.5 w-3.5 text-primary" /> : <Globe className="h-3.5 w-3.5 text-primary" />}
                  <span className="truncate">
                    {s.kind === "search" ? `Suche: ${s.query}` : (s.title || s.url)}
                  </span>
                  {s.status === "running" && <Loader2 className="h-3 w-3 animate-spin ml-auto shrink-0" />}
                </div>
                {s.kind === "page" && <div className="text-[11px] text-muted-foreground truncate mt-1">{s.url}</div>}
                {s.kind === "search" && s.results && (
                  <div className="text-[11px] text-muted-foreground mt-1">{s.results.length} Ergebnisse</div>
                )}
              </button>
            ))}
            <div ref={stepsEnd} />
          </div>

          {/* Browser-Fenster */}
          <div>
            <div className="glass-strong rounded-2xl overflow-hidden">
              <div className="flex items-center gap-2 px-3 py-2 border-b border-glass-border/60 bg-background/40">
                <div className="flex gap-1.5">
                  <span className="h-2.5 w-2.5 rounded-full bg-rose-500/70" />
                  <span className="h-2.5 w-2.5 rounded-full bg-amber-400/70" />
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-500/70" />
                </div>
                <div className="flex-1 mx-2 truncate text-[11px] text-muted-foreground bg-muted/40 rounded-full px-3 py-1">
                  {activePage?.url || "about:blank"}
                </div>
                {activePage?.url && (
                  <a href={activePage.url} target="_blank" rel="noreferrer" className="text-muted-foreground hover:text-foreground">
                    <ExternalLink className="h-3.5 w-3.5" />
                  </a>
                )}
              </div>
              <div className="relative bg-black/40 min-h-[280px] flex items-center justify-center">
                {activePage?.screenshot ? (
                  <img src={activePage.screenshot} alt={activePage.title || activePage.url} className="w-full object-cover" loading="lazy" />
                ) : busy ? (
                  <div className="text-xs text-muted-foreground flex items-center gap-2 py-16">
                    <Loader2 className="h-4 w-4 animate-spin" /> Agent lädt Seite…
                  </div>
                ) : (
                  <div className="text-xs text-muted-foreground flex flex-col items-center gap-2 py-16">
                    <Monitor className="h-6 w-6" /> Hier siehst du, was die KI gerade öffnet.
                  </div>
                )}
              </div>
              {activePage?.excerpt && (
                <div className="p-3 border-t border-glass-border/60 text-[11px] text-muted-foreground max-h-32 overflow-y-auto whitespace-pre-wrap">
                  {activePage.excerpt}
                </div>
              )}
            </div>

            {(answer || busy) && (
              <div className="glass rounded-2xl p-4 mt-4">
                <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">Ergebnis</div>
                <div className="text-sm leading-relaxed whitespace-pre-wrap">
                  {answer || <Loader2 className="h-4 w-4 animate-spin" />}
                </div>
              </div>
            )}
          </div>
        </div>
      </main>
      <Paywall open={paywall} onOpenChange={setPaywall} reason="Der Browser-Agent ist eine Pro-Funktion." />
    </div>
  );
};

export default BrowserAgent;
