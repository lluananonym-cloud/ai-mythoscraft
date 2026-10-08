import { useEffect, useState } from "react";
import LabsShell from "@/components/labs/LabsShell";
import MarkdownMessage from "@/components/MarkdownMessage";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Swords, Loader2, Trophy, Share2, RotateCcw, Equal } from "lucide-react";
import { toast } from "sonner";
import { arenaBoard, arenaFight, arenaVote, type ArenaSide, type BoardRow } from "@/lib/labs";

const IDEAS = [
  "Erklär mir schwarze Löcher für ein 10-jähriges Kind",
  "Schreib ein kurzes Gedicht über Minecraft",
  "Gib mir 5 kreative Namen für ein Café",
  "Wie lerne ich am schnellsten programmieren?",
];

/** Zwei anonyme KI-Modelle beantworten dieselbe Aufgabe, der Nutzer stimmt ab. */
export default function Arena() {
  const [prompt, setPrompt] = useState("");
  const [busy, setBusy] = useState(false);
  const [fight, setFight] = useState<{ prompt: string; a: ArenaSide; b: ArenaSide } | null>(null);
  const [voted, setVoted] = useState<"a" | "b" | "tie" | null>(null);
  const [board, setBoard] = useState<BoardRow[]>([]);

  const loadBoard = () => arenaBoard().then(setBoard).catch(() => {});
  useEffect(() => { loadBoard(); }, []);

  const start = async (text = prompt) => {
    const p = text.trim();
    if (!p) return;
    setBusy(true); setFight(null); setVoted(null);
    try {
      const r = await arenaFight(p);
      setFight({ prompt: p, ...r });
    } catch (e) { toast.error((e as Error).message); }
    finally { setBusy(false); }
  };

  const vote = async (w: "a" | "b" | "tie") => {
    if (!fight || voted) return;
    setVoted(w);
    try { await arenaVote(fight.a.id, fight.b.id, w); loadBoard(); }
    catch (e) { toast.error((e as Error).message); }
  };

  const share = async () => {
    if (!fight || !voted) return;
    const win = voted === "tie" ? "Unentschieden" : (voted === "a" ? fight.a.label : fight.b.label) + " gewinnt";
    const text = `⚔️ Mythos Live-Arena: „${fight.prompt.slice(0, 80)}“ – ${fight.a.label} gegen ${fight.b.label}: ${win}! Probier es selbst:`;
    const url = `${window.location.origin}/arena`;
    try {
      if (navigator.share) await navigator.share({ title: "Mythos Live-Arena", text, url });
      else { await navigator.clipboard.writeText(`${text} ${url}`); toast.success("Text kopiert, jetzt einfügen und teilen"); }
    } catch { /* abgebrochen */ }
  };

  const sorted = [...board].sort((x, y) => (y.wins - y.losses) - (x.wins - x.losses) || y.wins - x.wins);

  return (
    <LabsShell icon={Swords} title="Live-Arena" subtitle="Zwei KI-Modelle lösen dieselbe Aufgabe. Du stimmst ab, wer gewinnt – erst danach siehst du die Namen." wide>
      <section className="glass-strong rounded-2xl p-4 md:p-5 space-y-3">
        <Textarea value={prompt} onChange={(e) => setPrompt(e.target.value)} maxLength={2000}
          placeholder="Stell eine Aufgabe, z. B. „Schreib einen Witz über Katzen“" className="min-h-[90px] text-base" />
        <div className="flex flex-wrap gap-2">
          {IDEAS.map((i) => (
            <button key={i} onClick={() => { setPrompt(i); start(i); }} disabled={busy}
              className="rounded-full bg-white/5 hover:bg-white/10 px-3 py-2 text-xs text-left max-w-full">{i}</button>
          ))}
        </div>
        <Button onClick={() => start()} disabled={busy || !prompt.trim()} className="w-full h-11 bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white">
          {busy ? <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Beide Modelle denken nach …</> : <><Swords className="h-4 w-4 mr-2" />Kampf starten</>}
        </Button>
      </section>

      {fight && (
        <section className="space-y-3">
          <div className="grid gap-3 md:grid-cols-2">
            {(["a", "b"] as const).map((k) => {
              const side = fight[k];
              const won = voted === k;
              return (
                <div key={k} className={`glass rounded-2xl p-4 flex flex-col gap-3 min-w-0 border ${won ? "border-fuchsia-400" : "border-transparent"}`}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-display text-lg">Modell {k.toUpperCase()}</span>
                    {voted && <span className="text-xs rounded-full bg-white/10 px-2 py-1 truncate">{side.label}{won ? " 🏆" : ""}</span>}
                  </div>
                  <div className="text-sm min-w-0 overflow-x-auto max-h-[50vh] md:max-h-[420px] overflow-y-auto"><MarkdownMessage content={side.text} /></div>
                  {!voted && (
                    <Button onClick={() => vote(k)} className="h-11 w-full mt-auto" variant="outline">
                      <Trophy className="h-4 w-4 mr-2" />{k.toUpperCase()} ist besser
                    </Button>
                  )}
                </div>
              );
            })}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
            {!voted ? (
              <Button variant="ghost" className="h-11 sm:col-start-2" onClick={() => vote("tie")}><Equal className="h-4 w-4 mr-2" />Unentschieden</Button>
            ) : (
              <>
                <Button variant="outline" className="h-11" onClick={share}><Share2 className="h-4 w-4 mr-2" />Ergebnis teilen</Button>
                <Button variant="outline" className="h-11" onClick={() => start(fight.prompt)} disabled={busy}><RotateCcw className="h-4 w-4 mr-2" />Neue Runde, gleiche Aufgabe</Button>
                <Button variant="ghost" className="h-11" onClick={() => { setFight(null); setPrompt(""); setVoted(null); }}>Neue Aufgabe</Button>
              </>
            )}
          </div>
        </section>
      )}

      <section className="glass rounded-2xl p-4 md:p-5">
        <h2 className="font-display text-lg mb-3 flex items-center gap-2"><Trophy className="h-5 w-5 text-amber-300" />Rangliste</h2>
        {sorted.length === 0 ? <p className="text-sm text-muted-foreground">Noch keine Stimmen.</p> : (
          <ol className="space-y-2">
            {sorted.map((r, i) => {
              const total = r.wins + r.losses + r.ties;
              const rate = total ? Math.round((r.wins / total) * 100) : 0;
              return (
                <li key={r.id} className="flex items-center gap-3 text-sm">
                  <span className="w-6 text-center text-muted-foreground">{i + 1}.</span>
                  <span className="flex-1 min-w-0 truncate">{r.label}</span>
                  <span className="hidden sm:block w-32 h-2 rounded-full bg-white/10 overflow-hidden"><span className="block h-full bg-fuchsia-400" style={{ width: `${rate}%` }} /></span>
                  <span className="text-xs text-muted-foreground whitespace-nowrap">{r.wins} Siege · {rate}%</span>
                </li>
              );
            })}
          </ol>
        )}
      </section>
    </LabsShell>
  );
}
