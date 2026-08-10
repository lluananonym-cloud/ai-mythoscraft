import { useEffect, useRef, useState } from "react";
import { Chrome, Download, Loader2, MousePointerClick, Send, CheckCircle2, Puzzle, Square, Brain } from "lucide-react";

type Line = { kind: "you" | "ai" | "act" | "err"; text: string };

/**
 * Panel für /browser: Erweiterung installieren und danach direkt aus dem Chat
 * den echten Browser steuern (über die Bridge der Erweiterung).
 */
const BrowserExtensionPanel = ({ initialTask }: { initialTask?: string }) => {
  const [installed, setInstalled] = useState(false);
  const [version, setVersion] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [task, setTask] = useState(initialTask ?? "");
  const [lines, setLines] = useState<Line[]>([]);
  const end = useRef<HTMLDivElement>(null);
  const autoSent = useRef(false);
  const lastSeen = useRef(0);

  useEffect(() => { end.current?.scrollIntoView({ behavior: "smooth" }); }, [lines.length]);

  /* Erweiterung wirklich tracken: Heartbeat der Bridge + Timeout */
  useEffect(() => {
    const onMsg = (e: MessageEvent) => {
      const d = e.data;
      if (!d || d.source !== "mythos-ext") return;
      if (d.type === "ready") {
        lastSeen.current = Date.now();
        setInstalled(true);
        if (d.version) setVersion(d.version);
        if (typeof d.busy === "boolean") setBusy(d.busy);
        return;
      }
      if (d.type === "history") {
        setLines((d.lines || []).map((l: Line) => ({ kind: l.kind, text: l.text })));
        setBusy(!!d.busy);
        return;
      }
      if (d.type === "start") { setBusy(true); return; }
      if (d.type === "say") setLines((p) => [...p, { kind: "ai", text: d.text }]);
      if (d.type === "action") setLines((p) => [...p, { kind: "act", text: d.info || d.action }]);
      if (d.type === "error") setLines((p) => [...p, { kind: "err", text: d.message }]);
      if (d.type === "done") setBusy(false);
    };
    window.addEventListener("message", onMsg);
    const ping = () => window.postMessage({ source: "mythos-web", type: "ping" }, "*");
    ping();
    window.postMessage({ source: "mythos-web", type: "history" }, "*");
    const t = setInterval(() => {
      ping();
      if (lastSeen.current && Date.now() - lastSeen.current > 5000) setInstalled(false);
    }, 1500);
    return () => { window.removeEventListener("message", onMsg); clearInterval(t); };
  }, []);

  const download = () => {
    fetch("/mythos-browser.zip")
      .then((r) => { if (!r.ok) throw new Error(`Download fehlgeschlagen (${r.status})`); return r.blob(); })
      .then((blob) => {
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = "mythos-browser.zip";
        a.click();
        URL.revokeObjectURL(a.href);
      })
      .catch((e) => setLines((p) => [...p, { kind: "err", text: e.message }]));
  };

  const send = (t?: string) => {
    const text = (t ?? task).trim();
    if (!text || busy) return;
    if (!installed) { setLines((p) => [...p, { kind: "err", text: "Erweiterung ist nicht installiert oder inaktiv." }]); return; }
    setLines((p) => [...p, { kind: "you", text }]);
    setTask("");
    setBusy(true);
    window.postMessage({ source: "mythos-web", type: "task", task: text }, "*");
  };

  const stop = () => { window.postMessage({ source: "mythos-web", type: "stop" }, "*"); setBusy(false); };
  const forget = () => {
    window.postMessage({ source: "mythos-web", type: "forget" }, "*");
    window.postMessage({ source: "mythos-web", type: "clear" }, "*");
    setLines([]);
  };

  useEffect(() => {
    if (installed && initialTask && !autoSent.current) { autoSent.current = true; send(initialTask); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [installed, initialTask]);

  return (
    <div className="mt-2 glass rounded-2xl overflow-hidden">
      <div className="flex items-center gap-2 px-3 py-2 border-b border-glass-border/60 bg-background/40">
        <Puzzle className="h-4 w-4 text-primary" />
        <span className="text-xs font-medium">Mythos Browser Control</span>
        <span className={`ml-auto text-[10px] flex items-center gap-1 ${installed ? "text-emerald-400" : "text-muted-foreground"}`}>
          {installed ? <><CheckCircle2 className="h-3 w-3" /> verbunden{version ? ` · v${version}` : ""}</> : "nicht installiert"}
        </span>
      </div>

      {!installed && (
        <div className="p-3 space-y-2 text-[11px] text-muted-foreground">
          <p className="text-foreground text-xs font-medium flex items-center gap-1.5">
            <Chrome className="h-3.5 w-3.5" /> Einmalige Installation
          </p>
          <ol className="list-decimal pl-4 space-y-0.5">
            <li>ZIP herunterladen und entpacken</li>
            <li><code>chrome://extensions</code> öffnen</li>
            <li>„Entwicklermodus" oben rechts aktivieren</li>
            <li>„Entpackte Erweiterung laden" → entpackten Ordner wählen</li>
            <li>Diese Seite neu laden — dann steht hier „verbunden"</li>
          </ol>
          <button
            onClick={download}
            className="mt-1 text-[11px] rounded-full px-3 py-1.5 bg-gradient-primary text-primary-foreground flex items-center gap-1.5"
          >
            <Download className="h-3 w-3" /> Erweiterung herunterladen
          </button>
        </div>
      )}

      <div className="p-2 space-y-1.5 max-h-64 overflow-y-auto">
        {lines.length === 0 && installed && (
          <div className="text-[11px] text-muted-foreground px-1 py-1">
            Sag mir, was ich in deinem Browser machen soll — ich klicke und tippe sichtbar für dich.
          </div>
        )}
        {lines.map((l, i) => (
          <div
            key={i}
            className={
              l.kind === "you"
                ? "text-[12px] rounded-xl px-2.5 py-1.5 bg-primary/15 ml-8"
                : l.kind === "ai"
                ? "text-[12px] rounded-xl px-2.5 py-1.5 bg-muted/40 mr-8 whitespace-pre-wrap"
                : l.kind === "err"
                ? "text-[11px] text-destructive px-1"
                : "text-[11px] text-muted-foreground px-1 flex items-center gap-1.5"
            }
          >
            {l.kind === "act" && <MousePointerClick className="h-3 w-3 shrink-0" />}
            {l.text}
          </div>
        ))}
        {busy && (
          <div className="text-[11px] text-muted-foreground flex items-center gap-2 px-1">
            <Loader2 className="h-3 w-3 animate-spin" /> steuert deinen Browser…
          </div>
        )}
        <div ref={end} />
      </div>

      <div className="flex items-center gap-2 p-2 border-t border-glass-border/60">
        <input
          value={task}
          onChange={(e) => setTask(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); send(); } }}
          disabled={!installed}
          placeholder={installed ? "z.B. öffne YouTube und suche Lofi Beats" : "Erst Erweiterung installieren"}
          className="flex-1 bg-transparent text-xs outline-none px-1 disabled:opacity-50"
        />
        {busy ? (
          <button onClick={stop} className="text-[11px] rounded-full px-3 py-1.5 bg-destructive/15 text-destructive flex items-center gap-1.5">
            <Square className="h-3 w-3" /> Stop
          </button>
        ) : (
          <button
            onClick={() => send()}
            disabled={!installed || !task.trim()}
            className="text-[11px] rounded-full px-3 py-1.5 bg-primary/15 text-primary hover:bg-primary/25 disabled:opacity-40 flex items-center gap-1.5"
          >
            <Send className="h-3 w-3" /> Ausführen
          </button>
        )}
        <button
          onClick={forget}
          title="Verlauf & Erinnerung löschen"
          className="text-[11px] rounded-full px-2.5 py-1.5 bg-muted/50 hover:bg-muted text-muted-foreground flex items-center gap-1.5"
        >
          <Brain className="h-3 w-3" />
        </button>
      </div>
    </div>
  );
};

export default BrowserExtensionPanel;
