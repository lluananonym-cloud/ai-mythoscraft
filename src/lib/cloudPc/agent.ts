// Mythos Cloud-PC: der Agent. Er bekommt eine Aufgabe, plant, schreibt Dateien, führt Befehle im Terminal aus,
// öffnet Seiten im Browser und prüft sein Ergebnis – Schritt für Schritt, damit man live zuschauen kann.
// KI-Aufrufe laufen über das vorhandene Mythos-Gateway (askAI in labs.ts), es braucht keinen neuen Server.
import { askAI } from "@/lib/labs";
import { formatSize, type Vfs } from "./vfs";

export type Action =
  | { type: "write"; path: string; content: string; cut?: boolean }
  | { type: "run"; cmd: string }
  | { type: "open"; target: string }
  | { type: "read"; path: string }
  | { type: "done"; summary: string };

export type AgentEvent =
  | { kind: "thinking"; step: number }
  | { kind: "plan"; text: string }
  | { kind: "action"; id: string; action: Action; status: "running" | "done" | "error"; result?: string }
  | { kind: "hint"; text: string }
  | { kind: "done"; summary: string }
  | { kind: "stopped" }
  | { kind: "error"; text: string };

export type Driver = {
  write: (path: string, content: string) => Promise<void>;
  run: (cmd: string) => Promise<{ code: number; output: string }>;
  /** Datei oder Webseite im PC-Browser öffnen; liefert, was der Agent davon "sieht". */
  open: (target: string) => Promise<string>;
  emit: (ev: AgentEvent) => void;
  stopped: () => boolean;
  waitIfPaused: () => Promise<void>;
  /** Hinweise, die der Nutzer während der Arbeit geschickt hat (werden dabei geleert). */
  takeHints: () => string[];
};

const SYSTEM = `Du bist Mythos, ein KI-Agent mit eigenem Computer: dem "Mythos Cloud-PC". Der Nutzer schaut dir live zu, wie du arbeitest.
Der PC hat ein Dateisystem (Hauptordner "/"), ein Terminal und einen Browser.

Terminal-Befehle: ls, cd, cat, tree, mkdir, rm -r, mv, cp, grep, head, wc,
  node datei.js (JavaScript wie Node.js, mit require für eigene Dateien, fs und path; KEIN npm),
  python datei.py (Python 3; numpy, pandas, matplotlib usw. werden beim import automatisch geladen; KEIN pip nötig),
  web <url> (liest eine Webseite als Text), suche <begriffe> (Websuche, liefert Titel, Links und Ausschnitte).

So antwortest du – IMMER in genau diesem Format, eine bis drei Aktionen pro Antwort:
<plan>Ein kurzer Satz für den Zuschauer, was du jetzt tust.</plan>
<aktion typ="schreiben" pfad="/index.html">
kompletter Dateiinhalt, ohne Markdown-Zäune
</aktion>
<aktion typ="befehl">node test.js</aktion>
<aktion typ="lesen" pfad="/daten.csv"></aktion>
<aktion typ="öffnen">/index.html</aktion>
<aktion typ="fertig">Zusammenfassung für den Nutzer: was entstanden ist und wie man es benutzt.</aktion>

Regeln:
- Wenn du die Ausgabe eines Befehls brauchst, beende die Antwort nach dem Befehl; du bekommst das Ergebnis in der nächsten Nachricht.
- Dateien immer vollständig schreiben (keine Auslassungen wie "..."). Große Projekte auf mehrere Dateien verteilen.
- Websites und Apps: index.html + style.css + script.js, modernes dunkles Design, mobil tauglich, Bedienung über Buttons.
  Bibliotheken nur per CDN (https://cdn.jsdelivr.net/...). Öffne die Seite am Ende mit "öffnen", damit der Nutzer sie sieht.
- Recherchen: erst "suche", dann wichtige Seiten mit "web" lesen, Ergebnis als gut lesbare Datei (z. B. /bericht.md) speichern.
- Teste Programme mit node oder python, bevor du fertig meldest. Fehler selbst beheben.
- "fertig" erst, wenn die Aufgabe wirklich erledigt ist. Antworte auf Deutsch, außer der Nutzer schreibt in einer anderen Sprache.`;

const TYPES: Record<string, Action["type"]> = {
  schreiben: "write", write: "write", datei: "write",
  befehl: "run", run: "run", terminal: "run", ausfuehren: "run", "ausführen": "run",
  "öffnen": "open", oeffnen: "open", open: "open", browser: "open",
  lesen: "read", read: "read",
  fertig: "done", done: "done",
};

/** Aktionen aus der KI-Antwort lesen. Nicht geschlossene Blöcke (abgeschnittene Antwort) werden markiert. */
export function parseReply(text: string): { plan: string; actions: Action[] } {
  const plan = (text.match(/<plan>([\s\S]*?)<\/plan>/i)?.[1] || "").trim();
  const actions: Action[] = [];
  const re = /<aktion\b([^>]*)>([\s\S]*?)(<\/aktion>|$)/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text))) {
    const attrs = Object.fromEntries([...m[1].matchAll(/(\w+)\s*=\s*["']([^"']*)["']/g)].map((x) => [x[1].toLowerCase(), x[2]]));
    const type = TYPES[(attrs.typ || attrs.type || "").toLowerCase()];
    let body = m[2].replace(/^\r?\n/, "").replace(/\r?\n$/, "");
    const fence = body.match(/^\s*```[\w-]*\n([\s\S]*?)\n?```\s*$/);
    if (fence) body = fence[1];
    const cut = !m[3];
    const path = attrs.pfad || attrs.path || attrs.datei || "";
    if (type === "write" && path) actions.push({ type, path, content: body, cut });
    else if (type === "read" && (path || body.trim())) actions.push({ type, path: path || body.trim() });
    else if (type === "run" && body.trim()) actions.push({ type, cmd: body.trim() });
    else if (type === "open") actions.push({ type, target: (body.trim() || path || "/index.html") });
    else if (type === "done") actions.push({ type, summary: body.trim() });
    if (cut) break;
  }
  return { plan, actions };
}

