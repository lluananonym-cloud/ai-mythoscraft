// App-Builder im Stil von Lovable und Base44: links der Chat, rechts die Live-Vorschau und der Code.
// Die KI baut eine komplette, eigenständige index.html und ändert sie bei jeder weiteren Nachricht.
// Projekte liegen im Browser (localStorage) des jeweiligen Kontos.
import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import {
  ArrowLeft, ArrowUp, Check, Code2, Copy, Download, ExternalLink, Eye, FileCode2, Loader2, MessageSquare,
  Monitor, Plus, RefreshCw, RotateCcw, Smartphone, Square, Trash2,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import { isAllowed } from "@/lib/mythosModels";
import { copyText } from "@/lib/copyText";
import { CONTINUE_PROMPT, MAX_CONTINUE, looksCut, stitch } from "@/lib/codeContinue";

type BMsg = { role: "user" | "assistant"; content: string; html?: string; at: string };
type Project = { id: string; name: string; html: string; messages: BMsg[]; updated: string };

const EXAMPLES = [
  "Eine Landingpage für mein Café mit Speisekarte und Kontaktformular",
  "Ein Snake-Spiel mit Highscore",
  "Eine To-do-App mit Kategorien und Dunkelmodus",
  "Ein Portfolio für eine Fotografin mit Bildergalerie",
];

const SYSTEM = `Du bist der App-Builder von Mythos AI, vergleichbar mit Lovable oder Base44.
Du baust Web-Apps als EINE eigenständige Datei index.html.
Regeln:
- Antworte zuerst mit höchstens zwei kurzen Sätzen, was du baust oder änderst.
- Danach kommt genau EIN Codeblock \`\`\`html mit der KOMPLETTEN Datei, von <!DOCTYPE html> bis </html>. Nie kürzen, nie „…“ oder „Rest bleibt gleich“.
- Alles in einer Datei: CSS in <style>, JavaScript in <script>. Erlaubt sind CDN-Bibliotheken (z. B. Tailwind über https://cdn.tailwindcss.com).
- Modernes, sauberes Design, responsiv fürs Handy, sinnvolle Beispielinhalte statt Platzhaltertext.
- Bilder: nutze https://picsum.photos/seed/<wort>/800/600 oder Emojis/SVG, keine erfundenen Dateien.
- Daten speicherst du bei Bedarf in localStorage.
- Bei Änderungswünschen änderst du die bestehende Datei und gibst wieder die komplette Datei aus.
- Keine Erklärung des Codes nach dem Block.`;

const key = (uid?: string) => `mythos.build.${uid ?? "gast"}`;
const now = () => new Date().toISOString();
const newId = () => Math.random().toString(36).slice(2, 10);

function load(uid?: string): Project[] {
  try { return JSON.parse(localStorage.getItem(key(uid)) || "[]"); } catch { return []; }
}
function save(uid: string | undefined, list: Project[]) {
  try { localStorage.setItem(key(uid), JSON.stringify(list.slice(0, 30))); } catch { toast.error("Speicher voll: alte Projekte löschen"); }
}

/** HTML aus der Antwort holen, auch wenn der Block (noch) nicht geschlossen ist. */
function extractHtml(text: string): string | null {
  const m = /```(?:html)?[^\n]*\n([\s\S]*?)(?:```|$)/i.exec(text);
  const code = m?.[1]?.trim();
  if (code && /<(!doctype|html|body|div|script|style)/i.test(code)) return code;
  const raw = /<!doctype html[\s\S]*/i.exec(text)?.[0];
  return raw ? raw.replace(/```\s*$/, "").trim() : null;
}
/** Die Vorschau läuft abgeschottet (ohne Zugriff auf dein Konto). Dort fehlt localStorage, darum ein Ersatz im Speicher. */
const STORAGE_SHIM = `<script>try{localStorage.length}catch(e){var m={},s={getItem:function(k){return k in m?m[k]:null},setItem:function(k,v){m[k]=String(v)},removeItem:function(k){delete m[k]},clear:function(){m={}},key:function(i){return Object.keys(m)[i]||null},get length(){return Object.keys(m).length}};Object.defineProperty(window,"localStorage",{value:s});Object.defineProperty(window,"sessionStorage",{value:s})}</script>`;
const withShim = (h: string) => (/<head[^>]*>/i.test(h) ? h.replace(/<head[^>]*>/i, (t) => t + STORAGE_SHIM) : STORAGE_SHIM + h);

/** Text der Antwort ohne den Codeblock (für die Chat-Blase). */
const proseOf = (text: string) => text.replace(/```[\s\S]*?(```|$)/g, "").replace(/<!doctype html[\s\S]*/i, "").trim();

function downloadFile(name: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: "text/html;charset=utf-8" }));
  const a = document.createElement("a");
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export default function Build() {
  const { user } = useAuth();
  const sub = useSubscription();
  const [projects, setProjects] = useState<Project[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState(""); // gerade gestreamte Antwort
  const [tab, setTab] = useState<"chat" | "preview" | "code">("chat"); // nur Handy
  const [view, setView] = useState<"preview" | "code">("preview"); // Desktop rechts
  const [device, setDevice] = useState<"desktop" | "phone">("desktop");
  const [reloadKey, setReloadKey] = useState(0);
  const [copied, setCopied] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => { setProjects(load(user?.id)); }, [user?.id]);
  const active = projects.find((p) => p.id === activeId) ?? null;
  const liveHtml = busy ? extractHtml(live) : null;
  const html = liveHtml ?? active?.html ?? "";
  // Vorschau erst aktualisieren, wenn die Datei fertig ist (halbe Seiten flackern sonst).
  const previewHtml = busy && liveHtml && !/<\/html>/i.test(liveHtml) ? active?.html ?? "" : html;

  useEffect(() => { endRef.current?.scrollIntoView({ block: "end" }); }, [active?.messages.length, live]);

  const update = (id: string, fn: (p: Project) => Project) => setProjects((list) => {
    const next = list.map((p) => (p.id === id ? fn(p) : p)).sort((a, b) => b.updated.localeCompare(a.updated));
    save(user?.id, next);
    return next;
  });

  const model = isAllowed("code15:high", sub.tier) ? "code15:high" : "v1:normal";

  /** Ein gestreamter Aufruf der Chat-Funktion; liefert den Text. */
  const call = async (messages: { role: string; content: string }[], prefix: string, ctrl: AbortController) => {
    const resp = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/functions/v1/chat`, {
      method: "POST",
      signal: ctrl.signal,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}` },
      body: JSON.stringify({ userId: user?.id, mythos: model, mode: "general", build: true, messages }),
    });
    if (resp.status === 429) throw new Error("Zu viele Anfragen. Warte kurz.");
    if (!resp.ok || !resp.body) throw new Error(`Server-Fehler ${resp.status}`);
    const reader = resp.body.getReader();
    const dec = new TextDecoder();
    let buf = "", out = "";
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += dec.decode(value, { stream: true });
      let i;
      while ((i = buf.indexOf("\n")) !== -1) {
        const line = buf.slice(0, i).replace(/\r$/, ""); buf = buf.slice(i + 1);
        if (!line.startsWith("data: ")) continue;
        const json = line.slice(6).trim();
        if (json === "[DONE]") continue;
        try {
          const c = JSON.parse(json).choices?.[0]?.delta?.content;
          if (c) { out += c; setLive(prefix + stitch(prefix, out)); }
        } catch { /* unvollständige Zeile */ }
      }
    }
    return out;
  };

  const send = async (text = input) => {
    const prompt = text.trim();
    if (!prompt || busy) return;
    let proj = active;
    if (!proj) {
      proj = { id: newId(), name: prompt.slice(0, 40), html: "", messages: [], updated: now() };
      const next = [proj, ...projects]; setProjects(next); save(user?.id, next); setActiveId(proj.id);
    }
    const id = proj.id;
    const userMsg: BMsg = { role: "user", content: prompt, at: now() };
    update(id, (p) => ({ ...p, messages: [...p.messages, userMsg], updated: now() }));
    setInput(""); setBusy(true); setLive(""); setTab("chat");

    // Verlauf ohne alte Codeblöcke, dafür die aktuelle Datei: spart Platz und die KI kennt immer den neuesten Stand.
    const history = [...proj.messages, userMsg].slice(-10).map((m) => ({ role: m.role, content: m.role === "assistant" ? proseOf(m.content) || "Datei aktualisiert." : m.content }));
    const ctx = proj.html ? `\n\nAktuelle index.html:\n\`\`\`html\n${proj.html}\n\`\`\`` : "";
    const messages = [{ role: "system", content: SYSTEM + ctx }, ...history];
    const ctrl = new AbortController(); abortRef.current = ctrl;
    let full = "";
    try {
      full = await call(messages, "", ctrl);
      for (let k = 0; k < MAX_CONTINUE && looksCut(full) && !ctrl.signal.aborted; k++) {
        const more = await call([...messages, { role: "assistant", content: full }, { role: "user", content: CONTINUE_PROMPT }], full, ctrl);
        if (!more) break;
        full += stitch(full, more);
      }
    } catch (e) {
      if ((e as Error).name !== "AbortError") toast.error((e as Error).message || "Verbindungsfehler");
    }
    const newHtml = extractHtml(full);
    if (full) {
      const done = !!newHtml && /<\/html>/i.test(newHtml);
      if (newHtml && !done) toast.warning("Die Datei ist noch nicht ganz fertig. Schreib „mach weiter“.");
      update(id, (p) => ({
        ...p,
        html: newHtml ?? p.html,
        messages: [...p.messages, { role: "assistant", content: full, html: newHtml ?? undefined, at: now() }],
        updated: now(),
      }));
      if (newHtml && window.matchMedia("(max-width: 1023px)").matches) setTab("preview");
    }
    setLive(""); setBusy(false); abortRef.current = null;
  };

  const doCopy = async () => {
    if (!html) return;
    if (await copyText(html)) { setCopied(true); setTimeout(() => setCopied(false), 1500); } else toast.error("Kopieren fehlgeschlagen");
  };
  const openTab = () => {
    const url = URL.createObjectURL(new Blob([html], { type: "text/html;charset=utf-8" }));
    window.open(url, "_blank", "noopener");
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };
  const remove = (id: string) => {
    const next = projects.filter((p) => p.id !== id); setProjects(next); save(user?.id, next);
    if (activeId === id) setActiveId(null);
  };

  const Btn = ({ onClick, title, children, disabled }: { onClick: () => void; title: string; children: React.ReactNode; disabled?: boolean }) => (
    <button onClick={onClick} title={title} aria-label={title} disabled={disabled}
      className="rounded-lg p-2 text-muted-foreground hover:bg-white/5 hover:text-foreground disabled:opacity-40 touch-manipulation">{children}</button>
  );

  const composer = (big = false) => (
    <form onSubmit={(e) => { e.preventDefault(); send(); }}
      className={`rounded-3xl border border-white/10 bg-white/[0.04] p-3 ${big ? "shadow-[0_8px_40px_hsl(0_0%_0%/0.4)]" : ""}`}>
      <textarea
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey && !window.matchMedia("(pointer: coarse)").matches) { e.preventDefault(); send(); } }}
        rows={big ? 3 : 2}
        placeholder={active ? "Was soll sich ändern?" : "Beschreib deine App…"}
        className="w-full resize-none bg-transparent px-1 text-[16px] placeholder:text-muted-foreground focus:outline-none"
      />
      <div className="flex items-center justify-between">
        <span className="px-1 text-xs text-muted-foreground">{model === "code15:high" ? "MythosCode v1.5" : "Mythos v1"}</span>
        {busy ? (
          <button type="button" onClick={() => abortRef.current?.abort()} aria-label="Stoppen" className="flex h-9 w-9 items-center justify-center rounded-full bg-foreground text-background">
            <Square className="h-3.5 w-3.5 fill-current" />
          </button>
        ) : (
          <button type="submit" disabled={!input.trim()} aria-label="Senden" className="flex h-9 w-9 items-center justify-center rounded-full bg-foreground text-background disabled:opacity-30">
            <ArrowUp className="h-4 w-4" />
          </button>
        )}
      </div>
    </form>
  );

  // Startseite: noch kein Projekt gewählt
  if (!active) {
    return (
      <div className="min-h-screen bg-background text-foreground">
        <header className="flex items-center gap-2 px-4 py-3">
          <Link to="/app" className="rounded-lg p-2 text-muted-foreground hover:bg-white/5 hover:text-foreground" aria-label="Zurück"><ArrowLeft className="h-4 w-4" /></Link>
          <span className="font-medium">App-Builder</span>
        </header>
        <main className="mx-auto max-w-2xl px-4 pb-16 pt-10 sm:pt-20">
          <h1 className="mb-2 text-center font-serif text-4xl sm:text-5xl">Was willst du bauen?</h1>
          <p className="mb-8 text-center text-muted-foreground">Beschreib deine Idee. Mythos baut die App und zeigt sie dir sofort live.</p>
          {composer(true)}
          <div className="mt-4 flex flex-wrap justify-center gap-2">
            {EXAMPLES.map((e) => (
              <button key={e} onClick={() => send(e)} className="rounded-full border border-white/15 bg-white/[0.03] px-4 py-2 text-[13px] text-foreground/90 hover:bg-white/[0.08]">{e}</button>
            ))}
          </div>
          {projects.length > 0 && (
            <section className="mt-14">
              <h2 className="mb-3 text-sm text-muted-foreground">Deine Projekte</h2>
              <div className="grid gap-3 sm:grid-cols-2">
                {projects.map((p) => (
                  <div key={p.id} className="group overflow-hidden rounded-2xl border border-white/10 bg-white/[0.03]">
                    <button onClick={() => setActiveId(p.id)} className="block w-full text-left">
                      <div className="pointer-events-none h-36 overflow-hidden bg-white">
                        {p.html && <iframe title={p.name} srcDoc={p.html} sandbox="" className="origin-top-left border-0" style={{ width: "400%", height: 576, transform: "scale(0.25)" }} tabIndex={-1} />}
                      </div>
                      <div className="px-3 py-2 text-sm">{p.name}<div className="text-xs text-muted-foreground">{new Date(p.updated).toLocaleString("de-DE", { dateStyle: "short", timeStyle: "short" })}</div></div>
                    </button>
                    <div className="flex justify-end px-2 pb-2"><Btn onClick={() => remove(p.id)} title="Projekt löschen"><Trash2 className="h-4 w-4" /></Btn></div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </main>
      </div>
    );
  }

  const chatPane = (
    <div className="flex h-full min-h-0 flex-col">
      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-4">
        {active.messages.map((m, i) => m.role === "user" ? (
          <div key={i} className="ml-auto max-w-[85%] whitespace-pre-wrap rounded-2xl bg-white/[0.08] px-4 py-2.5 text-[15px]">{m.content}</div>
        ) : (
          <div key={i} className="space-y-2 text-[15px]">
            {proseOf(m.content) && <p className="whitespace-pre-wrap text-foreground/90">{proseOf(m.content)}</p>}
            {m.html && (
              <div className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2 text-sm">
                <FileCode2 className="h-4 w-4 text-muted-foreground" />
                <span className="flex-1">index.html {active.html === m.html ? "(aktuell)" : ""}</span>
                {active.html !== m.html && (
                  <button onClick={() => update(active.id, (p) => ({ ...p, html: m.html!, updated: now() }))} className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
                    <RotateCcw className="h-3.5 w-3.5" /> Wiederherstellen
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
        {busy && (
          <div className="space-y-2 text-[15px]">
            {proseOf(live) && <p className="whitespace-pre-wrap text-foreground/90">{proseOf(live)}</p>}
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span className="mythos-shimmer">{liveHtml ? `Schreibt index.html (${liveHtml.split("\n").length} Zeilen)` : "Plant die App…"}</span>
            </div>
          </div>
        )}
        <div ref={endRef} />
      </div>
      <div className="p-3">{composer()}</div>
    </div>
  );

  const previewPane = (
    <div className="flex h-full min-h-0 items-start justify-center overflow-auto bg-[hsl(0_0%_6%)] p-0 lg:p-3">
      {previewHtml ? (
        <iframe
          key={reloadKey}
          title="Vorschau"
          srcDoc={withShim(previewHtml)}
          sandbox="allow-scripts allow-forms allow-modals allow-popups"
          className={`h-full border-0 bg-white ${device === "phone" ? "w-[390px] max-w-full rounded-[28px] border-[6px] border-black lg:h-[780px]" : "w-full lg:rounded-xl"}`}
        />
      ) : (
        <div className="m-auto flex flex-col items-center gap-2 text-sm text-muted-foreground">
          {busy ? <Loader2 className="h-5 w-5 animate-spin" /> : <Eye className="h-5 w-5" />}
          {busy ? "Die Vorschau erscheint, sobald die Datei fertig ist." : "Noch keine Vorschau."}
        </div>
      )}
    </div>
  );

  const codePane = (
    <pre className="h-full min-h-0 overflow-auto bg-[hsl(0_0%_6%)] p-4 font-mono text-[12.5px] leading-relaxed text-foreground/90"><code>{html || "// Noch kein Code"}</code></pre>
  );

  const tools = (
    <div className="flex items-center gap-0.5">
      <Btn onClick={() => setReloadKey((k) => k + 1)} title="Vorschau neu laden" disabled={!html}><RefreshCw className="h-4 w-4" /></Btn>
      <Btn onClick={() => setDevice((d) => (d === "phone" ? "desktop" : "phone"))} title={device === "phone" ? "Desktop-Ansicht" : "Handy-Ansicht"}>
        {device === "phone" ? <Monitor className="h-4 w-4" /> : <Smartphone className="h-4 w-4" />}
      </Btn>
      <Btn onClick={doCopy} title="Code kopieren" disabled={!html}>{copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}</Btn>
      <Btn onClick={() => downloadFile("index.html", html)} title="index.html herunterladen" disabled={!html}><Download className="h-4 w-4" /></Btn>
      <Btn onClick={openTab} title="In neuem Tab öffnen" disabled={!html}><ExternalLink className="h-4 w-4" /></Btn>
    </div>
  );

  const seg = (v: string, cur: string, set: () => void, Icon: typeof Eye, label: string) => (
    <button onClick={set} className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm ${v === cur ? "bg-white/10 text-foreground" : "text-muted-foreground hover:text-foreground"}`}>
      <Icon className="h-4 w-4" /> {label}
    </button>
  );

  return (
    <div className="flex h-[100dvh] flex-col bg-background text-foreground">
      <header className="flex items-center gap-2 border-b border-white/10 px-2 py-1.5 sm:px-3">
        <button onClick={() => setActiveId(null)} className="rounded-lg p-2 text-muted-foreground hover:bg-white/5 hover:text-foreground" aria-label="Alle Projekte"><ArrowLeft className="h-4 w-4" /></button>
        <span className="min-w-0 flex-1 truncate text-sm font-medium">{active.name}</span>
        <div className="hidden items-center gap-1 lg:flex">
          {seg("preview", view, () => setView("preview"), Eye, "Vorschau")}
          {seg("code", view, () => setView("code"), Code2, "Code")}
        </div>
        <div className="hidden lg:block">{tools}</div>
        <Btn onClick={() => { setActiveId(null); setInput(""); }} title="Neues Projekt"><Plus className="h-4 w-4" /></Btn>
      </header>

      {/* Handy: Umschalter Chat / Vorschau / Code */}
      <div className="flex items-center justify-between gap-1 border-b border-white/10 px-2 py-1.5 lg:hidden">
        <div className="flex gap-1">
          {seg("chat", tab, () => setTab("chat"), MessageSquare, "Chat")}
          {seg("preview", tab, () => setTab("preview"), Eye, "Vorschau")}
          {seg("code", tab, () => setTab("code"), Code2, "Code")}
        </div>
        {tab !== "chat" && tools}
      </div>

      <div className="flex min-h-0 flex-1">
        <aside className={`min-h-0 w-full border-white/10 lg:block lg:w-[420px] lg:shrink-0 lg:border-r ${tab === "chat" ? "block" : "hidden"}`}>{chatPane}</aside>
        <section className={`min-h-0 flex-1 lg:block ${tab === "chat" ? "hidden" : "block"}`}>
          <div className="h-full lg:hidden">{tab === "code" ? codePane : previewPane}</div>
          <div className="hidden h-full lg:block">{view === "code" ? codePane : previewPane}</div>
        </section>
      </div>
    </div>
  );
}
