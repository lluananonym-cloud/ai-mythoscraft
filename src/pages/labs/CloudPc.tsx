import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import LabsShell from "@/components/labs/LabsShell";
import MarkdownMessage from "@/components/MarkdownMessage";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Monitor, TerminalSquare, FolderOpen, Globe, Play, Pause, Square, Send, Download, Save, RotateCw, Trash2,
  FilePlus, ExternalLink, Loader2, CheckCircle2, XCircle, FileText, Folder, Sparkles, MousePointer2, Hand,
} from "lucide-react";
import { toast } from "sonner";
import { Vfs, norm, formatSize } from "@/lib/cloudPc/vfs";
import { Shell } from "@/lib/cloudPc/shell";
import { killRunning, readWeb } from "@/lib/cloudPc/runtimes";
import { buildPage, download, makeZip } from "@/lib/cloudPc/project";
import { runAgent, type Action, type AgentEvent, type Driver } from "@/lib/cloudPc/agent";
import { openHtmlInTab, safeHtml, saveMyApp } from "@/lib/labs";

type Tab = "terminal" | "files" | "browser";
type Line = { text: string; kind: "out" | "err" | "info" | "cmd" };
type LogItem =
  | { kind: "user"; text: string }
  | { kind: "plan"; text: string }
  | { kind: "action"; id: string; action: Action; status: "running" | "done" | "error"; result?: string }
  | { kind: "hint"; text: string }
  | { kind: "info"; text: string; error?: boolean };
type Page = { target: string; kind: "html" | "text" | "web"; content: string; loading?: boolean };

const EXAMPLES = [
  { label: "Website bauen", task: "Baue eine moderne Website für ein Café namens Mondbohne: Startseite mit Bild-Header, Speisekarte mit Preisen, Öffnungszeiten und ein Kontaktformular." },
  { label: "Recherche", task: "Recherchiere im Internet, was es Neues bei KI-Assistenten gibt, und schreib einen übersichtlichen Bericht mit Quellen als bericht.md." },
  { label: "Daten auswerten", task: "Erzeuge mit Python Beispiel-Verkaufsdaten für 12 Monate, werte sie aus und baue eine Website mit Diagramm und den wichtigsten Zahlen." },
  { label: "Spiel programmieren", task: "Programmiere ein Snake-Spiel mit Highscore, Neustart-Button und Touch-Steuerung fürs Handy." },
];

const ACTION_ICON = { write: FileText, run: TerminalSquare, open: Globe, read: FolderOpen, done: CheckCircle2 } as const;
const TAB_OF: Record<Action["type"], Tab> = { write: "files", run: "terminal", open: "browser", read: "files", done: "browser" };