const clip = (s: string, n: number) => (s.length > n ? s.slice(0, n) + `\n… (${s.length - n} Zeichen gekürzt)` : s);

function fileList(fs: Vfs) {
  const files = fs.all();
  if (!files.length) return "(der PC ist leer)";
  return files.slice(0, 80).map(([p, c]) => `${p} (${formatSize(c.length)})`).join("\n") + (files.length > 80 ? `\n… und ${files.length - 80} weitere` : "");
}

export async function runAgent(task: string, fs: Vfs, d: Driver, maxSteps = 24): Promise<void> {
  const log: string[] = [];
  const hints: string[] = [];
  let emptyReplies = 0;
  let n = 0;
  for (let step = 1; step <= maxSteps; step++) {
    await d.waitIfPaused();
    if (d.stopped()) { d.emit({ kind: "stopped" }); return; }
    for (const h of d.takeHints()) { hints.push(h); log.push(`NUTZER-HINWEIS (hat Vorrang): ${h}`); }

    const older = log.slice(0, -8).map((l) => l.split("\n")[0].slice(0, 160));
    const recent = log.slice(-8).join("\n\n");
    const prompt = `AUFGABE DES NUTZERS:\n${task}\n${hints.length ? `\nHINWEISE DES NUTZERS WÄHREND DER ARBEIT:\n${hints.map((h) => "- " + h).join("\n")}\n` : ""}` +
      `\nDATEIEN AUF DEM PC:\n${fileList(fs)}\n` +
      (log.length ? `\nBISHER ERLEDIGT:\n${older.length ? older.join("\n") + "\n\n" : ""}${clip(recent, 9000)}\n` : "\nDu startest gerade. Leg los.\n") +
      `\nSchritt ${step} von höchstens ${maxSteps}. Antworte im vorgegebenen Format.`;

    d.emit({ kind: "thinking", step });
    let reply: string;
    try {
      reply = await askAI(SYSTEM, [{ role: "user", content: prompt }]);
    } catch (e) {
      d.emit({ kind: "error", text: (e as Error).message || "KI nicht erreichbar" });
      return;
    }
    if (d.stopped()) { d.emit({ kind: "stopped" }); return; }

    const { plan, actions } = parseReply(reply);
    if (plan) d.emit({ kind: "plan", text: plan });
    if (!actions.length) {
      if (++emptyReplies >= 3) { d.emit({ kind: "done", summary: reply.replace(/<[^>]+>/g, "").trim() || "Fertig." }); return; }
      log.push(`FEHLER: Deine Antwort enthielt keine <aktion>. Antworte nur im vorgegebenen Format.`);
      continue;
    }
    emptyReplies = 0;

    for (const a of actions.slice(0, 4)) {
      await d.waitIfPaused();
      if (d.stopped()) { d.emit({ kind: "stopped" }); return; }
      if (a.type === "done") { d.emit({ kind: "done", summary: a.summary || "Fertig." }); return; }
      const id = `a${++n}`;
      d.emit({ kind: "action", id, action: a, status: "running" });
      try {
        if (a.type === "write") {
          if (a.cut) throw new Error(`Deine Antwort wurde beim Schreiben von ${a.path} abgeschnitten. Schreibe die Datei kürzer oder teile sie in mehrere Dateien.`);
          await d.write(a.path, a.content);
          const res = `Gespeichert (${formatSize(a.content.length)})`;
          d.emit({ kind: "action", id, action: a, status: "done", result: res });
          log.push(`Datei geschrieben: ${a.path} (${a.content.split("\n").length} Zeilen)`);
        } else if (a.type === "run") {
          const r = await d.run(a.cmd);
          const res = clip(r.output.trim(), 2500) || "(keine Ausgabe)";
          d.emit({ kind: "action", id, action: a, status: r.code === 0 ? "done" : "error", result: res });
          log.push(`Befehl: ${a.cmd} → Exit-Code ${r.code}\n${res}`);
          if (r.code !== 0) break;
        } else if (a.type === "read") {
          const c = fs.read(a.path);
          d.emit({ kind: "action", id, action: a, status: "done", result: `${c.split("\n").length} Zeilen gelesen` });
          log.push(`Inhalt von ${a.path}:\n${clip(c, 6000)}`);
        } else if (a.type === "open") {
          const seen = await d.open(a.target);
          d.emit({ kind: "action", id, action: a, status: "done", result: clip(seen, 300) });
          log.push(`Im Browser geöffnet: ${a.target}\n${clip(seen, 3000)}`);
        }
      } catch (e) {
        const msg = (e as Error).message || String(e);
        d.emit({ kind: "action", id, action: a, status: "error", result: msg });
        log.push(`FEHLER bei ${a.type} ${"path" in a ? a.path : "cmd" in a ? a.cmd : ""}: ${msg}`);
        break;
      }
    }
  }
  d.emit({ kind: "done", summary: "Ich habe die maximale Anzahl an Schritten erreicht. Schau dir das Ergebnis an und gib mir einen Hinweis, wenn ich weitermachen soll." });
}
