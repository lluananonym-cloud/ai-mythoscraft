import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  Globe, Loader2, Search, ExternalLink, Monitor, CheckCircle2,
  Hand, MousePointer2, Play, RotateCcw,
} from "lucide-react";

type Step =
  | { kind: "search"; query: string; status: "running" | "done"; results?: { url: string; title: string; snippet: string }[] }
  | { kind: "page"; url: string; title?: string; reason?: string; status: "running" | "done"; excerpt?: string; screenshot?: string; screenshotFallback?: string };

const BOOT_LINES = [
  "MythosOS 1.0 — Agent Desktop",
  "Netzwerk verbunden · Proxy bereit",
  "Browser-Engine gestartet",
  "Sitzung initialisiert ✓",
];

/**
 * Live-Desktop der KI, direkt im Chat: Boot-Sequenz, sichtbarer Maus-Cursor,
 * Live-Screenshots und ein Eingreifen-Modus zum manuellen Weitersteuern.
 */
const AgentBrowser = ({ task, onDone }: { task: string; onDone?: (answer: string) => void }) => {
  const [steps, setSteps] = useState<Step[]>([]);
  const [answer, setAnswer] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [thinking, setThinking] = useState(true);
  const [active, setActive] = useState<number | null>(null);
  const [booting, setBooting] = useState(true);
  const [bootLine, setBootLine] = useState(0);
  const [cursor, setCursor] = useState({ x: 50, y: 50 });
  const [clicking, setClicking] = useState(false);
  const [takeover, setTakeover] = useState(false);
  const [shotKey, setShotKey] = useState(0);
  const started = useRef(false);
  const popup = useRef<Window | null>(null);
  const doneRef = useRef(onDone);
  doneRef.current = onDone;

  const pages = steps.filter((s) => s.kind === "page") as Extract<Step, { kind: "page" }>[];
  const activePage = (() => {
    if (active != null) {
      const s = steps[active];
      if (s && s.kind === "page") return s;
    }
    return pages[pages.length - 1];
  })();

  /* Desktop hochfahren */
  useEffect(() => {
    let i = 0;
    const t = setInterval(() => {
      i += 1;
      setBootLine(i);
      if (i >= BOOT_LINES.length) {
        clearInterval(t);
        setTimeout(() => setBooting(false), 350);
      }
    }, 260);
    return () => clearInterval(t);
  }, []);

  /* Maus-Cursor bewegt sich, solange die KI arbeitet */
  useEffect(() => {
    if (takeover || (!busy && !thinking)) return;
    const t = setInterval(() => {
      setCursor({ x: 12 + Math.random() * 76, y: 14 + Math.random() * 70 });
    }, 1100);
    return () => clearInterval(t);
  }, [busy, thinking, takeover]);

  /* Klick-Effekt bei jedem neuen Seitenaufruf */
  useEffect(() => {
    if (!pages.length) return;
    setClicking(true);
    const t = setTimeout(() => setClicking(false), 500);
    return () => clearTimeout(t);
  }, [pages.length]);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    let cancelled = false;

    (async () => {
      try {
        const r = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/functions/v1/browser-agent`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}`,
          },
          body: JSON.stringify({ task }),
        });
        if (!r.ok || !r.body) throw new Error(`Agent nicht erreichbar (${r.status})`);

        const reader = r.body.getReader();
        const dec = new TextDecoder();
        let buf = "";
        let acc = "";
        while (!cancelled) {
          const { done, value } = await reader.read();
          if (done) break;
          buf += dec.decode(value, { stream: true });
          const lines = buf.split("\n");
          buf = lines.pop() || "";
          for (const line of lines) {
            if (!line.startsWith("data: ")) continue;
            const p = line.slice(6).trim();
            if (!p || p === "[DONE]") continue;
            let j: any;
            try { j = JSON.parse(p); } catch { continue; }

            if (j.browser) {
              const b = j.browser;
              if (b.type === "think") { setThinking(b.status === "running"); continue; }
              if (b.type !== "search" && b.type !== "page") continue;
              setSteps((prev) => {
                const copy = [...prev];
                const i = copy.findIndex(
                  (s) =>
                    (b.type === "search" && s.kind === "search" && s.query === b.query) ||
                    (b.type === "page" && s.kind === "page" && s.url === b.url),
                );
                const next: Step =
                  b.type === "search"
                    ? { kind: "search", query: b.query, status: b.status, results: b.results }
                    : { kind: "page", url: b.url, title: b.title, reason: b.reason, status: b.status, excerpt: b.excerpt, screenshot: b.screenshot, screenshotFallback: b.screenshotFallback };
                if (i >= 0) copy[i] = { ...(copy[i] as any), ...(next as any) };
                else copy.push(next);
                return copy;
              });
              continue;
            }
            const d = j.choices?.[0]?.delta?.content;
            if (d) { acc += d; setAnswer(acc); }
          }
        }
        if (!cancelled) doneRef.current?.(acc);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Fehler");
      } finally {
        if (!cancelled) { setBusy(false); setThinking(false); }
      }
    })();

    return () => { cancelled = true; };
  }, [task]);

  const startTakeover = () => {
    setTakeover(true);
    if (activePage?.url) {
      popup.current = window.open(activePage.url, "mythos-takeover", "width=1100,height=800");
    }
  };
  const endTakeover = () => {
    setTakeover(false);
    try { popup.current?.close(); } catch { /* ignore */ }
    popup.current = null;
    setShotKey((k) => k + 1); // Screenshot neu laden -> Änderungen sichtbar
  };

  return (
    <div className="mt-2 space-y-3">
      <div className="glass rounded-2xl overflow-hidden">
        {/* Browser-Chrome */}
        <div className="flex items-center gap-2 px-3 py-2 border-b border-glass-border/60 bg-background/40">
          <div className="flex gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-rose-500/70" />
            <span className="h-2.5 w-2.5 rounded-full bg-amber-400/70" />
            <span className="h-2.5 w-2.5 rounded-full bg-emerald-500/70" />
          </div>
          <div className="flex-1 mx-1 truncate text-[11px] text-muted-foreground bg-muted/40 rounded-full px-3 py-1">
            {activePage?.url || (booting ? "boot://mythos-desktop" : "about:blank")}
          </div>
          {busy && !takeover && <Loader2 className="h-3.5 w-3.5 animate-spin text-primary shrink-0" />}
          {activePage?.url && (
            <a href={activePage.url} target="_blank" rel="noreferrer" className="text-muted-foreground hover:text-foreground shrink-0">
              <ExternalLink className="h-3.5 w-3.5" />
            </a>
          )}
        </div>

        {/* Viewport */}
        <div className="relative bg-black/60 min-h-[200px] flex items-center justify-center overflow-hidden">
          {booting ? (
            <div className="w-full py-8 px-5 font-mono text-[11px] text-emerald-400/90 space-y-1">
              {BOOT_LINES.slice(0, bootLine).map((l) => <div key={l}>› {l}</div>)}
              <div className="flex items-center gap-2 text-muted-foreground">
                <Loader2 className="h-3 w-3 animate-spin" /> Desktop wird hochgefahren…
              </div>
            </div>
          ) : activePage?.screenshot ? (
            <img
              key={`${activePage.screenshot}-${shotKey}`}
              src={activePage.screenshot}
              alt={activePage.title || activePage.url}
              className="w-full object-cover"
              onError={(e) => {
                const el = e.currentTarget;
                if (activePage.screenshotFallback && el.src !== activePage.screenshotFallback) {
                  el.src = activePage.screenshotFallback;
                }
              }}
            />
          ) : (
            <div className="text-xs text-muted-foreground flex items-center gap-2 py-14">
              {busy ? <><Loader2 className="h-4 w-4 animate-spin" /> Agent öffnet die erste Seite…</> : <><Monitor className="h-4 w-4" /> Keine Seite geöffnet</>}
            </div>
          )}

          {/* Sichtbarer Maus-Cursor */}
          {!booting && !takeover && (
            <div
              className="pointer-events-none absolute transition-all duration-700 ease-out z-20"
              style={{ left: `${cursor.x}%`, top: `${cursor.y}%` }}
            >
              <MousePointer2 className="h-4 w-4 text-white drop-shadow-[0_1px_3px_rgba(0,0,0,0.9)]" />
              {clicking && <span className="absolute -left-2 -top-2 h-8 w-8 rounded-full border border-primary/70 animate-ping" />}
            </div>
          )}

          {/* Eingreifen-Overlay */}
          {takeover && (
            <div className="absolute inset-0 z-30 bg-background/85 backdrop-blur-sm flex flex-col items-center justify-center gap-3 p-5 text-center">
              <Hand className="h-6 w-6 text-primary" />
              <div className="text-xs text-muted-foreground max-w-xs">
                Du steuerst jetzt selbst. Erledige im geöffneten Fenster z.&nbsp;B. den Login und gib die Steuerung dann zurück.
              </div>
              <div className="flex gap-2">
                {activePage?.url && (
                  <a
                    href={activePage.url} target="_blank" rel="noreferrer"
                    className="text-[11px] rounded-full px-3 py-1.5 bg-muted/60 hover:bg-muted"
                  >
                    Fenster erneut öffnen
                  </a>
                )}
                <button
                  onClick={endTakeover}
                  className="text-[11px] rounded-full px-3 py-1.5 bg-primary text-primary-foreground flex items-center gap-1.5"
                >
                  <Play className="h-3 w-3" /> KI übernimmt wieder
                </button>
              </div>
            </div>
          )}

          {activePage?.status === "running" && !takeover && (
            <div className="absolute inset-x-0 top-0 h-0.5 bg-gradient-primary animate-pulse" />
          )}
        </div>

        {/* Steuerleiste */}
        <div className="flex items-center gap-2 px-3 py-2 border-t border-glass-border/60 bg-background/30">
          <button
            onClick={takeover ? endTakeover : startTakeover}
            disabled={!activePage?.url}
            className="text-[11px] rounded-full px-3 py-1.5 bg-primary/15 text-primary hover:bg-primary/25 disabled:opacity-40 flex items-center gap-1.5"
          >
            {takeover ? <><Play className="h-3 w-3" /> Zurück an die KI</> : <><Hand className="h-3 w-3" /> Eingreifen</>}
          </button>
          <button
            onClick={() => setShotKey((k) => k + 1)}
            disabled={!activePage?.screenshot}
            className="text-[11px] rounded-full px-3 py-1.5 bg-muted/50 hover:bg-muted disabled:opacity-40 flex items-center gap-1.5"
          >
            <RotateCcw className="h-3 w-3" /> Neu laden
          </button>
          <span className="ml-auto text-[10px] text-muted-foreground">
            {takeover ? "Manuelle Steuerung" : thinking ? "KI plant…" : busy ? "KI arbeitet…" : "Fertig"}
          </span>
        </div>

        {/* Live-Schritte */}
        <div className="p-2 border-t border-glass-border/60 space-y-1 max-h-44 overflow-y-auto">
          {steps.length === 0 && (
            <div className="text-[11px] text-muted-foreground px-1 py-1">Der Agent überlegt, wonach er sucht…</div>
          )}
          {steps.map((s, i) => (
            <button
              key={i}
              onClick={() => setActive(i)}
              className={`w-full text-left rounded-lg px-2 py-1.5 transition-colors ${active === i ? "bg-primary/10" : "hover:bg-muted/40"}`}
            >
              <div className="flex items-center gap-2 text-[11px]">
                {s.kind === "search" ? <Search className="h-3 w-3 text-primary shrink-0" /> : <Globe className="h-3 w-3 text-primary shrink-0" />}
                <span className="truncate">
                  {s.kind === "search" ? `Suche: ${s.query}` : (s.title || s.url)}
                </span>
                {s.status === "running"
                  ? <Loader2 className="h-3 w-3 animate-spin ml-auto shrink-0" />
                  : <CheckCircle2 className="h-3 w-3 text-emerald-400 ml-auto shrink-0" />}
              </div>
              {s.kind === "page" && <div className="text-[10px] text-muted-foreground truncate pl-5">{s.url}</div>}
              {s.kind === "search" && s.results && (
                <div className="text-[10px] text-muted-foreground pl-5">{s.results.length} Ergebnisse</div>
              )}
            </button>
          ))}
          {thinking && (
            <div className="flex items-center gap-2 text-[11px] text-muted-foreground px-2 py-1.5">
              <Loader2 className="h-3 w-3 animate-spin" /> Agent überlegt den nächsten Schritt…
            </div>
          )}
        </div>
      </div>

      {error && <div className="text-xs text-destructive">{error}</div>}

      {(answer || busy) && (
        <div className="prose prose-sm dark:prose-invert max-w-none">
          {answer
            ? <ReactMarkdown remarkPlugins={[remarkGfm]}>{answer}</ReactMarkdown>
            : <div className="text-xs text-muted-foreground flex items-center gap-2"><Loader2 className="h-3.5 w-3.5 animate-spin" /> Agent recherchiert…</div>}
        </div>
      )}
    </div>
  );
};

export default AgentBrowser;
