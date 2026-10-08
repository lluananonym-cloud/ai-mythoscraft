import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import LabsShell from "@/components/labs/LabsShell";
import AppPreview from "@/components/labs/AppPreview";
import MyApps from "@/components/labs/MyApps";
import MarkdownMessage from "@/components/MarkdownMessage";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Users, Loader2, Play, Sparkles, Send, Square } from "lucide-react";
import { toast } from "sonner";
import { APP_RULES, askAI, extractHtml, extractJson, listMyApps, saveMyApp, type MyApp } from "@/lib/labs";

// Gleiche Idee wie der Multi-Agent-Modus der Coding-App (src/lib/mythos-code-app/renderer.js):
// mehrere Agenten mit eigener Rolle, deren Fortschritt live sichtbar ist.
type Role = "designer" | "coder" | "tester";
const ROLES: Record<Role, { name: string; job: string; emoji: string; color: string }> = {
  designer: { name: "Dana", job: "Designerin", emoji: "🎨", color: "from-pink-500/30 to-rose-500/20" },
  coder: { name: "Kai", job: "Programmierer", emoji: "💻", color: "from-sky-500/30 to-indigo-500/20" },
  tester: { name: "Tom", job: "Tester", emoji: "🧪", color: "from-emerald-500/30 to-teal-500/20" },
};
type Status = "wartet" | "arbeitet" | "fertig";
type Msg = { role: Role | "user"; text: string; html?: string };

const DESIGNER = `Du bist Dana, Designerin in einem KI-Team. Du planst das Aussehen einer Web-App, bevor Kai sie programmiert.
Antworte auf Deutsch, locker und kurz (max. 8 Stichpunkte): Farben, Aufbau, welche Buttons, wie es auf dem Handy aussieht. Sprich Kai direkt an.`;
const CODER = `Du bist Kai, Programmierer in einem KI-Team. Setze den Plan der Designerin exakt um.
${APP_RULES}`;
const TESTER = `Du bist Tom, Tester in einem KI-Team. Prüfe den HTML-Code gründlich: Fehler im JavaScript, Buttons ohne Funktion, Überlappungen auf dem Handy, fehlende Teile.
Antworte NUR mit JSON: {"ok":true|false,"comment":"ein lockerer Satz an das Team","issues":["konkretes Problem 1", "..."]}. Höchstens 5 Probleme, nur echte.`;