function actionLabel(a: Action) {
  switch (a.type) {
    case "write": return `Schreibt ${a.path}`;
    case "run": return a.cmd;
    case "open": return `Öffnet ${a.target}`;
    case "read": return `Liest ${a.path}`;
    default: return "Fertig";
  }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Mythos Cloud-PC: ein eigener Computer für den Agenten, der komplett im Browser läuft. */
export default function CloudPc() {
  const fs = useMemo(() => new Vfs(), []);
  const version = useSyncExternalStore(fs.subscribe, fs.getVersion);
  const [tab, setTab] = useState<Tab>("terminal");
  const [lines, setLines] = useState<Line[]>([
    { text: "MythosOS 1.0 – dein Cloud-PC läuft direkt in diesem Browser. Kostenlos, nichts einzurichten.\n„help“ zeigt alle Befehle.\n", kind: "info" },
  ]);
  const [cmd, setCmd] = useState("");
  const [running, setRunning] = useState(false);
  const [histPos, setHistPos] = useState(-1);
  const [selected, setSelected] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [page, setPage] = useState<Page | null>(null);
  const [address, setAddress] = useState("");
  const [frameKey, setFrameKey] = useState(0);

  const [task, setTask] = useState("");
  const [agentOn, setAgentOn] = useState(false);
  const [paused, setPaused] = useState(false);
  const [thinking, setThinking] = useState(false);
  const [agentTab, setAgentTab] = useState<Tab | null>(null);
  const [log, setLog] = useState<LogItem[]>([]);
  const [summary, setSummary] = useState<string | null>(null);
  const [hint, setHint] = useState("");

  const termEnd = useRef<HTMLDivElement>(null);
  const logEnd = useRef<HTMLDivElement>(null);
  const pausedRef = useRef(false);
  const stoppedRef = useRef(false);
  const hintsRef = useRef<string[]>([]);
  const resumeRef = useRef<(() => void) | null>(null);
  const pageErrors = useRef<string[]>([]);
  const typingRef = useRef(false);

  const print = useCallback((text: string, kind: Line["kind"] = "out") => {
    setLines((l) => {
      const next = [...l, { text, kind }];
      return next.length > 600 ? next.slice(-600) : next;
    });
  }, []);

  const openTarget = useCallback(async (target: string): Promise<Page> => {
    pageErrors.current = [];
    setAddress(target);
    if (/^(https?:\/\/|www\.)/i.test(target)) {
      setPage({ target, kind: "web", content: "", loading: true });
      try {
        const text = await readWeb(target);
        const p: Page = { target, kind: "web", content: text };
        setPage(p);
        return p;
      } catch (e) {
        const p: Page = { target, kind: "web", content: `Seite konnte nicht geladen werden: ${(e as Error).message}` };
        setPage(p);
        return p;
      }
    }
    const path = norm(target);
    const p: Page = /\.html?$/i.test(path)
      ? { target: path, kind: "html", content: buildPage(fs, path) }
      : { target: path, kind: "text", content: fs.read(path) };
    setPage(p);
    setFrameKey((k) => k + 1);
    return p;
  }, [fs]);

  const shell = useMemo(() => new Shell(fs, {
    open: (t) => { setTab("browser"); void openTarget(t); },
    clear: () => setLines([]),
  }), [fs, openTarget]);

  useEffect(() => { termEnd.current?.scrollIntoView({ block: "end" }); }, [lines, cmd]);
  useEffect(() => { logEnd.current?.scrollIntoView({ block: "end", behavior: "smooth" }); }, [log, thinking]);

  // Editor folgt der Datei, solange man selbst nichts ungespeichertes tippt.
  useEffect(() => {
    if (!selected || typingRef.current) return;
    setDraft(fs.isFile(selected) ? fs.read(selected) : "");
  }, [selected, version, fs]);

  // Links und Fehler aus der Vorschau.
  useEffect(() => {
    const onMsg = (e: MessageEvent) => {
      const d = e.data;
      if (d && typeof d.mythosPcError === "string") pageErrors.current.push(d.mythosPcError);
      if (d && typeof d.mythosPcOpen === "string") {
        const base = typeof d.from === "string" ? d.from.replace(/\/[^/]*$/, "") || "/" : "/";
        const t = norm(d.mythosPcOpen.split(/[?#]/)[0], base);
        if (fs.isFile(t)) void openTarget(t);
      }
    };
    window.addEventListener("message", onMsg);
    return () => window.removeEventListener("message", onMsg);
  }, [fs, openTarget]);

  // Live-Vorschau: geöffnete Seite neu laden, wenn sich Dateien ändern.
  useEffect(() => {
    if (!page || page.kind === "web" || page.loading) return;
    if (!fs.isFile(page.target)) return;
    const content = page.kind === "html" ? buildPage(fs, page.target) : fs.read(page.target);
    if (content !== page.content) setPage({ ...page, content });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);

  const busy = running || (agentOn && !paused);

  const runLine = async (line: string) => {
    print(`${shell.prompt()} ${line}\n`, "cmd");
    setRunning(true);
    try {
      return await shell.exec(line, (t, k) => print(t, k || "out"));
    } finally {
      setRunning(false);
    }
  };

  const submitCmd = async () => {
    const line = cmd;
    setCmd("");
    setHistPos(-1);
    if (!line.trim()) { print(`${shell.prompt()}\n`, "cmd"); return; }
    await runLine(line);
  };

  const onTermKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") { e.preventDefault(); void submitCmd(); }
    else if (e.key === "ArrowUp" || e.key === "ArrowDown") {
      e.preventDefault();
      const h = shell.history;
      if (!h.length) return;
      const pos = e.key === "ArrowUp" ? (histPos < 0 ? h.length - 1 : Math.max(0, histPos - 1)) : histPos < 0 ? -1 : histPos + 1;
      if (pos < 0 || pos >= h.length) { setHistPos(-1); setCmd(""); return; }
      setHistPos(pos);
      setCmd(h[pos]);
    }
  };

  const stopProgram = () => { if (killRunning()) print("^C\n", "err"); };

  // ---------- Dateien ----------
  const files = useMemo(() => fs.all(), [fs, version]); // eslint-disable-line react-hooks/exhaustive-deps
  const dirs = useMemo(() => fs.allDirs().filter((d) => d !== "/" && !files.some(([f]) => f.startsWith(d + "/"))), [fs, files]);
  const dirty = !!selected && fs.isFile(selected) && fs.read(selected) !== draft;

  const saveDraft = () => {
    if (!selected) return;
    try { fs.write(selected, draft); typingRef.current = false; toast.success("Gespeichert"); } catch (e) { toast.error((e as Error).message); }
  };
  const newFile = () => {
    const name = prompt("Name der neuen Datei (z. B. notizen.txt oder seite/index.html):");
    if (!name) return;
    try { const p = norm(name); if (!fs.isFile(p)) fs.write(p, ""); setSelected(p); setTab("files"); } catch (e) { toast.error((e as Error).message); }
  };
  const deleteFile = (p: string) => {
    if (!confirm(`${p} löschen?`)) return;
    fs.remove(p);
    if (selected === p) setSelected(null);
  };

  // ---------- Agent ----------
  const addLog = (item: LogItem) => setLog((l) => [...l, item]);
  const updateAction = (id: string, patch: Partial<Extract<LogItem, { kind: "action" }>>) =>
    setLog((l) => l.map((x) => (x.kind === "action" && x.id === id ? { ...x, ...patch } : x)));

  const driver: Driver = {
    emit: (ev: AgentEvent) => {
      if (ev.kind === "thinking") { setThinking(true); setAgentTab(null); return; }
      setThinking(false);
      if (ev.kind === "plan") addLog({ kind: "plan", text: ev.text });
      else if (ev.kind === "action") {
        if (ev.status === "running") addLog({ kind: "action", id: ev.id, action: ev.action, status: "running" });
        else updateAction(ev.id, { status: ev.status, result: ev.result });
      } else if (ev.kind === "done") { setSummary(ev.summary); addLog({ kind: "info", text: "Aufgabe erledigt." }); }
      else if (ev.kind === "stopped") addLog({ kind: "info", text: "Angehalten. Du hast jetzt die Kontrolle." });
      else if (ev.kind === "error") addLog({ kind: "info", text: ev.text, error: true });
    },
    stopped: () => stoppedRef.current,
    waitIfPaused: () => (pausedRef.current ? new Promise<void>((r) => { resumeRef.current = r; }) : Promise.resolve()),
    takeHints: () => hintsRef.current.splice(0),
    write: async (path, content) => {
      const p = norm(path);
      setTab("files"); setAgentTab("files");
      typingRef.current = true;
      setSelected(p);
      // Tippen sichtbar machen, aber nicht ewig warten.
      const total = Math.min(1600, 250 + content.length / 6);
      const frames = Math.max(1, Math.round(total / 40));
      for (let i = 1; i <= frames; i++) {
        if (stoppedRef.current) break;
        setDraft(content.slice(0, Math.round((content.length * i) / frames)));
        await sleep(40);
      }
      typingRef.current = false;
      fs.write(p, content);
      setDraft(content);
      await sleep(150);
    },
    run: async (line) => {
      setTab("terminal"); setAgentTab("terminal");
      for (let i = 1; i <= line.length; i += Math.max(1, Math.ceil(line.length / 25))) {
        setCmd(line.slice(0, i));
        await sleep(25);
      }
      setCmd("");
      return runLine(line);
    },
    open: async (target) => {
      setTab("browser"); setAgentTab("browser");
      const t = /^(https?:\/\/|www\.)/i.test(target) ? target : norm(target);
      if (!/^(https?:|www\.)/i.test(t) && !fs.isFile(t)) throw new Error(`${target} gibt es nicht`);
      const p = await openTarget(t);
      if (p.kind !== "html") return p.content.slice(0, 3000);
      await sleep(1800); // Seite laufen lassen, damit JavaScript-Fehler ankommen.
      const text = p.content.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>/gi, " ").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
      const errs = [...new Set(pageErrors.current)];
      return (errs.length ? `JavaScript-FEHLER auf der Seite:\n${errs.slice(0, 8).join("\n")}\n\n` : "Keine JavaScript-Fehler.\n") + `Sichtbarer Text: ${text.slice(0, 1200)}`;
    },
  };

  const startAgent = async (t = task) => {
    const goal = t.trim();
    if (!goal || agentOn) return;
    stoppedRef.current = false; pausedRef.current = false; hintsRef.current = [];
    setPaused(false); setSummary(null); setAgentOn(true);
    setLog((l) => [...l, { kind: "user", text: goal }]);
    setTask("");
    try {
      await runAgent(goal, fs, driver);
    } catch (e) {
      addLog({ kind: "info", text: (e as Error).message, error: true });
    } finally {
      setAgentOn(false); setThinking(false); setAgentTab(null); setPaused(false);
      pausedRef.current = false;
    }
  };

  const togglePause = () => {
    const next = !pausedRef.current;
    pausedRef.current = next;
    setPaused(next);
    if (next) addLog({ kind: "info", text: "Pause. Du kannst jetzt selbst tippen, Dateien ändern oder Mythos einen Hinweis geben." });
    else { resumeRef.current?.(); resumeRef.current = null; addLog({ kind: "info", text: "Mythos macht weiter." }); }
  };

  const stopAgent = () => {
    stoppedRef.current = true;
    killRunning();
    pausedRef.current = false;
    resumeRef.current?.(); resumeRef.current = null;
  };

  const sendHint = () => {
    const h = hint.trim();
    if (!h) return;
    setHint("");
    if (!agentOn) { void startAgent(h); return; }
    hintsRef.current.push(h);
    addLog({ kind: "hint", text: h });
    if (pausedRef.current) togglePause();
  };

  // ---------- Ergebnis ----------
  const mainHtml = files.find(([p]) => p === "/index.html")?.[0] || files.find(([p]) => /\.html?$/i.test(p))?.[0] || null;
  const downloadZip = () => {
    if (!files.length) { toast.error("Der PC ist noch leer"); return; }
    download(makeZip(files), "mythos-projekt.zip");
  };
  const saveAsApp = () => {
    if (!mainHtml) return;
    const html = buildPage(fs, mainHtml);
    const title = html.match(/<title>([^<]{1,80})<\/title>/i)?.[1]?.trim() || "Cloud-PC Projekt";
    saveMyApp({ title, html, source: "pc" });
    toast.success(`„${title}“ ist jetzt unter deinen Apps gespeichert`);
  };
  const resetPc = () => {
    if (agentOn || !confirm("Alle Dateien auf dem Cloud-PC löschen und neu starten?")) return;
    fs.reset();
    setSelected(null); setPage(null); setAddress(""); setLog([]); setSummary(null);
    setLines([{ text: "Cloud-PC neu gestartet.\n", kind: "info" }]);
    shell.cwd = "/";
  };

  const TabBtn = ({ id, icon: Icon, label }: { id: Tab; icon: typeof Monitor; label: string }) => (
    <button onClick={() => setTab(id)}
      className={`relative flex items-center gap-1.5 rounded-lg px-3 min-h-[40px] text-sm transition-colors ${tab === id ? "bg-white/15 text-white" : "text-muted-foreground hover:bg-white/5"}`}>
      <Icon className="h-4 w-4" />{label}
      {agentTab === id && <MousePointer2 className="h-3.5 w-3.5 text-fuchsia-300 animate-pulse" aria-label="Mythos arbeitet hier" />}
    </button>
  );

  return (
    <LabsShell icon={Monitor} title="Mythos Cloud-PC" wide
      subtitle="Mythos bekommt einen eigenen Computer mit Terminal, Dateien und Browser. Du schaust live zu und kannst jederzeit eingreifen.">
      {/* Aufgabe */}
      <section className="glass rounded-2xl p-3 md:p-4 space-y-3">
        <Textarea value={task} onChange={(e) => setTask(e.target.value)} rows={2} disabled={agentOn}
          onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); void startAgent(); } }}
          placeholder="Was soll Mythos auf seinem Computer für dich erledigen?" className="resize-none text-base" />
        <div className="flex flex-wrap gap-2">
          {!agentOn ? (
            <Button onClick={() => startAgent()} disabled={!task.trim()} className="min-h-[44px]"><Play className="h-4 w-4 mr-1.5" />Loslegen</Button>
          ) : (
            <>
              <Button onClick={togglePause} variant="secondary" className="min-h-[44px]">
                {paused ? <><Play className="h-4 w-4 mr-1.5" />Weiter</> : <><Hand className="h-4 w-4 mr-1.5" />Eingreifen</>}
              </Button>
              <Button onClick={stopAgent} variant="destructive" className="min-h-[44px]"><Square className="h-4 w-4 mr-1.5" />Stopp</Button>
            </>
          )}
          {!agentOn && EXAMPLES.map((ex) => (
            <Button key={ex.label} variant="outline" className="min-h-[44px]" onClick={() => setTask(ex.task)}>
              <Sparkles className="h-4 w-4 mr-1.5 text-fuchsia-300" />{ex.label}
            </Button>
          ))}
        </div>
      </section>

      {summary && (
        <section className="glass rounded-2xl p-4 space-y-3 border border-fuchsia-500/30">
          <div className="flex items-center gap-2 font-display"><CheckCircle2 className="h-5 w-5 text-emerald-400" />Fertig</div>
          <div className="text-sm"><MarkdownMessage content={summary} /></div>
          <div className="flex flex-wrap gap-2">
            {mainHtml && <Button className="min-h-[44px]" onClick={() => { setTab("browser"); void openTarget(mainHtml); }}><Globe className="h-4 w-4 mr-1.5" />Ansehen</Button>}
            <Button variant="secondary" className="min-h-[44px]" onClick={downloadZip}><Download className="h-4 w-4 mr-1.5" />Projekt als ZIP</Button>
            {mainHtml && <Button variant="secondary" className="min-h-[44px]" onClick={saveAsApp}><Save className="h-4 w-4 mr-1.5" />In meine Apps</Button>}
          </div>
        </section>
      )}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        {/* Bildschirm des PCs */}
        <section className="glass rounded-2xl overflow-hidden flex flex-col min-w-0">
          <div className="flex items-center gap-1 p-2 border-b border-white/10 overflow-x-auto">
            <TabBtn id="terminal" icon={TerminalSquare} label="Terminal" />
            <TabBtn id="files" icon={FolderOpen} label="Dateien" />
            <TabBtn id="browser" icon={Globe} label="Browser" />
            <span className="ml-auto flex items-center gap-1 shrink-0">
              <Button size="icon" variant="ghost" title="Projekt als ZIP herunterladen" aria-label="Projekt als ZIP herunterladen" onClick={downloadZip}><Download className="h-4 w-4" /></Button>
              <Button size="icon" variant="ghost" title="PC leeren" aria-label="PC leeren" onClick={resetPc} disabled={agentOn}><Trash2 className="h-4 w-4" /></Button>
            </span>
          </div>

          <div className="h-[58dvh] min-h-[340px] lg:h-[620px] relative">
            {/* Terminal */}
            <div className={`absolute inset-0 flex-col bg-black/70 font-mono text-[13px] leading-relaxed ${tab === "terminal" ? "flex" : "hidden"}`}
              onClick={() => document.getElementById("pc-term-input")?.focus()}>
              <div className="flex-1 overflow-y-auto p-3 whitespace-pre-wrap break-words">
                {lines.map((l, i) => (
                  <span key={i} className={l.kind === "err" ? "text-red-400" : l.kind === "info" ? "text-sky-300" : l.kind === "cmd" ? "text-emerald-300" : "text-zinc-200"}>{l.text}</span>
                ))}
                <div className="flex items-center gap-2" ref={termEnd}>
                  <span className="text-emerald-300 shrink-0">{shell.prompt()}</span>
                  <input id="pc-term-input" value={cmd} onChange={(e) => setCmd(e.target.value)} onKeyDown={onTermKey}
                    disabled={busy} autoComplete="off" autoCapitalize="off" spellCheck={false} aria-label="Befehl eingeben"
                    className="flex-1 min-w-0 bg-transparent outline-none text-zinc-100 disabled:opacity-100" />
                </div>
              </div>
              <div className="flex flex-wrap gap-2 p-2 border-t border-white/10 bg-black/40">
                {["help", "ls -l", "tree", "neofetch"].map((c) => (
                  <Button key={c} size="sm" variant="outline" disabled={busy} className="font-mono min-h-[36px]" onClick={() => runLine(c)}>{c}</Button>
                ))}
                {running && <Button size="sm" variant="destructive" className="min-h-[36px]" onClick={stopProgram}><Square className="h-3.5 w-3.5 mr-1" />Programm abbrechen</Button>}
              </div>
            </div>

            {/* Dateien + Editor */}
            <div className={`absolute inset-0 flex-col sm:flex-row ${tab === "files" ? "flex" : "hidden"}`}>
              <div className="sm:w-56 max-h-[35%] sm:max-h-none shrink-0 overflow-y-auto border-b sm:border-b-0 sm:border-r border-white/10 p-2 space-y-0.5">
                <Button size="sm" variant="outline" className="w-full min-h-[36px] mb-1" onClick={newFile}><FilePlus className="h-4 w-4 mr-1.5" />Neue Datei</Button>
                {!files.length && !dirs.length && <p className="text-xs text-muted-foreground p-2">Noch keine Dateien. Gib Mythos eine Aufgabe oder leg selbst los.</p>}
                {dirs.map((d) => (
                  <div key={d} className="flex items-center gap-1.5 px-2 py-1.5 text-sm text-muted-foreground truncate"><Folder className="h-4 w-4 shrink-0" />{d}</div>
                ))}
                {files.map(([p, c]) => (
                  <button key={p} onClick={() => { typingRef.current = false; setSelected(p); }}
                    className={`w-full flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-left text-sm min-h-[36px] ${selected === p ? "bg-white/15" : "hover:bg-white/5"}`}>
                    <FileText className="h-4 w-4 shrink-0 text-fuchsia-300" />
                    <span className="truncate flex-1" title={p}>{p.slice(1)}</span>
                    <span className="text-[10px] text-muted-foreground shrink-0">{formatSize(c.length)}</span>
                  </button>
                ))}
              </div>
              <div className="flex-1 min-w-0 min-h-0 flex flex-col">
                {selected ? (
                  <>
                    <div className="flex items-center gap-1 p-2 border-b border-white/10">
                      <span className="text-sm font-mono truncate flex-1">{selected}{dirty && " •"}</span>
                      <Button size="sm" variant="ghost" disabled={!dirty || busy} onClick={saveDraft} className="min-h-[36px]"><Save className="h-4 w-4 mr-1" />Speichern</Button>
                      <Button size="icon" variant="ghost" title="Im Browser öffnen" aria-label="Im Browser öffnen" onClick={() => { setTab("browser"); void openTarget(selected); }}><Globe className="h-4 w-4" /></Button>
                      <Button size="icon" variant="ghost" title="Löschen" aria-label="Datei löschen" disabled={busy} onClick={() => deleteFile(selected)}><Trash2 className="h-4 w-4" /></Button>
                    </div>
                    <textarea value={draft} readOnly={busy} spellCheck={false}
                      onChange={(e) => { typingRef.current = true; setDraft(e.target.value); }}
                      onKeyDown={(e) => { if ((e.ctrlKey || e.metaKey) && e.key === "s") { e.preventDefault(); saveDraft(); } }}
                      className="flex-1 min-h-0 w-full resize-none bg-black/60 p-3 font-mono text-[13px] leading-relaxed outline-none text-zinc-100" />
                  </>
                ) : (
                  <div className="flex-1 flex items-center justify-center text-sm text-muted-foreground p-6 text-center">Wähle links eine Datei aus.</div>
                )}
              </div>
            </div>

            {/* Browser */}
            <div className={`absolute inset-0 flex-col ${tab === "browser" ? "flex" : "hidden"}`}>
              <form className="flex items-center gap-1 p-2 border-b border-white/10"
                onSubmit={(e) => { e.preventDefault(); const t = address.trim(); if (t) void openTarget(/^(https?:|www\.|\/)/i.test(t) || fs.isFile(norm(t)) ? t : `https://${t}`).catch((err) => toast.error(err.message)); }}>
                <Button type="button" size="icon" variant="ghost" aria-label="Neu laden" disabled={!page} onClick={() => page && void openTarget(page.target).catch(() => {})}><RotateCw className="h-4 w-4" /></Button>
                <input value={address} onChange={(e) => setAddress(e.target.value)} placeholder="/index.html oder https://…" aria-label="Adresse"
                  className="flex-1 min-w-0 rounded-lg bg-white/5 px-3 min-h-[38px] text-sm outline-none focus:ring-1 focus:ring-fuchsia-400" />
                {page?.kind === "html" && (
                  <Button type="button" size="icon" variant="ghost" title="Im neuen Tab öffnen" aria-label="Im neuen Tab öffnen"
                    onClick={() => { try { openHtmlInTab(page.content, page.target); } catch (err) { toast.error((err as Error).message); } }}><ExternalLink className="h-4 w-4" /></Button>
                )}
                {page?.kind === "web" && (
                  <a href={/^https?:/i.test(page.target) ? page.target : `https://${page.target}`} target="_blank" rel="noreferrer noopener"
                    className="inline-flex h-10 w-10 items-center justify-center rounded-md hover:bg-white/10" title="Original öffnen" aria-label="Original öffnen"><ExternalLink className="h-4 w-4" /></a>
                )}
              </form>
              <div className="flex-1 min-h-0 overflow-auto bg-black/40">
                {!page && (
                  <div className="h-full flex flex-col items-center justify-center gap-3 p-6 text-center text-sm text-muted-foreground">
                    <Globe className="h-10 w-10 opacity-40" />
                    Hier erscheinen Websites, die Mythos baut, und Seiten aus dem Internet, die er liest.
                    {mainHtml && <Button variant="outline" onClick={() => void openTarget(mainHtml)}>{mainHtml} öffnen</Button>}
                  </div>
                )}
                {page?.loading && <div className="h-full flex items-center justify-center"><Loader2 className="h-6 w-6 animate-spin" /></div>}
                {page && !page.loading && page.kind === "html" && (
                  <iframe key={frameKey} title={page.target} srcDoc={safeHtml(page.content)} className="w-full h-full bg-white border-0"
                    sandbox="allow-scripts allow-forms allow-modals allow-pointer-lock" />
                )}
                {page && !page.loading && page.kind !== "html" && (
                  /\.md$/i.test(page.target) || page.kind === "web"
                    ? <div className="p-4 text-sm max-w-3xl"><MarkdownMessage content={page.content.slice(0, 60000)} /></div>
                    : <pre className="p-4 text-[13px] font-mono whitespace-pre-wrap break-words">{page.content}</pre>
                )}
              </div>
            </div>
          </div>
        </section>

        {/* Agent-Protokoll */}
        <aside className="glass rounded-2xl flex flex-col min-w-0 h-[50dvh] min-h-[320px] lg:h-auto lg:max-h-[690px]">
          <div className="flex items-center gap-2 p-3 border-b border-white/10">
            <span className={`h-2.5 w-2.5 rounded-full ${agentOn ? (paused ? "bg-amber-400" : "bg-emerald-400 animate-pulse") : "bg-zinc-500"}`} />
            <span className="font-display text-sm">{agentOn ? (paused ? "Pausiert – du hast die Kontrolle" : "Mythos arbeitet") : "Mythos wartet auf eine Aufgabe"}</span>
          </div>
          <div className="flex-1 overflow-y-auto p-3 space-y-2 text-sm">
            {!log.length && (
              <p className="text-muted-foreground text-xs leading-relaxed">
                Gib oben eine Aufgabe ein. Mythos plant, schreibt Dateien, führt Programme aus, liest Webseiten und testet sein Ergebnis.
                Alles läuft in deinem Browser und kostet nichts. Mit „Eingreifen“ hältst du ihn an und übernimmst selbst.
              </p>
            )}
            {log.map((item, i) => {
              if (item.kind === "user") return <div key={i} className="rounded-xl bg-fuchsia-500/15 px-3 py-2">{item.text}</div>;
              if (item.kind === "hint") return <div key={i} className="rounded-xl bg-amber-500/15 px-3 py-2"><span className="text-amber-300 text-xs block">Dein Hinweis</span>{item.text}</div>;
              if (item.kind === "plan") return <div key={i} className="text-zinc-200 pt-1">{item.text}</div>;
              if (item.kind === "info") return <div key={i} className={`text-xs ${item.error ? "text-red-400" : "text-muted-foreground"}`}>{item.text}</div>;
              const Icon = ACTION_ICON[item.action.type];
              return (
                <button key={i} onClick={() => setTab(TAB_OF[item.action.type])}
                  className="w-full text-left rounded-lg bg-white/5 hover:bg-white/10 px-2.5 py-2">
                  <span className="flex items-center gap-2 min-w-0">
                    <Icon className="h-4 w-4 shrink-0 text-fuchsia-300" />
                    <span className={`truncate flex-1 ${item.action.type === "run" ? "font-mono text-xs" : ""}`}>{actionLabel(item.action)}</span>
                    {item.status === "running" ? <Loader2 className="h-4 w-4 shrink-0 animate-spin" />
                      : item.status === "done" ? <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-400" />
                      : <XCircle className="h-4 w-4 shrink-0 text-red-400" />}
                  </span>
                  {item.status === "error" && item.result && <span className="block mt-1 text-xs text-red-300 line-clamp-3 font-mono">{item.result}</span>}
                </button>
              );
            })}
            {thinking && <div className="flex items-center gap-2 text-xs text-muted-foreground"><Loader2 className="h-3.5 w-3.5 animate-spin" />Mythos denkt nach …</div>}
            <div ref={logEnd} />
          </div>
          <form className="flex gap-2 p-2 border-t border-white/10" onSubmit={(e) => { e.preventDefault(); sendHint(); }}>
            <input value={hint} onChange={(e) => setHint(e.target.value)} aria-label="Hinweis an Mythos"
              placeholder={agentOn ? "Hinweis an Mythos, z. B. „Mach es blau“" : log.length ? "Weitere Aufgabe …" : "Aufgabe …"}
              className="flex-1 min-w-0 rounded-lg bg-white/5 px-3 min-h-[44px] text-sm outline-none focus:ring-1 focus:ring-fuchsia-400" />
            <Button type="submit" size="icon" className="h-11 w-11 shrink-0" disabled={!hint.trim()} aria-label="Senden"><Send className="h-4 w-4" /></Button>
          </form>
        </aside>
      </div>

      <p className="text-xs text-muted-foreground">
        Der Cloud-PC läuft komplett in deinem Browser (Python über WebAssembly, Webseiten über einen freien Lesedienst).
        Deine Dateien bleiben auf diesem Gerät. Programme können nichts außerhalb des PCs verändern.
      </p>
    </LabsShell>
  );
}
