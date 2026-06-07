import { useEffect, useRef, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import TopNav from "@/components/TopNav";
import Paywall from "@/components/Paywall";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Gamepad2, Sparkles, Play, Trash2, Loader2, Maximize2 } from "lucide-react";
import { toast } from "sonner";

type Game = { id: string; title: string; prompt: string; genre: string | null; html: string; play_count: number; created_at: string };

const GENRES = [
  "Endless Runner", "Arcade", "Puzzle", "Racing", "Shooter", "Plattformer",
  "Survival", "Tower Defense", "Klick-Spiel", "Geschicklichkeit", "Sport",
];

export default function Games() {
  const { user } = useAuth();
  const { isPro, loading: subLoading } = useSubscription();
  const [games, setGames] = useState<Game[]>([]);
  const [busy, setBusy] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [title, setTitle] = useState("");
  const [genre, setGenre] = useState("Arcade");
  const [playing, setPlaying] = useState<Game | null>(null);
  const iframeRef = useRef<HTMLIFrameElement | null>(null);

  const load = async () => {
    if (!user) return;
    const { data } = await supabase.from("pro_games")
      .select("id,title,prompt,genre,html,play_count,created_at")
      .eq("user_id", user.id).order("created_at", { ascending: false });
    if (data) setGames(data as any);
  };
  useEffect(() => { load(); }, [user?.id]);

  const generate = async () => {
    if (!prompt.trim() || !user) return;
    setBusy(true);
    try {
      const { data, error } = await supabase.functions.invoke("game-gen", {
        body: { prompt: prompt.trim(), title: title.trim() || "Mein Spiel", genre },
      });
      if (error || !data?.html) throw new Error(error?.message || data?.error || "Generation fehlgeschlagen");
      const { data: inserted, error: insErr } = await supabase.from("pro_games").insert({
        user_id: user.id, title: title.trim() || "Mein Spiel", prompt: prompt.trim(), genre, html: data.html,
      }).select().single();
      if (insErr) throw insErr;
      toast.success("Spiel ist fertig! 🎮");
      setPrompt(""); setTitle("");
      setGames((g) => [inserted as any, ...g]);
      setPlaying(inserted as any);
    } catch (e: any) {
      toast.error(e.message || "Fehler beim Generieren");
    } finally { setBusy(false); }
  };

  const play = async (g: Game) => {
    setPlaying(g);
    await supabase.from("pro_games").update({ play_count: g.play_count + 1 }).eq("id", g.id);
  };
  const del = async (g: Game) => {
    if (!confirm(`„${g.title}" löschen?`)) return;
    await supabase.from("pro_games").delete().eq("id", g.id);
    setGames((gs) => gs.filter(x => x.id !== g.id));
  };
  const openFull = (g: Game) => {
    const blob = new Blob([g.html], { type: "text/html" });
    window.open(URL.createObjectURL(blob), "_blank");
  };

  if (subLoading) return <div className="min-h-screen"><TopNav /></div>;
  if (!isPro) {
    return (
      <div className="min-h-screen"><TopNav />
        <main className="container max-w-3xl py-10">
          <div className="text-center mb-6">
            <Gamepad2 className="h-14 w-14 mx-auto text-fuchsia-400 mb-3" />
            <h1 className="font-display text-3xl font-bold mb-2">Game Coder <span className="text-fuchsia-400">Pro</span></h1>
            <p className="text-muted-foreground">Beschreibe ein Spiel, die KI baut ein komplettes 3D-Casual-Game (wie auf Poki) — sofort spielbar im Browser.</p>
          </div>
          <Paywall feature="Game Coder" />
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen"><TopNav />
      <main className="container max-w-5xl py-6 md:py-10 space-y-8">
        <header className="flex items-center gap-3">
          <Gamepad2 className="h-7 w-7 text-fuchsia-400" />
          <div>
            <h1 className="font-display text-2xl md:text-3xl font-bold">Game Coder</h1>
            <p className="text-xs text-muted-foreground">3D-Casual-Games in 30 Sekunden — Pro Feature</p>
          </div>
        </header>

        <section className="glass-strong rounded-2xl p-5 md:p-6 space-y-4">
          <div className="grid md:grid-cols-2 gap-3">
            <div><Label>Titel</Label>
              <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Cube Jumper" className="mt-1.5" maxLength={60} />
            </div>
            <div><Label>Genre</Label>
              <Select value={genre} onValueChange={setGenre}>
                <SelectTrigger className="mt-1.5"><SelectValue /></SelectTrigger>
                <SelectContent>{GENRES.map(g => <SelectItem key={g} value={g}>{g}</SelectItem>)}</SelectContent>
              </Select>
            </div>
          </div>
          <div><Label>Spiel-Idee (so detailliert wie du willst)</Label>
            <Textarea value={prompt} onChange={(e) => setPrompt(e.target.value)} maxLength={1000}
              placeholder="Ein Würfel rollt durch eine bunte Landschaft, sammelt Münzen ein und weicht roten Hindernissen aus. Score oben, Speed steigt langsam."
              className="mt-1.5 min-h-[120px]" />
            <div className="text-[10px] text-muted-foreground mt-1 text-right">{prompt.length}/1000</div>
          </div>
          <Button onClick={generate} disabled={busy || !prompt.trim()} className="w-full bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white hover:opacity-90 h-11">
            {busy ? <><Loader2 className="h-4 w-4 mr-2 animate-spin" />KI baut dein Spiel… (~20-40s)</> : <><Sparkles className="h-4 w-4 mr-2" />Spiel generieren</>}
          </Button>
        </section>

        <section>
          <h2 className="font-display text-lg mb-3">Deine Spiele ({games.length})</h2>
          {games.length === 0 ? (
            <p className="text-sm text-muted-foreground italic">Noch keine Spiele — beschreib oben dein erstes!</p>
          ) : (
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {games.map(g => (
                <div key={g.id} className="glass rounded-xl p-4 flex flex-col gap-3">
                  <div>
                    <div className="font-display text-base truncate">{g.title}</div>
                    <div className="text-[10px] uppercase tracking-wider text-fuchsia-400">{g.genre}</div>
                    <p className="text-xs text-muted-foreground line-clamp-2 mt-1">{g.prompt}</p>
                  </div>
                  <div className="text-[10px] text-muted-foreground">🎮 {g.play_count}× gespielt</div>
                  <div className="flex gap-1.5">
                    <Button size="sm" onClick={() => play(g)} className="flex-1 bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white"><Play className="h-3.5 w-3.5 mr-1" />Spielen</Button>
                    <Button size="icon" variant="outline" onClick={() => openFull(g)} title="In neuem Tab"><Maximize2 className="h-4 w-4" /></Button>
                    <Button size="icon" variant="ghost" onClick={() => del(g)} className="hover:text-destructive"><Trash2 className="h-4 w-4" /></Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <Dialog open={!!playing} onOpenChange={(o) => !o && setPlaying(null)}>
          <DialogContent className="max-w-5xl w-[95vw] h-[85vh] p-0 gap-0 overflow-hidden glass-strong">
            <DialogHeader className="px-4 py-2.5 border-b border-border/50">
              <DialogTitle className="flex items-center justify-between gap-2 text-base">
                <span className="truncate">🎮 {playing?.title}</span>
                {playing && <Button size="sm" variant="outline" onClick={() => openFull(playing)}><Maximize2 className="h-3.5 w-3.5 mr-1.5" />Vollbild-Tab</Button>}
              </DialogTitle>
            </DialogHeader>
            {playing && (
              <iframe ref={iframeRef} title={playing.title} srcDoc={playing.html}
                sandbox="allow-scripts allow-pointer-lock"
                className="w-full flex-1 bg-black" style={{ height: "calc(85vh - 49px)" }} />
            )}
          </DialogContent>
        </Dialog>
      </main>
    </div>
  );
}