export default function KiTeam() {
  const [idea, setIdea] = useState("");
  const [msgs, setMsgs] = useState<Msg[]>([]);
  const [status, setStatus] = useState<Record<Role, Status>>({ designer: "wartet", coder: "wartet", tester: "wartet" });
  const [busy, setBusy] = useState(false);
  const [app, setApp] = useState<MyApp | null>(null);
  const [preview, setPreview] = useState<MyApp | null>(null);
  const [change, setChange] = useState("");
  const [refresh, setRefresh] = useState(0);
  const stopRef = useRef(false);
  const endRef = useRef<HTMLDivElement>(null);
  const [params] = useSearchParams();

  // Aus dem Marktplatz übernommene App: direkt mit Änderungswünschen weiterbauen.
  useEffect(() => {
    const id = params.get("app");
    const found = id ? listMyApps().find((a) => a.id === id) : null;
    if (!found) return;
    setApp(found);
    setMsgs([
      { role: "user", text: `„${found.title}“ übernommen` },
      { role: "designer", text: "Coole App! Schreib unten, was wir daran ändern sollen, und wir legen los.", html: found.html },
    ]);
  }, [params]);

  const add = (m: Msg) => { setMsgs((x) => [...x, m]); setTimeout(() => endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" }), 50); };
  const set = (r: Role, s: Status) => setStatus((x) => ({ ...x, [r]: s }));
  const stopped = () => { if (stopRef.current) throw new Error("Gestoppt"); };

  /** Tom prüft, Kai bessert einmal nach. */
  const testAndFix = async (task: string, html: string): Promise<string> => {
    set("tester", "arbeitet");
    const review = extractJson<{ ok?: boolean; comment?: string; issues?: string[] }>(
      await askAI(TESTER, [{ role: "user", content: `Aufgabe: ${task}\n\nCode:\n${html.slice(0, 60000)}` }], "google/gemini-3.6-flash"));
    stopped();
    const issues = (review?.issues || []).filter(Boolean).slice(0, 5);
    add({ role: "tester", text: (review?.comment || (issues.length ? "Mir sind ein paar Sachen aufgefallen:" : "Sieht gut aus!")) + (issues.length ? "\n\n" + issues.map((i) => "- " + i).join("\n") : "") });
    set("tester", "fertig");
    if (!issues.length || review?.ok) return html;
    set("coder", "arbeitet");
    const fixed = extractHtml(await askAI(CODER, [{ role: "user", content: `Behebe diese Probleme vom Tester:\n${issues.map((i) => "- " + i).join("\n")}\n\nAktueller Code:\n${html}` }], "google/gemini-3.6-flash"));
    stopped();
    set("coder", "fertig");
    if (!fixed) return html;
    add({ role: "coder", text: "Erledigt, ich habe Toms Punkte behoben. 👍", html: fixed });
    return fixed;
  };

  const finish = (title: string, html: string, id?: string) => {
    const saved = saveMyApp({ id, title, html, source: "team" });
    setApp(saved); setRefresh((n) => n + 1);
    toast.success("Das Team ist fertig 🎉", { action: { label: "Öffnen", onClick: () => setPreview(saved) } });
  };

  const run = async () => {
    const task = idea.trim();
    if (!task) return;
    stopRef.current = false;
    setBusy(true); setApp(null); setMsgs([{ role: "user", text: task }]);
    setStatus({ designer: "arbeitet", coder: "wartet", tester: "wartet" });
    try {
      const design = await askAI(DESIGNER, [{ role: "user", content: task }], "google/gemini-3.6-flash");
      stopped();
      add({ role: "designer", text: design }); set("designer", "fertig"); set("coder", "arbeitet");
      let html = extractHtml(await askAI(CODER, [{ role: "user", content: `Projekt: ${task}\n\nPlan von Dana:\n${design}` }], "google/gemini-3.6-flash"));
      stopped();
      if (!html) throw new Error("Kai hat keinen Code geliefert. Bitte nochmal versuchen.");
      add({ role: "coder", text: "Hier ist die erste Version! Tom, schaust du drüber?", html }); set("coder", "fertig");
      html = await testAndFix(task, html);
      const title = html.match(/<title>([^<]{1,60})<\/title>/i)?.[1]?.trim() || task.slice(0, 40);
      finish(title, html);
    } catch (e) {
      const m = (e as Error).message;
      if (m !== "Gestoppt") toast.error(m);
      setStatus((s) => Object.fromEntries(Object.entries(s).map(([k, v]) => [k, v === "arbeitet" ? "wartet" : v])) as Record<Role, Status>);
    } finally { setBusy(false); }
  };

  const requestChange = async () => {
    const wish = change.trim();
    if (!wish || !app) return;
    stopRef.current = false;
    setBusy(true); setChange("");
    add({ role: "user", text: wish });
    try {
      set("coder", "arbeitet");
      let html = extractHtml(await askAI(CODER, [{ role: "user", content: `Änderungswunsch: ${wish}\n\nAktueller Code:\n${app.html}` }], "google/gemini-3.6-flash"));
      stopped();
      if (!html) throw new Error("Kai hat keinen Code geliefert.");
      add({ role: "coder", text: "Ist eingebaut!", html }); set("coder", "fertig");
      html = await testAndFix(wish, html);
      finish(app.title, html, app.source === "team" ? app.id : undefined);
    } catch (e) {
      const m = (e as Error).message;
      if (m !== "Gestoppt") toast.error(m);
    } finally { setBusy(false); }
  };

  return (
    <LabsShell icon={Users} title="KI-Team" subtitle="Designerin, Programmierer und Tester bauen gemeinsam deine App und besprechen sich dabei im Chat.">
      <section className="grid grid-cols-3 gap-2">
        {(Object.keys(ROLES) as Role[]).map((r) => (
          <div key={r} className={`rounded-xl bg-gradient-to-br ${ROLES[r].color} p-2.5 sm:p-3 text-center min-w-0`}>
            <div className="text-2xl">{ROLES[r].emoji}</div>
            <div className="text-xs font-medium truncate">{ROLES[r].name}</div>
            <div className="text-[10px] sm:text-[11px] text-foreground/70 truncate">{ROLES[r].job}</div>
            <div className="text-[10px] sm:text-[11px] text-muted-foreground flex items-center justify-center gap-1">
              {status[r] === "arbeitet" && <Loader2 className="h-3 w-3 animate-spin" />}{status[r]}
            </div>
          </div>
        ))}
      </section>

      {!msgs.length && (
        <section className="glass-strong rounded-2xl p-4 md:p-5 space-y-3">
          <Textarea value={idea} onChange={(e) => setIdea(e.target.value)} maxLength={1500} className="min-h-[110px] text-base"
            placeholder="Was soll das Team bauen? Z. B. „Eine Hausaufgaben-App mit Fächern, Fälligkeitsdatum und Häkchen“" />
          <Button onClick={run} disabled={busy || !idea.trim()} className="w-full h-11 bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white">
            <Sparkles className="h-4 w-4 mr-2" />Team loslegen lassen
          </Button>
        </section>
      )}

      {msgs.length > 0 && (
        <section className="space-y-3">
          {msgs.map((m, i) => m.role === "user" ? (
            <div key={i} className="flex justify-end"><div className="max-w-[85%] rounded-2xl bg-white/10 px-4 py-2.5 text-sm break-words">{m.text}</div></div>
          ) : (
            <div key={i} className="flex gap-2.5 min-w-0">
              <span className={`shrink-0 flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br ${ROLES[m.role].color} text-lg`}>{ROLES[m.role].emoji}</span>
              <div className="glass rounded-2xl px-4 py-3 min-w-0 flex-1">
                <div className="text-xs text-muted-foreground mb-1">{ROLES[m.role].name} · {ROLES[m.role].job}</div>
                <div className="text-sm"><MarkdownMessage content={m.text} /></div>
                {m.html && <Button size="sm" variant="outline" className="mt-2 h-9" onClick={() => setPreview({ id: "v" + i, title: "Zwischenstand", html: m.html!, source: "team", created_at: "" })}><Play className="h-3.5 w-3.5 mr-1.5" />Diese Version ansehen</Button>}
              </div>
            </div>
          ))}
          <div ref={endRef} />
          {busy && (
            <Button variant="outline" className="w-full h-11" onClick={() => { stopRef.current = true; }}><Square className="h-4 w-4 mr-2" />Stoppen</Button>
          )}
          {!busy && app && (
            <div className="glass-strong rounded-2xl p-3 space-y-2">
              <Button onClick={() => setPreview(app)} className="w-full h-11 bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white"><Play className="h-4 w-4 mr-2" />Fertige App öffnen</Button>
              <div className="flex gap-2">
                <Textarea value={change} onChange={(e) => setChange(e.target.value)} maxLength={800} placeholder="Änderung wünschen …" className="min-h-[44px] h-11 flex-1 resize-none" />
                <Button onClick={requestChange} disabled={!change.trim()} className="h-11 w-11 shrink-0" size="icon" aria-label="Änderung senden"><Send className="h-4 w-4" /></Button>
              </div>
            </div>
          )}
          {!busy && (
            <Button variant="ghost" className="w-full h-11" onClick={() => { setMsgs([]); setApp(null); setIdea(""); setStatus({ designer: "wartet", coder: "wartet", tester: "wartet" }); }}>Neues Projekt</Button>
          )}
        </section>
      )}

      <MyApps source="team" refresh={refresh} onOpen={setPreview} />
      <AppPreview app={preview} onClose={() => setPreview(null)} />
    </LabsShell>
  );
}
